package com.jumlo.app.ui.scan

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.jumlo.app.data.scan.DocumentFilterEngine
import com.jumlo.app.data.scan.ScanFilter
import com.jumlo.app.ui.icons.rounded.AutoAwesome
import com.jumlo.app.ui.icons.rounded.Crop
import java.io.FileOutputStream
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jumlo.app.R
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.pdf.SmartScanResultStore
import com.jumlo.app.data.processing.BitmapTransforms
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateBefore
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateNext
import com.jumlo.app.ui.icons.rounded.Bolt
import com.jumlo.app.ui.icons.rounded.Check
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.icons.rounded.Delete
import com.jumlo.app.ui.icons.rounded.Image
import com.jumlo.app.ui.icons.rounded.PictureAsPdf
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Full-screen iOS-inspired OLED document scanner camera & page reviewer.
 * Lifecycle-bound camera is created only after the primary Play-services scanner is unavailable.
 */
@Composable
internal fun CameraXScanFallback(
    onScannedPdf: (File) -> Unit,
    onScannedJpegs: (List<File>) -> Unit = {},
    jpegOutput: Boolean = false,
    onBack: () -> Unit,
    externalBusy: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val sessionId = rememberSaveable { UUID.randomUUID().toString() }
    val sessionDirectory = remember(sessionId) { File(context.cacheDir, "camera-scan-$sessionId") }
    var pagePaths by rememberSaveable(sessionId) { mutableStateOf(listOf<String>()) }
    var reviewIndex by rememberSaveable(sessionId) { mutableStateOf<Int?>(null) }
    var flashMode by rememberSaveable(sessionId) { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var initialized by remember { mutableStateOf(false) }
    var permitted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraReady by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    val latestResult by rememberUpdatedState(onScannedPdf)
    val latestJpegs by rememberUpdatedState(onScannedJpegs)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permitted = granted }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val highResSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
    }
    val capture = remember(highResSelector) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(highResSelector)
            .setJpegQuality(96)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .build()
    }
    var reviewEditEpoch by remember { mutableIntStateOf(0) }
    var cropTargetBitmap by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var activeFilter by rememberSaveable(sessionId) { mutableStateOf(ScanFilter.ORIGINAL) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(boundCamera, previewView) {
        val cam = boundCamera
        if (cam != null) {
            previewView.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build()
                    runCatching { cam.cameraControl.startFocusAndMetering(action) }
                    v.performClick()
                }
                true
            }
        } else {
            previewView.setOnTouchListener(null)
        }
    }

    LaunchedEffect(flashMode, boundCamera) {
        capture.flashMode = flashMode
        boundCamera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                runCatching { cam.cameraControl.enableTorch(flashMode == ImageCapture.FLASH_MODE_ON) }
            }
        }
    }
    LaunchedEffect(pagePaths.size, reviewIndex) {
        val idx = reviewIndex
        if (idx != null && (pagePaths.isEmpty() || idx !in pagePaths.indices)) {
            reviewIndex = if (pagePaths.isEmpty()) null else pagePaths.lastIndex
        }
    }
    LaunchedEffect(sessionId) {
        try {
            withContext(Dispatchers.IO) { check(sessionDirectory.mkdirs() || sessionDirectory.isDirectory) }
            initialized = true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            error = true
        }
    }
    val hostActivity = remember(context) {
        generateSequence<Context>(context) { current -> (current as? ContextWrapper)?.baseContext?.takeUnless { it === current } }
            .filterIsInstance<Activity>().firstOrNull()
    }
    DisposableEffect(sessionDirectory) {
        active = true
        onDispose {
            active = false
            // Preserve the session across rotation; remove captured pages when leaving the scanner.
            if (hostActivity?.isChangingConfigurations != true) {
                Thread { runCatching { sessionDirectory.deleteRecursively() } }.start()
            }
        }
    }
    DisposableEffect(permitted, lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var disposed = false
        cameraReady = false
        if (permitted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                            CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        val cameraPreview = Preview.Builder()
                            .setResolutionSelector(highResSelector)
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        preview = cameraPreview
                        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                        val groupBuilder = UseCaseGroup.Builder()
                            .addUseCase(cameraPreview)
                            .addUseCase(capture)
                        previewView.viewPort?.let { groupBuilder.setViewPort(it) }
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, groupBuilder.build())
                        cameraReady = true
                    } catch (_: Exception) {
                        error = true
                        cameraReady = false
                    }
                }
            }, mainExecutor)
        }
        onDispose {
            disposed = true
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            boundCamera = null
            preview?.let { provider?.unbind(it, capture) }
            cameraReady = false
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty() && !busy && !externalBusy) {
            busy = true; error = false
            scope.launch {
                val owned = mutableListOf<File>()
                try {
                    val files = withContext(Dispatchers.IO) {
                        check(pagePaths.size + uris.size <= 100)
                        uris.map { uri ->
                            currentCoroutineContext().ensureActive()
                            val target = File(sessionDirectory, "page-${UUID.randomUUID()}.jpg").also { owned += it }
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                target.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var total = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val read = input.read(buffer); if (read < 0) break
                                        total += read; check(total <= 100L * 1024 * 1024)
                                        check(context.cacheDir.usableSpace >= ResourceBudgetPolicy.minFreeBytes)
                                        output.write(buffer, 0, read)
                                    }
                                    output.fd.sync()
                                }
                            } ?: kotlin.error("SCAN_IMAGE_UNREADABLE")
                            BitmapTransforms.inspect(target)
                            target.path
                        }
                    }
                    pagePaths = pagePaths + files
                    owned.clear()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    error = true
                } finally {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { owned.forEach(File::delete) }
                    busy = false
                }
            }
        }
    }

    fun launchGallery() {
        try {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            error = true
        }
    }

    fun saveAsPdf() {
        if (pagePaths.isEmpty() || busy || externalBusy) return
        val pages = pagePaths.map(::File)
        busy = true; error = false
        scope.launch {
            var owned: File? = null
            try {
                owned = SmartScanResultStore.createPdfFromImages(context,pages)
                if (active) { latestResult(requireNotNull(owned)); owned = null }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = true
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { owned?.delete() }
                busy = false
            }
        }
    }

    fun saveAsJpegs() {
        if (pagePaths.isEmpty() || busy || externalBusy) return
        val pages = pagePaths.map(::File)
        busy = true; error = false
        scope.launch {
            var owned: List<File>? = null
            try {
                owned = SmartScanResultStore.copyImagesToCache(context, pages)
                if (active) { latestJpegs(requireNotNull(owned)); owned = null }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = true
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { owned?.forEach { it.delete() } }
                busy = false
            }
        }
    }

    fun captureNow() {
        if (!cameraReady || !initialized || busy || externalBusy || pagePaths.size >= 100) return
        haptics.actionClick()
        busy = true; error = false
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val file = File(sessionDirectory, "page-${UUID.randomUUID()}.jpg")
        try {
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        if (active) { pagePaths = pagePaths + file.path; busy = false }
                        else Thread { runCatching { file.delete() } }.start()
                    }
                    override fun onError(exception: ImageCaptureException) {
                        if (active) { busy = false; error = true }
                        Thread { runCatching { file.delete() } }.start()
                    }
                }
            )
        } catch (_: Exception) {
            busy = false; error = true
            scope.launch(Dispatchers.IO) { file.delete() }
        }
    }

    val currentReview = reviewIndex?.takeIf { it in pagePaths.indices }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("scan.cameraFallback")
    ) {
        if (currentReview != null) {
            val path = pagePaths[currentReview]
            val bitmap by produceState<Bitmap?>(null, path, reviewEditEpoch) {
                value = withContext(Dispatchers.IO) {
                    try { BitmapTransforms.decodeSampled(File(path), 12L * 1024 * 1024, maxEdge = 2400).bitmap }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { null }
                    catch (_: OutOfMemoryError) { null }
                }
            }
            DisposeOwnedPreviewBitmap(bitmap)

            cropTargetBitmap?.let { (targetPath, targetBmp) ->
                InteractiveDocumentCropDialog(
                    initialBitmap = targetBmp,
                    autoDetectInitial = true,
                    onApply = { warped ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    FileOutputStream(File(targetPath)).use { out ->
                                        warped.compress(Bitmap.CompressFormat.JPEG, 96, out)
                                        out.fd.sync()
                                    }
                                }
                            }
                            if (!warped.isRecycled) warped.recycle()
                            cropTargetBitmap = null
                            reviewEditEpoch++
                        }
                    },
                    onDismiss = { cropTargetBitmap = null }
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = { haptics.actionClick(); reviewIndex = null },
                        shape = CircleShape,
                        color = Color(0xFF1C1E22),
                        border = BorderStroke(1.dp, Color(0xFF2E3138)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_close), tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = "${currentReview + 1} / ${pagePaths.size}",
                        style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.semantics { heading() }
                    )
                    Surface(
                        onClick = {
                            haptics.actionClick()
                            pagePaths = pagePaths.filterNot { it == path }
                            scope.launch(Dispatchers.IO) {
                                File(path).delete()
                                File("$path.orig").delete()
                            }
                            reviewIndex = null
                        },
                        shape = QuietTheme.shapes.pill,
                        color = Color(0xFF1C1E22),
                        border = BorderStroke(1.dp, Color(0xFF2E3138)),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(stringResource(R.string.studio_capture_page), style = QuietTheme.typography.metadataStrong, color = Color.White)
                        }
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    bitmap?.takeUnless { it.isRecycled }?.let { decoded ->
                        val imageBitmap = remember(decoded) { decoded.asImageBitmap() }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = stringResource(R.string.jumlo_page_label, currentReview + 1),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(QuietTheme.shapes.thumbnail),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = QuietTheme.shapes.dockPill,
                        color = Color(0xFF16181C),
                        border = BorderStroke(1.dp, Color(0xFF2A2D34))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                enabled = currentReview > 0 && !busy && !externalBusy,
                                onClick = {
                                    haptics.selectionToggle()
                                    pagePaths = pagePaths.toMutableList().apply { add(currentReview - 1, removeAt(currentReview)) }
                                    reviewIndex = currentReview - 1
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, stringResource(R.string.jumlo_move_earlier), tint = Color.White)
                            }
                            IconButton(
                                enabled = currentReview < pagePaths.lastIndex && !busy && !externalBusy,
                                onClick = {
                                    haptics.selectionToggle()
                                    pagePaths = pagePaths.toMutableList().apply { add(currentReview + 1, removeAt(currentReview)) }
                                    reviewIndex = currentReview + 1
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateNext, stringResource(R.string.jumlo_move_later), tint = Color.White)
                            }
                            IconButton(
                                enabled = bitmap?.isRecycled == false && !busy && !externalBusy,
                                onClick = {
                                    val bmp = bitmap?.takeUnless { it.isRecycled } ?: return@IconButton
                                    haptics.actionClick()
                                    cropTargetBitmap = path to bmp
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Rounded.Crop, stringResource(R.string.scan_crop_action), tint = Color.White)
                            }
                            IconButton(
                                enabled = bitmap?.isRecycled == false && !busy && !externalBusy,
                                onClick = {
                                    val srcBmp = bitmap?.takeUnless { it.isRecycled } ?: return@IconButton
                                    haptics.selectionToggle()
                                    val nextFilter = when (activeFilter) {
                                        ScanFilter.ORIGINAL -> ScanFilter.CLEAN_DOCUMENT
                                        ScanFilter.CLEAN_DOCUMENT -> ScanFilter.CRISP_BW
                                        ScanFilter.CRISP_BW -> ScanFilter.MAGIC_COLOR
                                        ScanFilter.MAGIC_COLOR -> ScanFilter.ORIGINAL
                                    }
                                    activeFilter = nextFilter
                                    busy = true
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                val pageFile = File(path)
                                                val origFile = File("$path.orig")
                                                if (!origFile.isFile) pageFile.copyTo(origFile, overwrite = true)
                                                if (nextFilter == ScanFilter.ORIGINAL) {
                                                    origFile.copyTo(pageFile, overwrite = true)
                                                } else {
                                                    BitmapTransforms.decodeSampled(origFile, 12L * 1024 * 1024, maxEdge = 2400).use { rawDecoded ->
                                                        val enhanced = DocumentFilterEngine.apply(rawDecoded.bitmap, nextFilter)
                                                        try {
                                                            FileOutputStream(pageFile).use { out ->
                                                                enhanced.compress(Bitmap.CompressFormat.JPEG, 96, out)
                                                                out.fd.sync()
                                                            }
                                                        } finally {
                                                            if (enhanced !== rawDecoded.bitmap && !enhanced.isRecycled) enhanced.recycle()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        busy = false
                                        reviewEditEpoch++
                                    }
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    stringResource(R.string.scan_filter_clean),
                                    tint = if (activeFilter == ScanFilter.ORIGINAL) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                enabled = !busy && !externalBusy,
                                onClick = {
                                    haptics.actionClick()
                                    if (jpegOutput) saveAsJpegs() else saveAsPdf()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    if (jpegOutput) Icons.Rounded.Image else Icons.Rounded.PictureAsPdf,
                                    stringResource(if (jpegOutput) R.string.scan_save_jpg else R.string.ui_create_pdf),
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                enabled = !busy && !externalBusy,
                                onClick = {
                                    haptics.secondaryAction()
                                    pagePaths = pagePaths.filterNot { it == path }
                                    scope.launch(Dispatchers.IO) {
                                File(path).delete()
                                File("$path.orig").delete()
                            }
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        } else {
            if (permitted) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.matchParentSize(),
                    update = { capture.targetRotation = it.display?.rotation ?: Surface.ROTATION_0 }
                )
            }
            val guideColor = Color.White.copy(alpha = 0.35f)
            Canvas(Modifier.matchParentSize()) {
                val frameInset = 16.dp.toPx()
                inset(left = frameInset, right = frameInset, top = 64.dp.toPx(), bottom = 96.dp.toPx()) {
                    drawRoundRect(
                        color = guideColor,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { haptics.actionClick(); onBack() },
                    enabled = !busy && !externalBusy,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                if (pagePaths.isNotEmpty()) {
                    Surface(
                        onClick = { haptics.actionClick(); if (jpegOutput) saveAsJpegs() else saveAsPdf() },
                        enabled = !busy && !externalBusy,
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(46.dp).pressFeedback()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, stringResource(if (jpegOutput) R.string.scan_save_jpg else R.string.ui_create_pdf), tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            if (!permitted) {
                Box(Modifier.align(Alignment.Center).padding(horizontal = 24.dp)) {
                    QuietStateCard(
                        stringResource(R.string.studio_camera_permission),
                        stringResource(R.string.studio_camera_permission_body),
                        stringResource(R.string.studio_camera_enable),
                        { permission.launch(Manifest.permission.CAMERA) }
                    )
                }
            } else if (error) {
                Box(Modifier.align(Alignment.Center).padding(horizontal = 24.dp)) {
                    QuietStateCard(
                        stringResource(R.string.studio_scan_error),
                        stringResource(R.string.studio_scan_error_body),
                        stringResource(R.string.import_photo_gallery),
                        ::launchGallery,
                        isError = true,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (busy || externalBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    haptics.selectionToggle()
                                    flashMode = when (flashMode) {
                                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                        else -> ImageCapture.FLASH_MODE_AUTO
                                    }
                                },
                                shape = CircleShape,
                                color = if (flashMode == ImageCapture.FLASH_MODE_ON) Color.White else Color(0xFF23252A),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Bolt,
                                        contentDescription = stringResource(R.string.studio_camera_fallback),
                                        tint = if (flashMode == ImageCapture.FLASH_MODE_ON) Color.Black else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Surface(
                                onClick = { haptics.actionClick(); launchGallery() },
                                enabled = initialized && !busy && !externalBusy && pagePaths.size < 100,
                                shape = CircleShape,
                                color = Color(0xFF23252A),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Image,
                                        contentDescription = stringResource(R.string.import_photo_gallery),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                    val captureLabel = stringResource(R.string.studio_capture_page)
                    Surface(
                        onClick = ::captureNow,
                        enabled = cameraReady && initialized && !busy && !externalBusy && pagePaths.size < 100,
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(3.dp, Color.White),
                        modifier = Modifier.size(72.dp).pressFeedback().semantics { contentDescription = captureLabel }
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                val lastPath = pagePaths.lastOrNull()
                                if (lastPath != null) {
                                    val thumb by produceState<Bitmap?>(null, lastPath) {
                                        value = withContext(Dispatchers.IO) {
                                            try { BitmapTransforms.decodeSampled(File(lastPath), 4L * 1024 * 1024, maxEdge = 160).bitmap }
                                            catch (cancel: CancellationException) { throw cancel }
                                            catch (_: Exception) { null }
                                            catch (_: OutOfMemoryError) { null }
                                        }
                                    }
                                    DisposeOwnedPreviewBitmap(thumb)
                                    Box(
                                        Modifier
                                            .size(50.dp)
                                            .clickable { haptics.actionClick(); reviewIndex = pagePaths.lastIndex }
                                    ) {
                                        Surface(
                                            shape = QuietTheme.shapes.badge,
                                            color = Color(0xFF1C1E22),
                                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            thumb?.takeUnless { it.isRecycled }?.let { decoded ->
                                                val imageBitmap = remember(decoded) { decoded.asImageBitmap() }
                                                Image(imageBitmap, stringResource(R.string.studio_scan_page_count, pagePaths.size), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        ) {
                                            Text(
                                                text = pagePaths.size.toString(),
                                                style = QuietTheme.typography.badge,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (jpegOutput && pagePaths.isNotEmpty()) {
                                Surface(
                                    onClick = { haptics.actionClick(); saveAsJpegs() },
                                    enabled = !busy && !externalBusy,
                                    shape = QuietTheme.shapes.pill,
                                    color = Color(0xFF23252A),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                        Text("JPG", style = QuietTheme.typography.badge, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
