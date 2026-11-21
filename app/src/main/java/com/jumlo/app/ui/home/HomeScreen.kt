package com.jumlo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.DataLoadStatus
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.files.fileDateLabel
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowForward
import com.jumlo.app.ui.icons.rounded.DocumentScanner
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolIconColors
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.tools.ToolTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onTool: (UtilityTool) -> Unit, onTools: () -> Unit,
    onJob: (String) -> Unit, onOutput: (OutputRecord) -> Unit, onDraft: () -> Unit,
    onFiles: () -> Unit, onScan: () -> Unit, onDownloads: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val outputLoad by vm.outputLoad.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val sources by vm.imported.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val pillars = QuietTheme.pillars
    val colors = MaterialTheme.colorScheme
    var showConvert by rememberSaveable { mutableStateOf(false) }
    val frequent = remember(settings.rankedToolIds) {
        settings.rankedToolIds.mapNotNull { id ->
            CapabilityRegistry.tools.firstOrNull { it.id == id && CapabilityRegistry.visible(id) && CapabilityRegistry.enabled(id) }
        }.take(3)
    }
    val active = remember(jobs) { jobs.filter { it.isActive() }.take(2) }
    val recentOutputs = remember(outputs) { outputs.take(6) }
    val pinnedSet = remember(settings.quickActionIds) { settings.quickActionIds.toSet() }
    val sourceNamesById = remember(sources) { sources.associate { it.id to it.displayName } }
    fun open(id: String) { CapabilityRegistry.tools.firstOrNull { it.id == id }?.let(onTool) }
    val actions = remember(onScan, onTool) {
        listOf(
            Triple(R.string.modern_scan, R.drawable.ic_prism_scan, onScan),
            Triple(R.string.modern_convert, R.drawable.ic_prism_document, { showConvert = true }),
            Triple(R.string.modern_compress, R.drawable.ic_prism_compress, { open("pdf_compress") }),
            Triple(R.string.modern_download, R.drawable.ic_prism_download, { open("dl_media") })
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home.screen")) {
        val actionColumns = 4
        val frequentColumns = if (maxWidth >= 640.dp) 4 else 3
        val actionRows = remember(actions, actionColumns) { actions.chunked(actionColumns) }
        val frequentRows = remember(frequent, frequentColumns) { frequent.chunked(frequentColumns) }
        LazyColumn(Modifier.widthIn(max = 980.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = QuietTheme.spacing.listBottomBuffer),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item("hero", contentType = "hero") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.modern_home_title),
                            style = QuietTheme.typography.screenTitle,
                            color = colors.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            stringResource(R.string.modern_home_subtitle),
                            style = QuietTheme.typography.metadata,
                            color = colors.onSurfaceVariant
                        )
                    }
                    actionRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, icon, action) ->
                                val (tag, accentId) = when (label) {
                                    R.string.modern_scan -> "scan" to "scan_doc"
                                    R.string.modern_convert -> "convert" to "pdf_02"
                                    R.string.modern_compress -> "compress" to "pdf_compress"
                                    else -> "download" to "dl_media"
                                }
                                HomeQuickAction(stringResource(label), icon, action,
                                    Modifier.weight(1f).testTag("home.action.$tag"),
                                    accentToolId = accentId)
                            }
                        }
                    }
                }
            }
            if (frequent.isNotEmpty()) item("frequent-tools", contentType = "frequent-tools") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeonSectionHeader(stringResource(R.string.studio_frequently_used),
                        action = stringResource(R.string.modern_all_tools), onAction = onTools)
                    frequentRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { tool -> ToolTile(tool, settings.language, { onTool(tool) }, Modifier.weight(1f),
                                onPin = { vm.setQuickActionIds(HomeActionPolicy.togglePin(settings.quickActionIds, tool.id)) },
                                isPinned = tool.id in pinnedSet) }
                            repeat(frequentColumns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (form.sourceIds.isNotEmpty()) item("draft", contentType = "draft") {
                Surface(
                    onClick = onDraft,
                    shape = QuietTheme.shapes.card,
                    color = pillars.cardSurface,
                    shadowElevation = 0.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(QuietTheme.shapes.thumbnail)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(painterResource(R.drawable.ic_prism_document), null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.ui_workspace_draft), style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(stringResource(R.string.ui_input_count, form.sourceIds.size), style = QuietTheme.typography.metadata, color = colors.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.ui_resume), color = colors.primary, style = QuietTheme.typography.metadataStrong)
                    }
                }
            }
            if (active.isNotEmpty()) {
                item("active-heading", contentType = "section-heading") { NeonSectionHeader(stringResource(R.string.ui_active_tasks),
                    action = stringResource(R.string.neon_show_all), onAction = onDownloads) }
                items(active, key = { "job-${it.request.id}" }, contentType = { "active-job" }) { job ->
                    TransferRow(job, settings.language, { onJob(job.request.id) },
                        { vm.pause(job.request.id) }, { vm.cancel(job.request.id) }, { vm.retry(job) },
                        sourceName = job.request.sourceIds.singleOrNull()?.let { sourceNamesById[it] })
                }
            }
            item("recent-heading", contentType = "section-heading") { NeonSectionHeader(stringResource(R.string.neon_recent),
                action = stringResource(R.string.neon_show_all), onAction = onFiles) }
            item("recent-files", contentType = "recent-files") {
                when {
                    outputLoad != DataLoadStatus.READY -> DocumentLoadState(outputLoad, vm::reloadSavedData)
                    outputs.isEmpty() -> QuietStateCard(stringResource(R.string.modern_recent_empty),
                        stringResource(R.string.modern_recent_empty_body), stringResource(R.string.modern_scan), onScan,
                        icon = Icons.Rounded.DocumentScanner)
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentOutputs, key = { it.id }, contentType = { "recent-output" }) { output ->
                            Surface(
                                onClick = { onOutput(output) },
                                modifier = Modifier.width(156.dp).height(196.dp).pressFeedback(),
                                shape = QuietTheme.shapes.card,
                                color = pillars.cardSurface,
                                shadowElevation = 0.dp
                            ) {
                                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    NeonOutputPreview(output, Modifier.fillMaxWidth().height(104.dp))
                                    Text(output.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content))
                                    Text(
                                        formatOutputSizeSummary(output) + " · " + fileDateLabel(output.createdAtEpochMs),
                                        style = QuietTheme.typography.metadata,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Tools tab in the bottom navigation bar and section header already provide instant access.
        }
    }
    if (showConvert) Dialog(onDismissRequest = { showConvert = false }) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, pillars.cardBorder),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.modern_convert),
                style = QuietTheme.typography.sectionTitle,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 6.dp).semantics { heading() }
            )
            val convertOptions = remember {
                ConvertSheetOptions.mapNotNull { (id, label) ->
                    if (!CapabilityRegistry.visible(id) || !CapabilityRegistry.enabled(id)) null
                    else Triple(id, label, CapabilityRegistry.tools.firstOrNull { it.id == id })
                }
            }
            convertOptions.forEach { (id, label, tool) ->
                    val accent = remember(id) { ToolIconColors.iconColorFor(id) }
                    val glyph = remember(accent) { ToolIconColors.glyphOn(accent) }
                    Surface(
                        onClick = { showConvert = false; open(id) },
                        shape = QuietTheme.shapes.inputField,
                        color = pillars.cardSurface,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(QuietTheme.shapes.thumbnail).background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                ToolIcon(id, glyph, Modifier.size(20.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(label),
                                    style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                tool?.let {
                                    Text(
                                        if (settings.language.isRtl) it.subtitleAr else it.subtitleEn,
                                        style = QuietTheme.typography.metadata,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                null,
                                Modifier.size(18.dp),
                                tint = colors.onSurfaceVariant
                            )
                        }
                    }
            }
        }
        }
    }
}

private val ConvertSheetOptions = listOf(
    "pdf_02" to R.string.modern_images_pdf,
    "pdf_to_docx" to R.string.modern_pdf_word,
    "pdf_to_pptx" to R.string.modern_pdf_powerpoint,
    "office_to_pdf" to R.string.modern_office_to_pdf,
    "pdf_07" to R.string.modern_pdf_images,
    "img_04" to R.string.modern_convert_images
)

@Composable private fun HomeQuickAction(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    accentToolId: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val badgeBg = remember(accentToolId, colors.primaryContainer) {
        accentToolId?.let { ToolIconColors.iconColorFor(it) } ?: colors.primaryContainer
    }
    val badgeTint = remember(accentToolId, badgeBg, colors.onPrimaryContainer) {
        accentToolId?.let { ToolIconColors.glyphOn(badgeBg) } ?: colors.onPrimaryContainer
    }
    Column(
        modifier = modifier
            .clip(QuietTheme.shapes.inputField)
            .pressFeedback()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(QuietTheme.shapes.iconTile)
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, Modifier.size(26.dp), tint = badgeTint)
        }
        Text(
            label,
            style = QuietTheme.typography.metadataStrong,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
