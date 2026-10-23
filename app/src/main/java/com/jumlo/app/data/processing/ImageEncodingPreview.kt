package com.jumlo.app.data.processing

import android.graphics.Bitmap
import com.jumlo.app.core.ImageSizeBoundException
import com.jumlo.app.data.jobs.OperationFailure
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.util.UUID

/** The same codec/size search is used by private previews and published image operations. */
object ImageOutputEncoder {
    fun outputSettings(toolId:String,settings:ImageConversionSettings):ImageConversionSettings = when(toolId) {
        "img_01" -> settings
        "img_04" -> if(settings.format=="PNG") settings.copy(format="WEBP") else settings
        "img_target" -> settings.copy(format="JPEG")
        else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
    }

    fun encode(
        bitmap:Bitmap,destination:File,toolId:String,settings:ImageConversionSettings,
        originalBytes:Long,budget:Long,targetBytes:Long=0L,checkCancel:()->Unit={}
    ):ImageEncodingResult {
        val effective=outputSettings(toolId,settings)
        require(effective.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
        checkCancel()
        val result=when(toolId) {
            "img_04" -> BitmapTransforms.encodeGuaranteedSmallerWithResult(bitmap,destination,originalBytes,effective,budget,checkCancel)
            "img_target" -> {
                require(targetBytes>0L && targetBytes<Long.MAX_VALUE) { "INVALID_IMAGE_SIZE_BOUND" }
                BitmapTransforms.encodeGuaranteedSmallerWithResult(bitmap,destination,targetBytes+1L,effective,budget,checkCancel).also {
                    require(it.sizeBytes in 1..targetBytes) { "IMAGE_SIZE_BOUND_UNMET" }
                }
            }
            else -> BitmapTransforms.encodeWithResult(bitmap,destination,effective,budget,checkCancel)
        }
        checkCancel()
        return result
    }
}

/**
 * Both bitmaps are newly owned by this result. Close only after the UI stops displaying them.
 * When [sampled] is true, size, dimensions and quality describe that sample, not the full export.
 */
class EncodedImagePreview(
    val before:Bitmap,val after:Bitmap,
    val sourceBytes:Long,val sourceWidth:Int,val sourceHeight:Int,
    val encodedBytes:Long,val encodedWidth:Int,val encodedHeight:Int,
    val format:String,val quality:Int?,val sampled:Boolean,val requestedTargetBytes:Long?
):Closeable {
    override fun close() {
        if(!before.isRecycled) before.recycle()
        if(after!==before && !after.isRecycled) after.recycle()
    }
}

/** Bounded actual encode → decode; never writes a published output or changes source bytes. */
object ImageEncodingPreview {
    private const val MAX_WORKING_BYTES=32L*1024*1024
    private const val MAX_CANDIDATE_BYTES=16L*1024*1024

    fun create(
        source:File,scratchDirectory:File,toolId:String,settingsJson:String,
        maxBitmapBytes:Long,minFreeBytes:Long,maxEdge:Int=1200,checkCancel:()->Unit={}
    ):EncodedImagePreview {
        checkCancel()
        require(maxEdge in 1..1600 && maxBitmapBytes>=12L) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        require(minFreeBytes>=0L) { "DISK_BUDGET_EXCEEDED" }
        val target=if(toolId=="img_target") Json.decodeFromString<ImageTargetSettings>(settingsJson) else null
        val output=when(toolId) {
            "img_01" -> Json.decodeFromString<ImageConversionSettings>(settingsJson)
            "img_04" -> Json.decodeFromString<ImageCompressionSettings>(settingsJson).output
            "img_target" -> requireNotNull(target).output
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
        require(output.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
        val workingBudget=minOf(maxBitmapBytes,MAX_WORKING_BYTES)
        val info=BitmapTransforms.inspect(source)
        checkCancel()
        require(scratchDirectory.isDirectory || scratchDirectory.mkdirs()) { "OUTPUT_CREATE_FAILED" }
        val available=scratchDirectory.usableSpace
        require(available>=minFreeBytes && MAX_CANDIDATE_BYTES<=available-minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
        val directory=File(scratchDirectory,"jumlo-image-preview-${UUID.randomUUID()}")
        require(directory.mkdir()) { "OUTPUT_CREATE_FAILED" }
        var before:Bitmap?=null
        var after:Bitmap?=null
        try {
            val check:()->Unit={
                checkCancel()
                require(directory.usableSpace>=minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
                require(directory.listFiles().orEmpty().sumOf {it.length()}<=MAX_CANDIDATE_BYTES) { "DISK_BUDGET_EXCEEDED" }
            }
            check()
            // Leave room for a resize/matte during encoding and the separately decoded result.
            val decoded=BitmapTransforms.decodeSampled(source,workingBudget/3,maxEdge)
            val input=decoded.bitmap.also {before=it}
            check()
            val candidate=File(directory,"encoded.tmp")
            val result=ImageOutputEncoder.encode(input,candidate,toolId,output,source.length(),workingBudget,target?.targetBytes ?: 0L,check)
            check()
            val encoded=BitmapTransforms.decode(candidate,workingBudget-input.allocationByteCount.toLong()).bitmap.also {after=it}
            require(encoded.width==result.width && encoded.height==result.height) { "IMAGE_VERIFY_FAILED" }
            check()
            val preview=EncodedImagePreview(input,encoded,source.length(),info.normalizedWidth,info.normalizedHeight,
                result.sizeBytes,result.width,result.height,result.format,result.quality,
                input.width!=info.normalizedWidth || input.height!=info.normalizedHeight,target?.targetBytes)
            before=null;after=null
            return preview
        } catch(error:ImageSizeBoundException) {
            if(toolId=="img_04") throw OperationFailure("IMAGE_NOT_SMALLER")
            throw error
        } catch(oom:OutOfMemoryError) {
            throw OperationFailure("LOW_MEMORY")
        } finally {
            before?.takeUnless {it.isRecycled}?.recycle()
            after?.takeUnless {it.isRecycled}?.recycle()
            directory.deleteRecursively()
        }
    }
}
