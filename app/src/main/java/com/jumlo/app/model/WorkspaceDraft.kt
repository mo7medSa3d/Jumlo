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
    val wifiOnly:Boolean=false,val unmeteredOnly:Boolean=false,val cleartextConsent:Boolean=false,
    val text1:String="",val text2:String="",val text3:String="",val text4:String="",
    val num1:String="",val num2:String="",val num3:String="",val num4:String="",
    val flag1:Boolean=false,val flag2:Boolean=false,
    val secret1:String="",val secret2:String=""
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
            "pdf_03" -> Json.encodeToString(com.jumlo.app.data.processing.PdfMergeSettings(SafeNames.basename(outputName)))
            "pdf_05" -> Json.encodeToString(com.jumlo.app.data.processing.PdfSplitSettings(SafeNames.basename(outputName),if(flag1) "EVERY" else "RANGES",pages,if(flag1) number(num1.ifBlank{"1"},1,1000) else 1))
            "pdf_06" -> Json.encodeToString(com.jumlo.app.data.processing.PdfExtractSettings(SafeNames.basename(outputName),pages))
            "pdf_04" -> Json.encodeToString(com.jumlo.app.data.processing.PdfOrganizeSettings(SafeNames.basename(outputName),text1,text2,number(num1.ifBlank{"90"},-360,360)))
            "pdf_crop" -> Json.encodeToString(com.jumlo.app.data.processing.PdfCropSettings(SafeNames.basename(outputName),pages,fraction(cropLeft),fraction(cropTop),fraction(cropRight),fraction(cropBottom)))
            "pdf_compress" -> Json.encodeToString(com.jumlo.app.data.processing.PdfCompressSettings(SafeNames.basename(outputName),number(num1.ifBlank{"150"},36,600),number(quality,1,100)))
            "pdf_stamp" -> Json.encodeToString(com.jumlo.app.data.processing.PdfStampSettings(SafeNames.basename(outputName),text1,flag1,pages))
            "pdf_annotate" -> Json.encodeToString(com.jumlo.app.data.processing.PdfAnnotateSettings(SafeNames.basename(outputName),text1,number(num1.ifBlank{"1"},1,100000),fraction(num2.ifBlank{"0.1"}),fraction(num3.ifBlank{"0.1"}),flag1))
            "pdf_signature" -> Json.encodeToString(com.jumlo.app.data.processing.PdfSignatureSettings(SafeNames.basename(outputName),number(num1.ifBlank{"1"},1,100000),fraction(cropLeft),fraction(cropTop),fraction(cropRight),fraction(cropBottom)))
            "pdf_metadata" -> Json.encodeToString(com.jumlo.app.data.processing.PdfMetadataSettings(SafeNames.basename(outputName),text1,text2,text3,text4))
            "pdf_protect" -> Json.encodeToString(com.jumlo.app.data.processing.PdfProtectSettings(SafeNames.basename(outputName),downloadHandle ?: "",flag1))
            "pdf_forms" -> Json.encodeToString(com.jumlo.app.data.processing.PdfFormSettings(SafeNames.basename(outputName),text1.ifBlank{"{}"},flag1))
            "pdf_compare" -> Json.encodeToString(com.jumlo.app.data.processing.PdfCompareSettings())
            "pdf_redact" -> Json.encodeToString(com.jumlo.app.data.processing.PdfRedactSettings(SafeNames.basename(outputName),number(num1.ifBlank{"1"},1,100000),fraction(cropLeft),fraction(cropTop),fraction(cropRight),fraction(cropBottom),text1))
            "pdf_ocr" -> Json.encodeToString(com.jumlo.app.data.processing.PdfImagesSettings(pages,number(dpi,36,600),"PNG",number(quality,1,100),grayscale))
            "webpage_pdf" -> Json.encodeToString(com.jumlo.app.data.processing.PdfImagesSettings(text1,144,"PNG",90,false))
            "img_exif" -> Json.encodeToString(com.jumlo.app.data.processing.ImageExifSettings(flag2,flag1,converted))
            "img_watermark" -> Json.encodeToString(com.jumlo.app.data.processing.ImageWatermarkSettings(text1,number(num1.ifBlank{"40"},1,100),flag1,converted))
            "img_join","img_stitch" -> Json.encodeToString(com.jumlo.app.data.processing.ImageJoinSettings(if(toolId=="img_stitch") "VSTRIP" else text1.ifBlank{"GRID2"},number(num1.ifBlank{"8"},0,500),converted))
            "img_adjust" -> Json.encodeToString(com.jumlo.app.data.processing.ImageAdjustSettings(number(num1.ifBlank{"0"},-100,100),number(num2.ifBlank{"100"},0,300),number(num3.ifBlank{"100"},0,300),number(num4.ifBlank{"0"},0,2000),converted))
            "img_target" -> Json.encodeToString(com.jumlo.app.data.processing.ImageTargetSettings(number(num1.ifBlank{"200000"},1000,100000000).toLong(),converted))
            "img_compare" -> Json.encodeToString(com.jumlo.app.data.processing.ImageAdjustSettings())
            "file_batch_rename" -> Json.encodeToString(com.jumlo.app.data.processing.BatchRenameSettings(text1.ifBlank{"file-{n}"},number(num1.ifBlank{"1"},0,1000000)))
            "file_duplicates","file_trash" -> Json.encodeToString(com.jumlo.app.data.processing.HashSettings(expectedHash.trim().takeIf { it.isNotEmpty() }))
            "file_encrypted_zip" -> Json.encodeToString(com.jumlo.app.data.processing.EncryptedArchiveSettings(SafeNames.basename(outputName),downloadHandle ?: "",flag1))
            "dl_02","dl_03" -> Json.encodeToString(DownloadSettings(requireNotNull(downloadHandle),SafeNames.basename(outputName),wifiOnly,unmeteredOnly,cleartextConsent))
            else -> throw IllegalArgumentException("CAPABILITY_UNAVAILABLE")
        }
    }
    companion object {
        fun normalizeDigits(text:String):String=text.trim().map { c -> when(c) { in '\u0660'..'\u0669' -> '0'+(c-'\u0660');in '\u06f0'..'\u06f9' -> '0'+(c-'\u06f0');'\u066b' -> '.';else->c } }.joinToString("")
        fun forTool(id:String,sources:List<String>)=WorkspaceDraft(toolId=id,sourceIds=sources,outputName=when(id) { "pdf_02"->"Images.pdf";"file_07","file_08"->"Archive.zip";else->"download.bin" },format=if(id=="pdf_07") "PNG" else "JPEG",prefix=when(id) { "img_02"->"resized_";"img_03"->"cropped_";"img_04"->"compressed_";else->"converted_" },quality=if(id=="img_04") "75" else "85")
    }
}
