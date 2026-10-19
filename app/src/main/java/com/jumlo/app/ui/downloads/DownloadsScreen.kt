package com.jumlo.app.ui.downloads

import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.theme.QuietTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.core.StoragePaths

@Composable fun DownloadsScreen(vm:AppViewModel,onTool:(UtilityTool)->Unit,onJob:(String)->Unit) {
    val haptics=rememberAppHaptics()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selecting by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    val (downloads, activeDownloads, previousDownloads) = remember(jobs) {
        val downloads=jobs.filter {it.request.toolId.startsWith("dl_")}.sortedByDescending(JobSnapshot::updatedAtEpochMs)
        val activeDownloads=downloads.filter(JobSnapshot::isActive)
        val previousDownloads=downloads.filterNot(JobSnapshot::isActive)
        Triple(downloads, activeDownloads, previousDownloads)
    }
    LaunchedEffect(downloads) {
        selected=selected.intersect(activeDownloads.map {it.request.id}.toSet())
        if(activeDownloads.isEmpty()) selecting=false
    }
    val completed=remember(downloads) {downloads.count {it.progress.stage==JobStage.COMPLETED}}
    val mediaTool=CapabilityRegistry.tools.find {it.id=="dl_media" && CapabilityRegistry.enabled(it.id)}
        ?: CapabilityRegistry.tools.find {it.id=="dl_02" && CapabilityRegistry.enabled(it.id)}
    val batchTool=CapabilityRegistry.tools.find {it.id=="dl_03" && CapabilityRegistry.enabled(it.id)}
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=QuietTheme.spacing.listBottomBuffer),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item("download-summary") {
            NeonPanel(accent=MaterialTheme.colorScheme.primary) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(shape=RoundedCornerShape(19.dp),
                        color=MaterialTheme.colorScheme.primaryContainer,
                        modifier=Modifier.size(56.dp)) {
                        Box(contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.Download,null,Modifier.size(28.dp),
                                tint=MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.neon_download_headline),
                            style=QuietTheme.typography.screenTitle)
                        Text(stringResource(R.string.neon_download_subtitle),
                            style=QuietTheme.typography.metadata,
                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines=2)
                    }
                }
                if(!selecting && mediaTool!=null) {
                    QuietPrimaryButton(text=stringResource(R.string.ui_new_download),
                        onClick={onTool(mediaTool)},modifier=Modifier.fillMaxWidth(),
                        leadingIcon=Icons.Rounded.Download)
                }
            }
        }
        item("download-stats") {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(shape=RoundedCornerShape(20.dp),
                    color=MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier=Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Bolt,null,Modifier.size(22.dp),
                            tint=MaterialTheme.colorScheme.primary)
                        Text(activeDownloads.size.toString(),
                            style=MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.ui_active_tasks),
                            style=QuietTheme.typography.metadata,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(shape=RoundedCornerShape(20.dp),
                    color=MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier=Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.CheckCircle,null,Modifier.size(22.dp),
                            tint=MaterialTheme.colorScheme.primary)
                        Text(completed.toString(),
                            style=MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.downloads_completed_count,completed),
                            style=QuietTheme.typography.metadata,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if(!selecting && batchTool!=null) item("batch") {
            TextButton(onClick={onTool(batchTool)}) {Text(stringResource(R.string.ui_batch_download),style=QuietTheme.typography.metadataStrong)}
        }
        item("activity-title") {QuietSectionTitle(stringResource(R.string.transfer_activity_title))}
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
                    onSelect={haptics.selectionToggle();selected=if(job.request.id in selected) selected-job.request.id else selected+job.request.id})
            }
        }
        if(previousDownloads.isNotEmpty()) {
            item("previous-title") {QuietSectionTitle(stringResource(R.string.jumlo_activity_history))}
            items(previousDownloads,key={it.request.id},contentType={"past-transfer"}) {job->
                TransferRow(job,settings.language,{onJob(job.request.id)},{vm.pause(job.request.id)},
                    {vm.cancel(job.request.id)},{vm.retry(job)})
            }
        }
        if(downloads.isEmpty()) item("empty") {
            QuietStateCard(stringResource(R.string.downloads_empty_title),stringResource(R.string.premium_download_empty))
        }
    }
}

/** Priority-first operation history: ongoing work, durable results, then past operations. */
@Composable fun ActivityScreen(vm:AppViewModel,selectedJob:String?,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onNewRun:()->Unit,onShowAll:(()->Unit)?=null) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<OutputRecord?>(null) }
    val relevantJobs=remember(jobs,selectedJob) { jobs.filter { selectedJob==null || it.request.id==selectedJob } }
    val activeJobs=remember(relevantJobs) { relevantJobs.filter(JobSnapshot::isActive).sortedByDescending(JobSnapshot::updatedAtEpochMs) }
    val recentJobs=remember(relevantJobs) { relevantJobs.filterNot(JobSnapshot::isActive).sortedByDescending(JobSnapshot::updatedAtEpochMs) }
    val availableOutputs=remember(outputs,selectedJob) { outputs.filter { selectedJob==null || it.jobId==selectedJob } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("activity-summary") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.activity_title),style=QuietTheme.typography.screenTitle)
                    Text(stringResource(R.string.jumlo_activity_summary, activeJobs.size, availableOutputs.size),
                        style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(selectedJob!=null && onShowAll!=null) {
                    TextButton(onClick=onShowAll) {
                        Text(stringResource(R.string.ui_all),style=QuietTheme.typography.metadataStrong)
                    }
                }
            }
        }
        if(activeJobs.isEmpty() && recentJobs.isEmpty() && availableOutputs.isEmpty()) {
            item("activity-empty") {
                QuietStateCard(stringResource(R.string.jumlo_activity_empty_title),
                    stringResource(R.string.jumlo_activity_empty_body))
            }
        }
        if(activeJobs.isNotEmpty()) {
            item("active-title") { QuietSectionTitle(stringResource(R.string.jumlo_activity_active)) }
            items(activeJobs,key={"active-${it.request.id}"},contentType={"active"}) { job ->
                ActivityJobRow(vm,job,settings.language)
            }
        }
        if(availableOutputs.isNotEmpty()) {
            item("results-title") {
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    QuietSectionTitle(stringResource(R.string.jumlo_activity_results))
                    Text(stringResource(R.string.activity_published_notice),style=QuietTheme.typography.metadata)
                }
            }
            items(availableOutputs,key={"output-${it.id}"},contentType={"result"}) { output ->
                OutputRow(output,{onOpen(output)},{onShare(output)},{onExport(output)},{onReuse(output)},{vm.trashOutput(output)},
                    onDetails={details=output},isFavorite=output.id in settings.favoriteOutputIds,
                    onFavorite={vm.toggleOutputFavorite(output.id)})
            }
        }
        if(recentJobs.isNotEmpty()) {
            item("history-title") { QuietSectionTitle(stringResource(R.string.jumlo_activity_history)) }
            items(recentJobs,key={"recent-${it.request.id}"},contentType={"history"}) { job ->
                ActivityJobRow(vm,job,settings.language)
            }
        }
        item("new-run") {
            TextButton(onClick=onNewRun) { Text(stringResource(R.string.activity_start_new_run),style=QuietTheme.typography.metadataStrong) }
        }
    }
    details?.let { output ->
        AlertDialog(
            onDismissRequest={details=null},
            title={Text(output.displayName)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(output.mimeType,style=QuietTheme.typography.metadata)
                Text(formatOutputSizeSummary(output),style=QuietTheme.typography.bodyMedium)
                output.metadata.pageCount?.let { Text(stringResource(R.string.output_pages_count, it),style=QuietTheme.typography.metadata) }
                Text(stringResource(R.string.output_sha256_label, output.sha256.take(16) + "…"),style=QuietTheme.typography.metadata)
                val savedDescription=if(output.publicUri.isNullOrBlank()) {
                    stringResource(R.string.output_saved_privately)
                } else {
                    val folder=buildString {
                        append("Jumlo / ").append(StoragePaths.category(output.mimeType))
                        output.metadata.subfolder?.takeIf { it.isNotBlank() }?.let { append(" / ").append(it) }
                    }
                    stringResource(R.string.output_saved_location,folder)
                }
                Text(savedDescription,style=QuietTheme.typography.metadata)
            }},
            confirmButton={TextButton(onClick={details=null}) {Text(stringResource(R.string.account_done))}}
        )
    }
}

@Composable private fun ActivityJobRow(vm:AppViewModel,job:JobSnapshot,language:AppLanguage) {
    TransferRow(job,language,null,{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)})
    if(job.progress.stage==JobStage.WAITING_FOR_INPUT && job.request.toolId.startsWith("dl_") &&
        job.outcomes.lastOrNull {it.errorCode!=null}?.errorCode in setOf("SECRET_EXPIRED","DOWNLOAD_RESTART_REQUIRED")) {
        var replacement by remember(job.request.id) { mutableStateOf("") }
        OutlinedTextField(replacement,{replacement=it},label={Text(stringResource(R.string.activity_reenter_url))},modifier=Modifier.fillMaxWidth())
        Text(stringResource(R.string.activity_reenter_url_hint),style=QuietTheme.typography.metadata)
        TextButton(onClick={vm.retry(job,replacement);replacement=""},enabled=replacement.isNotBlank()) {
            Text(stringResource(R.string.activity_retry_urls))
        }
    }
    job.outcomes.filter {it.errorCode!=null}.forEach { outcome ->
        Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.55f),shape=MaterialTheme.shapes.medium,
            modifier=Modifier.fillMaxWidth().padding(top=6.dp)) {
            Text(text=stringResource(userMessageResource(outcome.errorCode!!)),style=QuietTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.padding(12.dp))
        }
    }
}
