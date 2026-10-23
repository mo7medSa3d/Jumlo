package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.PreviewFrame
import kotlin.math.*

/** Coordinates are normalized against the upright page, independent of layout direction. */
internal data class PreviewObject(
    val id:String,val bitmap:Bitmap,val left:Float,val top:Float,val right:Float,val bottom:Float,
    val rotation:Float=0f,val opacity:Float=1f
) {
    val center get()=Offset((left+right)/2,(top+bottom)/2)
    val width get()=right-left
    val height get()=bottom-top
    fun transform(center:Offset,width:Float,height:Float,rotation:Float)=copy(
        left=center.x-width/2,top=center.y-height/2,right=center.x+width/2,bottom=center.y+height/2,rotation=rotation)
}

/** Real page and real alpha bitmaps. A selected object's gestures never zoom the underlying page. */
@Composable
internal fun TransformablePagePreview(
    frame:PreviewFrame,objects:List<PreviewObject>,selectedId:String?,onSelect:(String?)->Unit,
    onTransform:(PreviewObject)->Unit,onCommit:()->Unit={},tag:String="object.canvas",
    description:String=stringResource(R.string.studio_watermark_object),pageNumber:Int?=null,
    allowTranslation:Boolean=true
) {
    var pageZoom by rememberSaveable(frame.page) {mutableFloatStateOf(1f)}
    var pagePan by remember(frame.page) {mutableStateOf(Offset.Zero)}
    val latestObjects by rememberUpdatedState(objects)
    val latestSelected by rememberUpdatedState(selectedId)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestTransform by rememberUpdatedState(onTransform)
    val latestCommit by rememberUpdatedState(onCommit)
    val latestAllowTranslation by rememberUpdatedState(allowTranslation)
    val accent=MaterialTheme.colorScheme.primary.toArgb()
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("preview.bitmap").clipToBounds(),contentAlignment=Alignment.Center) {
            val aspect=frame.width.toFloat()/frame.height
            val width=minOf(maxWidth,560.dp,560.dp*aspect)
            Canvas(Modifier.size(width,width/aspect).testTag(tag).semantics {contentDescription=description}
                .graphicsLayer {scaleX=pageZoom;scaleY=pageZoom;translationX=pagePan.x;translationY=pagePan.y}
                .pointerInput(frame.page,frame.width,frame.height) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        fun local(obj:PreviewObject,point:Offset):Offset {
                            val center=Offset(obj.center.x*size.width,obj.center.y*size.height)
                            val delta=point-center;val angle=Math.toRadians(-obj.rotation.toDouble())
                            return Offset((delta.x*cos(angle)-delta.y*sin(angle)).toFloat(),(delta.x*sin(angle)+delta.y*cos(angle)).toFloat())
                        }
                        fun half(obj:PreviewObject)=Offset(obj.width*size.width/2,obj.height*size.height/2)
                        val hitRadius=24.dp.toPx()
                        var rotate=false;var corner=false
                        val obj=latestObjects.asReversed().firstOrNull {item ->
                            val point=local(item,down.position);val h=half(item)
                            val isSelected=item.id==latestSelected
                            val rotateHit=isSelected && (point-Offset(0f,-h.y-28.dp.toPx())).getDistance()<hitRadius
                            val cornerHit=isSelected && listOf(Offset(-h.x,-h.y),Offset(h.x,-h.y),Offset(-h.x,h.y),Offset(h.x,h.y))
                                .any {(point-it).getDistance()<hitRadius}
                            val body=abs(point.x)<=h.x && abs(point.y)<=h.y
                            if(rotateHit || cornerHit || body) {rotate=rotateHit;corner=cornerHit;true} else false
                        }
                        latestSelect(obj?.id)
                        var updated=obj
                        var previous=down.position
                        var changed=false
                        do {
                            val event=awaitPointerEvent()
                            val pressed=event.changes.count {it.pressed}
                            if(pressed==0) break
                            val pan=event.calculatePan();val zoom=event.calculateZoom();val turn=event.calculateRotation()
                            val current=updated
                            if(current!=null) {
                                var center=current.center
                                var w=current.width;var h=current.height;var angle=current.rotation
                                if(pressed>1) {
                                    if(latestAllowTranslation) center+=Offset(pan.x/size.width,pan.y/size.height)
                                    val scale=zoom.coerceIn(.5f,2f);w*=scale;h*=scale
                                    angle=normalizeWatermarkRotation(angle+turn)
                                } else {
                                    val point=event.changes.first().position
                                    val pixelCenter=Offset(center.x*size.width,center.y*size.height)
                                    when {
                                        rotate -> {
                                            val a=previous-pixelCenter;val b=point-pixelCenter
                                            angle=normalizeWatermarkRotation(angle+Math.toDegrees((atan2(b.y,b.x)-atan2(a.y,a.x)).toDouble()).toFloat())
                                        }
                                        corner -> {
                                            val ratio=((point-pixelCenter).getDistance()/(previous-pixelCenter).getDistance().coerceAtLeast(1f)).coerceIn(.5f,2f)
                                            w*=ratio;h*=ratio
                                        }
                                        else -> if(latestAllowTranslation) center+=Offset(pan.x/size.width,pan.y/size.height)
                                    }
                                    previous=point
                                }
                                val scale=minOf(1f/w.coerceAtLeast(.001f),1f/h.coerceAtLeast(.001f),1f)
                                w=(w*scale).coerceAtLeast(.025f);h=(h*scale).coerceAtLeast(.025f)
                                if(latestAllowTranslation) center=Offset(center.x.coerceIn(w/2,1-w/2),center.y.coerceIn(h/2,1-h/2))
                                updated=current.transform(center,w,h,angle)
                                if(updated!=current) {latestTransform(requireNotNull(updated));changed=true}
                                event.changes.forEach {it.consume()}
                            } else if(pressed>1 || pageZoom>1.01f) {
                                pageZoom=(pageZoom*zoom).coerceIn(1f,4f)
                                val maxX=size.width*(pageZoom-1)/2;val maxY=size.height*(pageZoom-1)/2
                                pagePan=Offset((pagePan.x+pan.x).coerceIn(-maxX,maxX),(pagePan.y+pan.y).coerceIn(-maxY,maxY))
                                event.changes.forEach {it.consume()}
                            }
                        } while(event.changes.any {it.pressed})
                        if(changed) latestCommit()
                    }
                }) {
                drawIntoCanvas {compose ->
                    val canvas=compose.nativeCanvas
                    canvas.save()
                    try {
                        canvas.scale(size.width/frame.width,size.height/frame.height)
                        canvas.drawBitmap(frame.bitmap,null,RectF(0f,0f,frame.width.toFloat(),frame.height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
                        objects.forEach {obj ->
                            if(!obj.bitmap.isRecycled) {
                                val boxW=obj.width*frame.width;val boxH=obj.height*frame.height
                                val fit=minOf(boxW/obj.bitmap.width,boxH/obj.bitmap.height)
                                val w=obj.bitmap.width*fit;val h=obj.bitmap.height*fit
                                canvas.save();canvas.translate(obj.center.x*frame.width,obj.center.y*frame.height);canvas.rotate(obj.rotation)
                                canvas.drawBitmap(obj.bitmap,null,RectF(-w/2,-h/2,w/2,h/2),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {alpha=(obj.opacity.coerceIn(0f,1f)*255).roundToInt()})
                                if(obj.id==selectedId) {
                                    val unit=frame.width/size.width
                                    val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=accent;style=Paint.Style.STROKE;strokeWidth=2.dp.toPx()*unit}
                                    val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=accent}
                                    canvas.drawRect(-boxW/2,-boxH/2,boxW/2,boxH/2,stroke)
                                    listOf(-boxW/2 to -boxH/2,boxW/2 to -boxH/2,-boxW/2 to boxH/2,boxW/2 to boxH/2).forEach {(x,y)->canvas.drawCircle(x,y,6.dp.toPx()*unit,fill)}
                                    val top=-boxH/2-28.dp.toPx()*unit
                                    canvas.drawLine(0f,-boxH/2,0f,top,stroke);canvas.drawCircle(0f,top,7.dp.toPx()*unit,fill)
                                }
                                canvas.restore()
                            }
                        }
                        pageNumber?.let {number->canvas.drawText(number.toString(),frame.width*.47f,frame.height*.96f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=10f;color=android.graphics.Color.rgb(102,102,102)})}
                    } finally {canvas.restore()}
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(selectedId!=null) TextButton(onClick={onSelect(null)},modifier=Modifier.testTag("$tag.deselect")) {Text(stringResource(R.string.studio_watermark_deselect))}
            if(pageZoom>1.01f) TextButton(onClick={pageZoom=1f;pagePan=Offset.Zero}) {Text(stringResource(R.string.ui_preview_reset_zoom))}
        }
    }
}
