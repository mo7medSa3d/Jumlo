package com.jumlo.app.model

import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.processing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Editor text is versioned separately from the validated immutable execution request. */
@Serializable data class WorkspaceDraft(
    val version:Int=1,
    val toolId:String="pdf_02",
    val sourceIds:List<String> = emptyList(),
    val outputName:String="Images.pdf",
    val format:String="JPEG", val quality:String="85", val prefix:String="converted_",
    val width:String="1600",val height:String="1600",val percentage:String="",val longestEdge:String="",
    val fit:String="FIT",val allowUpscaling:Boolean=false,
    val cropLeft:String="0",val cropTop:String="0",val cropRight:String="1",val cropBottom:String="1",
    val turns:Int=0,val flipX:Boolean=false,val flipY:Boolean=false,
    val paperWidth:String="595",val paperHeight:String="842",val orientation:String="AUTO",val margin:String="10",val fill:Boolean=false,
    val pages:String="all",val dpi:String="144",val grayscale:Boolean=false,
    val zipLevel:String="6",val expectedHash:String="",
    val wifiOnly:Boolean=false,val unmeteredOnly:Boolean=false,val cleartextConsent:Boolean=false
) {
    fun executionSettings(downloadHandle:String?=null):String {
        require(version==1) { "UNSUPPORTED_DRAFT_VERSION" }
        fun number(text:String,min:Int,max:Int):Int=normalizeDigits(text).toIntOrNull()?.takeIf { it in min..max } ?: throw IllegalArgumentException("INVALID_NUMERIC_SETTING")
        fun fraction(text:String):Double=normalizeDigits(text).toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: throw IllegalArgumentException("INVALID_CROP")
        val converted by lazy { ImageConversionSettings(format,number(quality,1,100),-1,true,prefix) }
        return when(toolId) {
            "img_01" -> Json.encodeToString(converted)
            "img_02" -> {
                val percent=percentage.takeIf { it.isNotBlank() }?.let { number(it,1,400) }
                val edge=longestEdge.takeIf { it.isNotBlank() }?.let { number(it,1,30000) }
                require(percent==null || edge==null) { "CHOOSE_ONE_RESIZE_MODE" }
                val pixels=percent==null && edge==null
                Json.encodeToString(ImageResizeSettings(if(pixels) number(width,1,30000) else 1,if(pixels) number(height,1,30000) else 1,percent,edge,fit,allowUpscaling,converted))
            }
            "img_03" -> { val l=fraction(cropLeft);val t=fraction(cropTop);val r=fraction(cropRight);val b=fraction(cropBottom); require(l<r && t<b) { "INVALID_CROP" }; Json.encodeToString(ImageCropSettings(l,t,r,b,turns,flipX,flipY,converted)) }
            "img_04" -> Json.encodeToString(ImageCompressionSettings(converted))
            "pdf_02" -> Json.encodeToString(ImagesToPdfSettings(SafeNames.basename(outputName),number(paperWidth,72,14400),number(paperHeight,72,14400),orientation,number(margin,0,200),fill))
            "pdf_07" -> Json.encodeToString(PdfImagesSettings(pages.trim(),number(dpi,36,600),if(format=="JPEG") "JPEG" else "PNG",number(quality,1,100),grayscale))
            "file_07","file_08" -> Json.encodeToString(ArchiveSettings(SafeNames.basename(outputName),number(zipLevel,0,9)))
            "sha256" -> Json.encodeToString(HashSettings(expectedHash.trim().takeIf { it.isNotEmpty() }))
            "dl_02","dl_03" -> Json.encodeToString(DownloadSettings(requireNotNull(downloadHandle),SafeNames.basename(outputName),wifiOnly,unmeteredOnly,cleartextConsent))
            else -> throw IllegalArgumentException("CAPABILITY_UNAVAILABLE")
        }
    }
    companion object {
        fun normalizeDigits(text:String):String=text.trim().map { c -> when(c) { in '\u0660'..'\u0669' -> '0'+(c-'\u0660');in '\u06f0'..'\u06f9' -> '0'+(c-'\u06f0');'\u066b' -> '.';else->c } }.joinToString("")
        fun forTool(id:String,sources:List<String>)=WorkspaceDraft(toolId=id,sourceIds=sources,outputName=when(id) { "pdf_02"->"Images.pdf";"file_07","file_08"->"Archive.zip";else->"download.bin" },format=if(id=="pdf_07") "PNG" else "JPEG",prefix=when(id) { "img_02"->"resized_";"img_03"->"cropped_";"img_04"->"compressed_";else->"converted_" },quality=if(id=="img_04") "75" else "85")
    }
}
