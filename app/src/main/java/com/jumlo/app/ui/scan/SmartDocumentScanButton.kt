package com.jumlo.app.ui.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.jumlo.app.R
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Play Services scanner is an optional, on-device quality path: auto capture,
 * page boundaries, filters and cleanup UI are owned by ML Kit. Models can
 * download once from Play Services. The existing CameraX scanner is the fallback.
 */
@Composable
internal fun SmartDocumentScanButton(
    enabled: Boolean,
    onCapturedBatch: (List<File>) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence<Context>(context) { current ->
            (current as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val latestBatch by rememberUpdatedState(onCapturedBatch)
    val latestError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult
                .fromActivityResultIntent(activityResult.data)?.pages.orEmpty()
            if (pages.isEmpty()) {
                latestError()
            } else {
                busy = true
                scope.launch {
                    val created=mutableListOf<File>()
                    try {
                        withContext(Dispatchers.IO) {
                            pages.forEachIndexed { index,page ->
                                val file=File(context.cacheDir,
                                    "jumlo-smart-${UUID.randomUUID()}-${index+1}.jpg")
                                try {
                                    val input=context.contentResolver.openInputStream(page.imageUri)
                                        ?: throw IllegalStateException("SCAN_PAGE_UNREADABLE")
                                    input.use { source ->
                                        FileOutputStream(file).use { output ->
                                            source.copyTo(output)
                                            output.fd.sync()
                                        }
                                    }
                                    require(file.length()>0) { "SCAN_PAGE_EMPTY" }
                                    created.add(file)
                                } catch (error:Exception) {
                                    file.delete()
                                    throw error
                                }
                            }
                        }
                        latestBatch(created.toList())
                        // The ViewModel now owns cleanup after importing these copies.
                    } catch (cancel: CancellationException) {
                        created.forEach { it.delete() }
                        throw cancel
                    } catch (_: Exception) {
                        created.forEach { it.delete() }
                        latestError()
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        QuietTonalButton(
            text=stringResource(R.string.scan_smart_action),
            modifier=Modifier.fillMaxWidth(),
            enabled=enabled && !busy,
            onClick={
                val host=activity
                if(host==null) {
                    latestError()
                } else {
                    busy=true
                    val options=GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setPageLimit(100)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(options)
                        .getStartScanIntent(host)
                        .addOnSuccessListener { sender ->
                            try {
                                launcher.launch(IntentSenderRequest.Builder(sender).build())
                            } catch (_: Exception) {
                                latestError()
                            } finally {
                                busy=false
                            }
                        }
                        .addOnFailureListener {
                            busy=false
                            latestError()
                        }
                }
            }
        )
        Text(stringResource(R.string.scan_smart_hint),
            style=QuietTheme.typography.metadata,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
