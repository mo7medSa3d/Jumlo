package com.jumlo.app.ui.downloads

import androidx.compose.foundation.layout.*
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
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selecting by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    LaunchedEffect(jobs) {selected=selected.intersect(jobs.filter {it.isActive()}.map {it.request.id}.toSet());if(jobs.none {it.isActive() && it.request.toolId.startsWith("dl_")}) selecting=false}
    val downloads=remember(jobs) {jobs.filter {it.request.toolId.startsWith("dl_")}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(!selecting) item {CapabilityRegistry.tools.find {it.id=="dl_02" && CapabilityRegistry.enabled(it.id)}?.let {tool->QuietPrimaryButton(stringResource(R.string.ui_new_download),{onTool(tool)},Modifier.fillMaxWidth())}}
        if(!selecting) item {CapabilityRegistry.tools.find {it.id=="dl_03" && CapabilityRegistry.enabled(it.id)}?.let {tool->TextButton(onClick={onTool(tool)}) {Text(stringResource(R.string.ui_batch_download))}}}
        item {QuietSectionTitle(stringResource(R.string.transfer_activity_title))}
        if(downloads.any {it.isActive()}) item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={selecting=!selecting;selected=emptySet()}) {Text(stringResource(if(selecting) R.string.action_cancel else R.string.ui_select_transfers))}
            if(selecting) TextButton(onClick={downloads.filter {it.request.id in selected && it.isActive()}.forEach {vm.cancel(it.request.id)};selecting=false;selected=emptySet()},enabled=selected.isNotEmpty()) {Text(stringResource(R.string.ui_cancel_selected,selected.size))}
        }}
        if(downloads.isEmpty()) item {Text(stringResource(R.string.downloads_empty_title),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(downloads,key={it.request.id},contentType={"transfer"}) {job->TransferRow(job,settings.language,{onJob(job.request.id)},{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)},selection=if(selecting && job.isActive()) job.request.id in selected else null,onSelect={selected=if(job.request.id in selected) selected-job.request.id else selected+job.request.id})}
    }
}

@Composable fun ActivityScreen(vm:AppViewModel,selectedJob:String?,onOpen:(OutputRecord)->Unit,onShare:(OutputRecord)->Unit,onExport:(OutputRecord)->Unit,onReuse:(OutputRecord)->Unit,onNewRun:()->Unit) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(stringResource(R.string.activity_published_notice),style=MaterialTheme.typography.bodySmall)}
        items(jobs.filter {selectedJob==null || it.request.id==selectedJob},key={it.request.id}) {job->
            TransferRow(job,settings.language,null,{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)})
            if(job.progress.stage==JobStage.WAITING_FOR_INPUT && job.request.toolId.startsWith("dl_") &&
                job.outcomes.lastOrNull {it.errorCode!=null}?.errorCode in setOf("SECRET_EXPIRED","DOWNLOAD_RESTART_REQUIRED")) {
                var replacement by remember(job.request.id) {mutableStateOf("")}
                OutlinedTextField(replacement,{replacement=it},label={Text(stringResource(R.string.activity_reenter_url))},modifier=Modifier.fillMaxWidth())
                Text(stringResource(R.string.activity_reenter_url_hint),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={vm.retry(job,replacement);replacement=""},enabled=replacement.isNotBlank()) {Text(stringResource(R.string.activity_retry_urls))}
            }
            job.outcomes.filter {it.errorCode!=null}.forEach {Text(stringResource(userMessageResource(it.errorCode!!)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
        }
        items(outputs.filter {selectedJob==null || it.jobId==selectedJob},key={it.id}) {output->OutputRow(output,{onOpen(output)},{onShare(output)},{onExport(output)},{onReuse(output)},{vm.trashOutput(output)})}
        item {TextButton(onClick=onNewRun) {Text(stringResource(R.string.activity_start_new_run))}}
    }
}
