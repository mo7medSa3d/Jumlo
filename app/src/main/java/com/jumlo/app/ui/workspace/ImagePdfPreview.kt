package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.*
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietCard
import com.jumlo.app.ui.components.QuietChip
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** The three settings this preview owns. Kept apart from the rest of the draft on purpose. */
private data class PreviewSettings(val margin:String,val orientation:String,val fill:Boolean)

/** Output-faithful paper geometry with one local update per gesture and synchronized secondary fields. */
@Composable
internal fun ImagePdfPreview(vm:AppViewModel,sourceId:String,draft:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    var retry by remember {mutableIntStateOf(0)}
    val frame by produceState<Result<PreviewFrame>?>(null,sourceId,retry) {
        value=null
        value=try {Result.success(vm.preview(sourceId,1))}
            catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {Result.failure(error)}
    }
    val haptics=rememberAppHaptics()
    var margin by remember(draft.margin) {mutableFloatStateOf((WorkspaceDraft.normalizeDigits(draft.margin).toIntOrNull() ?: 10).coerceIn(0,200).toFloat())}
    var past by remember {mutableStateOf<List<PreviewSettings>>(emptyList())}
    var future by remember {mutableStateOf<List<PreviewSettings>>(emptyList())}
    fun snapshot()=PreviewSettings(draft.margin,draft.orientation,draft.fill)
    fun apply(next:PreviewSettings) {
        val before=snapshot()
        if(before==next) return
        past=(past+before).takeLast(20)
        future=emptyList()
        edit { it.copy(margin=next.margin,orientation=next.orientation,fill=next.fill) }
    }
    fun undo() {
        val previous=past.lastOrNull() ?: return
        past=past.dropLast(1)
        future=(future+snapshot()).takeLast(20)
        edit { it.copy(margin=previous.margin,orientation=previous.orientation,fill=previous.fill) }
        haptics.selectionToggle()
    }
    fun redo() {
        val next=future.lastOrNull() ?: return
        future=future.dropLast(1)
        past=(past+snapshot()).takeLast(20)
        edit { it.copy(margin=next.margin,orientation=next.orientation,fill=next.fill) }
        haptics.selectionToggle()
    }
    val image=frame?.getOrNull()
    QuietCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=::undo,enabled=past.isNotEmpty(),modifier=Modifier.heightIn(min=48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Undo,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.action_undo))
            }
            TextButton(onClick=::redo,enabled=future.isNotEmpty(),modifier=Modifier.heightIn(min=48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Redo,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.visual_edit_redo))
            }
        }
        Text(stringResource(R.string.upgrade_image_to_pdf_preview),style=QuietTheme.typography.sectionTitle)
        when {
            frame==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            image==null -> TextButton(onClick={retry++}) {Text(stringResource(R.string.upgrade_preview_unavailable))}
            else -> {
                val page=runCatching {PdfPageGeometry.resolve(image.width,image.height,draft.paperMode,
                    WorkspaceDraft.normalizeDigits(draft.paperWidth).toIntOrNull() ?: 595,WorkspaceDraft.normalizeDigits(draft.paperHeight).toIntOrNull() ?: 842,draft.orientation)}.getOrNull()
                val placement=page?.let {runCatching {ImagePdfLayout.resolve(image.width,image.height,it,margin.roundToInt(),draft.paperMode=="AUTO",draft.fill)}.getOrNull()}
                if(placement==null) Text(stringResource(R.string.upgrade_invalid_paper),color=MaterialTheme.colorScheme.error)
                else {
                    val guide=MaterialTheme.colorScheme.primary
                    val bitmap=image.bitmap.asImageBitmap()
                    val hint=stringResource(R.string.upgrade_margin_drag)
                    Canvas(Modifier.fillMaxWidth().height(310.dp).clip(QuietTheme.shapes.thumbnail)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .semantics {contentDescription=hint}
                        .pointerInput(page,draft.paperMode) {
                            var edge=0
                            var beforeDrag=margin
                            detectDragGestures(onDragStart={point ->
                                beforeDrag=margin
                                val scale=minOf((size.width-32.dp.toPx())/placement.pageWidthPt,(size.height-32.dp.toPx())/placement.pageHeightPt)
                                val left=(size.width-placement.pageWidthPt*scale)/2f;val top=(size.height-placement.pageHeightPt*scale)/2f
                                val x=(point.x-left)/scale;val y=(point.y-top)/scale
                                val distances=listOf(x,y,placement.pageWidthPt-x,placement.pageHeightPt-y)
                                edge=distances.indices.minBy {distances[it]}
                                haptics.dragStart()
                            },onDrag={change,_->
                                change.consume()
                                val scale=minOf((size.width-32.dp.toPx())/placement.pageWidthPt,(size.height-32.dp.toPx())/placement.pageHeightPt)
                                val x=(change.position.x-(size.width-placement.pageWidthPt*scale)/2f)/scale
                                val y=(change.position.y-(size.height-placement.pageHeightPt*scale)/2f)/scale
                                val distance=listOf(x,y,placement.pageWidthPt-x,placement.pageHeightPt-y)[edge]
                                val maximum=if(draft.paperMode=="AUTO") minOf(placement.pageWidthPt,placement.pageHeightPt)*.08f else minOf(placement.pageWidthPt,placement.pageHeightPt)*.45f
                                margin=(distance.coerceIn(0f,maximum)*25.4f/72f).coerceIn(0f,200f)
                            },onDragEnd={
                                apply(PreviewSettings(margin.roundToInt().toString(),draft.orientation,draft.fill));haptics.dragEnd()
                            },
                                onDragCancel={margin=beforeDrag})
                        }) {
                        val s=minOf((size.width-32.dp.toPx())/placement.pageWidthPt,(size.height-32.dp.toPx())/placement.pageHeightPt)
                        val x=(size.width-placement.pageWidthPt*s)/2;val y=(size.height-placement.pageHeightPt*s)/2
                        val w=placement.pageWidthPt*s;val h=placement.pageHeightPt*s;val m=placement.marginPt*s
                        drawRect(Color.White,Offset(x,y),Size(w,h))
                        clipRect(x+m,y+m,x+w-m,y+h-m) {
                            drawImage(bitmap,dstOffset=IntOffset((x+placement.leftPt*s).roundToInt(),(y+placement.topPt*s).roundToInt()),
                                dstSize=IntSize((placement.widthPt*s).roundToInt().coerceAtLeast(1),(placement.heightPt*s).roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.High)
                        }
                        drawRect(guide,Offset(x+m,y+m),Size(w-2*m,h-2*m),style=Stroke(1.5.dp.toPx()))
                        listOf(Offset(x+m,y+h/2),Offset(x+w-m,y+h/2),Offset(x+w/2,y+m),Offset(x+w/2,y+h-m)).forEach {
                            drawCircle(guide.copy(alpha=.15f),24.dp.toPx(),it);drawCircle(guide,5.dp.toPx(),it)
                        }
                    }
                }
            }
        }
        val marginLabel=stringResource(R.string.upgrade_margin_mm,margin.roundToInt())
        Text(marginLabel,style=QuietTheme.typography.metadataStrong)
        Slider(margin.coerceIn(0f,200f),{margin=it},valueRange=0f..200f,
            modifier=Modifier.semantics {contentDescription=marginLabel},
            onValueChangeFinished={apply(PreviewSettings(margin.roundToInt().toString(),draft.orientation,draft.fill));haptics.sliderTick()})
        if(draft.paperMode!="AUTO") LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(listOf("AUTO" to R.string.upgrade_orientation_auto,"PORTRAIT" to R.string.upgrade_orientation_portrait,"LANDSCAPE" to R.string.upgrade_orientation_landscape)) {option->
                QuietChip(draft.orientation==option.first,{apply(PreviewSettings(draft.margin,option.first,draft.fill))},stringResource(option.second))
            }
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(listOf(false to R.string.upgrade_scale_fit,true to R.string.upgrade_scale_fill)) {option->
                QuietChip(draft.fill==option.first,{apply(PreviewSettings(draft.margin,draft.orientation,option.first))},stringResource(option.second))
            }
        }
    }
}
