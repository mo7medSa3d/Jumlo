package com.jumlo.app.ui.tools

import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.theme.StaggeredReveal
import com.jumlo.app.ui.theme.QuietTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jumlo.app.R
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.*
import com.jumlo.app.model.*
import com.jumlo.app.data.jobs.CapabilityRegistry

@Composable fun ToolsScreen(vm:AppViewModel,onTool:(UtilityTool)->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("all")}
    val eligible=remember {CapabilityRegistry.tools.filter {CapabilityRegistry.visible(it.id) && CapabilityRegistry.enabled(it.id) && it.id !in setOf("dl_settings","file_02","file_trash")}}
    val visible=remember(search,category,settings.language) {eligible.filter {(category=="all" || it.pillar.name==category) && (search.isBlank() || it.titleAr.contains(search,true) || it.titleEn.contains(search,true))}}
    val byId=remember(eligible) {eligible.associateBy {it.id}}
    val recent=settings.recentToolIds.mapNotNull(byId::get).filter {it in visible}.take(4)
    val frequent=settings.mostUsedToolIds.mapNotNull(byId::get).filter {it in visible}.take(4)
    LazyColumn(
        modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=QuietTheme.spacing.listBottomBuffer),
        verticalArrangement=Arrangement.spacedBy(16.dp)
    ) {
        item("hero") {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.tools_catalog_title),style=QuietTheme.typography.screenTitle)
            Text(stringResource(R.string.redesign_tools_subtitle),style=QuietTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        item("search") {QuietSearchBar(search,stringResource(R.string.home_search_placeholder),{search=it},{search=""})}
        item("filters") {LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item {FilterChip(category=="all",{category="all"},label={Text(stringResource(R.string.ui_all),style=QuietTheme.typography.metadataStrong)})}
            items(PillarCategory.entries.filter {pillar->eligible.any {it.pillar==pillar}},key={it.name}) {pillar->FilterChip(category==pillar.name,{category=pillar.name},label={Text(if(settings.language.isRtl) pillar.labelAr else pillar.labelEn,style=QuietTheme.typography.metadataStrong)})}
        }}
        if(recent.isNotEmpty() && search.isBlank() && category=="all") {
            item("recentHeading") {QuietSectionTitle(stringResource(R.string.redesign_recent_tools))}
            item("recent") {LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                items(recent,key={it.id}) {t->AssistChip(onClick={onTool(t)},label={Text(if(settings.language.isRtl) t.titleAr else t.titleEn,style=QuietTheme.typography.metadataStrong)},leadingIcon={Icon(resolveStitchIcon(t.iconName),null,Modifier.size(18.dp))})}
            }}
        }
        if(frequent.isNotEmpty() && search.isBlank() && category=="all") {
            item("frequentHeading") {QuietSectionTitle(stringResource(R.string.redesign_most_used))}
            item("frequent") {Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                frequent.take(2).forEach {t->Box(Modifier.weight(1f)) {BentoTile(t,settings.language,onTool)}}
                if(frequent.size==1) Spacer(Modifier.weight(1f))
            }}
        }
        if(visible.isEmpty()) item("empty") {QuietStateCard(stringResource(R.string.tools_no_matching),stringResource(R.string.tools_no_matching_sub),stringResource(R.string.tools_reset_filters),{search="";category="all"})}
        PillarCategory.entries.forEach {pillar->
            val group=visible.filter {it.pillar==pillar}
            if(group.isNotEmpty()) {
                item("heading-${pillar.name}") {QuietSectionTitle(if(settings.language.isRtl) pillar.labelAr else pillar.labelEn)}
                itemsIndexed(group.chunked(2),key={_,row->"${pillar.name}-"+row.joinToString("-") {it.id}}) {rowIndex,row->
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                        row.forEach { t-> StaggeredReveal(rowIndex.coerceAtMost(10),Modifier.weight(1f)) {BentoTile(t,settings.language,onTool)} }
                        if(row.size==1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable private fun BentoTile(tool:UtilityTool,language:AppLanguage,onTool:(UtilityTool)->Unit) {
    val palette=MaterialTheme.colorScheme
    val (surface,tint)=when(tool.pillar) {
        PillarCategory.PDF -> palette.primaryContainer to palette.onPrimaryContainer
        PillarCategory.IMAGES -> palette.tertiaryContainer to palette.onTertiaryContainer
        PillarCategory.DOWNLOADS -> palette.secondaryContainer to palette.onSecondaryContainer
        PillarCategory.FILES -> palette.surfaceContainerHigh to palette.onSurface
        PillarCategory.VAULT -> palette.errorContainer to palette.onErrorContainer
    }
    Surface(
        onClick={onTool(tool)},
        shape=RoundedCornerShape(24.dp),
        color=surface,
        modifier=Modifier.pressFeedback().fillMaxWidth().height(148.dp)
    ) {
        Column(
            modifier=Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement=Arrangement.SpaceBetween
        ) {
            Icon(resolveStitchIcon(tool.iconName),null,tint=tint,modifier=Modifier.size(26.dp))
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(
                    text=if(language.isRtl) tool.titleAr else tool.titleEn,
                    color=tint,
                    style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold),
                    maxLines=2,
                    overflow=TextOverflow.Ellipsis
                )
                Text(
                    text=if(language.isRtl) tool.subtitleAr else tool.subtitleEn,
                    color=tint.copy(alpha=0.82f),
                    style=QuietTheme.typography.metadata,
                    maxLines=2,
                    overflow=TextOverflow.Ellipsis
                )
            }
        }
    }
}
