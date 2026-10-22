package com.jumlo.app.core

data class ScanPoint(val x:Float,val y:Float)
data class ScanImageRect(val left:Float,val top:Float,val width:Float,val height:Float) {
    fun normalized(x:Float,y:Float)=ScanPoint(((x-left)/width).coerceIn(0f,1f),((y-top)/height).coerceIn(0f,1f))
}
object ScanGeometry {
    fun fit(imageWidth:Float,imageHeight:Float,viewportWidth:Float,viewportHeight:Float):ScanImageRect {
        require(listOf(imageWidth,imageHeight,viewportWidth,viewportHeight).all { it.isFinite() && it>0f })
        val scale=minOf(viewportWidth/imageWidth,viewportHeight/imageHeight)
        val width=imageWidth*scale; val height=imageHeight*scale
        return ScanImageRect((viewportWidth-width)/2f,(viewportHeight-height)/2f,width,height)
    }
    fun valid(points:List<ScanPoint>):Boolean {
        if(points.size!=4 || points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
        return points.indices.all { i ->
            val a=points[i]; val b=points[(i+1)%4]; val c=points[(i+2)%4]
            (b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x)>.001f
        }
    }
}
