package com.jumlo.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
    if(toolId=="pdf_annotate") {
        val x=read(num2,.1f).coerceIn(0f,.76f)
        val y=read(num3,.1f).coerceIn(0f,.86f)
        return VisualRect(x,y,x+.24f,y+.14f)
    }
    val fallback=when(toolId) {
        "pdf_signature" -> VisualRect.SIGNATURE_START
        "pdf_redact" -> VisualRect.REDACTION_START
        "pdf_image" -> VisualRect(.18f,.22f,.82f,.78f)
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
    DisposableEffect(preview) {
        val bitmap=preview?.bitmap
        onDispose { bitmap?.takeUnless { it.isRecycled }?.recycle() }
    }
    val importedOverlay by produceState<PreviewFrame?>(null,overlayId) {
        value=null
        if(overlayId!=null) {
            try { value=vm.preview(overlayId,1) }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { value=null }
        }
    }
    DisposableEffect(importedOverlay) {
        val bitmap=importedOverlay?.bitmap
        onDispose { bitmap?.takeUnless { it.isRecycled }?.recycle() }
    }

    var rect by remember(sourceId,tool) { mutableStateOf(draft.visualRect()) }
    var dragging by remember { mutableStateOf(false) }
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

    fun saveRect(box:VisualRect) {
        val safe=box.valid()
        latestEdit { current ->
            if(annotation) current.copy(
                num2=safe.left.serialized(),num3=safe.top.serialized()
            ) else current.copy(
                cropLeft=safe.left.serialized(),cropTop=safe.top.serialized(),
                cropRight=safe.right.serialized(),cropBottom=safe.bottom.serialized()
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
                    "pdf_redact" -> VisualRect.REDACTION_START
                    "pdf_image" -> VisualRect(.18f,.22f,.82f,.78f)
                    "pdf_annotate" -> VisualRect(.1f,.1f,.34f,.24f)
                    else -> VisualRect.WHOLE
                }
                rect=reset
                saveRect(reset)
                haptics.selectionToggle()
            }) {
                Icon(Icons.Rounded.RestartAlt,null)
                Text(stringResource(R.string.visual_edit_reset))
            }
        }
        val frame=preview
        if(frame!=null && !frame.bitmap.isRecycled) {
            val aspect=frame.bitmap.width.toFloat()/frame.bitmap.height.coerceAtLeast(1)
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                // Never distort a portrait document just to fit a fixed-height preview.
                val canvasWidth=minOf(maxWidth,450.dp,(440.dp*aspect).coerceAtLeast(75.dp))
                val canvasHeight=canvasWidth/aspect
                Box(Modifier.width(canvasWidth).height(canvasHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceContainerLowest)) {
                    Image(frame.bitmap.asImageBitmap(),
                        contentDescription=stringResource(R.string.visual_editor_title),
                        modifier=Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)

                    // Preview the ACTUAL user-imported image, not a placeholder rectangle.
                    val overlay=importedOverlay?.bitmap
                    if(overlayTool && overlay!=null && !overlay.isRecycled) {
                        Image(overlay.asImageBitmap(),
                            contentDescription=stringResource(R.string.visual_overlay_image),
                            modifier=Modifier
                                .offset(x=canvasWidth*rect.left,y=canvasHeight*rect.top)
                                .size(canvasWidth*(rect.right-rect.left),
                                    canvasHeight*(rect.bottom-rect.top)),
                            contentScale=ContentScale.FillBounds)
                    }
                    if(annotation && draft.text1.isNotBlank()) {
                        Text(draft.text1,
                            modifier=Modifier.offset(x=canvasWidth*rect.left,
                                y=canvasHeight*rect.top)
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
                                handle=active.hitHandle(tap.x/size.width,tap.y/size.height,
                                    size.width.toFloat(),size.height.toFloat(),handlePx)
                                if(annotation && handle!=VisualHandle.NONE) handle=VisualHandle.BODY
                                dragging=handle!=VisualHandle.NONE
                            },
                            onDrag={ change,delta ->
                                if(handle!=VisualHandle.NONE) {
                                    change.consume()
                                    rect=active.dragged(handle,delta.x/size.width,
                                        delta.y/size.height).valid()
                                }
                            },
                            onDragEnd={
                                if(handle!=VisualHandle.NONE) {
                                    saveRect(active)
                                    haptics.selectionToggle()
                                }
                                handle=VisualHandle.NONE
                                dragging=false
                            },
                            onDragCancel={
                                dragging=false
                                handle=VisualHandle.NONE
                                rect=draft.visualRect()
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
                        } else if(tool=="pdf_redact") {
                            drawRect(Color.Black.copy(alpha=.5f),Offset(x0,y0),rectSize)
                        } else if(overlayTool) {
                            drawRect(colors.primary.copy(alpha=.10f),Offset(x0,y0),rectSize)
                        }
                        drawRect(colors.primary,Offset(x0,y0),rectSize,style=Stroke(3.dp.toPx()))
                        if(!annotation) listOf(
                            Offset(x0,y0),Offset(x1,y0),Offset(x0,y1),Offset(x1,y1)
                        ).forEach { corner ->
                            drawCircle(Color.White,11.dp.toPx(),center=corner)
                            drawCircle(colors.primary,8.dp.toPx(),center=corner)
                        }
                    }
                }
            }
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
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.visual_edit_free to 0f,
                        R.string.visual_edit_square to 1f,
                        R.string.visual_edit_document to (210f/297f)
                    ).forEach { (label,ratio) ->
                        FilterChip(false,onClick={
                            val box=if(ratio==0f) VisualRect.WHOLE
                                else VisualRect.fitAspect(frame.width,frame.height,ratio)
                            rect=box
                            saveRect(box)
                            haptics.selectionToggle()
                        },label={Text(stringResource(label))})
                    }
                }
                if(tool=="img_03") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            latestEdit { it.copy(turns=(it.turns+1)%4) }
                        },modifier=Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Rounded.RotateRight,null,Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.visual_rotate))
                        }
                        OutlinedButton(onClick={
                            latestEdit { it.copy(flipX=!it.flipX) }
                        },modifier=Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Flip,null,Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.visual_flip))
                        }
                    }
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
