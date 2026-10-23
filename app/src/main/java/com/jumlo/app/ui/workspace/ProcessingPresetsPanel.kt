package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.ProcessingPreset
import com.jumlo.app.model.WorkspaceDraft

/** Real per-tool preferences; no persisted file paths, passwords or page selections. */
@Composable fun ProcessingPresetsPanel(
    form: WorkspaceDraft,
    presets: List<ProcessingPreset>,
    onSave: (String) -> Unit,
    onApply: (ProcessingPreset) -> Unit,
    onDelete: (ProcessingPreset) -> Unit
) {
    var showSave by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<ProcessingPreset?>(null) }
    var deleting by remember { mutableStateOf<ProcessingPreset?>(null) }
    val owned=presets.filter { it.toolId == form.toolId }
    Column(Modifier.fillMaxWidth().testTag("workspace.presets"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bookmarks,null,tint=MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.preset_panel_title),style=MaterialTheme.typography.titleMedium,
                modifier=Modifier.weight(1f))
            TextButton(onClick={showSave=true},modifier=Modifier.heightIn(min=48.dp)) {
                Text(stringResource(R.string.preset_panel_save))
            }
        }
        if(owned.isEmpty()) {
            Text(stringResource(R.string.preset_panel_empty),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(owned,key={it.name}) { preset ->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                    AssistChip(onClick={onApply(preset)},label={Text(preset.name)},border=null,
                        modifier=Modifier.heightIn(min=48.dp))
                    TextButton(onClick={deleting=preset},modifier=Modifier.heightIn(min=48.dp)) {
                        Text(stringResource(R.string.preset_panel_delete))
                    }
                }
            }
        }
    }
    if(showSave) AlertDialog(
        onDismissRequest={showSave=false},
        title={Text(stringResource(R.string.preset_panel_save))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextField(name,{name=it.take(48)},singleLine=true,colors=borderlessFieldColors(),
                label={Text(stringResource(R.string.preset_panel_name))},
                modifier=Modifier.fillMaxWidth().testTag("workspace.presetName"))
            Text(stringResource(R.string.preset_panel_privacy),style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton(onClick={
            val trimmed=name.trim()
            val old=owned.firstOrNull {it.name==trimmed}
            showSave=false
            if(old!=null) confirmation=old else onSave(trimmed)
        },enabled=name.trim().isNotEmpty()) {Text(stringResource(R.string.preset_panel_save))}},
        dismissButton={TextButton(onClick={showSave=false}) {Text(stringResource(R.string.action_cancel))}}
    )
    confirmation?.let { existing ->
        AlertDialog(
            onDismissRequest={confirmation=null},
            title={Text(stringResource(R.string.preset_panel_replace_title))},
            text={Text(stringResource(R.string.preset_panel_replace_body))},
            confirmButton={TextButton(onClick={onSave(existing.name);confirmation=null}) {
                Text(stringResource(R.string.preset_panel_replace_title))
            }},
            dismissButton={TextButton(onClick={confirmation=null}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest={deleting=null},
            title={Text(stringResource(R.string.preset_panel_delete))},
            text={Text(stringResource(R.string.preset_panel_delete_body))},
            confirmButton={TextButton(onClick={onDelete(item);deleting=null}) {
                Text(stringResource(R.string.preset_panel_delete))
            }},
            dismissButton={TextButton(onClick={deleting=null}) {Text(stringResource(R.string.action_cancel))}}
        )
    }
}
