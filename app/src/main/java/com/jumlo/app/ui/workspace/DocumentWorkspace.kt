package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.PdfCompressionPolicy
import com.jumlo.app.data.engine.PureDomainAlgorithms.formatBytes
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.model.SourceRef
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.CompressionProfile
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.viewer.PdfViewerScreen
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.jumlo.app.data.processing.PdfCompressSettings

@Composable internal fun borderlessFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,
    disabledIndicatorColor=Color.Transparent,errorIndicatorColor=Color.Transparent,
    focusedContainerColor=MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow)

@Composable internal fun ViewerWorkspace(vm:AppViewModel,sources:List<SourceRef>,onImport:()->Unit,onBack:()->Unit = {},onOpenFile:()->Unit = onImport) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val pdfs=sources.filter { it.mimeType=="application/pdf" }
    val source=pdfs.firstOrNull {it.id==selected} ?: pdfs.firstOrNull()
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        QuietTonalButton(stringResource(R.string.import_device_files),onImport,Modifier.fillMaxWidth().padding(horizontal=20.dp))
        if(pdfs.size>1) LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(pdfs,key={it.id}) { item -> QuietChip(item.id==source?.id,{selected=item.id},item.displayName) }
        }
        if(source==null) QuietStateCard(stringResource(R.string.ui_choose_inputs),stringResource(R.string.ui_choose_inputs_body),stringResource(R.string.import_device_files),onImport,modifier=Modifier.padding(20.dp))
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
                    error!=null -> Text(stringResource(userMessageResource(error!!)),modifier=Modifier.padding(24.dp))
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable internal fun CompressionEstimate(vm:AppViewModel,sourceId:String,form:WorkspaceDraft) {
    val presets = listOf(PdfCompressionPolicy.Preset.BEST_QUALITY, PdfCompressionPolicy.Preset.BALANCED, PdfCompressionPolicy.Preset.STRONG)
    val measured = remember(sourceId) { mutableStateMapOf<PdfCompressionPolicy.Preset,Result<CompressionProfile>>() }
    LaunchedEffect(sourceId) {
        for(preset in presets) {
            try {
                val result=vm.measuredCompression(sourceId,preset.dpi,preset.quality)
                measured[preset]=if(result?.measuredBytes!=null) Result.success(result) else Result.failure(IllegalStateException("PREVIEW_UNAVAILABLE"))
            } catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {measured[preset]=Result.failure(error)}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        presets.forEachIndexed {index,preset ->
            val selected=form.num2.isBlank() && draftNumber(form.num1,150)==preset.dpi && draftNumber(form.quality,85)==preset.quality
            val profile=measured[preset]?.getOrNull()
            Surface(onClick={vm.updateForm {it.copy(num1=preset.dpi.toString(),quality=preset.quality.toString(),num2="")}},
                shape=QuietTheme.shapes.card,
                color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier=Modifier.fillMaxWidth().testTag("compression.preset.$index")) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(listOf(R.string.studio_compress_low,R.string.studio_compress_medium,R.string.studio_compress_high)[index]),style=QuietTheme.typography.sectionTitle)
                            Text(stringResource(listOf(R.string.studio_compress_low_detail,R.string.studio_compress_medium_detail,R.string.studio_compress_high_detail)[index]),style=QuietTheme.typography.metadata)
                        }
                        RadioButton(selected=selected,onClick=null)
                    }
                    when {
                        profile!=null -> {
                            Text(stringResource(R.string.rebuild_compression_before,formatBytes(profile.originalBytes)),style=QuietTheme.typography.metadata)
                            Text(stringResource(R.string.studio_compression_after,formatBytes(profile.measuredBytes!!)),style=QuietTheme.typography.metadataStrong)
                        }
                        measured[preset]?.isFailure==true -> Text(stringResource(R.string.msg_preview_unavailable),style=QuietTheme.typography.metadata)
                        else -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.studio_compression_measuring),style=QuietTheme.typography.metadata) }
                    }
                }
            }
        }
        val custom=presets.none { form.num2.isBlank() && draftNumber(form.num1,150)==it.dpi && draftNumber(form.quality,85)==it.quality }
        if(custom) {
            val profile by produceState<Result<CompressionProfile>?>(null,sourceId,Triple(form.num1,form.quality,form.num2)) {
                value=null
                delay(350)
                value=try {
                    val exact=Json.decodeFromString<PdfCompressSettings>(form.executionSettings())
                    Result.success(requireNotNull(vm.measuredCompression(sourceId,exact.maxDpi,exact.jpegQuality,exact.targetBytes)))
                } catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
            }
            QuietCard {
                Text(stringResource(R.string.studio_compression_custom),style=QuietTheme.typography.metadataStrong)
                when {
                    profile==null -> {LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.studio_compression_measuring))}
                    profile?.getOrNull()?.measuredBytes!=null -> {
                        val value=profile!!.getOrThrow()
                        Text(stringResource(R.string.rebuild_compression_before,formatBytes(value.originalBytes)))
                        Text(stringResource(R.string.studio_compression_after,formatBytes(value.measuredBytes!!)))
                    }
                    else -> Text(stringResource(R.string.msg_preview_unavailable))
                }
            }
        }
        Text(stringResource(R.string.studio_compression_measured_note),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
