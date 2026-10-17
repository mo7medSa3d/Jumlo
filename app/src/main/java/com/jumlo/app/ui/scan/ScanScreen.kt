package com.jumlo.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jumlo.app.R
import com.jumlo.app.ui.components.QuietCard
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.theme.QuietTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Document scan: CameraX preview + capture, automatic luminance-gradient edge
 * boundary detection to initialize the four corners, plus interactive draggable
 * corners so the user can refine the quad before perspective correction + crop.
 */
@Composable
fun ScanScreen(onCaptured: (java.io.File) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var captured by remember { mutableStateOf<File?>(null) }
    Column(Modifier.fillMaxSize().padding(QuietTheme.spacing.canvasGutterCompact), verticalArrangement = Arrangement.spacedBy(QuietTheme.spacing.interCardGap)) {
        Text(stringResource(R.string.scan_hint), style = QuietTheme.typography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { code ->
            val resId = when (code) {
                "scan_error_bind" -> R.string.scan_error_bind
                "scan_error_process" -> R.string.scan_error_process
                else -> R.string.scan_error_capture
            }
            QuietStateCard(
                title = stringResource(resId),
                subtitle = stringResource(R.string.scan_hint),
                actionLabel = stringResource(R.string.scan_retake),
                onAction = { error = null; captured = null },
                isError = true
            )
        }
        if (!granted) {
            QuietStateCard(
                title = stringResource(R.string.scan_title),
                subtitle = stringResource(R.string.scan_hint),
                actionLabel = stringResource(R.string.scan_allow_camera),
                onAction = { permission.launch(Manifest.permission.CAMERA) }
            )
        } else if (captured == null) {
            QuietCard(Modifier.fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val capture = ImageCapture.Builder().build()
                                imageCapture = capture
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                } catch (_: Exception) {
                                    error = "scan_error_bind"
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(380.dp).clip(QuietTheme.shapes.thumbnail)
                )
            }
            QuietPrimaryButton(
                text = stringResource(R.string.scan_capture),
                onClick = {
                    val capture = imageCapture ?: return@QuietPrimaryButton
                    val file = File(context.cacheDir, "scan-${System.currentTimeMillis()}.jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) { captured = file }
                            override fun onError(exception: ImageCaptureException) { error = "scan_error_capture" }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        captured?.let { file ->
            QuadAdjust(
                file = file,
                onApply = { quad ->
                    try { onCaptured(perspectiveQuad(file, quad)) } catch (_: Exception) { error = "scan_error_process" }
                },
                onRetake = { captured = null }
            )
        }
    }
}

/**
 * Estimates initial document quad corners (TL, TR, BR, BL in normalized 0..1)
 * by scanning luminance gradients inward from each edge on a downsampled grid.
 * Falls back to a clean 8% inset rectangle when contrast is low.
 */
internal fun detectDocumentQuad(bitmap: Bitmap): List<Offset> {
    val sw = 64
    val sh = 64
    val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
    val pixels = IntArray(sw * sh)
    scaled.getPixels(pixels, 0, sw, 0, 0, sw, sh)
    if (scaled !== bitmap) scaled.recycle()
    fun luma(x: Int, y: Int): Int {
        val c = pixels[y * sw + x]
        return ((c shr 16 and 0xff) * 77 + (c shr 8 and 0xff) * 150 + (c and 0xff) * 29) shr 8
    }
    val threshold = 24
    fun findLeft(y: Int): Float {
        for (x in 2 until sw / 2) if (abs(luma(x, y) - luma(x - 1, y)) >= threshold) return x.toFloat() / sw
        return 0.08f
    }
    fun findRight(y: Int): Float {
        for (x in (sw - 3) downTo sw / 2) if (abs(luma(x, y) - luma(x + 1, y)) >= threshold) return x.toFloat() / sw
        return 0.92f
    }
    fun findTop(x: Int): Float {
        for (y in 2 until sh / 2) if (abs(luma(x, y) - luma(x, y - 1)) >= threshold) return y.toFloat() / sh
        return 0.08f
    }
    fun findBottom(x: Int): Float {
        for (y in (sh - 3) downTo sh / 2) if (abs(luma(x, y) - luma(x, y + 1)) >= threshold) return y.toFloat() / sh
        return 0.92f
    }
    val tl = Offset(findLeft(sh / 4).coerceIn(0.03f, 0.45f), findTop(sw / 4).coerceIn(0.03f, 0.45f))
    val tr = Offset(findRight(sh / 4).coerceIn(0.55f, 0.97f), findTop(sw * 3 / 4).coerceIn(0.03f, 0.45f))
    val br = Offset(findRight(sh * 3 / 4).coerceIn(0.55f, 0.97f), findBottom(sw * 3 / 4).coerceIn(0.55f, 0.97f))
    val bl = Offset(findLeft(sh * 3 / 4).coerceIn(0.03f, 0.45f), findBottom(sw / 4).coerceIn(0.55f, 0.97f))
    return listOf(tl, tr, br, bl)
}

/**
 * Perspective correction with a user-adjusted quad (corners normalized 0..1,
 * order: TL, TR, BR, BL). Output size uses the longer of each edge pair.
 */
internal fun perspectiveQuad(file: File, quad: List<Offset>): File {
    require(quad.size == 4) { "SCAN_QUAD_INVALID" }
    val src = android.graphics.BitmapFactory.decodeFile(file.path) ?: throw IllegalArgumentException("SCAN_DECODE_FAILED")
    val w = src.width; val h = src.height
    val srcPts = FloatArray(8)
    quad.forEachIndexed { i, o ->
        srcPts[i * 2] = (o.x.coerceIn(0f, 1f) * w)
        srcPts[i * 2 + 1] = (o.y.coerceIn(0f, 1f) * h)
    }
    fun dist(a: Int, b: Int): Float {
        val dx = srcPts[a * 2] - srcPts[b * 2]; val dy = srcPts[a * 2 + 1] - srcPts[b * 2 + 1]
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    val outW = maxOf(dist(0, 1), dist(3, 2)).toInt().coerceAtLeast(1)
    val outH = maxOf(dist(0, 3), dist(1, 2)).toInt().coerceAtLeast(1)
    val matrix = Matrix().apply {
        setPolyToPoly(srcPts, 0, floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat()), 0, 4)
    }
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    val dest = File(file.parentFile, file.nameWithoutExtension + "-scan.jpg")
    FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    src.recycle(); out.recycle()
    return dest
}

/** Draggable four-corner quad over the captured photo (normalized coordinates). */
@Composable
private fun QuadAdjust(file: File, onApply: (List<Offset>) -> Unit, onRetake: () -> Unit) {
    val guideColor = MaterialTheme.colorScheme.tertiary
    val canvasBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val bitmap = remember(file) { android.graphics.BitmapFactory.decodeFile(file.path) }
    var corners by remember(file, bitmap) {
        mutableStateOf(bitmap?.let(::detectDocumentQuad) ?: listOf(Offset(0.08f, 0.08f), Offset(0.92f, 0.08f), Offset(0.92f, 0.92f), Offset(0.08f, 0.92f)))
    }
    var dragging by remember { mutableStateOf(-1) }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeUnless { it.isRecycled }?.recycle() }
    }
    QuietCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scan_adjust_hint), style = QuietTheme.typography.metadata)
        Box(Modifier.fillMaxWidth().height(340.dp).clip(QuietTheme.shapes.thumbnail).background(canvasBg)) {
            bitmap?.let {
                androidx.compose.foundation.Image(
                    it.asImageBitmap(), contentDescription = stringResource(R.string.scan_title),
                    modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        val nx = start.x / size.width; val ny = start.y / size.height
                        dragging = corners.indices.minByOrNull { i ->
                            val dx = corners[i].x - nx; val dy = corners[i].y - ny; dx * dx + dy * dy
                        } ?: -1
                    },
                    onDrag = { change, _ ->
                        if (dragging in corners.indices) {
                            val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                            val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                            corners = corners.toMutableList().also { list -> list[dragging] = Offset(nx, ny) }
                        }
                    },
                    onDragEnd = { dragging = -1 }
                )
            }) {
                val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
                for (i in pts.indices) {
                    val a = pts[i]; val b = pts[(i + 1) % pts.size]
                    drawLine(guideColor, a, b, strokeWidth = 4f)
                }
                pts.forEach { drawCircle(guideColor, radius = 16f, center = it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietTonalButton(text = stringResource(R.string.scan_retake), onClick = onRetake)
            QuietPrimaryButton(text = stringResource(R.string.scan_apply), onClick = { onApply(corners) })
        }
    }
}

/** Drawn signature pad: strokes -> transparent PNG in cache; callback gets local File. */
@Composable
fun SignaturePad(onSaved: (java.io.File) -> Unit) {
    val context = LocalContext.current
    val strokeColor = MaterialTheme.colorScheme.onSurface
    val padSurface = MaterialTheme.colorScheme.surfaceContainerLowest
    var path by remember { mutableStateOf(androidx.compose.ui.graphics.Path()) }
    var strokes by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.signature_title), style = QuietTheme.typography.metadataStrong)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(140.dp).clip(QuietTheme.shapes.thumbnail).background(padSurface).pointerInput(Unit) {
            detectDragGestures(onDragStart = { path.moveTo(it.x, it.y); strokes++ }, onDrag = { change, _ -> path.lineTo(change.position.x, change.position.y) })
        }) { drawPath(path, strokeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietTonalButton(text = stringResource(R.string.signature_clear), onClick = { path = androidx.compose.ui.graphics.Path(); strokes = 0 })
            QuietPrimaryButton(
                text = stringResource(R.string.signature_save),
                enabled = strokes > 0,
                onClick = {
                    val bitmap = Bitmap.createBitmap(600, 252, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val androidPath = Path(path.asAndroidPath())
                    canvas.drawPath(androidPath, Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 7f; isAntiAlias = true })
                    val file = File(context.cacheDir, "signature-${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    onSaved(file)
                }
            )
        }
        Text(stringResource(R.string.signature_hint), style = QuietTheme.typography.metadata)
    }
}
