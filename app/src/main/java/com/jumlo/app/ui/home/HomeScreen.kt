package com.jumlo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.jumlo.app.core.ToolCatalogue
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.DataLoadStatus
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.files.fileDateLabel
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.DocumentScanner
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolIconColors
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.tools.shortToolLabel

private data class HomeQuickActionSpec(
    val labelRes: Int,
    val tag: String,
    val accentId: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onTool: (UtilityTool) -> Unit,
    onTools: () -> Unit,
    onJob: (String) -> Unit,
    onOutput: (OutputRecord) -> Unit,
    onDraft: () -> Unit,
    onFiles: () -> Unit,
    onScan: () -> Unit,
    onDownloads: () -> Unit,
    onScanIdCard: () -> Unit = onScan,
    listState: LazyListState = rememberLazyListState(),
    resetToRootEpoch: Int = 0
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val outputLoad by vm.outputLoad.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val sources by vm.imported.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val pillars = QuietTheme.pillars
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReducedMotion.current
    var showConvert by rememberSaveable { mutableStateOf(false) }
    var homeSearch by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(resetToRootEpoch) {
        if (resetToRootEpoch > 0) {
            homeSearch = ""
            if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    val searchResults = remember(homeSearch) {
        val q = homeSearch.trim()
        if (q.isEmpty()) emptyList()
        else CapabilityRegistry.tools.filter { t ->
            CapabilityRegistry.visible(t.id) && CapabilityRegistry.enabled(t.id) && ToolCatalogue.isSearchable(t.id) &&
                listOf(
                    t.titleEn, t.titleAr, t.subtitleEn, t.subtitleAr,
                    shortToolLabel(t, AppLanguage.ENGLISH), shortToolLabel(t, AppLanguage.ARABIC),
                    ToolCatalogue.searchAliases(t.id)
                ).any { it.contains(q, true) }
        }.take(8)
    }
    val active = remember(jobs) { jobs.filter { it.isActive() }.take(2) }
    val visibleRecent = remember(outputs, settings.outputFolderIds, settings.lockedFolderIds) {
        outputs.filter { out ->
            val fid = settings.outputFolderIds[out.id]
            fid == null || fid !in settings.lockedFolderIds
        }.take(6)
    }
    val sourceNamesById = remember(sources) { sources.associate { it.id to it.displayName } }
    fun open(id: String) {
        val targetId = ToolCatalogue.canonicalId(id)
        CapabilityRegistry.tools.firstOrNull { it.id == targetId }?.let(onTool)
    }
    // Exactly 8 balanced quick actions in a 4x2 grid: 7 core tools + Explore Tools.
    val actions = remember(onScan, onScanIdCard, onTool, onTools) {
        listOf(
            HomeQuickActionSpec(R.string.modern_scan, "scan", "scan_doc", onScan),
            HomeQuickActionSpec(R.string.tool_scan_id_card_title, "id_card", "scan_id_card", onScanIdCard),
            HomeQuickActionSpec(R.string.modern_convert, "convert", "convert", { showConvert = true }),
            HomeQuickActionSpec(R.string.modern_compress, "compress", "pdf_compress", { open("pdf_compress") }),
            HomeQuickActionSpec(R.string.modern_pdf_word, "pdf_word", "pdf_to_docx", { open("pdf_to_docx") }),
            HomeQuickActionSpec(R.string.home_quick_ocr, "ocr", "pdf_ocr", { open("pdf_ocr") }),
            HomeQuickActionSpec(R.string.modern_download, "download", "dl_media", { open("dl_media") }),
            HomeQuickActionSpec(R.string.home_explore_tools, "all_tools", "explore_tools", onTools)
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home.screen")) {
        val actionColumns = if (maxWidth >= 600.dp) 8 else 4
        val actionRows = remember(actions, actionColumns) { actions.chunked(actionColumns) }
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 980.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = QuietTheme.spacing.listBottomBuffer),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("hero", contentType = "hero") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp)
                            .testTag("home.hero.card"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_hero_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = stringResource(R.string.home_hero_subtitle),
                                style = QuietTheme.typography.metadata,
                                color = colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = QuietTheme.shapes.iconTile,
                            color = colors.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.16f)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.ic_prism_brand_mark),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                    QuietSearchBar(
                        query = homeSearch,
                        placeholder = stringResource(R.string.neon_tools_search),
                        onQueryChange = { homeSearch = it },
                        onClear = { homeSearch = "" },
                        modifier = Modifier.fillMaxWidth().testTag("home.search")
                    )
                    if (homeSearch.isNotBlank()) {
                        if (searchResults.isEmpty()) {
                            Text(stringResource(R.string.tools_no_matching), style = QuietTheme.typography.metadata, color = colors.onSurfaceVariant)
                        } else {
                            Surface(shape = QuietTheme.shapes.card, color = pillars.cardSurface, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    searchResults.forEach { matched ->
                                        Row(
                                            Modifier.fillMaxWidth().clip(QuietTheme.shapes.inputField).pressFeedback().clickable { homeSearch = ""; onTool(matched) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(shortToolLabel(matched, settings.language), style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colors.onSurface, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        actionRows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { spec ->
                                    HomeQuickAction(
                                        label = stringResource(spec.labelRes),
                                        accentToolId = spec.accentId,
                                        onClick = spec.onClick,
                                        modifier = Modifier.weight(1f).testTag("home.action.${spec.tag}")
                                    )
                                }
                            }
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
                item("active-heading", contentType = "section-heading") {
                    NeonSectionHeader(
                        stringResource(R.string.ui_active_tasks),
                        action = stringResource(R.string.neon_show_all),
                        onAction = onDownloads
                    )
                }
                items(active, key = { "job-${it.request.id}" }, contentType = { "active-job" }) { job ->
                    TransferRow(
                        job, settings.language, { onJob(job.request.id) },
                        { vm.pause(job.request.id) }, { vm.cancel(job.request.id) }, { vm.retry(job) },
                        sourceName = job.request.sourceIds.singleOrNull()?.let { sourceNamesById[it] }
                    )
                }
            }
            item("recent-heading", contentType = "section-heading") {
                NeonSectionHeader(
                    stringResource(R.string.neon_recent),
                    action = stringResource(R.string.neon_show_all),
                    onAction = onFiles
                )
            }
            item("recent-files", contentType = "recent-files") {
                when {
                    outputLoad != DataLoadStatus.READY -> DocumentLoadState(outputLoad, vm::reloadSavedData)
                    visibleRecent.isEmpty() -> QuietStateCard(
                        stringResource(R.string.modern_recent_empty),
                        stringResource(R.string.modern_recent_empty_body),
                        stringResource(R.string.modern_scan),
                        onScan,
                        icon = Icons.Rounded.DocumentScanner
                    )
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(visibleRecent, key = { it.id }, contentType = { "recent-output" }) { output ->
                            Surface(
                                onClick = { onOutput(output) },
                                modifier = Modifier.width(156.dp).height(196.dp).pressFeedback(),
                                shape = QuietTheme.shapes.card,
                                color = pillars.cardSurface,
                                shadowElevation = 0.dp
                            ) {
                                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    NeonOutputPreview(output, Modifier.fillMaxWidth().height(104.dp))
                                    Text(
                                        output.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content)
                                    )
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
        }
    }
    if (showConvert) Dialog(onDismissRequest = { showConvert = false }) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = colors.surface,
            border = BorderStroke(1.dp, pillars.cardBorder),
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
                        val canonical = ToolCatalogue.canonicalId(id)
                        if (!CapabilityRegistry.visible(canonical) || !CapabilityRegistry.enabled(canonical) || !ToolCatalogue.isListed(canonical)) null
                        else Triple(canonical, label, CapabilityRegistry.tools.firstOrNull { it.id == canonical })
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

@Composable
private fun HomeQuickAction(
    label: String,
    accentToolId: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val accent = remember(accentToolId) { ToolIconColors.iconColorFor(accentToolId) }
    val iconForeground = remember(accent) { ToolIconColors.glyphOn(accent) }
    Column(
        modifier = modifier
            .clip(QuietTheme.shapes.inputField)
            .pressFeedback()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = QuietTheme.shapes.iconTile,
            color = accent,
            modifier = Modifier.size(54.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ToolIcon(accentToolId, iconForeground, Modifier.size(26.dp))
            }
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
