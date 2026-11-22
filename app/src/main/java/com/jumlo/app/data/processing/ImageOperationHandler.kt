package com.jumlo.app.data.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
import kotlin.math.roundToInt

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

    private fun appendDimensionsSuffix(fileName: String, width: Int, height: Int): String {
        val dot = fileName.lastIndexOf('.')
        val suffixed = if (dot > 0) "${fileName.substring(0, dot)}_${width}x${height}${fileName.substring(dot)}"
        else "${fileName}_${width}x${height}"
        return SafeNames.basename(suffixed)
    }

    /** Decodes at full resolution when it fits; gracefully samples only if an extreme image exceeds available heap. */
    private fun decodeForWork(file: java.io.File, budget: Long, maxEdge: Int = ImageWorkPolicy.MAX_EDGE_PX): DecodedImage {
        val info = BitmapTransforms.inspect(file)
        return if (info.decodePeakBytes <= budget && maxOf(info.normalizedWidth, info.normalizedHeight) <= maxEdge) {
            BitmapTransforms.decode(file, budget)
        } else {
            BitmapTransforms.decodeSampled(file, budget, maxEdge)
        }
    }

    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = withContext(Dispatchers.IO) {
        require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
        val lenientJson = Json { ignoreUnknownKeys = true }
        val resize=if(request.toolId=="img_02") lenientJson.decodeFromString<ImageResizeSettings>(request.settingsJson) else null
        val compress=if(request.toolId=="img_04") lenientJson.decodeFromString<ImageCompressionSettings>(request.settingsJson) else null
        val crop=when(request.toolId) {
            "img_03" -> lenientJson.decodeFromString<ImageCropSettings>(request.settingsJson)
            "img_04" -> compress?.takeIf { it.left > 0.0 || it.top > 0.0 || it.right < 1.0 || it.bottom < 1.0 || it.rotationQuarterTurns % 4 != 0 || it.flipX || it.flipY }?.let {
                ImageCropSettings(it.left, it.top, it.right, it.bottom, it.rotationQuarterTurns, it.flipX, it.flipY, it.output)
            }
            else -> null
        }
        val output=when(request.toolId) {
            "img_01" -> lenientJson.decodeFromString<ImageConversionSettings>(request.settingsJson)
            "img_02" -> resize!!.output
            "img_03" -> crop!!.output
            "img_04" -> compress!!.output
            "img_watermark","img_join" -> return@withContext extras(request,context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
        require(output.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
        // One published file per source; the published-output budget is a hard product limit.
        ResourceBudgetPolicy.requireOutputCount(request.sourceIds.size)
        val effectiveBudget = ImageWorkPolicy.effectiveBudget(context.budget.maxBitmapBytes)
        val isEncodePipeline = request.toolId == "img_01" || request.toolId == "img_04"
        val outcomes=mutableListOf<ItemOutcome>()
        for ((index, id) in request.sourceIds.withIndex()) {
            context.checkCancellation(); val key="image:$index"
            val existing=context.outputs.find(request.id,key)
            if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
            else try {
                val source=context.sources.get(id)
                val effectiveFormat = if (isEncodePipeline) ImageOutputEncoder.outputSettings(request.toolId,output).format else output.format
                val formatMaxEdge = ImageWorkPolicy.maxEdgeForFormat(effectiveFormat)
                val name = resolveOutputFileName(output.copy(format = effectiveFormat), source.displayName, index, request.sourceIds.size)
                val mime = BitmapTransforms.mime(effectiveFormat)
                if (resize != null) {
                    val outcome = context.sources.withSeekable(id, request.id) { file ->
                        val originalSize = file.length()
                        // Reserve half the budget for the output bitmap so source never starves target.
                        val decodeBudget = (effectiveBudget / 2L).coerceAtLeast(4L * 1024 * 1024)
                        val staged = java.io.File.createTempFile("resize-stage-", ".tmp", file.parentFile)
                        var ticket: OutputTicket? = null
                        try {
                            val (expectedW, expectedH, actualDims) = decodeForWork(file, decodeBudget, maxEdge = formatMaxEdge).use { decoded ->
                                val src = decoded.bitmap
                                var transformed: Bitmap? = null
                                try {
                                    val fitMode = FitMode.valueOf(resize.fit)
                                    val allowUpscale = ImageResizePolicy.allowsUpscaling(resize.percentage, resize.longestEdge, resize.allowUpscaling)
                                    val requested = ImageResizePolicy.targetDimensions(
                                        src.width, src.height,
                                        resize.percentage, resize.longestEdge, resize.width, resize.height
                                    )
                                    val expected = ImageGeometry.resolve(src.width, src.height, requested.first, requested.second, fitMode, allowUpscale)
                                    val available = (effectiveBudget - src.allocationByteCount.toLong()).coerceAtLeast(4L * 1024 * 1024)
                                    val pixelCap = minOf(ImageWorkPolicy.MAX_PIXELS, available / 4L, Int.MAX_VALUE / 4L).coerceAtLeast(1L)
                                    val fitsExact = maxOf(expected.width, expected.height) <= formatMaxEdge &&
                                        expected.width.toLong() * expected.height.toLong() <= pixelCap
                                    val dimensions = if (fitsExact) {
                                        expected.width to expected.height
                                    } else {
                                        ImageWorkPolicy.boundedDimensions(
                                            expected.width, expected.height, available,
                                            maxEdge = formatMaxEdge, maxPixels = ImageWorkPolicy.MAX_PIXELS
                                        )
                                    }
                                    val bmp = BitmapTransforms.resize(
                                        src, dimensions.first, dimensions.second, fitMode, allowUpscale,
                                        output.matteArgb, effectiveBudget
                                    ).also { transformed = it }
                                    if (bmp !== src && !src.isRecycled) src.recycle()
                                    context.checkCancellation()
                                    val encodedDims = encodeWithOomRetry(bmp, staged, output, effectiveBudget, context.checkCancellation)
                                    Triple(expected.width, expected.height, encodedDims)
                                } finally { if (transformed !== src && transformed?.isRecycled == false) transformed?.recycle() }
                            }
                            val wasCapped = actualDims.first != expectedW || actualDims.second != expectedH
                            val finalName = if (wasCapped) appendDimensionsSuffix(name, actualDims.first, actualDims.second) else name
                            val reserved = context.outputs.reserve(request.id, key, finalName, mime)
                            ticket = reserved
                            staged.copyTo(reserved.stageFile, overwrite = true)
                            staged.delete()
                            val record = context.outputs.publish(reserved) {
                                context.outputs.inspect(it, reserved.mimeType, originalSizeBytes = originalSize)
                            }
                            ItemOutcome(
                                key,
                                ItemStatus.CREATED,
                                record.id,
                                errorCode = if (wasCapped) "RESIZED_CAPPED_${actualDims.first}x${actualDims.second}" else null
                            )
                        } catch (t: Throwable) {
                            staged.delete()
                            ticket?.stageFile?.delete()
                            throw t
                        }
                    }
                    outcomes += outcome
                } else {
                    val ticket=context.outputs.reserve(request.id,key,name,mime)
                    val record=try { context.sources.withSeekable(id,request.id) { file ->
                        val originalSize = file.length()
                        if (crop != null) {
                            // Use BitmapRegionDecoder directly on file so cropping a 50 MP photo doesn't decode the whole image first.
                            BitmapTransforms.decodeCroppedRegion(
                                file, Box(crop.left, crop.top, crop.right, crop.bottom),
                                crop.rotationQuarterTurns, crop.flipX, crop.flipY,
                                effectiveBudget, maxEdge = formatMaxEdge
                            ).use { cropped ->
                                context.checkCancellation()
                                if (isEncodePipeline) {
                                    ImageOutputEncoder.encode(cropped.bitmap, ticket.stageFile, request.toolId, output, originalSize,
                                        effectiveBudget, checkCancel = context.checkCancellation)
                                } else {
                                    BitmapTransforms.encode(cropped.bitmap, ticket.stageFile, output, effectiveBudget, context.checkCancellation)
                                }
                                context.outputs.publish(ticket) { context.outputs.inspect(it, ticket.mimeType, originalSizeBytes = originalSize) }
                            }
                        } else {
                            decodeForWork(file, effectiveBudget, maxEdge = formatMaxEdge).use { decoded ->
                                val bmp = decoded.bitmap
                                context.checkCancellation()
                                if (request.toolId == "img_04" && originalSize > 0L) {
                                    val targetBytes = (originalSize * output.quality.coerceIn(5, 98) / 100.0).toLong().coerceAtLeast(4096L)
                                    BitmapTransforms.compressToTarget(bmp, ticket.stageFile, output.copy(format = effectiveFormat), targetBytes,
                                        effectiveBudget, checkCancel = context.checkCancellation)
                                } else if (isEncodePipeline) {
                                    ImageOutputEncoder.encode(bmp,ticket.stageFile,request.toolId,output,originalSize,
                                        effectiveBudget,checkCancel=context.checkCancellation)
                                } else {
                                    encodeWithOomRetry(bmp, ticket.stageFile, output, effectiveBudget, context.checkCancellation)
                                }
                                context.outputs.publish(ticket) { context.outputs.inspect(it,ticket.mimeType,originalSizeBytes=originalSize) }
                            }
                        }
                    } } catch(t:Throwable) { ticket.stageFile.delete(); throw t }
                    outcomes+=ItemOutcome(key,ItemStatus.CREATED,record.id)
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:ImageSizeBoundException) {
                if (request.toolId == "img_04") outcomes+=ItemOutcome(key,ItemStatus.UNCHANGED,errorCode="IMAGE_NOT_SMALLER")
                else outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e))
            }
            catch(e:Exception) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
            catch(oom:OutOfMemoryError) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode="LOW_MEMORY") }
            context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
        }
        outcomes
    }

    /** Encodes [bitmap], automatically downscaling by 0.75x on OutOfMemoryError so huge outputs succeed cleanly. */
    private fun encodeWithOomRetry(
        bitmap: Bitmap,
        stageFile: java.io.File,
        settings: ImageConversionSettings,
        budget: Long,
        checkCancel: () -> Unit
    ): Pair<Int, Int> {
        var current = bitmap
        var owned = false
        var lastOom: OutOfMemoryError? = null
        try {
            for (attempt in 0 until 4) {
                checkCancel()
                try {
                    BitmapTransforms.encode(current, stageFile, settings, budget, checkCancel)
                    return current.width to current.height
                } catch (oom: OutOfMemoryError) {
                    lastOom = oom
                    stageFile.delete()
                    val nw = (current.width * 0.75).roundToInt().coerceAtLeast(1)
                    val nh = (current.height * 0.75).roundToInt().coerceAtLeast(1)
                    if (nw == current.width && nh == current.height) throw oom
                    val prev = current
                    val prevOwned = owned
                    System.gc()
                    current = Bitmap.createScaledBitmap(prev, nw, nh, true).also {
                        if (!prev.hasAlpha()) it.setHasAlpha(false)
                    }
                    owned = true
                    if (prevOwned && !prev.isRecycled) prev.recycle()
                }
            }
            throw lastOom ?: OutOfMemoryError("Image encode exceeded available memory")
        } finally {
            if (owned && !current.isRecycled) current.recycle()
        }
    }

    private suspend fun extras(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        val planned=if(request.toolId=="img_join") 1 else request.sourceIds.size
        ResourceBudgetPolicy.requireOutputCount(maxOf(1,planned))
        val effectiveBudget = ImageWorkPolicy.effectiveBudget(context.budget.maxBitmapBytes)
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
                            context.sources.withSeekable(id,request.id){ f -> decodeForWork(f, effectiveBudget / 2).use{ d ->
                                val marked=if(logoId!=null){
                                    var r:Bitmap?=null
                                    context.sources.withSeekable(logoId,request.id){ lf ->
                                        val remaining=(effectiveBudget - d.bitmap.allocationByteCount.toLong() - ImageWorkPolicy.bytes(d.bitmap.width,d.bitmap.height)).coerceAtLeast(4L * 1024 * 1024)
                                        BitmapTransforms.decodeSampled(lf,remaining,1600).use{ ld ->
                                            r=BitmapExtras.watermark(d.bitmap,ld.bitmap,settings,effectiveBudget)
                                        }
                                    }
                                    r!!
                                } else BitmapExtras.watermark(d.bitmap,null,settings,effectiveBudget)
                                // Free source bitmap immediately before encoding the watermarked output.
                                if (!d.bitmap.isRecycled) d.bitmap.recycle()
                                try{ encodeWithOomRetry(marked,stage,settings.output,effectiveBudget,context.checkCancellation) } finally { if (!marked.isRecycled) marked.recycle() }
                            } }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                    catch(oom:OutOfMemoryError){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode="LOW_MEMORY") }
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
                        // Inspect headers first, then stream each tile one-by-one and recycle immediately.
                        val infos = request.sourceIds.map { id -> context.checkCancellation(); context.sources.withSeekable(id,request.id) { f ->
                            BitmapTransforms.inspect(f)
                        } }
                        val plan = ImageJoinPolicy.plan(infos.map { it.normalizedWidth to it.normalizedHeight },settings.layout,settings.gapPx,settings.overlapPx)
                        val stripMaxEdge = ImageWorkPolicy.maxEdgeForFormat(settings.output.format, strip = true)
                        var canvasBudget = maxOf(64L * 1024 * 1024, (effectiveBudget * 75L) / 100L)
                        var attemptScale = 1.0
                        var lastOom: OutOfMemoryError? = null
                        var succeeded = false
                        for (attempt in 0 until 5) {
                            context.checkCancellation()
                            val (baseW, baseH) = ImageWorkPolicy.boundedDimensions(
                                plan.width, plan.height, canvasBudget,
                                maxEdge = stripMaxEdge,
                                maxPixels = ImageWorkPolicy.MAX_PIXELS
                            )
                            val canvasW = (baseW * attemptScale).roundToInt().coerceAtLeast(1)
                            val canvasH = (baseH * attemptScale).roundToInt().coerceAtLeast(1)
                            val scaleX = canvasW.toDouble() / plan.width.toDouble()
                            val scaleY = canvasH.toDouble() / plan.height.toDouble()
                            val fullResolution = canvasW == plan.width && canvasH == plan.height
                            val joinPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            var joined: Bitmap? = null
                            try {
                                // joined is filled opaque white with setHasAlpha(false), so JPEG encoding allocates no matte bitmap.
                                val canvasBmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                                joined = canvasBmp
                                val canvas = Canvas(canvasBmp).apply { drawColor(Color.WHITE) }
                                canvasBmp.setHasAlpha(false)
                                val tileBudget = maxOf(16L * 1024 * 1024, effectiveBudget - canvasBmp.allocationByteCount.toLong())
                                for((idx,id) in request.sourceIds.withIndex()) {
                                    context.checkCancellation()
                                    val info = infos[idx]
                                    val placement = plan.placements[idx]
                                    val dstW = (info.normalizedWidth * scaleX).roundToInt().coerceAtLeast(1)
                                    val dstH = ((info.normalizedHeight - placement.trimTop) * scaleY).roundToInt().coerceAtLeast(1)
                                    val dstX = (placement.x * scaleX).roundToInt()
                                    val dstY = (placement.y * scaleY).roundToInt()
                                    val maxTileEdge = if (fullResolution) ImageWorkPolicy.MAX_EDGE_PX else maxOf(dstW, (info.normalizedHeight * scaleY).roundToInt()).coerceAtLeast(64)
                                    context.sources.withSeekable(id,request.id) { f ->
                                        val decoded = if (fullResolution && info.decodePeakBytes <= tileBudget) {
                                            BitmapTransforms.decode(f, tileBudget)
                                        } else {
                                            BitmapTransforms.decodeSampled(f, tileBudget, maxTileEdge)
                                        }
                                        decoded.use { d ->
                                            if (fullResolution && d.bitmap.width == info.normalizedWidth && d.bitmap.height == info.normalizedHeight) {
                                                BitmapExtras.drawJoinPart(canvas, d.bitmap, placement)
                                            } else {
                                                val srcTrim = if (info.normalizedHeight > 0) {
                                                    (placement.trimTop.toLong() * d.bitmap.height / info.normalizedHeight).toInt()
                                                } else 0
                                                val dstRect = Rect(dstX, dstY, dstX + dstW, dstY + dstH)
                                                BitmapExtras.drawJoinPartScaled(canvas, d.bitmap, srcTrim, dstRect, joinPaint)
                                            }
                                        }
                                    }
                                    context.report(JobProgress(JobStage.RUNNING,(idx+1).toLong(),request.sourceIds.size.toLong()))
                                }
                                BitmapTransforms.encode(canvasBmp,stage,settings.output,effectiveBudget,context.checkCancellation)
                                succeeded = true
                                break
                            } catch (oom: OutOfMemoryError) {
                                lastOom = oom
                                stage.delete()
                                attemptScale *= 0.75
                                canvasBudget = (canvasBudget * 3L / 4L).coerceAtLeast(16L * 1024 * 1024)
                                System.gc()
                            } finally {
                                joined?.let { if (!it.isRecycled) it.recycle() }
                            }
                        }
                        if (!succeeded) throw lastOom ?: OutOfMemoryError("Image join exceeded available memory")
                    }
                } catch(cancel:CancellationException) { throw cancel }
                catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                catch(oom:OutOfMemoryError){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode="LOW_MEMORY") }
            }
        }
        return outcomes
    }
}
