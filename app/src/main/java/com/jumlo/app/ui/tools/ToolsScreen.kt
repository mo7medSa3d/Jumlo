package com.jumlo.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/**
 * Compact document-tool discovery, inspired by task-first mobile scanner applications.
 * The registry is the only source of truth: unavailable or unsafe tools never appear
 * as non-working icons. Selection still routes to the real existing workspace.
 */
@Composable
fun ToolsScreen(vm: AppViewModel, onTool: (UtilityTool) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    val eligible = remember {
        CapabilityRegistry.tools.filter {
            CapabilityRegistry.visible(it.id) && CapabilityRegistry.enabled(it.id) &&
                it.id !in setOf("dl_settings", "file_02", "file_trash")
        }
    }
    val byId = remember(eligible) { eligible.associateBy { it.id } }
    val visible = remember(search, category, settings.language, eligible) {
        eligible.filter {
            (category == "all" || it.pillar.name == category) &&
                (search.isBlank() || it.titleAr.contains(search, true) ||
                    it.titleEn.contains(search, true) || it.subtitleEn.contains(search, true))
        }
    }
    val recent = settings.recentToolIds.mapNotNull(byId::get).filter { it in visible }.take(5)
    val frequent = settings.mostUsedToolIds.mapNotNull(byId::get).filter { it in visible }.take(4)
    val largeText = LocalDensity.current.fontScale >= 1.4f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> if (largeText) 5 else 6
            maxWidth >= 600.dp -> if (largeText) 4 else 5
            maxWidth >= 375.dp && !largeText -> 4
            else -> 3
        }
        LazyColumn(
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp,
                bottom = QuietTheme.spacing.listBottomBuffer),
            verticalArrangement = Arrangement.spacedBy(22.dp)
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
                                label = { Text(stringResource(R.string.ui_all)) })
                        }
                        items(PillarCategory.entries.filter { pillar ->
                            eligible.any { it.pillar == pillar }
                        }, key = { it.name }) { pillar ->
                            FilterChip(category == pillar.name, { category = pillar.name },
                                label = { Text(if (settings.language.isRtl) pillar.labelAr else pillar.labelEn) })
                        }
                    }
                }
            }
            if (recent.isNotEmpty() && category == "all" && search.isBlank()) {
                item("recent-tools") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeonSectionHeader(stringResource(R.string.redesign_recent_tools))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recent, key = { it.id }) { tool ->
                                CompactCatalogTool(tool, settings.language, { onTool(tool) },
                                    Modifier.width(94.dp))
                            }
                        }
                    }
                }
            }
            if (frequent.isNotEmpty() && category == "all" && search.isBlank()) {
                item("popular") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeonSectionHeader(stringResource(R.string.redesign_most_used))
                        frequent.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { tool ->
                                    CompactCatalogTool(tool, settings.language,
                                        { onTool(tool) }, Modifier.weight(1f))
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
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
            val groups = listOf(
                PillarCategory.PDF to R.string.neon_tools_edit,
                PillarCategory.IMAGES to R.string.neon_tools_convert,
                PillarCategory.DOWNLOADS to R.string.neon_tools_download,
                PillarCategory.FILES to R.string.neon_tools_manage,
                PillarCategory.VAULT to R.string.neon_tools_other
            )
            groups.forEach { (pillar, titleId) ->
                val group = visible.filter { it.pillar == pillar }
                if (group.isNotEmpty()) {
                    item("group-" + pillar.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeonSectionHeader(stringResource(titleId))
                            group.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { tool ->
                                        CompactCatalogTool(tool, settings.language,
                                            { onTool(tool) }, Modifier.weight(1f))
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 48dp+ tap target, high-contrast label, and no fixed-width English-only graphics. */
@Composable
private fun CompactCatalogTool(
    tool: UtilityTool,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    val (tint, container) = when (tool.pillar) {
        PillarCategory.PDF -> pillars.pdfPrimary to pillars.pdfContainer
        PillarCategory.IMAGES -> pillars.imagesPrimary to pillars.imagesContainer
        PillarCategory.DOWNLOADS -> pillars.downloadsPrimary to pillars.downloadsContainer
        PillarCategory.FILES -> pillars.filesPrimary to pillars.filesContainer
        PillarCategory.VAULT -> pillars.vaultPrimary to pillars.vaultContainer
    }
    val title = if (language.isRtl) tool.titleAr else tool.titleEn
    val haptics = rememberAppHaptics()
    Column(
        modifier = modifier
            .pressFeedback()
            .clickable(role = Role.Button) { haptics.actionClick(); onClick() }
            .semantics { contentDescription = title }
            .heightIn(min = 104.dp)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = container,
            contentColor = tint
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(resolveStitchIcon(tool.iconName), contentDescription = null,
                    modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
