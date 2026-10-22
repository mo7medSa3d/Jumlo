package com.jumlo.app.ui.workspace

import com.jumlo.app.core.PdfInkDrawing

/** Pending input stays separate from committed paths until the pointer is released. */
internal data class InkStrokeCapture(
    val maxPoints:Int=PdfInkDrawing.MAX_POINTS_PER_STROKE,
    val points:List<Float> = emptyList(),
    val limitReached:Boolean=false
) {
    fun append(x:Float,y:Float):InkStrokeCapture {
        if(!x.isFinite() || !y.isFinite()) return this
        if(points.size/2>=maxPoints) return copy(limitReached=true)
        return copy(points=points+listOf(x.coerceIn(0f,1f),y.coerceIn(0f,1f)))
    }
    fun completedStroke():List<Float>?=points.takeIf {it.size>=4}
    fun commitTo(strokes:List<List<Float>>):List<List<Float>> =
        completedStroke()?.let {PdfInkDrawing.append(strokes,it)} ?: strokes
    fun cancel()=copy(points=emptyList(),limitReached=false)
}
