package com.jumlo.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.NeonPanel
import com.jumlo.app.ui.components.SaveFolderCard
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

private val TYPE_CATEGORIES = listOf("PDF", "Images", "Video", "Audio", "Documents")

@Composable
fun StorageScreen(
    vm: AppViewModel,
    onChooseFolder: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenCategory: (String) -> Unit = {}
) {
    val usage by vm.storage.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    LaunchedEffect(Unit) { vm.refreshStorage() }
    val categories = remember(usage) {
        listOf(
            Triple("all", R.string.storage_results, usage.outputs),
            Triple("downloads", R.string.storage_transfers, usage.partials),
            Triple("cache", R.string.storage_temporary, usage.temporary),
            Triple("trash", R.string.storage_trash, usage.trash)
        )
    }
    val total = remember(categories) { categories.sumOf { it.third } }
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
                val colors = MaterialTheme.colorScheme
                val pillars = QuietTheme.pillars
                Surface(
                    shape = QuietTheme.shapes.heroCard,
                    color = pillars.cardSurface,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.modern_storage_usage),
                            style = QuietTheme.typography.metadataStrong,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            formatBytes(total),
                            style = QuietTheme.typography.display.copy(textDirection = TextDirection.Ltr),
                            color = colors.onSurface
                        )
                        Text(
                            stringResource(R.string.storage_scope),
                            style = QuietTheme.typography.metadata,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            item("save-folder") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SaveFolderCard(settings.downloadDestinationFolder, onChooseFolder, onOpenFolder)
                    if (settings.downloadDestinationFolder.startsWith("content://")) {
                        TextButton(
                            onClick = vm::resetDownloadDestinationFolder,
                            modifier = Modifier.testTag("storage.resetDownloadLocation")
                        ) {
                            Text(stringResource(R.string.modern_reset_save_folder))
                        }
                    }
                }
            }
            item("categories") {
                NeonPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        categories.forEach { (key, title, bytes) ->
                            StorageCategory(
                                label = stringResource(title),
                                bytes = bytes,
                                total = total,
                                modifier = Modifier.testTag("storage.category.$key"),
                                onClick = { haptics.actionClick(); onOpenCategory(key) }
                            )
                        }
                    }
                }
            }
            if (outputs.isNotEmpty()) item("by-type") {
                NeonPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.redesign_storage_by_type),
                            style = QuietTheme.typography.sectionTitle,
                            modifier = Modifier.semantics { heading() }
                        )
                        TYPE_CATEGORIES.forEach { category ->
                            val bytes = outputsByType[category] ?: 0L
                            if (bytes > 0L) {
                                StorageCategory(
                                    label = stringResource(when (category) {
                                        "Video" -> R.string.redesign_filter_video
                                        "Audio" -> R.string.redesign_filter_audio
                                        "Images" -> R.string.redesign_filter_images
                                        "PDF" -> R.string.redesign_pdf_type
                                        else -> R.string.redesign_filter_documents
                                    }),
                                    bytes = bytes,
                                    total = outputsTotal,
                                    modifier = Modifier.testTag("storage.type.$category"),
                                    onClick = { haptics.actionClick(); onOpenCategory(category) }
                                )
                            }
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

@Composable
private fun StorageCategory(
    label: String,
    bytes: Long,
    total: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(QuietTheme.shapes.inputField)
            .pressFeedback()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(formatBytes(bytes), style = QuietTheme.typography.metadataStrong.copy(textDirection = TextDirection.Ltr))

        }
        LinearProgressIndicator(
            progress = { if (total > 0L) (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}
