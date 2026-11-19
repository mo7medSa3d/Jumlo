package com.jumlo.app.data.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class ImageOperationHandler @Inject constructor():OperationHandler {
    private fun resolveOutputFileName(output: ImageConversionSettings, sourceDisplayName: String, index: Int, totalCount: Int, extOverride: String? = null): String {
        val ext = (extOverride ?: BitmapTransforms.extension(output.format)).trimStart('.')
        val custom = output.customOutputName.substringBeforeLast('.', output.customOutputName).trim()
            .takeUnless { it.isEmpty() || it.equals("download", true) || it.equals("Images", true) || it.equals("Document", true) }
        return if (custom != null) {
            val cleanBase = AutoOutputName.cleanBaseName(custom, "image")
            if (totalCount == 1) SafeNames.basename("$cleanBase.$ext")
            else SafeNames.basename("$cleanBase - ${(index + 1).toString().padStart(3, '0')}.$ext")
        } else {
            SafeNames.basename(output.namePrefix + SafeNames.fromDisplayName(sourceDisplayName, "image").substringBeforeLast('.') + "." + ext)
        }
    }

    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = withContext(Dispatchers.IO) {
        require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
        val resize=if(request.toolId=="img_02") Json.decodeFromString<ImageResizeSettings>(request.settingsJson) else null
        val crop=if(request.toolId=="img_03") Json.decodeFromString<ImageCropSettings>(request.settingsJson) else null
        val output=when(request.toolId) {
            "img_01" -> Json.decodeFromString<ImageConversionSettings>(request.settingsJson)
            "img_02" -> resize!!.output
            "img_03" -> crop!!.output
            "img_04" -> Json.decodeFromString<ImageCompressionSettings>(request.settingsJson).output
            "img_watermark","img_join" -> return@withContext extras(request,context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
        require(output.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
        // One published file per source; the published-output budget is a hard product limit.
        ResourceBudgetPolicy.requireOutputCount(request.sourceIds.size)
        val isEncodePipeline = request.toolId == "img_01" || request.toolId == "img_04"
        val outcomes=mutableListOf<ItemOutcome>()
        for ((index, id) in request.sourceIds.withIndex()) {
            context.checkCancellation(); val key="image:$index"
            val existing=context.outputs.find(request.id,key)
            if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
            else try {
                val source=context.sources.get(id)
                val effectiveFormat = if (isEncodePipeline) ImageOutputEncoder.outputSettings(request.toolId,output).format else output.format
                val name = resolveOutputFileName(output.copy(format = effectiveFormat), source.displayName, index, request.sourceIds.size)
                val ticket=context.outputs.reserve(request.id,key,name,BitmapTransforms.mime(effectiveFormat))
                val record=try { context.sources.withSeekable(id,request.id) { file ->
                    val originalSize = file.length()
                    BitmapTransforms.decode(file,context.budget.maxBitmapBytes).use { decoded ->
                        val src=decoded.bitmap; var transformed:Bitmap?=null
                        try {
                            val bmp=when {
                                resize!=null -> {
                                    val dimensions=ImageResizePolicy.targetDimensions(
                                        src.width,src.height,
                                        resize.percentage,resize.longestEdge,resize.width,resize.height)
                                    BitmapTransforms.resize(src,dimensions.first,dimensions.second,FitMode.valueOf(resize.fit),
                                        ImageResizePolicy.allowsUpscaling(resize.percentage,resize.longestEdge,resize.allowUpscaling),
                                        output.matteArgb,context.budget.maxBitmapBytes).also { transformed=it }
                                }
                                crop!=null -> BitmapTransforms.crop(src,Box(crop.left,crop.top,crop.right,crop.bottom),crop.rotationQuarterTurns,crop.flipX,crop.flipY,context.budget.maxBitmapBytes).also { transformed=it }
                                else -> src
                            }
                            context.checkCancellation()
                            val resident=if(bmp!==src) src.allocationByteCount.toLong() else 0L
                            if (isEncodePipeline) {
                                ImageOutputEncoder.encode(bmp,ticket.stageFile,request.toolId,output,originalSize,
                                    context.budget.maxBitmapBytes-resident,checkCancel=context.checkCancellation)
                            } else {
                                BitmapTransforms.encode(bmp,ticket.stageFile,output,context.budget.maxBitmapBytes-resident,context.checkCancellation)
                            }
                            context.outputs.publish(ticket) { context.outputs.inspect(it,ticket.mimeType,originalSizeBytes=originalSize) }
                        } finally { if(transformed!==src) transformed?.recycle() }
                    }
                } } catch(t:Throwable) { ticket.stageFile.delete(); throw t }
                outcomes+=ItemOutcome(key,ItemStatus.CREATED,record.id)
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:ImageSizeBoundException) {
                if (request.toolId == "img_04") outcomes+=ItemOutcome(key,ItemStatus.UNCHANGED,errorCode="IMAGE_NOT_SMALLER")
                else outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e))
            }
            catch(e:Exception) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
            context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
        }
        outcomes
    }

    private suspend fun extras(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        val planned=if(request.toolId=="img_join") 1 else request.sourceIds.size
        ResourceBudgetPolicy.requireOutputCount(maxOf(1,planned))
        val outcomes=mutableListOf<ItemOutcome>()
        suspend fun publishBytes(key:String,name:String,mime:String,originalBytes:Long?=null,write:suspend (java.io.File)->Unit):ItemOutcome {
            context.outputs.find(request.id,key)?.let{ return ItemOutcome(key,ItemStatus.CREATED,it.id) }
            context.checkCancellation()
            val ticket=context.outputs.reserve(request.id,key,name,mime)
            val rec=try {
                write(ticket.stageFile)
                context.checkCancellation()
                context.outputs.publish(ticket){ context.outputs.inspect(it,ticket.mimeType,originalSizeBytes=originalBytes) }
            } catch(t:Throwable) { ticket.stageFile.delete(); throw t }
            return ItemOutcome(key,ItemStatus.CREATED,rec.id)
        }
        when(request.toolId){
            "img_watermark" -> {
                val settings=Json.decodeFromString<ImageWatermarkSettings>(request.settingsJson)
                require(settings.output.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
                require(settings.opacityPercent in 1..100) { "INVALID_OPACITY" }
                require(if(settings.useLogo) request.sourceIds.size>=2 else settings.text.isNotBlank()) { "WATERMARK_CONTENT_REQUIRED" }
                val logoId=request.sourceIds.getOrNull(1)?.takeIf{ settings.useLogo }
                val logoSkipped=if(settings.useLogo && logoId!=null) 1 else 0
                val producedCount=request.sourceIds.size-logoSkipped
                var produced=0
                request.sourceIds.forEachIndexed{ index,id ->
                    if(settings.useLogo && index==1) return@forEachIndexed
                    val key="image:$index"
                    try {
                        val source=context.sources.get(id)
                        val name=resolveOutputFileName(settings.output,source.displayName,produced,producedCount)
                        produced++
                        outcomes+=publishBytes(key,name,BitmapTransforms.mime(settings.output.format),source.sizeBytes){ stage ->
                            context.sources.withSeekable(id,request.id){ f -> BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use{ d ->
                                val marked=if(logoId!=null){
                                    var r:Bitmap?=null
                                    context.sources.withSeekable(logoId,request.id){ lf ->
                                        val remaining=context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong()-ImageWorkPolicy.bytes(d.bitmap.width,d.bitmap.height)
                                        BitmapTransforms.decodeSampled(lf,remaining,1600).use{ ld ->
                                            r=BitmapExtras.watermark(d.bitmap,ld.bitmap,settings,context.budget.maxBitmapBytes)
                                        }
                                    }
                                    r!!
                                } else BitmapExtras.watermark(d.bitmap,null,settings,context.budget.maxBitmapBytes)
                                try{ BitmapTransforms.encode(marked,stage,settings.output,context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong(),context.checkCancellation) } finally { marked.recycle() }
                            } }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                    context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
                }
            }
            "img_join" -> {
                val settings=Json.decodeFromString<ImageJoinSettings>(request.settingsJson)
                require(settings.output.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
                val key="joined"
                try {
                    val firstSource = runCatching { context.sources.get(request.sourceIds.first()).displayName }.getOrDefault("joined")
                    val name=resolveOutputFileName(settings.output,firstSource,0,1)
                    val totalInputBytes = request.sourceIds.sumOf { id ->
                        runCatching { context.sources.get(id).sizeBytes ?: 0L }.getOrDefault(0L)
                    }.takeIf { it > 0L }
                    outcomes+=publishBytes(key,name,BitmapTransforms.mime(settings.output.format),originalBytes=totalInputBytes){ stage ->
                        // Inspect first, then keep only one decoded input beside the output.
                        // A strip of many pages no longer retains a copy of every source.
                        val infos = request.sourceIds.map { id -> context.checkCancellation(); context.sources.withSeekable(id,request.id) { f ->
                            BitmapTransforms.inspect(f)
                        } }
                        val plan = ImageJoinPolicy.plan(infos.map { it.normalizedWidth to it.normalizedHeight },settings.layout,settings.gapPx,settings.overlapPx)
                        val outputBytes = ImageWorkPolicy.bytes(plan.width,plan.height)
                        val decodePeak = infos.maxOf { it.decodePeakBytes }
                        require(outputBytes <= context.budget.maxBitmapBytes && decodePeak <= context.budget.maxBitmapBytes-outputBytes) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
                        // joined is filled opaque white with setHasAlpha(false), so JPEG encoding needs no extra matte.
                        val joined = Bitmap.createBitmap(plan.width,plan.height,Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(joined).apply { drawColor(Color.WHITE) }
                            joined.setHasAlpha(false)
                            for((idx,id) in request.sourceIds.withIndex()) {
                                context.checkCancellation()
                                context.sources.withSeekable(id,request.id) { f ->
                                    BitmapTransforms.decode(f,context.budget.maxBitmapBytes-joined.allocationByteCount.toLong()).use { decoded ->
                                        BitmapExtras.drawJoinPart(canvas,decoded.bitmap,plan.placements[idx])
                                    }
                                }
                                context.report(JobProgress(JobStage.RUNNING,(idx+1).toLong(),request.sourceIds.size.toLong()))
                            }
                            BitmapTransforms.encode(joined,stage,settings.output,context.budget.maxBitmapBytes,context.checkCancellation)
                        } finally { joined.recycle() }
                    }
                } catch(cancel:CancellationException) { throw cancel }
                catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
            }
        }
        return outcomes
    }
}
