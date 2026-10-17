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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Manual document scan: CameraX preview + capture, then a fixed-quad perspective
 * correction (inset quad, user aligns the document inside the frame). No automatic
 * edge detection is claimed.
 */
@Composable
fun ScanScreen(onCaptured: (java.io.File) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(com.jumlo.app.R.string.scan_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(com.jumlo.app.R.string.scan_hint))
        error?.let { code ->
            val resId = when (code) {
                "scan_error_bind" -> com.jumlo.app.R.string.scan_error_bind
                "scan_error_process" -> com.jumlo.app.R.string.scan_error_process
                else -> com.jumlo.app.R.string.scan_error_capture
            }
            Text(stringResource(resId), color = MaterialTheme.colorScheme.error)
        }
        if (!granted) {
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(com.jumlo.app.R.string.scan_allow_camera)) }
        } else {
            AndroidView(factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val capture = ImageCapture.Builder().build(); imageCapture = capture
                        try { val owner = ctx as? androidx.lifecycle.LifecycleOwner ?: throw IllegalStateException("NO_LIFECYCLE_OWNER"); provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }
                        catch (e: Exception) { error = "scan_error_bind" }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }, modifier = Modifier.fillMaxWidth().height(420.dp))
            Button(onClick = {
                val capture = imageCapture ?: return@Button
                val file = File(context.cacheDir, "scan-${System.currentTimeMillis()}.jpg")
                capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), Executors.newSingleThreadExecutor(), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        try {
                            onCaptured(perspectiveInset(file))
                        } catch (e: Exception) { error = "scan_error_process" }
                    }
                    override fun onError(exception: ImageCaptureException) { error = "scan_error_capture" }
                })
            }) { Text(stringResource(com.jumlo.app.R.string.scan_capture)) }
        }
        TextButton(onClick = onBack) { Text(stringResource(com.jumlo.app.R.string.scan_back)) }
    }
}

/** Fixed inset-quad perspective correction (manual alignment model). */
private fun perspectiveInset(file: File): File {
    val src = android.graphics.BitmapFactory.decodeFile(file.path) ?: throw IllegalArgumentException("SCAN_DECODE_FAILED")
    val w = src.width; val h = src.height
    val insetX = w * 0.08f; val insetY = h * 0.08f
    val matrix = Matrix().apply {
        setPolyToPoly(
            floatArrayOf(insetX, insetY, w - insetX, insetY, w - insetX, h - insetY, insetX, h - insetY), 0,
            floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()), 0, 4
        )
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    val dest = File(file.parentFile, file.nameWithoutExtension + "-scan.jpg")
    FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    src.recycle(); out.recycle()
    return dest
}

/** Drawn signature pad: strokes -> transparent PNG in cache; callback gets content URI string. */
@Composable
fun SignaturePad(onSaved: (java.io.File) -> Unit) {
    val context = LocalContext.current
    var path by remember { mutableStateOf(androidx.compose.ui.graphics.Path()) }
    var strokes by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(com.jumlo.app.R.string.signature_title))
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(140.dp).background(Color.White).pointerInput(Unit) {
            detectDragGestures(onDragStart = { path.moveTo(it.x, it.y); strokes++ }, onDrag = { change, _ -> path.lineTo(change.position.x, change.position.y) })
        }) { drawPath(path, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { path = androidx.compose.ui.graphics.Path(); strokes = 0 }) { Text(stringResource(com.jumlo.app.R.string.signature_clear)) }
            Button(enabled = strokes > 0, onClick = {
                val bitmap = Bitmap.createBitmap(600, 252, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val androidPath = Path(path.asAndroidPath())
                canvas.drawPath(androidPath, Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 7f; isAntiAlias = true })
                val file = File(context.cacheDir, "signature-${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                onSaved(file)
            }) { Text(stringResource(com.jumlo.app.R.string.signature_save)) }
        }
        Text(stringResource(com.jumlo.app.R.string.signature_hint), style = MaterialTheme.typography.bodySmall)
    }
}
