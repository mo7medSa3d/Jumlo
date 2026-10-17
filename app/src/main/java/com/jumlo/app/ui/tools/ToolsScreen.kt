package com.jumlo.app.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    val visible=remember(search,category,settings.language) {eligible.filter {(category=="all" || it.pillar.name==category) && (search.isBlank() || (if(settings.language.isRtl) it.titleAr else it.titleEn).contains(search,true))}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {QuietSearchBar(search,stringResource(R.string.home_search_placeholder),{search=it},{search=""})}
        item {LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item {FilterChip(category=="all",{category="all"},label={Text(stringResource(R.string.ui_all))})}
            items(PillarCategory.entries.filter {pillar->eligible.any {it.pillar==pillar}},key={it.name}) {pillar->FilterChip(category==pillar.name,{category=pillar.name},label={Text(if(settings.language.isRtl) pillar.labelAr else pillar.labelEn)})}
        }}
        item {Text(stringResource(R.string.ui_tools_count,visible.size),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(visible.isEmpty()) item {QuietStateCard(stringResource(R.string.tools_no_matching),stringResource(R.string.tools_no_matching_sub),stringResource(R.string.tools_reset_filters),{search="";category="all"})}
        PillarCategory.entries.forEach {pillar->
            val tools=visible.filter {it.pillar==pillar}
            if(tools.isNotEmpty()) {
                item(key="heading-${pillar.name}") {QuietSectionTitle(if(settings.language.isRtl) pillar.labelAr else pillar.labelEn)}
                items(tools,key={it.id},contentType={"tool"}) {ToolRow(it,settings.language,{onTool(it)})}
            }
        }
    }
}
