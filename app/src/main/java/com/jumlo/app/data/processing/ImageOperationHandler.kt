package com.jumlo.app.data.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.model.*
import kotlinx.coroutines.CancellationException
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

    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
        val resize=if(request.toolId=="img_02") Json.decodeFromString<ImageResizeSettings>(request.settingsJson) else null
        val crop=if(request.toolId=="img_03") Json.decodeFromString<ImageCropSettings>(request.settingsJson) else null
        val output=when(request.toolId) {
            "img_01" -> Json.decodeFromString<ImageConversionSettings>(request.settingsJson)
            "img_02" -> resize!!.output
            "img_03" -> crop!!.output
            "img_04" -> Json.decodeFromString<ImageCompressionSettings>(request.settingsJson).output
            "img_exif","img_watermark","img_join","img_adjust","img_target","img_compare","img_stitch" -> return extras(request,context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
        require(output.stripMetadata) { "METADATA_PRESERVATION_UNQUALIFIED" }
        // One published file per source; the published-output budget is a hard product limit.
        ResourceBudgetPolicy.requireOutputCount(request.sourceIds.size)
        val outcomes=mutableListOf<ItemOutcome>()
        for ((index, id) in request.sourceIds.withIndex()) {
            context.checkCancellation(); val key="image:$index"
            val existing=context.outputs.find(request.id,key)
            if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
            else try {
                val source=context.sources.get(id)
                val isCompress = request.toolId == "img_04"
                val effectiveFormat = if (isCompress && output.format == "PNG") "WEBP" else output.format
                val name = resolveOutputFileName(output.copy(format = effectiveFormat), source.displayName, index, request.sourceIds.size)
                val ticket=context.outputs.reserve(request.id,key,name,BitmapTransforms.mime(effectiveFormat))
                val record=context.sources.withSeekable(id,request.id) { file ->
                    val originalSize = file.length()
                    val originalInfo = BitmapTransforms.inspect(file)
                    BitmapTransforms.decode(file,context.budget.maxBitmapBytes).use { decoded ->
                        val src=decoded.bitmap; var transformed:Bitmap?=null
                        try {
                            val bmp=when {
                                resize!=null -> {
                                    val dimensions=ImageResizePolicy.targetDimensions(
                                        originalInfo.normalizedWidth,originalInfo.normalizedHeight,
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
                            if (isCompress) {
                                BitmapTransforms.encodeGuaranteedSmaller(
                                    bmp, ticket.stageFile, originalSize,
                                    output.copy(format = effectiveFormat),
                                    context.budget.maxBitmapBytes - resident,
                                    context.checkCancellation
                                )
                            } else {
                                BitmapTransforms.encode(bmp,ticket.stageFile,output,context.budget.maxBitmapBytes-resident)
                            }
                            context.outputs.publish(ticket) { context.outputs.inspect(it,ticket.mimeType,originalSizeBytes=originalSize) }
                        } finally { if(transformed!==src) transformed?.recycle() }
                    }
                }
                outcomes+=ItemOutcome(key,ItemStatus.CREATED,record.id)
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:ImageSizeBoundException) {
                if (request.toolId == "img_04") outcomes+=ItemOutcome(key,ItemStatus.UNCHANGED,errorCode="IMAGE_NOT_SMALLER")
                else outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e))
            }
            catch(e:Exception) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
            context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
        }
        return outcomes
    }

    private suspend fun extras(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        // EXIF writes a report plus a re-encoded copy per source; the rest publish one file per source.
        val perSource=if(request.toolId=="img_exif") 2 else 1
        val planned=if(request.toolId in setOf("img_join","img_stitch")) 2 else request.sourceIds.size*perSource
        ResourceBudgetPolicy.requireOutputCount(maxOf(1,planned))
        val outcomes=mutableListOf<ItemOutcome>()
        suspend fun publishBytes(key:String,name:String,mime:String,originalBytes:Long?=null,write:suspend (java.io.File)->Unit):ItemOutcome {
            context.outputs.find(request.id,key)?.let{ return ItemOutcome(key,ItemStatus.CREATED,it.id) }
            context.checkCancellation()
            val ticket=context.outputs.reserve(request.id,key,name,mime)
            write(ticket.stageFile)
            context.checkCancellation()
            val rec=context.outputs.publish(ticket){ context.outputs.inspect(it,ticket.mimeType,originalSizeBytes=originalBytes) }
            return ItemOutcome(key,ItemStatus.CREATED,rec.id)
        }
        when(request.toolId){
            "img_exif" -> {
                val settings=Json.decodeFromString<ImageExifSettings>(request.settingsJson)
                val safeNonGpsTags=listOf(
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_SOFTWARE,
                    ExifInterface.TAG_ARTIST,
                    ExifInterface.TAG_COPYRIGHT,
                    ExifInterface.TAG_IMAGE_DESCRIPTION
                )
                val inspectTags=safeNonGpsTags + listOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_ORIENTATION
                )
                request.sourceIds.forEachIndexed{ index,id ->
                    val key="image:$index"
                    try {
                        val source=context.sources.get(id)
                        var preservedAttrs=emptyMap<String,String>()
                        val report=context.sources.withSeekable(id,request.id){ f ->
                            val ex=ExifInterface(f)
                            if(!settings.removeAll) {
                                val kept=mutableMapOf<String,String>()
                                for(tag in safeNonGpsTags) ex.getAttribute(tag)?.let { kept[tag]=it }
                                if(!settings.removeLocation) {
                                    for(gpsTag in listOf(ExifInterface.TAG_GPS_LATITUDE,ExifInterface.TAG_GPS_LATITUDE_REF,ExifInterface.TAG_GPS_LONGITUDE,ExifInterface.TAG_GPS_LONGITUDE_REF)) {
                                        ex.getAttribute(gpsTag)?.let { kept[gpsTag]=it }
                                    }
                                }
                                preservedAttrs=kept
                            }
                            inspectTags.joinToString("\n"){ tag -> "$tag=${ex.getAttribute(tag) ?: "-"}" }
                        }
                        context.outputs.find(request.id,"report:$index")?.let{ outcomes+=ItemOutcome("report:$index",ItemStatus.CREATED,it.id) } ?: run {
                            val ticket=context.outputs.reserve(request.id,"report:$index",SafeNames.basename("exif_report_${index+1}.txt"),"text/plain")
                            ticket.stageFile.writeText(report+"\nremoveLocation=${settings.removeLocation}\nremoveAll=${settings.removeAll}\nretainedTags=${preservedAttrs.keys.joinToString(",")}\n")
                            val rec=context.outputs.publish(ticket){ OutputMetadata() }; outcomes+=ItemOutcome("report:$index",ItemStatus.CREATED,rec.id)
                        }
                        val outFormat=if(!settings.removeAll && settings.output.format!="JPEG") "JPEG" else settings.output.format
                        val outSettings=settings.output.copy(format=outFormat)
                        val name=resolveOutputFileName(outSettings,source.displayName,index,request.sourceIds.size)
                        outcomes+=publishBytes(key,name,BitmapTransforms.mime(outSettings.format),source.sizeBytes){ stage ->
                            context.sources.withSeekable(id,request.id){ f ->
                                BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use{ d ->
                                    BitmapTransforms.encode(d.bitmap,stage,outSettings,context.budget.maxBitmapBytes)
                                }
                            }
                            if(!settings.removeAll && preservedAttrs.isNotEmpty() && outSettings.format=="JPEG") {
                                val outExif=ExifInterface(stage)
                                preservedAttrs.forEach { (k,v) -> outExif.setAttribute(k,v) }
                                outExif.saveAttributes()
                            }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                    context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
                }
            }
            "img_watermark" -> {
                val settings=Json.decodeFromString<ImageWatermarkSettings>(request.settingsJson)
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
                                        BitmapTransforms.decode(lf,context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong()).use{ ld ->
                                            r=BitmapExtras.watermarkLogo(d.bitmap,ld.bitmap,context.budget.maxBitmapBytes,settings.opacityPercent)
                                        }
                                    }
                                    r!!
                                } else BitmapExtras.watermarkText(d.bitmap,settings.text,settings.opacityPercent,context.budget.maxBitmapBytes)
                                try{ BitmapTransforms.encode(marked,stage,settings.output,context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong()) } finally { marked.recycle() }
                            } }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                    context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
                }
            }
            "img_join","img_stitch" -> {
                val settings=Json.decodeFromString<ImageJoinSettings>(request.settingsJson)
                val key="joined"
                try {
                    val firstSource = runCatching { context.sources.get(request.sourceIds.first()).displayName }.getOrDefault("joined")
                    val name=resolveOutputFileName(settings.output,firstSource,0,1)
                    var totalInputBytes=0L
                    outcomes+=publishBytes(key,name,BitmapTransforms.mime(settings.output.format)){ stage ->
                        // Inspect first, then keep only one decoded input beside the output.
                        // A strip of many pages no longer retains a copy of every source.
                        val infos = request.sourceIds.map { id -> context.sources.withSeekable(id,request.id) { f ->
                            totalInputBytes += f.length()
                            BitmapTransforms.inspect(f)
                        } }
                        val plan = ImageJoinPolicy.plan(infos.map { it.normalizedWidth to it.normalizedHeight },settings.layout,settings.gapPx,settings.overlapPx)
                        val outputBytes = ImageWorkPolicy.bytes(plan.width,plan.height)
                        val decodePeak = infos.maxOf { it.decodePeakBytes }
                        require(outputBytes <= context.budget.maxBitmapBytes && decodePeak <= context.budget.maxBitmapBytes-outputBytes) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
                        // JPEG matting can require one additional output-sized bitmap.
                        if(settings.output.format=="JPEG") ImageWorkPolicy.requireFits(plan.width,plan.height,outputBytes,context.budget.maxBitmapBytes)
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
                            BitmapTransforms.encode(joined,stage,settings.output,context.budget.maxBitmapBytes)
                        } finally { joined.recycle() }
                    }
                    if(request.toolId=="img_stitch") {
                        val capKey="codec-capabilities"
                        context.outputs.find(request.id,capKey)?.let { outcomes+=ItemOutcome(capKey,ItemStatus.CREATED,it.id) } ?: run {
                            val capTicket=context.outputs.reserve(request.id,capKey,"codec_capabilities.txt","text/plain")
                            val report=BitmapExtras.codecCapabilities().joinToString("\n") {
                                "${it.format}|decode=${it.canDecode}|encode=${it.canEncode}|animation=${it.canPreserveAnimation}|api=${it.supportedApiRange}"
                            }
                            capTicket.stageFile.writeText(report+"\n")
                            val rec=context.outputs.publish(capTicket){ OutputMetadata() }
                            outcomes+=ItemOutcome(capKey,ItemStatus.CREATED,rec.id)
                        }
                    }
                } catch(cancel:CancellationException) { throw cancel }
                catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
            }
            "img_adjust" -> {
                val settings=Json.decodeFromString<ImageAdjustSettings>(request.settingsJson)
                request.sourceIds.forEachIndexed{ index,id ->
                    val key="image:$index"
                    try {
                        val source=context.sources.get(id)
                        val name=resolveOutputFileName(settings.output,source.displayName,index,request.sourceIds.size)
                        outcomes+=publishBytes(key,name,BitmapTransforms.mime(settings.output.format),source.sizeBytes){ stage ->
                            context.sources.withSeekable(id,request.id){ f ->
                                BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use{ d ->
                                    val a=BitmapExtras.adjust(d.bitmap,settings.brightness,settings.contrastPercent,settings.saturationPercent,settings.padPx,context.budget.maxBitmapBytes)
                                    try{ BitmapTransforms.encode(a,stage,settings.output,context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong()) } finally { a.recycle() }
                                }
                            }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                    context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
                }
            }
            "img_target" -> {
                val settings=Json.decodeFromString<ImageTargetSettings>(request.settingsJson)
                request.sourceIds.forEachIndexed{ index,id ->
                    val key="image:$index"
                    try {
                        val source=context.sources.get(id)
                        val name=resolveOutputFileName(settings.output.copy(format="JPEG"),source.displayName,index,request.sourceIds.size,"jpg")
                        var origSize=source.sizeBytes
                        outcomes+=publishBytes(key,name,"image/jpeg",origSize){ stage ->
                            context.sources.withSeekable(id,request.id){ f ->
                                origSize=f.length()
                                require(settings.targetBytes > 0L && settings.targetBytes < Long.MAX_VALUE) { "INVALID_IMAGE_SIZE_BOUND" }
                                // Target Size is an inclusive requested cap; it need not be
                                // smaller than an already tiny source.
                                BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use { d ->
                                    BitmapTransforms.encodeGuaranteedSmaller(
                                        d.bitmap,stage,settings.targetBytes+1L,
                                        settings.output.copy(format="JPEG"),context.budget.maxBitmapBytes,
                                        context.checkCancellation
                                    )
                                }
                                require(stage.length() in 1..settings.targetBytes) { "IMAGE_SIZE_BOUND_UNMET" }
                            }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                    context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
                }
            }
            "img_compare" -> {
                require(request.sourceIds.size==2){"TWO_IMAGES_REQUIRED"}
                val settings=runCatching { Json.decodeFromString<ImageAdjustSettings>(request.settingsJson) }.getOrNull()
                val key="compare-report"
                val reportName = settings?.output?.customOutputName?.takeIf { it.isNotBlank() }
                    ?.let { SafeNames.fromDisplayName(if(it.endsWith(".txt",true)) it else "$it.txt", "image-compare.txt") }
                    ?: "image-compare.txt"
                context.outputs.find(request.id,key)?.let{ outcomes+=ItemOutcome(key,ItemStatus.CREATED,it.id) } ?: run {
                    val ticket=context.outputs.reserve(request.id,key,reportName,"text/plain")
                    var dims=""; var diff=-1.0
                    context.sources.withSeekable(request.sourceIds[0],request.id){ a -> context.sources.withSeekable(request.sourceIds[1],request.id){ b ->
                        BitmapTransforms.decode(a,context.budget.maxBitmapBytes).use{ da -> BitmapTransforms.decode(b,context.budget.maxBitmapBytes-da.bitmap.allocationByteCount.toLong()).use{ db ->
                            dims="${da.bitmap.width}x${da.bitmap.height} vs ${db.bitmap.width}x${db.bitmap.height}"
                            diff=BitmapExtras.meanDifference(da.bitmap,db.bitmap,context.budget.maxBitmapBytes)
                            // The advertised visual difference map is a real second output.
                            val mapKey="compare-diff"
                            val map=BitmapExtras.diffMap(da.bitmap,db.bitmap,context.budget.maxBitmapBytes)
                            if(map!=null) {
                                val mapTicket=context.outputs.reserve(request.id,mapKey,"image-compare-diff.png","image/png")
                                try {
                                    BitmapTransforms.encode(map,mapTicket.stageFile,ImageConversionSettings(format="PNG",namePrefix="compare_diff_"),context.budget.maxBitmapBytes)
                                    val mapRecord=context.outputs.publish(mapTicket){ context.outputs.inspect(it,"image/png") }
                                    outcomes+=ItemOutcome(mapKey,ItemStatus.CREATED,mapRecord.id)
                                } finally { map.recycle() }
                            }
                        } }
                    } }
                    ticket.stageFile.writeText("dimensions=$dims\nmeanAbsDifference=${if(diff<0) "SIZE_MISMATCH" else diff.toString()}\nidentical=${diff==0.0}\n")
                    val rec=context.outputs.publish(ticket){ OutputMetadata() }; outcomes+=ItemOutcome(key,ItemStatus.CREATED,rec.id)
                }
            }
        }
        return outcomes
    }
}
