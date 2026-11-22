package com.jumlo.app.data.processing
import kotlinx.serialization.Serializable

@Serializable data class ImageConversionSettings(val format:String="JPEG",val quality:Int=95,val matteArgb:Int=-1,val stripMetadata:Boolean=true,val namePrefix:String="converted_",val customOutputName:String="")
@Serializable data class ImageResizeSettings(val width:Int=1600,val height:Int=1600,val percentage:Int?=null,val longestEdge:Int?=null,val fit:String="FIT",val allowUpscaling:Boolean=false,val output:ImageConversionSettings=ImageConversionSettings(quality=98,namePrefix="resized_"))
@Serializable data class ImageCropSettings(val left:Double=0.0,val top:Double=0.0,val right:Double=1.0,val bottom:Double=1.0,val rotationQuarterTurns:Int=0,val flipX:Boolean=false,val flipY:Boolean=false,val output:ImageConversionSettings=ImageConversionSettings(quality=98,namePrefix="cropped_"))
@Serializable data class ImageCompressionSettings(val output:ImageConversionSettings=ImageConversionSettings(quality=75,namePrefix="compressed_"),val left:Double=0.0,val top:Double=0.0,val right:Double=1.0,val bottom:Double=1.0,val rotationQuarterTurns:Int=0,val flipX:Boolean=false,val flipY:Boolean=false)
@Serializable data class ImagesToPdfSettings(val displayName:String="Images.pdf",val paperWidthPt:Int=595,val paperHeightPt:Int=842,val orientation:String="AUTO",val marginMm:Int=10,val fill:Boolean=false,val pageMode:String="AUTO",val quality:Int=85,val lossless:Boolean=false)
@Serializable data class PdfImagesSettings(val pages:String="all",val dpi:Int=200,val format:String="PNG",val quality:Int=95,val grayscale:Boolean=false,val displayName:String="",val ocrScript:String="LATIN",val keepOriginalImageQuality:Boolean=true,val separateImages:Boolean=false)
@Serializable data class ArchiveSettings(val displayName:String="Archive.zip",val compressionLevel:Int=6,val password:String?=null)
@Serializable data class DownloadSettings(
    val urlSecretHandle:String,
    val displayName:String="download.bin",
    val wifiOnly:Boolean=false,
    val unmeteredOnly:Boolean=false,
    val cleartextConsent:Boolean=false,
    val media:MediaSelectionSettings?=null,
    val directMode:String="ORIGINAL"
)

@Serializable data class PdfMergeSettings(val displayName:String="Merged.pdf")
@Serializable data class PdfSplitSettings(val displayName:String="Split.pdf", val mode:String="RANGES", val pages:String="1", val every:Int=1)
@Serializable data class PdfOrganizeSettings(val displayName:String="Organized.pdf", val order:String="", val rotatePages:String="", val rotateDegrees:Int=90, val pageEdits:List<PdfPageEdit> = emptyList())
@Serializable data class PdfExtractSettings(val displayName:String="Extracted.pdf", val pages:String="1")
@Serializable data class PdfCropOverride(val page:Int, val left:Double, val top:Double, val right:Double, val bottom:Double)
@Serializable data class PdfCropSettings(val displayName:String="Cropped.pdf", val pages:String="all", val left:Double=0.0, val top:Double=0.0, val right:Double=1.0, val bottom:Double=1.0, val overrides:List<PdfCropOverride> = emptyList())
@Serializable data class PdfCompressSettings(val displayName:String="Compressed.pdf", val maxDpi:Int=150, val jpegQuality:Int=80, val targetBytes:Long=0L)
@Serializable data class PdfStampSettings(val displayName:String="Stamped.pdf", val text:String="", val pageNumbers:Boolean=false, val pages:String="all", val centerX:Double=0.5, val centerY:Double=0.5, val fontSizePt:Int=42, val opacityPercent:Int=28, val rotationDegrees:Int=-35, val tiled:Boolean=false, val colorHex:String="#64748B", val fontFamily:String="sans", val useImage:Boolean=false, val imageScale:Double=1.0)
@Serializable data class PdfSignaturePlacement(val id:String="", val page:Int=1, val left:Double=0.6, val top:Double=0.75, val right:Double=0.95, val bottom:Double=0.95, val rotationDegrees:Float=0f)
@Serializable data class PdfSignatureSettings(val displayName:String="Signed.pdf", val page:Int=1, val left:Double=0.6, val top:Double=0.75, val right:Double=0.95, val bottom:Double=0.95, val rotationDegrees:Float=0f, val placements:List<PdfSignaturePlacement> = emptyList())
@Serializable data class PdfProtectSettings(
    val displayName:String="Protected.pdf", val secretHandle:String="", val removeProtection:Boolean=false,
    val allowPrinting:Boolean=true, val allowCopy:Boolean=true, val allowModify:Boolean=false,
    val allowAnnotations:Boolean=true, val allowForms:Boolean=true
)
@Serializable data class OfficeToPdfSettings(val displayName:String="Document.pdf")
@Serializable data class ImageWatermarkSettings(val text:String="", val opacityPercent:Int=40, val useLogo:Boolean=false, val output:ImageConversionSettings=ImageConversionSettings(quality=98,namePrefix="watermarked_"), val centerX:Double=0.5, val centerY:Double=0.5, val scale:Double=1.0, val rotationDegrees:Int=0, val tiled:Boolean=false, val colorHex:String="#FFFFFF", val fontFamily:String="sans")
@Serializable data class ImageJoinSettings(val layout:String="GRID2", val gapPx:Int=8, val overlapPx:Int=0, val output:ImageConversionSettings=ImageConversionSettings(format="PNG", namePrefix="joined_"))
@Serializable data class BatchRenameSettings(val pattern:String="file-{n}", val startAt:Int=1, val date:String="")
@Serializable data class EncryptedArchiveSettings(val displayName:String="Archive.zip", val secretHandle:String="", val extract:Boolean=false)

@Serializable data class MediaSelectionSettings(
    val assetIdentity:String, val choiceKey:String, val fingerprint:String,
    val mode:String="VIDEO",val audioOutput:String="ORIGINAL",val audioBitrate:Int=192
)

@Serializable data class PdfNupSettings(val displayName:String="PrintLayout.pdf", val pagesPerSheet:Int=2, val grayscale:Boolean=false)
@Serializable data class PdfRedactSettings(val displayName:String="Redacted.pdf", val pages:String="1", val left:Double=0.1, val top:Double=0.1, val right:Double=0.9, val bottom:Double=0.25, val fillBlack:Boolean=true, val overrides:List<PdfCropOverride> = emptyList())
