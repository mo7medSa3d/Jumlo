package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.PreviewFrame

private data class WatermarkRaster(val image:Bitmap,val textSize:Float)

/** Same Android text rasterization and point-to-page sizing as PdfBoxBackend.watermark. */
@Composable
internal fun PdfWatermarkPreview(frame:PreviewFrame,draft:WorkspaceDraft,page:Int,applies:Boolean,
    centerX:Float,centerY:Float,onPosition:(Float,Float)->Unit,onCommit:()->Unit) {
    val text=draft.text1.trim().replace(Regex("\\s+")," ").take(120)
    val color=draft.text3.takeIf {Regex("^#[0-9A-Fa-f]{6}$").matches(it)} ?: "#64748B"
    val opacity=draftNumber(draft.num4,28).coerceIn(5,100)
    val fontSize=draftNumber(draft.num1,42).coerceIn(12,90)
    val rotation=draftNumber(draft.text4,-35).coerceIn(-180,180)
    val tiled=draft.text2=="TILE"
    val raster=remember(text,color,opacity) {if(text.isBlank()) null else {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap=true;textSize=84f;typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
            this.color=android.graphics.Color.parseColor(color);alpha=opacity*255/100
        }
        val rawWidth=paint.measureText(text)
        if(rawWidth>1650f) paint.textSize*=1650f/rawWidth
        val metrics=paint.fontMetrics
        val image=Bitmap.createBitmap((paint.measureText(text)+48f).toInt().coerceIn(64,1710),
            (metrics.descent-metrics.ascent+36f).toInt().coerceIn(80,220),Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(image).drawText(text,24f,18f-metrics.ascent,paint)
        WatermarkRaster(image,paint.textSize)
    }}
    val latestX by rememberUpdatedState(centerX)
    val latestY by rememberUpdatedState(centerY)
    val latestPosition by rememberUpdatedState(onPosition)
    val latestCommit by rememberUpdatedState(onCommit)
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val aspect=frame.width.toFloat()/frame.height
        val width=minOf(maxWidth,440.dp,460.dp*aspect)
        val height=width/aspect
        Canvas(Modifier.size(width,height).clipToBounds()
            .pointerInput(frame,tiled,applies) {
                if(!tiled && applies) {
                    var beforeX=.5f;var beforeY=.5f
                    detectDragGestures(
                        onDragStart={beforeX=latestX;beforeY=latestY},
                        onDrag={change,delta ->
                            change.consume()
                            latestPosition((latestX+delta.x/size.width).coerceIn(0f,1f),
                                (latestY+delta.y/size.height).coerceIn(0f,1f))
                        },
                        onDragEnd={latestCommit()},
                        onDragCancel={latestPosition(beforeX,beforeY)})
                }
            }) {
            drawIntoCanvas {compose ->
                val canvas=compose.nativeCanvas
                canvas.save()
                try {
                    canvas.scale(size.width/frame.width,size.height/frame.height)
                    canvas.drawBitmap(frame.bitmap,null,RectF(0f,0f,frame.width.toFloat(),frame.height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
                    if(applies) {
                        raster?.let {overlay ->
                            val rawWidth=overlay.image.width*(fontSize/overlay.textSize)
                            val rawHeight=overlay.image.height*(fontSize/overlay.textSize)
                            val fit=minOf(1f,frame.width*(if(tiled) .36f else .8f)/rawWidth)
                            val w=rawWidth*fit;val h=rawHeight*fit
                            val spots=if(tiled) listOf(.18f,.5f,.82f).flatMap {y->listOf(.18f,.5f,.82f).map {x->x to y}}
                                else listOf(centerX to centerY)
                            spots.forEach {(x,y)->
                                canvas.save()
                                canvas.translate(x*frame.width,y*frame.height)
                                canvas.rotate(rotation.toFloat())
                                canvas.drawBitmap(overlay.image,null,RectF(-w/2,-h/2,w/2,h/2),Paint(Paint.FILTER_BITMAP_FLAG))
                                canvas.restore()
                            }
                        }
                        if(draft.flag1) canvas.drawText(page.toString(),frame.width*.47f,frame.height*.96f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=10f;this.color=android.graphics.Color.rgb(102,102,102);typeface=Typeface.create("sans-serif",Typeface.NORMAL)})
                    }
                } finally {canvas.restore()}
            }
        }
    }
}
