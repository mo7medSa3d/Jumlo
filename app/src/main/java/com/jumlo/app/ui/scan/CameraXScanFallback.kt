package com.jumlo.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jumlo.app.R
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.pdf.SmartScanResultStore
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import com.jumlo.app.data.processing.BitmapTransforms
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateBefore
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateNext
import com.jumlo.app.ui.icons.rounded.Delete
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Lifecycle-bound camera is created only after the primary Play-services scanner is unavailable. */
@Composable
internal fun CameraXScanFallback(onScannedPdf:(File)->Unit,onBack:()->Unit,externalBusy:Boolean) {
    val context=LocalContext.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    val sessionId=rememberSaveable {UUID.randomUUID().toString()}
    val sessionDirectory=remember(sessionId) {File(context.cacheDir,"camera-scan-$sessionId")}
    var pagePaths by rememberSaveable(sessionId) {mutableStateOf(listOf<String>())}
    var initialized by remember {mutableStateOf(false)}
    var permitted by remember {mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    var cameraReady by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by rememberSaveable {mutableStateOf(false)}
    var active by remember {mutableStateOf(true)}
    val latestResult by rememberUpdatedState(onScannedPdf)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permitted=granted }
    val previewView=remember {PreviewView(context).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FIT_CENTER}}
    @Suppress("DEPRECATION")
    val capture=remember {ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetResolution(Size(1600,2200)).build()}
    val mainExecutor=remember {ContextCompat.getMainExecutor(context)}
    LaunchedEffect(sessionId) {
        try {
            withContext(Dispatchers.IO) {check(sessionDirectory.mkdirs() || sessionDirectory.isDirectory)}
            initialized=true
        } catch(cancel:CancellationException) {throw cancel} catch(_:Exception) {error=true}
    }
    DisposableEffect(Unit) {active=true;onDispose {active=false}}
    DisposableEffect(permitted,lifecycleOwner) {
        var provider:ProcessCameraProvider?=null
        var preview:Preview?=null
        var disposed=false
        cameraReady=false
        if(permitted) {
            val future=ProcessCameraProvider.getInstance(context)
            future.addListener({
                if(!disposed) {
                    try {
                        val cameraProvider=future.get()
                        provider=cameraProvider
                        val selector=if(cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        val cameraPreview=Preview.Builder().build().also {it.setSurfaceProvider(previewView.surfaceProvider)}
                        preview=cameraPreview
                        capture.targetRotation=previewView.display?.rotation ?: Surface.ROTATION_0
                        cameraProvider.bindToLifecycle(lifecycleOwner,selector,cameraPreview,capture)
                        cameraReady=true
                    } catch(_:Exception) {error=true;cameraReady=false}
                }
            },mainExecutor)
        }
        onDispose {disposed=true;preview?.let {provider?.unbind(it,capture)};cameraReady=false}
    }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) {uris ->
        if(uris.isNotEmpty() && !busy && !externalBusy) {
            busy=true;error=false
            scope.launch {
                val owned=mutableListOf<File>()
                try {
                    val files=withContext(Dispatchers.IO) {
                        check(pagePaths.size+uris.size<=100)
                        uris.map {uri ->
                            currentCoroutineContext().ensureActive()
                            val target=File(sessionDirectory,"page-${UUID.randomUUID()}.jpg").also {owned+=it}
                            context.contentResolver.openInputStream(uri)?.use {input ->
                                target.outputStream().use {output ->
                                    val buffer=ByteArray(64*1024)
                                    var total=0L
                                    while(true) {
                                        currentCoroutineContext().ensureActive()
                                        val read=input.read(buffer);if(read<0) break
                                        total+=read;check(total<=100L*1024*1024)
                                        check(context.cacheDir.usableSpace>=ResourceBudgetPolicy.minFreeBytes)
                                        output.write(buffer,0,read)
                                    }
                                    output.fd.sync()
                                }
                            } ?: kotlin.error("SCAN_IMAGE_UNREADABLE")
                            BitmapTransforms.inspect(target)
                            target.path
                        }
                    }
                    pagePaths=pagePaths+files
                    owned.clear()
                } catch(cancel:CancellationException) {throw cancel}
                catch(_:Exception) {error=true}
                finally {
                    withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO) {owned.forEach(File::delete)}
                    busy=false
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().testTag("scan.cameraFallback")) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.studio_camera_fallback),style=QuietTheme.typography.sectionTitle)
                Text(stringResource(R.string.studio_camera_fallback_note),style=QuietTheme.typography.metadata)
            }
            item {
                if(permitted) AndroidView(factory={previewView},modifier=Modifier.fillMaxWidth().height(340.dp),
                    update={capture.targetRotation=it.display?.rotation ?: Surface.ROTATION_0})
                else QuietStateCard(stringResource(R.string.studio_camera_permission),stringResource(R.string.studio_camera_permission_body),
                    stringResource(R.string.studio_camera_enable),{permission.launch(Manifest.permission.CAMERA)})
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    QuietPrimaryButton(stringResource(R.string.studio_capture_page),modifier=Modifier.weight(1f),enabled=cameraReady && initialized && !busy && !externalBusy && pagePaths.size<100,
                        onClick={
                            busy=true;error=false
                            capture.targetRotation=previewView.display?.rotation ?: Surface.ROTATION_0
                            val file=File(sessionDirectory,"page-${UUID.randomUUID()}.jpg")
                            try { capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),mainExecutor,object:ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result:ImageCapture.OutputFileResults) {
                                    if(active) {pagePaths=pagePaths+file.path;busy=false}
                                    else scope.launch(Dispatchers.IO) {file.delete()}
                                }
                                override fun onError(exception:ImageCaptureException) {
                                    if(active) {busy=false;error=true}
                                    scope.launch(Dispatchers.IO) {file.delete()}
                                }
                            }) } catch(_:Exception) {
                                busy=false;error=true
                                scope.launch(Dispatchers.IO) {file.delete()}
                            }
                        })
                    QuietTonalButton(stringResource(R.string.import_photo_gallery),modifier=Modifier.weight(1f),enabled=initialized && !busy && !externalBusy && pagePaths.size<100,
                        onClick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})
                }
            }
            item {
                Text(stringResource(R.string.studio_scan_page_count,pagePaths.size),style=QuietTheme.typography.metadataStrong)
                if(pagePaths.isEmpty()) QuietStateCard(stringResource(R.string.studio_scan_empty),stringResource(R.string.studio_scan_empty_body),
                    stringResource(R.string.import_photo_gallery),{gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})
                else LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(pagePaths,key={_,path->path}) {index,path ->
                        val bitmap by produceState<Bitmap?>(null,path) {
                            value=withContext(Dispatchers.IO) {
                                runCatching {
                                    BitmapTransforms.decodeSampled(File(path),4L*1024*1024,maxEdge=320).bitmap
                                }.getOrNull()
                            }
                        }
                        // The decoded Bitmap is native memory. Release it whenever this lazy
                        // item leaves composition (scroll, delete, reorder, or scanner exit).
                        DisposeOwnedPreviewBitmap(bitmap)
                        Column(Modifier.width(144.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Surface(shape=QuietTheme.shapes.thumbnail,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                                Box(Modifier.fillMaxWidth().height(150.dp),contentAlignment=Alignment.Center) {
                                    bitmap?.let {Image(it.asImageBitmap(),stringResource(R.string.jumlo_page_label,index+1),Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
                                }
                            }
                            Text(stringResource(R.string.jumlo_page_label,index+1),style=QuietTheme.typography.metadata)
                            Row {
                                IconButton(enabled=index>0 && !busy,onClick={pagePaths=pagePaths.toMutableList().apply {add(index-1,removeAt(index))}}) {
                                    Icon(Icons.AutoMirrored.Rounded.NavigateBefore,stringResource(R.string.jumlo_move_earlier))
                                }
                                IconButton(enabled=!busy,onClick={
                                    pagePaths=pagePaths.filterNot {it==path}
                                    scope.launch(Dispatchers.IO) {File(path).delete()}
                                }) {Icon(Icons.Rounded.Delete,stringResource(R.string.action_delete))}
                                IconButton(enabled=index<pagePaths.lastIndex && !busy,onClick={pagePaths=pagePaths.toMutableList().apply {add(index+1,removeAt(index))}}) {
                                    Icon(Icons.AutoMirrored.Rounded.NavigateNext,stringResource(R.string.jumlo_move_later))
                                }
                            }
                        }
                    }
                }
            }
            if(error) item {
                QuietStateCard(stringResource(R.string.studio_scan_error),stringResource(R.string.studio_scan_error_body),
                    stringResource(R.string.import_photo_gallery),{gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},isError=true)
            }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(busy || externalBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            QuietPrimaryButton(stringResource(R.string.ui_create_pdf),enabled=pagePaths.isNotEmpty() && !busy && !externalBusy,modifier=Modifier.fillMaxWidth(),onClick={
                val pages=pagePaths.map(::File)
                busy=true;error=false
                scope.launch {
                    var owned:File?=null
                    try {
                        owned=SmartScanResultStore.createPdfFromImages(context,pages)
                        if(active) {latestResult(requireNotNull(owned));owned=null}
                    } catch(cancel:CancellationException) {throw cancel}
                    catch(_:Exception) {error=true}
                    finally {
                        withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO) {owned?.delete()}
                        busy=false
                    }
                }
            })
            TextButton(onClick=onBack,enabled=!busy && !externalBusy,modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.action_close))}
        }
    }
}
