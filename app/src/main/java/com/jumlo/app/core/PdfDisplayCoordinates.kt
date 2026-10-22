package com.jumlo.app.core

/** PDF affine matrix: x'=a*x+c*y+e, y'=b*x+d*y+f. */
data class PdfDisplayMatrix(val a: Double,val b: Double,val c: Double,val d: Double,val e: Double,val f: Double) {
    fun map(x:Double,y:Double):Pair<Double,Double> = (a*x+c*y+e) to (b*x+d*y+f)
}

data class PdfDisplayFrame(val widthPt:Double,val heightPt:Double,val displayToPage:PdfDisplayMatrix)

/**
 * The upright visible CropBox frame uses physical PDF points, with its origin at
 * the displayed bottom left. Normalized UI positions have a top-left origin.
 * /Rotate is clockwise; /UserUnit scales raw page units to physical points.
 */
object PdfDisplayCoordinates {
    /** Axis-aligned raw page bounds for a rectangle in the oriented physical frame. */
    fun pageRectangle(frame:PdfDisplayFrame,xPt:Double,yPt:Double,widthPt:Double,heightPt:Double):PdfCropRect {
        require(listOf(xPt,yPt,widthPt,heightPt).all { it.isFinite() } && widthPt>0 && heightPt>0) { "INVALID_PDF_PAGE_GEOMETRY" }
        val corners=listOf(xPt to yPt,(xPt+widthPt) to yPt,xPt to (yPt+heightPt),(xPt+widthPt) to (yPt+heightPt))
            .map { (x,y) -> frame.displayToPage.map(x,y) }
        val minX=corners.minOf { it.first };val minY=corners.minOf { it.second }
        val width=corners.maxOf { it.first }-minX;val height=corners.maxOf { it.second }-minY
        val result=PdfCropRect(minX.toFloat(),minY.toFloat(),width.toFloat(),height.toFloat())
        require(listOf(result.x,result.y,result.width,result.height).all { it.isFinite() } && result.width>0 && result.height>0) { "INVALID_PDF_PAGE_GEOMETRY" }
        return result
    }

    fun normalizedRectangle(frame:PdfDisplayFrame,left:Double,top:Double,right:Double,bottom:Double):PdfCropRect {
        Box(left,top,right,bottom).normalized()
        return pageRectangle(frame,left*frame.widthPt,(1.0-bottom)*frame.heightPt,
            (right-left)*frame.widthPt,(bottom-top)*frame.heightPt)
    }

    /**
     * The four corners of a normalized rectangle in page space, ordered the way a
     * PDF markup annotation expects them: upper-left, upper-right, lower-left,
     * lower-right. The corners go through the same rotation and user-unit mapping as
     * the rectangle, so a quad always matches its bounding box.
     */
    fun normalizedCorners(frame:PdfDisplayFrame,left:Double,top:Double,right:Double,bottom:Double):FloatArray {
        Box(left,top,right,bottom).normalized()
        val x0=left*frame.widthPt; val x1=right*frame.widthPt
        val yTop=(1.0-top)*frame.heightPt; val yBottom=(1.0-bottom)*frame.heightPt
        val corners=listOf(x0 to yTop,x1 to yTop,x0 to yBottom,x1 to yBottom)
            .map { (x,y) -> frame.displayToPage.map(x,y) }
        require(corners.all { it.first.isFinite() && it.second.isFinite() }) { "INVALID_PDF_PAGE_GEOMETRY" }
        return floatArrayOf(
            corners[0].first.toFloat(),corners[0].second.toFloat(),
            corners[1].first.toFloat(),corners[1].second.toFloat(),
            corners[2].first.toFloat(),corners[2].second.toFloat(),
            corners[3].first.toFloat(),corners[3].second.toFloat()
        )
    }

    fun frame(left:Double,bottom:Double,width:Double,height:Double,rotationDegrees:Int,userUnit:Double=1.0):PdfDisplayFrame {
        require(listOf(left,bottom,width,height,userUnit).all { it.isFinite() } && width>0 && height>0 && userUnit>0) { "INVALID_PDF_PAGE_GEOMETRY" }
        require(rotationDegrees%90==0) { "INVALID_ROTATION" }
        val rotation=((rotationDegrees%360)+360)%360
        val unit=1.0/userUnit
        val matrix=when(rotation) {
            90 -> PdfDisplayMatrix(0.0,unit,-unit,0.0,left+width,bottom)
            180 -> PdfDisplayMatrix(-unit,0.0,0.0,-unit,left+width,bottom+height)
            270 -> PdfDisplayMatrix(0.0,-unit,unit,0.0,left,bottom+height)
            else -> PdfDisplayMatrix(unit,0.0,0.0,unit,left,bottom)
        }
        val displayedWidth=(if(rotation%180==0) width else height)*userUnit
        val displayedHeight=(if(rotation%180==0) height else width)*userUnit
        require(displayedWidth.isFinite() && displayedHeight.isFinite() && listOf(matrix.a,matrix.b,matrix.c,matrix.d,matrix.e,matrix.f).all { it.isFinite() }) { "INVALID_PDF_PAGE_GEOMETRY" }
        return PdfDisplayFrame(displayedWidth,displayedHeight,matrix)
    }
}
