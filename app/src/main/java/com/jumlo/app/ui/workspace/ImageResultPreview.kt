package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.*
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietCard
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class ImageResult(val bitmap:Bitmap,val width:Int,val height:Int)
private data class ImagePreviewSettings(val tool:String,val width:String,val height:String,val percent:String,
    val edge:String,val fit:String,val upscale:Boolean,val text:String,val n1:String,val n2:String,
    val n3:String,val n4:String,val logo:Boolean)

/** Bounded raster previews use original dimensions and the engine's placement policies. */
@Composable
internal fun ImageResultPreview(vm:AppViewModel,sourceIds:List<String>,draft:WorkspaceDraft) {
    val joined=draft.toolId in setOf("img_join","img_stitch")
    var retry by remember(sourceIds) {mutableIntStateOf(0)}
    var showOriginal by rememberSaveable(sourceIds.firstOrNull(),draft.toolId) {mutableStateOf(false)}
    val settings=ImagePreviewSettings(draft.toolId,draft.width,draft.height,draft.percentage,draft.longestEdge,
        draft.fit,draft.allowUpscaling,draft.text1,draft.num1,draft.num2,draft.num3,draft.num4,draft.flag1)
    // Decode single images once; changing a slider does not reopen the source provider.
    val frame by produceState<Result<PreviewFrame>?>(null,sourceIds.firstOrNull(),retry) {
        value=null
        if(!joined && sourceIds.isNotEmpty()) value=try {Result.success(vm.preview(sourceIds.first(),1))}
            catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {Result.failure(error)}
    }
    val logoId=sourceIds.getOrNull(1)?.takeIf {draft.toolId=="img_watermark" && draft.flag1}
    val logoResult by produceState<Result<PreviewFrame>?>(null,logoId,retry) {
        value=null
        if(logoId!=null) value=try {Result.success(vm.thumbnail(logoId,1))}
            catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {Result.failure(error)}
    }
    val sizes by produceState<Result<List<Pair<Int,Int>>>?>(null,sourceIds,joined,retry) {
        value=null
        if(joined && sourceIds.isNotEmpty()) value=try {
            Result.success(sourceIds.map {id ->
                currentCoroutineContext().ensureActive()
                val image=vm.thumbnail(id,1)
                try {image.width to image.height} finally {image.bitmap.recycle()}
            })
        } catch(cancel:CancellationException) {throw cancel}
          catch(error:Exception) {Result.failure(error)}
    }
    val result by produceState<Result<ImageResult>?>(null,settings,frame,logoResult,sizes,retry) {
        value=null
        // A short cancellable debounce prevents decoding a whole collage for each drag event.
        delay(70)
        value=try {
            if(joined) {
                val dimensions=sizes?.getOrThrow() ?: return@produceState
                Result.success(renderJoinPreview(vm,sourceIds,dimensions,draft))
            } else {
                val source=frame?.getOrThrow() ?: return@produceState
                val logo=if(logoId!=null) logoResult?.getOrThrow() ?: return@produceState else null
                Result.success(withContext(Dispatchers.Default) {renderSinglePreview(source,logo,draft)})
            }
        } catch(cancel:CancellationException) {throw cancel}
          catch(error:Exception) {Result.failure(error)}
    }
    QuietCard(Modifier.fillMaxWidth()) {
        Text(stringResource(if(joined) R.string.interactive_layout_preview else R.string.interactive_result_preview),style=QuietTheme.typography.sectionTitle)
        val shown=if(showOriginal && !joined) frame?.getOrNull()?.let {ImageResult(it.bitmap,it.width,it.height)} else result?.getOrNull()
        if(shown!=null) {
            Image(shown.bitmap.asImageBitmap(),stringResource(R.string.interactive_result_preview),
                contentScale=ContentScale.Fit,
                modifier=Modifier.fillMaxWidth().height(320.dp).clip(QuietTheme.shapes.thumbnail)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Text(stringResource(if(showOriginal) R.string.interactive_preview_source_size else R.string.visual_output_dimensions,shown.width,shown.height),style=QuietTheme.typography.metadataStrong)
        } else if(result?.isFailure==true || frame?.isFailure==true || sizes?.isFailure==true || logoResult?.isFailure==true) {
            Text(stringResource(R.string.msg_preview_unavailable),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={retry++}) {Text(stringResource(R.string.interactive_preview_retry))}
        } else LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!joined) {
            OutlinedButton(onClick={showOriginal=!showOriginal},enabled=frame?.isSuccess==true,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(if(showOriginal) R.string.visual_show_edited else R.string.visual_show_original))
            }
            frame?.getOrNull()?.let {Text(stringResource(R.string.interactive_preview_source_size,it.width,it.height),style=QuietTheme.typography.metadata)}
        }
        Text(stringResource(R.string.interactive_preview_sampling),style=QuietTheme.typography.metadata)
        if(draft.toolId=="img_watermark") Text(stringResource(R.string.interactive_watermark_position),style=QuietTheme.typography.metadata)
    }
}

private fun previewBitmap(width:Int,height:Int):Bitmap {
    val scale=minOf(1.0,1200.0/maxOf(width,height))
    return Bitmap.createBitmap((width*scale).roundToInt().coerceAtLeast(1),
        (height*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
}

private fun renderSinglePreview(frame:PreviewFrame,logo:PreviewFrame?,draft:WorkspaceDraft):ImageResult {
    val sw=frame.width;val sh=frame.height
    val padding=if(draft.toolId=="img_adjust") draftNumber(draft.num4,0).coerceIn(0,2000) else 0
    val geometry=if(draft.toolId=="img_02") {
        val percentage=draft.percentage.takeIf {it.isNotBlank()}?.let {draftNumber(it,100)}
        val edge=draft.longestEdge.takeIf {it.isNotBlank()}?.let {draftNumber(it,1920)}
        val target=ImageResizePolicy.targetDimensions(sw,sh,percentage,edge,draftNumber(draft.width,1600),draftNumber(draft.height,1600))
        ImageGeometry.resolve(sw,sh,target.first,target.second,FitMode.valueOf(draft.fit),
            ImageResizePolicy.allowsUpscaling(percentage,edge,draft.allowUpscaling))
    } else Geometry(sw+padding*2,sh+padding*2,Box(0.0,0.0,1.0,1.0),
        Box(padding.toDouble(),padding.toDouble(),(sw+padding).toDouble(),(sh+padding).toDouble()))
    val out=previewBitmap(geometry.width,geometry.height)
    try {
        val canvas=Canvas(out)
        canvas.scale(out.width.toFloat()/geometry.width,out.height.toFloat()/geometry.height)
        // Padding and the default JPEG matte are white in the processor.
        canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if(draft.toolId=="img_adjust") {
            val c=draftNumber(draft.num2,100).coerceIn(0,300)/100f
            val s=draftNumber(draft.num3,100).coerceIn(0,300)/100f
            val b=draftNumber(draft.num1,0).coerceIn(-100,100)*1.27f
            val matrix=ColorMatrix(floatArrayOf(c,0f,0f,0f,b,0f,c,0f,0f,b,0f,0f,c,0f,b,0f,0f,0f,1f,0f))
            matrix.postConcat(ColorMatrix().apply {setSaturation(s)})
            paint.colorFilter=ColorMatrixColorFilter(matrix)
        }
        val crop=geometry.crop;val draw=geometry.draw;val source=frame.bitmap
        canvas.drawBitmap(source,Rect((crop.left*source.width).roundToInt(),(crop.top*source.height).roundToInt(),
            (crop.right*source.width).roundToInt(),(crop.bottom*source.height).roundToInt()),
            RectF(draw.left.toFloat(),draw.top.toFloat(),draw.right.toFloat(),draw.bottom.toFloat()),paint)
        if(draft.toolId=="img_watermark") {
            val opacity=draftNumber(draft.num1,40).coerceIn(1,100)
            if(draft.flag1 && logo!=null) {
                val pad=minOf(16,sw/20,sh/20)
                val width=(sw/4).coerceAtLeast(1)
                val height=(logo.height*width.toFloat()/logo.width).toInt().coerceIn(1,(sh-pad*2).coerceAtLeast(1))
                canvas.drawBitmap(logo.bitmap,null,RectF((sw-width-pad).toFloat(),(sh-height-pad).toFloat(),(sw-pad).toFloat(),(sh-pad).toFloat()),
                    Paint(Paint.FILTER_BITMAP_FLAG).apply {alpha=opacity*255/100})
            } else if(!draft.flag1) {
                val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.WHITE;alpha=opacity*255/100;textSize=(sw/12f).coerceAtLeast(18f)
                    isFakeBoldText=true;setShadowLayer(4f,2f,2f,Color.BLACK)
                }
                canvas.drawText(draft.text1,sw*.08f,sh*.92f,textPaint)
            }
        }
        return ImageResult(out,geometry.width,geometry.height)
    } catch(error:Throwable) {out.recycle();throw error}
}

/** Thumbnails are decoded and released one at a time, even for very large batches. */
private suspend fun renderJoinPreview(vm:AppViewModel,ids:List<String>,sizes:List<Pair<Int,Int>>,draft:WorkspaceDraft):ImageResult {
    val plan=ImageJoinPolicy.plan(sizes,if(draft.toolId=="img_stitch") "VSTRIP" else draft.text1.ifBlank {"GRID2"},
        draftNumber(draft.num1,8),if(draft.toolId=="img_stitch") draftNumber(draft.num2,0) else 0)
    val output=previewBitmap(plan.width,plan.height)
    try {
        val canvas=Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.scale(output.width.toFloat()/plan.width,output.height.toFloat()/plan.height)
        for((index,id) in ids.withIndex()) {
            currentCoroutineContext().ensureActive()
            val frame=vm.thumbnail(id,1)
            try {
                val p=plan.placements[index];val (w,h)=sizes[index]
                canvas.drawBitmap(frame.bitmap,Rect(0,(p.trimTop.toFloat()/h*frame.bitmap.height).roundToInt().coerceIn(0,frame.bitmap.height-1),frame.bitmap.width,frame.bitmap.height),
                    RectF(p.x.toFloat(),p.y.toFloat(),(p.x+w).toFloat(),(p.y+h-p.trimTop).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
            } finally {frame.bitmap.recycle()}
        }
        return ImageResult(output,plan.width,plan.height)
    } catch(error:Throwable) {output.recycle();throw error}
}
