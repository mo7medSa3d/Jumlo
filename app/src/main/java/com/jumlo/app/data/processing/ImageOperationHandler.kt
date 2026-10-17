package com.jumlo.app.data.processing

import android.graphics.Bitmap
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

    private suspend fun extras(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        val outcomes=mutableListOf<ItemOutcome>()
        suspend fun publishBytes(key:String,name:String,mime:String,write:(java.io.File)->Unit):ItemOutcome {
            context.outputs.find(request.id,key)?.let{ return ItemOutcome(key,ItemStatus.CREATED,it.id) }
            context.checkCancellation()
            val ticket=context.outputs.reserve(request.id,key,name,mime); write(ticket.stageFile)
            context.checkCancellation()
            val rec=context.outputs.publish(ticket){ context.outputs.inspect(it,ticket.mimeType) }
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
                        val name=SafeNames.basename(outSettings.namePrefix+SafeNames.fromDisplayName(source.displayName,"image").substringBeforeLast('.')+"."+BitmapTransforms.extension(outSettings.format))
                        outcomes+=publishBytes(key,name,BitmapTransforms.mime(outSettings.format)){ stage ->
                            kotlinx.coroutines.runBlocking {
                                context.sources.withSeekable(id,request.id){ f ->
                                    BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use{ d ->
                                        BitmapTransforms.encode(d.bitmap,stage,outSettings,context.budget.maxBitmapBytes)
                                    }
                                }
                                if(!settings.removeAll && preservedAttrs.isNotEmpty() && outSettings.format=="JPEG") {
                                    run {
                                        val outExif=ExifInterface(stage)
                                        preservedAttrs.forEach { (k,v) -> outExif.setAttribute(k,v) }
                                        outExif.saveAttributes()
                                    }
                                }
                            }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                }
            }
            "img_watermark" -> {
                val settings=Json.decodeFromString<ImageWatermarkSettings>(request.settingsJson)
                require(settings.opacityPercent in 1..100) { "INVALID_OPACITY" }
                require(if(settings.useLogo) request.sourceIds.size>=2 else settings.text.isNotBlank()) { "WATERMARK_CONTENT_REQUIRED" }
                val logoId=request.sourceIds.getOrNull(1)?.takeIf{ settings.useLogo }
                request.sourceIds.forEachIndexed{ index,id ->
                    if(settings.useLogo && index==1) return@forEachIndexed
                    val key="image:$index"
                    try {
                        val source=context.sources.get(id)
                        val name=SafeNames.basename(settings.output.namePrefix+SafeNames.fromDisplayName(source.displayName,"image").substringBeforeLast('.')+"."+BitmapTransforms.extension(settings.output.format))
                        outcomes+=publishBytes(key,name,BitmapTransforms.mime(settings.output.format)){ stage ->
                            kotlinx.coroutines.runBlocking { context.sources.withSeekable(id,request.id){ f -> BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use{ d ->
                                val marked=if(logoId!=null){ var r:Bitmap?=null; kotlinx.coroutines.runBlocking{ context.sources.withSeekable(logoId,request.id){ lf -> BitmapTransforms.decode(lf,context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong()).use{ ld -> r=BitmapExtras.watermarkLogo(d.bitmap,ld.bitmap,context.budget.maxBitmapBytes,settings.opacityPercent) } } }; r!! } else BitmapExtras.watermarkText(d.bitmap,settings.text,settings.opacityPercent,context.budget.maxBitmapBytes)
                                try{ BitmapTransforms.encode(marked,stage,settings.output,context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong()) } finally { marked.recycle() }
                            } } }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                }
            }
            "img_join","img_stitch" -> {
                val settings=Json.decodeFromString<ImageJoinSettings>(request.settingsJson)
                val key="joined"
                try {
                    val name=SafeNames.basename(settings.output.namePrefix+"joined."+BitmapTransforms.extension(settings.output.format))
                    outcomes+=publishBytes(key,name,BitmapTransforms.mime(settings.output.format)){ stage ->
                        kotlinx.coroutines.runBlocking {
                            val bitmaps=mutableListOf<Bitmap>()
                            try { for(id in request.sourceIds){
                                context.checkCancellation()
                                val resident=bitmaps.sumOf { it.allocationByteCount.toLong() }
                                context.sources.withSeekable(id,request.id){ f -> BitmapTransforms.decode(f,context.budget.maxBitmapBytes-resident).use{ d -> bitmaps.add(requireNotNull(d.bitmap.copy(Bitmap.Config.ARGB_8888,false)) { "IMAGE_COPY_FAILED" }) } }
                            }
                                val joined=BitmapExtras.join(bitmaps,settings.layout,settings.gapPx,context.budget.maxBitmapBytes,settings.overlapPx)
                                try{ BitmapTransforms.encode(joined,stage,settings.output,context.budget.maxBitmapBytes-bitmaps.sumOf { it.allocationByteCount.toLong() }) } finally { joined.recycle() }
                            } finally { bitmaps.forEach{ if(!it.isRecycled) it.recycle() } }
                        }
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
                    try { val source=context.sources.get(id); val name=SafeNames.basename(settings.output.namePrefix+SafeNames.fromDisplayName(source.displayName,"image").substringBeforeLast('.')+"."+BitmapTransforms.extension(settings.output.format))
                        outcomes+=publishBytes(key,name,BitmapTransforms.mime(settings.output.format)){ stage -> kotlinx.coroutines.runBlocking{ context.sources.withSeekable(id,request.id){ f -> BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use{ d -> val a=BitmapExtras.adjust(d.bitmap,settings.brightness,settings.contrastPercent,settings.saturationPercent,settings.padPx,context.budget.maxBitmapBytes); try{ BitmapTransforms.encode(a,stage,settings.output,context.budget.maxBitmapBytes-d.bitmap.allocationByteCount.toLong()) } finally { a.recycle() } } } } }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                }
            }
            "img_target" -> {
                val settings=Json.decodeFromString<ImageTargetSettings>(request.settingsJson)
                request.sourceIds.forEachIndexed{ index,id ->
                    val key="image:$index"
                    try { val source=context.sources.get(id); val name=SafeNames.basename(settings.output.namePrefix+SafeNames.fromDisplayName(source.displayName,"image").substringBeforeLast('.')+".jpg")
                        outcomes+=publishBytes(key,name,"image/jpeg"){ stage -> kotlinx.coroutines.runBlocking{ context.sources.withSeekable(id,request.id){ f -> BitmapTransforms.decode(f,context.budget.maxBitmapBytes).use{ d ->
                            var lo=1; var hi=100; var best:java.io.File?=null
                            val tmp=java.io.File(stage.parentFile,"qtmp.jpg")
                            while(lo<=hi){ context.checkCancellation(); val mid=(lo+hi)/2; BitmapTransforms.encode(d.bitmap,tmp,ImageConversionSettings(format="JPEG",quality=mid),context.budget.maxBitmapBytes); if(tmp.length()<=settings.targetBytes){ best=java.io.File(stage.parentFile,"qbest.jpg"); tmp.copyTo(best,true); lo=mid+1 } else hi=mid-1 }
                            val chosen=best ?: run { tmp.delete();throw OperationFailure("TARGET_SIZE_UNREACHABLE") }
                            try { chosen.copyTo(stage,overwrite=true) } finally { tmp.delete(); chosen.delete() }
                        } } } }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(e:Exception){ outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                }
            }
            "img_compare" -> {
                require(request.sourceIds.size==2){"TWO_IMAGES_REQUIRED"}
                val key="compare-report"
                context.outputs.find(request.id,key)?.let{ outcomes+=ItemOutcome(key,ItemStatus.CREATED,it.id) } ?: run {
                    val ticket=context.outputs.reserve(request.id,key,"image-compare.txt","text/plain")
                    kotlinx.coroutines.runBlocking {
                        var dims=""; var diff=-1.0
                        context.sources.withSeekable(request.sourceIds[0],request.id){ a -> context.sources.withSeekable(request.sourceIds[1],request.id){ b ->
                            BitmapTransforms.decode(a,context.budget.maxBitmapBytes).use{ da -> BitmapTransforms.decode(b,context.budget.maxBitmapBytes-da.bitmap.allocationByteCount.toLong()).use{ db -> dims="${da.bitmap.width}x${da.bitmap.height} vs ${db.bitmap.width}x${db.bitmap.height}"; diff=BitmapExtras.meanDifference(da.bitmap,db.bitmap) } }
                        } }
                        ticket.stageFile.writeText("dimensions=$dims\nmeanAbsDifference=${if(diff<0) "SIZE_MISMATCH" else diff.toString()}\nidentical=${diff==0.0}\n")
                    }
                    val rec=context.outputs.publish(ticket){ OutputMetadata() }; outcomes+=ItemOutcome(key,ItemStatus.CREATED,rec.id)
                }
            }
        }
        return outcomes
    }
}
