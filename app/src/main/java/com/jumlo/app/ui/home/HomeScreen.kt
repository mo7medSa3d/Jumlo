package com.jumlo.app.ui.home

import com.jumlo.app.ui.theme.pressFeedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.jumlo.app.ui.theme.StaggeredReveal
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*
import com.jumlo.app.data.jobs.CapabilityRegistry

@Composable fun HomeScreen(vm:AppViewModel,onTool:(UtilityTool)->Unit,onTools:()->Unit,onJob:(String)->Unit,onOutput:(OutputRecord)->Unit,onDraft:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val outputs by vm.outputs.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val eligible=remember {CapabilityRegistry.tools.filter {CapabilityRegistry.visible(it.id) && CapabilityRegistry.canProcess(it.id)}}
    val selected=remember(settings.quickActionIds,eligible) {HomeActionPolicy.resolve(settings.quickActionIds,eligible.map {it.id})}
    val frequent=remember(selected,eligible) {val indexed=eligible.associateBy {it.id};selected.mapNotNull(indexed::get)}
    var editing by rememberSaveable {mutableStateOf(false)}
    var pending by rememberSaveable {mutableStateOf(ArrayList(selected))}
    BackHandler(enabled=editing) {editing=false}
    if(editing) {
        HomeActionEditor(eligible,pending,settings.language,{pending=ArrayList(it)},{vm.setQuickActionIds(pending);editing=false},{editing=false})
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns=if(maxWidth>=840.dp) 4 else if(maxWidth>=320.dp) 2 else 1
        LazyColumn(Modifier.fillMaxSize().widthIn(max=960.dp).align(Alignment.TopCenter),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item("hero") {Surface(onClick=onTools,modifier=Modifier.pressFeedback().fillMaxWidth(),color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.redesign_home_hero),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.redesign_home_subtitle),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.redesign_explore_tools),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.ArrowForward,null,tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }}
            item {Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.redesign_featured_tools),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                TextButton(onClick={pending=ArrayList(selected);editing=true}) {Text(stringResource(R.string.ui_customize_actions))}
            }}
            itemsIndexed(frequent.chunked(columns),key={_,row->row.joinToString {t->t.id}}) {rowIndex,row->
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach {tool->
                        StaggeredReveal(rowIndex,Modifier.weight(1f)) { Surface(onClick={onTool(tool)},modifier=Modifier.pressFeedback(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.heightIn(min=132.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Icon(resolveStitchIcon(tool.iconName),null,tint=MaterialTheme.colorScheme.primary)
                                Text(when(tool.id) {"pdf_02"->stringResource(R.string.ui_images_pdf);"pdf_03"->stringResource(R.string.ui_merge_pdf);"img_02"->stringResource(R.string.ui_resize_image);"dl_media"->stringResource(R.string.ui_new_download);else->if(settings.language.isRtl) tool.titleAr else tool.titleEn},style=MaterialTheme.typography.titleMedium)
                            }
                        } }
                    }
                    repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                }
            }
            if(form.sourceIds.isNotEmpty()) item {ListItem(headlineContent={Text(stringResource(R.string.ui_workspace_draft))},supportingContent={Text(stringResource(R.string.ui_input_count,form.sourceIds.size))},trailingContent={TextButton(onClick=onDraft) {Text(stringResource(R.string.ui_resume))}})}
            val active=jobs.filter {it.isActive()}.take(2)
            if(active.isNotEmpty()) item {QuietSectionTitle(stringResource(R.string.ui_active_tasks))}
            items(active,key={"job-${it.request.id}"}) {job->TransferRow(job,settings.language,{onJob(job.request.id)},{vm.pause(job.request.id)},{vm.cancel(job.request.id)},{vm.retry(job)})}
            item {QuietSectionTitle(stringResource(R.string.recent_results_title))}
            if(outputs.isEmpty()) item {Text(stringResource(R.string.ui_recent_empty),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(outputs.isNotEmpty()) item("recent-carousel") {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(end=12.dp)) {
                    items(outputs.take(7),key={it.id}) {output ->
                        Surface(onClick={onOutput(output)},modifier=Modifier.pressFeedback().width(172.dp).heightIn(min=112.dp),
                            color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(22.dp)) {
                            Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Icon(when {output.mimeType.startsWith("image/")->Icons.Rounded.Image;output.mimeType=="application/pdf"->Icons.Rounded.PictureAsPdf;output.mimeType.startsWith("video/")->Icons.Rounded.Movie;else->Icons.Rounded.InsertDriveFile},null,tint=MaterialTheme.colorScheme.primary)
                                Text(output.displayName,style=MaterialTheme.typography.labelMedium,maxLines=1)
                                Text(formatBytes(output.sizeBytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
