package com.jumlo.app.ui.files

import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jumlo.app.R
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.files.DocumentListing
import com.jumlo.app.data.files.previewDocumentRename
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*

@Composable fun FilesScreen(vm:AppViewModel,onImport:()->Unit,onFolder:()->Unit,onSavedFolder:()->Unit,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onSource:(String)->Unit,onBrowse:(DocumentEntry)->Unit,onProviderOpen:(DocumentEntry)->Unit,onProviderImport:(DocumentEntry)->Unit,onCopy:(DocumentEntry)->Unit,onRename:(DocumentEntry)->Unit,onDelete:(DocumentEntry)->Unit,onNewFolder:()->Unit) {
    val haptics=rememberAppHaptics()
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
    var deleteIds by remember {mutableStateOf<Set<String>?>(null)}
    var purgeIds by remember {mutableStateOf<Set<String>?>(null)}
    var details by remember {mutableStateOf<OutputRecord?>(null)}
    LaunchedEffect(outputs) {selected=selected.intersect(outputs.map {it.id}.toSet())}
    val visible=remember(outputs,search,sort,type) {
        outputs.filter {it.displayName.contains(search,true) && (type=="all" || StoragePaths.category(it.mimeType)==type)}.let {list->
            when(sort) {"name"->list.sortedBy {it.displayName.lowercase()};"size"->list.sortedByDescending {it.sizeBytes};"type"->list.sortedWith(compareBy({StoragePaths.category(it.mimeType)},{it.displayName}));else->list.sortedByDescending {it.createdAtEpochMs}}
        }
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item("heading") { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nav_files),style=MaterialTheme.typography.headlineLarge)
            Text(if(settings.downloadDestinationFolder.startsWith("content://")) stringResource(R.string.redesign_custom_saved_path)
                else stringResource(R.string.redesign_storage_path),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.redesign_storage_folder),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={haptics.actionClick();onSavedFolder()},modifier=Modifier.weight(1f).pressFeedback()) { Icon(Icons.Rounded.FolderOpen,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.redesign_open_folder)) }
                OutlinedButton(onClick={haptics.actionClick();onImport()},modifier=Modifier.weight(1f).pressFeedback()) { Icon(Icons.Rounded.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.import_device_files)) }
            }
        } }
        item("search") {QuietSearchBar(search,stringResource(R.string.ui_search_files),{search=it},{search=""})}
        item("sections") {LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(listOf("outputs" to R.string.ui_outputs,"sources" to R.string.ui_imported,"folder" to R.string.ui_granted_folder,"trash" to R.string.files_trash_title),key={it.first}) {(key,label)->FilterChip(section==key,{haptics.selectionToggle();section=key;selecting=false;selected=emptySet()},label={Text(stringResource(label))})}
        }}
        if(section=="outputs" && outputs.any { it.publicUri==null }) item("public-store-warning") {
            Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.65f),shape=MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.redesign_save_fallback),modifier=Modifier.padding(14.dp),
                    color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodySmall)
            }
        }
        when(section) {
            "outputs" -> {
                item("filter") { Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        items(listOf("all","Video","Audio","PDF","Images","Documents")) {filter->
                            FilterChip(type==filter,{haptics.selectionToggle();type=filter},label={Text(when(filter){"all"->stringResource(R.string.ui_all);"Video"->stringResource(R.string.redesign_filter_video);"Audio"->stringResource(R.string.redesign_filter_audio);"PDF"->"PDF";"Images"->stringResource(R.string.redesign_filter_images);else->stringResource(R.string.redesign_filter_documents)})})
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        TextButton(onClick={haptics.actionClick();sortPicker=true}) { Icon(Icons.Rounded.Sort,null,Modifier.size(18.dp)); Text(stringResource(R.string.redesign_sort)) }
                        IconButton(onClick={haptics.selectionToggle();vm.setFileViewMode(when(settings.fileViewMode) {FileViewMode.LIST->FileViewMode.GRID;FileViewMode.GRID->FileViewMode.GALLERY;FileViewMode.GALLERY->FileViewMode.LIST})}) {
                            Icon(when(settings.fileViewMode){FileViewMode.LIST->Icons.Rounded.ViewList;FileViewMode.GRID->Icons.Rounded.GridView;FileViewMode.GALLERY->Icons.Rounded.PhotoLibrary},stringResource(R.string.redesign_file_view))
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick={haptics.selectionToggle();selecting=!selecting;selected=emptySet()},enabled=outputs.isNotEmpty()) {Text(stringResource(if(selecting) R.string.action_cancel else R.string.ui_select_files))}
                    }
                    if(selecting) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled=selected.isNotEmpty(),onClick={haptics.secondaryAction();deleteIds=selected}) {Text(stringResource(R.string.ui_trash_selected,selected.size))}
                        TextButton(onClick={haptics.selectionToggle();selected=visible.map {it.id}.toSet()}) {Text(stringResource(R.string.redesign_select_all))}
                    } else if(outputs.isNotEmpty()) TextButton(onClick={haptics.secondaryAction();deleteIds=outputs.map {it.id}.toSet()}) {Text(stringResource(R.string.redesign_delete_all),color=MaterialTheme.colorScheme.error)}
                } }
                if(visible.isEmpty()) item("empty") {QuietStateCard(stringResource(R.string.ui_recent_empty),stringResource(R.string.redesign_storage_folder))}
                if(settings.fileViewMode==FileViewMode.LIST) {
                    items(visible,key={it.id},contentType={"output"}) {output -> OutputRow(output,{if(selecting) selected=selected.symmetric(output.id) else onOpen(output)},{onShare(output)},{onExport(output)},{onReuse(output)},{deleteIds=setOf(output.id)},if(selecting) output.id in selected else null,{selected=selected.symmetric(output.id)})}
                } else {
                    val columns=2 // A readable, consistent two-column gallery on phones
                    items(visible.chunked(columns),key={it.joinToString("-") {f->f.id}},contentType={"tileRow"}) {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            row.forEach {output->
                                Surface(onClick={haptics.actionClick();if(selecting) selected=selected.symmetric(output.id) else onOpen(output)},modifier=Modifier.pressFeedback().weight(1f),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.fillMaxWidth().height(if(settings.fileViewMode==FileViewMode.GALLERY) 125.dp else 72.dp),contentAlignment=Alignment.Center) {
                                            if(output.mimeType.startsWith("image/")) AsyncImage(model=output.uri,contentDescription=output.displayName,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium))
                                            else Icon(when {output.mimeType=="application/pdf"->Icons.Rounded.PictureAsPdf;output.mimeType.startsWith("video/")->Icons.Rounded.Movie;output.mimeType.startsWith("audio/")->Icons.Rounded.AudioFile;else->Icons.Rounded.InsertDriveFile},null,Modifier.size(35.dp),tint=MaterialTheme.colorScheme.primary)
                                        }
                                        Text(output.displayName,style=MaterialTheme.typography.labelMedium,maxLines=2)
                                        Text(formatBytes(output.sizeBytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        if(selecting) Checkbox(checked=output.id in selected,onCheckedChange={selected=selected.symmetric(output.id)})
                                        else Row {
                                            IconButton(onClick={haptics.actionClick();onShare(output)},modifier=Modifier.pressFeedback().size(48.dp)) {Icon(Icons.Rounded.Share,null,Modifier.size(19.dp))}
                                            IconButton(onClick={haptics.actionClick();details=output},modifier=Modifier.pressFeedback().size(48.dp)) {Icon(Icons.Rounded.Info,null,Modifier.size(19.dp))}
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
                item {Text(stringResource(R.string.files_snapshots_sub),style=MaterialTheme.typography.bodySmall)}
                if(imported.isEmpty()) item {Text(stringResource(R.string.files_empty_snapshots))}
                items(imported.filter {it.displayName.contains(search,true)}.sortedBy {if(sort=="name") it.displayName.lowercase() else ""},key={it.id}) {source->FileMenuRow(source.displayName,formatBytes(source.sizeBytes),{onSource(source.id)}) {close->DropdownMenuItem(text={Text(stringResource(R.string.files_add_to_workspace))},onClick={close();onSource(source.id)})}}
            }
            "trash" -> {
                item {Text(stringResource(R.string.ui_trash_notice),style=MaterialTheme.typography.bodySmall)}
                if(trashed.isEmpty()) item {Text(stringResource(R.string.files_trash_empty))}
                else item {TextButton(onClick={purgeIds=trashed.map {it.id}.toSet()}) {Text(stringResource(R.string.files_purge_trash))}}
                items(trashed.filter {it.displayName.contains(search,true)},key={it.id}) {entry->FileMenuRow(entry.displayName,formatBytes(entry.sizeBytes),{vm.restoreOutput(entry.id)},Icons.Rounded.DeleteOutline) {close->
                    DropdownMenuItem(text={Text(stringResource(R.string.action_restore))},onClick={close();vm.restoreOutput(entry.id)})
                    DropdownMenuItem(text={Text(stringResource(R.string.action_delete))},onClick={close();purgeIds=setOf(entry.id)})
                }}
            }
            "folder" -> {
                item {OutlinedButton(onClick=onFolder) {Text(stringResource(R.string.tool_browse_storage))}}
                val listing=tree
                if(listing==null) item {Text(stringResource(R.string.ui_folder_empty))}
                else {
                    item {Text(listing.name,style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.ui_provider_notice),style=MaterialTheme.typography.bodySmall)}
                    if(listing.writable) item {TextButton(onClick=onNewFolder) {Text(stringResource(R.string.files_new_folder))}}
                    items(listing.entries.filter {it.name.contains(search,true)},key={it.uri}) {entry->ProviderFileRow(entry,listing.writable,{onBrowse(entry)},{onProviderOpen(entry)},{onProviderImport(entry)},{onCopy(entry)},{onRename(entry)},{onDelete(entry)})}
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
        Text(output.mimeType);Text(formatBytes(output.sizeBytes));Text(stringResource(R.string.redesign_storage_folder))
    }},confirmButton={TextButton(onClick={details=null}) {Text(stringResource(R.string.account_done))}})}
    purgeIds?.let {ids->AlertDialog(onDismissRequest={purgeIds=null},title={Text(stringResource(R.string.ui_purge_title))},text={Text(stringResource(R.string.ui_purge_body,ids.size))},confirmButton={TextButton(onClick={purgeIds=null;vm.purgeTrashIds(ids)}) {Text(stringResource(R.string.ui_delete_forever))}},dismissButton={TextButton(onClick={purgeIds=null}) {Text(stringResource(R.string.action_cancel))}})}
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
                textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr),
                isError=preview.errorCode!=null)
            preview.name?.let {name->
                Text(stringResource(R.string.files_rename_proposed),style=MaterialTheme.typography.labelMedium)
                TechnicalText(name)
            }
            val message = when(preview.errorCode) {
                "INVALID_FILENAME" -> R.string.files_rename_invalid_name
                "DESTINATION_EXISTS" -> R.string.files_rename_exists
                "ACCESS_LOST" -> R.string.msg_access_lost
                "PROVIDER_RENAME_UNAVAILABLE" -> R.string.msg_provider_unavailable
                else -> if(!preview.canConfirm) R.string.files_rename_unchanged else null
            }
            message?.let {Text(stringResource(it),style=MaterialTheme.typography.bodySmall,
                color=if(preview.errorCode!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
        }},
        confirmButton={TextButton(enabled=preview.canConfirm,onClick={
            if(preview.canConfirm) preview.name?.let(onConfirm)
        }) {Text(stringResource(R.string.action_rename))}},
        dismissButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.action_cancel))}}
    )
}
