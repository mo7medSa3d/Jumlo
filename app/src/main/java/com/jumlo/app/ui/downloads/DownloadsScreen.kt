package com.jumlo.app.ui.downloads

import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.theme.LocalColorSystem
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Download
import com.jumlo.app.ui.icons.rounded.CheckCircle
import com.jumlo.app.ui.icons.rounded.Bolt
import com.jumlo.app.ui.icons.rounded.History
import com.jumlo.app.ui.icons.rounded.Link
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    val jobLoad by vm.jobLoad.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sources by vm.imported.collectAsStateWithLifecycle()
    val system = LocalColorSystem.current
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
    LaunchedEffect(downloads) {
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
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=14.dp,bottom=QuietTheme.spacing.listBottomBuffer),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        item("download-summary") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = system.splashMid,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(system.splashStart, system.splashMid, system.splashEnd)
                            )
                        )
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 56.dp, y = (-52).dp)
                            .size(190.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(system.splashGlow1.copy(alpha = 0.25f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Download, null, Modifier.size(26.dp), tint = Color.White)
                                }
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    stringResource(R.string.neon_download_headline),
                                    style = QuietTheme.typography.screenTitle,
                                    color = Color.White,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    stringResource(R.string.neon_download_subtitle),
                                    style = QuietTheme.typography.metadata,
                                    color = Color.White.copy(alpha = 0.88f),
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
                                    containerColor = Color.White,
                                    contentColor = system.splashStart
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
                                color = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Rounded.Link, null, Modifier.size(18.dp), tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.ui_batch_download),
                                        style = QuietTheme.typography.metadataStrong,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item("download-stats") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonPanel(Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
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
                        shape = RoundedCornerShape(10.dp),
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
                actionLabel=if (mediaTool!=null) stringResource(R.string.ui_new_download) else null,
                onAction=if (mediaTool!=null) ({ onTool(mediaTool) }) else null,
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
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sources by vm.imported.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<OutputRecord?>(null) }
    val relevantJobs=remember(jobs,selectedJob) { jobs.filter { selectedJob==null || it.request.id==selectedJob } }
    val activeJobs=remember(relevantJobs) { relevantJobs.filter(JobSnapshot::isActive).sortedByDescending(JobSnapshot::updatedAtEpochMs) }
    val recentJobs=remember(relevantJobs) { relevantJobs.filterNot(JobSnapshot::isActive).sortedByDescending(JobSnapshot::updatedAtEpochMs) }
    val availableOutputs=remember(outputs,selectedJob) { outputs.filter { selectedJob==null || it.jobId==selectedJob } }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.widthIn(max=820.dp).fillMaxSize().align(Alignment.TopCenter),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("activity-summary") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.activity_title),style=QuietTheme.typography.screenTitle,modifier=Modifier.semantics { heading() })
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
                ActivityJobRow(vm,job,settings.language,sources)
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
                ActivityJobRow(vm,job,settings.language,sources)
            }
        }
        item("new-run") {
            TextButton(onClick=onNewRun) { Text(stringResource(R.string.activity_start_new_run),style=QuietTheme.typography.metadataStrong) }
        }
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

@Composable private fun ActivityJobRow(vm:AppViewModel,job:JobSnapshot,language:AppLanguage,sources:List<SourceRef>) {
    TransferRow(job,language,null,{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)},
        sourceName=job.request.sourceIds.singleOrNull()?.let { id -> sources.firstOrNull { it.id == id }?.displayName })
    if(job.progress.stage==JobStage.WAITING_FOR_INPUT && job.request.toolId.startsWith("dl_") &&
        job.outcomes.lastOrNull {it.errorCode!=null}?.errorCode in setOf("SECRET_EXPIRED","DOWNLOAD_RESTART_REQUIRED")) {
        var replacement by remember(job.request.id) { mutableStateOf("") }
        OutlinedTextField(replacement,{replacement=it},label={Text(stringResource(R.string.activity_reenter_url))},modifier=Modifier.fillMaxWidth())
        Text(stringResource(R.string.activity_reenter_url_hint),style=QuietTheme.typography.metadata)
        TextButton(onClick={vm.retry(job,replacement);replacement=""},enabled=replacement.isNotBlank()) {
            Text(stringResource(R.string.activity_retry_urls))
        }
    }
    job.outcomes.mapNotNull {it.errorCode}.forEach { errorCode ->
        Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.55f),shape=MaterialTheme.shapes.medium,
            modifier=Modifier.fillMaxWidth().padding(top=6.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(text=stringResource(userMessageResource(errorCode)),style=QuietTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.padding(12.dp))
        }
    }
}
