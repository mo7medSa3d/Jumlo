package com.jumlo.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.core.ToolCatalogue
import com.jumlo.app.core.ToolWorkspace
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolPalette
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/**
 * Compact document-tool discovery, inspired by task-first mobile scanner applications.
 * The registry is the only source of truth: unavailable or unsafe tools never appear
 * as non-working icons. Selection still routes to the real existing workspace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(vm: AppViewModel, onTool: (UtilityTool) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    // The catalogue is the workspaces. A capability that is folded into a workspace
    // stays reachable, but it no longer competes for a tile.
    val eligible = remember {
        CapabilityRegistry.tools.filter {
            CapabilityRegistry.visible(it.id) && CapabilityRegistry.enabled(it.id) &&
                ToolCatalogue.isListed(it.id)
        }
    }
    val byId = remember(eligible) { eligible.associateBy { it.id } }
    val visible = remember(search, category, settings.language, eligible) {
        eligible.filter {
            (category == "all" || it.pillar.name == category) &&
                (search.isBlank() || it.titleAr.contains(search, true) ||
                    it.titleEn.contains(search, true) || it.subtitleEn.contains(search, true) ||
                    it.subtitleAr.contains(search, true))
        }
    }
    val recent = settings.recentToolIds.mapNotNull(byId::get).filter { it in visible }.take(5)
    val frequent = settings.mostUsedToolIds.mapNotNull(byId::get).filter { it in visible }.take(4)
    val largeText = LocalDensity.current.fontScale >= 1.4f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Readable, spacious task tiles: text scaling determines the phone grid.
        val columns = when {
            maxWidth >= 900.dp -> if (largeText) 3 else 5
            maxWidth >= 600.dp -> if (largeText) 2 else 3
            else -> 2
        }
        LazyColumn(
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp,
                bottom = QuietTheme.spacing.listBottomBuffer),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item("catalog-search") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.neon_smart_tools),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface)
                    QuietSearchBar(search, stringResource(R.string.neon_tools_search),
                        { search = it }, { search = "" })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item("all") {
                            FilterChip(category == "all", { category = "all" },
                                modifier = Modifier.heightIn(min = 44.dp),
                                shape = RoundedCornerShape(16.dp), border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                                label = { Text(stringResource(R.string.ui_all)) })
                        }
                        items(PillarCategory.entries.filter { pillar ->
                            eligible.any { it.pillar == pillar }
                        }, key = { it.name }) { pillar ->
                            FilterChip(category == pillar.name, { category = pillar.name },
                                modifier = Modifier.heightIn(min = 44.dp),
                                shape = RoundedCornerShape(16.dp), border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                                label = { Text(if (settings.language.isRtl) pillar.labelAr else pillar.labelEn) })
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item("empty") {
                    QuietStateCard(stringResource(R.string.tools_no_matching),
                        stringResource(R.string.tools_no_matching_sub),
                        stringResource(R.string.tools_reset_filters),
                        { search = ""; category = "all" })
                }
            }
            ToolCatalogue.all().forEach { entry ->
                val primary = visible.filter { it.id in entry.primary }
                val merged = visible.filter { it.id in entry.merged }
                if (primary.isNotEmpty()) {
                    item("workspace-" + entry.workspace.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeonSectionHeader(stringResource(entry.workspace.titleRes()))
                            primary.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { tool ->
                                        CompactCatalogTool(tool, settings.language,
                                            { onTool(tool) }, Modifier.weight(1f))
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            if (merged.isNotEmpty()) {
                                // Folded actions: reachable, but they do not take a tile.
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    merged.forEach { tool ->
                                        QuietChip(selected = false, onClick = { onTool(tool) },
                                            label = if (settings.language.isRtl) tool.titleAr else tool.titleEn)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Task-first tool tile. A single Surface owns hit testing and TalkBack semantics. */
@Composable
private fun CompactCatalogTool(
    tool: UtilityTool,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val tint = ToolPalette.accentFor(tool.id, isDark)
    val iconSurface = ToolPalette.containerFor(tool.id, colors.surfaceContainerLow, isDark)
    val title = if (language.isRtl) tool.titleAr else tool.titleEn
    val haptics = rememberAppHaptics()
    Surface(
        onClick = { haptics.actionClick(); onClick() },
        modifier = modifier.fillMaxWidth().pressFeedback().heightIn(min = 122.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(56.dp).background(iconSurface, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center) {
                ModernToolIcon(tool.iconName, tint, Modifier.size(28.dp))
            }
            Text(title, color = colors.onSurface,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** The heading of a workspace, in the app's own vocabulary. */
internal fun ToolWorkspace.titleRes(): Int = when (this) {
    ToolWorkspace.PDF -> R.string.neon_tools_edit
    ToolWorkspace.SCANNER -> R.string.workspace_scanner
    ToolWorkspace.IMAGES -> R.string.neon_tools_convert
    ToolWorkspace.FILES -> R.string.neon_tools_manage
    ToolWorkspace.DOWNLOADS -> R.string.neon_tools_download
}
