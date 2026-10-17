package com.jumlo.app.data.processing
import kotlinx.serialization.Serializable

@Serializable data class ImageConversionSettings(val format:String="JPEG",val quality:Int=85,val matteArgb:Int=-1,val stripMetadata:Boolean=true,val namePrefix:String="converted_",val customOutputName:String="")
@Serializable data class ImageResizeSettings(val width:Int=1600,val height:Int=1600,val percentage:Int?=null,val longestEdge:Int?=null,val fit:String="FIT",val allowUpscaling:Boolean=false,val output:ImageConversionSettings=ImageConversionSettings(namePrefix="resized_"))
@Serializable data class ImageCropSettings(val left:Double=0.0,val top:Double=0.0,val right:Double=1.0,val bottom:Double=1.0,val rotationQuarterTurns:Int=0,val flipX:Boolean=false,val flipY:Boolean=false,val output:ImageConversionSettings=ImageConversionSettings(namePrefix="cropped_"))
@Serializable data class ImageCompressionSettings(val output:ImageConversionSettings=ImageConversionSettings(quality=75,namePrefix="compressed_"))
@Serializable data class ImagesToPdfSettings(val displayName:String="Images.pdf",val paperWidthPt:Int=595,val paperHeightPt:Int=842,val orientation:String="AUTO",val marginMm:Int=10,val fill:Boolean=false)
@Serializable data class PdfImagesSettings(val pages:String="all",val dpi:Int=144,val format:String="PNG",val quality:Int=90,val grayscale:Boolean=false,val displayName:String="")
@Serializable data class ArchiveSettings(val displayName:String="Archive.zip",val compressionLevel:Int=6)
@Serializable data class HashSettings(val expectedSha256:String?=null)
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
@Serializable data class PdfOrganizeSettings(val displayName:String="Organized.pdf", val order:String="", val rotatePages:String="", val rotateDegrees:Int=90)
@Serializable data class PdfExtractSettings(val displayName:String="Extracted.pdf", val pages:String="1")
@Serializable data class PdfCropSettings(val displayName:String="Cropped.pdf", val pages:String="all", val left:Double=0.0, val top:Double=0.0, val right:Double=1.0, val bottom:Double=1.0)
@Serializable data class PdfCompressSettings(val displayName:String="Compressed.pdf", val maxDpi:Int=150, val jpegQuality:Int=80)
@Serializable data class PdfStampSettings(val displayName:String="Stamped.pdf", val text:String="", val pageNumbers:Boolean=false, val pages:String="all")
@Serializable data class PdfAnnotateSettings(val displayName:String="Annotated.pdf", val text:String="", val page:Int=1, val x:Double=0.1, val y:Double=0.1, val asAnnotation:Boolean=false)
@Serializable data class PdfSignatureSettings(val displayName:String="Signed.pdf", val page:Int=1, val left:Double=0.6, val top:Double=0.75, val right:Double=0.95, val bottom:Double=0.95)
@Serializable data class PdfMetadataSettings(val displayName:String="Document.pdf", val title:String="", val author:String="", val subject:String="", val keywords:String="", val insertAtPage:Int=1)
@Serializable data class PdfProtectSettings(val displayName:String="Protected.pdf", val secretHandle:String="", val removeProtection:Boolean=false)
@Serializable data class PdfFormSettings(val displayName:String="Form.pdf", val fieldsJson:String="{}", val flatten:Boolean=false)
@Serializable data class PdfCompareSettings(val dummy:Int=0, val displayName:String="compare-report.txt")
@Serializable data class PdfRedactSettings(val displayName:String="Redacted.pdf", val page:Int=1, val left:Double=0.0, val top:Double=0.0, val right:Double=1.0, val bottom:Double=0.2, val verifyText:String="")
@Serializable data class ImageExifSettings(val removeLocation:Boolean=true, val removeAll:Boolean=false, val output:ImageConversionSettings=ImageConversionSettings(namePrefix="exif_"))
@Serializable data class ImageWatermarkSettings(val text:String="", val opacityPercent:Int=40, val useLogo:Boolean=false, val output:ImageConversionSettings=ImageConversionSettings(namePrefix="watermarked_"))
@Serializable data class ImageJoinSettings(val layout:String="GRID2", val gapPx:Int=8, val overlapPx:Int=0, val output:ImageConversionSettings=ImageConversionSettings(format="PNG", namePrefix="joined_"))
@Serializable data class ImageAdjustSettings(val brightness:Int=0, val contrastPercent:Int=100, val saturationPercent:Int=100, val padPx:Int=0, val output:ImageConversionSettings=ImageConversionSettings(namePrefix="adjusted_"))
@Serializable data class ImageTargetSettings(val targetBytes:Long=200000, val output:ImageConversionSettings=ImageConversionSettings(namePrefix="target_"))
@Serializable data class BatchRenameSettings(val pattern:String="file-{n}", val startAt:Int=1)
@Serializable data class EncryptedArchiveSettings(val displayName:String="Archive.zip", val secretHandle:String="", val extract:Boolean=false)
@Serializable data class GoogleDriveSettings(val fileIdOrUrl:String="", val folderId:String="", val exportMode:Boolean=false, val tokenSecretHandle:String="")

@Serializable data class MediaSelectionSettings(
    val assetIdentity:String, val choiceKey:String, val fingerprint:String,
    val mode:String="VIDEO",val audioOutput:String="ORIGINAL",val audioBitrate:Int=192
)
