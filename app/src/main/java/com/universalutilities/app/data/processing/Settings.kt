package com.universalutilities.app.data.processing
import kotlinx.serialization.Serializable

@Serializable data class ImageConversionSettings(val format:String="JPEG",val quality:Int=85,val matteArgb:Int=-1,val stripMetadata:Boolean=true,val namePrefix:String="converted_")
@Serializable data class ImageResizeSettings(val width:Int=1600,val height:Int=1600,val percentage:Int?=null,val longestEdge:Int?=null,val fit:String="FIT",val allowUpscaling:Boolean=false,val output:ImageConversionSettings=ImageConversionSettings(namePrefix="resized_"))
@Serializable data class ImageCropSettings(val left:Double=0.0,val top:Double=0.0,val right:Double=1.0,val bottom:Double=1.0,val rotationQuarterTurns:Int=0,val flipX:Boolean=false,val flipY:Boolean=false,val output:ImageConversionSettings=ImageConversionSettings(namePrefix="cropped_"))
@Serializable data class ImageCompressionSettings(val output:ImageConversionSettings=ImageConversionSettings(quality=75,namePrefix="compressed_"))
@Serializable data class ImagesToPdfSettings(val displayName:String="Images.pdf",val paperWidthPt:Int=595,val paperHeightPt:Int=842,val orientation:String="AUTO",val marginMm:Int=10,val fill:Boolean=false)
@Serializable data class PdfImagesSettings(val pages:String="all",val dpi:Int=144,val format:String="PNG",val quality:Int=90,val grayscale:Boolean=false)
@Serializable data class ArchiveSettings(val displayName:String="Archive.zip",val compressionLevel:Int=6)
@Serializable data class HashSettings(val expectedSha256:String?=null)
@Serializable data class DownloadSettings(val urlSecretHandle:String,val displayName:String="download.bin",val wifiOnly:Boolean=false,val unmeteredOnly:Boolean=false,val cleartextConsent:Boolean=false,val media:MediaSelectionSettings?=null)

@Serializable data class MediaSelectionSettings(
    val assetIdentity:String, val choiceKey:String, val fingerprint:String,
    val mode:String="VIDEO",val audioOutput:String="ORIGINAL",val audioBitrate:Int=192
)
