package com.jumlo.app.ui.workspace

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.jumlo.app.BuildConfig
import com.jumlo.app.R
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.processing.BatchRenameSettings
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.intake.Intake
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.intake.IntakeController
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.Date
import java.util.Locale

import com.jumlo.app.ui.components.formatBytes

private data class RenamePreviewInput(val names:List<String>,val settings:BatchRenameSettings)
private data class RenamePreviewState(val input:RenamePreviewInput,val result:Result<List<String>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkspaceScreen(
    vm:AppViewModel,form:WorkspaceDraft,imported:List<SourceRef>,busy:Boolean,urls:String,reduceMotion:Boolean,
    onImport:()->Unit,onPhotos:()->Unit,onGoogleSignIn:()->Unit,onExecute:()->Unit
) {
    val tool=form.toolId
    val download=tool.startsWith("dl_")
    val driveImport=tool=="file_drive" && !form.flag1
    val viewer=tool=="pdf_01"
    val imageConversion=tool.startsWith("img_") && tool !in setOf("img_compare","img_target")
    val hasAdvanced=tool !in setOf("file_08","file_duplicates","file_batch_rename","pdf_compare","img_compare","img_target","sha256")
    var previewId by rememberSaveable {mutableStateOf<String?>(null)}
    var advanced by rememberSaveable {mutableStateOf(false)}
    var renamePreviewOpen by rememberSaveable {mutableStateOf(false)}
    val queue=remember(form.sourceIds,imported) {val indexed=imported.associateBy {it.id};form.sourceIds.mapNotNull(indexed::get)}
    val preview=queue.find {it.mimeType?.startsWith("image/")==true || it.mimeType=="application/pdf"}
    val minimum=if(tool in setOf("pdf_03","pdf_compare","img_compare") || (tool=="img_watermark" && form.flag1)) 2 else 1
    val inputMismatch=remember(tool,queue) { queue.any {source->
        val mime=source.mimeType
        when {
            mime==null || mime=="application/octet-stream" -> false
            tool=="pdf_02" || tool.startsWith("img_") -> !mime.startsWith("image/")
            tool.startsWith("pdf_") && tool!="pdf_signature" -> mime!="application/pdf"
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
                lines.forEach {com.jumlo.app.core.HttpPolicy.url(it,form.cleartextConsent)}
            }
        }.exceptionOrNull()?.let(::stableError)
    }
    val countValid=tool !in setOf("pdf_07","file_08") || queue.size==1
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
    val ready=CapabilityRegistry.enabled(tool) && !busy && !inputMismatch && settingsError==null && countValid &&
        (tool!="file_batch_rename" || proposedNames!=null) &&
        (tool=="webpage_pdf" || driveImport || if(download) urls.isNotBlank() else queue.size>=minimum)
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=840.dp).align(Alignment.CenterHorizontally),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(!CapabilityRegistry.enabled(tool)) item {Text(stringResource(R.string.ui_tool_unavailable),color=MaterialTheme.colorScheme.error)}
            if(!download) {
                item(key="inputs") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        QuietTonalButton(stringResource(R.string.import_device_files),onImport,Modifier.weight(1f))
                        if(tool=="pdf_02" || tool.startsWith("img_")) QuietTonalButton(stringResource(R.string.import_photo_gallery),onPhotos,Modifier.weight(1f))
                    }
                }
                if(queue.isEmpty()) item {QuietStateCard(stringResource(R.string.ui_choose_inputs),stringResource(R.string.ui_choose_inputs_body))}
                if(preview!=null) item(key="preview-${preview.id}") {InputPreview(vm,preview.id,reduceMotion)}
                if(queue.isNotEmpty()) item {Text(stringResource(R.string.ui_input_count,queue.size),style=MaterialTheme.typography.titleSmall)}
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
                OutlinedTextField(urls,vm::setUrls,label={Text(stringResource(if(tool=="dl_03") R.string.workspace_direct_urls_batch else R.string.workspace_direct_url_single))},modifier=Modifier.fillMaxWidth(),minLines=if(tool=="dl_03") 4 else 2,textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr),trailingIcon={IconButton(onClick={clipboard.getText()?.text?.takeIf {it.isNotBlank()}?.let(vm::setUrls)}) {Icon(Icons.Rounded.ContentPaste,stringResource(R.string.action_paste))}})
                Text(stringResource(R.string.workspace_direct_url_hint),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ui_sensitive_draft_note),style=MaterialTheme.typography.bodySmall)
            }
            if(!viewer) {
                item(key="essential-settings") {
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(imageConversion) {
                            if(tool=="img_exif" && !form.flag1) Text(stringResource(R.string.ui_exif_jpeg_policy),style=MaterialTheme.typography.bodySmall)
                            else Options(stringResource(R.string.field_output_format),listOf("JPEG","PNG","WEBP"),form.format) {v->vm.updateForm {it.copy(format=v)}}
                            if(tool=="img_02") {
                                when {
                                    form.percentage.isNotBlank() -> Field(stringResource(R.string.field_percentage_opt),form.percentage,{v->vm.updateForm {it.copy(percentage=v)}},true)
                                    form.longestEdge.isNotBlank() -> Field(stringResource(R.string.field_longest_edge_opt),form.longestEdge,{v->vm.updateForm {it.copy(longestEdge=v)}},true)
                                    else -> {
                                        Field(stringResource(R.string.field_target_width),form.width,{v->vm.updateForm {it.copy(width=v)}},true)
                                        Field(stringResource(R.string.field_target_height),form.height,{v->vm.updateForm {it.copy(height=v)}},true)
                                    }
                                }
                            } else if(form.format!="PNG" || (tool=="img_exif" && !form.flag1)) Field(stringResource(R.string.field_quality),form.quality,{v->vm.updateForm {it.copy(quality=v)}},true)
                        }
                        when(tool) {
                            "img_target" -> TargetImageSettings(form,vm::updateForm)
                            "img_watermark","pdf_stamp","pdf_annotate" -> ContentToolSettings(form,vm::updateForm)
                            "file_batch_rename" -> {
                                Field(stringResource(R.string.field_rename_pattern),form.text1,{v->vm.updateForm {it.copy(text1=v)}})
                                Field(stringResource(R.string.field_start_number),form.num1,{v->vm.updateForm {it.copy(num1=v)}},true)
                                Text(stringResource(R.string.field_rename_note),style=MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.ui_rename_copy_note),style=MaterialTheme.typography.bodySmall)
                                if(renameInput!=null && renamePreview==null) Text(stringResource(R.string.ui_rename_checking),style=MaterialTheme.typography.bodySmall)
                                renamePreview?.exceptionOrNull()?.let {Text(stringResource(userMessageResource(stableError(it))),color=MaterialTheme.colorScheme.error)}
                                if(!proposedNames.isNullOrEmpty()) TextButton(onClick={renamePreviewOpen=true}) {Text(stringResource(R.string.ui_rename_preview_open,proposedNames.size))}
                            }
                            "sha256" -> Field(stringResource(R.string.field_expected_sha256),form.expectedHash,{v->vm.updateForm {it.copy(expectedHash=v)}})
                            "pdf_07" -> {
                                Field(stringResource(R.string.field_pages_spec),form.pages,{v->vm.updateForm {it.copy(pages=v)}})
                                Field(stringResource(R.string.field_dpi),form.dpi,{v->vm.updateForm {it.copy(dpi=v)}},true)
                            }
                            "file_08" -> Text(stringResource(R.string.field_zip_extract_note))
                            "file_duplicates" -> Text(stringResource(R.string.field_duplicates_note))
                            "pdf_compare" -> Text(stringResource(R.string.field_compare_pdf_note))
                            "img_compare" -> Text(stringResource(R.string.field_compare_img_note))
                        }
                        Text(stringResource(R.string.ui_destination),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(hasAdvanced) TextButton(onClick={advanced=true}) {Text(stringResource(R.string.ui_advanced))}
                        if(inputMismatch) Text(stringResource(R.string.msg_file_type),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        settingsError?.let {Text(stringResource(userMessageResource(it)),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                        if(!countValid && queue.isNotEmpty()) Text(stringResource(R.string.msg_one_file),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        if(tool=="pdf_03" && queue.size==1) Text(stringResource(R.string.ui_merge_minimum),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
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
            item {Text(stringResource(R.string.ui_advanced),style=MaterialTheme.typography.titleLarge)}
            item {WorkspaceFields(vm,form,vm::updateForm,onGoogleSignIn)}
            item {TextButton(onClick={advanced=false}) {Text(stringResource(R.string.action_close))}}
        }
    }
    previewId?.let {id->AlertDialog(onDismissRequest={previewId=null},title={Text(stringResource(R.string.dialog_preview_title))},text={InputPreview(vm,id,reduceMotion)},confirmButton={TextButton(onClick={previewId=null}) {Text(stringResource(R.string.action_close))}})}
    if(renamePreviewOpen && proposedNames!=null) AlertDialog(
        onDismissRequest={renamePreviewOpen=false},
        title={Text(stringResource(R.string.ui_rename_preview_title))},
        text={
            LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                itemsIndexed(proposedNames,key={index,_->queue[index].id}) {index,name ->
                    Column {
                        Text(queue[index].displayName,style=MaterialTheme.typography.bodyMedium.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr))
                        Text(stringResource(R.string.ui_rename_preview_name,name),style=MaterialTheme.typography.bodySmall.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr),color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={renamePreviewOpen=false}) {Text(stringResource(R.string.action_close))}}
    )
}

@Composable private fun InputPreview(vm:AppViewModel,sourceId:String,reduceMotion:Boolean=false) {
    var page by rememberSaveable(sourceId) { mutableIntStateOf(1) }
    var frame by remember(sourceId) { mutableStateOf<PreviewFrame?>(null) }
    var failure by remember(sourceId) { mutableStateOf<String?>(null) }
    var loading by remember(sourceId) { mutableStateOf(false) }
    LaunchedEffect(sourceId,page) {
        loading=true;failure=null;frame=null
        try { frame=vm.preview(sourceId,page) }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { failure=stableError(error) }
        finally { loading=false }
    }
    DisposableEffect(frame) { val previous=frame;onDispose { previous?.bitmap?.takeUnless { it.isRecycled }?.recycle() } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            QuietShimmerSkeleton(reduceMotion=reduceMotion)
        }
        failure?.let { Text(stringResource(userMessageResource(it)),color=MaterialTheme.colorScheme.error) }
        frame?.let { current ->
            Image(current.bitmap.asImageBitmap(),stringResource(R.string.dialog_preview_title),Modifier.fillMaxWidth().heightIn(min=120.dp,max=420.dp),contentScale=ContentScale.Fit)
            Text("${current.width} x ${current.height} | ${current.page} / ${current.count}")
            if(current.count>1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=page>1 && !loading,onClick={page--}) { Text(stringResource(R.string.action_previous)) }
                TextButton(enabled=page<current.count && !loading,onClick={page++}) { Text(stringResource(R.string.action_next)) }
            }
        }
    }
}

/** Target-size handler emits JPEG and selects quality itself; only these settings are consumed. */
@Composable fun TargetImageSettings(form:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ui_target_jpeg_policy),style=MaterialTheme.typography.bodySmall)
        Field(stringResource(R.string.field_target_bytes),form.num1.ifBlank {"200000"},{v->edit {it.copy(num1=v)}},true)
        Field(stringResource(R.string.field_output_prefix),form.prefix,{v->edit {it.copy(prefix=v)}})
    }
}

/** Operation-defining content stays in the main workspace, before the execution action. */
@Composable fun ContentToolSettings(form:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        when(form.toolId) {
            "img_watermark" -> {
                Toggle(stringResource(R.string.field_use_second_logo),form.flag1) {v->edit {it.copy(flag1=v)}}
                if(form.flag1) Text(stringResource(R.string.ui_watermark_logo_required),style=MaterialTheme.typography.bodySmall)
                else Field(stringResource(R.string.field_watermark_text),form.text1,{v->edit {it.copy(text1=v)}})
            }
            "pdf_stamp" -> {
                Field(stringResource(R.string.field_watermark_text),form.text1,{v->edit {it.copy(text1=v)}})
                Toggle(stringResource(R.string.field_add_page_numbers),form.flag1) {v->edit {it.copy(flag1=v)}}
            }
            "pdf_annotate" -> {
                Field(stringResource(R.string.field_text),form.text1,{v->edit {it.copy(text1=v)}})
                Field(stringResource(R.string.field_page_number),form.num1,{v->edit {it.copy(num1=v)}},true)
            }
        }
    }
}

@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,numeric:Boolean=false) {
    OutlinedTextField(value,{onValue(it.take(512))},label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable private fun Options(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(options,key={it}) { choice -> FilterChip(selected==choice,{onSelect(choice)},label={Text(choice)}) } }
}
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));Switch(checked,onChange)} }

@Composable private fun WorkspaceFields(vm:AppViewModel,f:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit,onGoogleSignIn:()->Unit={}) {
    QuietCard {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val image=f.toolId.startsWith("img_") && f.toolId !in setOf("img_compare","img_target")
            if(image) {
                if(f.toolId=="img_exif" && !f.flag1) Text(stringResource(R.string.ui_exif_jpeg_policy),style=MaterialTheme.typography.bodySmall)
                else Options(stringResource(R.string.field_output_format),listOf("JPEG","PNG","WEBP"),f.format) { value->edit {it.copy(format=value)} }
                if(f.format!="PNG" || (f.toolId=="img_exif" && !f.flag1)) Field(stringResource(R.string.field_quality),f.quality,{value->edit {it.copy(quality=value)}},true)
                Field(stringResource(R.string.field_output_prefix),f.prefix,{value->edit {it.copy(prefix=value)}})
                Text(stringResource(R.string.field_image_policy_note),style=MaterialTheme.typography.bodySmall)
            }
            when(f.toolId) {
                "img_02" -> {
                    if(f.percentage.isBlank() && f.longestEdge.isBlank()) {
                        Field(stringResource(R.string.field_target_width),f.width,{v->edit {it.copy(width=v)}},true)
                        Field(stringResource(R.string.field_target_height),f.height,{v->edit {it.copy(height=v)}},true)
                    }
                    Field(stringResource(R.string.field_percentage_opt),f.percentage,{v->edit {it.copy(percentage=v,longestEdge="")}},true)
                    Field(stringResource(R.string.field_longest_edge_opt),f.longestEdge,{v->edit {it.copy(longestEdge=v,percentage="")}},true)
                    Options(stringResource(R.string.field_fit_strategy),listOf("FIT","FILL","PAD","STRETCH"),f.fit) {v->edit {it.copy(fit=v)}}
                    Toggle(stringResource(R.string.field_allow_upscaling),f.allowUpscaling) {v->edit {it.copy(allowUpscaling=v)}}
                }
                "img_03" -> {
                    Text(stringResource(R.string.field_crop_hint))
                    Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true);Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true)
                    Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true);Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true)
                    Options(stringResource(R.string.field_clockwise_rotation),listOf("0","90","180","270"),(f.turns*90).toString()) {v->edit {it.copy(turns=v.toInt()/90)}}
                    Toggle(stringResource(R.string.field_flip_horizontal),f.flipX) {v->edit {it.copy(flipX=v)}};Toggle(stringResource(R.string.field_flip_vertical),f.flipY) {v->edit {it.copy(flipY=v)}}
                }
                "pdf_02" -> {
                                        Field(stringResource(R.string.field_paper_width),f.paperWidth,{v->edit {it.copy(paperWidth=v)}},true)
                    Field(stringResource(R.string.field_paper_height),f.paperHeight,{v->edit {it.copy(paperHeight=v)}},true)
                    Field(stringResource(R.string.field_margin_mm),f.margin,{v->edit {it.copy(margin=v)}},true)
                    Options(stringResource(R.string.field_orientation),listOf("AUTO","PORTRAIT","LANDSCAPE"),f.orientation) {v->edit {it.copy(orientation=v)}}
                    Toggle(stringResource(R.string.field_fill_page),f.fill) {v->edit {it.copy(fill=v)}}
                }
                "pdf_07" -> {
                    Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}})
                    Field(stringResource(R.string.field_dpi),f.dpi,{v->edit {it.copy(dpi=v)}},true)
                    Options(stringResource(R.string.field_output_format),listOf("PNG","JPEG"),f.format) {v->edit {it.copy(format=v)}}
                    if(f.format=="JPEG") Field(stringResource(R.string.field_quality),f.quality,{v->edit {it.copy(quality=v)}},true)
                    Toggle(stringResource(R.string.field_grayscale),f.grayscale) {v->edit {it.copy(grayscale=v)}}
                    Text(stringResource(R.string.field_raster_note))
                }
                "file_07" -> { Field(stringResource(R.string.field_zip_level),f.zipLevel,{v->edit {it.copy(zipLevel=v)}},true);Text(stringResource(R.string.field_zip_create_note)) }
                "file_08" -> Text(stringResource(R.string.field_zip_extract_note))
                "sha256" -> Field(stringResource(R.string.field_expected_sha256),f.expectedHash,{v->edit {it.copy(expectedHash=v)}})
                "pdf_03" -> {  Text(stringResource(R.string.field_merge_note)) }
                "pdf_05" -> {  Toggle(stringResource(R.string.field_split_every_n),f.flag1) {v->edit {it.copy(flag1=v)}}; if(f.flag1) Field(stringResource(R.string.field_pages_per_part),f.num1,{v->edit {it.copy(num1=v)}},true) else Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_06" -> {  Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}) }
                "pdf_04" -> {  Field(stringResource(R.string.field_organize_order),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_rotate_pages),f.text2,{v->edit {it.copy(text2=v)}}); Field(stringResource(R.string.field_rotate_degrees),f.num1,{v->edit {it.copy(num1=v)}},true) }
                "pdf_crop" -> {  Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_compress" -> {  Field(stringResource(R.string.field_max_image_dpi),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_quality),f.quality,{v->edit {it.copy(quality=v)}},true); Text(stringResource(R.string.field_compress_note)) }
                "pdf_stamp" -> Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}})
                "pdf_annotate" -> { Field(stringResource(R.string.field_x_norm),f.num2,{v->edit {it.copy(num2=v)}},true); Field(stringResource(R.string.field_y_norm),f.num3,{v->edit {it.copy(num3=v)}},true); Toggle(stringResource(R.string.field_as_note_annotation),f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_signature" -> {  Text(stringResource(R.string.field_signature_note)); com.jumlo.app.ui.scan.SignaturePad { file -> vm.intakeLocalFile(file,"signature.png","image/png") }; Field(stringResource(R.string.field_page_number),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true) }
                "pdf_metadata" -> {  Field(stringResource(R.string.field_title),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_author),f.text2,{v->edit {it.copy(text2=v)}}); Field(stringResource(R.string.field_subject),f.text3,{v->edit {it.copy(text3=v)}}); Field(stringResource(R.string.field_keywords),f.text4,{v->edit {it.copy(text4=v)}}); Field(stringResource(R.string.field_insert_at_page),f.num1,{v->edit {it.copy(num1=v)}},true) }
                "pdf_protect" -> {  Toggle(stringResource(R.string.field_remove_protection),f.flag1) {v->edit {it.copy(flag1=v)}}; Field(stringResource(R.string.field_user_password),f.secret1,{v->edit {it.copy(secret1=v)}}); if(!f.flag1) Field(stringResource(R.string.field_owner_password),f.secret2,{v->edit {it.copy(secret2=v)}}); Text(stringResource(R.string.field_protect_note)) }
                "pdf_forms" -> {  Field(stringResource(R.string.field_forms_json),f.text1,{v->edit {it.copy(text1=v)}}); Toggle(stringResource(R.string.field_flatten_form),f.flag1) {v->edit {it.copy(flag1=v)}} }
                "pdf_compare" -> Text(stringResource(R.string.field_compare_pdf_note))
                "pdf_redact" -> {  Field(stringResource(R.string.field_page_number),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_left),f.cropLeft,{v->edit {it.copy(cropLeft=v)}},true); Field(stringResource(R.string.field_top),f.cropTop,{v->edit {it.copy(cropTop=v)}},true); Field(stringResource(R.string.field_right),f.cropRight,{v->edit {it.copy(cropRight=v)}},true); Field(stringResource(R.string.field_bottom),f.cropBottom,{v->edit {it.copy(cropBottom=v)}},true); Field(stringResource(R.string.field_verify_redacted_text),f.text1,{v->edit {it.copy(text1=v)}}); Text(stringResource(R.string.field_redact_note)) }
                "pdf_ocr" -> { Field(stringResource(R.string.field_pages_spec),f.pages,{v->edit {it.copy(pages=v)}}); Text(stringResource(R.string.field_ocr_note)) }
                "webpage_pdf" -> { Field(stringResource(R.string.field_webpage_url),f.text1,{v->edit {it.copy(text1=v)}}); Text(stringResource(R.string.field_webpage_note)) }
                "img_exif" -> { Toggle(stringResource(R.string.field_exif_remove_all),f.flag1) {v->edit {it.copy(flag1=v)}}; Toggle(stringResource(R.string.field_exif_remove_location),f.flag2) {v->edit {it.copy(flag2=v)}}; Text(stringResource(R.string.field_exif_note)) }
                "img_watermark" -> Field(stringResource(R.string.field_opacity_percent),f.num1,{v->edit {it.copy(num1=v)}},true)
                "img_join","img_stitch" -> {
                    if(f.toolId=="img_join") Options(stringResource(R.string.field_layout),listOf("GRID2","HSTRIP","VSTRIP"),f.text1.ifBlank{"GRID2"}) {v->edit {it.copy(text1=v)}}
                    Field(stringResource(R.string.field_gap_px),f.num1,{v->edit {it.copy(num1=v)}},true)
                    if(f.toolId=="img_stitch") Field(stringResource(R.string.field_overlap_px),f.num2,{v->edit {it.copy(num2=v)}},true)
                }
                "img_adjust" -> { Field(stringResource(R.string.field_brightness),f.num1,{v->edit {it.copy(num1=v)}},true); Field(stringResource(R.string.field_contrast),f.num2,{v->edit {it.copy(num2=v)}},true); Field(stringResource(R.string.field_saturation),f.num3,{v->edit {it.copy(num3=v)}},true); Field(stringResource(R.string.field_padding_px),f.num4,{v->edit {it.copy(num4=v)}},true) }
                "img_target" -> TargetImageSettings(f,edit)
                "img_compare" -> Text(stringResource(R.string.field_compare_img_note))
                "file_batch_rename" -> { Field(stringResource(R.string.field_rename_pattern),f.text1,{v->edit {it.copy(text1=v)}}); Field(stringResource(R.string.field_start_number),f.num1,{v->edit {it.copy(num1=v)}},true); Text(stringResource(R.string.field_rename_note)) }
                "file_duplicates" -> Text(stringResource(R.string.field_duplicates_note))
                "file_trash" -> Text(stringResource(R.string.field_trash_note))
                "file_encrypted_zip" -> {  Toggle(stringResource(R.string.field_extract_instead),f.flag1) {v->edit {it.copy(flag1=v)}}; Field(stringResource(R.string.field_zip_password),f.secret1,{v->edit {it.copy(secret1=v)}}); Text(stringResource(R.string.field_encrypted_zip_note)) }
                "file_drive" -> {
                    Toggle(stringResource(R.string.field_drive_export_mode),f.flag1) {v->edit {it.copy(flag1=v)}}
                    if(!f.flag1) Field(stringResource(R.string.field_drive_file_id),f.text1,{v->edit {it.copy(text1=v)}})
                    else Field(stringResource(R.string.field_drive_folder_id),f.text2,{v->edit {it.copy(text2=v)}})
                    QuietTonalButton(text=stringResource(R.string.field_drive_signin),onClick=onGoogleSignIn)
                    Field(stringResource(R.string.field_drive_token),f.secret1,{v->edit {it.copy(secret1=v)}})
                    Text(stringResource(R.string.field_drive_note),style=MaterialTheme.typography.bodySmall)
                }
                "dl_02","dl_03" -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
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
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if(f.outputName.isNotBlank() && f.outputName != "download.bin") f.outputName else stringResource(R.string.workspace_direct_url_hint),
                                    style = MaterialTheme.typography.bodySmall,
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
