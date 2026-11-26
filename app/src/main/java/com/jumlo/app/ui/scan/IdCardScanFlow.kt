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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.jumlo.app.R
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.scan.DocumentFilterEngine
import com.jumlo.app.data.scan.IdCardA4Composer
import com.jumlo.app.data.scan.ScanFilter
import com.jumlo.app.diagnostics.AppDiagnostics
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.automirrored.rounded.RotateRight
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ID_CARD_MAX_EDGE = 1600
private const val PREVIEW_FILTER_MAX_EDGE = 720

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

    var frontBmp by remember { mutableStateOf<Bitmap?>(null) }
    var backBmp by remember { mutableStateOf<Bitmap?>(null) }
    var filter by rememberSaveable { mutableStateOf(ScanFilter.CLEAN_DOCUMENT) }
    var busy by remember { mutableStateOf(false) }
    var scannerLaunched by rememberSaveable { mutableStateOf(false) }
    var autoLaunchedOnce by rememberSaveable { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var capturingFront by rememberSaveable { mutableStateOf(true) }

    fun decodeCardFile(file: File, snapEdges: Boolean): Bitmap? = runCatching {
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
        val upright = if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
        } else raw
        val processed = if (!snapEdges) upright else {
            snapToCardEdges(upright).also { if (it !== upright) upright.recycle() }
        }
        scaleDownToMaxEdge(processed, ID_CARD_MAX_EDGE).also { if (it !== processed) processed.recycle() }
    }.onFailure { AppDiagnostics.report("id_card_decode", it) }.getOrNull()

    fun decodeUri(uri: Uri, snapEdges: Boolean): Bitmap? = runCatching {
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
                val bmp = withContext(Dispatchers.IO) { decodeUri(uri, snapEdges = true) }
                if (bmp != null) {
                    if (capturingFront) { frontBmp = bmp; capturingFront = false }
                    else { backBmp = bmp }
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
                    frontBmp = decoded[0]
                    backBmp = decoded[1]
                }
                decoded.size == 1 -> {
                    if (capturingFront) {
                        frontBmp = decoded[0]
                        if (backBmp == null) capturingFront = false
                    } else {
                        backBmp = decoded[0]
                    }
                }
                else -> errorRes = R.string.scan_capture_failed
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

    LaunchedEffect(Unit) {
        if (!autoLaunchedOnce && frontBmp == null && backBmp == null && playServicesAvailable) {
            autoLaunchedOnce = true
            launchMlIdScanner(maxPages = 2)
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
}

@Composable
private fun CardSlot(label: String, bmp: Bitmap?, filter: ScanFilter, modifier: Modifier, onRotate: (() -> Unit)? = null, onPick: () -> Unit) {
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
                if (onRotate != null) {
                    Surface(
                        onClick = onRotate,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.visual_rotate), modifier = Modifier.size(18.dp))
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
 * on a downscaled copy. Falls back to [src] if the card boundary is not clearly separated.
 */
internal fun snapToCardEdges(src: Bitmap): Bitmap {
    if (src.width < 80 || src.height < 80) return src
    val work = scaleDownToMaxEdge(src, 320)
    try {
        val w = work.width
        val h = work.height
        val pixels = IntArray(w * h)
        work.getPixels(pixels, 0, w, 0, 0, w, h)
        val lum = IntArray(w * h) { i ->
            val c = pixels[i]
            (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }

        val xMidStart = (w * 0.22f).toInt()
        val xMidEnd = (w * 0.78f).toInt()
        val yMidStart = (h * 0.22f).toInt()
        val yMidEnd = (h * 0.78f).toInt()

        fun rowEdgeScore(y: Int, x0: Int, x1: Int): Float {
            if (y <= 1 || y >= h - 2 || x1 <= x0) return 0f
            var sum = 0L
            for (x in x0 until x1) {
                sum += kotlin.math.abs(lum[(y + 1) * w + x] - lum[(y - 1) * w + x])
            }
            return sum.toFloat() / (x1 - x0)
        }

        fun colEdgeScore(x: Int, y0: Int, y1: Int): Float {
            if (x <= 1 || x >= w - 2 || y1 <= y0) return 0f
            var sum = 0L
            for (y in y0 until y1) {
                sum += kotlin.math.abs(lum[y * w + (x + 1)] - lum[y * w + (x - 1)])
            }
            return sum.toFloat() / (y1 - y0)
        }

        fun findPeak(range: IntProgression, scorer: (Int) -> Float): Int? {
            var bestIdx = -1
            var bestScore = 0f
            var sum = 0f
            var count = 0
            for (i in range) {
                val s = scorer(i)
                sum += s
                count++
                if (s > bestScore) {
                    bestScore = s
                    bestIdx = i
                }
            }
            val avg = if (count > 0) sum / count else 0f
            return if (bestIdx >= 0 && bestScore >= maxOf(14f, avg * 2.1f)) bestIdx else null
        }

        // Try 4-corner perspective rectification first when the card is tilted.
        val xL0 = (w * 0.18f).toInt(); val xL1 = (w * 0.46f).toInt()
        val xR0 = (w * 0.54f).toInt(); val xR1 = (w * 0.82f).toInt()
        val yT0 = (h * 0.18f).toInt(); val yT1 = (h * 0.46f).toInt()
        val yB0 = (h * 0.54f).toInt(); val yB1 = (h * 0.82f).toInt()
        val topL = findPeak(2..(h * 0.26f).toInt()) { rowEdgeScore(it, xL0, xL1) }
        val topR = findPeak(2..(h * 0.26f).toInt()) { rowEdgeScore(it, xR0, xR1) }
        val botL = findPeak((h - 3) downTo (h * 0.74f).toInt()) { rowEdgeScore(it, xL0, xL1) }
        val botR = findPeak((h - 3) downTo (h * 0.74f).toInt()) { rowEdgeScore(it, xR0, xR1) }
        val leftT = findPeak(2..(w * 0.26f).toInt()) { colEdgeScore(it, yT0, yT1) }
        val leftB = findPeak(2..(w * 0.26f).toInt()) { colEdgeScore(it, yB0, yB1) }
        val rightT = findPeak((w - 3) downTo (w * 0.74f).toInt()) { colEdgeScore(it, yT0, yT1) }
        val rightB = findPeak((w - 3) downTo (w * 0.74f).toInt()) { colEdgeScore(it, yB0, yB1) }

        if (topL != null && topR != null && botL != null && botR != null &&
            leftT != null && leftB != null && rightT != null && rightB != null
        ) {
            val tilted = kotlin.math.abs(topL - topR) >= 3 || kotlin.math.abs(botL - botR) >= 3 ||
                kotlin.math.abs(leftT - leftB) >= 3 || kotlin.math.abs(rightT - rightB) >= 3
            if (tilted) {
                fun intersect(
                    ax1: Float, ay1: Float, ax2: Float, ay2: Float,
                    bx1: Float, by1: Float, bx2: Float, by2: Float
                ): Pair<Float, Float>? {
                    val denom = (ax1 - ax2) * (by1 - by2) - (ay1 - ay2) * (bx1 - bx2)
                    if (kotlin.math.abs(denom) < 1e-3f) return null
                    val px = ((ax1 * ay2 - ay1 * ax2) * (bx1 - bx2) - (ax1 - ax2) * (bx1 * by2 - by1 * bx2)) / denom
                    val py = ((ax1 * ay2 - ay1 * ax2) * (by1 - by2) - (ay1 - ay2) * (bx1 * by2 - by1 * bx2)) / denom
                    return px to py
                }
                val xA = (xL0 + xL1) * 0.5f; val xB = (xR0 + xR1) * 0.5f
                val yA = (yT0 + yT1) * 0.5f; val yB = (yB0 + yB1) * 0.5f
                val tl = intersect(xA, topL.toFloat(), xB, topR.toFloat(), leftT.toFloat(), yA, leftB.toFloat(), yB)
                val tr = intersect(xA, topL.toFloat(), xB, topR.toFloat(), rightT.toFloat(), yA, rightB.toFloat(), yB)
                val br = intersect(xA, botL.toFloat(), xB, botR.toFloat(), rightT.toFloat(), yA, rightB.toFloat(), yB)
                val bl = intersect(xA, botL.toFloat(), xB, botR.toFloat(), leftT.toFloat(), yA, leftB.toFloat(), yB)
                if (tl != null && tr != null && br != null && bl != null) {
                    val corners = listOf(tl, tr, br, bl)
                    val inBounds = corners.all { (cx, cy) -> cx in -w * 0.05f..w * 1.05f && cy in -h * 0.05f..h * 1.05f }
                    val qW = ((tr.first - tl.first) + (br.first - bl.first)) * 0.5f
                    val qH = ((bl.second - tl.second) + (br.second - tr.second)) * 0.5f
                    val qRatio = if (qH > 1f) qW / qH else 0f
                    if (inBounds && qW >= w * 0.50f && qH >= h * 0.50f && (qRatio in 1.25f..1.95f || qRatio in 0.51f..0.80f)) {
                        val sxScale = src.width.toFloat() / w
                        val syScale = src.height.toFloat() / h
                        val srcPts = floatArrayOf(
                            tl.first * sxScale, tl.second * syScale,
                            tr.first * sxScale, tr.second * syScale,
                            br.first * sxScale, br.second * syScale,
                            bl.first * sxScale, bl.second * syScale
                        )
                        val outW = (qW * sxScale).toInt().coerceIn(80, src.width)
                        val outH = (qH * syScale).toInt().coerceIn(80, src.height)
                        val dstPts = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
                        val warp = Matrix()
                        if (warp.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
                            val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(warped)
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(src, warp, paint)
                            return warped
                        }
                    }
                }
            }
        }

        val top = findPeak(2..(h * 0.24f).toInt()) { rowEdgeScore(it, xMidStart, xMidEnd) } ?: 0
        val bottom = findPeak((h - 3) downTo (h * 0.76f).toInt()) { rowEdgeScore(it, xMidStart, xMidEnd) } ?: (h - 1)
        val left = findPeak(2..(w * 0.24f).toInt()) { colEdgeScore(it, yMidStart, yMidEnd) } ?: 0
        val right = findPeak((w - 3) downTo (w * 0.76f).toInt()) { colEdgeScore(it, yMidStart, yMidEnd) } ?: (w - 1)

        if (top == 0 && bottom == h - 1 && left == 0 && right == w - 1) return src
        val cw = right - left + 1
        val ch = bottom - top + 1
        if (cw < w * 0.50f || ch < h * 0.50f) return src
        val ratio = cw.toFloat() / ch.toFloat()
        val validCardRatio = ratio in 1.25f..1.95f || ratio in 0.51f..0.80f
        if (!validCardRatio) return src

        val sx = (left.toFloat() / w * src.width).toInt().coerceIn(0, src.width - 2)
        val sy = (top.toFloat() / h * src.height).toInt().coerceIn(0, src.height - 2)
        val sw = (cw.toFloat() / w * src.width).toInt().coerceIn(1, src.width - sx)
        val sh = (ch.toFloat() / h * src.height).toInt().coerceIn(1, src.height - sy)
        return Bitmap.createBitmap(src, sx, sy, sw, sh)
    } finally {
        if (work !== src) work.recycle()
    }
}
