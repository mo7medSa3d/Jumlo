package com.jumlo.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.PageSelection
import com.jumlo.app.core.PdfPageEdit
import com.jumlo.app.core.PdfPageOrder
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.theme.QuietTheme
import kotlin.math.roundToInt

/** Bounded lazy thumbnails; page actions write into the existing durable workspace draft. */
@Composable
internal fun PdfPageEditor(
    vm: AppViewModel,
    sourceId: String,
    draft: WorkspaceDraft,
    edit: ((WorkspaceDraft) -> WorkspaceDraft) -> Unit
) {
    // -1 means loading, zero means failed. Never show an endless spinner for a corrupt PDF.
    val count by produceState(-1, sourceId) {
        value = -1
        value = runCatching {
            val first = vm.thumbnail(sourceId, 1)
            try { first.count } finally { first.bitmap.takeUnless { it.isRecycled }?.recycle() }
        }.getOrDefault(0)
    }
    if (count < 0) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    if (count == 0) {
        Text(stringResource(R.string.msg_preview_unavailable), color=MaterialTheme.colorScheme.error)
        return
    }
    val total = count
    val boundaryMode = draft.toolId == "pdf_05" && draft.flag2 && !draft.flag1
    val initialOrder = remember(total) { PdfPageEdit.start(total).order }
    val order = remember(draft.text1, total) {
        if (draft.toolId != "pdf_04" || draft.text1.isBlank()) initialOrder
        else runCatching { PdfPageOrder.parse(draft.text1,total) }.getOrDefault(initialOrder)
    }
    var selectedLocal by rememberSaveable(sourceId) { mutableStateOf("") }
    val selected: Set<Int> = if (draft.toolId == "pdf_04") {
        selectedLocal.split(',').mapNotNull(String::toIntOrNull).filter { it in order }.toSet()
    } else if (draft.toolId == "pdf_05" && draft.flag1) emptySet()
    else if (boundaryMode) runCatching {
        if (draft.pages.isBlank()) emptySet() else PageSelection.parse(draft.pages,total).toSet()
    }.getOrDefault(emptySet())
    else runCatching {
        if (draft.pages.isBlank() || draft.pages == "all") initialOrder.toSet()
        else PageSelection.parse(draft.pages, total).toSet()
    }.getOrDefault(emptySet())

    var undo by rememberSaveable(sourceId) { mutableStateOf(listOf<String>()) }
    var redo by rememberSaveable(sourceId) { mutableStateOf(listOf<String>()) }
    fun persistOrder(next: List<Int>) {
        if (next == order || next.isEmpty()) return
        undo = (undo + order.joinToString(",")).takeLast(30)
        redo = emptyList()
        edit { it.copy(text1=next.joinToString(",")) }
    }
    fun updateSelection(next: Set<Int>) {
        if (draft.toolId == "pdf_04") selectedLocal=next.sorted().joinToString(",")
        else if (draft.toolId == "pdf_05") {
            val markers = next.sorted().joinToString(",")
            edit { it.copy(flag1=false, flag2=boundaryMode, pages=markers.ifBlank { if(boundaryMode) "" else "all" }) }
        } else if (next.isNotEmpty()) edit { it.copy(pages=next.sorted().joinToString(",")) }
    }
    val ordered = PdfPageEdit(order, selected)

    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
            Column {
                Text(stringResource(R.string.jumlo_page_editor_title), style=QuietTheme.typography.sectionTitle)
                Text(stringResource(when {
                    boundaryMode -> R.string.jumlo_split_marker_hint
                    draft.toolId == "pdf_04" -> R.string.jumlo_page_editor_hint
                    else -> R.string.jumlo_select_page_hint
                }), style=QuietTheme.typography.metadata)
            }
            Text(stringResource(R.string.jumlo_page_count,total), style=QuietTheme.typography.metadata)
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=4.dp)) {
            items(order,key={ it }) { page ->
                PdfPageTile(vm,sourceId,page,page in selected, draft.toolId=="pdf_04",
                    onSelect={ updateSelection(ordered.toggle(page).selected) },
                    selectionEnabled = !draft.flag1 && (!boundaryMode || page < total),
                    onMove={delta ->
                        val position=order.indexOf(page)+1
                        val target=(position+delta).coerceIn(1,order.size)
                        if (target!=position) persistOrder(ordered.move(page,target).order)
                    })
            }
        }
        if (draft.toolId=="pdf_04") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={
                    undo.lastOrNull()?.let { previous ->
                        undo=undo.dropLast(1);redo=redo+order.joinToString(",")
                        edit { it.copy(text1=previous) }
                    }
                },enabled=undo.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.Undo,null,Modifier.size(18.dp));Text(stringResource(R.string.jumlo_undo))
                }
                TextButton(onClick={
                    redo.lastOrNull()?.let { next ->
                        redo=redo.dropLast(1);undo=undo+order.joinToString(",")
                        edit { it.copy(text1=next) }
                    }
                },enabled=redo.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.Redo,null,Modifier.size(18.dp));Text(stringResource(R.string.jumlo_redo))
                }
                TextButton(onClick={
                    persistOrder(ordered.deleteSelected().order)
                    selectedLocal=""
                },enabled=selected.isNotEmpty() && selected.size<order.size) {
                    Icon(Icons.Rounded.DeleteOutline,null,Modifier.size(18.dp));Text(stringResource(R.string.action_remove))
                }
            }
            if (selected.isNotEmpty()) {
                TextButton(onClick={ edit { it.copy(text2=selected.sorted().joinToString(","),num1="90") } }) {
                    Icon(Icons.AutoMirrored.Rounded.RotateRight,null,Modifier.size(18.dp))
                    Text(stringResource(R.string.jumlo_rotate_selection))
                }
            }
        } else if (draft.toolId == "pdf_05") {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item("boundary-mode") {
                    FilterChip(boundaryMode, onClick={
                        edit { it.copy(flag1=false,flag2=true,pages="") }
                    }, label={Text(stringResource(R.string.jumlo_split_markers))})
                }
                item("each-page") {
                    FilterChip(draft.flag1 && draft.num1.ifBlank { "1" } == "1", onClick={
                        edit { it.copy(flag1=true,flag2=false,num1="1",pages="all") }
                    }, label={Text(stringResource(R.string.jumlo_split_each))})
                }
            }
            if (boundaryMode) {
                if (selected.isNotEmpty()) Text(stringResource(R.string.jumlo_split_parts_preview, selected.size+1),
                    style=QuietTheme.typography.metadataStrong,color=MaterialTheme.colorScheme.primary)
                TextButton(onClick={updateSelection(emptySet())},enabled=selected.isNotEmpty()) {
                    Text(stringResource(R.string.jumlo_clear_markers))
                }
            } else if (!draft.flag1) {
                TextButton(onClick={updateSelection(initialOrder.toSet())}) {Text(stringResource(R.string.jumlo_select_all))}
            }
        } else {
            TextButton(onClick={updateSelection(initialOrder.toSet())}) {Text(stringResource(R.string.jumlo_select_all))}
        }
    }
}

@Composable
private fun PdfPageTile(
    vm:AppViewModel,sourceId:String,page:Int,selected:Boolean,reorder:Boolean,
    onSelect:()->Unit,onMove:(Int)->Unit,selectionEnabled:Boolean=true
) {
    val frameState by produceState<Result<PreviewFrame>?>(null,sourceId,page) {
        value = runCatching { vm.thumbnail(sourceId,page) }
    }
    val frame = frameState?.getOrNull()
    val currentMove by rememberUpdatedState(onMove)
    DisposableEffect(frame) {
        val rendered=frame
        onDispose { rendered?.bitmap?.takeUnless { it.isRecycled }?.recycle() }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var displacement by remember(page) { mutableFloatStateOf(0f) }
    Column(Modifier.width(116.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Column(
            Modifier
                .graphicsLayer { translationX=displacement }
                .then(if (reorder) Modifier.pointerInput(page) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd={
                            val step=(displacement / 126.dp.toPx()).roundToInt()
                            if(step!=0) currentMove(if(rtl) -step else step)
                            displacement=0f
                        },
                        onDragCancel={displacement=0f},
                        onDrag={change,drag -> change.consume(); displacement+=drag.x}
                    )
                } else Modifier)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(enabled=selectionEnabled,onClick=onSelect)
                .padding(8.dp),
            horizontalAlignment=Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(146.dp),contentAlignment=Alignment.Center) {
                if(frame != null && frame?.bitmap?.isRecycled==false)
                    Image(frame!!.bitmap.asImageBitmap(),contentDescription=stringResource(R.string.jumlo_page_label,page),
                        modifier=Modifier.fillMaxSize())
                else if (frameState == null) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                else Text(stringResource(R.string.msg_preview_unavailable),
                    color=MaterialTheme.colorScheme.error, style=QuietTheme.typography.metadata)
            }
            Text(stringResource(R.string.jumlo_page_label,page),style=QuietTheme.typography.metadata)
        }
        if(reorder) Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            IconButton(onClick={onMove(-1)},modifier=Modifier.size(48.dp)) {
                Icon(if(rtl) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,stringResource(R.string.jumlo_move_earlier))
            }
            IconButton(onClick={onMove(1)},modifier=Modifier.size(48.dp)) {
                Icon(if(rtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,stringResource(R.string.jumlo_move_later))
            }
        }
    }
}
