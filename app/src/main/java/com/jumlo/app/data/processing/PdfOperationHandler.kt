package com.jumlo.app.data.processing

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.pdf.*
import com.jumlo.app.model.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
        "pdf_image" -> structural(request,context,"overlayImage")
        "pdf_metadata" -> structural(request,context,"metadata")
        "pdf_protect" -> structural(request,context,"protect")
        "pdf_forms" -> structural(request,context,"forms")
        "pdf_compare" -> structural(request,context,"compare")
        "pdf_redact" -> structural(request,context,"redact")
        else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
    }

    private fun pages(spec:String,count:Int):List<Int> = if(spec.trim().equals("all",ignoreCase=true)||spec.isBlank()) (1..count).toList() else PageSelection.parse(spec,count)

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
        val firstSourceName = request.sourceIds.firstOrNull()?.let { id -> runCatching { context.sources.get(id).displayName }.getOrNull() }
        fun name(fallback:String):String {
            val raw = s.optString("displayName","").trim()
            val chosen = if(raw.isNotEmpty() && raw != "Document.pdf" && raw != "download.bin") raw
                else firstSourceName?.let { AutoOutputName.fromSource(request.toolId, it) } ?: fallback
            return SafeNames.basename(if(chosen.endsWith(".pdf",true)) chosen else "$chosen.pdf")
        }
        suspend fun publishOne(key:String,display:String,originalBytes:Long?=null,stage:suspend (File)->Unit):List<ItemOutcome> {
            context.outputs.find(request.id,key)?.let { return listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) }
            val ticket=context.outputs.reserve(request.id,key,display,"application/pdf")
            stage(ticket.stageFile)
            val record=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf",originalSizeBytes=originalBytes) }
            return listOf(ItemOutcome(key,ItemStatus.CREATED,record.id))
        }
        return when(kind){
            "merge" -> {
                require(request.sourceIds.isNotEmpty()){"ONE_PDF_REQUIRED"}
                publishOne("merged",name("Merged.pdf")){ out ->
                    seekAll(context,request,request.sourceIds){ files ->
                        files.forEachIndexed { idx, f ->
                            context.checkCancellation()
                            openUnencrypted(f).close()
                            context.report(JobProgress(JobStage.RUNNING,(idx+1).toLong(),files.size.toLong()))
                        }
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
                        val groups:List<Pair<Int,Int>> = when(s.optString("mode","RANGES")) {
                            "EVERY" -> {
                                val every=s.optInt("every",1).coerceAtLeast(1)
                                (0 until count step every).map { it to minOf(every,count-it) }
                            }
                            "BOUNDARIES" -> PdfSplitPlan.splitAfter(count,s.optString("pages",""))
                            "RANGES" -> pages(s.optString("pages","all"),count).map { (it-1) to 1 }
                            else -> throw OperationFailure("INVALID_SPLIT_MODE")
                        }
                        if (LargeResultStrategy.forPageOutputs(groups.size, context.budget.maxOutputFiles) == LargeResultStrategy.ARCHIVE) {
                            // Large fan-outs are streamed into ONE ZIP rather than writing
                            // thousands of separate Room/MediaStore outputs or failing at 512.
                            val key="split-archive"
                            val existing=context.outputs.find(request.id,key)
                            if (existing != null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
                            else {
                                val base=AutoOutputName.cleanBaseName(firstSourceName ?: "PDF", "PDF")
                                val display=SafeNames.fromDisplayName("$base - pages.zip", "Split pages.zip")
                                val ticket=context.outputs.reserve(request.id,key,display,"application/zip")
                                val entries=groups.mapIndexed { gi,group -> ArchiveInput(
                                    "Part ${(gi+1).toString().padStart(6,'0')}.pdf"
                                ) {
                                    context.checkCancellation()
                                    val temp=File.createTempFile("split-page-",".pdf",ticket.stageFile.parentFile)
                                    try {
                                        PdfBoxBackend.split(doc,listOf(PageGroup(group.first,group.second)),listOf(temp))
                                        // CRC alone only proves ZIP integrity: validate the PDF
                                        // structure and the expected page count BEFORE packing.
                                        PdfBoxBackend.open(temp,null).use { part ->
                                            require(part.numberOfPages == group.second) { "PDF_VERIFY_FAILED" }
                                        }
                                        object:FilterInputStream(FileInputStream(temp)) {
                                            override fun close() {
                                                try { super.close() } finally {
                                                    temp.delete()
                                                    runBlocking { context.report(JobProgress(JobStage.RUNNING,(gi+1).toLong(),groups.size.toLong())) }
                                                }
                                            }
                                        }
                                    } catch(t:Throwable) { temp.delete(); throw t }
                                } }
                                ArchiveCore.create(ticket.stageFile,entries,6,ArchiveLimits(Long.MAX_VALUE,groups.size,1)) {
                                    context.checkCancellation()
                                    if(ticket.stageFile.parentFile.usableSpace<context.budget.minFreeBytes)
                                        throw OperationFailure("DISK_BUDGET_EXCEEDED")
                                }
                                val rec=context.outputs.publish(ticket) { context.outputs.inspect(it,"application/zip") }
                                outcomes+=ItemOutcome(key,ItemStatus.CREATED,rec.id)
                            }
                        } else {
                            groups.forEachIndexed{ gi,g ->
                                context.checkCancellation()
                                val key="part:${gi+1}"
                                val existing=context.outputs.find(request.id,key)
                                if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
                                else {
                                    val partDisplayName = AutoOutputName.splitPartName(s.optString("displayName",""), firstSourceName, gi + 1)
                                    val ticket=context.outputs.reserve(request.id,key,partDisplayName,"application/pdf")
                                    PdfBoxBackend.split(doc,listOf(PageGroup(g.first,g.second)),listOf(ticket.stageFile))
                                    val rec=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf") }
                                    outcomes+=ItemOutcome(key,ItemStatus.CREATED,rec.id)
                                }
                                context.report(JobProgress(JobStage.RUNNING,(gi+1).toLong(),groups.size.toLong()))
                            }
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
                            context.report(JobProgress(JobStage.RUNNING,1L,2L))
                            if(kind=="extract"){
                                val selected=pages(s.optString("pages","all"),count)
                                PdfBoxBackend.extract(doc,selected.map{ it-1 },out)
                            } else {
                                val orderSpec=s.optString("order","")
                                // Parse the exact intended order. mapNotNull() previously dropped malformed
                                // tokens and could publish a PDF with missing pages as a successful edit.
                                val order=PdfPageOrder.parse(orderSpec,count)
                                val rotateSpec=s.optString("rotatePages","")
                                val rotateSet=if(rotateSpec.isBlank()) emptySet() else pages(rotateSpec,count).toSet()
                                val degrees=s.optInt("rotateDegrees",90)
                                PdfBoxBackend.organize(doc,order.map{ OrganizeOp(it-1, if(it in rotateSet) degrees else 0) },out)
                            }
                            context.report(JobProgress(JobStage.RUNNING,2L,2L))
                        }
                    }
                }
            }
            "crop" -> singleDoc(request,context,s,name("Cropped.pdf")){ doc,out ->
                val selected=pages(s.optString("pages","all"),doc.numberOfPages)
                selected.forEachIndexed{ idx,p ->
                    context.checkCancellation()
                    PdfBoxBackend.setCrop(doc,p-1,s.optDouble("left",0.0),s.optDouble("top",0.0),s.optDouble("right",1.0),s.optDouble("bottom",1.0))
                    runBlocking { context.report(JobProgress(JobStage.RUNNING,(idx+1).toLong(),selected.size.toLong())) }
                }
                doc.save(out)
            }
            "compress" -> {
                require(request.sourceIds.size==1){"ONE_PDF_REQUIRED"}
                val key="document"
                context.outputs.find(request.id,key)?.let{ return listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) }
                val ticket=context.outputs.reserve(request.id,key,name("Compressed.pdf"),"application/pdf")
                var originalSize=0L
                context.sources.withSeekable(request.sourceIds.single(),request.id){ src ->
                    originalSize=src.length()
                    compressPdfGuaranteedSmaller(src,ticket.stageFile,s.optInt("maxDpi",150),s.optInt("jpegQuality",80),context)
                }
                val record=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf",originalSizeBytes=originalSize) }
                listOf(ItemOutcome(key,ItemStatus.CREATED,record.id))
            }
            "stamp" -> singleDoc(request,context,s,name("Stamped.pdf")){ doc,out ->
                val selected=pages(s.optString("pages","all"),doc.numberOfPages).map{ it-1 }
                val text=s.optString("text","")
                val addNumbers = s.optBoolean("pageNumbers",false) || text.isBlank()
                PdfBoxBackend.stampWatermarkAndNumbers(doc,selected,text,addNumbers)
                runBlocking { context.report(JobProgress(JobStage.RUNNING,selected.size.toLong(),selected.size.toLong())) }
                doc.save(out)
            }
            "annotate" -> singleDoc(request,context,s,name("Annotated.pdf")){ doc,out ->
                val page=s.optInt("page",1).coerceIn(1,doc.numberOfPages.coerceAtLeast(1))
                val text=s.optString("text","").ifBlank { "Note" }
                val x=s.optDouble("x",0.1); val y=s.optDouble("y",0.1)
                // Standard-14 Helvetica cannot encode Arabic/non-ASCII glyphs in content streams;
                // store non-ASCII text in a Unicode PDF text annotation so user text is never lost.
                val requiresUnicodeAnnotation = text.any { it !in ' '..'~' }
                if(s.optBoolean("asAnnotation",false) || requiresUnicodeAnnotation) PdfBoxBackend.addTextAnnotation(doc,page-1,text,x,y)
                else PdfBoxBackend.overlayText(doc,listOf(page-1),text,x,y,12f,0.1f)
                runBlocking { context.report(JobProgress(JobStage.RUNNING,1L,1L)) }
                doc.save(out)
            }
            "overlayImage" -> {
                require(request.sourceIds.size==2) { "PDF_AND_IMAGE_REQUIRED" }
                val pdfId = request.sourceIds.firstOrNull {
                    context.sources.get(it).mimeType=="application/pdf"
                } ?: throw OperationFailure("PDF_AND_IMAGE_REQUIRED")
                val imageId = request.sourceIds.firstOrNull {
                    context.sources.get(it).mimeType?.startsWith("image/")==true
                } ?: throw OperationFailure("PDF_AND_IMAGE_REQUIRED")
                // Allow images and PDFs to be selected in either order.
                publishOne("document",name("ImageOverlay.pdf")) { out ->
                    seekAll(context,request,listOf(pdfId,imageId)) { files ->
                        openUnencrypted(files[0]).use { doc ->
                            val target = (s.optInt("page",1)-1)
                                .coerceIn(0,(doc.numberOfPages-1).coerceAtLeast(0))
                            PdfBoxBackend.stampImage(doc,target,files[1],
                                s.optDouble("left",.15),s.optDouble("top",.2),
                                s.optDouble("right",.75),s.optDouble("bottom",.7))
                            doc.save(out)
                        }
                    }
                }
            }
            "signature" -> {
                require(request.sourceIds.size==2){"PDF_AND_SIGNATURE_IMAGE_REQUIRED"}
                val pdfId = request.sourceIds.firstOrNull {
                    context.sources.get(it).mimeType=="application/pdf"
                } ?: throw OperationFailure("PDF_AND_SIGNATURE_IMAGE_REQUIRED")
                val imageId = request.sourceIds.firstOrNull {
                    context.sources.get(it).mimeType?.startsWith("image/")==true
                } ?: throw OperationFailure("PDF_AND_SIGNATURE_IMAGE_REQUIRED")
                publishOne("signed",name("Signed.pdf")){ out ->
                    seekAll(context,request,listOf(pdfId,imageId)){ files ->
                        openUnencrypted(files[0]).use{ doc ->
                            val targetPage = (s.optInt("page",1)-1).coerceIn(0, (doc.numberOfPages-1).coerceAtLeast(0))
                            PdfBoxBackend.stampImage(doc,targetPage,files[1],s.optDouble("left",0.6),s.optDouble("top",0.75),s.optDouble("right",0.95),s.optDouble("bottom",0.95))
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
                val reportName = SafeNames.fromDisplayName(s.optString("displayName","compare-report.txt").let { if(it.endsWith(".txt",true)) it else "$it.txt" }, "compare-report.txt")
                context.outputs.find(request.id,key)?.let{ listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) } ?: run {
                    val ticket=context.outputs.reserve(request.id,key,reportName,"text/plain")
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
                    openUnencrypted(out).use{ check -> require(!PdfBoxBackend.containsText(check,verify)){"REDACTION_VERIFICATION_FAILED"} }
                }
            }
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }

    /**
     * Never turn text/vector pages into lossy screenshots just to claim a size reduction.
     * A candidate is eligible only when the PDF parser reopens it, page count is preserved,
     * and its measured size is strictly below the original. Page image recompression is lossy;
     * original input is never modified. The more aggressive pass is reserved for smaller-file presets.
     */
    private suspend fun compressPdfGuaranteedSmaller(
        source: File, out: File, requestedDpi: Int, requestedQuality: Int,
        context: ExecutionContext
    ) {
        val originalSize = source.length()
        val originalPages = openUnencrypted(source).use { it.numberOfPages }
        require(originalPages > 0) { "PDF_VERIFY_FAILED" }
        val candidate = File(out.parentFile, "compress-${context.hashCode()}-candidate.pdf")
        val best = File(out.parentFile, "compress-${context.hashCode()}-best.pdf")
        try {
            var bestBytes = originalSize
            val profiles = PdfCompressionPolicy.profiles(requestedDpi, requestedQuality)
            for ((passIndex, profile) in profiles.withIndex()) {
                val (dpi, quality) = profile
                context.checkCancellation()
                candidate.delete()
                openUnencrypted(source).use { doc ->
                    PdfBoxBackend.compressImages(doc, dpi, quality) { done, total ->
                        context.checkCancellation()
                        val completed = passIndex.toLong()*total.toLong()+done.toLong()
                        val allWork = profiles.size.toLong()*total.toLong()
                        runBlocking { context.report(JobProgress(JobStage.RUNNING, completed, allWork)) }
                    }
                    doc.save(candidate)
                }
                context.checkCancellation()
                val validPages = runCatching {
                    openUnencrypted(candidate).use { it.numberOfPages }
                }.getOrNull()
                if (validPages != null && PdfCompressionPolicy.betterCandidate(
                        originalSize, bestBytes, candidate.length(), originalPages, validPages)) {
                    candidate.copyTo(best, overwrite = true)
                    bestBytes = candidate.length()
                }
            }
            // Text/vector PDFs may already be near optimal. Never replace them with
            // screenshots, and never publish an unreadable or larger candidate.
            if (best.exists() && bestBytes < originalSize) best.copyTo(out, overwrite = true)
            else source.copyTo(out, overwrite = true)
        } finally {
            candidate.delete()
            best.delete()
        }
    }

    private suspend fun seekAll(context:ExecutionContext,request:JobRequest,ids:List<String>,block:suspend (List<File>)->Unit){ suspend fun rec(i:Int,acc:MutableList<File>){ if(i>=ids.size){ block(acc.toList()); return }; context.sources.withSeekable(ids[i],request.id){ f -> acc.add(f); rec(i+1,acc); acc.removeAt(acc.lastIndex) } }; rec(0,mutableListOf()) }
    private suspend fun singleDoc(request:JobRequest,context:ExecutionContext,s:JSONObject,display:String,block:(com.tom_roush.pdfbox.pdmodel.PDDocument,File)->Unit):List<ItemOutcome>{
        require(request.sourceIds.size==1){"ONE_PDF_REQUIRED"}
        val key="document"
        context.outputs.find(request.id,key)?.let{ return listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) }
        val ticket=context.outputs.reserve(request.id,key,display,"application/pdf")
        var originalSize:Long?=null
        context.sources.withSeekable(request.sourceIds.single(),request.id){ src ->
            originalSize=src.length()
            openUnencrypted(src).use{ block(it,ticket.stageFile) }
        }
        val record=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf",originalSizeBytes=originalSize) }
        return listOf(ItemOutcome(key,ItemStatus.CREATED,record.id))
    }

    private suspend fun imagesToPdf(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        val settings=Json.decodeFromString<ImagesToPdfSettings>(request.settingsJson)
        require(request.sourceIds.isNotEmpty() && settings.marginMm in 0..200 &&
            settings.pageMode in setOf("AUTO", "A4", "LETTER", "CUSTOM")) { "INVALID_PDF_SETTINGS" }
        val margin=settings.marginMm*72f/25.4f
        context.outputs.find(request.id,"document")?.let { return listOf(ItemOutcome("document",ItemStatus.CREATED,it.id)) }
        val firstSourceName = runCatching { context.sources.get(request.sourceIds.first()).displayName }.getOrNull()
        val chosenName = settings.displayName.trim().takeUnless { it.isEmpty() || it == "Images.pdf" || it == "download.bin" }
            ?: firstSourceName?.let { AutoOutputName.fromSource("pdf_02", it) } ?: "Images.pdf"
        val ticket=context.outputs.reserve(request.id,"document",SafeNames.basename(chosenName.let { if(it.endsWith(".pdf",true)) it else "$it.pdf" }),"application/pdf")
        var totalInputBytes=0L
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
            for ((index, id) in request.sourceIds.withIndex()) {
                context.checkCancellation()
                context.sources.withSeekable(id,request.id) { file ->
                    totalInputBytes+=file.length()
                    BitmapTransforms.decode(file,context.budget.maxBitmapBytes).use { decoded ->
                        val bitmap=decoded.bitmap
                        val geometry=PdfPageGeometry.resolve(bitmap.width, bitmap.height,
                            settings.pageMode, settings.paperWidthPt, settings.paperHeightPt, settings.orientation)
                        val w=geometry.widthPt
                        val h=geometry.heightPt
                        // Automatic geometry also scales the margin for unusually narrow images.
                        val pageMargin = if (settings.pageMode == "AUTO") minOf(margin, minOf(w,h)*0.08f)
                            else margin
                        require(pageMargin*2<minOf(w,h)) { "INVALID_PDF_SETTINGS" }
                        val pdPage=PDPage(PDRectangle(w,h))
                        outDoc.addPage(pdPage)
                        val aw=w-2*pageMargin
                        val ah=h-2*pageMargin
                        if (settings.fill) {
                            val renderScale=minOf(2048f/maxOf(w,h), 2.5f).coerceAtLeast(1f)
                            val canvasW=(w*renderScale).roundToInt().coerceAtLeast(64)
                            val canvasH=(h*renderScale).roundToInt().coerceAtLeast(64)
                            val pageBmp=Bitmap.createBitmap(canvasW,canvasH,Bitmap.Config.ARGB_8888)
                            try {
                                val canvas=Canvas(pageBmp)
                                canvas.drawColor(Color.WHITE)
                                val mPx=pageMargin*renderScale
                                val awPx=canvasW-2*mPx; val ahPx=canvasH-2*mPx
                                val scale=maxOf(awPx/bitmap.width,ahPx/bitmap.height)
                                val dw=bitmap.width*scale; val dh=bitmap.height*scale
                                canvas.save()
                                canvas.clipRect(mPx,mPx,canvasW-mPx,canvasH-mPx)
                                canvas.drawBitmap(bitmap,null,RectF((canvasW-dw)/2f,(canvasH-dh)/2f,(canvasW+dw)/2f,(canvasH+dh)/2f),Paint(Paint.FILTER_BITMAP_FLAG))
                                canvas.restore()
                                val pdImage=JPEGFactory.createFromImage(outDoc,pageBmp,0.85f)
                                PDPageContentStream(outDoc,pdPage).use { cs ->
                                    cs.drawImage(pdImage,0f,0f,w.toFloat(),h.toFloat())
                                }
                            } finally { pageBmp.recycle() }
                        } else {
                            val scale=minOf(aw/bitmap.width,ah/bitmap.height)
                            val dw=bitmap.width*scale; val dh=bitmap.height*scale
                            val x=(w-dw)/2f; val y=(h-dh)/2f
                            val pdImage=JPEGFactory.createFromImage(outDoc,bitmap,0.85f)
                            PDPageContentStream(outDoc,pdPage).use { cs ->
                                cs.drawImage(pdImage,x,y,dw,dh)
                            }
                        }
                    }
                }
                context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),request.sourceIds.size.toLong()))
            }
            context.checkCancellation()
            context.report(JobProgress(JobStage.FINALIZING,request.sourceIds.size.toLong(),request.sourceIds.size.toLong()))
            outDoc.save(ticket.stageFile)
        }
        val record=context.outputs.publish(ticket) { file -> context.outputs.inspect(file,"application/pdf",originalSizeBytes=totalInputBytes).also { require(it.pageCount==request.sourceIds.size) { "PDF_VERIFY_FAILED" } } }
        return listOf(ItemOutcome("document",ItemStatus.CREATED,record.id))
    }

    private suspend fun pagesToImages(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        require(request.sourceIds.size==1) { "ONE_PDF_REQUIRED" }
        val settings=Json.decodeFromString<PdfImagesSettings>(request.settingsJson)
        require(settings.dpi in 36..600 && settings.format in setOf("JPEG","PNG") && settings.quality in 1..100) { "INVALID_RASTER_SETTINGS" }
        val sourceRef = runCatching { context.sources.get(request.sourceIds.single()) }.getOrNull()
        val originalPdfFolder = AutoOutputName.cleanBaseName(sourceRef?.displayName ?: settings.displayName.ifBlank { "PDF" }, "PDF")
        val customBase = settings.displayName.substringBeforeLast('.', settings.displayName).trim()
            .takeUnless { it.isEmpty() || it.equals("download", ignoreCase = true) || it.equals("Images", ignoreCase = true) || it.equals("Document", ignoreCase = true) }
        val effectiveBaseName = customBase?.let { AutoOutputName.cleanBaseName(it, originalPdfFolder) } ?: originalPdfFolder
        val ext = BitmapTransforms.extension(settings.format)
        return context.sources.withSeekable(request.sourceIds.single(),request.id) { source ->
            openUnencrypted(source).close()
            ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = try { PdfRenderer(fd) } catch (_: SecurityException) { throw OperationFailure("PDF_ENCRYPTED") }
                renderer.use { renderer ->
                    val pages=PageSelection.parse(if(settings.pages.isBlank() || settings.pages.trim().equals("all",true)) "1-${renderer.pageCount}" else settings.pages,renderer.pageCount)
                    val outputMime=BitmapTransforms.mime(settings.format)

                    // One bitmap at a time: no full-document rasterization in resident memory.
                    fun renderPage(number:Int, target:File) {
                        context.checkCancellation()
                        renderer.openPage(number-1).use { page ->
                            var width=(page.width*settings.dpi/72.0).roundToInt().coerceAtLeast(1)
                            var height=(page.height*settings.dpi/72.0).roundToInt().coerceAtLeast(1)
                            val maxPixels=(context.budget.maxBitmapBytes / 4L).coerceAtLeast(512L * 512L)
                            if(width.toLong()*height > maxPixels) {
                                val scale=sqrt(maxPixels.toDouble()/(width.toDouble()*height))
                                width=(width*scale).roundToInt().coerceAtLeast(1)
                                height=(height*scale).roundToInt().coerceAtLeast(1)
                            }
                            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                if(settings.grayscale) {
                                    val rowPixels=IntArray(width)
                                    for(y in 0 until height) {
                                        bitmap.getPixels(rowPixels,0,width,0,y,width,1)
                                        for(x in 0 until width) {
                                            val c=rowPixels[x]
                                            val luma=(((c shr 16 and 0xff)*77)+((c shr 8 and 0xff)*150)+((c and 0xff)*29)) shr 8
                                            rowPixels[x]=(c and -0x1000000) or (luma shl 16) or (luma shl 8) or luma
                                        }
                                        bitmap.setPixels(rowPixels,0,width,0,y,width,1)
                                    }
                                }
                                context.checkCancellation()
                                BitmapTransforms.encode(bitmap,target,ImageConversionSettings(format=settings.format,quality=settings.quality),context.budget.maxBitmapBytes)
                            } finally { bitmap.recycle() }
                        }
                    }

                    if (LargeResultStrategy.forPageOutputs(pages.size, context.budget.maxOutputFiles) == LargeResultStrategy.ARCHIVE) {
                        val key="images-archive"
                        val existing=context.outputs.find(request.id,key)
                        if(existing!=null) listOf(ItemOutcome(key,ItemStatus.CREATED,existing.id))
                        else {
                            val display=SafeNames.fromDisplayName("$effectiveBaseName - images.zip", "PDF images.zip")
                            val ticket=context.outputs.reserve(request.id,key,display,"application/zip")
                            val inputs=pages.mapIndexed { index,number ->
                                ArchiveInput(AutoOutputName.pageImageName(effectiveBaseName,number,ext)) {
                                    context.checkCancellation()
                                    val temp=File.createTempFile("render-page-",".$ext",ticket.stageFile.parentFile)
                                    try {
                                        renderPage(number,temp)
                                        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                                        BitmapFactory.decodeFile(temp.path,options)
                                        require(options.outWidth>0 && options.outHeight>0) { "IMAGE_VERIFY_FAILED" }
                                        object:FilterInputStream(FileInputStream(temp)) {
                                            override fun close() {
                                                try { super.close() } finally {
                                                    temp.delete()
                                                    runBlocking { context.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),pages.size.toLong())) }
                                                }
                                            }
                                        }
                                    } catch(t:Throwable) { temp.delete(); throw t }
                                }
                            }
                            ArchiveCore.create(ticket.stageFile,inputs,6,ArchiveLimits(Long.MAX_VALUE,inputs.size,1)) {
                                context.checkCancellation()
                                if(ticket.stageFile.parentFile.usableSpace<context.budget.minFreeBytes)
                                    throw OperationFailure("DISK_BUDGET_EXCEEDED")
                            }
                            val rec=context.outputs.publish(ticket) { context.outputs.inspect(it,"application/zip",subfolder=originalPdfFolder) }
                            listOf(ItemOutcome(key,ItemStatus.CREATED,rec.id))
                        }
                    } else {
                        val outcomes=mutableListOf<ItemOutcome>()
                        pages.forEachIndexed { index,number ->
                            context.checkCancellation()
                            val key="page:$number"
                            val existing=context.outputs.find(request.id,key)
                            if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
                            else try {
                                val pageFileName=AutoOutputName.pageImageName(effectiveBaseName,number,ext)
                                val ticket=context.outputs.reserve(request.id,key,pageFileName,outputMime)
                                renderPage(number,ticket.stageFile)
                                val record=context.outputs.publish(ticket) { context.outputs.inspect(it,ticket.mimeType,subfolder=originalPdfFolder) }
                                outcomes+=ItemOutcome(key,ItemStatus.CREATED,record.id)
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
}
