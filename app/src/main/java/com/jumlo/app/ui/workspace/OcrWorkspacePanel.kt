package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException

/** Real per-page embedded text/OCR preview; export continues through the validated job pipeline. */
@Composable
internal fun OcrWorkspacePanel(vm:AppViewModel,sourceId:String,onExport:()->Unit,exportEnabled:Boolean) {
    var page by rememberSaveable(sourceId) {mutableIntStateOf(1)}
    var attempts by rememberSaveable(sourceId) {mutableIntStateOf(0)}
    val clipboard=LocalClipboardManager.current
    val frame by produceState<Result<PreviewFrame>?>(null,sourceId,page) {
        value=null
        value=try {Result.success(vm.preview(sourceId,page))} catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    DisposeOwnedPreviewBitmap(frame?.getOrNull()?.bitmap)
    val recognized by produceState<Result<String>?>(null,sourceId,page,attempts) {
        value=null
        if(attempts>0) value=try {Result.success(vm.recognizePage(sourceId,page))} catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.studio_ocr_hint),style=QuietTheme.typography.metadata)
        OcrLanguageNotice()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            @Composable fun Page(modifier:Modifier) {
                Surface(modifier,shape=QuietTheme.shapes.thumbnail,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.height(340.dp),contentAlignment=Alignment.Center) {
                        frame?.getOrNull()?.let {Image(it.bitmap.asImageBitmap(),stringResource(R.string.jumlo_page_label,page),Modifier.fillMaxSize().testTag("ocr.page"),contentScale=ContentScale.Fit)}
                            ?: if(frame?.isFailure==true) Text(stringResource(R.string.msg_preview_unavailable)) else CircularProgressIndicator()
                    }
                }
            }
            @Composable fun TextPanel(modifier:Modifier) {
                Surface(modifier,shape=QuietTheme.shapes.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.heightIn(min=220.dp,max=340.dp).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.studio_ocr_text),style=QuietTheme.typography.metadataStrong)
                        when {
                            attempts==0->QuietStateCard(stringResource(R.string.studio_ocr_text),stringResource(R.string.studio_ocr_hint),stringResource(R.string.studio_ocr_recognize),{attempts++})
                            recognized==null->{LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.studio_ocr_wait))}
                            recognized?.isFailure==true->QuietStateCard(stringResource(userMessageResource(stableError(recognized!!.exceptionOrNull()!!))),
                                stringResource(R.string.rebuild_ocr_note),stringResource(R.string.interactive_preview_retry),{attempts++},isError=true)
                            recognized?.getOrNull().isNullOrBlank()->QuietStateCard(stringResource(R.string.studio_ocr_empty),stringResource(R.string.rebuild_ocr_note),stringResource(R.string.studio_ocr_recognize),{attempts++})
                            else->SelectionContainer {Text(recognized!!.getOrThrow(),Modifier.testTag("ocr.text"),style=QuietTheme.typography.bodyMedium)}
                        }
                    }
                }
            }
            if(maxWidth>=660.dp) Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {Page(Modifier.weight(1f));TextPanel(Modifier.weight(1f))}
            else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {Page(Modifier.fillMaxWidth());TextPanel(Modifier.fillMaxWidth())}
        }
        frame?.getOrNull()?.let {DocumentPageControls(page,it.count) {page=it}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled=!recognized?.getOrNull().isNullOrBlank(),onClick={
                try {clipboard.setText(AnnotatedString(recognized!!.getOrThrow()))} catch(_:RuntimeException) {vm.notifyMessage("CLIPBOARD_UNAVAILABLE")}
            },modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(stringResource(R.string.studio_ocr_copy))}
            FilledTonalButton(enabled=exportEnabled && !recognized?.getOrNull().isNullOrBlank(),onClick=onExport,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                Text(stringResource(R.string.studio_ocr_export))
            }
        }
    }
}

/** Explain the installed model's exact script limit without guessing the language from an empty scan. */
@Composable
internal fun OcrLanguageNotice() {
    Text(
        text = stringResource(R.string.ocr_scanned_arabic_limit),
        modifier = Modifier.fillMaxWidth().testTag("ocr.script-limitation"),
        style = QuietTheme.typography.metadataStrong,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
