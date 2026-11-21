package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.model.SourceRef
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.viewer.PdfViewerScreen
import kotlinx.coroutines.CancellationException

@Composable internal fun borderlessFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,
    disabledIndicatorColor=Color.Transparent,errorIndicatorColor=Color.Transparent,
    focusedContainerColor=MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow)

@Composable internal fun ViewerWorkspace(vm:AppViewModel,sources:List<SourceRef>,onImport:()->Unit,onBack:()->Unit = {},onOpenFile:()->Unit = onImport) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val pdfs=remember(sources) { sources.filter { it.mimeType=="application/pdf" } }
    val source=remember(pdfs,selected) { pdfs.firstOrNull {it.id==selected} ?: pdfs.firstOrNull() }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        QuietTonalButton(stringResource(R.string.import_pdf_single),onImport,Modifier.fillMaxWidth().padding(horizontal=20.dp))
        if(pdfs.size>1) LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(pdfs,key={it.id},contentType={"pdf-chip"}) { item -> QuietChip(item.id==source?.id,{selected=item.id},item.displayName) }
        }
        if(source==null) QuietStateCard(stringResource(R.string.ui_choose_pdf_single),stringResource(R.string.ui_choose_pdf_single_body),stringResource(R.string.import_pdf_single),onImport,modifier=Modifier.padding(20.dp))
        else {
            var uri by remember(source.id) { mutableStateOf<String?>(null) }
            var error by remember(source.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(source.id) {
                try { uri=vm.viewerUri(source.id) }
                catch(cancel:CancellationException) { throw cancel }
                catch(failure:Exception) { error=stableError(failure) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                when {
                    uri!=null -> PdfViewerScreen(uri!!,source.displayName,onBack,onOpenFile,{u,_->vm.importForPrint(com.jumlo.app.ui.intake.Intake(listOf(u),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,null))},Modifier.fillMaxSize())
                    error!=null -> Text(stringResource(userMessageResource(error!!)),modifier=Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}
