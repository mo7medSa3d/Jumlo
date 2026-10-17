package com.jumlo.app.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.files.DocumentListing
import com.jumlo.app.data.files.previewDocumentRename
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*

@Composable fun FilesScreen(vm:AppViewModel,onImport:()->Unit,onFolder:()->Unit,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onSource:(String)->Unit,onBrowse:(DocumentEntry)->Unit,onProviderOpen:(DocumentEntry)->Unit,onProviderImport:(DocumentEntry)->Unit,onCopy:(DocumentEntry)->Unit,onRename:(DocumentEntry)->Unit,onDelete:(DocumentEntry)->Unit,onNewFolder:()->Unit) {
    val imported by vm.imported.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val tree by vm.tree.collectAsStateWithLifecycle()
    var section by rememberSaveable {mutableStateOf("outputs")}
    var search by rememberSaveable {mutableStateOf("")}
    var alphabetical by rememberSaveable {mutableStateOf(false)}
    var filtering by rememberSaveable {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    var selecting by rememberSaveable {mutableStateOf(false)}
    var purgeIds by remember {mutableStateOf<Set<String>?>(null)}
    LaunchedEffect(outputs) {selected=selected.intersect(outputs.map {it.id}.toSet())}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=onImport,modifier=Modifier.weight(1f)) {Text(stringResource(R.string.import_device_files))}
            TextButton(onClick=onFolder,modifier=Modifier.weight(1f)) {Text(stringResource(R.string.tool_browse_storage))}
            IconButton(onClick={filtering=!filtering}) {Icon(Icons.Rounded.Search,stringResource(R.string.search_title))}
        }}
        if(filtering) item {
            QuietSearchBar(search,stringResource(R.string.ui_search_files),{search=it},{search=""})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!alphabetical,{alphabetical=false},label={Text(stringResource(R.string.ui_recent_order))})
                FilterChip(alphabetical,{alphabetical=true},label={Text(stringResource(R.string.ui_name_order))})
            }
        }
        item {LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(listOf("outputs" to R.string.ui_outputs,"sources" to R.string.ui_imported,"folder" to R.string.ui_granted_folder,"trash" to R.string.files_trash_title),key={it.first}) {(key,label)->FilterChip(section==key,{section=key;selecting=false;selected=emptySet()},label={Text(stringResource(label))})}
        }}
        when(section) {
            "outputs" -> {
                item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(selecting) {
                        TextButton(onClick={selecting=false;selected=emptySet()}) {Text(stringResource(R.string.action_cancel))}
                        TextButton(onClick={outputs.filter {it.id in selected}.forEach(vm::trashOutput);selected=emptySet();selecting=false},enabled=selected.isNotEmpty()) {Text(stringResource(R.string.ui_trash_selected,selected.size))}
                    } else TextButton(onClick={selecting=true},enabled=outputs.isNotEmpty()) {Text(stringResource(R.string.ui_select_files))}
                }}
                val visible=outputs.filter {it.displayName.contains(search,true)}.let {if(alphabetical) it.sortedBy {file->file.displayName.lowercase()} else it}
                if(visible.isEmpty()) item {Text(stringResource(R.string.ui_recent_empty))}
                items(visible,key={it.id},contentType={"output"}) {output->OutputRow(output,{onOpen(output)},{onShare(output)},{onExport(output)},{onReuse(output)},{vm.trashOutput(output)},if(selecting) output.id in selected else null,{selected=if(output.id in selected) selected-output.id else selected+output.id})}
            }
            "sources" -> {
                item {Text(stringResource(R.string.files_snapshots_sub),style=MaterialTheme.typography.bodySmall)}
                if(imported.isEmpty()) item {Text(stringResource(R.string.files_empty_snapshots))}
                items(imported.filter {it.displayName.contains(search,true)}.let {if(alphabetical) it.sortedBy {file->file.displayName.lowercase()} else it},key={it.id},contentType={"source"}) {source->FileMenuRow(source.displayName,formatBytes(source.sizeBytes),{onSource(source.id)}) {close->DropdownMenuItem(text={Text(stringResource(R.string.files_add_to_workspace))},onClick={close();onSource(source.id)})}}
            }
            "trash" -> {
                item {Text(stringResource(R.string.ui_trash_notice),style=MaterialTheme.typography.bodySmall)}
                if(trashed.isEmpty()) item {Text(stringResource(R.string.files_trash_empty))}
                else item {TextButton(onClick={purgeIds=trashed.map {it.id}.toSet()}) {Text(stringResource(R.string.files_purge_trash))}}
                items(trashed.filter {it.displayName.contains(search,true)}.let {if(alphabetical) it.sortedBy {file->file.displayName.lowercase()} else it},key={it.id},contentType={"trash"}) {entry->FileMenuRow(entry.displayName,formatBytes(entry.sizeBytes),{vm.restoreOutput(entry.id)},Icons.Rounded.DeleteOutline) {close->
                    DropdownMenuItem(text={Text(stringResource(R.string.action_restore))},onClick={close();vm.restoreOutput(entry.id)})
                    DropdownMenuItem(text={Text(stringResource(R.string.action_delete))},onClick={close();purgeIds=setOf(entry.id)})
                }}
            }
            "folder" -> {
                val listing=tree
                if(listing==null) item {Text(stringResource(R.string.ui_folder_empty))}
                else {
                    item {Text(listing.name,style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.ui_provider_notice),style=MaterialTheme.typography.bodySmall)}
                    if(listing.writable) item {TextButton(onClick=onNewFolder) {Text(stringResource(R.string.files_new_folder))}}
                    items(listing.entries.filter {it.name.contains(search,true)},key={it.uri},contentType={"provider"}) {entry->
                        ProviderFileRow(entry,listing.writable,{onBrowse(entry)},{onProviderOpen(entry)},
                            {onProviderImport(entry)},{onCopy(entry)},{onRename(entry)},{onDelete(entry)})
                    }
                }
            }
        }
    }
    purgeIds?.let {ids->AlertDialog(onDismissRequest={purgeIds=null},title={Text(stringResource(R.string.ui_purge_title))},text={Text(stringResource(R.string.ui_purge_body,ids.size))},confirmButton={TextButton(onClick={purgeIds=null;vm.purgeTrashIds(ids)}) {Text(stringResource(R.string.ui_delete_forever))}},dismissButton={TextButton(onClick={purgeIds=null}) {Text(stringResource(R.string.action_cancel))}})}
}

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
