package com.jumlo.app.model

import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.processing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class OfficeExportSettings(val displayName:String, val pages:String="all")

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
    val zipLevel:String="6",
    val wifiOnly:Boolean=false,val unmeteredOnly:Boolean=false,val cleartextConsent:Boolean=true,
    val text1:String="",val text2:String="",val text3:String="",val text4:String="",
    val num1:String="",val num2:String="",val num3:String="",val num4:String="",
    val flag1:Boolean=false,val flag2:Boolean=false,
    val secret1:String="",val secret2:String="",val paperMode:String="AUTO",
    val allowPrinting:Boolean=true,val allowCopy:Boolean=true,val allowModify:Boolean=false,
    val allowAnnotations:Boolean=true,val allowForms:Boolean=true,
    val renameDate:String="",
    val watermarkFont:String="sans",val watermarkUseImage:Boolean=false,val watermarkScale:Double=1.0,
    val signaturePlacements:List<PdfSignaturePlacement> = emptyList(),val signatureRotationDegrees:Float=0f,
    val cropOverrides:List<PdfCropOverride> = emptyList(),
    val pageEdits:List<com.jumlo.app.data.processing.PdfPageEdit> = emptyList()
) {
    fun executionSettings(downloadHandle:String?=null):String {
        require(version==1) { "UNSUPPORTED_DRAFT_VERSION" }
        fun number(text:String,min:Int,max:Int):Int=normalizeDigits(text).toIntOrNull()?.takeIf { it in min..max } ?: throw IllegalArgumentException("INVALID_NUMERIC_SETTING")
        fun fraction(text:String):Double=normalizeDigits(text).toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: throw IllegalArgumentException("INVALID_CROP")
        val customImgName = outputName.trim().takeUnless { it == "download.bin" || it == "Images.pdf" }.orEmpty()
        val converted by lazy { ImageConversionSettings(format,if(format=="PNG") 85 else number(quality,1,100),-1,true,prefix,customImgName) }
        return when(toolId) {
            "pdf_to_docx","pdf_to_pptx" -> Json.encodeToString(OfficeExportSettings(SafeNames.basename(outputName),pages.trim().ifBlank {"all"}))
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
            "pdf_02" -> Json.encodeToString(ImagesToPdfSettings(SafeNames.basename(outputName),number(paperWidth,72,14400),number(paperHeight,72,14400),orientation,number(margin,0,200),fill,paperMode,number(quality,1,100),flag1))
            "pdf_07","pdf_long_image" -> Json.encodeToString(PdfImagesSettings(pages.trim().ifBlank{"all"},number(dpi.ifBlank{"144"},36,600),if(format=="JPEG") "JPEG" else "PNG",if(format=="JPEG") number(quality,1,100) else 85,grayscale,outputName.trim()))
            "file_07","file_08" -> Json.encodeToString(ArchiveSettings(SafeNames.basename(outputName),number(zipLevel,0,9)))
            "pdf_03" -> Json.encodeToString(com.jumlo.app.data.processing.PdfMergeSettings(SafeNames.basename(outputName)))
            "pdf_05" -> Json.encodeToString(com.jumlo.app.data.processing.PdfSplitSettings(SafeNames.basename(outputName),if(flag1) "EVERY" else if(flag2) "BOUNDARIES" else "RANGES",if(flag2) pages else pages.ifBlank{"all"},if(flag1) number(num1.ifBlank{"1"},1,1000) else 1))
            "pdf_06" -> Json.encodeToString(com.jumlo.app.data.processing.PdfExtractSettings(SafeNames.basename(outputName),pages.ifBlank{"all"}))
            "pdf_04" -> Json.encodeToString(com.jumlo.app.data.processing.PdfOrganizeSettings(SafeNames.basename(outputName),text1,text2,if(pageEdits.isEmpty()) number(num1.ifBlank{"90"},-360,360) else 90,pageEdits))
            "pdf_crop" -> {
                require(cropOverrides.size<=10000 && cropOverrides.map {it.page}.distinct().size==cropOverrides.size) { "INVALID_CROP" }
                cropOverrides.forEach {crop ->
                    require(crop.page in 1..100000) { "INVALID_PAGE_INDEX" }
                    com.jumlo.app.core.Box(crop.left,crop.top,crop.right,crop.bottom).normalized()
                }
                Json.encodeToString(com.jumlo.app.data.processing.PdfCropSettings(SafeNames.basename(outputName),pages.ifBlank{"all"},fraction(cropLeft),fraction(cropTop),fraction(cropRight),fraction(cropBottom),cropOverrides))
            }
            "pdf_compress" -> Json.encodeToString(com.jumlo.app.data.processing.PdfCompressSettings(SafeNames.basename(outputName),number(num1.ifBlank{"150"},36,600),number(quality,1,100),if(num2.isBlank()||normalizeDigits(num2)=="0") 0L else number(num2,1,4096).toLong()*1024L*1024L))
            "pdf_stamp" -> {
                require(text1.isNotBlank() || flag1 || watermarkUseImage) { "STAMP_CONTENT_REQUIRED" }
                val style = com.jumlo.app.core.PdfWatermarkStyle(
                    centerX = fraction(num2.ifBlank{"0.5"}),
                    centerY = fraction(num3.ifBlank{"0.5"}),
                    fontSizePt = number(num1.ifBlank{"42"},12,90),
                    opacityPercent = number(num4.ifBlank{"28"},5,100),
                    rotationDegrees = number(text4.ifBlank{"-35"},-180,180),
                    tiled = text2 == "TILE",
                    colorHex = text3.ifBlank{"#64748B"},
                    fontFamily = watermarkFont,
                    imageScale = watermarkScale
                )
                Json.encodeToString(com.jumlo.app.data.processing.PdfStampSettings(
                    SafeNames.basename(outputName),text1,flag1,pages.ifBlank{"all"},
                    style.centerX,style.centerY,style.fontSizePt,style.opacityPercent,
                    style.rotationDegrees,style.tiled,style.colorHex,style.fontFamily,watermarkUseImage,style.imageScale))
            }
            "pdf_signature" -> {
                require(signatureRotationDegrees.isFinite() && signatureRotationDegrees in -180f..180f) { "INVALID_ROTATION" }
                require(signaturePlacements.size<=1000) { "TOO_MANY_SIGNATURES" }
                signaturePlacements.forEach { placement ->
                    require(placement.page in 1..100000 && placement.rotationDegrees.isFinite() && placement.rotationDegrees in -180f..180f) { "INVALID_SIGNATURE_PLACEMENT" }
                    com.jumlo.app.core.Box(placement.left,placement.top,placement.right,placement.bottom).normalized()
                }
                Json.encodeToString(com.jumlo.app.data.processing.PdfSignatureSettings(SafeNames.basename(outputName),number(num1.ifBlank{"1"},1,100000),fraction(cropLeft),fraction(cropTop),fraction(cropRight),fraction(cropBottom),signatureRotationDegrees,signaturePlacements))
            }
            "pdf_protect" -> Json.encodeToString(com.jumlo.app.data.processing.PdfProtectSettings(
                SafeNames.basename(outputName),downloadHandle ?: "",flag1,
                allowPrinting,allowCopy,allowModify,allowAnnotations,allowForms))
            "office_to_pdf" -> Json.encodeToString(com.jumlo.app.data.processing.OfficeToPdfSettings(SafeNames.basename(outputName)))
            "pdf_nup" -> Json.encodeToString(com.jumlo.app.data.processing.PdfNupSettings(SafeNames.basename(outputName),if(num1=="4") 4 else 2,grayscale))
            "pdf_privacy_redact" -> Json.encodeToString(com.jumlo.app.data.processing.PdfRedactSettings(SafeNames.basename(outputName),pages.ifBlank{"1"},fraction(cropLeft),fraction(cropTop),fraction(cropRight),fraction(cropBottom),!flag1,cropOverrides))
            "pdf_ocr" -> Json.encodeToString(com.jumlo.app.data.processing.PdfImagesSettings(pages.ifBlank{"all"},number(dpi.ifBlank{"144"},36,600),"PNG",number(quality,1,100),grayscale,SafeNames.fromDisplayName(outputName.ifBlank{"searchable.pdf"},"searchable.pdf"),text1.ifBlank{"AUTO"}))
            "img_watermark" -> {
                if(flag1) require(sourceIds.distinct().size>=2) { "WATERMARK_LOGO_REQUIRED" }
                else require(text1.isNotBlank()) { "WATERMARK_CONTENT_REQUIRED" }
                com.jumlo.app.core.OverlayStylePolicy.requireFont(watermarkFont)
                com.jumlo.app.core.OverlayStylePolicy.requireColor(text3.ifBlank{"#FFFFFF"})
                require(watermarkScale.isFinite() && watermarkScale in .1..3.0) { "INVALID_WATERMARK_SIZE" }
                Json.encodeToString(com.jumlo.app.data.processing.ImageWatermarkSettings(text1,number(num1.ifBlank{"40"},1,100),flag1,converted,
                    fraction(num2.ifBlank{"0.5"}),fraction(num3.ifBlank{"0.5"}),watermarkScale,number(text4.ifBlank{"0"},-180,180),text2=="TILE",text3.ifBlank{"#FFFFFF"},watermarkFont))
            }
            "img_join" -> Json.encodeToString(com.jumlo.app.data.processing.ImageJoinSettings(text1.ifBlank{"GRID2"},number(num1.ifBlank{"8"},0,500),0,converted))
            "file_batch_rename" -> {
                val pattern=text1.ifBlank{"file-{n}"}
                require(pattern.length<=200) { "INVALID_RENAME_PATTERN" }
                Json.encodeToString(com.jumlo.app.data.processing.BatchRenameSettings(pattern,number(num1.ifBlank{"1"},0,1000000),renameDate))
            }
            "file_encrypted_zip" -> Json.encodeToString(com.jumlo.app.data.processing.EncryptedArchiveSettings(SafeNames.basename(outputName),downloadHandle ?: "",flag1))
            "dl_02","dl_03" -> Json.encodeToString(DownloadSettings(requireNotNull(downloadHandle),SafeNames.fromDisplayName(outputName.ifBlank{"download.bin"},"download.bin"),wifiOnly,unmeteredOnly,cleartextConsent))
            else -> throw IllegalArgumentException("CAPABILITY_UNAVAILABLE")
        }
    }
    companion object {
        fun normalizeDigits(text:String):String=text.trim().map { c -> when(c) { in '\u0660'..'\u0669' -> '0'+(c-'\u0660');in '\u06f0'..'\u06f9' -> '0'+(c-'\u06f0');'\u066b' -> '.';else->c } }.joinToString("")
        fun forTool(id:String,sources:List<String>)=WorkspaceDraft(toolId=id,sourceIds=sources,outputName=when(id) { "pdf_to_docx"->"Document.docx";"pdf_to_pptx"->"Document.pptx";"pdf_ocr"->"Extracted.txt";"pdf_02"->"Images.pdf";"pdf_03"->"Merged.pdf";"pdf_04"->"Organized.pdf";"pdf_05"->"Split.pdf";"pdf_06"->"Extracted.pdf";"pdf_long_image"->"Long.png";"pdf_crop","pdf_compress","pdf_stamp","pdf_signature","pdf_protect","pdf_nup","pdf_privacy_redact","office_to_pdf"->"Document.pdf";"file_07","file_08","file_encrypted_zip"->"Archive.zip";else->"download.bin" },format=if(id=="pdf_07" || id=="pdf_long_image") "PNG" else "JPEG",prefix=when(id) { "img_02"->"resized_";"img_03"->"cropped_";"img_04"->"compressed_";else->"converted_" },quality=if(id=="img_04") "75" else "85").let { initial ->
            when(id) {
                "pdf_signature" -> initial.copy(cropLeft="0.6",cropTop="0.75",cropRight="0.95",cropBottom="0.95")
                "pdf_compress" -> initial.copy(num1="180",quality="75")
                "file_batch_rename" -> initial.copy(renameDate=java.time.LocalDate.now().toString())
                else -> initial
            }
        }
    }
}
