package com.jumlo.app.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.*
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.StaggeredReveal

/** Only real, available capabilities are rendered. Nothing is a simulated converter. */
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        LazyColumn(
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp,
                bottom = QuietTheme.spacing.listBottomBuffer),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("catalog-search") {
                NeonPanel {
                    NeonSectionHeader(stringResource(R.string.neon_smart_tools))
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
                    NeonPanel {
                        NeonSectionHeader(stringResource(R.string.redesign_recent_tools))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(recent, key = { it.id }) { tool ->
                                AssistChip(onClick = { onTool(tool) },
                                    leadingIcon = {
                                        Icon(resolveStitchIcon(tool.iconName), null, Modifier.size(17.dp))
                                    },
                                    label = {
                                        Text(if (settings.language.isRtl) tool.titleAr else tool.titleEn,
                                            style = QuietTheme.typography.metadataStrong)
                                    })
                            }
                        }
                    }
                }
            }
            if (frequent.isNotEmpty() && category == "all" && search.isBlank()) {
                item("popular") {
                    NeonPanel {
                        NeonSectionHeader(stringResource(R.string.redesign_most_used))
                        frequent.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { tool ->
                                    NeonToolTile(tool, settings.language, { onTool(tool) },
                                        Modifier.weight(1f), compact = true)
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
                        NeonPanel {
                            NeonSectionHeader(stringResource(titleId))
                            group.chunked(columns).forEachIndexed { rowIndex, row ->
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { tool ->
                                        StaggeredReveal(rowIndex.coerceAtMost(10), Modifier.weight(1f)) {
                                            NeonToolTile(tool, settings.language,
                                                { onTool(tool) }, Modifier.fillMaxWidth())
                                        }
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
