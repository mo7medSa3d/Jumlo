package com.jumlo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.*
import com.jumlo.app.data.jobs.CapabilityRegistry
import java.util.Locale

fun formatBytes(value:Long?):String {
    if(value==null) return "—"
    if(value<1024) return "$value B"
    val units=listOf("KiB","MiB","GiB","TiB"); var n=value.toDouble()/1024; var index=0
    while(n>=1024 && index<units.lastIndex) {n/=1024;index++}
    return String.format(Locale.ROOT,"%.1f %s",n,units[index])
}

@Composable fun TechnicalText(text:String, modifier:Modifier=Modifier) {
    Text(text,modifier,style=MaterialTheme.typography.bodySmall.copy(textDirection=TextDirection.Ltr),color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun FileMenuRow(name:String,metadata:String,onOpen:(()->Unit)?,icon:ImageVector=Icons.Rounded.InsertDriveFile,selection:Boolean?=null,onSelect:(()->Unit)?=null,showOverflow:Boolean=true,menu:@Composable ColumnScope.(()->Unit)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Surface(modifier=if(selection!=null || onOpen!=null) Modifier.clickable {if(selection!=null) onSelect?.invoke() else onOpen?.invoke()} else Modifier,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(selection!=null) Checkbox(selection,{onSelect?.invoke()}) else Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(name,style=MaterialTheme.typography.titleSmall.copy(textDirection=TextDirection.Ltr),maxLines=2,overflow=TextOverflow.Ellipsis)
                TechnicalText(metadata)
            }
            if(showOverflow) Box {
                IconButton(onClick={expanded=true}) {Icon(Icons.Rounded.MoreVert,stringResource(R.string.ui_more_actions))}
                DropdownMenu(expanded,{expanded=false}) {menu {expanded=false}}
            }
        }
    }
}

@Composable fun OutputRow(output:OutputRecord,onOpen:()->Unit,onShare:()->Unit,onExport:()->Unit,onReuse:()->Unit,onTrash:()->Unit,selection:Boolean?=null,onSelect:(()->Unit)?=null) {
    FileMenuRow(output.displayName,"${formatBytes(output.sizeBytes)} · ${output.mimeType}",onOpen,selection=selection,onSelect=onSelect) {close->
        DropdownMenuItem(text={Text(stringResource(R.string.action_open))},onClick={close();onOpen()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_share))},onClick={close();onShare()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_save_as))},onClick={close();onExport()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_use_as_input))},onClick={close();onReuse()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_move_to_trash))},onClick={close();onTrash()})
    }
}

@Composable fun stageLabel(stage:JobStage):String=stringResource(when(stage) {
    JobStage.QUEUED->R.string.ui_stage_queued;JobStage.VALIDATING->R.string.ui_stage_validating;JobStage.RUNNING->R.string.ui_stage_running;JobStage.FINALIZING->R.string.ui_stage_finalizing;JobStage.COMPLETED->R.string.ui_stage_completed;JobStage.PARTIAL->R.string.ui_stage_partial;JobStage.FAILED->R.string.ui_stage_failed;JobStage.CANCELLED->R.string.ui_stage_cancelled;JobStage.INTERRUPTED->R.string.ui_stage_interrupted;JobStage.WAITING_FOR_INPUT->R.string.ui_stage_waiting
})
fun JobSnapshot.isActive()=progress.stage in setOf(JobStage.QUEUED,JobStage.VALIDATING,JobStage.RUNNING,JobStage.FINALIZING)

@Composable fun TransferRow(job:JobSnapshot,language:AppLanguage,onOpen:(()->Unit)?,onPause:()->Unit,onCancel:()->Unit,onRetry:()->Unit,selection:Boolean?=null,onSelect:(()->Unit)?=null) {
    val tool=CapabilityRegistry.tools.find {it.id==job.request.toolId}
    val name=tool?.let {if(language.isRtl) it.titleAr else it.titleEn} ?: job.request.toolId
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        FileMenuRow(name,stageLabel(job.progress.stage),onOpen,Icons.Rounded.Download,selection=selection,onSelect=onSelect,showOverflow=onOpen!=null || job.isActive() || job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.INTERRUPTED,JobStage.WAITING_FOR_INPUT)) {close->
            if(onOpen!=null) DropdownMenuItem(text={Text(stringResource(R.string.action_details))},onClick={close();onOpen()})
            if(job.isActive()) {
                if(job.request.toolId.startsWith("dl_")) DropdownMenuItem(text={Text(stringResource(R.string.action_pause))},onClick={close();onPause()})
                DropdownMenuItem(text={Text(stringResource(R.string.action_cancel))},onClick={close();onCancel()})
            }
            if(job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.INTERRUPTED,JobStage.WAITING_FOR_INPUT)) DropdownMenuItem(text={Text(stringResource(R.string.action_retry_failed))},onClick={close();onRetry()})
        }
        if(job.isActive()) {
            val total=job.progress.totalUnits
            if(total==null || total<=0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress={(job.progress.completedUnits.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
        }
        if(job.outcomes.isNotEmpty()) Text(stringResource(R.string.job_outcomes_summary,job.outcomes.count {it.outputId!=null},job.outcomes.count {it.status==ItemStatus.FAILED}),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun ToolRow(tool:UtilityTool,language:AppLanguage,onOpen:()->Unit) {
    val icon=when(tool.pillar) {PillarCategory.PDF->Icons.Rounded.PictureAsPdf;PillarCategory.IMAGES->Icons.Rounded.Image;PillarCategory.DOWNLOADS->Icons.Rounded.Download;else->Icons.Rounded.Folder}
    Surface(onClick=onOpen,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            Text(if(language.isRtl) tool.titleAr else tool.titleEn,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
        }
    }
}
