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
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import java.io.File

/** No app camera permission or intermediate capture screen is needed for the Play-services scanner. */
@Composable
internal fun SmartDocumentScanLauncher(onScannedPdf: (File) -> Unit, onBack: () -> Unit, externalBusy: Boolean = false) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence<Context>(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val scope = rememberCoroutineScope()
    var launched by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    var fallback by rememberSaveable { mutableStateOf(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)!=ConnectionResult.SUCCESS) }
    var active by remember { mutableStateOf(true) }
    val latestResult by rememberUpdatedState(onScannedPdf)
    val latestBack by rememberUpdatedState(onBack)
    val latestExternalBusy by rememberUpdatedState(externalBusy)
    DisposableEffect(Unit) { active = true; onDispose { active = false } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        launched = false
        busy = false
        if (latestExternalBusy) return@rememberLauncherForActivityResult
        if (activityResult.resultCode != Activity.RESULT_OK) {
            latestBack()
        } else {
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)?.pdf
            if (pdf == null || pdf.pageCount <= 0) error = true
            else {
                busy = true
                scope.launch {
                    var owned: File? = null
                    try {
                        owned = SmartScanResultStore.copyPdf(context, pdf.uri, pdf.pageCount)
                        latestResult(requireNotNull(owned))
                        owned = null // The ViewModel now owns import/publication and cleanup.
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { error = true }
                    catch (_: OutOfMemoryError) { error = true }
                    finally { owned?.delete(); busy = false }
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
            .setPageLimit(100)
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
            .addOnFailureListener { if (active) { busy = false; error = true; fallback = true } }
        } catch (_: Exception) { busy=false;error=true;fallback=true }
    }
    if(fallback) {
        CameraXScanFallback(onScannedPdf=onScannedPdf,onBack=onBack,externalBusy=externalBusy)
        return
    }
    LaunchedEffect(Unit) { if (!launched && !externalBusy) startScan() }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.scan_title), style = QuietTheme.typography.sectionTitle)
        Text(stringResource(if (error) R.string.scan_error_smart else R.string.scan_smart_hint),
            style = QuietTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy || externalBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        QuietPrimaryButton(stringResource(R.string.scan_smart_action), onClick = ::startScan,
            enabled = !busy && !externalBusy, modifier = Modifier.fillMaxWidth())
        QuietTonalButton(stringResource(R.string.action_close), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
