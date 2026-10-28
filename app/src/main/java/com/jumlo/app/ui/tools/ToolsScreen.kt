package com.jumlo.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.UtilityTool
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolPalette
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/** Every available tool has one tile; the catalogue is grouped into sections and
 *  the persisted usage ranking orders the tiles within each section. */
@Composable
fun ToolsScreen(vm: AppViewModel, onTool: (UtilityTool) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val eligible = remember { CapabilityRegistry.tools.filter {
        CapabilityRegistry.visible(it.id) && CapabilityRegistry.enabled(it.id)
    } }
    ToolsCatalogue(eligible, settings.language, settings.rankedToolIds,
        settings.quickActionIds, settings.recentToolIds, search, { search = it }, onTool)
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
        setOf("pdf_to_docx", "pdf_to_pptx", "pdf_02", "pdf_07", "pdf_ocr", "img_01")),
    ToolSection("edit", "Edit & Sign", "التحرير والتوقيع",
        setOf("pdf_annotate", "pdf_signature", "pdf_stamp", "pdf_image", "pdf_forms", "pdf_metadata", "pdf_clean")),
    ToolSection("optimize", "Optimize", "التحسين",
        setOf("pdf_compress", "img_02", "img_03", "img_04", "img_adjust", "img_target", "img_join",
            "img_stitch", "img_compare", "img_exif", "img_watermark")),
    ToolSection("protect", "Protect", "الحماية", setOf("pdf_protect")),
    ToolSection("download", "Download", "التنزيل", setOf("dl_media", "dl_02", "dl_03")),
    ToolSection("files", "Files", "الملفات",
        setOf("file_02", "file_07", "file_08", "file_encrypted_zip", "file_batch_rename",
            "file_duplicates", "file_trash", "sha256", "file_drive"))
)

private fun sectionTitle(section: ToolSection, language: AppLanguage): String =
    if (language.isRtl) section.titleAr else section.titleEn

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
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
    onTool: (UtilityTool) -> Unit
) {
    val byId = remember(tools) { tools.associateBy { it.id } }
    val filtered = remember(tools, rankedToolIds, search) {
        val ranks = rankedToolIds.withIndex().associate { it.value to it.index }
        tools.withIndex().filter { (_, tool) ->
            search.isBlank() || listOf(tool.titleEn, tool.titleAr, tool.subtitleEn, tool.subtitleAr)
                .any { it.contains(search.trim(), ignoreCase = true) }
        }.sortedWith(compareBy<IndexedValue<UtilityTool>> { ranks[it.value.id] ?: Int.MAX_VALUE }
            .thenBy { it.index }).map { it.value }
    }
    val pinned = remember(tools, pinnedToolIds) {
        pinnedToolIds.mapNotNull { byId[it] }.filter { CapabilityRegistry.enabled(it.id) }
    }
    val recent = remember(tools, recentToolIds, pinned) {
        val pinnedIds = pinned.map { it.id }.toSet()
        recentToolIds.mapNotNull { byId[it] }.filter { it.id !in pinnedIds }.take(8)
    }
    val featuredIds = (pinned.map { it.id } + recent.map { it.id }).toSet()
    val largeText = LocalDensity.current.fontScale >= 1.6f
    BoxWithConstraints(Modifier.fillMaxSize().testTag("tools.screen")) {
        val columns = when {
            maxWidth >= 900.dp -> if (largeText) 3 else 5
            maxWidth >= 600.dp -> if (largeText) 2 else 4
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
            item("search", span = { GridItemSpan(maxLineSpan) }) {
                QuietSearchBar(search, stringResource(R.string.neon_tools_search),
                    onSearch, { onSearch("") }, modifier=Modifier.testTag("tools.search"))
            }
            if (search.isNotBlank()) {
                if (filtered.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
                    QuietStateCard(stringResource(R.string.tools_no_matching),
                        stringResource(R.string.tools_no_matching_sub),
                        stringResource(R.string.tools_reset_filters), { onSearch("") })
                }
                items(filtered, key = { it.id }, contentType = { "tool" }) { tool ->
                    ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                }
            } else {
                if (pinned.isNotEmpty()) {
                    item("pinned", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.tools_section_pinned))
                    }
                    items(pinned, key = { "pinned:" + it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
                if (recent.isNotEmpty()) {
                    item("recent", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.tools_section_recent))
                    }
                    items(recent, key = { "recent:" + it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
                val remaining = filtered.filter { it.id !in featuredIds }
                for (section in toolSections) {
                    val sectionTools = remaining.filter { it.id in section.ids }
                    if (sectionTools.isEmpty()) continue
                    item("section:" + section.key, span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(sectionTitle(section, language))
                    }
                    items(sectionTools, key = { it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
                val ungrouped = remaining.filter { tool -> toolSections.none { section -> section.ids.contains(tool.id) } }
                if (ungrouped.isNotEmpty()) {
                    item("section:all", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.tools_section_all))
                    }
                    items(ungrouped, key = { it.id }, contentType = { "tool" }) { tool ->
                        ToolTile(tool, language, { onTool(tool) }, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

/** One click target with an icon and an unclipped name; no categories or text-only shortcuts. */
@Composable
internal fun ToolTile(tool: UtilityTool, language: AppLanguage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val tint = ToolPalette.accentFor(tool.id, isDark)
    val iconSurface = ToolPalette.containerFor(tool.id, colors.surfaceContainerLow, isDark)
    val haptics = rememberAppHaptics()
    Surface(onClick = { haptics.actionClick(); onClick() },
        modifier = modifier.fillMaxWidth().heightIn(min = 132.dp).pressFeedback().testTag("tool.${tool.id}"),
        shape = QuietTheme.shapes.card, color = colors.surfaceContainerLow, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).background(iconSurface, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center) {
                ToolIcon(tool.id, tint, Modifier.size(24.dp).toolSharedElement(tool.id).testTag("tool.icon.${tool.id}"))
            }
            Text(shortToolLabel(tool, language), color = colors.onSurface,
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
        "pdf_clean" -> "Clear metadata" to "مسح البيانات"
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
        "file_duplicates" -> "Find duplicates" to "كشف التكرار"
        else -> tool.titleEn to tool.titleAr
    }
    return if (language.isRtl) labels.second else labels.first
}
