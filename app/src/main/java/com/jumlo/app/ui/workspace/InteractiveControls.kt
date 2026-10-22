package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlin.math.roundToInt

internal fun draftNumber(value:String,fallback:Int):Int =
    WorkspaceDraft.normalizeDigits(value).toIntOrNull() ?: fallback

/** Visible values, reset and accessibility semantics for every numeric slider. */
@Composable
internal fun InteractiveIntSlider(label:String,current:Int,minimum:Int,maximum:Int,reset:Int,onChange:(Int)->Unit) {
    val safe=current.coerceIn(minimum,maximum)
    val haptics=rememberAppHaptics()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,modifier=Modifier.weight(1f),style=QuietTheme.typography.metadataStrong)
        Text(safe.toString(),style=QuietTheme.typography.metadataStrong)
        TextButton(onClick={onChange(reset.coerceIn(minimum,maximum))}) { Text(stringResource(R.string.visual_edit_reset)) }
    }
    if(maximum>minimum) Slider(value=safe.toFloat(),valueRange=minimum.toFloat()..maximum.toFloat(),
        steps=if(maximum-minimum<=20) (maximum-minimum-1).coerceAtLeast(0) else 0,
        onValueChange={onChange(it.roundToInt())},onValueChangeFinished={haptics.selectionToggle()},
        modifier=Modifier.semantics {contentDescription=label;stateDescription=safe.toString()})
}

@Composable
internal fun InteractiveToggle(label:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
        .toggleable(value=checked,role=Role.Switch,onValueChange=onChange),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),style=QuietTheme.typography.bodyMedium)
        Switch(checked,onCheckedChange=null)
    }
}

/** For counts with a large range, adjacent buttons avoid an imprecise million-step slider. */
@Composable
internal fun InteractiveCounter(label:String,value:Int,minimum:Int,maximum:Int,onChange:(Int)->Unit) {
    val safe=value.coerceIn(minimum,maximum)
    val decrease=stringResource(R.string.interactive_decrease,label)
    val increase=stringResource(R.string.interactive_increase,label)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),style=QuietTheme.typography.metadataStrong)
        IconButton(onClick={onChange(safe-1)},enabled=safe>minimum) {Icon(Icons.Rounded.Remove,decrease)}
        Text(safe.toString(),style=QuietTheme.typography.metadataStrong,
            modifier=Modifier.semantics {contentDescription=label;stateDescription=safe.toString()})
        IconButton(onClick={onChange(safe+1)},enabled=safe<maximum) {Icon(Icons.Rounded.Add,increase)}
    }
}
