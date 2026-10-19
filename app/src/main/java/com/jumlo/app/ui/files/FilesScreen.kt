package com.jumlo.app.ui.files

import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jumlo.app.R
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.files.DocumentListing
import com.jumlo.app.data.files.previewDocumentRename
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*

@Composable fun FilesScreen(vm:AppViewModel,onImport:()->Unit,onFolder:()->Unit,onSavedFolder:()->Unit,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onSource:(String)->Unit,onBrowse:(DocumentEntry)->Unit,onProviderOpen:(DocumentEntry)->Unit,onProviderImport:(DocumentEntry)->Unit,onCopy:(DocumentEntry)->Unit,onRename:(DocumentEntry)->Unit,onDelete:(DocumentEntry)->Unit,onNewFolder:()->Unit,
    onOutputWorkflow:(List<OutputRecord>,String)->Unit,onSourceWorkflow:(List<SourceRef>,String)->Unit) {
    val haptics=rememberAppHaptics()
    val context=LocalContext.current
    val imported by vm.imported.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tree by vm.tree.collectAsStateWithLifecycle()
    var section by rememberSaveable {mutableStateOf("outputs")}
    var search by rememberSaveable {mutableStateOf("")}
    var sort by rememberSaveable {mutableStateOf("date")}
    var type by rememberSaveable {mutableStateOf("all")}
    var sortPicker by rememberSaveable {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    var selecting by rememberSaveable {mutableStateOf(false)}
    var selectedSources by remember { mutableStateOf(setOf<String>()) }
    var selectingSources by rememberSaveable { mutableStateOf(false) }
    var deleteIds by remember {mutableStateOf<Set<String>?>(null)}
    var purgeIds by remember {mutableStateOf<Set<String>?>(null)}
    var details by remember {mutableStateOf<OutputRecord?>(null)}
    LaunchedEffect(outputs) {selected=selected.intersect(outputs.map {it.id}.toSet())}
    LaunchedEffect(imported) {selectedSources=selectedSources.intersect(imported.map {it.id}.toSet())}
    val screenWidth=LocalConfiguration.current.screenWidthDp
    val columns=when { screenWidth>=840->4;screenWidth>=600->3;else->2 }
    val visible=remember(outputs,search,sort,type,settings.favoriteOutputIds) {
        outputs.filter {it.displayName.contains(search,true) && (type=="all" || (type=="favorites" && it.id in settings.favoriteOutputIds) || StoragePaths.category(it.mimeType)==type)}.let {list->
            when(sort) {"name"->list.sortedBy {it.displayName.lowercase()};"size"->list.sortedByDescending {it.sizeBytes};"type"->list.sortedWith(compareBy({StoragePaths.category(it.mimeType)},{it.displayName}));else->list.sortedByDescending {it.createdAtEpochMs}}
        }
    }
    val visibleSources=remember(imported,search,sort) {
        imported.filter {it.displayName.contains(search,true)}.let {list->
            if(sort=="name") list.sortedBy {it.displayName.lowercase()} else list
        }
    }
    val visibleTrashed=remember(trashed,search) {
        trashed.filter {it.displayName.contains(search,true)}
    }
    val visibleProviderEntries=remember(tree,search) {
        tree?.entries?.filter {it.name.contains(search,true)}.orEmpty()
    }
    val selectedOutputs = visible.filter { it.id in selected }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=QuietTheme.spacing.listBottomBuffer),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item("heading") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(selecting && section=="outputs") stringResource(R.string.neon_selection_count,selected.size)
                else stringResource(R.string.neon_my_documents),style=QuietTheme.typography.screenTitle)
            Text(if(settings.downloadDestinationFolder.startsWith("content://")) stringResource(R.string.redesign_custom_saved_path)
                else stringResource(R.string.redesign_storage_path),style=QuietTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={haptics.actionClick();onSavedFolder()},modifier=Modifier.weight(1f).pressFeedback()) { Icon(Icons.Rounded.FolderOpen,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.redesign_open_folder),maxLines=1,overflow=TextOverflow.Ellipsis) }
                QuietPrimaryButton(text=stringResource(R.string.import_device_files),
                    onClick={onImport()},modifier=Modifier.weight(1f),leadingIcon=Icons.Rounded.Add)
            }
        } }
        item("search") {QuietSearchBar(search,stringResource(R.string.ui_search_files),{search=it;selected=emptySet()},{search="";selected=emptySet()})}
        item("sections") {LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(listOf("outputs" to R.string.ui_outputs,"sources" to R.string.ui_imported,"folder" to R.string.ui_granted_folder,"trash" to R.string.files_trash_title),key={it.first}) {(key,label)->FilterChip(section==key,{haptics.selectionToggle();section=key;selecting=false;selected=emptySet();selectingSources=false;selectedSources=emptySet()},label={Text(stringResource(label),style=QuietTheme.typography.metadataStrong)})}
        }}
        if(section=="outputs" && outputs.any { it.publicUri==null }) item("public-store-warning") {
            Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.65f),shape=MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.redesign_save_fallback),modifier=Modifier.padding(14.dp),
                    color=MaterialTheme.colorScheme.onErrorContainer,style=QuietTheme.typography.metadata)
            }
        }
        when(section) {
            "outputs" -> {
                item("filter") { Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        items(listOf("all","favorites","Video","Audio","PDF","Images","Documents")) {filter->
                            FilterChip(type==filter,{haptics.selectionToggle();type=filter;selected=emptySet()},label={Text(when(filter){"all"->stringResource(R.string.ui_all);"favorites"->stringResource(R.string.files_favorites);"Video"->stringResource(R.string.redesign_filter_video);"Audio"->stringResource(R.string.redesign_filter_audio);"PDF"->"PDF";"Images"->stringResource(R.string.redesign_filter_images);else->stringResource(R.string.redesign_filter_documents)},style=QuietTheme.typography.metadataStrong)})
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        TextButton(onClick={haptics.actionClick();sortPicker=true}) { Icon(Icons.AutoMirrored.Rounded.Sort,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.redesign_sort),style=QuietTheme.typography.metadataStrong) }
                        IconButton(onClick={haptics.selectionToggle();vm.setFileViewMode(when(settings.fileViewMode) {FileViewMode.LIST->FileViewMode.GRID;FileViewMode.GRID->FileViewMode.GALLERY;FileViewMode.GALLERY->FileViewMode.LIST})}) {
                            Icon(when(settings.fileViewMode){FileViewMode.LIST->Icons.AutoMirrored.Rounded.ViewList;FileViewMode.GRID->Icons.Rounded.GridView;FileViewMode.GALLERY->Icons.Rounded.PhotoLibrary},stringResource(R.string.redesign_file_view))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick={haptics.selectionToggle();selecting=!selecting;selected=emptySet()},enabled=outputs.isNotEmpty()) {Text(stringResource(if(selecting) R.string.action_cancel else R.string.ui_select_files),style=QuietTheme.typography.metadataStrong)}
                    }
                    if(selecting) TextButton(
                        onClick={haptics.selectionToggle();selected=visible.map { it.id }.toSet()}
                    ) { Text(stringResource(R.string.redesign_select_all)) }
                    else if(outputs.isNotEmpty()) TextButton(onClick={
                        haptics.secondaryAction();deleteIds=outputs.map { it.id }.toSet()
                    }) { Text(stringResource(R.string.redesign_delete_all),color=MaterialTheme.colorScheme.error) }
                } }
                if(visible.isEmpty()) item("empty") {QuietStateCard(stringResource(R.string.ui_recent_empty),stringResource(R.string.redesign_storage_folder))}
                if(settings.fileViewMode==FileViewMode.LIST) {
                    items(visible,key={it.id},contentType={"output"}) {output -> OutputRow(output,{if(selecting) selected=selected.symmetric(output.id) else onOpen(output)},{onShare(output)},{onExport(output)},{onReuse(output)},{deleteIds=setOf(output.id)},if(selecting) output.id in selected else null,{selected=selected.symmetric(output.id)},onDetails={details=output},isFavorite=output.id in settings.favoriteOutputIds,
                        onFavorite={vm.toggleOutputFavorite(output.id)})}
                } else {
                    items(visible.chunked(columns),key={it.joinToString("-") {f->f.id}},contentType={"tileRow"}) {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                            row.forEach {output->
                                val cardHeight=if(settings.fileViewMode==FileViewMode.GALLERY) 340.dp else 288.dp
                                Surface(
                                    onClick={haptics.actionClick();if(selecting) selected=selected.symmetric(output.id) else onOpen(output)},
                                    modifier=Modifier.pressFeedback().weight(1f).height(cardHeight),
                                    shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                                    border=androidx.compose.foundation.BorderStroke(1.dp,
                                        if(selecting && output.id in selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant),
                                    color=MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.SpaceBetween) {
                                        Box(Modifier.fillMaxWidth()
                                            .height(if(settings.fileViewMode==FileViewMode.GALLERY) 194.dp else 150.dp)) {
                                            NeonOutputPreview(output, Modifier.fillMaxSize())
                                            if(selecting) Icon(
                                                if(output.id in selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                stringResource(R.string.ui_select_files),
                                                tint=MaterialTheme.colorScheme.primary,
                                                modifier=Modifier.align(Alignment.TopEnd).padding(7.dp).size(27.dp)
                                            )
                                        }
                                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                            Text(output.displayName,style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold,textDirection=TextDirection.Content),maxLines=2,overflow=TextOverflow.Ellipsis)
                                            Text(formatOutputSizeSummary(output),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        }
                                        if(!selecting) {
                                            var actionsExpanded by remember(output.id) { mutableStateOf(false) }
                                            Row(horizontalArrangement=Arrangement.End,modifier=Modifier.fillMaxWidth()) {
                                                IconButton(onClick={haptics.selectionToggle();vm.toggleOutputFavorite(output.id)},modifier=Modifier.pressFeedback().size(48.dp)) {
                                                    Icon(if(output.id in settings.favoriteOutputIds) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                        stringResource(if(output.id in settings.favoriteOutputIds) R.string.files_unmark_favorite else R.string.files_mark_favorite),Modifier.size(20.dp))
                                                }
                                                Box {
                                                    IconButton(onClick={haptics.actionClick();actionsExpanded=true},modifier=Modifier.pressFeedback().size(48.dp)) {
                                                        Icon(Icons.Rounded.MoreVert,stringResource(R.string.ui_more_actions),Modifier.size(22.dp))
                                                    }
                                                    DropdownMenu(expanded=actionsExpanded,onDismissRequest={actionsExpanded=false}) {
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_share))},onClick={actionsExpanded=false;onShare(output)})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_details))},onClick={actionsExpanded=false;details=output})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_save_as))},onClick={actionsExpanded=false;onExport(output)})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_use_as_input))},onClick={actionsExpanded=false;onReuse(output)})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.action_move_to_trash))},onClick={actionsExpanded=false;deleteIds=setOf(output.id)})
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            "sources" -> {
                item("source-controls") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.files_snapshots_sub),style=QuietTheme.typography.metadata,modifier=Modifier.weight(1f))
                        TextButton(enabled=imported.isNotEmpty(),onClick={
                            haptics.selectionToggle();selectingSources=!selectingSources;selectedSources=emptySet()
                        }) {Text(stringResource(if(selectingSources) R.string.action_cancel else R.string.ui_select_files))}
                    }
                }
                if(selectingSources) item("source-batch-actions") {
                    val matching=visibleSources.filter { it.id in selectedSources }
                    val onlyPdfs=matching.size>=2 && matching.all {it.mimeType.equals("application/pdf",true)}
                    val onlyImages=matching.isNotEmpty() && matching.all {it.mimeType?.startsWith("image/")==true}
                    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        TextButton(onClick={selectedSources=visibleSources.map {it.id}.toSet()}) {
                            Text(stringResource(R.string.redesign_select_all))
                        }
                        if(matching.isNotEmpty()) {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick={
                                    haptics.actionClick();onSourceWorkflow(matching,"file_07");selectingSources=false;selectedSources=emptySet()
                                },modifier=Modifier.weight(1f)) {
                                    Text(stringResource(R.string.files_bundle_selected,matching.size),maxLines=1)
                                }
                                if(onlyPdfs) FilledTonalButton(onClick={
                                    haptics.actionClick();onSourceWorkflow(matching,"pdf_03");selectingSources=false;selectedSources=emptySet()
                                },modifier=Modifier.weight(1f)) {
                                    Text(stringResource(R.string.files_merge_selected,matching.size),maxLines=1)
                                }
                                if(onlyImages) FilledTonalButton(onClick={
                                    haptics.actionClick();onSourceWorkflow(matching,"pdf_02");selectingSources=false;selectedSources=emptySet()
                                },modifier=Modifier.weight(1f)) {
                                    Text(stringResource(R.string.files_images_to_pdf_selected,matching.size),maxLines=1)
                                }
                            }
                        } else Text(stringResource(R.string.files_batch_tool_hint),style=QuietTheme.typography.metadata)
                    }
                }
                if(imported.isEmpty()) item("sources-empty") {QuietStateCard(stringResource(R.string.files_empty_snapshots),stringResource(R.string.files_snapshots_sub),stringResource(R.string.import_device_files),onImport)}
                items(visibleSources,key={it.id}) {source->
                    FileMenuRow(source.displayName,formatBytes(source.sizeBytes),
                        {if(selectingSources) selectedSources=selectedSources.symmetric(source.id) else onSource(source.id)},
                        selection=if(selectingSources) source.id in selectedSources else null,
                        onSelect={selectedSources=selectedSources.symmetric(source.id)}) {close->
                        DropdownMenuItem(text={Text(stringResource(R.string.files_add_to_workspace))},onClick={close();onSource(source.id)})
                    }
                }
            }
            "trash" -> {
                item {Text(stringResource(R.string.ui_trash_notice),style=QuietTheme.typography.metadata)}
                if(trashed.isEmpty()) item("trash-empty") {QuietStateCard(stringResource(R.string.files_trash_title),stringResource(R.string.files_trash_empty))}
                else item {TextButton(onClick={purgeIds=trashed.map {it.id}.toSet()}) {Text(stringResource(R.string.files_purge_trash))}}
                items(visibleTrashed,key={it.id}) {entry->FileMenuRow(entry.displayName,formatBytes(entry.sizeBytes),{vm.restoreOutput(entry.id)},Icons.Rounded.DeleteOutline) {close->
                    DropdownMenuItem(text={Text(stringResource(R.string.action_restore))},onClick={close();vm.restoreOutput(entry.id)})
                    DropdownMenuItem(text={Text(stringResource(R.string.action_delete))},onClick={close();purgeIds=setOf(entry.id)})
                }}
            }
            "folder" -> {
                item {OutlinedButton(onClick=onFolder) {Text(stringResource(R.string.tool_browse_storage))}}
                val listing=tree
                if(listing==null) item("folder-empty") {QuietStateCard(stringResource(R.string.ui_granted_folder),stringResource(R.string.ui_folder_empty),stringResource(R.string.tool_browse_storage),onFolder)}
                else {
                    item {Text(listing.name,style=QuietTheme.typography.sectionTitle);Text(stringResource(R.string.ui_provider_notice),style=QuietTheme.typography.metadata)}
                    if(listing.writable) item {TextButton(onClick=onNewFolder) {Text(stringResource(R.string.files_new_folder))}}
                    items(visibleProviderEntries,key={it.uri}) {entry->ProviderFileRow(entry,listing.writable,{onBrowse(entry)},{onProviderOpen(entry)},{onProviderImport(entry)},{onCopy(entry)},{onRename(entry)},{onDelete(entry)})}
                }
            }
        }
    }
    if(section=="outputs" && selecting && selectedOutputs.isNotEmpty()) {
        val onlyPdfs = selectedOutputs.size >= 2 &&
            selectedOutputs.all { it.mimeType=="application/pdf" }
        val onlyImages = selectedOutputs.isNotEmpty() &&
            selectedOutputs.all { it.mimeType.startsWith("image/") }
        Surface(
            modifier=Modifier.align(Alignment.BottomCenter)
                .padding(start=14.dp,end=14.dp,bottom=98.dp),
            color=MaterialTheme.colorScheme.surfaceContainerHigh,
            shape=androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            border=androidx.compose.foundation.BorderStroke(
                1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.5f)),
            shadowElevation=9.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=7.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceEvenly) {
                NeonBatchAction(Icons.Rounded.Share,stringResource(R.string.action_share),{
                    haptics.actionClick()
                    val uris=ArrayList(selectedOutputs.map {
                        android.net.Uri.parse(it.publicUri ?: it.uri)
                    })
                    try {
                        val mime=selectedOutputs.map { it.mimeType }.distinct()
                            .singleOrNull() ?: "*/*"
                        val intent=android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE)
                            .apply {
                                type=mime
                                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM,uris)
                                clipData=android.content.ClipData.newUri(
                                    context.contentResolver,"Jumlo",uris.first()).also { clip ->
                                        uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                                    }
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(android.content.Intent.createChooser(intent,null))
                    } catch (_:Exception) { vm.notifyMessage("NO_COMPATIBLE_APP_OR_ACCESS_LOST") }
                })
                NeonBatchAction(Icons.Rounded.FolderZip,stringResource(R.string.action_save_as),{
                    haptics.actionClick()
                    onOutputWorkflow(selectedOutputs,"file_07")
                    selected=emptySet();selecting=false
                })
                if(onlyPdfs) NeonBatchAction(Icons.Rounded.MergeType,
                    stringResource(R.string.ui_merge_pdf),{
                        haptics.actionClick()
                        onOutputWorkflow(selectedOutputs,"pdf_03")
                        selected=emptySet();selecting=false
                    })
                else if(onlyImages) NeonBatchAction(Icons.Rounded.PictureAsPdf,
                    stringResource(R.string.ui_images_pdf),{
                        haptics.actionClick()
                        onOutputWorkflow(selectedOutputs,"pdf_02")
                        selected=emptySet();selecting=false
                    })
                NeonBatchAction(Icons.Rounded.DeleteOutline,stringResource(R.string.action_delete),{
                    haptics.secondaryAction()
                    deleteIds=selectedOutputs.map { it.id }.toSet()
                },destructive=true)
            }
        }
    }
    }
    if(sortPicker) AlertDialog(onDismissRequest={sortPicker=false},title={Text(stringResource(R.string.redesign_sort))},text={Column {
        listOf("date" to R.string.redesign_sort_date,"size" to R.string.redesign_sort_size,"name" to R.string.redesign_sort_name,"type" to R.string.redesign_sort_type).forEach { (key,label)->
            TextButton(onClick={sort=key;sortPicker=false},modifier=Modifier.fillMaxWidth()) {Text(stringResource(label))}
        }
    }},confirmButton={TextButton(onClick={sortPicker=false}) {Text(stringResource(R.string.account_cancel))}})
    deleteIds?.let { ids -> AlertDialog(onDismissRequest={deleteIds=null},title={Text(stringResource(R.string.dialog_delete_title))},text={Text(stringResource(R.string.redesign_delete_confirm))},confirmButton={
        TextButton(onClick={haptics.secondaryAction();vm.trashOutputs(outputs.filter {it.id in ids});selected=emptySet();selecting=false;deleteIds=null}) {Text(stringResource(R.string.action_delete))}
    },dismissButton={TextButton(onClick={deleteIds=null}) {Text(stringResource(R.string.account_cancel))}})}
    details?.let {output->AlertDialog(onDismissRequest={details=null},title={Text(output.displayName)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(output.mimeType,style=QuietTheme.typography.metadata)
        Text(formatOutputSizeSummary(output),style=QuietTheme.typography.bodyMedium)
        output.metadata.pageCount?.let { Text(stringResource(R.string.output_pages_count, it),style=QuietTheme.typography.metadata) }
        Text(stringResource(R.string.output_sha256_label, output.sha256.take(16) + "…"),style=QuietTheme.typography.metadata)
        val savedDescription=if(output.publicUri.isNullOrBlank()) {
            stringResource(R.string.output_saved_privately)
        } else {
            val folder=buildString {
                append("Jumlo / ").append(StoragePaths.category(output.mimeType))
                output.metadata.subfolder?.takeIf { it.isNotBlank() }?.let { append(" / ").append(it) }
            }
            stringResource(R.string.output_saved_location,folder)
        }
        Text(savedDescription,style=QuietTheme.typography.metadata)
    }},confirmButton={TextButton(onClick={details=null}) {Text(stringResource(R.string.account_done))}})}
    purgeIds?.let {ids->AlertDialog(onDismissRequest={purgeIds=null},title={Text(stringResource(R.string.ui_purge_title))},text={Text(stringResource(R.string.ui_purge_body,ids.size))},confirmButton={TextButton(onClick={purgeIds=null;vm.purgeTrashIds(ids)}) {Text(stringResource(R.string.ui_delete_forever))}},dismissButton={TextButton(onClick={purgeIds=null}) {Text(stringResource(R.string.action_cancel))}})}
}

@Composable
private fun RowScope.NeonBatchAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onAction: () -> Unit,
    destructive: Boolean = false
) {
    Column(Modifier.weight(1f).pressFeedback(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onAction) {
            Icon(icon, label, Modifier.size(23.dp),
                tint = if(destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
        }
        Text(label, style = QuietTheme.typography.metadata,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Set<String>.symmetric(id:String):Set<String> = if(id in this) this-id else this+id

@Composable internal fun ProviderFileRow(
    entry: DocumentEntry,
    destinationWritable: Boolean,
    onBrowse: () -> Unit,
    onOpen: () -> Unit,
    onImport: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val showMutations = !entry.isDirectory && (entry.canRename || entry.canDelete)
    FileMenuRow(entry.name,
        if(entry.isDirectory) stringResource(R.string.files_folder_label) else formatBytes(entry.sizeBytes),
        if(entry.readable) ({if(entry.isDirectory) onBrowse() else onOpen()}) else null,
        if(entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
        showOverflow=entry.readable || showMutations
    ) { close ->
        if(entry.isDirectory && entry.readable) DropdownMenuItem(text={Text(stringResource(R.string.files_browse_inside))},onClick={close();onBrowse()})
        if(!entry.isDirectory && entry.readable) {
            DropdownMenuItem(text={Text(stringResource(R.string.action_open))},onClick={close();onOpen()})
            DropdownMenuItem(text={Text(stringResource(R.string.files_import_action))},onClick={close();onImport()})
            if(destinationWritable) DropdownMenuItem(text={Text(stringResource(R.string.action_copy))},onClick={close();onCopy()})
        }
        if(!entry.isDirectory) {
            if(entry.canRename) DropdownMenuItem(text={Text(stringResource(R.string.action_rename))},onClick={close();onRename()})
            if(entry.canDelete) DropdownMenuItem(text={Text(stringResource(R.string.files_delete_original))},onClick={close();onDelete()})
        }
    }
}

@Composable internal fun ProviderRenameDialog(
    sourceUri: String,
    enteredName: String,
    listing: DocumentListing?,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val preview = remember(listing,sourceUri,enteredName) {previewDocumentRename(listing,sourceUri,enteredName)}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(stringResource(R.string.dialog_rename_title))},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(enteredName,onNameChange,singleLine=true,modifier=Modifier.fillMaxWidth(),
                label={Text(stringResource(R.string.files_rename_new_name))},
                textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Content),
                isError=preview.errorCode!=null)
            preview.name?.let {name->
                Text(stringResource(R.string.files_rename_proposed),style=QuietTheme.typography.metadataStrong)
                TechnicalText(name)
            }
            val message = when(preview.errorCode) {
                "INVALID_FILENAME" -> R.string.files_rename_invalid_name
                "DESTINATION_EXISTS" -> R.string.files_rename_exists
                "ACCESS_LOST" -> R.string.msg_access_lost
                "PROVIDER_RENAME_UNAVAILABLE" -> R.string.msg_provider_unavailable
                else -> if(!preview.canConfirm) R.string.files_rename_unchanged else null
            }
            message?.let {Text(stringResource(it),style=QuietTheme.typography.metadata,
                color=if(preview.errorCode!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
        }},
        confirmButton={TextButton(enabled=preview.canConfirm,onClick={
            if(preview.canConfirm) preview.name?.let(onConfirm)
        }) {Text(stringResource(R.string.action_rename))}},
        dismissButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.action_cancel))}}
    )
}
