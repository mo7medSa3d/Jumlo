package com.jumlo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import com.jumlo.app.ui.icons.*
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

/** Start swipe opens the same accessible actions; end swipe moves a managed output to undoable trash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeFileActions(
    name:String,
    enabled:Boolean=true,
    onShare:()->Unit,
    onRename:()->Unit,
    onTrash:()->Unit,
    onPrint:(()->Unit)?=null,
    content:@Composable ()->Unit
) {
    var actions by remember { mutableStateOf(false) }
    val haptics=rememberAppHaptics()
    val currentTrash by rememberUpdatedState(onTrash)
    val currentEnabled by rememberUpdatedState(enabled)
    val state=rememberSwipeToDismissBoxState(confirmValueChange={ value ->
        if(currentEnabled && value!=SwipeToDismissBoxValue.Settled) {
            haptics.selectionToggle()
            if(value==SwipeToDismissBoxValue.EndToStart) currentTrash() else actions=true
        }
        false // Both actions keep the item stable until the repository confirms a mutation.
    })
    val moreLabel=stringResource(R.string.ui_more_actions)
    val trashLabel=stringResource(R.string.action_move_to_trash)
    SwipeToDismissBox(state,
        modifier=Modifier.clip(QuietTheme.shapes.card).semantics {
            if(enabled) customActions=listOf(
                CustomAccessibilityAction(moreLabel) { actions=true; true },
                CustomAccessibilityAction(trashLabel) { onTrash(); true })
        },
        enableDismissFromStartToEnd=enabled,enableDismissFromEndToStart=enabled,
        backgroundContent={
            val deleting=state.dismissDirection==SwipeToDismissBoxValue.EndToStart
            Box(Modifier.fillMaxSize().background(if(deleting) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer).padding(20.dp),
                contentAlignment=if(deleting) Alignment.CenterEnd else Alignment.CenterStart) {
                Icon(if(deleting) Icons.Rounded.DeleteOutline else Icons.Rounded.MoreHoriz,null)
            }
        }) { content() }
    if(actions) AlertDialog(onDismissRequest={actions=false},title={Text(name)},text={
        Column(Modifier.fillMaxWidth()) {
            TextButton(onClick={actions=false;onShare()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(R.string.action_share))
            }
            TextButton(onClick={actions=false;onRename()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(R.string.action_rename))
            }
            if(onPrint!=null) TextButton(onClick={actions=false;onPrint()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(R.string.upgrade_print))
            }
        }
    },confirmButton={TextButton(onClick={actions=false}) {Text(stringResource(R.string.action_cancel))}})
}
