package com.jumlo.app.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jumlo.app.ui.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.core.ToolCatalogue
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.UtilityTool
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolIconColors
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/** Every available tool has one tile; the catalogue is grouped into sections and
 *  the persisted usage ranking orders the tiles within each section. */
@Composable
fun ToolsScreen(vm: AppViewModel, onTool: (UtilityTool) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var showAllTools by rememberSaveable { mutableStateOf(false) }
    val eligible = remember { CapabilityRegistry.tools.filter {
        CapabilityRegistry.visible(it.id) && CapabilityRegistry.enabled(it.id) && com.jumlo.app.core.ToolCatalogue.isListed(it.id)
    } }
    ToolsCatalogue(eligible, settings.language, settings.rankedToolIds,
        settings.quickActionIds, settings.recentToolIds, search, { search = it },
        showAllTools, { showAllTools = !showAllTools }, onTool)
}

/** One catalogue group: a header and the tool IDs it contains. */
private data class ToolSection(val key: String, val titleEn: String, val titleAr: String, val ids: Set<String>)

/**
 * Sections follow the product brief. Tools outside every section fall into the
 * trailing "All tools" group, so a newly registered tool is never silently hidden.
 */
private val toolSections = listOf(
    ToolSection("scan", "Scan & Capture", "المسح والالتقاط", setOf("scan_doc")),
    ToolSection("organize", "Organize PDF", "تنظيم PDF",
        setOf("pdf_01", "pdf_03", "pdf_04", "pdf_05", "pdf_06", "pdf_crop")),
    ToolSection("convert", "Convert", "التحويل",
        setOf("pdf_to_docx", "pdf_to_pptx", "office_to_pdf", "pdf_02", "pdf_07", "pdf_ocr", "img_01")),
    ToolSection("edit", "Edit & Sign", "التحرير والتوقيع",
        setOf("pdf_signature", "pdf_stamp")),
    ToolSection("optimize", "Optimize", "التحسين",
        setOf("pdf_compress", "img_02", "img_03", "img_04", "img_join", "img_watermark")),
    ToolSection("protect", "Protect", "الحماية", setOf("pdf_protect")),
    ToolSection("download", "Download", "التنزيل", setOf("dl_media", "dl_02", "dl_03")),
    ToolSection("files", "Files", "الملفات",
        setOf("file_07", "file_08", "file_encrypted_zip", "file_batch_rename"))
)

private fun sectionTitle(section: ToolSection, language: AppLanguage): String =
    if (language.isRtl) section.titleAr else section.titleEn

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(colors.primary, CircleShape)
        )
        Text(
            title,
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        count?.let {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.primaryContainer
            ) {
                Text(
                    it.toString(),
                    color = colors.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** Stateless UI boundary lets interaction tests exercise the real catalogue without a database. */
@Composable
internal fun ToolsCatalogue(
    tools: List<UtilityTool>,
    language: AppLanguage,
    rankedToolIds: List<String>,
    pinnedToolIds: List<String>,
    recentToolIds: List<String>,
    search: String,
    onSearch: (String) -> Unit,
    showAllTools: Boolean = false,
    onToggleShowAll: () -> Unit = {},
    onTool: (UtilityTool) -> Unit
) {
    val byId = remember(tools) { tools.associateBy { it.id } }
    val specialistCount = remember(tools) { tools.count { ToolCatalogue.isListed(it.id) && !ToolCatalogue.isPrimary(it.id) } }
    val filtered = remember(tools, rankedToolIds, search, showAllTools) {
        val ranks = rankedToolIds.withIndex().associate { it.value to it.index }
        val query = search.trim()
        tools.withIndex().filter { (_, tool) ->
            if (query.isEmpty()) {
                ToolCatalogue.isListed(tool.id) && (showAllTools || ToolCatalogue.isPrimary(tool.id))
            } else {
                ToolCatalogue.isSearchable(tool.id) &&
                    listOf(
                        tool.id, tool.titleEn, tool.titleAr, tool.subtitleEn, tool.subtitleAr,
                        shortToolLabel(tool, AppLanguage.ENGLISH),
                        shortToolLabel(tool, AppLanguage.ARABIC)
                    ).any { it.contains(query, ignoreCase = true) }
            }
        }.sortedWith(compareBy<IndexedValue<UtilityTool>> { ranks[it.value.id] ?: Int.MAX_VALUE }
            .thenBy { it.index }).map { it.value }
    }
    val pinned = remember(tools, pinnedToolIds) {
        pinnedToolIds.mapNotNull { byId[it] }.filter { CapabilityRegistry.enabled(it.id) && ToolCatalogue.isListed(it.id) }
    }
    val recent = remember(tools, recentToolIds, pinned) {
        val pinnedIds = pinned.map { it.id }.toSet()
        recentToolIds.mapNotNull { byId[it] }.filter { it.id !in pinnedIds && ToolCatalogue.isListed(it.id) }.take(8)
    }
    val featuredIds = (pinned.map { it.id } + recent.map { it.id }).toSet()
    var selectedSectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme
    val availableSections = remember(filtered, selectedSectionKey) {
        toolSections.filter { section -> filtered.any { it.id in section.ids } }
    }
    val largeText = LocalDensity.current.fontScale >= 1.6f
    BoxWithConstraints(Modifier.fillMaxSize().testTag("tools.screen")) {
        val columns = when {
            maxWidth >= 1200.dp -> if (largeText) 3 else 5
            maxWidth >= 900.dp -> if (largeText) 2 else 4
            maxWidth >= 600.dp -> if (largeText) 2 else 3
            largeText -> 1
            else -> 2
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().align(Alignment.TopCenter).testTag("tools.grid"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp,
                bottom = QuietTheme.spacing.listBottomBuffer),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("catalogue-intro", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.tools_catalogue_title),
                        style = QuietTheme.typography.screenTitle,
                        color = colors.onSurface
                    )
                    Text(
                        stringResource(R.string.tools_catalogue_subtitle),
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            item("search", span = { GridItemSpan(maxLineSpan) }) {
                QuietSearchBar(search, stringResource(R.string.neon_tools_search),
                    onSearch, { onSearch("") }, modifier=Modifier.testTag("tools.search"))
            }
            if (search.isBlank() && availableSections.isNotEmpty()) {
                item("category-chips", span = { GridItemSpan(maxLineSpan) }) {
                    val pillars = QuietTheme.pillars
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = pillars.cardSurface,
                        labelColor = colors.onSurfaceVariant,
                        selectedContainerColor = colors.primaryContainer,
                        selectedLabelColor = colors.onPrimaryContainer
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item("cat-all") {
                            val active = selectedSectionKey == null
                            FilterChip(
                                selected = active,
                                onClick = { haptics.selectionToggle(); selectedSectionKey = null },
                                shape = RoundedCornerShape(12.dp),
                                colors = chipColors,
                                border = if (active) BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)) else BorderStroke(1.dp, pillars.cardBorder),
                                label = { Text(stringResource(R.string.ui_all), style = QuietTheme.typography.metadataStrong) }
                            )
                        }
                        rowItems(availableSections, key = { "cat:${it.key}" }) { section ->
                            val active = selectedSectionKey == section.key
                            FilterChip(
                                selected = active,
                                onClick = { haptics.selectionToggle(); selectedSectionKey = if (active) null else section.key },
                                shape = RoundedCornerShape(12.dp),
                                colors = chipColors,
                                border = if (active) BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)) else BorderStroke(1.dp, pillars.cardBorder),
                                label = { Text(sectionTitle(section, language), style = QuietTheme.typography.metadataStrong) }
                            )
                        }
                    }
                }
            }
            if (search.isNotBlank()) {
                item("search-count", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.tools_search_results_count, filtered.size),
                        style = QuietTheme.typography.metadataStrong,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (filtered.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    QuietStateCard(stringResource(R.string.tools_no_matching),
                        stringResource(R.string.tools_no_matching_sub),
                        stringResource(R.string.tools_reset_filters), { onSearch("") },
                        icon = Icons.Rounded.Search)
                }
                items(filtered, key = { it.id }, contentType = { "tool" }) { tool ->
                    ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                }
            } else {
                val activeSection = selectedSectionKey
                if (activeSection == null && pinned.isNotEmpty()) {
                    item("pinned", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.tools_section_pinned), pinned.size)
                    }
                    items(pinned, key = { "pinned:" + it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
                if (activeSection == null && recent.isNotEmpty()) {
                    item("recent", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.tools_section_recent), recent.size)
                    }
                    items(recent, key = { "recent:" + it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
                val remaining = if (activeSection == null) filtered.filter { it.id !in featuredIds } else filtered
                for (section in toolSections) {
                    if (activeSection != null && section.key != activeSection) continue
                    val sectionTools = remaining.filter { it.id in section.ids }
                    if (sectionTools.isEmpty()) continue
                    item("section:" + section.key, span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(sectionTitle(section, language), sectionTools.size)
                    }
                    items(sectionTools, key = { it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
                val ungrouped = if (activeSection == null) {
                    remaining.filter { tool -> toolSections.none { section -> section.ids.contains(tool.id) } }
                } else emptyList()
                if (ungrouped.isNotEmpty()) {
                    item("section:all", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.tools_section_all), ungrouped.size)
                    }
                    items(ungrouped, key = { it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
                if (specialistCount > 0) {
                    item("toggle-all-tools", span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedButton(
                            onClick = onToggleShowAll,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp)
                        ) {
                            Text(if (showAllTools) stringResource(R.string.tools_show_essential) else stringResource(R.string.tools_show_more, specialistCount))
                        }
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
internal fun ToolTile(tool: UtilityTool, language: AppLanguage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val isDark = colors.background.luminance() < 0.5f
    val accent = ToolIconColors.iconColorFor(tool.id)
    val iconForeground = ToolIconColors.glyphOn(accent)
    val tileColor = pillars.cardSurface
    val borderColor = ToolIconColors.mix(pillars.cardBorder, accent, if (isDark) 0.28f else 0.16f)
    val subtitle = if (language.isRtl) tool.subtitleAr else tool.subtitleEn
    val haptics = rememberAppHaptics()
    Surface(
        onClick = { haptics.actionClick(); onClick() },
        modifier = modifier.fillMaxWidth().heightIn(min = 122.dp).pressFeedback().testTag("tool.${tool.id}"),
        shape = QuietTheme.shapes.card,
        color = tileColor,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isDark) 1.dp else 2.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier.size(42.dp).background(accent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ToolIcon(
                        tool.id, iconForeground,
                        Modifier.size(21.dp).toolSharedElement(tool.id).testTag("tool.icon.${tool.id}")
                    )
                }
                Box(
                    Modifier
                        .size(7.dp)
                        .background(accent.copy(alpha = 0.55f), CircleShape)
                )
            }
            Text(
                shortToolLabel(tool, language),
                color = colors.onSurface,
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                subtitle,
                color = colors.onSurfaceVariant,
                style = QuietTheme.typography.metadata,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Concise task labels; full catalogue titles still support search and workspace headings. */
internal fun shortToolLabel(tool: UtilityTool, language: AppLanguage): String {
    val labels = when (tool.id) {
        "pdf_01" -> "View PDF" to "عرض PDF"
        "pdf_02" -> "Images to PDF" to "صور إلى PDF"
        "pdf_03" -> "Merge" to "دمج"
        "pdf_04" -> "Organize pages" to "ترتيب الصفحات"
        "pdf_05" -> "Split" to "تقسيم"
        "pdf_06" -> "Extract pages" to "استخراج صفحات"
        "pdf_07" -> "PDF to images" to "PDF إلى صور"
        "pdf_to_docx" -> "PDF to Word" to "PDF إلى Word"
        "pdf_to_pptx" -> "PDF to PowerPoint" to "PDF إلى PowerPoint"
        "pdf_ocr" -> "OCR" to "استخراج النص"
        "pdf_compress" -> "Compress" to "ضغط"
        "pdf_protect" -> "Password" to "كلمة المرور"
        "pdf_stamp" -> "Watermark" to "علامة مائية"
        "pdf_signature" -> "Sign" to "توقيع"
        "pdf_crop" -> "Crop pages" to "قص الصفحات"
        "scan_doc" -> "Smart Scan" to "مسح ذكي"
        "img_01" -> "Convert images" to "تحويل الصور"
        "img_02" -> "Resize" to "تغيير الحجم"
        "img_03" -> "Crop & rotate" to "قص وتدوير"
        "img_04" -> "Compress images" to "ضغط الصور"
        "dl_media" -> "Download" to "تنزيل"
        "dl_02" -> "Direct link" to "رابط مباشر"
        "dl_03" -> "Batch links" to "عدة روابط"
        "file_07" -> "Create ZIP" to "إنشاء ZIP"
        "file_08" -> "Extract ZIP" to "فك ZIP"
        "file_encrypted_zip" -> "Encrypted ZIP" to "ZIP مشفر"
        "file_batch_rename" -> "Batch rename" to "تسمية جماعية"
        else -> tool.titleEn to tool.titleAr
    }
    return if (language.isRtl) labels.second else labels.first
}
