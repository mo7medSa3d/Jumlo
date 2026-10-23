package com.jumlo.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.DataLoadStatus
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.files.fileDateLabel
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.tools.ToolTile

@Composable
fun HomeScreen(vm: AppViewModel, onTool: (UtilityTool) -> Unit, onTools: () -> Unit,
    onJob: (String) -> Unit, onOutput: (OutputRecord) -> Unit, onDraft: () -> Unit,
    onFiles: () -> Unit, onScan: () -> Unit, onDownloads: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val outputLoad by vm.outputLoad.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    var showConvert by rememberSaveable { mutableStateOf(false) }
    fun open(id: String) { CapabilityRegistry.tools.firstOrNull { it.id == id }?.let(onTool) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home.screen")) {
        val columns = if (maxWidth >= 640.dp) 4 else 2
        LazyColumn(Modifier.widthIn(max = 980.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = QuietTheme.spacing.listBottomBuffer),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item("hero") {
                Surface(shape = QuietTheme.shapes.card, color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(stringResource(R.string.modern_home_title), style = QuietTheme.typography.display,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.modern_home_subtitle), style = QuietTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        val actions = listOf(
                            Triple(R.string.modern_scan, R.drawable.ic_prism_scan, onScan),
                            Triple(R.string.modern_convert, R.drawable.ic_prism_document, { showConvert = true }),
                            Triple(R.string.modern_compress, R.drawable.ic_prism_compress, { open("pdf_compress") }),
                            Triple(R.string.modern_download, R.drawable.ic_prism_download, { open("dl_media") })
                        )
                        actions.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (label, icon, action) ->
                                    val tag = when (label) {
                                        R.string.modern_scan -> "scan"
                                        R.string.modern_convert -> "convert"
                                        R.string.modern_compress -> "compress"
                                        else -> "download"
                                    }
                                    HomeQuickAction(stringResource(label), icon, action,
                                        Modifier.weight(1f).testTag("home.action.$tag"))
                                }
                            }
                        }
                    }
                }
            }
            val frequent = settings.rankedToolIds.mapNotNull { id ->
                CapabilityRegistry.tools.firstOrNull { it.id == id && CapabilityRegistry.enabled(id) }
            }.take(4)
            if (frequent.isNotEmpty()) item("frequent-tools") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonSectionHeader(stringResource(R.string.studio_frequently_used),
                        action = stringResource(R.string.modern_all_tools), onAction = onTools)
                    frequent.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { tool -> ToolTile(tool, settings.language, { onTool(tool) }, Modifier.weight(1f)) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (form.sourceIds.isNotEmpty()) item("draft") {
                Surface(onClick = onDraft, shape = QuietTheme.shapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_prism_document), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.ui_workspace_draft), style = QuietTheme.typography.bodyMedium)
                            Text(stringResource(R.string.ui_input_count, form.sourceIds.size), style = QuietTheme.typography.metadata)
                        }
                        Text(stringResource(R.string.ui_resume), color = MaterialTheme.colorScheme.primary, style = QuietTheme.typography.metadataStrong)
                    }
                }
            }
            val active = jobs.filter { it.isActive() }.take(2)
            if (active.isNotEmpty()) {
                item("active-heading") { NeonSectionHeader(stringResource(R.string.ui_active_tasks),
                    action = stringResource(R.string.neon_show_all), onAction = onDownloads) }
                items(active, key = { "job-${it.request.id}" }) { job ->
                    TransferRow(job, settings.language, { onJob(job.request.id) },
                        { vm.pause(job.request.id) }, { vm.cancel(job.request.id) }, { vm.retry(job) })
                }
            }
            item("recent-heading") { NeonSectionHeader(stringResource(R.string.neon_recent),
                action = stringResource(R.string.neon_show_all), onAction = onFiles) }
            item("recent-files") {
                when {
                    outputLoad != DataLoadStatus.READY -> DocumentLoadState(outputLoad, vm::reloadSavedData)
                    outputs.isEmpty() -> QuietStateCard(stringResource(R.string.modern_recent_empty),
                        stringResource(R.string.modern_recent_empty_body), stringResource(R.string.modern_scan), onScan)
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(outputs.take(8), key = { it.id }) { output ->
                            Surface(onClick = { onOutput(output) }, modifier = Modifier.width(176.dp).pressFeedback(),
                                shape = QuietTheme.shapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NeonOutputPreview(output, Modifier.fillMaxWidth().height(140.dp))
                                    Text(output.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = QuietTheme.typography.bodyMedium)
                                    Text(formatOutputSizeSummary(output), style = QuietTheme.typography.metadata,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text(fileDateLabel(output.createdAtEpochMs), style = QuietTheme.typography.metadata,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item("all-tools") { FilledTonalButton(onClick = onTools, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(painterResource(R.drawable.ic_prism_grid), null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.modern_all_tools))
            } }
        }
    }
    if (showConvert) AlertDialog(onDismissRequest = { showConvert = false },
        title = { Text(stringResource(R.string.modern_convert)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("pdf_02" to R.string.modern_images_pdf, "pdf_to_docx" to R.string.modern_pdf_word,
                "pdf_to_pptx" to R.string.modern_pdf_powerpoint, "pdf_07" to R.string.modern_pdf_images,
                "img_01" to R.string.modern_convert_images).forEach { (id, label) ->
                if (CapabilityRegistry.enabled(id)) TextButton(onClick = { showConvert = false; open(id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(label)) }
            }
        } }, confirmButton = { TextButton(onClick = { showConvert = false }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable private fun HomeQuickAction(label: String, icon: Int, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 92.dp).pressFeedback(),
        shape = QuietTheme.shapes.inputField, color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = QuietTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}
