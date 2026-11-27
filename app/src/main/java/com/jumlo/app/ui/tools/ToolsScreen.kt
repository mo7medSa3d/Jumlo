package com.jumlo.app.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Star
import com.jumlo.app.ui.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.core.ToolCatalogue
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.UtilityTool
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.home.HomeActionPolicy
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolIconColors
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.AppHaptics
import com.jumlo.app.ui.util.rememberAppHaptics

/** Every available tool has one tile; the catalogue is grouped into sections and
 *  the persisted usage ranking orders the tiles within each section. */
@Composable
fun ToolsScreen(
    vm: AppViewModel,
    onTool: (UtilityTool) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    resetToRootEpoch: Int = 0
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val eligible = remember { CapabilityRegistry.tools.filter {
        CapabilityRegistry.visible(it.id) && CapabilityRegistry.enabled(it.id) && ToolCatalogue.isListed(it.id)
    } }
    ToolsCatalogue(
        tools = eligible,
        language = settings.language,
        rankedToolIds = settings.rankedToolIds,
        pinnedToolIds = settings.quickActionIds,
        recentToolIds = settings.recentToolIds,
        search = search,
        onSearch = { search = it },
        onTool = onTool,
        onPin = { tool -> vm.setQuickActionIds(HomeActionPolicy.togglePin(settings.quickActionIds, tool.id)) },
        gridState = gridState,
        resetToRootEpoch = resetToRootEpoch
    )
}

/** One catalogue group: a header and the tool IDs it contains. */
private data class ToolSection(val key: String, val titleEn: String, val titleAr: String, val ids: Set<String>)

/**
 * Sections follow the product brief. Tools outside every section fall into the
 * trailing "All tools" group, so a newly registered tool is never silently hidden.
 */
private val toolSections = listOf(
    ToolSection("scan", "Scan & Convert", "المسح والتحويل",
        setOf("scan_doc", "scan_id_card", "pdf_ocr", "pdf_to_docx", "pdf_to_pptx", "office_to_pdf", "pdf_02", "pdf_07", "pdf_long_image")),
    ToolSection("edit", "PDF Edit & Security", "تحرير وحماية PDF",
        setOf("pdf_signature", "pdf_stamp", "pdf_03", "pdf_05", "pdf_06", "pdf_04", "pdf_crop", "pdf_compress", "pdf_protect", "pdf_privacy_redact", "pdf_nup", "pdf_01")),
    ToolSection("optimize", "Images", "الصور",
        setOf("img_01", "img_02", "img_04", "img_bg_remove", "img_watermark", "img_join")),
    ToolSection("files", "Files & Download", "الملفات والتنزيل",
        setOf("dl_media", "file_07", "file_08", "file_batch_rename"))
)

private fun sectionTitle(section: ToolSection, language: AppLanguage): String =
    if (language.isRtl) section.titleAr else section.titleEn

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        count?.let {
            Text(
                it.toString(),
                color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

/** Stateless UI boundary lets interaction tests exercise the real catalogue without a database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolsCatalogue(
    tools: List<UtilityTool>,
    language: AppLanguage,
    rankedToolIds: List<String>,
    pinnedToolIds: List<String>,
    recentToolIds: List<String>,
    search: String,
    onSearch: (String) -> Unit,
    onTool: (UtilityTool) -> Unit,
    onPin: (UtilityTool) -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    resetToRootEpoch: Int = 0
) {
    val byId = remember(tools) { tools.associateBy { it.id } }
    val canonicalRanked = remember(rankedToolIds) { rankedToolIds.map(ToolCatalogue::canonicalId).distinct() }
    val canonicalPinned = remember(pinnedToolIds) { pinnedToolIds.map(ToolCatalogue::canonicalId).distinct() }
    val canonicalRecent = remember(recentToolIds) { recentToolIds.map(ToolCatalogue::canonicalId).distinct() }
    val filtered = remember(tools, canonicalRanked, search) {
        val ranks = canonicalRanked.withIndex().associate { it.value to it.index }
        val query = search.trim()
        tools.withIndex().filter { (_, tool) ->
            if (query.isEmpty()) {
                ToolCatalogue.isListed(tool.id)
            } else {
                ToolCatalogue.isSearchable(tool.id) &&
                    listOf(
                        tool.id, tool.titleEn, tool.titleAr, tool.subtitleEn, tool.subtitleAr,
                        shortToolLabel(tool, AppLanguage.ENGLISH),
                        shortToolLabel(tool, AppLanguage.ARABIC),
                        ToolCatalogue.searchAliases(tool.id)
                    ).any { it.contains(query, ignoreCase = true) }
            }
        }.sortedWith(compareBy<IndexedValue<UtilityTool>> { ranks[it.value.id] ?: Int.MAX_VALUE }
            .thenBy { it.index }).map { it.value }
    }
    val pinned = remember(tools, canonicalPinned) {
        canonicalPinned.mapNotNull { byId[it] }.filter { CapabilityRegistry.enabled(it.id) && ToolCatalogue.isListed(it.id) }
    }
    val recent = remember(tools, canonicalRecent, pinned) {
        val pinnedIds = pinned.map { it.id }.toSet()
        canonicalRecent.mapNotNull { byId[it] }.filter { it.id !in pinnedIds && ToolCatalogue.isListed(it.id) }.take(4)
    }
    val featuredIds = remember(pinned, recent) { (pinned.map { it.id } + recent.map { it.id }).toSet() }
    var selectedSectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var actionSheetTool by remember { mutableStateOf<UtilityTool?>(null) }
    val pinnedSet = remember(canonicalPinned) { canonicalPinned.toSet() }
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme
    val availableSections = remember(filtered) {
        toolSections.filter { section -> filtered.any { it.id in section.ids } }
    }
    val effectiveSectionKey = remember(selectedSectionKey, availableSections) {
        selectedSectionKey?.takeIf { key -> availableSections.any { it.key == key } }
    }
    val (sectionedTools, ungroupedTools) = remember(filtered, featuredIds, effectiveSectionKey) {
        val remaining = if (effectiveSectionKey == null) filtered.filter { it.id !in featuredIds } else filtered
        val sections = toolSections.mapNotNull { section ->
            if (effectiveSectionKey != null && section.key != effectiveSectionKey) return@mapNotNull null
            val matching = remaining.filter { it.id in section.ids }
            if (matching.isEmpty()) null else section to matching
        }
        val ungrouped = if (effectiveSectionKey == null) {
            remaining.filter { tool -> toolSections.none { section -> section.ids.contains(tool.id) } }
        } else emptyList()
        sections to ungrouped
    }
    val largeText = LocalDensity.current.fontScale >= 1.6f
    val reduceMotion = LocalReducedMotion.current
    LaunchedEffect(resetToRootEpoch) {
        if (resetToRootEpoch > 0) {
            onSearch("")
            selectedSectionKey = null
            if (reduceMotion) gridState.scrollToItem(0) else gridState.animateScrollToItem(0)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("tools.screen")) {
        val columns = when {
            maxWidth >= 1200.dp -> if (largeText) 4 else 6
            maxWidth >= 900.dp -> if (largeText) 4 else 5
            maxWidth >= 600.dp -> if (largeText) 3 else 4
            largeText -> 3
            else -> 4
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().align(Alignment.TopCenter).testTag("tools.grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = QuietTheme.spacing.listBottomBuffer + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (search.isBlank() && availableSections.isNotEmpty()) {
                item("category-chips", span = { GridItemSpan(maxLineSpan) }, contentType = "category-chips") {
                    val pillars = QuietTheme.pillars
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = pillars.cardSurface,
                        labelColor = colors.onSurfaceVariant,
                        selectedContainerColor = colors.primaryContainer,
                        selectedLabelColor = colors.onPrimaryContainer
                    )
                    val activeChipBorder = remember(colors.primary) { BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item("cat-all", contentType = "category-chip") {
                            val active = effectiveSectionKey == null
                            FilterChip(
                                selected = active,
                                onClick = { haptics.selectionToggle(); selectedSectionKey = null },
                                shape = QuietTheme.shapes.pill,
                                colors = chipColors,
                                border = if (active) activeChipBorder else null,
                                label = { Text(stringResource(R.string.ui_all), style = QuietTheme.typography.metadataStrong) }
                            )
                        }
                        rowItems(availableSections, key = { "cat:${it.key}" }, contentType = { "category-chip" }) { section ->
                            val active = effectiveSectionKey == section.key
                            FilterChip(
                                selected = active,
                                onClick = { haptics.selectionToggle(); selectedSectionKey = if (active) null else section.key },
                                shape = QuietTheme.shapes.pill,
                                colors = chipColors,
                                border = if (active) activeChipBorder else null,
                                label = { Text(sectionTitle(section, language), style = QuietTheme.typography.metadataStrong) }
                            )
                        }
                    }
                }
            }
            if (search.isNotBlank()) {
                item("search-count", span = { GridItemSpan(maxLineSpan) }, contentType = "search-count") {
                    Text(
                        stringResource(R.string.tools_search_results_count, filtered.size),
                        style = QuietTheme.typography.metadataStrong,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                if (filtered.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    QuietStateCard(stringResource(R.string.tools_no_matching),
                        stringResource(R.string.tools_no_matching_sub),
                        stringResource(R.string.tools_reset_filters), { onSearch(""); selectedSectionKey = null },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        icon = Icons.Rounded.Search)
                }
                items(filtered, key = { it.id }, contentType = { "tool" }) { tool ->
                    ToolTile(tool, language, { onTool(tool) }, if (reduceMotion) Modifier else Modifier.animateItem(), haptics, onPin = { actionSheetTool = tool }, isPinned = tool.id in pinnedSet)
                }
            } else {
                val activeSection = effectiveSectionKey
                if (activeSection == null && pinned.isNotEmpty()) {
                    item("pinned", span = { GridItemSpan(maxLineSpan) }, contentType = "section-header") {
                        SectionHeader(stringResource(R.string.tools_section_pinned), pinned.size)
                    }
                    items(pinned, key = { "pinned:" + it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, if (reduceMotion) Modifier else Modifier.animateItem(), haptics, onPin = { actionSheetTool = tool }, isPinned = true)
                    }
                }
                if (activeSection == null && recent.isNotEmpty()) {
                    item("recent", span = { GridItemSpan(maxLineSpan) }, contentType = "section-header") {
                        SectionHeader(stringResource(R.string.tools_section_recent), recent.size)
                    }
                    items(recent, key = { "recent:" + it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, if (reduceMotion) Modifier else Modifier.animateItem(), haptics, onPin = { actionSheetTool = tool }, isPinned = tool.id in pinnedSet)
                    }
                }
                for ((section, sectionTools) in sectionedTools) {
                    item("section:" + section.key, span = { GridItemSpan(maxLineSpan) }, contentType = "section-header") {
                        SectionHeader(sectionTitle(section, language), sectionTools.size)
                    }
                    items(sectionTools, key = { it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, if (reduceMotion) Modifier else Modifier.animateItem(), haptics, onPin = { actionSheetTool = tool }, isPinned = tool.id in pinnedSet)
                    }
                }
                if (ungroupedTools.isNotEmpty()) {
                    item("section:all", span = { GridItemSpan(maxLineSpan) }, contentType = "section-header") {
                        SectionHeader(stringResource(R.string.tools_section_all), ungroupedTools.size)
                    }
                    items(ungroupedTools, key = { it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, if (reduceMotion) Modifier else Modifier.animateItem(), haptics, onPin = { actionSheetTool = tool }, isPinned = tool.id in pinnedSet)
                    }
                }

            }
        }
    }
    actionSheetTool?.let { target ->
        val pinnedNow = target.id in pinnedSet
        Dialog(onDismissRequest = { actionSheetTool = null }) {
            Surface(
                shape = QuietTheme.shapes.heroCard,
                color = colors.surface,
                border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    shortToolLabel(target, language),
                    style = QuietTheme.typography.sectionTitle,
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Button(
                    onClick = {
                        haptics.selectionToggle()
                        onPin(target)
                        actionSheetTool = null
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = QuietTheme.shapes.button
                ) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (pinnedNow) R.string.tools_unpin_action else R.string.tools_pin_action),
                        style = QuietTheme.typography.button
                    )
                }
                FilledTonalButton(
                    onClick = {
                        haptics.actionClick()
                        actionSheetTool = null
                        onTool(target)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = QuietTheme.shapes.button
                ) {
                    Text(
                        stringResource(R.string.tools_open_action),
                        style = QuietTheme.typography.metadataStrong
                    )
                }
            }
            }
        }
    }
}

/** Fixed-icon tile: the icon color is the tool's own vivid color (see
 * ToolIconColors), never the theme accent — in a warm system every tile
 * used to read as the same orange. Text stays neutral in every theme. */
@Composable
internal fun ToolTile(tool: UtilityTool, language: AppLanguage, onClick: () -> Unit, modifier: Modifier = Modifier,
    appHaptics: AppHaptics? = null, onPin: (() -> Unit)? = null, isPinned: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val accent = remember(tool.id) { ToolIconColors.iconColorFor(tool.id) }
    val iconForeground = remember(accent) { ToolIconColors.glyphOn(accent) }
    val haptics = appHaptics ?: rememberAppHaptics()
    val click: () -> Unit = { haptics.actionClick(); onClick() }
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(QuietTheme.shapes.inputField)
        .pressFeedback()
        .testTag("tool.${tool.id}")
    val interactiveModifier = if (onPin == null) baseModifier.clickable(onClick = click)
        else baseModifier.combinedClickable(onClick = click, onLongClick = { haptics.longPress(); onPin() })
    Column(
        modifier = interactiveModifier.padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = QuietTheme.shapes.iconTile,
                color = accent,
                modifier = Modifier.size(54.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ToolIcon(
                        tool.id, iconForeground,
                        Modifier.size(26.dp).toolSharedElement(tool.id).testTag("tool.icon.${tool.id}")
                    )
                }
            }
            if (isPinned) {
                Surface(
                    shape = CircleShape,
                    color = if (isDark) colors.surfaceContainerHigh else colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.TopEnd).size(19.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
        Text(
            shortToolLabel(tool, language),
            color = colors.onSurface,
            style = QuietTheme.typography.metadataStrong,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Concise task labels; full catalogue titles still support search and workspace headings. */
internal fun shortToolLabel(tool: UtilityTool, language: AppLanguage): String {
    val labels = when (tool.id) {
        "pdf_01" -> "PDF Reader" to "عارض PDF"
        "pdf_02" -> "Images to PDF" to "صور إلى PDF"
        "pdf_03" -> "Merge PDF" to "دمج PDF"
        "pdf_04" -> "Organize" to "ترتيب"
        "pdf_05" -> "Split PDF" to "تقسيم PDF"
        "pdf_06" -> "Extract" to "استخراج"
        "pdf_07" -> "PDF to Images" to "PDF إلى صور"
        "pdf_long_image" -> "Long Image" to "صورة مطولة"
        "pdf_to_docx" -> "Word" to "Word"
        "pdf_to_pptx" -> "PowerPoint" to "PowerPoint"
        "office_to_pdf" -> "Office to PDF" to "أوفيس إلى PDF"
        "pdf_ocr" -> "Extract Text" to "استخراج نص"
        "pdf_compress" -> "Compress" to "ضغط PDF"
        "pdf_protect" -> "Protect" to "حماية PDF"
        "pdf_nup" -> "2/4 per Sheet" to "2/4 في صفحة"
        "pdf_privacy_redact" -> "Redact" to "طمس"
        "pdf_stamp" -> "Watermark" to "علامة مائية"
        "pdf_signature" -> "Sign PDF" to "توقيع PDF"
        "pdf_crop" -> "Crop PDF" to "قص PDF"
        "scan_doc" -> "Smart Scan" to "مسح ذكي"
        "scan_id_card" -> "ID Card" to "البطاقة"
        "img_01" -> "Convert" to "تحويل الصور"
        "img_02" -> "Resize" to "تغيير الحجم"
        "img_04" -> "Edit & Compress" to "تعديل وضغط"
        "img_bg_remove" -> "Remove BG" to "إزالة الخلفية"
        "img_watermark" -> "Watermark" to "علامة مائية"
        "img_join" -> "Collage" to "كولاج"
        "dl_media" -> "Download" to "تنزيل"
        "file_07" -> "Create ZIP" to "إنشاء ZIP"
        "file_08" -> "Extract ZIP" to "فك ZIP"
        "file_batch_rename" -> "Rename" to "إعادة تسمية"
        else -> tool.titleEn to tool.titleAr
    }
    return if (language.isRtl) labels.second else labels.first
}
