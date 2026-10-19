package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietShimmerSkeleton
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlin.math.min

private fun WorkspaceDraft.visualRect():VisualRect {
    fun parse(s:String,f:Float) = s.toFloatOrNull()?.takeIf { it.isFinite() } ?: f
    val default = when(toolId) {
        "pdf_signature" -> VisualRect.SIGNATURE_START
        "pdf_redact" -> VisualRect.REDACTION_START
        else -> VisualRect.WHOLE
    }
    return VisualRect(parse(cropLeft,default.left),parse(cropTop,default.top),
        parse(cropRight,default.right),parse(cropBottom,default.bottom)).valid()
}

private fun Float.serialized() = "%.5f".format(java.util.Locale.US,this)

@Composable
internal fun VisualDocumentEditor(
    vm:AppViewModel,
    sourceId:String,
    draft:WorkspaceDraft,
    edit:((WorkspaceDraft)->WorkspaceDraft)->Unit
) {
    val cropMode=draft.toolId in setOf("img_03","pdf_crop","pdf_redact","pdf_signature")
    val positionMode=false
    val sourceIsPdf=draft.toolId.startsWith("pdf_")
    val selectedPage=(draft.num1.toIntOrNull() ?: 1).coerceAtLeast(1)
    var preview by remember(sourceId,selectedPage) { mutableStateOf<PreviewFrame?>(null) }
    var failed by remember(sourceId,selectedPage) { mutableStateOf(false) }
    var count by remember(sourceId) { mutableIntStateOf(1) }
    LaunchedEffect(sourceId,selectedPage) {
        failed=false
        preview=null
        try {
            val frame=vm.preview(sourceId,if(sourceIsPdf) selectedPage.coerceIn(1,count) else 1)
            preview=frame
            count=frame.count.coerceAtLeast(1)
        } catch(cancel:CancellationException) {throw cancel}
          catch(_:Exception) {failed=true}
    }
    DisposableEffect(preview) {
        val old=preview
        onDispose { old?.bitmap?.takeUnless {it.isRecycled}?.recycle() }
    }

    var rect by remember(sourceId,draft.toolId) { mutableStateOf(draft.visualRect()) }
    LaunchedEffect(draft.cropLeft,draft.cropTop,draft.cropRight,draft.cropBottom) {
        val incoming=draft.visualRect()
        if(incoming!=rect) rect=incoming
    }
    val colors=MaterialTheme.colorScheme
    val density=LocalDensity.current
    val handlePx=with(density) { 34.dp.toPx() }
    val edge=colors.primary
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.visual_edit_title),
                style=QuietTheme.typography.sectionTitle,modifier=Modifier.weight(1f))
            TextButton(onClick={
                val reset=when(draft.toolId) {
                    "pdf_signature" -> VisualRect.SIGNATURE_START
                    "pdf_redact" -> VisualRect.REDACTION_START
                    else -> VisualRect.WHOLE
                }
                rect=reset
                edit { it.copy(cropLeft=reset.left.serialized(),cropTop=reset.top.serialized(),
                    cropRight=reset.right.serialized(),cropBottom=reset.bottom.serialized()) }
            }) { Icon(Icons.Rounded.RestartAlt,null);Text(stringResource(R.string.visual_edit_reset)) }
        }
        val frame=preview
        if(frame!=null && !frame.bitmap.isRecycled) {
            val w=frame.bitmap.width.coerceAtLeast(1)
            val h=frame.bitmap.height.coerceAtLeast(1)
            val aspect=w.toFloat()/h
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                val canvasWidth=minOf(maxWidth,450.dp)
                val canvasHeight=(canvasWidth/aspect).coerceIn(130.dp,440.dp)
                Box(Modifier.width(canvasWidth).height(canvasHeight).clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceContainerLowest)) {
                    Image(frame.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                    if(cropMode || positionMode) {
                        val activeRect by rememberUpdatedState(rect)
                        var currentHandle by remember {mutableStateOf(VisualHandle.NONE)}
                        Canvas(Modifier.fillMaxSize().pointerInput(cropMode,positionMode,handlePx) {
                            detectDragGestures(
                                onDragStart={ at ->
                                    currentHandle=activeRect.hitHandle(
                                        at.x/size.width,at.y/size.height,
                                        size.width.toFloat(),size.height.toFloat(),handlePx)
                                },
                                onDragEnd={
                                    if(currentHandle!=VisualHandle.NONE) {
                                        if(positionMode) {
                                            edit {it.copy(num2=rect.left.serialized(),num3=rect.top.serialized())}
                                        } else {
                                            edit { it.copy(cropLeft=rect.left.serialized(),cropTop=rect.top.serialized(),
                                                cropRight=rect.right.serialized(),cropBottom=rect.bottom.serialized()) }
                                        }
                                    }
                                    currentHandle=VisualHandle.NONE
                                },
                                onDragCancel={currentHandle=VisualHandle.NONE},
                                onDrag={change,delta->
                                    if(currentHandle!=VisualHandle.NONE) {
                                        change.consume()
                                        rect=rect.dragged(if(positionMode) VisualHandle.BODY else currentHandle,
                                            delta.x/size.width,delta.y/size.height).valid()
                                    }
                                }
                            )
                        }) {
                            val drawn=if(positionMode) rect else rect
                            val left=drawn.left*size.width
                            val top=drawn.top*size.height
                            val right=drawn.right*size.width
                            val bottom=drawn.bottom*size.height
                            val stroke=3.dp.toPx()
                            drawRect(edge,topLeft=Offset(left,top),size=androidx.compose.ui.geometry.Size(right-left,bottom-top),
                                style=Stroke(stroke))
                            if(cropMode) listOf(
                                Offset(left,top),Offset(right,top),Offset(left,bottom),Offset(right,bottom)
                            ).forEach { point ->
                                drawCircle(Color.White,11.dp.toPx(),point)
                                drawCircle(edge,8.dp.toPx(),point)
                            }
                            if(cropMode && draft.toolId !in setOf("pdf_signature","pdf_redact")) {
                                val shade=Color.Black.copy(alpha=.45f)
                                drawRect(shade,size=androidx.compose.ui.geometry.Size(size.width,top))
                                drawRect(shade,topLeft=Offset(0f,bottom),
                                    size=androidx.compose.ui.geometry.Size(size.width,size.height-bottom))
                                drawRect(shade,topLeft=Offset(0f,top),
                                    size=androidx.compose.ui.geometry.Size(left,bottom-top))
                                drawRect(shade,topLeft=Offset(right,top),
                                    size=androidx.compose.ui.geometry.Size(size.width-right,bottom-top))
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.visual_edit_drag_hint),style=QuietTheme.typography.metadata,
                color=colors.onSurfaceVariant)
            if(draft.toolId in setOf("img_03","pdf_crop")) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.visual_edit_free to 0f,
                        R.string.visual_edit_square to 1f,
                        R.string.visual_edit_document to (210f/297f)
                    ).forEach { (label,ratio) ->
                        FilterChip(false,onClick={
                            val next=if(ratio==0f) VisualRect.WHOLE
                                else VisualRect.fitAspect(frame.width,frame.height,ratio)
                            rect=next
                            edit {it.copy(cropLeft=next.left.serialized(),cropTop=next.top.serialized(),
                                cropRight=next.right.serialized(),cropBottom=next.bottom.serialized())}
                        },label={Text(stringResource(label))})
                    }
                }
            }
        } else if(failed) Text(stringResource(R.string.msg_preview_unavailable),color=colors.error)
        else QuietShimmerSkeleton()
    }
}
