package com.jumlo.app.ui.workspace

import com.jumlo.app.ui.icons.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.core.PdfCompressionPolicy
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.processing.BatchRenameSettings
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.CompressionProfile
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private data class RenamePreviewInput(val names:List<String>,val settings:BatchRenameSettings)
private data class RenamePreviewState(val input:RenamePreviewInput,val result:Result<List<String>>)

/** Retains the original public call shape, including positional and trailing-lambda callers. */
@Composable fun WorkspaceScreen(
    vm:AppViewModel,form:WorkspaceDraft,imported:List<SourceRef>,busy:Boolean,urls:String,reduceMotion:Boolean,
    onImport:()->Unit,onPhotos:()->Unit,onGoogleSignIn:()->Unit,onExecute:()->Unit
) = WorkspaceScreen(vm,form,imported,busy,urls,reduceMotion,onImport,onPhotos,onGoogleSignIn,
    onPrintPages=null,onExecute=onExecute)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkspaceScreen(
    vm:AppViewModel,form:WorkspaceDraft,imported:List<SourceRef>,busy:Boolean,urls:String,reduceMotion:Boolean,
    onImport:()->Unit,onPhotos:()->Unit,onGoogleSignIn:()->Unit,
    onPrintPages:((String,List<Pair<Int,Int>>)->Unit)?,onExecute:()->Unit
) {
    val tool=form.toolId
    val processingPresets by vm.processingPresets.collectAsStateWithLifecycle()
    val download=tool.startsWith("dl_")
    val driveImport=tool=="file_drive" && !form.flag1
    val viewer=tool=="pdf_01"
    val hasAdvanced=!viewer
    var previewId by rememberSaveable {mutableStateOf<String?>(null)}
    var advanced by rememberSaveable {mutableStateOf(false)}
    var renamePreviewOpen by rememberSaveable {mutableStateOf(false)}

    // Populate smart defaults on tool entry so no manual typing is required on the main workspace.
    val defaultWatermark = stringResource(R.string.watermark_preset_confidential)
    LaunchedEffect(tool) {
        when(tool) {
            "pdf_stamp" -> if(form.text1.isBlank() && !form.flag1) vm.updateForm { it.copy(text1=defaultWatermark) }
            "img_watermark" -> if(form.text1.isBlank() && !form.flag1) vm.updateForm { it.copy(text1="Jumlo") }
            // Only the text-carrying kinds get a body; a highlight is just a region.
            "pdf_annotate" -> if(com.jumlo.app.core.PdfAnnotationKind.from(form.text2).carriesText && form.text1.isBlank())
                vm.updateForm { it.copy(text1="Jumlo", num1=it.num1.ifBlank{"1"}) }
            "file_batch_rename" -> if(form.text1.isBlank()) vm.updateForm { it.copy(text1="{name}_{n}", num1=it.num1.ifBlank{"1"}) }
            "img_02" -> if(form.percentage.isBlank() && form.longestEdge.isBlank() && form.width=="1600" && form.height=="1600") {
                vm.updateForm { it.copy(percentage="75") }
            }
        }
    }

    val queue=remember(form.sourceIds,imported) {val indexed=imported.associateBy {it.id};form.sourceIds.mapNotNull(indexed::get)}
    var selectedSourceId by rememberSaveable(tool) { mutableStateOf<String?>(null) }
    val preview=queue.firstOrNull {it.id==selectedSourceId && (it.mimeType?.startsWith("image/")==true || it.mimeType=="application/pdf")}
        ?: queue.firstOrNull {it.mimeType?.startsWith("image/")==true || it.mimeType=="application/pdf"}
    var formFieldsReady by remember(tool,form.sourceIds) {mutableStateOf(tool!="pdf_forms")}
    val visualPageSelection=tool in setOf("pdf_04","pdf_05","pdf_06","pdf_07","pdf_crop","pdf_stamp","pdf_ocr")
    val editorSource=if(tool in setOf("pdf_image","pdf_signature"))
        queue.firstOrNull { it.mimeType=="application/pdf" } else preview
    val overlayImage=if(tool in setOf("pdf_image","pdf_signature"))
        queue.firstOrNull { it.mimeType?.startsWith("image/")==true } else null
    val minimum=if(tool in setOf("pdf_03","pdf_compare","img_compare","pdf_signature","pdf_image","img_join","img_stitch") ||
        (tool=="img_watermark" && form.flag1)) 2 else 1
    val inputMismatch=remember(tool,queue) { queue.any {source->
        val mime=source.mimeType
        when {
            tool=="pdf_image" || tool=="pdf_signature" ->
                queue.size!=2 || queue.count { it.mimeType=="application/pdf" }!=1 ||
                    queue.count { it.mimeType?.startsWith("image/")==true }!=1
            mime==null || mime=="application/octet-stream" -> false
            tool=="pdf_02" || tool.startsWith("img_") -> !mime.startsWith("image/")
            tool.startsWith("pdf_") -> mime!="application/pdf"
            tool=="file_08" -> mime !in setOf("application/zip","application/x-zip-compressed")
            else -> false
        }
    }}
    val settingsError=remember(form,urls) {
        if(viewer) null else runCatching {
            form.executionSettings(if(download) "validation-only" else null)
            if(download && urls.isNotBlank()) {
                val lines=urls.lineSequence().map {it.trim()}.filter {it.isNotEmpty()}.toList()
                require(lines.size<=100 && (tool=="dl_03" || lines.size==1)) {"INVALID_DOWNLOAD_LIST"}
                lines.forEach { line ->
                    if(com.jumlo.app.core.DownloadLinkPolicy.requiresMediaExtractor(line)) {
                        throw com.jumlo.app.data.jobs.OperationFailure("MEDIA_SELECTION_REQUIRED")
                    }
                    com.jumlo.app.core.HttpPolicy.url(
                        com.jumlo.app.core.DownloadLinkPolicy.resolveDirectUrl(line),
                        form.cleartextConsent
                    )
                }
            }
        }.exceptionOrNull()?.let(::stableError)
    }
    val countValid=when(tool) {
        "pdf_07","file_08","pdf_04","pdf_05","pdf_06","pdf_crop",
        "pdf_compress","pdf_stamp","pdf_annotate","pdf_forms","pdf_clean","pdf_protect","pdf_ocr","pdf_to_docx","pdf_to_pptx" -> queue.size==1
        "pdf_image","pdf_signature","pdf_compare","img_compare" -> queue.size==2
        "pdf_metadata" -> queue.size in 1..2
        "file_encrypted_zip" -> !form.flag1 || queue.size==1
        else -> true
    }
    val renameInput=remember(form,queue) {
        if(tool=="file_batch_rename") runCatching {
            RenamePreviewInput(queue.map {it.displayName},Json.decodeFromString<BatchRenameSettings>(form.executionSettings()))
        }.getOrNull() else null
    }
    val computedRename by produceState<RenamePreviewState?>(initialValue=null,key1=renameInput) {
        value=null
        renameInput?.let {input ->
            value=withContext(Dispatchers.Default) {
                RenamePreviewState(input,runCatching {PureDomainAlgorithms.batchRenamePreview(input.names,input.settings.pattern,input.settings.startAt)})
            }
        }
    }
    // Never enable a new pattern using the previous pattern's successful preview.
    val renamePreview=computedRename?.takeIf {it.input==renameInput}?.result
    val proposedNames=renamePreview?.getOrNull()
    val ready=CapabilityRegistry.enabled(tool) && !busy && !inputMismatch && settingsError==null && countValid && formFieldsReady &&
        (tool!="file_encrypted_zip" || form.secret1.isNotEmpty()) &&
        (tool!="pdf_protect" || if(form.flag1) form.secret1.isNotEmpty() else form.secret2.isNotEmpty()) &&
        (tool!="pdf_05" || !form.flag2 || form.flag1 || form.pages.isNotBlank()) &&
        (tool!="file_batch_rename" || proposedNames!=null) &&
        (tool!="pdf_annotate" || !com.jumlo.app.core.PdfAnnotationKind.from(form.text2).isFreehand || form.effectiveInkStrokes().isNotEmpty()) &&
        (tool=="webpage_pdf" || driveImport || if(download) urls.isNotBlank() else queue.size>=minimum)
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=840.dp).align(Alignment.CenterHorizontally),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(!CapabilityRegistry.enabled(tool)) item {Text(stringResource(R.string.ui_tool_unavailable),color=MaterialTheme.colorScheme.error)}
            if(!download) {
                if(!viewer && !driveImport) item("processing-presets") {
                    ProcessingPresetsPanel(form,processingPresets,
                        onSave=vm::saveProcessingPreset,
                        onApply=vm::applyProcessingPreset,
                        onDelete=vm::deleteProcessingPreset)
                }
                item(key="inputs") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        QuietTonalButton(stringResource(R.string.import_device_files),onImport,Modifier.weight(1f))
                        if(tool in setOf("pdf_02","pdf_image","pdf_signature") || tool.startsWith("img_"))
                            QuietTonalButton(stringResource(R.string.import_photo_gallery),onPhotos,Modifier.weight(1f))
                    }
                }
                if(queue.isEmpty()) item {QuietStateCard(stringResource(R.string.ui_choose_inputs),stringResource(R.string.ui_choose_inputs_body))}
                if(queue.size>1) item("visual-source-order") {
                    SourceOrderStrip(vm,queue,preview?.id,
                        if(tool=="img_watermark" && form.flag1) queue.getOrNull(1)?.id else null,
                        onSelect={selectedSourceId=it},onMove=vm::moveSource)
                }
                if(preview!=null && tool !in setOf(
                    "img_03","img_02","img_adjust","img_join","img_stitch","img_watermark","pdf_crop","pdf_redact","pdf_signature","pdf_annotate","pdf_image","pdf_stamp","pdf_02"
                ) && !(tool in setOf("pdf_04", "pdf_05", "pdf_06") && queue.size==1))
                    item(key="preview-${preview.id}") {
                        InputPreview(vm,preview.id,reduceMotion,isFullViewer=viewer,
                            adjustment=if(tool=="img_adjust") form else null,
                            raster=if(tool=="pdf_07") form else null)
                    }
                if(visualPageSelection && queue.size==1 && queue.single().mimeType=="application/pdf")
                    item(key="pdf-visual-editor-${queue.single().id}") { PdfPageEditor(vm,queue.single().id,form,vm::updateForm,
                        onPrint={pages->onPrintPages?.invoke(queue.single().id,pages)},printEnabled=onPrintPages!=null) }
                if(editorSource!=null && tool in setOf(
                    "img_03","pdf_crop","pdf_signature","pdf_redact","pdf_annotate","pdf_image"
                )) {
                    item(key="visual-geometry-editor") {
                        VisualDocumentEditor(vm,editorSource.id,form,vm::updateForm,overlayImage?.id)
                    }
                }
                if(tool=="pdf_stamp" && editorSource?.mimeType=="application/pdf") {
                    item(key="visual-watermark-editor") {
                        VisualWatermarkEditor(vm,editorSource.id,form,vm::updateForm)
                    }
                }
                if(tool in setOf("pdf_image","pdf_signature") && queue.size<2) {
                    item(key="image-overlay-hint") {
                        Text(stringResource(R.string.visual_two_sources),
                            style=QuietTheme.typography.metadata,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(tool in setOf("img_02","img_adjust","img_watermark","img_join","img_stitch") && preview!=null && !inputMismatch) item("transformed-image-preview") {
                    val ids=when {
                        tool in setOf("img_join","img_stitch") -> queue.map {it.id}
                        tool=="img_watermark" && form.flag1 -> listOfNotNull(
                            preview.takeUnless {it.id==queue.getOrNull(1)?.id}?.id ?: queue.firstOrNull()?.id,
                            queue.getOrNull(1)?.id)
                        else -> listOf(preview.id)
                    }
                    ImageResultPreview(vm,ids,form)
                }
                if(tool.startsWith("img_") || tool in setOf("pdf_compress","pdf_07","pdf_02","pdf_to_docx","pdf_to_pptx")) {
                    item(key="visual-intuitive-controls") {
                        VisualImageSettings(form,vm::updateForm)
                    }
                }
                if(tool=="pdf_compress" && queue.size==1) {
                    item(key="compress-live-size-preview") {
                        val sourceId=queue.single().id
                        var profile by remember(sourceId) { mutableStateOf<CompressionProfile?>(null) }
                        LaunchedEffect(sourceId) { profile=vm.compressionProfile(sourceId) }
                        profile?.let { prof ->
                            val dpi=form.num1.toIntOrNull()?:180
                            val quality=form.quality.toIntOrNull()?:75
                            val est=PdfCompressionPolicy.estimateBytes(prof.originalBytes,prof.imageBytes,dpi,quality)
                            val saving=PdfCompressionPolicy.estimatedSavingPercent(prof.originalBytes,est)
                            Surface(
                                shape=RoundedCornerShape(20.dp),
                                color=MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation=2.dp,
                                modifier=Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier=Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text=stringResource(R.string.ui_size_before_after,formatBytes(prof.originalBytes),formatBytes(est),saving),
                                            style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Bold),
                                            color=MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text=stringResource(R.string.field_compress_estimate_note),
                                            style=QuietTheme.typography.metadata,
                                            color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.85f)
                                        )
                                    }
                                    if(saving>0) {
                                        QuietBadge(
                                            text="-$saving%",
                                            containerColor=MaterialTheme.colorScheme.primary,
                                            contentColor=MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if(tool=="pdf_02" && preview!=null) item("paper-preview") {
                    ImagePdfPreview(vm,preview.id,form,vm::updateForm)
                }
                if(tool=="pdf_forms" && queue.size==1 && !inputMismatch) item("pdf-form-fields") {
                    PdfFormEditor(vm,queue.single().id,form,vm::updateForm,onReady={formFieldsReady=it})
                }
                if(tool in setOf("file_07","file_encrypted_zip","file_batch_rename","sha256","pdf_protect","pdf_clean","pdf_metadata","webpage_pdf","img_exif")) item("primary-tool-settings") {
                    WorkspaceFields(vm,form,vm::updateForm,onGoogleSignIn)
                }
                if(queue.isNotEmpty()) item {Text(stringResource(R.string.ui_input_count,queue.size),style=QuietTheme.typography.metadataStrong)}
                if(queue.isNotEmpty() && !countValid) item(key="source-count-help") {
                    Text(stringResource(R.string.watermark_correct_source_count),
                        style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.error)
                }
                itemsIndexed(queue,key={_,it->it.id},contentType={_,_->"input"}) {index,source->
                    val detail=proposedNames?.getOrNull(index)?.let {stringResource(R.string.ui_rename_preview_name,it)} ?: formatBytes(source.sizeBytes)
                    com.jumlo.app.ui.components.FileMenuRow(source.displayName,detail,if(source.mimeType?.startsWith("image/")==true || source.mimeType=="application/pdf") ({previewId=source.id}) else null) {close->
                        if(source.mimeType?.startsWith("image/")==true || source.mimeType=="application/pdf") DropdownMenuItem(text={Text(stringResource(R.string.action_preview))},onClick={close();previewId=source.id})
                        if(index>0) DropdownMenuItem(text={Text(stringResource(R.string.action_move_up))},onClick={close();vm.moveSource(index,-1)})
                        if(index<queue.lastIndex) DropdownMenuItem(text={Text(stringResource(R.string.action_move_down))},onClick={close();vm.moveSource(index,1)})
                        DropdownMenuItem(text={Text(stringResource(R.string.action_remove))},onClick={close();vm.removeSource(source.id)})
                    }
                }
            } else item(key="url") {
                val clipboard=LocalClipboardManager.current
                OutlinedTextField(urls,vm::setUrls,label={Text(stringResource(if(tool=="dl_03") R.string.workspace_direct_urls_batch else R.string.workspace_direct_url_single))},modifier=Modifier.fillMaxWidth(),minLines=if(tool=="dl_03") 4 else 2,textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr),trailingIcon={IconButton(onClick={clipboard.getText()?.text?.takeIf {it.isNotBlank()}?.let(vm::setUrls)}) {Icon(Icons.Rounded.ContentPaste,stringResource(R.string.action_paste))}})
                Text(stringResource(R.string.workspace_direct_url_hint),style=QuietTheme.typography.metadata)
                // Live per-line validation: the batch tool parses the list before anything is sent.
                val batchStatus=remember(urls,tool) {
                    if(tool=="dl_03" && urls.isNotBlank()) runCatching { com.jumlo.app.data.engine.PureDomainAlgorithms.parseBatchUrls(urls) }.getOrNull() else null
                }
                batchStatus?.let { candidates ->
                    val valid=candidates.count { it.status==com.jumlo.app.model.UrlValidationStatus.VALID }
                    val duplicates=candidates.count { it.status==com.jumlo.app.model.UrlValidationStatus.DUPLICATE }
                    val unsupported=candidates.count { it.status==com.jumlo.app.model.UrlValidationStatus.UNSUPPORTED_PROTOCOL }
                    val malformed=candidates.count { it.status==com.jumlo.app.model.UrlValidationStatus.MALFORMED }
                    Text(stringResource(R.string.workspace_batch_valid_links,valid,candidates.size),style=QuietTheme.typography.metadataStrong)
                    if(duplicates>0) Text(stringResource(R.string.workspace_batch_duplicates,duplicates),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.tertiary)
                    if(unsupported+malformed>0) Text(stringResource(R.string.workspace_batch_invalid_links,unsupported+malformed),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.ui_sensitive_draft_note),style=QuietTheme.typography.metadata)
            }
            if(tool in setOf("pdf_annotate","img_watermark")) {
                item(key="inline-content-settings") {
                    QuietCard {
                        ContentToolSettings(form,vm::updateForm)
                    }
                }
            }
            if(!viewer) {
                item(key="essential-settings") {
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(
                            color=MaterialTheme.colorScheme.surfaceContainerLow,
                            shape=RoundedCornerShape(20.dp),
                            tonalElevation=2.dp
                        ) {
                            Row(
                                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier=Modifier.weight(1f),
                                    verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                                    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(R.string.ui_smart_defaults_active),style=QuietTheme.typography.metadataStrong,color=MaterialTheme.colorScheme.onSurface)
                                        Text(stringResource(R.string.ui_destination),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if(hasAdvanced) {
                                    TextButton(onClick={advanced=true}) {
                                        Icon(Icons.Rounded.Tune,null,modifier=Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.ui_advanced),style=QuietTheme.typography.metadataStrong)
                                    }
                                }
                            }
                        }
                        if(tool=="file_batch_rename") {
                            if(renameInput!=null && renamePreview==null) Text(stringResource(R.string.ui_rename_checking),style=QuietTheme.typography.metadata)
                            renamePreview?.exceptionOrNull()?.let {Text(stringResource(userMessageResource(stableError(it))),color=MaterialTheme.colorScheme.error)}
                            if(!proposedNames.isNullOrEmpty()) TextButton(onClick={renamePreviewOpen=true}) {Text(stringResource(R.string.ui_rename_preview_open,proposedNames.size))}
                        }
                        if(inputMismatch) Text(stringResource(R.string.msg_file_type),color=MaterialTheme.colorScheme.error,style=QuietTheme.typography.metadata)
                        settingsError?.let {Text(stringResource(userMessageResource(it)),color=MaterialTheme.colorScheme.error,style=QuietTheme.typography.metadata)}
                        if(!countValid && queue.isNotEmpty()) Text(stringResource(R.string.msg_one_file),color=MaterialTheme.colorScheme.error,style=QuietTheme.typography.metadata)
                        if(tool=="pdf_03" && queue.size==1) Text(stringResource(R.string.ui_merge_minimum),color=MaterialTheme.colorScheme.error,style=QuietTheme.typography.metadata)
                    }
                }
            }
        }
        if(!viewer) Surface(tonalElevation=2.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp)) {
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom=8.dp))
                QuietPrimaryButton(text=when(tool) {"pdf_02"->stringResource(R.string.ui_create_pdf);"pdf_03"->stringResource(R.string.ui_merge_files,queue.size);else->stringResource(if(download) R.string.workspace_queue_download else R.string.workspace_process_files)},onClick=onExecute,modifier=Modifier.fillMaxWidth(),enabled=ready)
            }
        }
    }
    if(advanced) ModalBottomSheet(onDismissRequest={advanced=false}) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Text(stringResource(R.string.ui_advanced),style=QuietTheme.typography.sectionTitle)}
            item {WorkspaceFields(vm,form,vm::updateForm,onGoogleSignIn)}
            item {TextButton(onClick={advanced=false}) {Text(stringResource(R.string.action_close))}}
        }
    }
    previewId?.let {id->AlertDialog(onDismissRequest={previewId=null},title={Text(stringResource(R.string.dialog_preview_title))},text={InputPreview(vm,id,reduceMotion,isFullViewer=true)},confirmButton={TextButton(onClick={previewId=null}) {Text(stringResource(R.string.action_close))}})}
    if(renamePreviewOpen && proposedNames!=null) AlertDialog(
        onDismissRequest={renamePreviewOpen=false},
        title={Text(stringResource(R.string.ui_rename_preview_title))},
        text={
            LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                itemsIndexed(proposedNames,key={index,_->queue[index].id}) {index,name ->
                    Column {
                        Text(queue[index].displayName,style=QuietTheme.typography.bodyMedium.copy(textDirection=TextDirection.Content))
                        Text(stringResource(R.string.ui_rename_preview_name,name),style=QuietTheme.typography.metadata.copy(textDirection=TextDirection.Content),color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={renamePreviewOpen=false}) {Text(stringResource(R.string.action_close))}}
    )
}

/**
 * Rebuilt 2026 PDF & Image Viewer:
 * - Smooth horizontal page swiping via HorizontalPager (automatically reverses in RTL).
 * - Pinch-to-zoom & pan gestures (1x..4x) + double-tap quick zoom (1x <-> 2.5x).
 * - Interactive horizontal page thumbnail strip for multi-page PDFs.
 * - Dark mode page rendering toggle (inverts white paper for comfortable OLED reading).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun InputPreview(
    vm:AppViewModel,sourceId:String,reduceMotion:Boolean=false,
    isFullViewer:Boolean=false,adjustment:WorkspaceDraft?=null,raster:WorkspaceDraft?=null
) {
    var pageCount by rememberSaveable(sourceId) { mutableIntStateOf(1) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    var scale by remember(sourceId, pagerState.currentPage) { mutableFloatStateOf(1f) }
    var offset by remember(sourceId, pagerState.currentPage) { mutableStateOf(Offset.Zero) }
    var currentDims by remember(sourceId) { mutableStateOf<Pair<Int,Int>?>(null) }
    var compareOriginal by rememberSaveable(sourceId) { mutableStateOf(false) }
    var darkPageReading by rememberSaveable(sourceId) { mutableStateOf(false) }

    Surface(
        shape = QuietTheme.shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFullViewer) 460.dp else 320.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clipToBounds()
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = scale <= 1.02f,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val pageNumber = pageIndex + 1
                    var frame by remember(sourceId, pageNumber) { mutableStateOf<PreviewFrame?>(null) }
                    var failure by remember(sourceId, pageNumber) { mutableStateOf<String?>(null) }
                    var loading by remember(sourceId, pageNumber) { mutableStateOf(true) }

                    LaunchedEffect(sourceId, pageNumber) {
                        loading = true; failure = null; frame = null
                        try {
                            val loaded = vm.preview(sourceId, pageNumber)
                            frame = loaded
                            if (loaded.count > 0 && loaded.count != pageCount) {
                                pageCount = loaded.count
                            }
                            if (pageIndex == pagerState.currentPage) {
                                currentDims = loaded.width to loaded.height
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            failure = stableError(error)
                        } finally {
                            loading = false
                        }
                    }
                    LaunchedEffect(frame,pagerState.currentPage) {
                        if(pageIndex==pagerState.currentPage) currentDims=frame?.let {it.width to it.height}
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(sourceId, pageNumber,pagerState.currentPage) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1.05f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                            .pointerInput(sourceId, pageNumber,pagerState.currentPage) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    scale = newScale
                                    if (newScale > 1.01f) {
                                        val maxPanX = (size.width * (newScale - 1f)) / 2f
                                        val maxPanY = (size.height * (newScale - 1f)) / 2f
                                        offset = Offset(
                                            x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                            y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                        )
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (loading) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                QuietShimmerSkeleton(reduceMotion = reduceMotion)
                            }
                        }
                        failure?.let {
                            Text(
                                text = stringResource(userMessageResource(it)),
                                color = MaterialTheme.colorScheme.error,
                                style = QuietTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        frame?.let { current ->
                            val adjustmentFilter=remember(adjustment?.num1,adjustment?.num2,adjustment?.num3,raster?.grayscale,darkPageReading) {
                                when {
                                    darkPageReading -> ColorFilter.colorMatrix(
                                        ColorMatrix(
                                            floatArrayOf(
                                                -0.88f, 0f, 0f, 0f, 240f,
                                                0f, -0.88f, 0f, 0f, 240f,
                                                0f, 0f, -0.88f, 0f, 240f,
                                                0f, 0f, 0f, 1f, 0f
                                            )
                                        )
                                    )
                                    raster?.grayscale==true -> ColorFilter.colorMatrix(ColorMatrix().apply {setToSaturation(0f)})
                                    adjustment?.toolId!="img_adjust" -> null
                                    else -> {
                                        val brightness=(adjustment.num1.toFloatOrNull()?:0f).coerceIn(-100f,100f)
                                        val contrast=(adjustment.num2.toFloatOrNull()?:100f).coerceIn(0f,300f)/100f
                                        val saturation=(adjustment.num3.toFloatOrNull()?:100f).coerceIn(0f,300f)/100f
                                        val filterMatrix=ColorMatrix().apply {
                                            setToSaturation(saturation)
                                            val matrixValues=values
                                            val shift=brightness*1.27f
                                            for(row in 0..2) {
                                                for(col in 0..2) matrixValues[row*5+col]*=contrast
                                                matrixValues[row*5+4]=shift
                                            }
                                        }
                                        ColorFilter.colorMatrix(filterMatrix)
                                    }
                                }
                            }
                            Image(
                                bitmap = current.bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.dialog_preview_title),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = if (pageIndex == pagerState.currentPage) scale else 1f
                                        scaleY = if (pageIndex == pagerState.currentPage) scale else 1f
                                        translationX = if (pageIndex == pagerState.currentPage) offset.x else 0f
                                        translationY = if (pageIndex == pagerState.currentPage) offset.y else 0f
                                    },
                                contentScale = ContentScale.Fit,
                                colorFilter = if(compareOriginal) null else adjustmentFilter
                            )
                        }
                    }
                }

                if (scale > 1.05f) {
                    Surface(
                        onClick = { scale = 1f; offset = Offset.Zero },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.ZoomOutMap, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                text = "${(scale * 100).toInt()}% · ${stringResource(R.string.ui_preview_reset_zoom)}",
                                style = QuietTheme.typography.badge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietBadge(
                        text = stringResource(R.string.ui_page_indicator, pagerState.currentPage + 1, pageCount),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    currentDims?.let { (w, h) ->
                        val dimensions=if(raster!=null) {
                            val scale=draftNumber(raster.dpi,144).coerceIn(36,600)/72f
                            "${kotlin.math.round(w*scale).toInt()} × ${kotlin.math.round(h*scale).toInt()} px"
                        } else "${w} × ${h}"
                        Text(dimensions,style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { darkPageReading = !darkPageReading },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DarkMode,
                            contentDescription = stringResource(R.string.pref_theme_dark),
                            tint = if (darkPageReading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (pageCount > 1) {
                        IconButton(
                            enabled = pagerState.currentPage > 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateBefore, stringResource(R.string.action_previous))
                        }
                        IconButton(
                            enabled = pagerState.currentPage < pageCount - 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext, stringResource(R.string.action_next))
                        }
                    }
                }
            }

            // Interactive Page Thumbnail Strip for multi-page PDFs
            if (pageCount > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items((1..pageCount.coerceAtMost(120)).toList(), key = { it }) { thumbPage ->
                        val isCurrent = pagerState.currentPage == thumbPage - 1
                        var thumbFrame by remember(sourceId, thumbPage) { mutableStateOf<PreviewFrame?>(null) }
                        LaunchedEffect(sourceId, thumbPage) {
                            thumbFrame = runCatching { vm.thumbnail(sourceId, thumbPage) }.getOrNull()
                        }
                        Surface(
                            onClick = { scope.launch { pagerState.animateScrollToPage(thumbPage - 1) } },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = if (isCurrent) 4.dp else 1.dp,
                            modifier = Modifier.size(width = 56.dp, height = 74.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(3.dp)) {
                                thumbFrame?.let { tf ->
                                    Image(
                                        bitmap = tf.bitmap.asImageBitmap(),
                                        contentDescription = thumbPage.toString(),
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.78f),
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp)
                                ) {
                                    Text(
                                        text = thumbPage.toString(),
                                        style = QuietTheme.typography.badge,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if(adjustment?.toolId=="img_adjust") {
                FilledTonalButton(onClick={compareOriginal=!compareOriginal},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Icon(Icons.Rounded.Compare,null,Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(
                        if(compareOriginal) R.string.visual_show_edited
                        else R.string.visual_show_original
                    ))
                }
            }
            Text(
                text = stringResource(R.string.ui_preview_zoom_hint),
                style = QuietTheme.typography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Target-size handler emits JPEG and selects quality itself; only these settings are consumed. */
@Composable fun TargetImageSettings(form:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ui_target_jpeg_policy),style=QuietTheme.typography.metadata)
        ImageTargetControls(form,edit)
        Field(stringResource(R.string.field_output_prefix),form.prefix,{v->edit {it.copy(prefix=v)}})
    }
}

/** Operation-defining content controls; accessible in Advanced settings and direct tests. */
@Composable fun ContentToolSettings(form:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        when(form.toolId) {
            "img_watermark" -> {
                Toggle(stringResource(R.string.field_use_second_logo),form.flag1) {v->edit {it.copy(flag1=v)}}
                if(form.flag1) Text(stringResource(R.string.ui_watermark_logo_required),style=QuietTheme.typography.metadata)
                else Field(stringResource(R.string.field_watermark_text),form.text1,{v->edit {it.copy(text1=v)}})
            }
            "pdf_stamp" -> {
                Field(stringResource(R.string.field_watermark_text),form.text1,{v->edit {it.copy(text1=v)}})
                Toggle(stringResource(R.string.field_add_page_numbers),form.flag1) {v->edit {it.copy(flag1=v)}}
            }
            "pdf_annotate" -> {
                val kind=com.jumlo.app.core.PdfAnnotationKind.from(form.text2)
                Text(stringResource(R.string.field_annotation_kind),style=QuietTheme.typography.metadataStrong)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(com.jumlo.app.core.PdfAnnotationKind.entries.toList(),key={it.id}) { choice ->
                        QuietChip(selected=kind==choice,{edit {it.copy(text2=choice.id)}},label=stringResource(annotationKindLabel(choice)))
                    }
                }
                if(kind.carriesText) Field(stringResource(R.string.field_text),form.text1,{v->edit {it.copy(text1=v)}})
            }
        }
    }
}

@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,numeric:Boolean=false,secret:Boolean=false) {
    OutlinedTextField(value,{onValue(it.take(512))},label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=when{numeric->KeyboardType.Decimal;secret->KeyboardType.Password;else->KeyboardType.Text}),
        visualTransformation=if(secret) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable private fun Options(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=QuietTheme.typography.metadataStrong)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(options,key={it}) { choice -> FilterChip(selected==choice,{onSelect(choice)},label={Text(choice)}) } }
}
@Composable private fun LabeledOptions(label:String,options:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=QuietTheme.typography.metadataStrong)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(options,key={it.first}) { (value, display) ->
            FilterChip(selected==value,{onSelect(value)},label={Text(display)})
        }
    }
}
private fun annotationKindLabel(kind:com.jumlo.app.core.PdfAnnotationKind):Int = when(kind) {
    com.jumlo.app.core.PdfAnnotationKind.HIGHLIGHT -> R.string.annotation_highlight
    com.jumlo.app.core.PdfAnnotationKind.UNDERLINE -> R.string.annotation_underline
    com.jumlo.app.core.PdfAnnotationKind.STRIKETHROUGH -> R.string.annotation_strikethrough
    com.jumlo.app.core.PdfAnnotationKind.NOTE -> R.string.annotation_note
    com.jumlo.app.core.PdfAnnotationKind.SQUARE -> R.string.annotation_square
    com.jumlo.app.core.PdfAnnotationKind.CIRCLE -> R.string.annotation_circle
    com.jumlo.app.core.PdfAnnotationKind.TEXTBOX -> R.string.annotation_textbox
    com.jumlo.app.core.PdfAnnotationKind.INK -> R.string.annotation_ink
    com.jumlo.app.core.PdfAnnotationKind.TEXT -> R.string.annotation_text
}
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,role=Role.Switch,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));Switch(checked,null)} }

@Composable private fun WorkspaceFields(vm:AppViewModel,f:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit,onGoogleSignIn:()->Unit={}) {
    QuietCard {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(f.toolId !in setOf("file_duplicates","file_trash","sha256")) {
                Field(stringResource(R.string.ui_custom_output_name),f.outputName,{value->edit {it.copy(outputName=value)}})
            }
            val image=f.toolId.startsWith("img_") && f.toolId !in setOf("img_compare","img_target")
            if(image) {
                if(f.toolId=="img_exif" && !f.flag1) Text(stringResource(R.string.ui_exif_jpeg_policy),style=QuietTheme.typography.metadata)
                else Options(stringResource(R.string.field_output_format),listOf("JPEG","PNG","WEBP"),f.format) { value->edit {it.copy(format=value)} }
                if(f.format!="PNG" || (f.toolId=="img_exif" && !f.flag1)) Field(stringResource(R.string.field_quality),f.quality,{value->edit {it.copy(quality=value)}},true)
                Field(stringResource(R.string.field_output_prefix),f.prefix,{value->edit {it.copy(prefix=value)}})
                Text(stringResource(R.string.field_image_policy_note),style=QuietTheme.typography.metadata)
            }
            when(f.toolId) {
                "img_02" -> {
                    if(f.percentage.isBlank() && f.longestEdge.isBlank()) {
                        Field(stringResource(R.string.field_target_width),f.width,{v->edit {it.copy(width=v)}},true)
                        Field(stringResource(R.string.field_target_height),f.height,{v->edit {it.copy(height=v)}},true)
                    }
                    Field(stringResource(R.string.field_percentage_opt),f.percentage,{v->edit {it.copy(percentage=v,longestEdge="")}},true)
                    Field(stringResource(R.string.field_longest_edge_opt),f.longestEdge,{v->edit {it.copy(longestEdge=v,percentage="")}},true)
                    LabeledOptions(
                        label=stringResource(R.string.field_fit_strategy),
                        options=listOf(
                            "FIT" to stringResource(R.string.ui_fit_contain),
                            "FILL" to stringResource(R.string.ui_fit_cover),
                            "PAD" to stringResource(R.string.ui_fit_pad),
                            "STRETCH" to stringResource(R.string.ui_fit_stretch)
                        ),
                        selected=f.fit
                    ) {v->edit {it.copy(fit=v)}}
                    Toggle(stringResource(R.string.field_allow_upscaling),f.allowUpscaling) {v->edit {it.copy(allowUpscaling=v)}}
                }
                "img_03" -> {
                    Text(stringResource(R.string.field_crop_hint),style=QuietTheme.typography.metadata)
                    Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true);Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true)
                    Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true);Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true)
                    Options(stringResource(R.string.field_clockwise_rotation),listOf("0","90","180","270"),(f.turns*90).toString()) {v->edit {it.copy(turns=v.toInt()/90)}}
                    Toggle(stringResource(R.string.field_flip_horizontal),f.flipX) {v->edit {it.copy(flipX=v)}};Toggle(stringResource(R.string.field_flip_vertical),f.flipY) {v->edit {it.copy(flipY=v)}}
                }
                "pdf_02" -> {
                    LabeledOptions(
                        label=stringResource(R.string.jumlo_paper_mode),
                        options=listOf(
                            "AUTO" to stringResource(R.string.ui_orient_auto),
                            "A4" to "A4", "LETTER" to "Letter",
                            "CUSTOM" to stringResource(R.string.jumlo_custom_paper)
                        ),selected=f.paperMode
                    ) { mode -> edit {it.copy(paperMode=mode)} }
                    if(f.paperMode == "CUSTOM") {
                        Field(stringResource(R.string.field_paper_width),f.paperWidth,{v->edit {it.copy(paperWidth=v)}},true)
                        Field(stringResource(R.string.field_paper_height),f.paperHeight,{v->edit {it.copy(paperHeight=v)}},true)
                    }
                    Field(stringResource(R.string.field_margin_mm),f.margin,{v->edit {it.copy(margin=v)}},true)
                    LabeledOptions(
                        label=stringResource(R.string.field_orientation),
                        options=listOf(
                            "AUTO" to stringResource(R.string.ui_orient_auto),
                            "PORTRAIT" to stringResource(R.string.ui_orient_portrait),
                            "LANDSCAPE" to stringResource(R.string.ui_orient_landscape)
                        ),
                        selected=f.orientation
                    ) {v->edit {it.copy(orientation=v)}}
                    Toggle(stringResource(R.string.field_fill_page),f.fill) {v->edit {it.copy(fill=v)}}
                }
                "pdf_07" -> {
                    Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}})
                    Field(stringResource(R.string.field_dpi),f.dpi,{v->edit {it.copy(dpi=v)}},true)
                    Options(stringResource(R.string.field_output_format),listOf("PNG","JPEG"),f.format) {v->edit {it.copy(format=v)}}
                    if(f.format=="JPEG") Field(stringResource(R.string.field_quality),f.quality,{v->edit {it.copy(quality=v)}},true)
                    Toggle(stringResource(R.string.field_grayscale),f.grayscale) {v->edit {it.copy(grayscale=v)}}
                    Text(stringResource(R.string.field_raster_note),style=QuietTheme.typography.metadata)
                }
                "file_07" -> {
                    PresetChoices(stringResource(R.string.field_zip_level),listOf(
                        "0" to R.string.interactive_zip_store,"1" to R.string.interactive_zip_fast,
                        "6" to R.string.interactive_zip_balanced,"9" to R.string.interactive_zip_maximum),f.zipLevel) {v->edit {it.copy(zipLevel=v)}}
                    InteractiveIntSlider(stringResource(R.string.field_zip_level),draftNumber(f.zipLevel,6),0,9,6) {v->edit {it.copy(zipLevel=v.toString())}}
                    Text(stringResource(R.string.interactive_zip_note),style=QuietTheme.typography.metadata)
                }
                "file_08" -> Text(stringResource(R.string.field_zip_extract_note),style=QuietTheme.typography.metadata)
                "sha256" -> Field(stringResource(R.string.field_expected_sha256),f.expectedHash,{v->edit {it.copy(expectedHash=v)}})
                "pdf_03" -> { Text(stringResource(R.string.field_merge_note),style=QuietTheme.typography.metadata) }
                "pdf_05" -> { Toggle(stringResource(R.string.field_split_every_n),f.flag1) {v->edit {it.copy(flag1=v)}}; if(f.flag1) Field(stringResource(R.string.field_pages_per_part),f.num1,{v->edit {it.copy(num1=v)}},true) else Field(stringResource(if(f.flag2) R.string.jumlo_split_markers else R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_06" -> { Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_04" -> { Field(stringResource(R.string.field_organize_order),f.text1,{v->edit {it.copy(text1=v,pageEdits=emptyList())}}); Field(stringResource(R.string.field_rotate_pages),f.text2,{v->edit {it.copy(text2=v,pageEdits=emptyList())}}); Field(stringResource(R.string.field_rotate_degrees),f.num1,{v->edit {it.copy(num1=v,pageEdits=emptyList())}},true) }
                "pdf_crop" -> { Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_compress" -> {
                    // Presets are the primary control; DPI and quality stay as the
                    // advanced inputs underneath them.
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(PdfCompressionPolicy.Preset.entries.toList(),key={it.name}) { preset ->
                            QuietChip(
                                selected = f.num2.isBlank() && f.num1==preset.dpi.toString() && f.quality==preset.quality.toString(),
                                onClick = { edit { it.copy(num1=preset.dpi.toString(),quality=preset.quality.toString(),num2="") } },
                                label = stringResource(
                                    when(preset) {
                                        PdfCompressionPolicy.Preset.STRONG -> R.string.visual_compress_strong
                                        PdfCompressionPolicy.Preset.SMALL_FILE -> R.string.visual_compress_small
                                        PdfCompressionPolicy.Preset.BALANCED -> R.string.visual_compress_balanced
                                        PdfCompressionPolicy.Preset.BEST_QUALITY -> R.string.visual_compress_best
                                    })
                            )
                        }
                    }
                    Field(stringResource(R.string.field_target_size_mb),f.num2,{v->edit {it.copy(num2=v)}},true)
                    if(f.num2.isNotBlank()) Text(stringResource(R.string.field_target_size_note),style=QuietTheme.typography.metadata)
                    // The slider replaces the typed DPI and quality: the user chooses a
                    // trade-off and sees the estimated result before committing.
                    val sourceId=f.sourceIds.firstOrNull()
                    var profile by remember(sourceId) { mutableStateOf<CompressionProfile?>(null) }
                    LaunchedEffect(sourceId) {
                        profile=sourceId?.let { vm.compressionProfile(it) }
                    }
                    val dpi=f.num1.toIntOrNull()?:150
                    val quality=f.quality.toIntOrNull()?:70
                    val position=PdfCompressionPolicy.sliderPosition(dpi,quality)
                    val estimate=profile?.let { PdfCompressionPolicy.estimateBytes(it.originalBytes,it.imageBytes,dpi,quality) }
                    val sliderLabel=stringResource(R.string.field_compress_slider,position.times(100).toInt())
                    Slider(
                        value=position,
                        onValueChange={ p ->
                            val step=PdfCompressionPolicy.sliderStep(p)
                            edit { it.copy(num1=step.first.toString(),quality=step.second.toString()) }
                        },
                        valueRange=0f..1f,
                        modifier=Modifier.semantics { contentDescription=sliderLabel;stateDescription=sliderLabel }
                    )
                    Text(sliderLabel,style=QuietTheme.typography.metadataStrong)
                    if(estimate!=null) {
                        Text(stringResource(R.string.field_compress_estimate,com.jumlo.app.data.engine.PureDomainAlgorithms.formatBytes(estimate)),
                            style=QuietTheme.typography.metadata)
                        val saving=PdfCompressionPolicy.estimatedSavingPercent(profile!!.originalBytes,estimate)
                        if(saving>0) Text(stringResource(R.string.field_compress_saving,saving),style=QuietTheme.typography.metadataStrong)
                        Text(stringResource(R.string.field_compress_estimate_note),style=QuietTheme.typography.metadata)
                    }
                    Text(stringResource(R.string.field_compress_note),style=QuietTheme.typography.metadata)
                }
                "pdf_stamp" -> {
                    ContentToolSettings(f,edit)
                    Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}})
                }
                "pdf_annotate" -> {
                    ContentToolSettings(f,edit)
                    // The region is dragged and resized on the page preview, so the
                    // position fields are no longer needed.
                    Text(stringResource(R.string.field_annotation_region_hint),style=QuietTheme.typography.metadata)
                    if(com.jumlo.app.core.PdfAnnotationKind.from(f.text2)==com.jumlo.app.core.PdfAnnotationKind.TEXT)
                        Toggle(stringResource(R.string.field_as_note_annotation),f.flag1) {v->edit {it.copy(flag1=v)}}
                }
                "pdf_signature" -> { Text(stringResource(R.string.field_signature_note),style=QuietTheme.typography.metadata); com.jumlo.app.ui.scan.SignaturePad { file -> vm.intakeLocalFile(file,"signature.png","image/png") }; Field(stringResource(R.string.field_page_number),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_clean" -> {
                    Toggle(stringResource(R.string.field_remove_title),f.flag1){v->edit {it.copy(flag1=v)}}
                    Text(stringResource(R.string.field_clean_note),style=QuietTheme.typography.metadata)
                }
                "pdf_metadata" -> { Field(stringResource(R.string.field_title),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_author),f.text2,{v->edit {it.copy(text2=v)}}); Field(stringResource(R.string.field_subject),f.text3,{v->edit {it.copy(text3=v)}}); Field(stringResource(R.string.field_keywords),f.text4,{v->edit {it.copy(text4=v)}}); if(f.sourceIds.size==2) PdfInsertionControls(vm,f,edit) }
                "pdf_protect" -> { Toggle(stringResource(R.string.field_remove_protection),f.flag1) {v->edit {it.copy(flag1=v)}}; Field(stringResource(R.string.field_user_password),f.secret1,{v->edit {it.copy(secret1=v)}},secret=true); if(!f.flag1) Field(stringResource(R.string.field_owner_password),f.secret2,{v->edit {it.copy(secret2=v)}},secret=true); Text(stringResource(R.string.field_protect_note),style=QuietTheme.typography.metadata) }
                "pdf_forms" -> { Field(stringResource(R.string.field_forms_json),f.text1,{v->edit {it.copy(text1=v)}}); Toggle(stringResource(R.string.field_flatten_form),f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_ocr" -> { Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Text(stringResource(R.string.field_ocr_note),style=QuietTheme.typography.metadata) }
                "webpage_pdf" -> { Field(stringResource(R.string.field_webpage_url),f.text1,{v->edit {it.copy(text1=v)}}); Text(stringResource(R.string.field_webpage_note),style=QuietTheme.typography.metadata) }
                "img_exif" -> { Toggle(stringResource(R.string.field_exif_remove_all),f.flag1) {v->edit {it.copy(flag1=v)}}; Toggle(stringResource(R.string.field_exif_remove_location),f.flag2) {v->edit {it.copy(flag2=v)}}; Text(stringResource(R.string.field_exif_note),style=QuietTheme.typography.metadata) }
                "img_watermark" -> {
                    ContentToolSettings(f,edit)
                    Field(stringResource(R.string.field_opacity_percent),f.num1,{v->edit {it.copy(num1=v)}},true)
                }
                "img_join","img_stitch" -> {
                    if(f.toolId=="img_join") LabeledOptions(
                        label=stringResource(R.string.field_layout),
                        options=listOf(
                            "GRID2" to stringResource(R.string.ui_layout_grid2),
                            "HSTRIP" to stringResource(R.string.ui_layout_hstrip),
                            "VSTRIP" to stringResource(R.string.ui_layout_vstrip)
                        ),
                        selected=f.text1.ifBlank{"GRID2"}
                    ) {v->edit {it.copy(text1=v)}}
                    Field(stringResource(R.string.field_gap_px),f.num1,{v->edit {it.copy(num1=v)}},true)
                    if(f.toolId=="img_stitch") Field(stringResource(R.string.field_overlap_px),f.num2,{v->edit {it.copy(num2=v)}},true)
                }
                "img_adjust" -> { Field(stringResource(R.string.field_brightness),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_contrast),f.num2,{v->edit {it.copy(num2=v)}},true); Field(stringResource(R.string.field_saturation),f.num3,{v->edit {it.copy(num3=v)}},true); Field(stringResource(R.string.field_padding_px),f.num4,{v->edit {it.copy(num4=v)}},true) }
                "img_target" -> TargetImageSettings(f,edit)
                "img_compare" -> Text(stringResource(R.string.field_compare_img_note),style=QuietTheme.typography.metadata)
                "file_batch_rename" -> { Field(stringResource(R.string.field_rename_pattern),f.text1,{v->edit {it.copy(text1=v)}}); InteractiveCounter(stringResource(R.string.field_start_number),draftNumber(f.num1,1),0,1000000) {v->edit {it.copy(num1=v.toString())}}; Text(stringResource(R.string.field_rename_note),style=QuietTheme.typography.metadata); Text(stringResource(R.string.ui_rename_copy_note),style=QuietTheme.typography.metadata) }
                "file_duplicates" -> Text(stringResource(R.string.field_duplicates_note),style=QuietTheme.typography.metadata)
                "file_trash" -> Text(stringResource(R.string.field_trash_note),style=QuietTheme.typography.metadata)
                "file_encrypted_zip" -> { Toggle(stringResource(R.string.field_extract_instead),f.flag1) {v->edit {it.copy(flag1=v)}}; Field(stringResource(R.string.field_zip_password),f.secret1,{v->edit {it.copy(secret1=v)}},secret=true); Text(stringResource(R.string.field_encrypted_zip_note),style=QuietTheme.typography.metadata) }
                "file_drive" -> {
                    Toggle(stringResource(R.string.field_drive_export_mode),f.flag1) {v->edit {it.copy(flag1=v)}}
                    if(!f.flag1) Field(stringResource(R.string.field_drive_file_id),f.text1,{v->edit {it.copy(text1=v)}})
                    else Field(stringResource(R.string.field_drive_folder_id),f.text2,{v->edit {it.copy(text2=v)}})
                    QuietTonalButton(text=stringResource(R.string.field_drive_signin),onClick=onGoogleSignIn)
                    Field(stringResource(R.string.field_drive_token),f.secret1,{v->edit {it.copy(secret1=v)}})
                    Text(stringResource(R.string.field_drive_note),style=QuietTheme.typography.metadata)
                }
                "dl_02","dl_03" -> {
                    Toggle(stringResource(R.string.ui_wifi_only),f.wifiOnly) {v->edit {it.copy(wifiOnly=v)}}
                    Toggle(stringResource(R.string.ui_unmetered_only),f.unmeteredOnly) {v->edit {it.copy(unmeteredOnly=v)}}
                    Toggle(stringResource(R.string.ui_http_consent),f.cleartextConsent) {v->edit {it.copy(cleartextConsent=v)}}
                    if(f.cleartextConsent) Text(stringResource(R.string.ui_http_notice),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.nav_downloads),
                                    style = QuietTheme.typography.metadataStrong.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if(f.outputName.isNotBlank() && f.outputName != "download.bin") f.outputName else stringResource(R.string.workspace_direct_url_hint),
                                    style = QuietTheme.typography.metadata,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
