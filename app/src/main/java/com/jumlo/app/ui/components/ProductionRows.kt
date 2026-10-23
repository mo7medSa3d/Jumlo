package com.jumlo.app.ui.components

import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.graphicsLayer
import com.jumlo.app.ui.theme.LocalReducedMotion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.semantics.*
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.*
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
import com.jumlo.app.ui.theme.QuietTheme
import java.util.Locale
import kotlin.math.roundToInt

fun formatBytes(value:Long?):String {
    if(value==null) return "—"
    if(value<1024) return "$value B"
    val units=listOf("KiB","MiB","GiB","TiB"); var n=value.toDouble()/1024; var index=0
    while(n>=1024 && index<units.lastIndex) {n/=1024;index++}
    return String.format(Locale.ROOT,"%.1f %s",n,units[index])
}

@Composable
fun formatOutputSizeSummary(output: OutputRecord): String {
    val before = output.metadata.originalSizeBytes
    val after = output.sizeBytes
    val sizePart = if (before != null && before > 0L) {
        val exactSaved = if (before > after) (before - after).toDouble() * 100.0 / before else 0.0
        val savedPct = exactSaved.roundToInt().coerceIn(0, 99)
        if (savedPct > 0)
            stringResource(R.string.ui_size_before_after, formatBytes(before), formatBytes(after), savedPct)
        else
            stringResource(R.string.visual_actual_output_size, formatBytes(before), formatBytes(after))
    } else formatBytes(after)
    val folderPart = output.metadata.subfolder?.takeIf { it.isNotBlank() }?.let {
        " · " + stringResource(R.string.ui_saved_subfolder, it)
    }.orEmpty()
    return sizePart + folderPart
}

@Composable fun TechnicalText(text:String, modifier:Modifier=Modifier) {
    Text(text,modifier,style=QuietTheme.typography.metadata.copy(textDirection=TextDirection.Content),color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun outputTypeLabel(mimeType: String): String = stringResource(when {
    mimeType == "application/pdf" -> R.string.redesign_pdf_type
    mimeType.startsWith("image/") -> R.string.redesign_filter_images
    mimeType.startsWith("video/") -> R.string.redesign_filter_video
    mimeType.startsWith("audio/") -> R.string.redesign_filter_audio
    mimeType in setOf("application/zip", "application/x-zip-compressed", "application/x-7z-compressed") -> R.string.filter_archives
    else -> R.string.redesign_filter_documents
})

@OptIn(ExperimentalFoundationApi::class)
@Composable fun FileMenuRow(name:String,metadata:String,onOpen:(()->Unit)?,icon:ImageVector=Icons.Rounded.InsertDriveFile,selection:Boolean?=null,onSelect:(()->Unit)?=null,showOverflow:Boolean=true,onLongClick:(()->Unit)?=null,leadingPreview:(@Composable ()->Unit)?=null,menu:@Composable ColumnScope.(()->Unit)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    val haptics=rememberAppHaptics()
    Surface(modifier=if(selection!=null || onOpen!=null) Modifier.pressFeedback().combinedClickable(
        onClick={haptics.actionClick();if(selection!=null) onSelect?.invoke() else onOpen?.invoke()},
        onLongClick=onLongClick
    ).semantics { if(selection!=null) { selected=selection; role=Role.Checkbox } } else Modifier,shape=QuietTheme.shapes.card,color=if(selection==true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(selection!=null) Checkbox(selection,onCheckedChange=null)
            else if(leadingPreview!=null) leadingPreview()
            else Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(name,style=QuietTheme.typography.bodyMedium.copy(textDirection=TextDirection.Content),maxLines=2,overflow=TextOverflow.Ellipsis)
                TechnicalText(metadata)
            }
            if(showOverflow) Box {
                IconButton(onClick={haptics.actionClick();expanded=true},modifier=Modifier.pressFeedback()) {Icon(Icons.Rounded.MoreVert,stringResource(R.string.ui_more_actions))}
                DropdownMenu(expanded,{expanded=false}) {menu {expanded=false}}
            }
        }
    }
}

@Composable fun OutputRow(output:OutputRecord,onOpen:()->Unit,onShare:()->Unit,onExport:()->Unit,onReuse:()->Unit,onTrash:()->Unit,selection:Boolean?=null,onSelect:(()->Unit)?=null,onDetails:(()->Unit)?=null,isFavorite:Boolean=false,onFavorite:(()->Unit)?=null,onPrint:(()->Unit)?=null,onRename:(()->Unit)?=null,onLongClick:(()->Unit)?=null) {
    val summary = "${formatOutputSizeSummary(output)} · ${com.jumlo.app.ui.files.fileDateLabel(output.createdAtEpochMs)}"
    val outputIcon=when {
        output.mimeType=="application/pdf" -> Icons.Rounded.PictureAsPdf
        output.mimeType.startsWith("image/") -> Icons.Rounded.Image
        output.mimeType.startsWith("video/") -> Icons.Rounded.Movie
        output.mimeType.startsWith("audio/") -> Icons.Rounded.AudioFile
        output.mimeType.contains("zip") -> Icons.Rounded.FolderZip
        else -> Icons.Rounded.InsertDriveFile
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
    FileMenuRow(output.displayName,summary,onOpen,icon=outputIcon,selection=selection,onSelect=onSelect,onLongClick=onLongClick,
        leadingPreview={NeonOutputPreview(output,Modifier.size(56.dp))}) {close->
        DropdownMenuItem(text={Text(stringResource(R.string.action_open))},onClick={close();onOpen()})
        if(onFavorite!=null) DropdownMenuItem(
            text={Text(stringResource(if(isFavorite) R.string.files_unmark_favorite else R.string.files_mark_favorite))},
            leadingIcon={Icon(if(isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,null)},
            onClick={close();onFavorite()})
        if(onPrint!=null) DropdownMenuItem(text={Text(stringResource(R.string.upgrade_print))},leadingIcon={Icon(Icons.Rounded.Print,null)},onClick={close();onPrint()})
        if(onRename!=null) DropdownMenuItem(text={Text(stringResource(R.string.action_rename))},onClick={close();onRename()})
        if(onDetails!=null) DropdownMenuItem(text={Text(stringResource(R.string.action_details))},onClick={close();onDetails()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_share))},onClick={close();onShare()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_save_as))},onClick={close();onExport()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_use_as_input))},onClick={close();onReuse()})
        DropdownMenuItem(text={Text(stringResource(R.string.action_move_to_trash))},onClick={close();onTrash()})
    }
    if(output.publicUri==null) Text(
        stringResource(R.string.ui_private_output_save_as),
        modifier=Modifier.padding(horizontal=12.dp),
        style=QuietTheme.typography.metadata,
        color=MaterialTheme.colorScheme.error
    )
    }
}

@Composable fun stageLabel(stage:JobStage):String=stringResource(when(stage) {
    JobStage.QUEUED->R.string.ui_stage_queued;JobStage.VALIDATING->R.string.ui_stage_validating;JobStage.RUNNING->R.string.ui_stage_running;JobStage.FINALIZING->R.string.ui_stage_finalizing;JobStage.COMPLETED->R.string.ui_stage_completed;JobStage.PARTIAL->R.string.ui_stage_partial;JobStage.FAILED->R.string.ui_stage_failed;JobStage.CANCELLED->R.string.ui_stage_cancelled;JobStage.INTERRUPTED->R.string.ui_stage_interrupted;JobStage.PAUSED->R.string.ui_stage_paused;JobStage.WAITING_FOR_INPUT->R.string.ui_stage_waiting
})
fun JobSnapshot.isActive()=progress.stage in setOf(JobStage.QUEUED,JobStage.VALIDATING,JobStage.RUNNING,JobStage.FINALIZING)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TransferRow(job:JobSnapshot,language:AppLanguage,onOpen:(()->Unit)?,onPause:()->Unit,onCancel:()->Unit,onRetry:()->Unit,selection:Boolean?=null,onSelect:(()->Unit)?=null) {
    val haptics=rememberAppHaptics()
    val tool=CapabilityRegistry.tools.find {it.id==job.request.toolId}
    val name=tool?.let {if(language.isRtl) it.titleAr else it.titleEn} ?: job.request.toolId
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        FileMenuRow(name,stageLabel(job.progress.stage),onOpen,Icons.Rounded.Download,selection=selection,onSelect=onSelect,showOverflow=onOpen!=null || job.isActive() || job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.INTERRUPTED,JobStage.PAUSED,JobStage.WAITING_FOR_INPUT)) {close->
            if(onOpen!=null) DropdownMenuItem(text={Text(stringResource(R.string.action_details))},onClick={close();onOpen()})
            if(job.isActive() || job.progress.stage==JobStage.PAUSED) {
                if(job.isActive() && job.progress.stage!=JobStage.FINALIZING && job.request.toolId.startsWith("dl_")) DropdownMenuItem(text={Text(stringResource(R.string.action_pause))},onClick={close();onPause()})
                DropdownMenuItem(text={Text(stringResource(R.string.action_cancel))},onClick={close();onCancel()})
            }
            if(job.progress.stage==JobStage.PAUSED) DropdownMenuItem(text={Text(stringResource(R.string.action_resume))},onClick={close();onRetry()})
            else if(job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.INTERRUPTED,JobStage.WAITING_FOR_INPUT)) DropdownMenuItem(text={Text(stringResource(R.string.action_retry_failed))},onClick={close();onRetry()})
            if(job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.WAITING_FOR_INPUT))
                DropdownMenuItem(text={Text(stringResource(R.string.job_stop_retrying))},onClick={close();onCancel()})
        }
        if(job.isActive()) {
            val total=job.progress.totalUnits
            if(total==null || total<=0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                val fraction=(job.progress.completedUnits.toDouble()/total).toFloat().coerceIn(0f,1f)
                val progress by animateFloatAsState(fraction,
                    animationSpec=if(LocalReducedMotion.current) snap() else spring(dampingRatio=.9f,stiffness=260f),label="job-progress")
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(progress={progress},modifier=Modifier.weight(1f))
                    Text(stringResource(R.string.premium_progress_percent,(fraction*100).roundToInt()),
                        style=QuietTheme.typography.metadataStrong,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Visible one-tap controls for active, paused, failed, or interrupted operations.
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(job.request.toolId.startsWith("dl_") && job.isActive() && job.progress.stage!=JobStage.FINALIZING) {
                TextButton(onClick={haptics.actionClick();onPause()},modifier=Modifier.sizeIn(minHeight=48.dp)) {
                    Icon(Icons.Rounded.Pause,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pause))
                }
            } else if(job.progress.stage==JobStage.PAUSED) {
                TextButton(onClick={haptics.actionClick();onRetry()},modifier=Modifier.sizeIn(minHeight=48.dp)) {
                    Icon(Icons.Rounded.PlayArrow,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_resume))
                }
            } else if(job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.INTERRUPTED,JobStage.WAITING_FOR_INPUT)) {
                TextButton(onClick={haptics.actionClick();onRetry()},modifier=Modifier.sizeIn(minHeight=48.dp)) {
                    Icon(Icons.Rounded.Refresh,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_retry_failed))
                }
            }
            if((job.isActive() && job.progress.stage!=JobStage.FINALIZING) ||
                job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.WAITING_FOR_INPUT)) {
                TextButton(onClick={haptics.secondaryAction();onCancel()},modifier=Modifier.sizeIn(minHeight=48.dp)) {
                    Icon(Icons.Rounded.Close,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(if(job.progress.stage in setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.WAITING_FOR_INPUT))
                        R.string.job_stop_retrying else R.string.action_cancel))
                }
            }
        }
        if(job.progress.stage==JobStage.COMPLETED && job.request.toolId.startsWith("dl_")) {
            var ready by remember(job.request.id) { mutableStateOf(false) }
            LaunchedEffect(job.request.id) { ready=true }
            val confirmScale by animateFloatAsState(if(ready) 1f else .80f,
                animationSpec=if(LocalReducedMotion.current) snap() else spring(dampingRatio=.62f,stiffness=360f),
                label="download-success-pulse")
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle,contentDescription=null,
                    tint=MaterialTheme.colorScheme.tertiary,
                    modifier=Modifier.size(20.dp).graphicsLayer { scaleX=confirmScale;scaleY=confirmScale })
                Text(stringResource(R.string.brand_completed),style=QuietTheme.typography.navigation,
                    color=MaterialTheme.colorScheme.tertiary)
            }
        }
        if(job.outcomes.isNotEmpty()) {
            val published=job.outcomes.count { it.outputId!=null }
            val notPublished=job.outcomes.count { it.status!=ItemStatus.CREATED && it.status!=ItemStatus.SKIPPED }
            Text(stringResource(R.string.job_outcomes_summary,published,notPublished),style=QuietTheme.typography.metadata)
            // Say why an item produced no file instead of leaving the user guessing.
            job.outcomes.firstOrNull { it.status!=ItemStatus.CREATED && it.errorCode!=null }?.errorCode?.let { code ->
                Text(stringResource(userMessageResource(code)),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.error)
            }
        }
    }
}
