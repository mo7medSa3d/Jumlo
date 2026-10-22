package com.jumlo.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietShimmerSkeleton
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.core.PdfAnnotationKind
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.CancellationException
import java.util.Locale
import kotlin.math.min

/**
 * Unified direct-manipulation editor. Uses actual Android-rendered PDF/image frames.
 * Coordinates are the validated normalized top-left fractions consumed by the existing
 * image crop, PDF crop, signature/overlay-image and annotation engines.
 *
 * This is strictly UI/gesture logic. The underlying PDF remains unchanged until
 * the user explicitly runs the processing job in WorkspaceScreen.
 */
private fun WorkspaceDraft.visualRect(): VisualRect {
    fun read(raw:String,fallback:Float):Float =
        raw.toFloatOrNull()?.takeIf { it.isFinite() } ?: fallback
    val fallback=when(toolId) {
        "pdf_signature" -> VisualRect.SIGNATURE_START
        "pdf_image" -> VisualRect(.18f,.22f,.82f,.78f)
        "pdf_annotate" -> VisualRect(.1f,.1f,.34f,.24f)
        else -> VisualRect.WHOLE
    }
    return VisualRect(
        read(cropLeft,fallback.left),read(cropTop,fallback.top),
        read(cropRight,fallback.right),read(cropBottom,fallback.bottom)
    ).valid()
}

private fun Float.serialized():String = String.format(Locale.US,"%.5f",this)

@Composable
internal fun VisualDocumentEditor(
    vm:AppViewModel,
    sourceId:String,
    draft:WorkspaceDraft,
    edit:((WorkspaceDraft)->WorkspaceDraft)->Unit,
    overlayId:String?=null
) {
    val tool=draft.toolId
    val overlayTool=tool in setOf("pdf_image","pdf_signature")
    val annotation=tool=="pdf_annotate"
    val cropTool=tool in setOf("img_03","pdf_crop")
    val sourceIsPdf=tool.startsWith("pdf_")
    val haptics=rememberAppHaptics()
    var pageCount by remember(sourceId) { mutableIntStateOf(1) }
    var currentPage by rememberSaveable(sourceId,tool) {
        mutableIntStateOf(if(sourceIsPdf) (draft.num1.toIntOrNull()?:1).coerceAtLeast(1) else 1)
    }
    LaunchedEffect(draft.num1) { if(tool in setOf("pdf_image","pdf_signature","pdf_annotate","pdf_redact")) currentPage=(draft.num1.toIntOrNull() ?: 1).coerceIn(1,pageCount) }
    var preview by remember(sourceId,currentPage) { mutableStateOf<PreviewFrame?>(null) }
    var failed by remember(sourceId,currentPage) { mutableStateOf(false) }
    LaunchedEffect(sourceId,currentPage,pageCount) {
        preview=null
        failed=false
        try {
            val frame=vm.preview(sourceId,if(sourceIsPdf) currentPage.coerceIn(1,pageCount) else 1)
            preview=frame
            pageCount=frame.count.coerceAtLeast(1)
        } catch(cancel:CancellationException) { throw cancel }
          catch(_:Exception) { failed=true }
    }
    val importedOverlay by produceState<PreviewFrame?>(null,overlayId) {
        value=null
        if(overlayId!=null) {
            try { value=vm.preview(overlayId,1) }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { value=null }
        }
    }
    var rect by remember(sourceId,tool) { mutableStateOf(draft.visualRect()) }
    val snapshotSaver = androidx.compose.runtime.saveable.Saver<List<VisualEditSnapshot>,ArrayList<String>>(
        save={ ArrayList(it.map { d -> kotlinx.serialization.json.Json.encodeToString(VisualEditSnapshot.serializer(),d) }) },
        restore={ it.map { raw -> kotlinx.serialization.json.Json.decodeFromString(VisualEditSnapshot.serializer(),raw) } })
    var undoHistory by rememberSaveable(sourceId,tool,stateSaver=snapshotSaver) { mutableStateOf(emptyList<VisualEditSnapshot>()) }
    var redoHistory by rememberSaveable(sourceId,tool,stateSaver=snapshotSaver) { mutableStateOf(emptyList<VisualEditSnapshot>()) }
    var lockedAspect by rememberSaveable(sourceId,tool) { mutableFloatStateOf(0f) }
    var zoom by rememberSaveable(sourceId,tool) { mutableFloatStateOf(1f) }
    var pan by remember(sourceId,tool) { mutableStateOf(Offset.Zero) }
    val transformState=rememberTransformableState { zoomChange,panChange,_ ->
        zoom=(zoom*zoomChange).coerceIn(1f,4f)
        pan=if(zoom==1f) Offset.Zero else pan+panChange
    }
    val currentDraft by rememberUpdatedState(draft)
    var beforeDrag by remember(sourceId,tool) { mutableStateOf<VisualRect?>(null) }
    var dragging by remember { mutableStateOf(false) }
    // A freehand stroke is captured as it is drawn, not placed in a rectangle.
    val inkKind = annotation && PdfAnnotationKind.from(currentDraft.text2).isFreehand
    var inkPoints by remember(sourceId,tool) { mutableStateOf<List<Offset>>(emptyList()) }
    // The guide line the rectangle is snapped to, drawn so the snap is visible.
    var snapGuide by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(draft.cropLeft,draft.cropTop,draft.cropRight,draft.cropBottom,
        draft.num2,draft.num3,sourceId,tool) {
        if(!dragging) {
            val fromDraft=draft.visualRect()
            if(fromDraft!=rect) rect=fromDraft
        }
    }
    val latestEdit by rememberUpdatedState(edit)
    val colors=MaterialTheme.colorScheme
    val density=LocalDensity.current
    val handlePx=with(density) { 34.dp.toPx() }

    fun snapshot(box:VisualRect=rect)=VisualEditSnapshot(box,currentDraft.turns,currentDraft.flipX,currentDraft.flipY,lockedAspect)
    fun recordSnapshot() {
        undoHistory=(undoHistory+snapshot()).takeLast(25)
        redoHistory=emptyList()
    }
    fun recordEdit(previous:VisualRect,updated:VisualRect) {
        if(previous!=updated) { undoHistory=(undoHistory+snapshot(previous)).takeLast(25);redoHistory=emptyList() }
    }
    fun restoreSnapshot(snapshot:VisualEditSnapshot) {
        rect=snapshot.rect;lockedAspect=snapshot.aspect
        latestEdit { it.copy(cropLeft=rect.left.serialized(),cropTop=rect.top.serialized(),cropRight=rect.right.serialized(),cropBottom=rect.bottom.serialized(),
            num2=if(annotation) rect.left.serialized() else it.num2,num3=if(annotation) rect.top.serialized() else it.num3,
            turns=snapshot.turns,flipX=snapshot.flipX,flipY=snapshot.flipY) }
    }
    fun saveRect(box:VisualRect) {
        val safe=box.valid()
        latestEdit { current ->
            current.copy(
                cropLeft=safe.left.serialized(),cropTop=safe.top.serialized(),
                cropRight=safe.right.serialized(),cropBottom=safe.bottom.serialized(),
                num2=if(annotation) safe.left.serialized() else current.num2,
                num3=if(annotation) safe.top.serialized() else current.num3
            )
        }
    }

    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.visual_edit_title),
                style=QuietTheme.typography.sectionTitle,modifier=Modifier.weight(1f))
            TextButton(onClick={
                val reset=when(tool) {
                    "pdf_signature" -> VisualRect.SIGNATURE_START
                                "pdf_image" -> VisualRect(.18f,.22f,.82f,.78f)
                    "pdf_annotate" -> VisualRect(.1f,.1f,.34f,.24f)
                    else -> VisualRect.WHOLE
                }
                if(rect!=reset || lockedAspect!=0f) recordSnapshot()
                lockedAspect=0f
                rect=reset
                saveRect(reset)
                haptics.selectionToggle()
            }) {
                Icon(Icons.Rounded.RestartAlt,null)
                Text(stringResource(R.string.visual_edit_reset))
            }
            IconButton(enabled=undoHistory.isNotEmpty(),onClick={
                if(undoHistory.isNotEmpty()) {
                    redoHistory=(redoHistory+snapshot()).takeLast(25)
                    val previous=undoHistory.last()
                    undoHistory=undoHistory.dropLast(1)
                    restoreSnapshot(previous)
                    haptics.selectionToggle()
                }
            }) { Icon(Icons.Rounded.Undo,stringResource(R.string.visual_edit_undo)) }
            IconButton(enabled=redoHistory.isNotEmpty(),onClick={
                if(redoHistory.isNotEmpty()) {
                    undoHistory=(undoHistory+snapshot()).takeLast(25)
                    val next=redoHistory.last()
                    redoHistory=redoHistory.dropLast(1)
                    restoreSnapshot(next)
                    haptics.selectionToggle()
                }
            }) { Icon(Icons.Rounded.Redo,stringResource(R.string.visual_edit_redo)) }
            // A visible history: every recorded step is listed, so undo is discoverable
            // and the user can jump straight back to an earlier state.
            var historyOpen by rememberSaveable(sourceId,tool) { mutableStateOf(false) }
            if(undoHistory.isNotEmpty()) {
                TextButton(onClick={historyOpen=!historyOpen}) {
                    Text(stringResource(R.string.visual_edit_history,undoHistory.size))
                }
            }
            if(historyOpen && undoHistory.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    undoHistory.asReversed().forEachIndexed { index,entry ->
                        val step=undoHistory.size-index
                        TextButton(onClick={
                            redoHistory=emptyList()
                            undoHistory=undoHistory.take(undoHistory.size-index)
                            restoreSnapshot(entry)
                            haptics.selectionToggle()
                        },modifier=Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.visual_edit_history_step,step),
                                style=QuietTheme.typography.metadata)
                        }
                    }
                }
            }
        }
        val frame=preview
        if(frame!=null && !frame.bitmap.isRecycled) {
            val sourceAspect=frame.bitmap.width.toFloat()/frame.bitmap.height.coerceAtLeast(1)
            val rotated=tool=="img_03" && draft.turns%2!=0
            val aspect=if(rotated) 1f/sourceAspect else sourceAspect
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                // Never distort a portrait document just to fit a fixed-height preview.
                val canvasWidth=minOf(maxWidth,450.dp,(440.dp*aspect).coerceAtLeast(75.dp))
                val canvasHeight=canvasWidth/aspect
                Box(Modifier.width(canvasWidth).height(canvasHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceContainerLowest)
                    .pointerInput(sourceId,currentPage) { detectTapGestures(onDoubleTap={ zoom=1f;pan=Offset.Zero }) }
                    .transformable(transformState,canPan={ zoom>1f }),contentAlignment=Alignment.Center) {
                    val sourceWidth=if(rotated) canvasHeight else canvasWidth
                    val sourceHeight=if(rotated) canvasWidth else canvasHeight
                    Box(Modifier.requiredSize(sourceWidth,sourceHeight).graphicsLayer {
                        rotationZ=if(tool=="img_03") (draft.turns%4)*90f else 0f
                        scaleX=zoom*(if(tool=="img_03" && draft.flipX) -1f else 1f)
                        scaleY=zoom*(if(tool=="img_03" && draft.flipY) -1f else 1f)
                        val maxX=size.width*(zoom-1f)/2f;val maxY=size.height*(zoom-1f)/2f
                        translationX=pan.x.coerceIn(-maxX,maxX);translationY=pan.y.coerceIn(-maxY,maxY)
                    },contentAlignment=AbsoluteAlignment.TopLeft) {
                    Image(frame.bitmap.asImageBitmap(),
                        contentDescription=stringResource(R.string.visual_editor_title),
                        modifier=Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)

                    // Preview the ACTUAL user-imported image, not a placeholder rectangle.
                    val overlay=importedOverlay?.bitmap
                    if(overlayTool && overlay!=null && !overlay.isRecycled) {
                        Image(overlay.asImageBitmap(),
                            contentDescription=stringResource(R.string.visual_overlay_image),
                            modifier=Modifier
                                .absoluteOffset(x=sourceWidth*rect.left,y=sourceHeight*rect.top)
                                .size(sourceWidth*(rect.right-rect.left),
                                    sourceHeight*(rect.bottom-rect.top)),
                            contentScale=ContentScale.Fit)
                    }
                    if(annotation && draft.text1.isNotBlank()) {
                        Text(draft.text1,
                            modifier=Modifier.absoluteOffset(x=sourceWidth*rect.left,
                                y=sourceHeight*rect.top)
                                .widthIn(max=180.dp).padding(5.dp),
                            maxLines=2,overflow=TextOverflow.Ellipsis,
                            style=QuietTheme.typography.metadataStrong,
                            color=colors.primary)
                    }

                    val active by rememberUpdatedState(rect)
                    var handle by remember { mutableStateOf(VisualHandle.NONE) }
                    Canvas(Modifier.fillMaxSize().pointerInput(tool,sourceId,currentPage,handlePx) {
                        detectDragGestures(
                            onDragStart={ tap ->
                                if(inkKind) {
                                    inkPoints=listOf(Offset(tap.x/size.width,tap.y/size.height))
                                    return@detectDragGestures
                                }
                                beforeDrag=active
                                handle=active.hitHandle(tap.x/size.width,tap.y/size.height,
                                    size.width.toFloat(),size.height.toFloat(),handlePx)
                                if(annotation && handle!=VisualHandle.NONE) handle=VisualHandle.BODY
                                dragging=handle!=VisualHandle.NONE
                            },
                            onDrag={ change,delta ->
                                if(inkKind) {
                                    change.consume()
                                    inkPoints=inkPoints+Offset(change.position.x/size.width,change.position.y/size.height)
                                    return@detectDragGestures
                                }
                                if(handle!=VisualHandle.NONE) {
                                    change.consume()
                                    val moved=active.draggedLocked(handle,delta.x/size.width,
                                        delta.y/size.height,if(lockedAspect>0f) lockedAspect/sourceAspect else 0f).valid()
                                    val snapped=SnapGuides.snap(moved,handle)
                                    rect=snapped.rect
                                    if(snapped.snapped){
                                        // The guide is drawn in page coordinates, so it is
                                        // stored normalized like everything else here.
                                        snapGuide=Offset(snapped.guideX?: -1f,snapped.guideY?: -1f)
                                        haptics.snap()
                                    } else snapGuide=null
                                }
                            },
                            onDragEnd={
                                if(inkKind) {
                                    if(inkPoints.size>=2) {
                                        val flat=inkPoints.flatMap { listOf(it.x,it.y) }
                                        latestEdit { it.copy(inkPoints=flat) }
                                        haptics.selectionToggle()
                                    }
                                    inkPoints=emptyList()
                                    return@detectDragGestures
                                }
                                if(handle!=VisualHandle.NONE) {
                                    beforeDrag?.let { recordEdit(it,active) }
                                    saveRect(active)
                                    haptics.selectionToggle()
                                }
                                beforeDrag=null
                                handle=VisualHandle.NONE
                                dragging=false
                                snapGuide=null
                            },
                            onDragCancel={
                                dragging=false
                                handle=VisualHandle.NONE
                                snapGuide=null
                                rect=beforeDrag ?: draft.visualRect()
                                beforeDrag=null
                            }
                        )
                    }) {
                        val x0=rect.left*size.width
                        val y0=rect.top*size.height
                        val x1=rect.right*size.width
                        val y1=rect.bottom*size.height
                        val rectSize=androidx.compose.ui.geometry.Size(x1-x0,y1-y0)
                        if(cropTool) {
                            val shade=Color.Black.copy(alpha=.48f)
                            drawRect(shade,size=androidx.compose.ui.geometry.Size(size.width,y0))
                            drawRect(shade,Offset(0f,y1),
                                androidx.compose.ui.geometry.Size(size.width,size.height-y1))
                            drawRect(shade,Offset(0f,y0),androidx.compose.ui.geometry.Size(x0,y1-y0))
                            drawRect(shade,Offset(x1,y0),
                                androidx.compose.ui.geometry.Size(size.width-x1,y1-y0))
                            for(i in 1..2) {
                                val position=i/3f
                                drawLine(Color.White.copy(alpha=.5f),
                                    Offset(x0+(x1-x0)*position,y0),
                                    Offset(x0+(x1-x0)*position,y1),1.dp.toPx())
                                drawLine(Color.White.copy(alpha=.5f),
                                    Offset(x0,y0+(y1-y0)*position),
                                    Offset(x1,y0+(y1-y0)*position),1.dp.toPx())
                            }
                        } else if(annotation) {
                            when(PdfAnnotationKind.from(currentDraft.text2)) {
                                PdfAnnotationKind.HIGHLIGHT ->
                                    drawRect(Color(1f,.92f,.23f).copy(alpha=.45f),Offset(x0,y0),rectSize)
                                PdfAnnotationKind.UNDERLINE ->
                                    drawLine(colors.primary,Offset(x0,y1),Offset(x1,y1),4.dp.toPx())
                                PdfAnnotationKind.STRIKETHROUGH ->
                                    drawLine(colors.primary,Offset(x0,(y0+y1)/2f),Offset(x1,(y0+y1)/2f),3.dp.toPx())
                                PdfAnnotationKind.CIRCLE ->
                                    drawOval(colors.primary.copy(alpha=.25f),Offset(x0,y0),rectSize,style=Stroke(3.dp.toPx()))
                                PdfAnnotationKind.NOTE ->
                                    drawRect(Color(1f,.85f,.3f).copy(alpha=.55f),Offset(x0,y0),
                                        androidx.compose.ui.geometry.Size(24.dp.toPx(),24.dp.toPx()))
                                else ->
                                    drawRect(colors.primary.copy(alpha=.10f),Offset(x0,y0),rectSize)
                            }
                        } else if(overlayTool) {
                            drawRect(colors.primary.copy(alpha=.10f),Offset(x0,y0),rectSize)
                        }
                        if(inkKind) {
                            // The stroke is drawn as the user draws it.
                            if(inkPoints.size>=2) {
                                val path=androidx.compose.ui.graphics.Path()
                                inkPoints.forEachIndexed { index,point ->
                                    val px=point.x*size.width; val py=point.y*size.height
                                    if(index==0) path.moveTo(px,py) else path.lineTo(px,py)
                                }
                                drawPath(path,colors.primary,style=Stroke(4.dp.toPx()))
                            }
                        } else {
                        drawRect(colors.primary,Offset(x0,y0),rectSize,style=Stroke(3.dp.toPx()))
                        snapGuide?.let { guide ->
                            if(guide.x>=0f) drawLine(colors.tertiary,Offset(guide.x*size.width,0f),
                                Offset(guide.x*size.width,size.height),1.5.dp.toPx())
                            if(guide.y>=0f) drawLine(colors.tertiary,Offset(0f,guide.y*size.height),
                                Offset(size.width,guide.y*size.height),1.5.dp.toPx())
                        }
                        listOf(
                            Offset(x0,y0),Offset(x1,y0),Offset(x0,y1),Offset(x1,y1)
                        ).forEach { corner ->
                            drawCircle(Color.White,11.dp.toPx(),center=corner)
                            drawCircle(colors.primary,8.dp.toPx(),center=corner)
                        }
                        }
                    }
                }
            }
            }
            TextButton(onClick={zoom=1f;pan=Offset.Zero}) { Text(stringResource(R.string.editor_fit_preview)) }
            Text(stringResource(R.string.visual_edit_drag_hint),
                style=QuietTheme.typography.metadata,color=colors.onSurfaceVariant)
            if(sourceIsPdf && pageCount>1) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,
                    verticalAlignment=Alignment.CenterVertically) {
                    IconButton(enabled=currentPage>1,onClick={
                        currentPage--
                        if(tool in setOf("pdf_image","pdf_signature","pdf_annotate","pdf_redact"))
                            latestEdit { it.copy(num1=currentPage.toString()) }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateBefore,
                            stringResource(R.string.action_previous))
                    }
                    Text(stringResource(R.string.ui_page_indicator,currentPage,pageCount),
                        style=QuietTheme.typography.metadataStrong)
                    IconButton(enabled=currentPage<pageCount,onClick={
                        currentPage++
                        if(tool in setOf("pdf_image","pdf_signature","pdf_annotate","pdf_redact"))
                            latestEdit { it.copy(num1=currentPage.toString()) }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateNext,
                            stringResource(R.string.action_next))
                    }
                }
                if(tool=="pdf_crop") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(draft.pages=="all",onClick={
                            latestEdit { it.copy(pages="all") }
                        },label={Text(stringResource(R.string.visual_apply_all))})
                        FilterChip(draft.pages==currentPage.toString(),onClick={
                            latestEdit { it.copy(pages=currentPage.toString()) }
                        },label={Text(stringResource(R.string.visual_apply_page))})
                    }
                }
            }
            if(cropTool) {
                if(!annotation) {
                    // The handles and these fields are two views of the same rectangle.
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        CropNumberField(stringResource(R.string.field_left),draft.cropLeft,Modifier.weight(1f)) { value ->
                            recordSnapshot();val next=rect.copy(left=value);rect=next;saveRect(next);haptics.selectionToggle()
                        }
                        CropNumberField(stringResource(R.string.field_top),draft.cropTop,Modifier.weight(1f)) { value ->
                            recordSnapshot();val next=rect.copy(top=value);rect=next;saveRect(next);haptics.selectionToggle()
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        CropNumberField(stringResource(R.string.field_right),draft.cropRight,Modifier.weight(1f)) { value ->
                            recordSnapshot();val next=rect.copy(right=value);rect=next;saveRect(next);haptics.selectionToggle()
                        }
                        CropNumberField(stringResource(R.string.field_bottom),draft.cropBottom,Modifier.weight(1f)) { value ->
                            recordSnapshot();val next=rect.copy(bottom=value);rect=next;saveRect(next);haptics.selectionToggle()
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.visual_edit_free to 0f,
                        R.string.visual_edit_square to 1f,
                        R.string.visual_edit_document to (210f/297f)
                    ).forEach { (label,ratio) ->
                        FilterChip(lockedAspect==ratio,onClick={
                            val box=if(ratio==0f) VisualRect.WHOLE
                                else VisualRect.fitAspect(frame.width,frame.height,ratio)
                            if(rect!=box || lockedAspect!=ratio) recordSnapshot()
                            lockedAspect=ratio
                            rect=box
                            saveRect(box)
                            haptics.selectionToggle()
                        },label={Text(stringResource(label))})
                    }
                }
                if(tool=="img_03") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            recordSnapshot();latestEdit { it.copy(turns=(it.turns+1)%4) }
                        },modifier=Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Rounded.RotateRight,null,Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.visual_rotate))
                        }
                        OutlinedButton(onClick={
                            recordSnapshot();latestEdit { it.copy(flipX=!it.flipX) }
                        },modifier=Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Flip,null,Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.visual_flip))
                        }
                    }
                }
            }
            if(tool=="img_03") OutlinedButton(onClick={ recordSnapshot();latestEdit { it.copy(flipY=!it.flipY) } }) {
                Text(stringResource(R.string.editor_flip_vertical))
            }
            Text(stringResource(R.string.editor_adjust_region),style=QuietTheme.typography.metadataStrong)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(R.string.editor_move_left to (-.02f to 0f),R.string.editor_move_right to (.02f to 0f),
                    R.string.editor_move_up to (0f to -.02f),R.string.editor_move_down to (0f to .02f)).forEach { (label,delta) ->
                    OutlinedButton(onClick={val next=rect.dragged(VisualHandle.BODY,delta.first,delta.second);recordEdit(rect,next);rect=next;saveRect(next)}) { Text(stringResource(label)) }
                }
            }
            if(!annotation) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(R.string.editor_smaller to -.02f,R.string.editor_larger to .02f).forEach { (label,delta) ->
                    OutlinedButton(onClick={val next=rect.draggedLocked(VisualHandle.BOTTOM_RIGHT,delta,delta,if(lockedAspect>0f) lockedAspect/sourceAspect else 0f);recordEdit(rect,next);rect=next;saveRect(next)}) { Text(stringResource(label)) }
                }
            }
            if(tool=="pdf_redact") Text(stringResource(R.string.visual_redact_warning),
                style=QuietTheme.typography.metadata,color=colors.error)
        } else if(failed) {
            Text(stringResource(R.string.msg_preview_unavailable),color=colors.error)
        } else QuietShimmerSkeleton()

        if(tool=="pdf_signature" && overlayId==null) {
            Text(stringResource(R.string.visual_signature_required),
                style=QuietTheme.typography.metadata,color=colors.onSurfaceVariant)
            com.jumlo.app.ui.scan.SignaturePad { file ->
                vm.intakeLocalFile(file,"signature.png","image/png")
            }
        }
        if(tool=="pdf_image" && overlayId==null) {
            Text(stringResource(R.string.visual_two_sources),
                style=QuietTheme.typography.metadata,color=colors.onSurfaceVariant)
        }
    }
}

/**
 * Numeric mirror of a crop edge. The handle is the primary control; this field is the
 * exact-value input for the same number and never disagrees with it.
 */
@Composable
internal fun CropNumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        // A drag on the handles rewrites the value; reflect it unless the user is typing.
        val typed = text.replace(',', '.').toFloatOrNull()
        val external = value.replace(',', '.').toFloatOrNull()
        if (typed == null || external == null || typed != external) text = value
    }
    val parsed = text.replace(',', '.').toFloatOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.take(8)
            val candidate = text.replace(',', '.').toFloatOrNull()
            if (candidate != null && candidate.isFinite() && candidate in 0f..1f) onChange(candidate)
        },
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        singleLine = true,
        isError = text.isNotEmpty() && (parsed == null || parsed < 0f || parsed > 1f),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = modifier.heightIn(min = 64.dp)
    )
}
