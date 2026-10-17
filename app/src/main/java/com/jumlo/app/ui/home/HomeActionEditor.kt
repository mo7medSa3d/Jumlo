package com.jumlo.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.UtilityTool

/** Inline editing stays inside the hinge-safe route and supports keyboard/TalkBack ordering. */
@Composable fun HomeActionEditor(eligible:List<UtilityTool>,selected:List<String>,language:AppLanguage,onChange:(List<String>)->Unit,onSave:()->Unit,onCancel:()->Unit) {
    val byId=eligible.associateBy {it.id}
    fun label(tool:UtilityTool)=if(language.isRtl) tool.titleAr else tool.titleEn
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(stringResource(R.string.ui_customize_actions),style=MaterialTheme.typography.titleLarge);Text(stringResource(R.string.ui_actions_local),style=MaterialTheme.typography.bodyMedium)}
            itemsIndexed(selected,key={_,id->id}) {index,id->byId[id]?.let {tool->
                Column {
                    Text(label(tool),style=MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        IconButton(enabled=index>0,onClick={onChange(selected.toMutableList().apply {add(index-1,removeAt(index))})}) {Icon(Icons.Rounded.ArrowUpward,stringResource(R.string.ui_action_up,label(tool)))}
                        IconButton(enabled=index<selected.lastIndex,onClick={onChange(selected.toMutableList().apply {add(index+1,removeAt(index))})}) {Icon(Icons.Rounded.ArrowDownward,stringResource(R.string.ui_action_down,label(tool)))}
                        TextButton(onClick={onChange(selected-id)}) {Text(stringResource(R.string.action_remove))}
                    }
                }
            }}
            item {TextButton(onClick={onChange(HomeActionPolicy.resolve(emptyList(),eligible.map {it.id}))}) {Text(stringResource(R.string.ui_actions_reset))}}
            item {Text(stringResource(R.string.ui_actions_available),style=MaterialTheme.typography.titleSmall)}
            items(eligible.filter {it.id !in selected},key={it.id}) {tool->
                ListItem(headlineContent={Text(label(tool))},trailingContent={IconButton(enabled=selected.size<4,onClick={onChange(selected+tool.id)}) {Icon(Icons.Rounded.Add,stringResource(R.string.ui_action_add,label(tool)))}})
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton(onClick=onCancel,modifier=Modifier.weight(1f)) {Text(stringResource(R.string.action_cancel))}
            Button(onClick=onSave,enabled=selected.isNotEmpty(),modifier=Modifier.weight(1f)) {Text(stringResource(R.string.ui_actions_save))}
        }
    }
}
