package com.jumlo.app.data.processing

import kotlinx.serialization.Serializable

/** One output page instance. Source pages are one based; rotation is relative clockwise. */
@Serializable
data class PdfPageEdit(val instanceId:String,val sourcePage:Int,val rotationDegrees:Int=0)
