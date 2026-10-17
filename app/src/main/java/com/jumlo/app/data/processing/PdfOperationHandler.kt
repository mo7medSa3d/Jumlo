package com.jumlo.app.data.processing

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.pdf.*
import com.jumlo.app.model.*
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton class PdfOperationHandler @Inject constructor(private val secrets:com.jumlo.app.data.source.EphemeralSecretStore):OperationHandler {
    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = when(request.toolId) {
        "pdf_02" -> imagesToPdf(request,context)
        "pdf_07" -> pagesToImages(request,context)
        "pdf_03" -> structural(request,context,"merge")
        "pdf_05" -> structural(request,context,"split")
        "pdf_04" -> structural(request,context,"organize")
        "pdf_06" -> structural(request,context,"extract")
        "pdf_crop" -> structural(request,context,"crop")
        "pdf_compress" -> structural(request,context,"compress")
        "pdf_stamp" -> structural(request,context,"stamp")
        "pdf_annotate" -> structural(request,context,"annotate")
        "pdf_signature" -> structural(request,context,"signature")
        "pdf_metadata" -> structural(request,context,"metadata")
        "pdf_protect" -> structural(request,context,"protect")
        "pdf_forms" -> structural(request,context,"forms")
        "pdf_compare" -> structural(request,context,"compare")
        "pdf_redact" -> structural(request,context,"redact")
        else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
    }

    private fun pages(spec:String,count:Int):List<Int> = if(spec.trim()=="all"||spec.isBlank()) (1..count).toList() else PageSelection.parse(spec,count)

    private fun openUnencrypted(file: File): com.tom_roush.pdfbox.pdmodel.PDDocument {
        val doc = try {
            PdfBoxBackend.open(file, null)
        } catch (_: InvalidPasswordException) {
            throw OperationFailure("PDF_ENCRYPTED")
        }
        if (doc.isEncrypted) {
            doc.close()
            throw OperationFailure("PDF_ENCRYPTED")
        }
        return doc
    }

    private fun parseFormEntries(rawInput: String): Map<String, FormEntry> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return emptyMap()
        val values = mutableMapOf<String, FormEntry>()
        fun toFormEntry(v: String): FormEntry = when {
            v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true) -> FormEntry.Check(v.toBoolean())
            v.toIntOrNull() != null -> FormEntry.Radio(v.toInt())
            else -> FormEntry.Text(v)
        }
        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            obj.keys().forEach { k -> values[k] = toFormEntry(obj.getString(k)) }
        } else {
            trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && '=' in it }.forEach { line ->
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim()
                if (key.isNotEmpty()) values[key] = toFormEntry(value)
            }
        }
        return values
    }

    private suspend fun structural(request:JobRequest,context:ExecutionContext,kind:String):List<ItemOutcome> {
        val json: (String) -> JSONObject = { JSONObject(it) }
        val s = json(request.settingsJson)
        fun name(fallback:String):String = SafeNames.basename(s.optString("displayName",fallback).let { if(it.endsWith(".pdf",true)) it else "$it.pdf" })
        suspend fun publishOne(key:String,display:String,stage:suspend (File)->Unit):List<ItemOutcome> {
            context.outputs.find(request.id,key)?.let { return listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) }
            val ticket=context.outputs.reserve(request.id,key,display,"application/pdf")
            try { stage(ticket.stageFile) } catch(e:Exception) { throw e }
            val record=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf") }
            return listOf(ItemOutcome(key,ItemStatus.CREATED,record.id))
        }
        return when(kind){
            "merge" -> {
                require(request.sourceIds.isNotEmpty()){"ONE_PDF_REQUIRED"}
                publishOne("merged",name("Merged.pdf")){ out ->
                    seekAll(context,request,request.sourceIds){ files ->
                        files.forEach { f -> openUnencrypted(f).close() }
                        PdfBoxBackend.merge(files.map{ PdfInput(it) },out)
                    }
                }
            }
            "split" -> {
                require(request.sourceIds.size==1){"ONE_PDF_REQUIRED"}
                val outcomes=mutableListOf<ItemOutcome>()
                context.sources.withSeekable(request.sourceIds.single(),request.id){ src ->
                    openUnencrypted(src).use{ doc ->
                        val count=doc.numberOfPages
                        val groups:List<Pair<Int,Int>> = if(s.optString("mode","RANGES")=="EVERY"){
                            val every=s.optInt("every",1).coerceAtLeast(1); (0 until count step every).map{ it to minOf(every,count-it) }
                        } else {
                            val selected=pages(s.optString("pages","1"),count); selected.map{ (it-1) to 1 }
                        }
                        groups.forEachIndexed{ gi,g ->
                            val key="part:${gi+1}"
                            context.outputs.find(request.id,key)?.let{ outcomes+=ItemOutcome(key,ItemStatus.CREATED,it.id); return@forEachIndexed }
                            val ticket=context.outputs.reserve(request.id,key,name("Split-${gi+1}.pdf"),"application/pdf")
                            PdfBoxBackend.split(doc,listOf(PageGroup(g.first,g.second)),listOf(ticket.stageFile))
                            val rec=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf") }
                            outcomes+=ItemOutcome(key,ItemStatus.CREATED,rec.id)
                        }
                    }
                }
                outcomes
            }
            "extract","organize" -> {
                require(request.sourceIds.size==1){"ONE_PDF_REQUIRED"}
                publishOne(kind,name(if(kind=="extract")"Extracted.pdf" else "Organized.pdf")){ out ->
                    context.sources.withSeekable(request.sourceIds.single(),request.id){ src ->
                        openUnencrypted(src).use{ doc ->
                            val count=doc.numberOfPages
                            if(kind=="extract"){
                                val selected=pages(s.optString("pages","1"),count)
                                PdfBoxBackend.extract(doc,selected.map{ it-1 },out)
                            } else {
                                val orderSpec=s.optString("order","")
                                val order=if(orderSpec.isBlank()) (1..count).toList() else orderSpec.split(",").mapNotNull{ it.trim().toIntOrNull() }
                                require(order.isNotEmpty() && order.all{ it in 1..count }){"INVALID_PAGE_INDEX"}
                                val rotateSpec=s.optString("rotatePages","")
                                val rotateSet=if(rotateSpec.isBlank()) emptySet() else pages(rotateSpec,count).toSet()
                                val degrees=s.optInt("rotateDegrees",90)
                                PdfBoxBackend.organize(doc,order.map{ OrganizeOp(it-1, if(it in rotateSet) degrees else 0) },out)
                            }
                        }
                    }
                }
            }
            "crop" -> singleDoc(request,context,s,name("Cropped.pdf")){ doc,out ->
                val selected=pages(s.optString("pages","all"),doc.numberOfPages)
                selected.forEach{ p -> PdfBoxBackend.setCrop(doc,p-1,s.optDouble("left",0.0),s.optDouble("top",0.0),s.optDouble("right",1.0),s.optDouble("bottom",1.0)) }
                doc.save(out)
            }
            "compress" -> singleDoc(request,context,s,name("Compressed.pdf")){ doc,out ->
                PdfBoxBackend.compressImages(doc,s.optInt("maxDpi",150),s.optInt("jpegQuality",80)); doc.save(out)
            }
            "stamp" -> singleDoc(request,context,s,name("Stamped.pdf")){ doc,out ->
                val selected=pages(s.optString("pages","all"),doc.numberOfPages).map{ it-1 }
                val text=s.optString("text","")
                PdfBoxBackend.stampWatermarkAndNumbers(doc,selected,text,s.optBoolean("pageNumbers",false))
                doc.save(out)
            }
            "annotate" -> singleDoc(request,context,s,name("Annotated.pdf")){ doc,out ->
                val page=s.optInt("page",1); val text=s.optString("text",""); val x=s.optDouble("x",0.1); val y=s.optDouble("y",0.1)
                require(text.isNotBlank()){"INVALID_ANNOTATION"}
                if(s.optBoolean("asAnnotation",false)) PdfBoxBackend.addTextAnnotation(doc,page-1,text,x,y)
                else PdfBoxBackend.overlayText(doc,listOf(page-1),text,x,y,12f,0.1f)
                doc.save(out)
            }
            "signature" -> {
                require(request.sourceIds.size==2){"PDF_AND_SIGNATURE_IMAGE_REQUIRED"}
                publishOne("signed",name("Signed.pdf")){ out ->
                    seekAll(context,request,request.sourceIds){ files ->
                        openUnencrypted(files[0]).use{ doc ->
                            PdfBoxBackend.stampImage(doc,s.optInt("page",1)-1,files[1],s.optDouble("left",0.6),s.optDouble("top",0.75),s.optDouble("right",0.95),s.optDouble("bottom",0.95))
                            doc.save(out)
                        }
                    }
                }
            }
            "metadata" -> {
                require(request.sourceIds.size in 1..2){"ONE_OR_TWO_PDFS_REQUIRED"}
                val metaMap=mapOf("title" to s.optString("title",""),"author" to s.optString("author",""),"subject" to s.optString("subject",""),"keywords" to s.optString("keywords",""))
                if(request.sourceIds.size==2){
                    publishOne("document",name("Document.pdf")){ out ->
                        seekAll(context,request,request.sourceIds){ files ->
                            val tempInserted=File(out.parentFile,"inserted-tmp.pdf")
                            try {
                                openUnencrypted(files[0]).use{ baseDoc ->
                                    openUnencrypted(files[1]).close()
                                    val insertPos=(s.optInt("insertAtPage",1)-1).coerceIn(0,baseDoc.numberOfPages)
                                    PdfBoxBackend.insert(baseDoc,PdfInput(files[1]),insertPos,tempInserted)
                                }
                                openUnencrypted(tempInserted).use{ mergedDoc ->
                                    PdfBoxBackend.setMetadata(mergedDoc,metaMap)
                                    mergedDoc.save(out)
                                }
                            } finally { tempInserted.delete() }
                        }
                    }
                } else {
                    singleDoc(request,context,s,name("Document.pdf")){ doc,out ->
                        PdfBoxBackend.setMetadata(doc,metaMap)
                        doc.save(out)
                    }
                }
            }
            "protect" -> {
                require(request.sourceIds.size==1){"ONE_PDF_REQUIRED"}
                val handle=s.optString("secretHandle","")
                val rawChars=secrets.copy(handle) ?: throw OperationFailure("SECRET_EXPIRED")
                try {
                    val raw=String(rawChars)
                    val parts=raw.split("\n",limit=2)
                    val user=parts.getOrElse(0){""}
                    val owner=parts.getOrElse(1){""}
                    val remove=s.optBoolean("removeProtection",false)
                    publishOne("document",name(if(remove) "Unlocked.pdf" else "Protected.pdf")){ out ->
                        context.sources.withSeekable(request.sourceIds.single(),request.id){ src ->
                            val openPw=(user.ifEmpty { owner }).toCharArray()
                            try {
                                PdfBoxBackend.open(src,if(remove) openPw else null).use{ doc ->
                                    if(remove) PdfBoxBackend.removeProtection(doc,out)
                                    else {
                                        require(owner.isNotEmpty()){"OWNER_PASSWORD_REQUIRED"}
                                        val userArr=user.toCharArray(); val ownerArr=owner.toCharArray()
                                        try { PdfBoxBackend.protect(doc,userArr,ownerArr,out) }
                                        finally { userArr.fill('\u0000'); ownerArr.fill('\u0000') }
                                    }
                                }
                            } finally { openPw.fill('\u0000') }
                        }
                    }
                } finally { rawChars.fill('\u0000') }
            }
            "forms" -> singleDoc(request,context,s,name("Form.pdf")){ doc,out ->
                val values = parseFormEntries(s.optString("fieldsJson","{}"))
                if (values.isNotEmpty()) {
                    PdfBoxBackend.fillForm(doc,values)
                }
                if(s.optBoolean("flatten",false)) PdfBoxBackend.flattenForm(doc,out) else doc.save(out)
            }
            "compare" -> {
                require(request.sourceIds.size==2){"TWO_PDFS_REQUIRED"}
                val key="compare-report"
                context.outputs.find(request.id,key)?.let{ listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) } ?: run {
                    val ticket=context.outputs.reserve(request.id,key,"compare-report.txt","text/plain")
                    seekAll(context,request,request.sourceIds){ files ->
                        val result=openUnencrypted(files[0]).use{ da -> openUnencrypted(files[1]).use{ db -> PdfBoxBackend.compareText(da,db) } }
                        ticket.stageFile.writeText("verdict=${result.verdict}\npagesA=${result.pageCountA}\npagesB=${result.pageCountB}\nfirstDifferentPage=${result.firstDifferentPage ?: "-"}\n")
                    }
                    val rec=context.outputs.publish(ticket){ OutputMetadata() }
                    listOf(ItemOutcome(key,ItemStatus.CREATED,rec.id))
                }
            }
            "redact" -> singleDoc(request,context,s,name("Redacted.pdf")){ doc,out ->
                val page=s.optInt("page",1)
                val region=RedactRegion(s.optDouble("left",0.0),s.optDouble("top",0.0),s.optDouble("right",1.0),s.optDouble("bottom",0.2))
                val result=PdfBoxBackend.redact(doc,mapOf((page-1) to listOf(region)))
                require(result.leftoverText.isEmpty() && result.pageFallbacks.isEmpty()){"REDACTION_VERIFICATION_FAILED"}
                val verify=s.optString("verifyText","")
                doc.save(out)
                if(verify.isNotBlank()){
                    openUnencrypted(out).use{ check -> require(!PdfBoxBackend.pageTexts(check).joinToString(" ").contains(verify)){"REDACTION_VERIFICATION_FAILED"} }
                }
            }
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }
    private suspend fun seekAll(context:ExecutionContext,request:JobRequest,ids:List<String>,block:suspend (List<File>)->Unit){ suspend fun rec(i:Int,acc:MutableList<File>){ if(i>=ids.size){ block(acc.toList()); return }; context.sources.withSeekable(ids[i],request.id){ f -> acc.add(f); rec(i+1,acc); acc.removeAt(acc.lastIndex) } }; rec(0,mutableListOf()) }
    private suspend fun singleDoc(request:JobRequest,context:ExecutionContext,s:JSONObject,display:String,block:(com.tom_roush.pdfbox.pdmodel.PDDocument,File)->Unit):List<ItemOutcome>{
        require(request.sourceIds.size==1){"ONE_PDF_REQUIRED"}
        val key="document"
        context.outputs.find(request.id,key)?.let{ return listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) }
        val ticket=context.outputs.reserve(request.id,key,display,"application/pdf")
        context.sources.withSeekable(request.sourceIds.single(),request.id){ src -> openUnencrypted(src).use{ block(it,ticket.stageFile) } }
        val record=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf") }
        return listOf(ItemOutcome(key,ItemStatus.CREATED,record.id))
    }

    private suspend fun imagesToPdf(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        val settings=Json.decodeFromString<ImagesToPdfSettings>(request.settingsJson)
        require(request.sourceIds.isNotEmpty() && request.sourceIds.size <= context.budget.maxOutputFiles &&
            settings.paperWidthPt in 72..14400 && settings.paperHeightPt in 72..14400 && settings.marginMm>=0) { "INVALID_PDF_SETTINGS" }
        val margin=settings.marginMm*72f/25.4f
        require(margin*2<minOf(settings.paperWidthPt,settings.paperHeightPt) && settings.orientation in setOf("AUTO","PORTRAIT","LANDSCAPE")) { "INVALID_PDF_SETTINGS" }
        context.outputs.find(request.id,"document")?.let { return listOf(ItemOutcome("document",ItemStatus.CREATED,it.id)) }
        val ticket=context.outputs.reserve(request.id,"document",SafeNames.basename(settings.displayName.let { if(it.endsWith(".pdf",true)) it else "$it.pdf" }),"application/pdf")
        // One decoded bitmap is resident at a time and is recycled by use{}.
        // Do not add released pages to the next page's memory budget.
        val document=PdfDocument()
        try {
        for ((index, id) in request.sourceIds.withIndex()) {
                context.checkCancellation()
                context.sources.withSeekable(id,request.id) { file ->
                    BitmapTransforms.decode(file,context.budget.maxBitmapBytes).use { decoded ->
                        val bitmap=decoded.bitmap
                        val landscape=settings.orientation=="LANDSCAPE" || (settings.orientation=="AUTO" && bitmap.width>bitmap.height)
                        val w=if(landscape) maxOf(settings.paperWidthPt,settings.paperHeightPt) else minOf(settings.paperWidthPt,settings.paperHeightPt)
                        val h=if(landscape) minOf(settings.paperWidthPt,settings.paperHeightPt) else maxOf(settings.paperWidthPt,settings.paperHeightPt)
                        val page=document.startPage(PdfDocument.PageInfo.Builder(w,h,index+1).create())
                        try {
                            val canvas=page.canvas; canvas.drawColor(Color.WHITE)
                            val aw=w-2*margin; val ah=h-2*margin
                            val scale=if(settings.fill) maxOf(aw/bitmap.width,ah/bitmap.height) else minOf(aw/bitmap.width,ah/bitmap.height)
                            val dw=bitmap.width*scale; val dh=bitmap.height*scale
                            canvas.save(); canvas.clipRect(margin,margin,w-margin,h-margin)
                            canvas.drawBitmap(bitmap,null,RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2),Paint(Paint.FILTER_BITMAP_FLAG))
                            canvas.restore()
                        } finally { document.finishPage(page) }
                    }
                }
                context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
            }
            context.checkCancellation()
            context.report(JobProgress(JobStage.FINALIZING,request.sourceIds.size.toLong(),request.sourceIds.size.toLong()))
            FileOutputStream(ticket.stageFile).use { document.writeTo(it); it.fd.sync() }
        } finally { document.close() }
        val record=context.outputs.publish(ticket) { file -> context.outputs.inspect(file,"application/pdf").also { require(it.pageCount==request.sourceIds.size) { "PDF_VERIFY_FAILED" } } }
        return listOf(ItemOutcome("document",ItemStatus.CREATED,record.id))
    }
    private suspend fun pagesToImages(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        require(request.sourceIds.size==1) { "ONE_PDF_REQUIRED" }
        val settings=Json.decodeFromString<PdfImagesSettings>(request.settingsJson)
        require(settings.dpi in 36..600 && settings.format in setOf("JPEG","PNG") && settings.quality in 1..100) { "INVALID_RASTER_SETTINGS" }
        return context.sources.withSeekable(request.sourceIds.single(),request.id) { source ->
            ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val pages=PageSelection.parse(if(settings.pages=="all") "1-${renderer.pageCount}" else settings.pages,renderer.pageCount)
                    require(pages.size<=context.budget.maxOutputFiles) { "OUTPUT_COUNT_BUDGET_EXCEEDED" }
                    val outcomes=mutableListOf<ItemOutcome>()
                    pages.forEachIndexed { index,number ->
                        context.checkCancellation(); val key="page:$number"
                        val existing=context.outputs.find(request.id,key)
                        if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
                        else try {
                            renderer.openPage(number-1).use { page ->
                                val width=(page.width*settings.dpi/72.0).roundToInt(); val height=(page.height*settings.dpi/72.0).roundToInt()
                                MemoryBudget.requireBitmaps(width,height,1,context.budget.maxBitmapBytes)
                                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap,null,Matrix().apply { setScale(width.toFloat()/page.width,height.toFloat()/page.height) },PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    if(settings.grayscale) {
                                        val paint=Paint().apply { colorFilter=ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
                                        MemoryBudget.requireBitmaps(width,height,2,context.budget.maxBitmapBytes)
                                        val gray=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                                        try { Canvas(gray).drawBitmap(bitmap,0f,0f,paint); Canvas(bitmap).drawBitmap(gray,0f,0f,null) } finally { gray.recycle() }
                                    }
                                    val ticket=context.outputs.reserve(request.id,key,"page_${number.toString().padStart(4,'0')}.${BitmapTransforms.extension(settings.format)}",BitmapTransforms.mime(settings.format))
                                    context.checkCancellation()
                                    BitmapTransforms.encode(bitmap,ticket.stageFile,ImageConversionSettings(format=settings.format,quality=settings.quality),context.budget.maxBitmapBytes)
                                    val record=context.outputs.publish(ticket) { context.outputs.inspect(it,ticket.mimeType) }
                                    outcomes+=ItemOutcome(key,ItemStatus.CREATED,record.id)
                                } finally { bitmap.recycle() }
                            }
                        } catch(cancel:CancellationException) { throw cancel }
                        catch(e:Exception) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(e)) }
                        context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),pages.size.toLong()))
                    }
                    outcomes
                }
            }
        }
    }
}
