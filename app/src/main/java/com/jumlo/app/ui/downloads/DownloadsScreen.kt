package com.jumlo.app.ui.downloads

import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import androidx.compose.foundation.layout.*
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Download
import com.jumlo.app.ui.icons.rounded.CheckCircle
import com.jumlo.app.ui.icons.rounded.Bolt
import com.jumlo.app.ui.icons.rounded.FolderOpen
import com.jumlo.app.ui.icons.rounded.History
import com.jumlo.app.ui.icons.rounded.Link
import com.jumlo.app.ui.icons.rounded.Refresh
import com.jumlo.app.ui.icons.rounded.Share
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*
import com.jumlo.app.data.jobs.CapabilityRegistry

@Composable fun DownloadsScreen(vm:AppViewModel,onTool:(UtilityTool)->Unit,onJob:(String)->Unit) {
    val haptics=rememberAppHaptics()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val jobLoad by vm.jobLoad.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sources by vm.imported.collectAsStateWithLifecycle()
    val pillars = QuietTheme.pillars
    val colors = MaterialTheme.colorScheme
    var selecting by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    val (downloads, activeDownloads, previousDownloads) = remember(jobs) {
        val downloads=jobs.filter {it.request.toolId.startsWith("dl_")}.sortedByDescending(JobSnapshot::updatedAtEpochMs)
        val activeDownloads=downloads.filter(JobSnapshot::isActive)
        val previousDownloads=downloads.filterNot(JobSnapshot::isActive)
        Triple(downloads, activeDownloads, previousDownloads)
    }
    LaunchedEffect(activeDownloads) {
        selected=selected.intersect(activeDownloads.map {it.request.id}.toSet())
        if(activeDownloads.isEmpty()) selecting=false
    }
    val completed=remember(downloads) {downloads.count {it.progress.stage==JobStage.COMPLETED}}
    val sourceNamesById=remember(sources) {sources.associate {it.id to it.displayName}}
    val mediaTool=remember {
        CapabilityRegistry.tools.find {it.id=="dl_media" && CapabilityRegistry.enabled(it.id)}
            ?: CapabilityRegistry.tools.find {it.id=="dl_02" && CapabilityRegistry.enabled(it.id)}
    }
    val batchTool=remember {CapabilityRegistry.tools.find {it.id=="dl_03" && CapabilityRegistry.enabled(it.id)}}
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier=Modifier.widthIn(max=820.dp).fillMaxSize().align(Alignment.TopCenter),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=14.dp,bottom=24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        item("download-summary") {
            Surface(
                shape = QuietTheme.shapes.heroCard,
                color = pillars.cardSurface,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = QuietTheme.shapes.iconTile,
                                color = colors.primary,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Download, null, Modifier.size(24.dp), tint = colors.onPrimary)
                                }
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    stringResource(R.string.neon_download_headline),
                                    style = QuietTheme.typography.screenTitle,
                                    color = colors.onSurface,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    stringResource(R.string.neon_download_subtitle),
                                    style = QuietTheme.typography.metadata,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                        if (!selecting && mediaTool != null) {
                            Button(
                                onClick = { haptics.toolExecute(); onTool(mediaTool) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).pressFeedback(),
                                shape = QuietTheme.shapes.button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                            ) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ui_new_download), style = QuietTheme.typography.button)
                            }
                        }
                        if (!selecting && batchTool != null) {
                            Surface(
                                onClick = { haptics.actionClick(); onTool(batchTool) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressFeedback(),
                                shape = QuietTheme.shapes.button,
                                color = colors.surfaceContainer,
                                contentColor = colors.onSurface
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Rounded.Link, null, Modifier.size(18.dp), tint = colors.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.ui_batch_download),
                                        style = QuietTheme.typography.metadataStrong,
                                        color = colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (downloads.isNotEmpty()) item("download-stats") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonPanel(Modifier.weight(1f)) {
                    Surface(
                        shape = QuietTheme.shapes.badge,
                        color = colors.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Bolt, null, Modifier.size(20.dp), tint = colors.onPrimaryContainer)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            activeDownloads.size.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Text(
                            stringResource(R.string.ui_active_tasks),
                            style = QuietTheme.typography.metadata,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                NeonPanel(Modifier.weight(1f)) {
                    Surface(
                        shape = QuietTheme.shapes.badge,
                        color = pillars.successContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = pillars.successPrimary)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            completed.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Text(
                            stringResource(R.string.ui_stage_completed),
                            style = QuietTheme.typography.metadata,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item("activity-title") {QuietSectionTitle(stringResource(R.string.transfer_activity_title), Modifier.semantics { heading() })}
        if(activeDownloads.isNotEmpty()) {
            item("selection") {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={haptics.selectionToggle();selecting=!selecting;selected=emptySet()}) {
                        Text(stringResource(if(selecting) R.string.action_cancel else R.string.ui_select_transfers))
                    }
                    if(selecting) TextButton(onClick={
                        haptics.secondaryAction()
                        activeDownloads.filter {it.request.id in selected}.forEach {vm.cancel(it.request.id)}
                        selecting=false;selected=emptySet()
                    },enabled=selected.isNotEmpty()) {Text(stringResource(R.string.ui_cancel_selected,selected.size))}
                }
            }
            items(activeDownloads,key={it.request.id},contentType={"active-transfer"}) {job->
                TransferRow(job,settings.language,{onJob(job.request.id)},{vm.pause(job.request.id)},
                    {vm.cancel(job.request.id)},{vm.retry(job)},
                    selection=if(selecting) job.request.id in selected else null,
                    onSelect={haptics.selectionToggle();selected=if(job.request.id in selected) selected-job.request.id else selected+job.request.id},
                    sourceName=job.request.sourceIds.singleOrNull()?.let { sourceNamesById[it] })
            }
        }
        if(previousDownloads.isNotEmpty()) {
            item("previous-title") {QuietSectionTitle(stringResource(R.string.jumlo_activity_history), Modifier.semantics { heading() })}
            items(previousDownloads,key={it.request.id},contentType={"past-transfer"}) {job->
                TransferRow(job,settings.language,{onJob(job.request.id)},{vm.pause(job.request.id)},
                    {vm.cancel(job.request.id)},{vm.retry(job)},
                    sourceName=job.request.sourceIds.singleOrNull()?.let { sourceNamesById[it] })
            }
        }
        if(jobLoad!=com.jumlo.app.ui.DataLoadStatus.READY) item("load-state") {DocumentLoadState(jobLoad,vm::reloadSavedData)}
        if(jobLoad==com.jumlo.app.ui.DataLoadStatus.READY && downloads.isEmpty()) item("empty") {
            QuietStateCard(
                title=stringResource(R.string.downloads_empty_title),
                subtitle=stringResource(R.string.premium_download_empty),
                icon=Icons.Rounded.Download
            )
        }
    }
    }
}

/** Priority-first operation history: ongoing work, durable results, then past operations. */
@Composable fun ActivityScreen(vm:AppViewModel,selectedJob:String?,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onNewRun:()->Unit,onShowAll:(()->Unit)?=null,onPrint:((OutputRecord)->Unit)?=null) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val jobLoad by vm.jobLoad.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sources by vm.imported.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<OutputRecord?>(null) }
    val relevantJobs=remember(jobs,selectedJob) { jobs.filter { selectedJob==null || it.request.id==selectedJob } }
    val activeJobs=remember(relevantJobs) { relevantJobs.filter(JobSnapshot::isActive).sortedByDescending(JobSnapshot::updatedAtEpochMs) }
    val recentJobs=remember(relevantJobs) { relevantJobs.filterNot(JobSnapshot::isActive).sortedByDescending(JobSnapshot::updatedAtEpochMs) }
    val availableOutputs=remember(outputs,selectedJob) { outputs.filter { selectedJob==null || it.jobId==selectedJob } }
    val jobHasTrashedOutputs=remember(trashed,selectedJob) { selectedJob!=null && trashed.any { it.jobId==selectedJob } }
    val sourceNamesById=remember(sources) { sources.associate { it.id to it.displayName } }
    val focusJob=remember(relevantJobs,selectedJob) { selectedJob?.let { id -> relevantJobs.firstOrNull { it.request.id==id } } }
    var finalizingTimedOut by remember(focusJob?.request?.id) { mutableStateOf(false) }
    val waitingForOutputs = focusJob?.progress?.stage == JobStage.COMPLETED && availableOutputs.isEmpty() && !jobHasTrashedOutputs && focusJob.outcomes.isNotEmpty()
    LaunchedEffect(focusJob?.request?.id, waitingForOutputs) {
        if (!waitingForOutputs) {
            finalizingTimedOut = false
        } else {
            kotlinx.coroutines.delay(2500L)
            finalizingTimedOut = true
        }
    }
    val completedOutputsReady = focusJob?.progress?.stage == JobStage.COMPLETED && availableOutputs.isNotEmpty()
    val completedOutputsPending = waitingForOutputs && !finalizingTimedOut
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.widthIn(max=820.dp).fillMaxSize().align(Alignment.TopCenter),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=14.dp,bottom=QuietTheme.spacing.listBottomBuffer),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("activity-summary") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.jumlo_activity_summary, activeJobs.size, availableOutputs.size),
                    style=QuietTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                if(selectedJob!=null && onShowAll!=null) {
                    TextButton(onClick=onShowAll) {
                        Text(stringResource(R.string.ui_all),style=QuietTheme.typography.metadataStrong)
                    }
                }
            }
        }
        if(completedOutputsReady) {
            item("completion-hero") {
                CompletedResultCard(
                    outputs = availableOutputs,
                    onOpen = { onOpen(availableOutputs.first()) },
                    onShare = { onShare(availableOutputs.first()) },
                    onNewRun = onNewRun
                )
            }
        } else if(completedOutputsPending) {
            item("completion-finalizing") {
                NeonPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(stringResource(R.string.s10_finalizing_output), style = QuietTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if(jobLoad!=com.jumlo.app.ui.DataLoadStatus.READY) item("load-state") {DocumentLoadState(jobLoad,vm::reloadSavedData)}
        if(jobLoad==com.jumlo.app.ui.DataLoadStatus.READY && activeJobs.isEmpty() && recentJobs.isEmpty() && availableOutputs.isEmpty()) {
            item("activity-empty") {
                QuietStateCard(stringResource(R.string.jumlo_activity_empty_title),
                    stringResource(R.string.jumlo_activity_empty_body),
                    icon=Icons.Rounded.History)
            }
        }
        if(activeJobs.isNotEmpty()) {
            item("active-title") { QuietSectionTitle(stringResource(R.string.jumlo_activity_active), Modifier.semantics { heading() }) }
            items(activeJobs,key={"active-${it.request.id}"},contentType={"active"}) { job ->
                ActivityJobRow(vm,job,settings.language,job.request.sourceIds.singleOrNull()?.let { sourceNamesById[it] })
            }
        }
        if(availableOutputs.isNotEmpty()) {
            item("results-title") {
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    QuietSectionTitle(stringResource(R.string.jumlo_activity_results), Modifier.semantics { heading() })
                    Text(stringResource(R.string.activity_published_notice),style=QuietTheme.typography.metadata)
                }
            }
            items(availableOutputs,key={"output-${it.id}"},contentType={"result"}) { output ->
                OutputRow(output,{onOpen(output)},{onShare(output)},{onExport(output)},{onReuse(output)},{vm.trashOutput(output)},
                    onDetails={details=output},isFavorite=output.id in settings.favoriteOutputIds,
                    onFavorite={vm.toggleOutputFavorite(output.id)},
                    onPrint=if(onPrint!=null && (output.mimeType=="application/pdf" || output.mimeType.startsWith("image/"))) ({onPrint(output)}) else null)
            }
        }
        if(recentJobs.isNotEmpty()) {
            item("history-title") { QuietSectionTitle(stringResource(R.string.jumlo_activity_history), Modifier.semantics { heading() }) }
            items(recentJobs,key={"recent-${it.request.id}"},contentType={"history"}) { job ->
                ActivityJobRow(vm,job,settings.language,job.request.sourceIds.singleOrNull()?.let { sourceNamesById[it] })
            }
        }
        item("new-run") {
            TextButton(onClick=onNewRun) { Text(stringResource(R.string.activity_start_new_run),style=QuietTheme.typography.metadataStrong) }
        }
    }
    }
    details?.let { output ->
        OutputDetailsDialog(output = output, onDismiss = { details = null })
    }
}

private val REENTER_URL_ERROR_CODES = setOf("SECRET_EXPIRED", "DOWNLOAD_RESTART_REQUIRED")

@Composable private fun ActivityJobRow(vm:AppViewModel,job:JobSnapshot,language:AppLanguage,sourceName:String?) {
    TransferRow(job,language,null,{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)},
        sourceName=sourceName)
    if(job.progress.stage==JobStage.WAITING_FOR_INPUT && job.request.toolId.startsWith("dl_") &&
        job.outcomes.lastOrNull {it.errorCode!=null}?.errorCode in REENTER_URL_ERROR_CODES) {
        var replacement by remember(job.request.id) { mutableStateOf("") }
        OutlinedTextField(
            value = replacement,
            onValueChange = { replacement = it },
            label = { Text(stringResource(R.string.activity_reenter_url)) },
            singleLine = true,
            shape = QuietTheme.shapes.inputField,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.activity_reenter_url_hint),style=QuietTheme.typography.metadata)
        TextButton(onClick={vm.retry(job,replacement);replacement=""},enabled=replacement.isNotBlank(),modifier=Modifier.heightIn(min=48.dp)) {
            Text(stringResource(R.string.activity_retry_urls))
        }
    }
    // Single error presentation is handled directly inside TransferRow to avoid duplicate error banners.
}

@Composable
internal fun CompletedResultCard(
    outputs: List<OutputRecord>,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onNewRun: () -> Unit,
    modifier: Modifier = Modifier,
    newRunLabel: String? = null
) {
    val primary = outputs.firstOrNull() ?: return
    val sizeSummary = if (outputs.size > 1) formatBytes(outputs.sumOf { it.sizeBytes }) else formatOutputSizeSummary(primary)
    // formatOutputSizeSummary already appends the subfolder when present.
    val metadataLine = if (primary.metadata.subfolder.isNullOrBlank()) {
        "$sizeSummary · ${stringResource(R.string.s10_saved_location_default)}"
    } else sizeSummary
    CompletedResultCard(
        title = if (outputs.size > 1) stringResource(R.string.s10_files_saved_count, outputs.size)
        else stringResource(R.string.s10_saved_title),
        displayName = primary.displayName,
        metadataLine = metadataLine,
        onOpen = onOpen,
        onShare = onShare,
        onNewRun = onNewRun,
        newRunLabel = newRunLabel ?: stringResource(R.string.s10_new_operation),
        modifier = modifier
    )
}

@Composable
internal fun CompletedResultCard(
    title: String,
    displayName: String,
    metadataLine: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onNewRun: () -> Unit,
    newRunLabel: String,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    val colors = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        shape = QuietTheme.shapes.heroCard,
        color = pillars.cardSurface,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth().testTag("completion.result.card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = QuietTheme.shapes.iconTile,
                    color = pillars.successContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = pillars.successPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = QuietTheme.typography.sectionTitle,
                        color = pillars.successPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = displayName,
                        style = QuietTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDirection = TextDirection.Content
                        ),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metadataLine,
                        style = QuietTheme.typography.metadata.copy(textDirection = TextDirection.Content),
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { haptics.actionClick(); onOpen() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).pressFeedback(),
                    shape = QuietTheme.shapes.button
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = { haptics.actionClick(); onShare() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).pressFeedback(),
                    shape = QuietTheme.shapes.button
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(
                onClick = { haptics.actionClick(); onNewRun() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressFeedback(),
                shape = QuietTheme.shapes.button
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(newRunLabel)
            }
        }
    }
}
