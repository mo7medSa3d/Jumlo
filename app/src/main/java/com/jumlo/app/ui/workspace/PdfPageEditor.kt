package com.jumlo.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.*
import com.jumlo.app.ui.util.rememberAppHaptics
import com.jumlo.app.ui.util.rememberZoomPanState
import com.jumlo.app.ui.util.zoomPan
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.PageSelection
import com.jumlo.app.core.PdfPageEdit
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.theme.QuietTheme

/** Bounded lazy thumbnails; page actions write into the existing durable workspace draft. */
@Composable
internal fun PdfPageEditor(
    vm: AppViewModel,
    sourceId: String,
    draft: WorkspaceDraft,
    edit: ((WorkspaceDraft) -> WorkspaceDraft) -> Unit,
    onPrint: (List<Pair<Int,Int>>) -> Unit = {},
    printEnabled: Boolean = false
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
    val organize = draft.toolId == "pdf_04"
    val initialOrder = remember(total) { (1..total).toList() }
    val seeds = remember(sourceId,total) {
        draft.pageEdits.takeIf { it.isNotEmpty() && it.all { p -> p.sourcePage in 1..total && p.rotationDegrees in setOf(0,90,180,270) && p.instanceId.isNotBlank() } && it.map { p -> p.instanceId }.toSet().size==it.size }
            ?: initialPageEdits(total,draft.text1,draft.text2,draft.num1.toIntOrNull() ?: 90)
    }
    val retainedModel:PdfPageEditorSession=androidx.lifecycle.viewmodel.compose.viewModel(key="pdf-page-editor-session")
    val session=remember(retainedModel,sourceId,draft.toolId,total) {
        retainedModel.forDocument("${draft.toolId}:$sourceId",seeds)
    }
    var history by session.history
    var livePages by remember(sourceId) { mutableStateOf<List<com.jumlo.app.data.processing.PdfPageEdit>?>(null) }
    val pages = if(organize) livePages ?: history.current else initialOrder.map { com.jumlo.app.data.processing.PdfPageEdit("page-$it",it) }
    val order=pages.map { it.sourcePage }
    var selectedIds by session.selection
    var focusedId by rememberSaveable(sourceId) { mutableStateOf(pages.first().instanceId) }
    val focusedPage=pages.firstOrNull {it.instanceId==focusedId} ?: pages.first()

    LaunchedEffect(draft.pageEdits,draft.text1,draft.text2,draft.num1) {
        if(organize) {
            val fromDraft=draft.pageEdits.takeIf { it.isNotEmpty() && it.all { p -> p.sourcePage in 1..total && p.rotationDegrees in setOf(0,90,180,270) && p.instanceId.isNotBlank() } && it.map { p -> p.instanceId }.toSet().size==it.size }
                ?: initialPageEdits(total,draft.text1,draft.text2,draft.num1.toIntOrNull() ?: 90)
            if(fromDraft!=history.current) { history=history.commit(fromDraft);selectedIds=emptyList() }
        }
    }
    val selected: Set<Int> = if (organize) pages.filter { it.instanceId in selectedIds }.map { it.sourcePage }.toSet()
        else if (draft.toolId == "pdf_05" && draft.flag1) emptySet()
        else if (boundaryMode) runCatching { if(draft.pages.isBlank()) emptySet() else PageSelection.parse(draft.pages,total).toSet() }.getOrDefault(emptySet())
        else runCatching { if(draft.pages.isBlank() || draft.pages=="all") initialOrder.toSet() else PageSelection.parse(draft.pages,total).toSet() }.getOrDefault(emptySet())
    val haptics = rememberAppHaptics()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var edgeScroll by remember { mutableFloatStateOf(0f) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val stepPx=with(LocalDensity.current) {126.dp.toPx()}
    LaunchedEffect(edgeScroll) {
        var accumulated=0f
        while(edgeScroll!=0f) {
            accumulated+=listState.scrollBy(edgeScroll)
            if(kotlin.math.abs(accumulated)>=stepPx) {
                val live=livePages
                val from=live?.indexOfFirst {it.instanceId==draggedId} ?: -1
                if(live!=null && from>=0) {
                    val delta=if(accumulated>0) 1 else -1
                    val target=(from+delta).coerceIn(0,live.lastIndex)
                    if(target!=from) {livePages=live.toMutableList().apply {add(target,removeAt(from))};haptics.sliderTick()}
                }
                accumulated=0f
            }
            kotlinx.coroutines.delay(16)
        }
    }
    fun persist(next:PageEditHistory) {
        if(next==history) return
        history=next
        selectedIds=selectedIds.filter { id -> next.current.any { it.instanceId==id } }
        edit { it.copy(pageEdits=next.current,text1=next.current.map { p -> p.sourcePage }.distinct().joinToString(",")) }
    }
    fun updateSelection(next:Set<Int>) {
        if(organize) selectedIds=pages.filter { it.sourcePage in next }.map { it.instanceId }
        else if(draft.toolId=="pdf_05") edit { it.copy(flag1=false,flag2=boundaryMode,pages=next.sorted().joinToString(",").ifBlank { if(boundaryMode) "" else "all" }) }
        else if(next.isNotEmpty()) edit { it.copy(pages=next.sorted().joinToString(",")) }
    }
    val ordered=PdfPageEdit(order,selected)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.jumlo_page_editor_title), style=QuietTheme.typography.sectionTitle)
                Text(stringResource(when {
                    boundaryMode -> R.string.jumlo_split_marker_hint
                    draft.toolId == "pdf_04" -> R.string.jumlo_page_editor_hint
                    else -> R.string.jumlo_select_page_hint
                }), style=QuietTheme.typography.metadata)
            }
            Text(stringResource(R.string.jumlo_page_count,total), style=QuietTheme.typography.metadata)
        }
        if(organize && selectedIds.isNotEmpty()) Surface(
            color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
                Text(stringResource(R.string.neon_selection_count,selectedIds.size),
                    style=QuietTheme.typography.metadataStrong,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick={persist(history.delete(selectedIds.toSet()));selectedIds=emptyList()},enabled=selectedIds.size<pages.size) {
                    Icon(Icons.Rounded.DeleteOutline,null,Modifier.size(18.dp));Text(stringResource(R.string.action_remove)) } }
                item { TextButton(onClick={persist(history.rotate(selectedIds.toSet()))}) {
                    Icon(Icons.AutoMirrored.Rounded.RotateRight,null,Modifier.size(18.dp));Text(stringResource(R.string.jumlo_rotate_selection)) } }
                item { TextButton(onClick={persist(history.duplicate(selectedIds.toSet()))}) { Text(stringResource(R.string.editor_duplicate)) } }
                if(printEnabled) item { TextButton(onClick={onPrint(pages.filter { it.instanceId in selectedIds }.map { it.sourcePage to it.rotationDegrees })}) { Text(stringResource(R.string.editor_print_selected)) } }
            }
            }
        }
        if(organize) {
            PdfFocusedPagePreview(vm,sourceId,focusedPage.sourcePage,focusedPage.rotationDegrees,focusedPage.instanceId)
            val focusIndex=pages.indexOf(focusedPage)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                IconButton(enabled=focusIndex>0,onClick={focusedId=pages[focusIndex-1].instanceId}) {
                    Icon(if(rtl) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,stringResource(R.string.action_previous))
                }
                Text(stringResource(R.string.ui_page_indicator,focusIndex+1,pages.size),style=QuietTheme.typography.metadataStrong)
                IconButton(enabled=focusIndex<pages.lastIndex,onClick={focusedId=pages[focusIndex+1].instanceId}) {
                    Icon(if(rtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,stringResource(R.string.action_next))
                }
            }
        }
        LazyRow(modifier=Modifier.onGloballyPositioned {viewport=it.boundsInRoot()},state=listState,horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=4.dp)) {
            items(pages,key={ it.instanceId }) { entry ->
                val index=pages.indexOf(entry)
                PdfPageTile(vm,sourceId,entry.sourcePage,if(organize) entry.instanceId in selectedIds else entry.sourcePage in selected,organize,
                    rotation=entry.rotationDegrees,instanceId=entry.instanceId,position=index+1,totalPages=pages.size,canMoveEarlier=index>0,canMoveLater=index<pages.lastIndex,
                    onSelect={
                        focusedId=entry.instanceId
                        if(organize) selectedIds=if(entry.instanceId in selectedIds) selectedIds-entry.instanceId else selectedIds+entry.instanceId
                        else updateSelection(ordered.toggle(entry.sourcePage).selected)
                        haptics.selectionToggle()
                    },
                    selectionEnabled = !draft.flag1 && (!boundaryMode || entry.sourcePage < total),
                    onMove={delta -> persist(history.move(entry.instanceId,index+delta));haptics.sliderTick() },
                    onDragStart={ focusedId=entry.instanceId;draggedId=entry.instanceId;livePages=history.current;if(entry.instanceId !in selectedIds) selectedIds=selectedIds+entry.instanceId;haptics.longPress() },
                    onDragStep={ delta ->
                        val live=livePages ?: history.current
                        val from=live.indexOfFirst { it.instanceId==entry.instanceId }
                        val target=(from+delta).coerceIn(0,live.lastIndex)
                        if(from!=target) { livePages=live.toMutableList().apply { add(target,removeAt(from)) };haptics.sliderTick() }

                    },
                    onDragPosition={ point ->
                        edgeScroll=when { point.x<viewport.left+48f -> if(rtl) 18f else -18f;point.x>viewport.right-48f -> if(rtl) -18f else 18f;else -> 0f }
                    },
                    onDragEnd={ commit -> val live=livePages;livePages=null;draggedId=null;edgeScroll=0f;if(commit && live!=null) persist(history.commit(live));haptics.selectionToggle() })
            }
        }
        if (organize) {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick={persist(history.undo());selectedIds=emptyList()},enabled=history.past.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.Undo,null,Modifier.size(18.dp));Text(stringResource(R.string.jumlo_undo)) } }
                item { TextButton(onClick={persist(history.redo());selectedIds=emptyList()},enabled=history.future.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.Redo,null,Modifier.size(18.dp));Text(stringResource(R.string.jumlo_redo)) } }
                item { TextButton(onClick={selectedIds=pages.map { it.instanceId }}) { Text(stringResource(R.string.jumlo_select_all)) } }
                item { TextButton(onClick={selectedIds=emptyList()},enabled=selectedIds.isNotEmpty()) { Text(stringResource(R.string.editor_clear_selection)) } }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfPageTile(
    vm:AppViewModel,sourceId:String,page:Int,selected:Boolean,reorder:Boolean,
    onSelect:()->Unit,onMove:(Int)->Unit,selectionEnabled:Boolean=true,
    rotation:Int=0,instanceId:String="page-$page",onDragStart:()->Unit={},onDragStep:(Int)->Unit={},onDragEnd:(Boolean)->Unit={},onDragPosition:(Offset)->Unit={},
    position:Int=page,totalPages:Int=page,canMoveEarlier:Boolean=true,canMoveLater:Boolean=true
) {
    val frameState by produceState<Result<PreviewFrame>?>(null,sourceId,page) {
        value = runCatching { vm.thumbnail(sourceId,page) }
    }
    val frame = frameState?.getOrNull()
    // Frames remain owned by Compose until no longer referenced; let GC release their bitmap.
    val latestStart by rememberUpdatedState(onDragStart)
    val latestStep by rememberUpdatedState(onDragStep)
    val latestEnd by rememberUpdatedState(onDragEnd)
    val latestPosition by rememberUpdatedState(onDragPosition)
    var tileCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val pageDescription=stringResource(R.string.editor_page_instance,page,position,totalPages,rotation)
    val earlierDescription=stringResource(R.string.jumlo_move_earlier)
    val laterDescription=stringResource(R.string.jumlo_move_later)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var displacement by remember(page) { mutableFloatStateOf(0f) }
    Column(Modifier.width(116.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Column(
            Modifier
                .onGloballyPositioned {tileCoordinates=it}
                .graphicsLayer { translationX=displacement }
                .then(if (reorder) Modifier.pointerInput(instanceId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart={latestStart()},
                        onDragEnd={latestEnd(true);displacement=0f},
                        onDragCancel={latestEnd(false);displacement=0f},
                        onDrag={change,drag ->
                            change.consume();tileCoordinates?.let {latestPosition(it.localToRoot(change.position))};displacement+=drag.x
                            val step=(displacement/126.dp.toPx()).toInt()
                            if(step!=0) { latestStep(if(rtl) -step else step);displacement-=step*126.dp.toPx() }
                        }
                    )
                } else Modifier)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                .semantics {
                    this.selected=selected;contentDescription=pageDescription
                    role=androidx.compose.ui.semantics.Role.Checkbox
                    toggleableState=if(selected) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
                    if(reorder) customActions=buildList {
                        if(canMoveEarlier) add(CustomAccessibilityAction(earlierDescription) {onMove(-1);true})
                        if(canMoveLater) add(CustomAccessibilityAction(laterDescription) {onMove(1);true})
                    }
                }
                .combinedClickable(enabled=selectionEnabled,onClick=onSelect,onLongClick=if(reorder) null else onSelect)
                .padding(8.dp),
            horizontalAlignment=Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(146.dp),contentAlignment=Alignment.Center) {
                if(frame != null && frame?.bitmap?.isRecycled==false)
                    Image(frame!!.bitmap.asImageBitmap(),contentDescription=stringResource(R.string.jumlo_page_label,page),
                        modifier=Modifier.fillMaxSize().graphicsLayer {
                            rotationZ=rotation.toFloat()
                            if(rotation%180!=0) {val fit=minOf(size.width/size.height,size.height/size.width);scaleX=fit;scaleY=fit}
                        })
                else if (frameState == null) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                else Text(stringResource(R.string.msg_preview_unavailable),
                    color=MaterialTheme.colorScheme.error, style=QuietTheme.typography.metadata)
            }
            Text(stringResource(R.string.jumlo_page_label,page),style=QuietTheme.typography.metadata)
        }
        if(reorder) Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            IconButton(onClick={onMove(-1)},enabled=canMoveEarlier,modifier=Modifier.size(48.dp)) {
                Icon(if(rtl) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,stringResource(R.string.jumlo_move_earlier))
            }
            IconButton(onClick={onMove(1)},enabled=canMoveLater,modifier=Modifier.size(48.dp)) {
                Icon(if(rtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,stringResource(R.string.jumlo_move_later))
            }
        }
    }
}


/** A single bounded full-size frame; focus follows page-instance identity through reorder. */
@Composable
private fun PdfFocusedPagePreview(vm:AppViewModel,sourceId:String,page:Int,rotation:Int,instanceId:String) {
    var retry by remember(sourceId,page) { mutableIntStateOf(0) }
    val result by produceState<Result<PreviewFrame>?>(null,sourceId,page,retry) {
        value=null
        value=try { Result.success(vm.preview(sourceId,page)) }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { Result.failure(error) }
    }
    val zoomState = key(sourceId, instanceId, rotation) { rememberZoomPanState(1f, 6f, allowRotation = true) }
    val resetLabel=stringResource(R.string.ui_preview_reset_zoom)
    val zoomLabel=stringResource(R.string.print_preview_zoom,(zoomState.scale*100).toInt())
    val pageLabel=stringResource(R.string.jumlo_page_label,page)
    val frame=result?.getOrNull()
    BoxWithConstraints(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .semantics {
            contentDescription=pageLabel;stateDescription=zoomLabel
            customActions=listOf(CustomAccessibilityAction(resetLabel) {zoomState.reset();true})
        }
        .zoomPan(zoomState,key="$sourceId:$instanceId:$rotation"),contentAlignment=Alignment.Center) {
        when {
            result==null -> CircularProgressIndicator(Modifier.size(28.dp))
            frame==null || frame.bitmap.isRecycled -> Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(stringResource(R.string.msg_preview_unavailable),color=MaterialTheme.colorScheme.error)
                TextButton(onClick={retry++}) {Text(stringResource(R.string.print_preview_retry))}
            }
            else -> {
                val turned=rotation%180!=0
                val sourceAspect=frame.bitmap.width.toFloat()/frame.bitmap.height.coerceAtLeast(1)
                val aspect=if(turned) 1f/sourceAspect else sourceAspect
                val displayWidth=minOf(maxWidth,maxHeight*aspect)
                val displayHeight=displayWidth/aspect
                val sourceWidth=if(turned) displayHeight else displayWidth
                val sourceHeight=if(turned) displayWidth else displayHeight
                Image(frame.bitmap.asImageBitmap(),contentDescription=null,contentScale=ContentScale.FillBounds,
                    modifier=Modifier.requiredSize(sourceWidth,sourceHeight).graphicsLayer {
                        rotationZ=rotation.toFloat()+zoomState.rotation;scaleX=zoomState.scale;scaleY=zoomState.scale
                        translationX=zoomState.offset.x;translationY=zoomState.offset.y
                    })
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(zoomLabel,style=QuietTheme.typography.metadata)
        TextButton(onClick={zoomState.reset()},enabled=zoomState.scale>1.01f || zoomState.offset!=Offset.Zero) {Text(resetLabel)}
    }
}
