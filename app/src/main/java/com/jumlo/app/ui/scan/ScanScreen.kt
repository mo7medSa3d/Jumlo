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
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.exifinterface.media.ExifInterface
import com.jumlo.app.core.ScanGeometry
import com.jumlo.app.core.ScanPoint
import com.jumlo.app.ui.util.rememberAppHaptics
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Document scan: CameraX preview + capture, automatic luminance-gradient edge
 * boundary detection to initialize the four corners, plus interactive draggable
 * corners so the user can refine the quad before perspective correction + crop.
 */
@Composable
fun ScanScreen(onCaptured: (java.io.File) -> Unit, onCapturedBatch: (List<File>) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    val captured = remember(capturedPath) { capturedPath?.let(::File)?.takeIf(File::isFile) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(QuietTheme.spacing.canvasGutterCompact), verticalArrangement = Arrangement.spacedBy(QuietTheme.spacing.interCardGap)) {
        Text(stringResource(R.string.scan_hint), style = QuietTheme.typography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { code ->
            val resId = when (code) {
                "scan_error_bind" -> R.string.scan_error_bind
                "scan_error_process" -> R.string.scan_error_process
                "scan_error_smart" -> R.string.scan_error_smart
                else -> R.string.scan_error_capture
            }
            QuietStateCard(
                title = stringResource(resId),
                subtitle = stringResource(R.string.scan_hint),
                actionLabel = stringResource(R.string.scan_retake),
                onAction = {
                    captured?.delete()
                    error = null
                    capturedPath = null
                },
                isError = true
            )
        }
        SmartDocumentScanButton(
            enabled = !processing && captured == null,
            onCapturedBatch = onCapturedBatch,
            onError = { error = "scan_error_smart" },
            autoLaunch = true,
            onCancelled = onBack
        )
        // Only show the manual CameraX fallback when Play Services ML Kit Document Scanner is unavailable.
        val useManualFallback = error == "scan_error_smart"
        if (useManualFallback && !granted) {
            QuietStateCard(
                title = stringResource(R.string.scan_title),
                subtitle = stringResource(R.string.scan_hint),
                actionLabel = stringResource(R.string.scan_allow_camera),
                onAction = { permission.launch(Manifest.permission.CAMERA) }
            )
        } else if (useManualFallback && captured == null) {
            QuietCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(380.dp)) {
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
                        modifier = Modifier.fillMaxSize().clip(QuietTheme.shapes.thumbnail)
                    )
                    // High-contrast viewfinder guides: visual only, never intercept touches.
                    ComposeCanvas(Modifier.fillMaxSize().padding(32.dp)) {
                        val length = size.minDimension * 0.15f
                        val endX = size.width
                        val endY = size.height
                        val thick = 3.5.dp.toPx()
                        val guide = Color.White
                        drawLine(guide, Offset(0f, length), Offset(0f, 0f), thick, StrokeCap.Round)
                        drawLine(guide, Offset(0f, 0f), Offset(length, 0f), thick, StrokeCap.Round)
                        drawLine(guide, Offset(endX - length, 0f), Offset(endX, 0f), thick, StrokeCap.Round)
                        drawLine(guide, Offset(endX, 0f), Offset(endX, length), thick, StrokeCap.Round)
                        drawLine(guide, Offset(0f, endY - length), Offset(0f, endY), thick, StrokeCap.Round)
                        drawLine(guide, Offset(0f, endY), Offset(length, endY), thick, StrokeCap.Round)
                        drawLine(guide, Offset(endX - length, endY), Offset(endX, endY), thick, StrokeCap.Round)
                        drawLine(guide, Offset(endX, endY - length), Offset(endX, endY), thick, StrokeCap.Round)
                    }
                }
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
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                error = null
                                capturedPath = file.absolutePath
                            }
                            override fun onError(exception: ImageCaptureException) {
                                file.delete()
                                error = "scan_error_capture"
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        captured?.let { file ->
            if (processing) LinearProgressIndicator(Modifier.fillMaxWidth())
            QuadAdjust(
                file = file,
                busy = processing,
                onApply = { quad ->
                    if (processing) return@QuadAdjust
                    processing = true
                    scope.launch {
                        try {
                            val warped = withContext(Dispatchers.IO) { perspectiveQuad(file, quad) }
                            file.delete()
                            capturedPath = null
                            onCaptured(warped)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            error = "scan_error_process"
                        } finally {
                            processing = false
                        }
                    }
                },
                onRetake = {
                    if (!processing) {
                        file.delete()
                        capturedPath = null
                    }
                }
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
internal fun decodeScanBitmapBounded(file: File, maxEdge: Int = 2048): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap=android.graphics.BitmapFactory.decodeFile(file.path, opts) ?: return null
    val orientation=runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix=Matrix().apply {
        when(orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f);postScale(-1f,1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f);postScale(-1f,1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    if(matrix.isIdentity) return bitmap
    return try {
        Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also { if(it!==bitmap) bitmap.recycle() }
    } catch(error:Throwable) { bitmap.recycle();throw error }
}

internal fun perspectiveQuad(file: File, quad: List<Offset>): File {
    require(ScanGeometry.valid(quad.map { ScanPoint(it.x,it.y) })) { "SCAN_QUAD_INVALID" }
    val src = decodeScanBitmapBounded(file, 2400) ?: throw IllegalArgumentException("SCAN_DECODE_FAILED")
    var out: Bitmap? = null
    val dest = File(file.parentFile, file.nameWithoutExtension + "-scan.jpg")
    try {
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
        val outW = maxOf(dist(0, 1), dist(3, 2)).toInt().coerceIn(1, 2400)
        val outH = maxOf(dist(0, 3), dist(1, 2)).toInt().coerceIn(1, 2400)
        val matrix = Matrix().apply {
            require(setPolyToPoly(srcPts, 0, floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat()), 0, 4)) { "SCAN_QUAD_INVALID" }
        }
        val rendered = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also { out = it }
        Canvas(rendered).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        FileOutputStream(dest).use {
            require(rendered.compress(Bitmap.CompressFormat.JPEG, 92, it)) { "SCAN_ENCODE_FAILED" }
            it.fd.sync()
        }
        return dest
    } catch (t: Throwable) {
        dest.delete()
        throw t
    } finally {
        if (!src.isRecycled) src.recycle()
        out?.takeUnless { it.isRecycled }?.recycle()
    }
}

/** Draggable four-corner quad over the captured photo (normalized coordinates). */
@Composable
private fun QuadAdjust(file: File, busy: Boolean = false, onApply: (List<Offset>) -> Unit, onRetake: () -> Unit) {
    val guideColor=MaterialTheme.colorScheme.tertiary
    val canvasBg=MaterialTheme.colorScheme.surfaceContainerLowest
    val haptics=rememberAppHaptics()
    val touchRadius=with(LocalDensity.current) { 26.dp.toPx() }
    var loadFailed by remember(file) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null,file) {
        value=withContext(Dispatchers.IO) { runCatching { decodeScanBitmapBounded(file,1600) }.getOrNull() }
        loadFailed=value==null
    }
    var corners by rememberSaveable(file.path) { mutableStateOf(listOf(.08f,.08f,.92f,.08f,.92f,.92f,.08f,.92f)) }
    var initialized by rememberSaveable(file.path) { mutableStateOf(false) }
    LaunchedEffect(bitmap) {
        if(bitmap!=null && !initialized) {
            val detected=detectDocumentQuad(bitmap!!)
            if(ScanGeometry.valid(detected.map {ScanPoint(it.x,it.y)})) corners=detected.flatMap {listOf(it.x,it.y)}
            initialized=true
        }
    }
    fun points()=corners.chunked(2).map { Offset(it[0],it[1]) }
    fun move(index:Int,x:Float,y:Float) {
        val next=points().toMutableList().apply { this[index]=Offset(x.coerceIn(0f,1f),y.coerceIn(0f,1f)) }
        if(ScanGeometry.valid(next.map {ScanPoint(it.x,it.y)})) corners=next.flatMap {listOf(it.x,it.y)}
    }
    var dragging by remember { mutableIntStateOf(-1) }
    var selectedCorner by rememberSaveable(file.path) { mutableIntStateOf(0) }
    var precise by rememberSaveable { mutableStateOf(false) }
    // Compose/RenderThread may retain a frame after disposal. Let the bitmap be
    // collected once both renderer and composition release it instead of recycling a displayed image.
    QuietCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scan_adjust_hint),style=QuietTheme.typography.metadata)
        if(loadFailed) Text(stringResource(R.string.upgrade_preview_unavailable),color=MaterialTheme.colorScheme.error)
        else if(bitmap==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().heightIn(min=220.dp,max=420.dp).aspectRatio(1f)
            .clip(QuietTheme.shapes.thumbnail).background(canvasBg)) {
            bitmap?.let { image ->
                androidx.compose.foundation.Image(image.asImageBitmap(),contentDescription=stringResource(R.string.scan_title),
                    modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().pointerInput(busy,image,touchRadius) {
                    if(busy) return@pointerInput
                    fun fit()=ScanGeometry.fit(image.width.toFloat(),image.height.toFloat(),size.width.toFloat(),size.height.toFloat())
                    var beforeDrag=corners
                    detectDragGestures(onDragStart={ start ->
                        beforeDrag=corners
                        val rect=fit()
                        dragging=points().indices.minByOrNull { i ->
                            val p=points()[i]; (Offset(rect.left+p.x*rect.width,rect.top+p.y*rect.height)-start).getDistanceSquared()
                        }?.takeIf { i ->
                            val p=points()[i]; (Offset(rect.left+p.x*rect.width,rect.top+p.y*rect.height)-start).getDistance()<=touchRadius
                        } ?: -1
                        if(dragging>=0) {selectedCorner=dragging;haptics.longPress()}
                    },onDrag={change,_->
                        if(dragging>=0) {change.consume();val p=fit().normalized(change.position.x,change.position.y);move(dragging,p.x,p.y)}
                    },onDragEnd={if(dragging>=0) haptics.sliderTick();dragging=-1},onDragCancel={corners=beforeDrag;dragging=-1})
                }) {
                    val rect=ScanGeometry.fit(image.width.toFloat(),image.height.toFloat(),size.width,size.height)
                    val pts=points().map {Offset(rect.left+it.x*rect.width,rect.top+it.y*rect.height)}
                    pts.indices.forEach {i->drawLine(guideColor,pts[i],pts[(i+1)%4],strokeWidth=3.dp.toPx())}
                    pts.forEachIndexed { i,p->
                        drawCircle(guideColor.copy(alpha=.16f),touchRadius,p)
                        drawCircle(guideColor,if(i==dragging) 10.dp.toPx() else 7.dp.toPx(),p)
                    }
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={precise=!precise},enabled=!busy) {Text(stringResource(R.string.upgrade_scan_precision))}
            TextButton(onClick={corners=listOf(0f,0f,1f,0f,1f,1f,0f,1f)},enabled=!busy) {Text(stringResource(R.string.visual_edit_reset))}
        }
        if(precise) {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(4) {index->FilterChip(selectedCorner==index,{selectedCorner=index},label={Text(stringResource(R.string.upgrade_scan_corner,index+1))})}
            }
            val xLabel=stringResource(R.string.upgrade_scan_horizontal)
            val yLabel=stringResource(R.string.upgrade_scan_vertical)
            Text(xLabel,style=QuietTheme.typography.metadataStrong)
            Slider(corners[selectedCorner*2],{move(selectedCorner,it,corners[selectedCorner*2+1])},enabled=!busy,
                modifier=Modifier.semantics {contentDescription=xLabel;stateDescription="${(corners[selectedCorner*2]*100).toInt()}%"},
                onValueChangeFinished={haptics.sliderTick()})
            Text(yLabel,style=QuietTheme.typography.metadataStrong)
            Slider(corners[selectedCorner*2+1],{move(selectedCorner,corners[selectedCorner*2],it)},enabled=!busy,
                modifier=Modifier.semantics {contentDescription=yLabel;stateDescription="${(corners[selectedCorner*2+1]*100).toInt()}%"},
                onValueChangeFinished={haptics.sliderTick()})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            QuietTonalButton(text=stringResource(R.string.scan_retake),onClick=onRetake,enabled=!busy,modifier=Modifier.weight(1f))
            QuietPrimaryButton(text=stringResource(R.string.scan_apply),enabled=!busy && bitmap!=null,
                onClick={onApply(points())},modifier=Modifier.weight(1f))
        }
    }
}

/** Drawn signature pad: strokes -> transparent PNG in cache; callback gets local File. */
@Composable
fun SignaturePad(onSaved: (java.io.File) -> Unit) {
    val context = LocalContext.current
    val strokeColor = MaterialTheme.colorScheme.onSurface
    val padSurface = MaterialTheme.colorScheme.surfaceContainerLowest
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    val activePoints = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val hasInk = completedStrokes.isNotEmpty() || activePoints.size >= 2
    var saveFailed by remember {mutableStateOf(false)}
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.signature_title), style = QuietTheme.typography.metadataStrong)
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxWidth().height(140.dp).clip(QuietTheme.shapes.thumbnail).background(padSurface)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            activePoints.clear()
                            activePoints.add(start)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            activePoints.add(change.position)
                        },
                        onDragEnd = {
                            if (activePoints.size >= 2) completedStrokes.add(activePoints.toList())
                            activePoints.clear()
                        },
                        onDragCancel = { activePoints.clear() }
                    )
                }
        ) {
            fun drawStroke(pts: List<Offset>) {
                if (pts.size < 2) return
                val strokePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(
                    strokePath,
                    strokeColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            completedStrokes.forEach(::drawStroke)
            drawStroke(activePoints)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietTonalButton(text = stringResource(R.string.signature_clear), onClick = { completedStrokes.clear(); activePoints.clear() })
            QuietPrimaryButton(
                text = stringResource(R.string.signature_save),
                enabled = hasInk && canvasSize.width>0 && canvasSize.height>0,
                onClick = {
                    saveFailed=false
                    val geometry=signatureExportGeometry(canvasSize.width,canvasSize.height)
                    val outWidth=geometry.width
                    val outHeight=geometry.height
                    val sx=geometry.scale
                    val sy=geometry.scale
                    val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                    val file = File(context.cacheDir, "signature-${System.currentTimeMillis()}.png")
                    try {
                        val canvas = Canvas(bitmap)
                        val androidPath = Path()
                        val allStrokes = completedStrokes.toList() + if (activePoints.size >= 2) listOf(activePoints.toList()) else emptyList()
                        for (stroke in allStrokes) {
                            if (stroke.size < 2) continue
                            androidPath.moveTo(stroke.first().x * sx, stroke.first().y * sy)
                            for (i in 1 until stroke.size) androidPath.lineTo(stroke[i].x * sx, stroke[i].y * sy)
                        }
                        canvas.drawPath(androidPath, Paint().apply {
                            color = android.graphics.Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = 4f*geometry.scale
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        })
                        FileOutputStream(file).use {
                            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "SIGNATURE_ENCODE_FAILED" }
                            it.fd.sync()
                        }
                        onSaved(file)
                    } catch (error: Exception) {
                        file.delete()
                        saveFailed=true
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            )
        }
        if(saveFailed) Text(stringResource(R.string.interactive_signature_failed),color=MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.signature_hint), style = QuietTheme.typography.metadata)
    }
}
