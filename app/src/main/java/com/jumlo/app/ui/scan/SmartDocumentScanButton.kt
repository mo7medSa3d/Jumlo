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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.jumlo.app.R
import com.jumlo.app.data.pdf.SmartScanResultStore
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** No app camera permission or intermediate capture screen is needed for the Play-services scanner.
 *  jpegOutput=true delivers the scanner's JPEG pages via onScannedJpegs instead of
 *  the PDF; when the scanner returns no pages, the PDF path is used as a fallback
 *  so a scan never fails solely because of the requested format. */
@Composable
internal fun SmartDocumentScanLauncher(
    onScannedPdf: (File) -> Unit, onBack: () -> Unit, externalBusy: Boolean = false,
    jpegOutput: Boolean = false, onScannedJpegs: (List<File>) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence<Context>(context) { current -> (current as? ContextWrapper)?.baseContext?.takeUnless { it === current } }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val scope = rememberCoroutineScope()
    var launched by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    val playServicesAvailable = remember(context) { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==ConnectionResult.SUCCESS }
    var failures by rememberSaveable { mutableIntStateOf(0) }
    var fallback by rememberSaveable { mutableStateOf(!playServicesAvailable) }
    var active by remember { mutableStateOf(true) }
    val latestResult by rememberUpdatedState(onScannedPdf)
    val latestJpegs by rememberUpdatedState(onScannedJpegs)
    val latestBack by rememberUpdatedState(onBack)
    val latestExternalBusy by rememberUpdatedState(externalBusy)
    DisposableEffect(Unit) { active = true; onDispose { active = false } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        launched = false
        busy = false
        // Never silently discard a completed scan result; deliver it cleanly.
        if (activityResult.resultCode != Activity.RESULT_OK) {
            latestBack()
        } else {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val pdf = scanResult?.pdf
            val jpegUris = scanResult?.pages?.mapNotNull { it.imageUri }.orEmpty()
            if (jpegOutput && jpegUris.isNotEmpty()) {
                busy = true
                scope.launch {
                    var owned: List<File>? = null
                    try {
                        owned = SmartScanResultStore.copyJpegPages(context, jpegUris)
                        if (active) {
                            latestJpegs(requireNotNull(owned))
                            owned = null // The ViewModel now owns publication and cleanup.
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { error = true }
                    catch (_: OutOfMemoryError) { error = true }
                    finally {
                        withContext(NonCancellable + Dispatchers.IO) { owned?.forEach { it.delete() } }
                        busy = false
                    }
                }
            } else if (!jpegOutput && jpegUris.isNotEmpty()) {
                // Build PDF directly from the scanner's full-resolution cropped JPEG pages instead of Play Services' downscaled PDF output.
                busy = true
                scope.launch {
                    var jpegs: List<File>? = null
                    var ownedPdf: File? = null
                    try {
                        jpegs = SmartScanResultStore.copyJpegPages(context, jpegUris)
                        ownedPdf = SmartScanResultStore.createPdfFromImages(context, jpegs)
                        if (active) {
                            latestResult(requireNotNull(ownedPdf))
                            ownedPdf = null
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) {
                        if (pdf != null && pdf.pageCount > 0) {
                            runCatching { SmartScanResultStore.copyPdf(context, pdf.uri, pdf.pageCount) }
                                .onSuccess { fallbackPdf ->
                                    if (active) latestResult(fallbackPdf) else fallbackPdf.delete()
                                }
                                .onFailure { error = true }
                        } else {
                            error = true
                        }
                    } catch (_: OutOfMemoryError) {
                        error = true
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) {
                            jpegs?.forEach { it.delete() }
                            ownedPdf?.delete()
                        }
                        busy = false
                    }
                }
            } else if (pdf == null || pdf.pageCount <= 0) error = true
            else {
                busy = true
                scope.launch {
                    var owned: File? = null
                    try {
                        owned = SmartScanResultStore.copyPdf(context, pdf.uri, pdf.pageCount)
                        if (active) {
                            latestResult(requireNotNull(owned))
                            owned = null // The ViewModel now owns import/publication and cleanup.
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { error = true }
                    catch (_: OutOfMemoryError) { error = true }
                    finally {
                        withContext(NonCancellable + Dispatchers.IO) { owned?.delete() }
                        busy = false
                    }
                }
            }
        }
    }
    fun startScan() {
        if (busy || externalBusy || launched) return
        val host = activity
        if (host == null) { error = true; return }
        busy = true
        error = false
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF,GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        try { GmsDocumentScanning.getClient(options).getStartScanIntent(host)
            .addOnSuccessListener { sender ->
                if (!active || latestExternalBusy) { busy = false; return@addOnSuccessListener }
                try {
                    launched = true
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                } catch (_: Exception) { launched = false; busy = false; error = true }
            }
            .addOnFailureListener {
                // Once the user chose the basic camera, no late result may flip fallback back.
                if (!active) return@addOnFailureListener
                failures++
                fallback = ScannerFallbackPolicy.shouldFallback(playServicesAvailable, failures)
                busy = false; error = true
            }
        } catch (_: Exception) {
            failures++
            fallback = ScannerFallbackPolicy.shouldFallback(playServicesAvailable, failures)
            busy=false;error=true
        }
    }
    if(fallback) {
        CameraXScanFallback(onScannedPdf=onScannedPdf,onScannedJpegs=onScannedJpegs,jpegOutput=jpegOutput,onBack=onBack,externalBusy=externalBusy)
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, externalBusy) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!launched && !externalBusy && !error) startScan()
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.scan_title), style = QuietTheme.typography.sectionTitle, modifier = Modifier.semantics { heading() })
        Text(stringResource(if (error) R.string.scan_error_smart else R.string.scan_smart_hint),
            style = QuietTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { if (error) liveRegion = LiveRegionMode.Polite })
        if (busy || externalBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error && !fallback) {
            QuietPrimaryButton(stringResource(R.string.scan_smart_retry), onClick = ::startScan,
                enabled = !busy && !externalBusy, modifier = Modifier.fillMaxWidth())
            QuietTonalButton(stringResource(R.string.scan_use_basic_camera), onClick = { active = false; fallback = true },
                modifier = Modifier.fillMaxWidth())
        }
        if (!error) {
            QuietPrimaryButton(stringResource(R.string.scan_smart_action), onClick = ::startScan,
                enabled = !busy && !externalBusy, modifier = Modifier.fillMaxWidth())
        }
        QuietTonalButton(stringResource(R.string.action_close), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
