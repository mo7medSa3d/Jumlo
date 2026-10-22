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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.jumlo.app.R
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Primary Smart Scan engine powered by ML Kit Document Scanner (GmsDocumentScanner)
 * with multi-page capture, PDF + JPEG output, edge detection, auto-crop, filters,
 * page reordering, and gallery import enabled. Launches directly on screen entry.
 */
@Composable
internal fun SmartDocumentScanButton(
    enabled: Boolean,
    onCapturedBatch: (List<File>) -> Unit,
    onError: () -> Unit,
    autoLaunch: Boolean = true,
    onCapturedPdf: ((File) -> Unit)? = null,
    onCancelled: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence<Context>(context) { current ->
            (current as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    val latestBatch by rememberUpdatedState(onCapturedBatch)
    val latestPdf by rememberUpdatedState(onCapturedPdf)
    val latestError by rememberUpdatedState(onError)
    val latestCancelled by rememberUpdatedState(onCancelled)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val pdfResult = scanResult?.pdf
            val pages = scanResult?.pages.orEmpty()
            if (pdfResult != null && latestPdf != null) {
                busy = true
                scope.launch {
                    val pdfFile = File(context.cacheDir, "jumlo-smart-${UUID.randomUUID()}.pdf")
                    try {
                        withContext(Dispatchers.IO) {
                            val input = context.contentResolver.openInputStream(pdfResult.uri)
                                ?: throw IllegalStateException("SCAN_PDF_UNREADABLE")
                            input.use { source ->
                                FileOutputStream(pdfFile).use { output ->
                                    source.copyTo(output)
                                    output.fd.sync()
                                }
                            }
                            require(pdfFile.length() > 0L) { "SCAN_PDF_EMPTY" }
                        }
                        latestPdf?.invoke(pdfFile)
                    } catch (cancel: CancellationException) {
                        pdfFile.delete()
                        throw cancel
                    } catch (_: Exception) {
                        pdfFile.delete()
                        latestError()
                    } finally {
                        busy = false
                    }
                }
            } else if (pages.isEmpty()) {
                latestError()
            } else {
                busy = true
                scope.launch {
                    val created = mutableListOf<File>()
                    try {
                        withContext(Dispatchers.IO) {
                            pages.forEachIndexed { index, page ->
                                val file = File(context.cacheDir,
                                    "jumlo-smart-${UUID.randomUUID()}-${index + 1}.jpg")
                                try {
                                    val input = context.contentResolver.openInputStream(page.imageUri)
                                        ?: throw IllegalStateException("SCAN_PAGE_UNREADABLE")
                                    input.use { source ->
                                        FileOutputStream(file).use { output ->
                                            source.copyTo(output)
                                            output.fd.sync()
                                        }
                                    }
                                    require(file.length() > 0) { "SCAN_PAGE_EMPTY" }
                                    created.add(file)
                                } catch (error: Exception) {
                                    file.delete()
                                    throw error
                                }
                            }
                        }
                        latestBatch(created.toList())
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
        } else if (activityResult.resultCode == Activity.RESULT_CANCELED) {
            latestCancelled?.invoke()
        }
    }

    fun launchSmartScanner() {
        val host = activity
        if (host == null) {
            latestError()
            return
        }
        busy = true
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
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
                    busy = false
                }
            }
            .addOnFailureListener {
                busy = false
                latestError()
            }
    }

    LaunchedEffect(enabled, autoLaunch) {
        if (enabled && autoLaunch && !autoLaunched) {
            autoLaunched = true
            launchSmartScanner()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietPrimaryButton(
            text = stringResource(R.string.scan_smart_action),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !busy,
            onClick = { launchSmartScanner() }
        )
        Text(
            stringResource(R.string.scan_smart_hint),
            style = QuietTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
