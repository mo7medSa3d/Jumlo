package com.jumlo.app.ui.adaptive

/** Physical window pixels, never logical start/end coordinates. */
data class PixelBounds(val left:Int,val top:Int,val right:Int,val bottom:Int) {
    val width:Int get()=(right-left).coerceAtLeast(0)
    val height:Int get()=(bottom-top).coerceAtLeast(0)
    val area:Long get()=width.toLong()*height
}
data class HingeBounds(val bounds:PixelBounds,val vertical:Boolean)

/** Keep one contiguous usable pane. This deliberately favors legibility over dual-pane density. */
fun safeViewport(root:PixelBounds,hinges:List<HingeBounds>,rtl:Boolean):PixelBounds {
    var pane=root
    for(feature in hinges) {
        val h=feature.bounds
        if(feature.vertical) {
            if(h.bottom<=pane.top || h.top>=pane.bottom || h.right<=pane.left || h.left>=pane.right) continue
            val left=pane.copy(right=h.left.coerceIn(pane.left,pane.right))
            val right=pane.copy(left=h.right.coerceIn(pane.left,pane.right))
            pane=when {left.area>right.area->left;right.area>left.area->right;rtl->right;else->left}
        } else {
            if(h.right<=pane.left || h.left>=pane.right || h.bottom<=pane.top || h.top>=pane.bottom) continue
            val top=pane.copy(bottom=h.top.coerceIn(pane.top,pane.bottom))
            val bottom=pane.copy(top=h.bottom.coerceIn(pane.top,pane.bottom))
            pane=if(top.area>=bottom.area) top else bottom
        }
    }
    return pane
}
