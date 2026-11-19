package com.jumlo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.NeonPanel
import com.jumlo.app.ui.components.SaveFolderCard
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.theme.LocalColorSystem
import com.jumlo.app.ui.theme.QuietTheme

@Composable fun StorageScreen(vm: AppViewModel, onChooseFolder: () -> Unit, onOpenFolder: () -> Unit) {
    val usage by vm.storage.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val system = LocalColorSystem.current
    LaunchedEffect(Unit) { vm.refreshStorage() }
    val categories = remember(usage) {
        listOf(R.string.storage_imports to usage.sources, R.string.storage_results to usage.outputs,
            R.string.storage_transfers to usage.partials, R.string.storage_temporary to usage.temporary,
            R.string.storage_trash to usage.trash)
    }
    val total = remember(categories) { categories.sumOf { it.second } }
    val (outputsByType, outputsTotal) = remember(outputs) {
        outputs.groupBy { StoragePaths.category(it.mimeType) }
            .mapValues { (_, files) -> files.sumOf { it.sizeBytes } } to outputs.sumOf { it.sizeBytes }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter).testTag("storage.screen"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("storage-hero") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = system.splashMid,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(system.splashStart, system.splashMid, system.splashEnd)
                                )
                            )
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                stringResource(R.string.modern_storage_usage),
                                style = QuietTheme.typography.metadataStrong,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                formatBytes(total),
                                style = QuietTheme.typography.display,
                                color = Color.White
                            )
                            Text(
                                stringResource(R.string.storage_scope),
                                style = QuietTheme.typography.metadata,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                        }
                    }
                }
            }
            item("save-folder") {
                SaveFolderCard(settings.downloadDestinationFolder, onChooseFolder, onOpenFolder)
            }
            item("categories") {
                NeonPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        categories.forEach { (title, bytes) ->
                            StorageCategory(stringResource(title), bytes, total)
                        }
                    }
                }
            }
            if (outputs.isNotEmpty()) item("by-type") {
                NeonPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(R.string.redesign_storage_by_type),
                            style = QuietTheme.typography.sectionTitle,
                            modifier = Modifier.semantics { heading() }
                        )
                        outputsByType.forEach { (category, bytes) ->
                            StorageCategory(stringResource(when (category) {
                                "Video" -> R.string.redesign_filter_video
                                "Audio" -> R.string.redesign_filter_audio
                                "Images" -> R.string.redesign_filter_images
                                "PDF" -> R.string.redesign_pdf_type
                                else -> R.string.redesign_filter_documents
                            }), bytes, outputsTotal)
                        }
                    }
                }
            }
            item("refresh") {
                FilledTonalButton(
                    onClick = vm::refreshStorage,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = QuietTheme.shapes.button
                ) {
                    Text(stringResource(R.string.files_state_refresh_folder))
                }
            }
        }
    }
}

@Composable private fun StorageCategory(label: String, bytes: Long, total: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(formatBytes(bytes), style = QuietTheme.typography.metadataStrong)
        }
        LinearProgressIndicator(
            progress = { if (total > 0L) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}
