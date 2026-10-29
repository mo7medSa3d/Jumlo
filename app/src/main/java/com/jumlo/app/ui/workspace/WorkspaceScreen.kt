package com.jumlo.app.ui.workspace

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
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateBefore
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateNext
import com.jumlo.app.ui.icons.rounded.*
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
import androidx.compose.ui.platform.testTag
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
    var previewId by rememberSaveable(tool, form.sourceIds) {mutableStateOf<String?>(null)}
    var advanced by rememberSaveable(tool) {mutableStateOf(false)}
    var renamePreviewOpen by rememberSaveable(tool, form.sourceIds) {mutableStateOf(false)}

    // Populate smart defaults on tool entry so no manual typing is required on the main workspace.
    val defaultWatermark = stringResource(R.string.watermark_preset_confidential)
    LaunchedEffect(tool) {
        when(tool) {
            "pdf_stamp" -> if(form.text1.isBlank() && !form.flag1) vm.updateForm { it.copy(text1=defaultWatermark) }
            "img_watermark" -> if(form.text1.isBlank() && !form.flag1) vm.updateForm { it.copy(text1="Jumlo") }
            // Only the text-carrying kinds get a body; a highlight is just a region.
            "pdf_annotate" -> if(com.jumlo.app.core.PdfAnnotationKind.from(form.text2).carriesText && form.text1.isBlank())
                vm.updateForm { it.copy(text1="Jumlo", num1=it.num1.ifBlank{"1"}) }
            "file_batch_rename" -> vm.updateForm { it.copy(text1=it.text1.ifBlank { "{name}_{n}" }, num1=it.num1.ifBlank { "1" },
                renameDate=it.renameDate.ifBlank { java.time.LocalDate.now().toString() }) }
            "img_02" -> if(form.percentage.isBlank() && form.longestEdge.isBlank() && form.width=="1600" && form.height=="1600") {
                vm.updateForm { it.copy(percentage="75") }
            }
        }
    }

    val queue=remember(form.sourceIds,imported) {val indexed=imported.associateBy {it.id};form.sourceIds.mapNotNull(indexed::get)}
    if(viewer) {
        ViewerWorkspace(vm,queue,onImport)
        return
    }
    var selectedSourceId by rememberSaveable(tool, form.sourceIds) { mutableStateOf<String?>(null) }
    val preview=queue.firstOrNull {it.id==selectedSourceId && (it.mimeType?.startsWith("image/")==true || it.mimeType=="application/pdf")}
        ?: queue.firstOrNull {it.mimeType?.startsWith("image/")==true || it.mimeType=="application/pdf"}
    var formFieldsReady by remember(tool,form.sourceIds) {mutableStateOf(tool!="pdf_forms")}
    val visualPageSelection=tool in setOf("pdf_04","pdf_05","pdf_06","pdf_07","pdf_crop","pdf_stamp","pdf_ocr","pdf_to_docx","pdf_to_pptx")
    val editorSource=if(tool in setOf("pdf_image","pdf_signature","pdf_stamp"))
        queue.firstOrNull { it.mimeType=="application/pdf" } else preview
    val overlayImage=if(tool in setOf("pdf_image","pdf_signature","pdf_stamp"))
        queue.firstOrNull { it.mimeType?.startsWith("image/")==true } else null
    val minimum=if(tool in setOf("pdf_03","pdf_compare","img_compare","pdf_signature","pdf_image","img_join","img_stitch") ||
        (tool=="img_watermark" && form.flag1) || (tool=="pdf_stamp" && form.watermarkUseImage)) 2 else 1
    val inputMismatch=remember(tool,queue,form.watermarkUseImage) { queue.any {source->
        val mime=source.mimeType
        when {
            tool=="pdf_image" || tool=="pdf_signature" || tool=="pdf_stamp" && form.watermarkUseImage ->
                queue.size!=2 || queue.count { it.mimeType=="application/pdf" }!=1 ||
                    queue.count { it.mimeType?.startsWith("image/")==true }!=1
            mime==null || mime=="application/octet-stream" -> false
            tool=="pdf_ocr" -> mime!="application/pdf" && !mime.startsWith("image/")
            tool=="pdf_02" || tool.startsWith("img_") -> !mime.startsWith("image/")
            tool.startsWith("pdf_") -> mime!="application/pdf"
            tool=="file_08" -> mime !in setOf("application/zip","application/x-zip-compressed")
            else -> false
        }
    }}
    val pagePickerSource=queue.filter {it.mimeType=="application/pdf"}.singleOrNull()?.takeIf {
        visualPageSelection && (queue.size==1 || tool=="pdf_stamp" && form.watermarkUseImage)
    }
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
                        true
                    )
                }
            }
        }.exceptionOrNull()?.let(::stableError)
    }
    val countValid=when(tool) {
        "pdf_07","file_08","pdf_04","pdf_05","pdf_06","pdf_crop",
        "pdf_compress","pdf_annotate","pdf_forms","pdf_clean","pdf_protect","pdf_ocr","pdf_to_docx","pdf_to_pptx" -> queue.size==1
        "pdf_image","pdf_signature","pdf_compare","img_compare" -> queue.size==2
        "pdf_stamp" -> queue.size==if(form.watermarkUseImage) 2 else 1
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
                RenamePreviewState(input,runCatching {PureDomainAlgorithms.batchRenamePreview(input.names,input.settings.pattern,input.settings.startAt,input.settings.date)})
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
        (tool!="pdf_signature" || form.signaturePlacements.isNotEmpty()) &&
        (tool!="pdf_annotate" || !com.jumlo.app.core.PdfAnnotationKind.from(form.text2).isFreehand || form.effectiveInkStrokes().isNotEmpty()) &&
        (driveImport || if(download) urls.isNotBlank() else queue.size>=minimum)
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=840.dp).align(Alignment.CenterHorizontally).testTag("workspace.scroll"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
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
                        if(tool in setOf("pdf_02","pdf_image","pdf_signature","pdf_stamp","pdf_ocr") || tool.startsWith("img_"))
                            QuietTonalButton(stringResource(R.string.import_photo_gallery),onPhotos,Modifier.weight(1f))
                    }
                }
                if(queue.isEmpty()) item {QuietStateCard(stringResource(R.string.ui_choose_inputs),stringResource(R.string.ui_choose_inputs_body),stringResource(R.string.import_device_files),onImport)}
                if(tool in setOf("pdf_to_docx","pdf_to_pptx","pdf_ocr")) item("conversion-scope") {
                    Text(stringResource(when(tool) { "pdf_to_docx" -> R.string.rebuild_word_note; "pdf_to_pptx" -> R.string.rebuild_slides_note; else -> R.string.rebuild_ocr_note }),
                        style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(queue.size>1) item("visual-source-order") {
                    SourceOrderStrip(vm,queue,preview?.id,
                        if(tool=="img_watermark" && form.flag1) queue.getOrNull(1)?.id else null,
                        onSelect={selectedSourceId=it},onMove=vm::moveSource,renamed=proposedNames)
                }
                if(preview!=null && tool !in setOf(
                    "img_01","img_03","img_02","img_04","img_target","img_adjust","img_join","img_stitch","img_watermark","pdf_crop","pdf_redact","pdf_signature","pdf_annotate","pdf_image","pdf_stamp","pdf_02","pdf_ocr","pdf_metadata"
                ) && !(tool in setOf("pdf_04", "pdf_05", "pdf_06") && queue.size==1))
                    item(key="preview-${preview.id}") {
                        InputPreview(vm,preview.id,reduceMotion,isFullViewer=viewer,
                            adjustment=if(tool=="img_adjust") form else null,
                            raster=if(tool=="pdf_07") form else null)
                    }
                if(pagePickerSource!=null)
                    item(key="pdf-visual-editor-${pagePickerSource.id}") { PdfPageEditor(vm,pagePickerSource.id,form,vm::updateForm,
                        onPrint={pages->onPrintPages?.invoke(pagePickerSource.id,pages)},printEnabled=onPrintPages!=null) }
                if(editorSource!=null && tool in setOf(
                    "img_03","pdf_crop","pdf_redact","pdf_annotate","pdf_image"
                )) {
                    item(key="visual-geometry-editor") {
                        VisualDocumentEditor(vm,editorSource.id,form,vm::updateForm,overlayImage?.id)
                    }
                }
                if(tool=="pdf_signature" && editorSource?.mimeType=="application/pdf") item("signature-placement-editor") {
                    SignaturePlacementEditor(vm,editorSource.id,overlayImage?.id,form,vm::updateForm)
                }
                if(tool=="pdf_stamp" && editorSource?.mimeType=="application/pdf") {
                    item(key="visual-watermark-editor") {
                        VisualWatermarkEditor(vm,editorSource.id,form,vm::updateForm,overlayImage?.id,onPhotos)
                    }
                }
                if(tool in setOf("pdf_image","pdf_signature") && queue.size<2) {
                    item(key="image-overlay-hint") {
                        Text(stringResource(R.string.visual_two_sources),
                            style=QuietTheme.typography.metadata,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(tool=="img_watermark" && queue.isNotEmpty() && !inputMismatch) item("image-watermark-editor") {
                    VisualWatermarkEditor(vm,queue.first().id,form,vm::updateForm,queue.getOrNull(1)?.id?.takeIf {form.flag1},onPhotos)
                }
                if(tool in setOf("img_02","img_adjust","img_join","img_stitch") && preview!=null && !inputMismatch) item("transformed-image-preview") {
                    val ids=when {
                        tool in setOf("img_join","img_stitch") -> queue.map {it.id}
                        tool=="img_watermark" && form.flag1 -> listOfNotNull(
                            preview.takeUnless {it.id==queue.getOrNull(1)?.id}?.id ?: queue.firstOrNull()?.id,
                            queue.getOrNull(1)?.id)
                        else -> listOf(preview.id)
                    }
                    ImageResultPreview(vm,ids,form)
                }
                if(tool in setOf("img_01","img_04","img_target") && preview!=null && !inputMismatch) item("encoded-image-preview") {
                    EncodedImagePreviewPanel(vm,preview.id,form)
                }
                if(tool.startsWith("img_") || tool in setOf("pdf_compress","pdf_07","pdf_02")) {
                    item(key="visual-intuitive-controls") {
                        VisualImageSettings(form,vm::updateForm)
                    }
                }
                if(tool=="pdf_02" && preview!=null) item("paper-preview") {
                    ImagePdfPreview(vm,preview.id,form,vm::updateForm)
                }
                if(tool=="pdf_compress" && queue.size==1) item("compression-estimate") {
                    CompressionEstimate(vm,queue.single().id,form)
                }
                if(tool=="pdf_ocr" && queue.size==1 && !inputMismatch) item("ocr-result-panel") {
                    OcrWorkspacePanel(vm,queue.single().id,onExecute,ready)
                }
                if(tool=="file_duplicates" && queue.size>=2) item("duplicate-result-panel") {
                    DuplicateResultsPanel(vm,queue,onImport)
                }
                if(tool=="pdf_forms" && queue.size==1 && !inputMismatch) item("pdf-form-fields") {
                    PdfFormEditor(vm,queue.single().id,form,vm::updateForm,onReady={formFieldsReady=it},onImport=onImport)
                }
                if(tool=="pdf_metadata" && preview!=null) item("metadata-workspace") {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if(maxWidth>=720.dp) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f)) {InputPreview(vm,preview.id,reduceMotion)}
                            Box(Modifier.weight(1f)) {WorkspaceFields(vm,form,vm::updateForm,onGoogleSignIn)}
                        } else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            InputPreview(vm,preview.id,reduceMotion)
                            WorkspaceFields(vm,form,vm::updateForm,onGoogleSignIn)
                        }
                    }
                }
                if(tool in setOf("file_07","file_encrypted_zip","file_batch_rename","sha256","pdf_protect","pdf_clean","img_exif")) item("primary-tool-settings") {
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
                TextField(urls,vm::setUrls,label={Text(stringResource(if(tool=="dl_03") R.string.workspace_direct_urls_batch else R.string.workspace_direct_url_single))},modifier=Modifier.fillMaxWidth(),shape=QuietTheme.shapes.card,colors=borderlessFieldColors(),minLines=if(tool=="dl_03") 4 else 2,textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr),trailingIcon={IconButton(onClick={
                    try {
                        val pasted=clipboard.getText()?.text?.takeIf {it.isNotBlank()}
                        if(pasted==null) vm.notifyMessage("CLIPBOARD_EMPTY") else vm.setUrls(pasted)
                    }
                    catch(_:RuntimeException) { vm.notifyMessage("CLIPBOARD_UNAVAILABLE") }
                }) {Icon(Icons.Rounded.ContentPaste,stringResource(R.string.action_paste))}})
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
                Text(stringResource(R.string.rebuild_download_privacy),style=QuietTheme.typography.metadata)
            }
            if(!viewer) {
                item(key="essential-settings") {
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(
                            color=MaterialTheme.colorScheme.surfaceContainerLow,
                            shape=QuietTheme.shapes.card
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
                                    TextButton(onClick={advanced=!advanced}) {
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
            if(advanced) item("inline-advanced-settings") {
                WorkspaceFields(vm,form,vm::updateForm,onGoogleSignIn)
            }
        }
        if(!viewer) Surface(tonalElevation=2.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp)) {
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom=8.dp))
                QuietPrimaryButton(text=when(tool) {"pdf_to_docx"->stringResource(R.string.rebuild_word_action);"pdf_to_pptx"->stringResource(R.string.rebuild_slides_action);"pdf_ocr"->stringResource(R.string.rebuild_ocr_action);"pdf_02"->stringResource(R.string.ui_create_pdf);"pdf_03"->stringResource(R.string.ui_merge_files,queue.size);else->stringResource(if(download) R.string.workspace_queue_download else R.string.workspace_process_files)},onClick=onExecute,modifier=Modifier.fillMaxWidth(),enabled=ready)
            }
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
 * 2026 PDF & Image Previewer:
 * - Smooth horizontal page swiping via HorizontalPager (automatically reverses in RTL).
 * - Pinch-to-zoom & pan gestures + double-tap quick zoom (1x <-> 2.5x).
 * - Modern floating page indicator pill & prev/next controls.
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

    Surface(
        shape = QuietTheme.shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFullViewer) 440.dp else 300.dp)
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
                    // RenderThread can retain a displayed bitmap after composition disposal.
                    // Release through normal ownership instead of recycling a live frame.

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
                            val adjustmentFilter=remember(adjustment?.num1,adjustment?.num2,adjustment?.num3,raster?.grayscale) {
                                if(raster?.grayscale==true) ColorFilter.colorMatrix(ColorMatrix().apply {setToSaturation(0f)})
                                else if(adjustment?.toolId!="img_adjust") null else {
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
                            Image(
                                bitmap = current.bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.dialog_preview_title),
                                modifier = Modifier
                                    .fillMaxSize().testTag("preview.bitmap")
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
                if (pageCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,numeric:Boolean=false,secret:Boolean=false,ltrValue:Boolean=false) {
    var revealed by rememberSaveable(label) { mutableStateOf(false) }
    TextField(value,{onValue(it.take(512))},label={Text(label)},modifier=Modifier.fillMaxWidth(),shape=QuietTheme.shapes.card,colors=borderlessFieldColors(),singleLine=true,
        textStyle=if(ltrValue) LocalTextStyle.current.copy(textDirection=TextDirection.Ltr) else LocalTextStyle.current,
        keyboardOptions=KeyboardOptions(keyboardType=when{numeric->KeyboardType.Decimal;secret->KeyboardType.Password;else->KeyboardType.Text}),
        trailingIcon=if(secret) ({ IconButton(onClick={revealed=!revealed}) {
            Icon(Icons.Rounded.Visibility,stringResource(if(revealed) R.string.studio_password_hide else R.string.studio_password_show))
        } }) else null,
        visualTransformation=if(secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None)
    if(secret && value.isNotEmpty()) {
        val strength = passwordStrength(value)
        val strengthLabel = stringResource(when(strength) { 2 -> R.string.studio_password_strong; 1 -> R.string.studio_password_fair; else -> R.string.studio_password_weak })
        LinearProgressIndicator(progress={ (strength + 1) / 3f }, modifier=Modifier.fillMaxWidth(),
            color=if(strength==0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.studio_password_strength,strengthLabel),style=QuietTheme.typography.metadata)
    }
}

/** A local usability hint only; password length is never logged or transmitted. */
internal fun passwordStrength(value:String):Int = when {
    value.length >= 14 && value.any(Char::isLetter) && value.any { !it.isLetter() } -> 2
    value.length >= 10 -> 1
    else -> 0
}

@Composable private fun Options(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=QuietTheme.typography.metadataStrong)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(options,key={it}) { choice -> FilterChip(selected==choice,{onSelect(choice)},border=null,label={Text(choice)}) } }
}
@Composable private fun LabeledOptions(label:String,options:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=QuietTheme.typography.metadataStrong)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(options,key={it.first}) { (value, display) ->
            FilterChip(selected==value,{onSelect(value)},border=null,label={Text(display)})
        }
    }
}
internal fun annotationKindLabel(kind:com.jumlo.app.core.PdfAnnotationKind):Int = when(kind) {
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
                if(f.format!="PNG" || (f.toolId=="img_exif" && !f.flag1)) InteractiveIntSlider(stringResource(R.string.field_quality),draftNumber(f.quality,85),1,100,85) {v->edit {it.copy(quality=v.toString())}}
                Field(stringResource(R.string.field_output_prefix),f.prefix,{value->edit {it.copy(prefix=value)}})
                Text(stringResource(R.string.field_image_policy_note),style=QuietTheme.typography.metadata)
            }
            when(f.toolId) {
                "img_02" -> {
                    if(f.percentage.isBlank() && f.longestEdge.isBlank()) {
                        InteractiveIntSlider(stringResource(R.string.field_target_width),draftNumber(f.width,1600),1,8192,1600) {v->edit {it.copy(width=v.toString())}}
                        InteractiveIntSlider(stringResource(R.string.field_target_height),draftNumber(f.height,1600),1,8192,1600) {v->edit {it.copy(height=v.toString())}}
                    }
                    InteractiveIntSlider(stringResource(R.string.field_percentage_opt),draftNumber(f.percentage,100),1,400,100) {v->edit {it.copy(percentage=v.toString(),longestEdge="")}}
                    InteractiveIntSlider(stringResource(R.string.field_longest_edge_opt),draftNumber(f.longestEdge,1920),64,8192,1920) {v->edit {it.copy(longestEdge=v.toString(),percentage="")}}
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
                        InteractiveIntSlider(stringResource(R.string.field_paper_width),draftNumber(f.paperWidth,595),72,1440,595) {v->edit {it.copy(paperWidth=v.toString())}}
                        InteractiveIntSlider(stringResource(R.string.field_paper_height),draftNumber(f.paperHeight,842),72,1440,842) {v->edit {it.copy(paperHeight=v.toString())}}
                    }
                    InteractiveIntSlider(stringResource(R.string.field_margin_mm),draftNumber(f.margin,10),0,100,10) {v->edit {it.copy(margin=v.toString())}}
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
                    InteractiveIntSlider(stringResource(R.string.field_dpi),draftNumber(f.dpi,144),36,600,144) {v->edit {it.copy(dpi=v.toString())}}
                    Options(stringResource(R.string.field_output_format),listOf("PNG","JPEG"),f.format) {v->edit {it.copy(format=v)}}
                    if(f.format=="JPEG") InteractiveIntSlider(stringResource(R.string.field_quality),draftNumber(f.quality,85),1,100,85) {v->edit {it.copy(quality=v.toString())}}
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
                "pdf_05" -> { Toggle(stringResource(R.string.field_split_every_n),f.flag1) {v->edit {it.copy(flag1=v)}}; if(f.flag1) InteractiveIntSlider(stringResource(R.string.field_pages_per_part),draftNumber(f.num1,1),1,1000,1) {v->edit {it.copy(num1=v.toString())}} else Field(stringResource(if(f.flag2) R.string.jumlo_split_markers else R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_06" -> { Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_04" -> { Field(stringResource(R.string.field_organize_order),f.text1,{v->edit {it.copy(text1=v,pageEdits=emptyList())}}); Field(stringResource(R.string.field_rotate_pages),f.text2,{v->edit {it.copy(text2=v,pageEdits=emptyList())}}); Options(stringResource(R.string.field_rotate_degrees),listOf("0","90","180","270"),f.num1) {v->edit {it.copy(num1=v,pageEdits=emptyList())}} }
                "pdf_crop" -> { Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Text(stringResource(R.string.field_crop_hint),style=QuietTheme.typography.metadata) }
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
                    InteractiveToggle(stringResource(R.string.interactive_compression_target),f.num2.isNotBlank()) {v->edit {it.copy(num2=if(v) "5" else "")}}
                    if(f.num2.isNotBlank()) InteractiveIntSlider(stringResource(R.string.field_target_size_mb),draftNumber(f.num2,5),1,100,5) {v->edit {it.copy(num2=v.toString())}}
                    if(f.num2.isNotBlank()) Text(stringResource(R.string.field_target_size_note),style=QuietTheme.typography.metadata)
                    // The slider replaces the typed DPI and quality: the user chooses a
                    // trade-off and sees the estimated result before committing.
                    val dpi=f.num1.toIntOrNull()?:150
                    val quality=f.quality.toIntOrNull()?:70
                    val position=PdfCompressionPolicy.sliderPosition(dpi,quality)
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
                "pdf_signature" -> Text(stringResource(R.string.field_signature_note),style=QuietTheme.typography.metadata)
                "pdf_clean" -> {
                    Toggle(stringResource(R.string.field_remove_title),f.flag1){v->edit {it.copy(flag1=v)}}
                    Text(stringResource(R.string.field_clean_note),style=QuietTheme.typography.metadata)
                }
                "pdf_metadata" -> { Field(stringResource(R.string.field_title),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_author),f.text2,{v->edit {it.copy(text2=v)}}); Field(stringResource(R.string.field_subject),f.text3,{v->edit {it.copy(text3=v)}}); Field(stringResource(R.string.field_keywords),f.text4,{v->edit {it.copy(text4=v)}}); if(f.sourceIds.size==2) PdfInsertionControls(vm,f,edit) }
                "pdf_protect" -> {
                    Toggle(stringResource(R.string.field_remove_protection),f.flag1) {v->edit {it.copy(flag1=v)}}
                    Field(stringResource(R.string.field_user_password),f.secret1,{v->edit {it.copy(secret1=v)}},secret=true)
                    if(!f.flag1) {
                        Field(stringResource(R.string.field_owner_password),f.secret2,{v->edit {it.copy(secret2=v)}},secret=true)
                        Text(stringResource(R.string.studio_permissions),style=QuietTheme.typography.sectionTitle)
                        Toggle(stringResource(R.string.studio_allow_print),f.allowPrinting) {v->edit {it.copy(allowPrinting=v)}}
                        Toggle(stringResource(R.string.studio_allow_copy),f.allowCopy) {v->edit {it.copy(allowCopy=v)}}
                        Toggle(stringResource(R.string.studio_allow_modify),f.allowModify) {v->edit {it.copy(allowModify=v)}}
                        Toggle(stringResource(R.string.studio_allow_annotations),f.allowAnnotations) {v->edit {it.copy(allowAnnotations=v)}}
                        Toggle(stringResource(R.string.studio_allow_forms),f.allowForms) {v->edit {it.copy(allowForms=v)}}
                        Text(stringResource(R.string.studio_permissions_note),style=QuietTheme.typography.metadata)
                    }
                    Text(stringResource(R.string.field_protect_note),style=QuietTheme.typography.metadata)
                }
                "pdf_forms" -> Toggle(stringResource(R.string.field_flatten_form),f.flag1) {v->edit {it.copy(flag1=v)}}
                "pdf_to_docx","pdf_to_pptx","pdf_ocr" -> Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}})
                "img_exif" -> { Toggle(stringResource(R.string.field_exif_remove_all),f.flag1) {v->edit {it.copy(flag1=v)}}; Toggle(stringResource(R.string.field_exif_remove_location),f.flag2) {v->edit {it.copy(flag2=v)}}; Text(stringResource(R.string.field_exif_note),style=QuietTheme.typography.metadata) }
                "img_watermark" -> {
                    ContentToolSettings(f,edit)
                    InteractiveIntSlider(stringResource(R.string.field_opacity_percent),draftNumber(f.num1,40),1,100,40) {v->edit {it.copy(num1=v.toString())}}
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
                    InteractiveIntSlider(stringResource(R.string.field_gap_px),draftNumber(f.num1,8),0,500,8) {v->edit {it.copy(num1=v.toString())}}
                    if(f.toolId=="img_stitch") InteractiveIntSlider(stringResource(R.string.field_overlap_px),draftNumber(f.num2,0),0,400,0) {v->edit {it.copy(num2=v.toString())}}
                }
                "img_adjust" -> {
                    InteractiveIntSlider(stringResource(R.string.field_brightness),draftNumber(f.num1,0),-100,100,0) {v->edit {it.copy(num1=v.toString())}}
                    InteractiveIntSlider(stringResource(R.string.field_contrast),draftNumber(f.num2,100),0,300,100) {v->edit {it.copy(num2=v.toString())}}
                    InteractiveIntSlider(stringResource(R.string.field_saturation),draftNumber(f.num3,100),0,300,100) {v->edit {it.copy(num3=v.toString())}}
                    InteractiveIntSlider(stringResource(R.string.field_padding_px),draftNumber(f.num4,0),0,2000,0) {v->edit {it.copy(num4=v.toString())}}
                }
                "img_target" -> TargetImageSettings(f,edit)
                "img_compare" -> Text(stringResource(R.string.field_compare_img_note),style=QuietTheme.typography.metadata)
                "file_batch_rename" -> {
                    Field(stringResource(R.string.field_rename_pattern),f.text1,{v->edit {it.copy(text1=v)}},ltrValue=true)
                    Text(stringResource(R.string.studio_rename_patterns),style=QuietTheme.typography.metadataStrong)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(listOf("{name}","{n}","{date}")) { token ->
                            FilterChip(selected=false,border=null,onClick={edit {it.copy(text1=(it.text1+token).take(512))}},label={Text(token)})
                        }
                    }
                    InteractiveCounter(stringResource(R.string.field_start_number),draftNumber(f.num1,1),0,1000000) {v->edit {it.copy(num1=v.toString())}}
                    Text(stringResource(R.string.field_rename_note),style=QuietTheme.typography.metadata)
                    Text(stringResource(R.string.ui_rename_copy_note),style=QuietTheme.typography.metadata)
                }
                "file_duplicates" -> Text(stringResource(R.string.field_duplicates_note),style=QuietTheme.typography.metadata)
                "file_trash" -> Text(stringResource(R.string.field_trash_note),style=QuietTheme.typography.metadata)
                "file_encrypted_zip" -> { Toggle(stringResource(R.string.tool_extract_zip),f.flag1) {v->edit {it.copy(flag1=v)}}; Field(stringResource(R.string.field_zip_password),f.secret1,{v->edit {it.copy(secret1=v)}},secret=true); Text(stringResource(R.string.field_encrypted_zip_note),style=QuietTheme.typography.metadata) }
                "file_drive" -> {
                    Toggle(stringResource(R.string.field_drive_export_mode),f.flag1) {v->edit {it.copy(flag1=v)}}
                    if(!f.flag1) Field(stringResource(R.string.field_drive_file_id),f.text1,{v->edit {it.copy(text1=v)}})
                    else Field(stringResource(R.string.field_drive_folder_id),f.text2,{v->edit {it.copy(text2=v)}})
                    QuietTonalButton(text=stringResource(R.string.field_drive_signin),onClick=onGoogleSignIn)
                    Field(stringResource(R.string.field_drive_token),f.secret1,{v->edit {it.copy(secret1=v)}})
                    Text(stringResource(R.string.field_drive_note),style=QuietTheme.typography.metadata)
                }
                "dl_02","dl_03" -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = QuietTheme.shapes.card
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
