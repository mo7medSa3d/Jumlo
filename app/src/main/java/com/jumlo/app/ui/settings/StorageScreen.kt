package com.jumlo.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.SaveFolderCard
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.theme.QuietTheme

@Composable fun StorageScreen(vm: AppViewModel, onChooseFolder: () -> Unit, onOpenFolder: () -> Unit) {
    val usage by vm.storage.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshStorage() }
    val categories = listOf(R.string.storage_imports to usage.sources, R.string.storage_results to usage.outputs,
        R.string.storage_transfers to usage.partials, R.string.storage_temporary to usage.temporary,
        R.string.storage_trash to usage.trash)
    val total = categories.sumOf { it.second }
    LazyColumn(Modifier.fillMaxSize().testTag("storage.screen"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatBytes(total), style = QuietTheme.typography.display)
            Text(stringResource(R.string.modern_storage_usage), style = QuietTheme.typography.bodyMedium)
            Text(stringResource(R.string.storage_scope), style = QuietTheme.typography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SaveFolderCard(settings.downloadDestinationFolder, onChooseFolder, onOpenFolder) }
        item { Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            categories.forEach { (title, bytes) ->
                StorageCategory(stringResource(title), bytes, total)
            }
        } }
        if (outputs.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.redesign_storage_by_type), style = QuietTheme.typography.sectionTitle)
                outputs.groupBy { StoragePaths.category(it.mimeType) }.forEach { (category, files) ->
                    StorageCategory(stringResource(when (category) {
                        "Video" -> R.string.redesign_filter_video
                        "Audio" -> R.string.redesign_filter_audio
                        "Images" -> R.string.redesign_filter_images
                        "PDF" -> R.string.redesign_pdf_type
                        else -> R.string.redesign_filter_documents
                    }), files.sumOf { it.sizeBytes }, outputs.sumOf { it.sizeBytes })
                }
            }
        }
        item { TextButton(onClick = vm::refreshStorage) { Text(stringResource(R.string.files_state_refresh_folder)) } }
    }
}

@Composable private fun StorageCategory(label: String, bytes: Long, total: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f), style = QuietTheme.typography.bodyMedium)
            Text(formatBytes(bytes), style = QuietTheme.typography.metadataStrong)
        }
        LinearProgressIndicator(progress = { if (total > 0L) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}
