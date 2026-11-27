package com.jumlo.app.ui.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.jumlo.app.R
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.scan.CardOrientationDetector
import com.jumlo.app.data.scan.DocumentFilterEngine
import com.jumlo.app.data.scan.IdCardA4Composer
import com.jumlo.app.data.scan.ScanFilter
import com.jumlo.app.diagnostics.AppDiagnostics
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.automirrored.rounded.RotateRight
import com.jumlo.app.ui.icons.rounded.Crop
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ID_CARD_MAX_EDGE = 2400
private const val PREVIEW_FILTER_MAX_EDGE = 960

private fun scaleDownToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= maxEdge || longest <= 0) return src
    val scale = maxEdge.toFloat() / longest.toFloat()
    val targetW = (src.width * scale).toInt().coerceAtLeast(1)
    val targetH = (src.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, targetW, targetH, true)
}

/**
 * ML Kit Document Scanner-powered ID Card / Passport flow:
 * uses on-device ML edge detection & perspective correction for front and back,
 * then composes an exact A4 2-in-1 PDF.
 */
@Composable
internal fun IdCardScanFlow(
    onPdfReady: (File) -> Unit,
    onBack: () -> Unit,
    externalBusy: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence<Context>(context) { current -> (current as? ContextWrapper)?.baseContext?.takeUnless { it === current } }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val playServicesAvailable = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val haptics = com.jumlo.app.ui.util.rememberAppHaptics()

    var frontBmp by remember { mutableStateOf<Bitmap?>(null) }
    var backBmp by remember { mutableStateOf<Bitmap?>(null) }
    var filter by rememberSaveable { mutableStateOf(ScanFilter.CLEAN_DOCUMENT) }
    var busy by remember { mutableStateOf(false) }
    var scannerLaunched by rememberSaveable { mutableStateOf(false) }
    var autoLaunchedOnce by rememberSaveable { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var capturingFront by rememberSaveable { mutableStateOf(true) }
    var cropPendingSide by remember { mutableStateOf<Pair<Boolean, Bitmap>?>(null) }

    suspend fun decodeCardFile(file: File, snapEdges: Boolean): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val maxSide = maxOf(opts.outWidth, opts.outHeight)
        var sample = 1
        while (maxSide / sample > ID_CARD_MAX_EDGE) sample *= 2
        val raw = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val rotation = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val exifFixed = if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
        } else raw
        val mlOriented = CardOrientationDetector.autoOrientLandscape(exifFixed).also {
            if (it !== exifFixed) exifFixed.recycle()
        }
        val processed = if (!snapEdges) mlOriented else {
            snapToCardEdges(mlOriented).also { if (it !== mlOriented) mlOriented.recycle() }
        }
        scaleDownToMaxEdge(processed, ID_CARD_MAX_EDGE).also { if (it !== processed) processed.recycle() }
    }.onFailure { AppDiagnostics.report("id_card_decode", it) }.getOrNull()

    suspend fun decodeUri(uri: Uri, snapEdges: Boolean): Bitmap? = runCatching {
        val tmp = File(context.cacheDir, "id-import-${System.currentTimeMillis()}.jpg")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src ->
                tmp.outputStream().use { dst ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = src.read(buf); if (read < 0) break
                        total += read; check(total <= 100L * 1024 * 1024)
                        check(context.cacheDir.usableSpace >= ResourceBudgetPolicy.minFreeBytes)
                        if (read > 0) dst.write(buf, 0, read)
                    }
                }
            }
            decodeCardFile(tmp, snapEdges = snapEdges)
        } finally { tmp.delete() }
    }.onFailure { AppDiagnostics.report("id_card_import", it) }.getOrNull()

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            errorRes = null
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeUri(uri, snapEdges = false) }
                if (bmp != null) {
                    cropPendingSide = capturingFront to bmp
                } else {
                    errorRes = R.string.scan_capture_failed
                }
            }
        }
    }

    val mlScannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        scannerLaunched = false
        if (activityResult.resultCode != Activity.RESULT_OK) {
            busy = false
            if (frontBmp == null && backBmp == null) onBack()
            return@rememberLauncherForActivityResult
        }
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val pageUris = scanResult?.pages?.mapNotNull { it.imageUri }.orEmpty()
        if (pageUris.isEmpty()) {
            busy = false
            errorRes = R.string.scan_capture_failed
            return@rememberLauncherForActivityResult
        }
        busy = true
        errorRes = null
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                pageUris.take(2).mapNotNull { uri -> decodeUri(uri, snapEdges = false) }
            }
            busy = false
            when {
                decoded.size >= 2 -> {
                    haptics.captureSuccess()
                    frontBmp = decoded[0]
                    backBmp = decoded[1]
                }
                decoded.size == 1 -> {
                    haptics.captureSuccess()
                    if (capturingFront) {
                        frontBmp = decoded[0]
                        if (backBmp == null) capturingFront = false
                    } else {
                        backBmp = decoded[0]
                    }
                }
                else -> { haptics.error(); errorRes = R.string.scan_capture_failed }
            }
        }
    }

    fun launchMlIdScanner(maxPages: Int) {
        if (busy || externalBusy || scannerLaunched) return
        if (!playServicesAvailable || activity == null) {
            galleryPicker.launch("image/*")
            return
        }
        busy = true
        errorRes = null
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(maxPages)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        try {
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    try {
                        scannerLaunched = true
                        mlScannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } catch (e: Exception) {
                        scannerLaunched = false
                        busy = false
                        AppDiagnostics.report("id_card_ml_launch", e)
                        errorRes = R.string.scan_capture_failed
                    }
                }
                .addOnFailureListener { e ->
                    busy = false
                    AppDiagnostics.report("id_card_ml_intent", e)
                    errorRes = R.string.scan_capture_failed
                }
        } catch (e: Exception) {
            busy = false
            AppDiagnostics.report("id_card_ml_client", e)
            errorRes = R.string.scan_capture_failed
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!autoLaunchedOnce && frontBmp == null && backBmp == null && playServicesAvailable) {
                autoLaunchedOnce = true
                launchMlIdScanner(maxPages = 2)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            frontBmp = null
            backBmp = null
        }
    }

    val filterChoices = remember {
        listOf(
            ScanFilter.ORIGINAL to R.string.scan_filter_original,
            ScanFilter.CLEAN_DOCUMENT to R.string.scan_filter_clean,
            ScanFilter.CRISP_BW to R.string.scan_filter_bw,
            ScanFilter.MAGIC_COLOR to R.string.scan_filter_magic
        )
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).testTag("scan.idcard.screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.scan_back))
            }
            Text(stringResource(R.string.scan_id_card_title), style = QuietTheme.typography.sectionTitle, color = colors.onSurface)
        }
        errorRes?.let { msgRes ->
            Surface(
                shape = QuietTheme.shapes.card,
                color = colors.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(msgRes),
                    style = QuietTheme.typography.metadataStrong,
                    color = colors.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardSlot(
                label = stringResource(R.string.scan_id_front),
                bmp = frontBmp,
                filter = filter,
                modifier = Modifier.weight(1f),
                onRotate = {
                    frontBmp?.takeUnless { it.isRecycled }?.let { src ->
                        val m = Matrix().apply { postRotate(90f) }
                        frontBmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                    }
                },
                onCrop = {
                    frontBmp?.takeUnless { it.isRecycled }?.let { src -> cropPendingSide = true to src }
                },
                onPick = {
                    capturingFront = true; errorRes = null
                    launchMlIdScanner(maxPages = if (backBmp == null) 2 else 1)
                }
            )
            CardSlot(
                label = stringResource(R.string.scan_id_back),
                bmp = backBmp,
                filter = filter,
                modifier = Modifier.weight(1f),
                onRotate = {
                    backBmp?.takeUnless { it.isRecycled }?.let { src ->
                        val m = Matrix().apply { postRotate(90f) }
                        backBmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                    }
                },
                onCrop = {
                    backBmp?.takeUnless { it.isRecycled }?.let { src -> cropPendingSide = false to src }
                },
                onPick = {
                    capturingFront = false; errorRes = null
                    launchMlIdScanner(maxPages = 1)
                }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietTonalButton(
                text = stringResource(R.string.scan_id_scan_now),
                enabled = !busy && !externalBusy,
                onClick = {
                    capturingFront = frontBmp == null
                    launchMlIdScanner(maxPages = if (frontBmp == null && backBmp == null) 2 else 1)
                },
                modifier = Modifier.weight(1f)
            )
            QuietTonalButton(
                text = stringResource(R.string.scan_gallery_short),
                enabled = !busy && !externalBusy,
                onClick = {
                    capturingFront = frontBmp == null
                    galleryPicker.launch("image/*")
                },
                modifier = Modifier.weight(1f)
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filterChoices, key = { it.first.name }, contentType = { "filter-chip" }) { (f, labelRes) ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    shape = QuietTheme.shapes.pill,
                    border = null,
                    label = { Text(stringResource(labelRes), style = QuietTheme.typography.metadataStrong) }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (busy || externalBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        QuietPrimaryButton(
            text = stringResource(R.string.scan_id_create_a4),
            enabled = frontBmp != null && backBmp != null && !busy && !externalBusy,
            onClick = {
                val f = frontBmp ?: return@QuietPrimaryButton
                val b = backBmp ?: return@QuietPrimaryButton
                busy = true
                errorRes = null
                scope.launch {
                    val out = File(context.cacheDir, "jumlo-scan-id-${System.currentTimeMillis()}.pdf")
                    val result = withContext(Dispatchers.IO) {
                        runCatching { IdCardA4Composer.compose(f, b, filter, out) }
                    }
                    busy = false
                    result.onSuccess { onPdfReady(out) }.onFailure { err ->
                        out.delete()
                        AppDiagnostics.report("id_card_compose", err)
                        errorRes = R.string.scan_compose_failed
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    cropPendingSide?.let { (isFrontSide, sourceBitmap) ->
        InteractiveDocumentCropDialog(
            initialBitmap = sourceBitmap,
            autoDetectInitial = true,
            onApply = { cropped ->
                if (isFrontSide) {
                    frontBmp = cropped
                    if (backBmp == null) capturingFront = false
                } else {
                    backBmp = cropped
                }
                cropPendingSide = null
            },
            onDismiss = {
                // Keep original uncropped frame if user cancels manual corner adjustment so nothing is ever lost.
                if (isFrontSide && frontBmp == null) {
                    frontBmp = sourceBitmap
                    if (backBmp == null) capturingFront = false
                } else if (!isFrontSide && backBmp == null) {
                    backBmp = sourceBitmap
                }
                cropPendingSide = null
            }
        )
    }
}

@Composable
private fun CardSlot(
    label: String,
    bmp: Bitmap?,
    filter: ScanFilter,
    modifier: Modifier,
    onRotate: (() -> Unit)? = null,
    onCrop: (() -> Unit)? = null,
    onPick: () -> Unit
) {
    val pillars = QuietTheme.pillars
    val filtered by produceState<Bitmap?>(initialValue = bmp, key1 = bmp, key2 = filter) {
        value = if (bmp == null || bmp.isRecycled) null else withContext(Dispatchers.Default) {
            runCatching {
                val previewSrc = scaleDownToMaxEdge(bmp, PREVIEW_FILTER_MAX_EDGE)
                val rendered = DocumentFilterEngine.apply(previewSrc, filter)
                if (previewSrc !== bmp && previewSrc !== rendered) previewSrc.recycle()
                rendered
            }.getOrNull() ?: bmp
        }
    }
    DisposeOwnedPreviewBitmap(filtered.takeUnless { it === bmp })
    Surface(
        onClick = onPick,
        shape = QuietTheme.shapes.card,
        color = pillars.cardSurface,
        modifier = modifier.height(168.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val readyBitmap = filtered
            if (readyBitmap != null && !readyBitmap.isRecycled) {
                val imageBitmap = remember(readyBitmap) { readyBitmap.asImageBitmap() }
                Image(imageBitmap, label, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onCrop != null) {
                        Surface(
                            onClick = onCrop,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Crop, stringResource(R.string.scan_crop_action), modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    if (onRotate != null) {
                        Surface(
                            onClick = onRotate,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.visual_rotate), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            } else {
                QuietTonalButton(label, onPick)
            }
        }
    }
}

/**
 * Refines an ID card crop by finding the strongest outer border edges via luminance gradients
 * on a downscaled copy. Falls back cleanly to [src] if the card boundary is not clearly separated.
 */
internal fun snapToCardEdges(src: Bitmap): Bitmap {
    if (src.width < 80 || src.height < 80) return src
    val quad = DocumentQuadProcessor.detectConservativeQuad(src)
    if (quad.isFullFrame() || !DocumentQuadGeometry.isValid(quad)) return src
    return DocumentQuadProcessor.warp(src, quad, maxEdge = maxOf(src.width, src.height))
}
