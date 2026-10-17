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
import androidx.compose.ui.text.font.FontWeight
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

@Composable fun DownloadsScreen(vm:AppViewModel,onTool:(UtilityTool)->Unit,onJob:(String)->Unit) {
    val haptics=rememberAppHaptics()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selecting by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    LaunchedEffect(jobs) {selected=selected.intersect(jobs.filter {it.isActive()}.map {it.request.id}.toSet());if(jobs.none {it.isActive() && it.request.toolId.startsWith("dl_")}) selecting=false}
    val downloads=remember(jobs) {jobs.filter {it.request.toolId.startsWith("dl_")}}
    val active=downloads.count {it.isActive()}
    val completed=downloads.count {it.progress.stage==JobStage.COMPLETED}
    val mediaTool=CapabilityRegistry.tools.find {it.id=="dl_media" && CapabilityRegistry.enabled(it.id)}
        ?: CapabilityRegistry.tools.find {it.id=="dl_02" && CapabilityRegistry.enabled(it.id)}
    val batchTool=CapabilityRegistry.tools.find {it.id=="dl_03" && CapabilityRegistry.enabled(it.id)}
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=QuietTheme.spacing.listBottomBuffer),
        verticalArrangement=Arrangement.spacedBy(18.dp)
    ) {
        item("download-hero") {
            Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(30.dp),
                color=MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.Download,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.redesign_download_hero),style=QuietTheme.typography.screenTitle,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.brand_downloads_tagline),style=QuietTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    if(!selecting && mediaTool!=null) QuietPrimaryButton(stringResource(R.string.ui_new_download),{onTool(mediaTool)},Modifier.fillMaxWidth())
                }
            }
        }
        item("download-stats") {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                listOf(Triple(active,R.string.brand_download_active,Icons.Rounded.Bolt),
                    Triple(completed,R.string.brand_download_done,Icons.Rounded.CheckCircle)).forEach {(number,label,icon)->
                    Surface(Modifier.weight(1f).height(84.dp),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxSize().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
                            Column {
                                Text(number.toString(),style=QuietTheme.typography.sectionTitle,fontWeight=FontWeight.Bold)
                                Text(stringResource(label),style=QuietTheme.typography.metadata)
                            }
                        }
                    }
                }
            }
        }
        if(!selecting && batchTool!=null) item("batch") {
            TextButton(onClick={onTool(batchTool)}) {Text(stringResource(R.string.ui_batch_download),style=QuietTheme.typography.metadataStrong)}
        }
        item("activity-title") { QuietSectionTitle(stringResource(R.string.transfer_activity_title)) }
        if(downloads.any {it.isActive()}) item("selection") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={haptics.selectionToggle();selecting=!selecting;selected=emptySet()}) {Text(stringResource(if(selecting) R.string.action_cancel else R.string.ui_select_transfers))}
                if(selecting) TextButton(onClick={haptics.secondaryAction();downloads.filter {it.request.id in selected && it.isActive()}.forEach {vm.cancel(it.request.id)};selecting=false;selected=emptySet()},enabled=selected.isNotEmpty()) {Text(stringResource(R.string.ui_cancel_selected,selected.size))}
            }
        }
        if(downloads.isEmpty()) item("empty") {QuietStateCard(stringResource(R.string.downloads_empty_title),stringResource(R.string.brand_downloads_tagline))}
        items(downloads,key={it.request.id},contentType={"transfer"}) {job->
            TransferRow(job,settings.language,{onJob(job.request.id)},{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)},selection=if(selecting && job.isActive()) job.request.id in selected else null,onSelect={haptics.selectionToggle();selected=if(job.request.id in selected) selected-job.request.id else selected+job.request.id})
        }
    }
}

@Composable fun ActivityScreen(vm:AppViewModel,selectedJob:String?,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onNewRun:()->Unit) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(stringResource(R.string.activity_published_notice),style=QuietTheme.typography.metadata)}
        items(jobs.filter {selectedJob==null || it.request.id==selectedJob},key={it.request.id}) {job->
            TransferRow(job,settings.language,null,{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)})
            if(job.progress.stage==JobStage.WAITING_FOR_INPUT && job.request.toolId.startsWith("dl_") &&
                job.outcomes.lastOrNull {it.errorCode!=null}?.errorCode in setOf("SECRET_EXPIRED","DOWNLOAD_RESTART_REQUIRED")) {
                var replacement by remember(job.request.id) {mutableStateOf("")}
                OutlinedTextField(replacement,{replacement=it},label={Text(stringResource(R.string.activity_reenter_url))},modifier=Modifier.fillMaxWidth())
                Text(stringResource(R.string.activity_reenter_url_hint),style=QuietTheme.typography.metadata)
                TextButton(onClick={vm.retry(job,replacement);replacement=""},enabled=replacement.isNotBlank()) {Text(stringResource(R.string.activity_retry_urls))}
            }
            job.outcomes.filter {it.errorCode!=null}.forEach {outcome ->
                Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.55f),shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().padding(top=6.dp)) {
                    Text(
                        text=stringResource(userMessageResource(outcome.errorCode!!)),
                        style=QuietTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onErrorContainer,
                        modifier=Modifier.padding(12.dp)
                    )
                }
            }
        }
        items(outputs.filter {selectedJob==null || it.jobId==selectedJob},key={it.id}) {output->OutputRow(output,{onOpen(output)},{onShare(output)},{onExport(output)},{onReuse(output)},{vm.trashOutput(output)})}
        item {TextButton(onClick=onNewRun) {Text(stringResource(R.string.activity_start_new_run),style=QuietTheme.typography.metadataStrong)}}
    }
}
