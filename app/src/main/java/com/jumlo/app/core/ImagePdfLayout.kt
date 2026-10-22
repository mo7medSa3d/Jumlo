package com.jumlo.app.core

data class ImagePdfPlacement(val pageWidthPt:Float,val pageHeightPt:Float,val marginPt:Float,
    val leftPt:Float,val topPt:Float,val widthPt:Float,val heightPt:Float)

/** Shared physical page placement. UI direction and display density never alter document geometry. */
object ImagePdfLayout {
    fun resolve(imageWidth:Int,imageHeight:Int,page:PdfPageSize,marginMm:Int,automatic:Boolean,fill:Boolean):ImagePdfPlacement {
        require(imageWidth>0 && imageHeight>0 && marginMm in 0..200) { "INVALID_PDF_SETTINGS" }
        val w=page.widthPt; val h=page.heightPt
        val requested=marginMm*72f/25.4f
        val margin=if(automatic) minOf(requested,minOf(w,h)*.08f) else requested
        // A margin larger than the page is clamped instead of failing the whole run.
        val usable=if(automatic) margin else minOf(margin,(minOf(w,h)-1f)/2f)
        require(usable>=0f && usable*2<minOf(w,h)) { "INVALID_PDF_SETTINGS" }
        val sx=(w-2*usable)/imageWidth; val sy=(h-2*usable)/imageHeight
        val scale=if(fill) maxOf(sx,sy) else minOf(sx,sy)
        val dw=imageWidth*scale; val dh=imageHeight*scale
        return ImagePdfPlacement(w,h,usable,(w-dw)/2,(h-dh)/2,dw,dh)
    }
}
