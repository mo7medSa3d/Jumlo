package com.jumlo.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.jumlo.app.ui.theme.ToolPalette
import com.jumlo.app.ui.theme.StaggeredReveal
import com.jumlo.app.ui.theme.pressFeedback

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
    onDownloads: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val outputLoad by vm.outputLoad.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val eligible = remember {
        CapabilityRegistry.tools.filter {
            CapabilityRegistry.visible(it.id) && CapabilityRegistry.canProcess(it.id)
        }
    }
    var search by rememberSaveable { mutableStateOf("") }
    val selected = remember(settings.quickActionIds, eligible) {
        HomeActionPolicy.resolve(settings.quickActionIds, eligible.map { it.id })
    }
    val byId = remember(eligible) { eligible.associateBy { it.id } }
    // A search narrows the tiles; it never hides the way into the whole catalogue.
    val matches = remember(search, eligible) {
        if (search.isBlank()) eligible
        else eligible.filter {
            it.titleEn.contains(search, true) || it.titleAr.contains(search, true) ||
                it.subtitleEn.contains(search, true) || it.subtitleAr.contains(search, true)
        }
    }
    val frequent = selected.mapNotNull(byId::get)
    val suggestions = remember(eligible, selected) {
        listOf("pdf_07", "pdf_compress", "img_01", "img_03", "file_07", "dl_media")
            .filterNot { it in selected }.mapNotNull(byId::get).take(4)
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf(ArrayList(selected)) }
    BackHandler(enabled = editing) { editing = false }
    if (editing) {
        HomeActionEditor(eligible, pending, settings.language,
            { pending = ArrayList(it) }, { vm.setQuickActionIds(pending); editing = false }, { editing = false })
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 840.dp) 4 else 2
        LazyColumn(
            modifier = Modifier.widthIn(max = 980.dp).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp,
                bottom = QuietTheme.spacing.listBottomBuffer),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("intro") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.neon_home_subtitle),
                            style = QuietTheme.typography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onTools,
                        modifier = Modifier.pressFeedback().heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.neon_tools_search))
                    }
                }
            }
            item("home-search") {
                QuietSearchBar(search, stringResource(R.string.home_search_hint),
                    { search = it }, { search = "" })
            }
            item("scan-hero") {
                NeonHero(
                    title = stringResource(R.string.neon_hero_title),
                    description = stringResource(R.string.neon_hero_body),
                    button = stringResource(R.string.neon_start_scan),
                    onAction = onScan
                )
            }
            item("quick-actions") {
                Column {
                    NeonSectionHeader(
                        title = stringResource(R.string.neon_quick_actions),
                        action = stringResource(R.string.ui_customize_actions),
                        onAction = { pending = ArrayList(selected); editing = true }
                    )
                    // Four quick actions on a phone, six on a wide screen: the rest of
                    // the catalogue lives in the Tools sections.
                    val quick = frequent.filter { it in matches }.take(if (columns >= 3) 6 else 4)
                    quick.chunked(columns).forEachIndexed { rowIndex, row ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { tool ->
                                StaggeredReveal(rowIndex, Modifier.weight(1f)) {
                                    NeonToolTile(tool, settings.language, { onTool(tool) },
                                        modifier = Modifier.fillMaxWidth(), compact = true)
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDownloads, modifier = Modifier.weight(1f)
                            .pressFeedback().heightIn(min = 50.dp)) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.neon_shortcut_download),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = onTools, modifier = Modifier.weight(1f)
                            .pressFeedback().heightIn(min = 50.dp)) {
                            Icon(Icons.Rounded.GridView, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.neon_show_all),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (form.sourceIds.isNotEmpty()) {
                item("draft") {
                    NeonPanel {
                        NeonSectionHeader(stringResource(R.string.ui_workspace_draft),
                            action = stringResource(R.string.ui_resume), onAction = onDraft)
                        Text(stringResource(R.string.ui_input_count, form.sourceIds.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = QuietTheme.typography.metadata)
                    }
                }
            }
            val active = jobs.filter { it.isActive() }.take(2)
            if (active.isNotEmpty()) {
                item("active-heading") {
                    NeonSectionHeader(stringResource(R.string.ui_active_tasks))
                }
                items(active, key = { "active-" + it.request.id }) { job ->
                    TransferRow(job, settings.language, { onJob(job.request.id) },
                        { vm.pause(job.request.id) }, { vm.cancel(job.request.id) }, { vm.retry(job) })
                }
            }
            item("recent-files") {
                NeonPanel {
                    NeonSectionHeader(
                        title = stringResource(R.string.neon_recent),
                        action = stringResource(R.string.neon_show_all),
                        onAction = onFiles
                    )
                    if(outputLoad!=com.jumlo.app.ui.DataLoadStatus.READY) DocumentLoadState(outputLoad,vm::reloadSavedData)
                    else if (outputs.isEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Description, null,
                                Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.neon_recent_empty),
                                style = QuietTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(outputs.take(8), key = { it.id }) { output ->
                                Surface(
                                    onClick = { onOutput(output) },
                                    modifier = Modifier.pressFeedback().width(156.dp),
                                    shape = RoundedCornerShape(19.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        NeonOutputPreview(output, Modifier.fillMaxWidth().height(125.dp))
                                        Text(output.displayName,
                                            style = QuietTheme.typography.metadataStrong,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatOutputSizeSummary(output),
                                            style = QuietTheme.typography.metadata,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val pinned = outputs.filter { it.id in settings.favoriteOutputIds }.take(4)
            if (pinned.isNotEmpty()) {
                item("favorites") {
                    NeonPanel {
                        NeonSectionHeader(stringResource(R.string.home_favorite_results))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(pinned, key = { it.id }) { output ->
                                Surface(onClick = { onOutput(output) },
                                    modifier = Modifier.pressFeedback().width(170.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer) {
                                    Row(Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Star, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(output.displayName, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = QuietTheme.typography.metadataStrong)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                // A short row of the most relevant tools, and a way into the rest.
                item("suggestions") {
                    NeonPanel {
                        NeonSectionHeader(
                            title = stringResource(R.string.neon_smart_tools),
                            action = stringResource(R.string.neon_show_all),
                            onAction = onTools
                        )
                        suggestions.take(3).forEach { tool ->
                            FileMenuRow(
                                name = if (settings.language.isRtl) tool.titleAr else tool.titleEn,
                                metadata = if (settings.language.isRtl) tool.subtitleAr else tool.subtitleEn,
                                onOpen = { onTool(tool) },
                                icon = resolveStitchIcon(tool.iconName),
                                showOverflow = false,
                                menu = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
