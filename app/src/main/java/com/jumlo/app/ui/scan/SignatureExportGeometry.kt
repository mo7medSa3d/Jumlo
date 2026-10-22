package com.jumlo.app.ui.scan

import kotlin.math.roundToInt

internal data class SignatureExportGeometry(val width:Int,val height:Int,val scale:Float)

/** A single scale preserves the signature shape and stroke weight in the exported PNG. */
internal fun signatureExportGeometry(width:Int,height:Int):SignatureExportGeometry {
    require(width>0 && height>0)
    val scale=600f/maxOf(width,height)
    return SignatureExportGeometry((width*scale).roundToInt().coerceAtLeast(1),
        (height*scale).roundToInt().coerceAtLeast(1),scale)
}
