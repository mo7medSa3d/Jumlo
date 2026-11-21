package com.jumlo.app.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.scan.DocumentFilterEngine
import com.jumlo.app.data.scan.IdCardA4Composer
import com.jumlo.app.data.scan.ScanFilter
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun IdCardScanFlow(
    onPdfReady: (File) -> Unit,
    onBack: () -> Unit,
    externalBusy: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    var frontBmp by remember { mutableStateOf<Bitmap?>(null) }
    var backBmp by remember { mutableStateOf<Bitmap?>(null) }
    var filter by remember { mutableStateOf(ScanFilter.CLEAN_DOCUMENT) }
    var busy by remember { mutableStateOf(false) }
    var pickingFront by remember { mutableStateOf(true) }

    fun decodeUri(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }.getOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeUri(uri) }
                if (bmp != null) {
                    if (pickingFront) { frontBmp?.recycle(); frontBmp = bmp }
                    else { backBmp?.recycle(); backBmp = bmp }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { frontBmp?.recycle(); backBmp?.recycle() }
    }

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
                pickingFront = true; picker.launch("image/*")
            }
            CardSlot(stringResource(R.string.scan_id_back), backBmp, filter, Modifier.weight(1f)) {
                pickingFront = false; picker.launch("image/*")
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filterChoices, key = { it.first.name }) { (f, labelRes) ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    shape = QuietTheme.shapes.pill,
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
    val filtered = remember(bmp, filter) { bmp?.let { runCatching { DocumentFilterEngine.apply(it, filter) }.getOrNull() } }
    Surface(
        onClick = onPick,
        shape = QuietTheme.shapes.card,
        color = pillars.cardSurface,
        border = BorderStroke(1.dp, pillars.cardBorder),
        modifier = modifier.height(160.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (filtered != null) {
                Image(filtered.asImageBitmap(), label, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            } else {
                QuietTonalButton(label, onPick)
            }
        }
    }
}
