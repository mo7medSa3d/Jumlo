package com.jumlo.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jumlo.app.R
import com.jumlo.app.data.scan.DocumentFilterEngine
import com.jumlo.app.data.scan.IdCardA4Composer
import com.jumlo.app.data.scan.ScanFilter
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    var frontBmp by remember { mutableStateOf<Bitmap?>(null) }
    var backBmp by remember { mutableStateOf<Bitmap?>(null) }
    var filter by remember { mutableStateOf(ScanFilter.CLEAN_DOCUMENT) }
    var flashOn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var capturingFront by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }

    fun decodeAndCropCard(file: File, cropToFrame: Boolean): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val maxSide = maxOf(opts.outWidth, opts.outHeight)
        var sample = 1
        while (maxSide / sample > 2048) sample *= 2
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
        if (!cropToFrame) return upright
        // Crop to the centered ID-1 (1.586:1) guide frame area (82% width).
        val cw = (upright.width * 0.86f).toInt().coerceIn(1, upright.width)
        val ch = (cw / 1.586f).toInt().coerceIn(1, upright.height)
        val left = ((upright.width - cw) / 2).coerceAtLeast(0)
        val top = ((upright.height - ch) / 2).coerceAtLeast(0)
        Bitmap.createBitmap(upright, left, top, cw, ch).also { if (it !== upright) upright.recycle() }
    }.getOrNull()

    fun decodeUri(uri: Uri): Bitmap? = runCatching {
        val tmp = File(context.cacheDir, "id-import-${System.currentTimeMillis()}.jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            decodeAndCropCard(tmp, cropToFrame = false)
        } finally { tmp.delete() }
    }.getOrNull()

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeUri(uri) }
                if (bmp != null) {
                    if (capturingFront) { frontBmp?.recycle(); frontBmp = bmp; capturingFront = false }
                    else { backBmp?.recycle(); backBmp = bmp }
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { frontBmp?.recycle(); backBmp?.recycle() } }

    val bothReady = frontBmp != null && backBmp != null

    if (!bothReady && hasCamera) {
        // LIVE CAMERAX ID CARD VIEWFINDER WITH CORNER-BRACKET GUIDE FRAME
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("scan.idcard.screen")) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                }
            )
            // Corner-bracket ID card frame overlay
            Canvas(Modifier.fillMaxSize()) {
                val frameW = size.width * 0.86f
                val frameH = frameW / 1.586f
                val left = (size.width - frameW) / 2f
                val top = (size.height - frameH) / 2f
                val bracket = 28.dp.toPx()
                val stroke = 3.5.dp.toPx()
                val accent = Color(0xFF00D2B4)
                // Dim outside frame
                drawRect(Color.Black.copy(alpha = 0.52f), Offset.Zero, Size(size.width, top))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(0f, top + frameH), Size(size.width, size.height - top - frameH))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(0f, top), Size(left, frameH))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(left + frameW, top), Size(size.width - left - frameW, frameH))
                // 4 corner brackets
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(accent, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Round)
                    drawLine(accent, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Round)
                }
                corner(left, top, bracket, bracket)
                corner(left + frameW, top, -bracket, bracket)
                corner(left, top + frameH, bracket, -bracket)
                corner(left + frameW, top + frameH, -bracket, -bracket)
            }
            // Top bar: Back, Step Pill (Front/Back), Flash
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
                        flashOn = !flashOn
                        camera?.cameraControl?.enableTorch(flashOn)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.scan_flash), tint = if (flashOn) Color(0xFFFFD54F) else Color.White)
                }
            }
            // Bottom studio controls: Captured Front thumb, Shutter, Gallery import
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                    // Front thumbnail badge if captured
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        frontBmp?.takeUnless { it.isRecycled }?.let { thumb ->
                            Surface(
                                onClick = { capturingFront = true },
                                shape = QuietTheme.shapes.thumbnail,
                                border = BorderStroke(1.5.dp, Color.White),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    // Circular Shutter Button
                    Surface(
                        onClick = {
                            if (busy) return@Surface
                            busy = true
                            val tmp = File(context.cacheDir, "id-cap-${System.currentTimeMillis()}.jpg")
                            val outOpts = ImageCapture.OutputFileOptions.Builder(tmp).build()
                            imageCapture.takePicture(outOpts, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                                    scope.launch {
                                        val bmp = withContext(Dispatchers.IO) {
                                            try { decodeAndCropCard(tmp, cropToFrame = true) } finally { tmp.delete() }
                                        }
                                        busy = false
                                        if (bmp != null) {
                                            if (capturingFront) { frontBmp?.recycle(); frontBmp = bmp; capturingFront = false }
                                            else { backBmp?.recycle(); backBmp = bmp }
                                        }
                                    }
                                }
                                override fun onError(e: ImageCaptureException) { tmp.delete(); busy = false }
                            })
                        },
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(4.dp, Color(0xFF00D2B4)),
                        modifier = Modifier.size(72.dp)
                    ) {}
                    // Gallery picker fallback
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

    // REVIEW & FILTER A4 2-IN-1 PREVIEW (or fallback when camera permission denied)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardSlot(stringResource(R.string.scan_id_front), frontBmp, filter, Modifier.weight(1f)) {
                frontBmp?.recycle(); frontBmp = null; capturingFront = true
                if (!hasCamera) galleryPicker.launch("image/*")
            }
            CardSlot(stringResource(R.string.scan_id_back), backBmp, filter, Modifier.weight(1f)) {
                backBmp?.recycle(); backBmp = null; capturingFront = false
                if (!hasCamera) galleryPicker.launch("image/*")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filterChoices, key = { it.first.name }) { (f, labelRes) ->
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
                scope.launch {
                    val out = File(context.cacheDir, "jumlo-scan-id-${System.currentTimeMillis()}.pdf")
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { IdCardA4Composer.compose(f, b, filter, out) }.isSuccess
                    }
                    busy = false
                    if (ok) onPdfReady(out)
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
        value = if (bmp == null) null else withContext(Dispatchers.Default) {
            runCatching { DocumentFilterEngine.apply(bmp, filter) }.getOrNull() ?: bmp
        }
    }
    Surface(
        onClick = onPick,
        shape = QuietTheme.shapes.card,
        color = pillars.cardSurface,
        modifier = modifier.height(168.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (filtered != null && !filtered.isRecycled) {
                Image(filtered.asImageBitmap(), label, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            } else {
                QuietTonalButton(label, onPick)
            }
        }
    }
}
