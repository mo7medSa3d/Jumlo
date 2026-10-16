package com.universalutilities.app.data.processing

import android.graphics.Bitmap
import com.universalutilities.app.core.*
import com.universalutilities.app.data.jobs.*
import com.universalutilities.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton class ImageOperationHandler @Inject constructor():OperationHandler {
    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
        val resize=if(request.toolId=="img_02") Json.decodeFromString<ImageResizeSettings>(request.settingsJson) else null
        val crop=if(request.toolId=="img_03") Json.decodeFromString<ImageCropSettings>(request.settingsJson) else null
        val output=when(request.toolId) {
            "img_01" -> Json.decodeFromString<ImageConversionSettings>(request.settingsJson)
            "img_02" -> resize!!.output
            "img_03" -> crop!!.output
            "img_04" -> Json.decodeFromString<ImageCompressionSettings>(request.settingsJson).output
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
        // This candidate explicitly emits sRGB and removes metadata after orientation normalization.
        require(output.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
        val outcomes=mutableListOf<ItemOutcome>()
        for ((index, id) in request.sourceIds.withIndex()) {
            context.checkCancellation(); val key="image:$index"
            val existing=context.outputs.find(request.id,key)
            if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
            else try {
                val source=context.sources.get(id)
                val name=SafeNames.basename(output.namePrefix+SafeNames.fromDisplayName(source.displayName,"image").substringBeforeLast('.')+"."+BitmapTransforms.extension(output.format))
                val ticket=context.outputs.reserve(request.id,key,name,BitmapTransforms.mime(output.format))
                val record=context.sources.withSeekable(id,request.id) { file ->
                    BitmapTransforms.decode(file,context.budget.maxBitmapBytes).use { decoded ->
                        val src=decoded.bitmap; var transformed:Bitmap?=null
                        try {
                            val bmp=when {
                                resize!=null -> {
                                    val dimensions=when {
                                        resize.percentage!=null -> { require(resize.percentage in 1..400) { "INVALID_PERCENTAGE" }; maxOf(1,(src.width*resize.percentage/100.0).roundToInt()) to maxOf(1,(src.height*resize.percentage/100.0).roundToInt()) }
                                        resize.longestEdge!=null -> { require(resize.longestEdge>0) { "INVALID_DIMENSIONS" }; val scale=resize.longestEdge.toDouble()/maxOf(src.width,src.height); maxOf(1,(src.width*scale).roundToInt()) to maxOf(1,(src.height*scale).roundToInt()) }
                                        else -> resize.width to resize.height
                                    }
                                    BitmapTransforms.resize(src,dimensions.first,dimensions.second,FitMode.valueOf(resize.fit),resize.allowUpscaling,output.matteArgb,context.budget.maxBitmapBytes).also { transformed=it }
                                }
                                crop!=null -> BitmapTransforms.crop(src,Box(crop.left,crop.top,crop.right,crop.bottom),crop.rotationQuarterTurns,crop.flipX,crop.flipY,context.budget.maxBitmapBytes).also { transformed=it }
                                else -> src
                            }
                            context.checkCancellation()
                            val resident=if(bmp!==src) src.allocationByteCount.toLong() else 0L
                            BitmapTransforms.encode(bmp,ticket.stageFile,output,context.budget.maxBitmapBytes-resident)
                            context.outputs.publish(ticket) { context.outputs.inspect(it,ticket.mimeType) }
                        } finally { if(transformed!==src) transformed?.recycle() }
                    }
                }
                val status=if(request.toolId=="img_04" && source.sizeBytes!=null && record.sizeBytes>=source.sizeBytes) ItemStatus.LARGER else ItemStatus.CREATED
                outcomes+=ItemOutcome(key,status,record.id)
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:Exception) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
            context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
        }
        return outcomes
    }
}
