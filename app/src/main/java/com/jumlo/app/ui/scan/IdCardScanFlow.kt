package com.jumlo.app.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
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

/** CamScanner-style live CameraX ID Card / Passport scanner with corner-bracket overlay, flash, and A4 2-in-1 preview. */
@Composable
internal fun IdCardScanFlow(
    onPdfReady: (File) -> Unit,
    onBack: () -> Unit,
    externalBusy: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    LaunchedEffect(Unit) { if (!hasCamera) permLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var frontBmp by remember { mutableStateOf<Bitmap?>(null) }
    var backBmp by remember { mutableStateOf<Bitmap?>(null) }
    var filter by rememberSaveable { mutableStateOf(ScanFilter.CLEAN_DOCUMENT) }
    var flashOn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var capturingFront by rememberSaveable { mutableStateOf(true) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }

    fun decodeAndCropCard(file: File, cropToFrame: Boolean): Bitmap? = runCatching {
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
        val cropped = if (!cropToFrame) upright else {
            // Crop to the centered ID-1 (1.586:1) guide frame area (86% width).
            val cw = (upright.width * 0.86f).toInt().coerceIn(1, upright.width)
            val ch = (cw / 1.586f).toInt().coerceIn(1, upright.height)
            val left = ((upright.width - cw) / 2).coerceAtLeast(0)
            val top = ((upright.height - ch) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(upright, left, top, cw, ch).also { if (it !== upright) upright.recycle() }
        }
        scaleDownToMaxEdge(cropped, ID_CARD_MAX_EDGE).also { if (it !== cropped) cropped.recycle() }
    }.onFailure { AppDiagnostics.report("id_card_decode", it) }.getOrNull()

    fun decodeUri(uri: Uri): Bitmap? = runCatching {
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
            decodeAndCropCard(tmp, cropToFrame = false)
        } finally { tmp.delete() }
    }.onFailure { AppDiagnostics.report("id_card_import", it) }.getOrNull()

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            errorRes = null
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeUri(uri) }
                if (bmp != null) {
                    if (capturingFront) { frontBmp = bmp; capturingFront = false }
                    else { backBmp = bmp }
                } else {
                    errorRes = R.string.scan_capture_failed
                }
            }
        }
    }

    // Never recycle bitmaps held in Compose state; detach references and unbind CameraX cleanly.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { camera?.cameraControl?.enableTorch(false) }
            runCatching { cameraProvider?.unbindAll() }
            camera = null
            cameraProvider = null
            frontBmp = null
            backBmp = null
        }
    }

    val bothReady = frontBmp != null && backBmp != null

    if (!bothReady && hasCamera) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("scan.idcard.screen")) {
            var previewReleased by remember { mutableStateOf(false) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    previewReleased = false
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            if (previewReleased) return@addListener
                            runCatching {
                                val provider = future.get()
                                if (previewReleased) return@addListener
                                cameraProvider = provider
                                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                                val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                                provider.unbindAll()
                                val bound = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                                camera = bound
                                if (flashOn && bound.cameraInfo.hasFlashUnit()) {
                                    runCatching { bound.cameraControl.enableTorch(true) }
                                }
                            }.onFailure {
                                AppDiagnostics.report("id_card_camera_bind", it)
                                errorRes = R.string.scan_capture_failed
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                onRelease = {
                    previewReleased = true
                    runCatching { camera?.cameraControl?.enableTorch(false) }
                    flashOn = false
                    runCatching { cameraProvider?.unbindAll() }
                    camera = null
                }
            )
            Canvas(Modifier.fillMaxSize()) {
                val frameW = size.width * 0.86f
                val frameH = frameW / 1.586f
                val left = (size.width - frameW) / 2f
                val top = (size.height - frameH) / 2f
                val bracket = 28.dp.toPx()
                val stroke = 3.5.dp.toPx()
                val accent = Color(0xFF00D2B4)
                drawRect(Color.Black.copy(alpha = 0.52f), Offset.Zero, Size(size.width, top))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(0f, top + frameH), Size(size.width, size.height - top - frameH))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(0f, top), Size(left, frameH))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(left + frameW, top), Size(size.width - left - frameW, frameH))
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(accent, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Round)
                    drawLine(accent, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Round)
                }
                corner(left, top, bracket, bracket)
                corner(left + frameW, top, -bracket, bracket)
                corner(left, top + frameH, bracket, -bracket)
                corner(left + frameW, top + frameH, -bracket, -bracket)
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.scan_back), tint = Color.White)
                }
                Surface(shape = QuietTheme.shapes.pill, color = Color.Black.copy(alpha = 0.65f)) {
                    Text(
                        stringResource(if (capturingFront) R.string.scan_id_front else R.string.scan_id_back),
                        style = QuietTheme.typography.metadataStrong,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val cam = camera
                        if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                            flashOn = !flashOn
                            runCatching { cam.cameraControl.enableTorch(flashOn) }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.scan_flash), tint = if (flashOn) Color(0xFFFFD54F) else Color.White)
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                errorRes?.let { msgRes ->
                    Surface(shape = QuietTheme.shapes.pill, color = colors.errorContainer) {
                        Text(
                            stringResource(msgRes),
                            style = QuietTheme.typography.metadataStrong,
                            color = colors.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Text(
                    stringResource(if (capturingFront) R.string.scan_id_hint_front else R.string.scan_id_hint_back),
                    style = QuietTheme.typography.metadata,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        frontBmp?.takeUnless { it.isRecycled }?.let { thumb ->
                            Surface(
                                onClick = { if (!busy) capturingFront = true },
                                shape = QuietTheme.shapes.thumbnail,
                                border = BorderStroke(1.5.dp, Color.White),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Image(thumb.asImageBitmap(), stringResource(R.string.scan_id_front), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    val captureLabel = stringResource(R.string.scan_capture)
                    Surface(
                        onClick = {
                            if (busy) return@Surface
                            val targetFront = capturingFront
                            busy = true
                            errorRes = null
                            val tmp = File(context.cacheDir, "id-cap-${System.currentTimeMillis()}.jpg")
                            val outOpts = ImageCapture.OutputFileOptions.Builder(tmp).build()
                            imageCapture.takePicture(outOpts, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                                    if (!scope.isActive) { tmp.delete(); return }
                                    scope.launch {
                                        val bmp = try {
                                            withContext(Dispatchers.IO) { decodeAndCropCard(tmp, cropToFrame = true) }
                                        } finally {
                                            withContext(NonCancellable + Dispatchers.IO) { tmp.delete() }
                                        }
                                        busy = false
                                        if (bmp != null) {
                                            if (targetFront) { frontBmp = bmp; capturingFront = false }
                                            else { backBmp = bmp }
                                        } else {
                                            errorRes = R.string.scan_capture_failed
                                        }
                                    }
                                }
                                override fun onError(e: ImageCaptureException) {
                                    tmp.delete()
                                    busy = false
                                    AppDiagnostics.report("id_card_capture", e)
                                    errorRes = R.string.scan_capture_failed
                                }
                            })
                        },
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(4.dp, Color(0xFF00D2B4)),
                        modifier = Modifier.size(72.dp).semantics { contentDescription = captureLabel }
                    ) {}
                    Surface(
                        onClick = { galleryPicker.launch("image/*") },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PhotoLibrary, stringResource(R.string.scan_gallery_short), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        return
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
        if (!hasCamera && !bothReady) {
            Surface(
                shape = QuietTheme.shapes.card,
                color = pillars.cardSurface,
                border = BorderStroke(1.dp, pillars.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.scan_camera_permission_title), style = QuietTheme.typography.sectionTitle, color = colors.onSurface)
                    Text(stringResource(R.string.scan_camera_permission_body), style = QuietTheme.typography.metadata, color = colors.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuietPrimaryButton(
                            text = stringResource(R.string.scan_open_settings),
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuietTonalButton(
                            text = stringResource(R.string.scan_gallery_short),
                            onClick = { galleryPicker.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
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
            CardSlot(stringResource(R.string.scan_id_front), frontBmp, filter, Modifier.weight(1f)) {
                frontBmp = null; capturingFront = true; errorRes = null
                if (!hasCamera) galleryPicker.launch("image/*")
            }
            CardSlot(stringResource(R.string.scan_id_back), backBmp, filter, Modifier.weight(1f)) {
                backBmp = null; capturingFront = false; errorRes = null
                if (!hasCamera) galleryPicker.launch("image/*")
            }
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
private fun CardSlot(label: String, bmp: Bitmap?, filter: ScanFilter, modifier: Modifier, onPick: () -> Unit) {
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
            } else {
                QuietTonalButton(label, onPick)
            }
        }
    }
}
