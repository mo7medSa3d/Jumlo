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
        "pdf_clean" -> structural(request,context,"clean")
        "pdf_protect" -> structural(request,context,"protect")
        "pdf_forms" -> structural(request,context,"forms")
        else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
    }

    /** A placed image must be upright: re-encode with the EXIF orientation applied. */
    private fun uprightImage(file: File, context: ExecutionContext): File {
        val info=BitmapTransforms.inspect(file)
        // PDFBox may keep decoded pixels, RGB and alpha buffers together while embedding PNGs.
        require(info.decodePeakBytes<=context.budget.maxBitmapBytes/2) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        val orientation = info.orientation
        if (orientation == 1) return file
        val temp = File.createTempFile("upright-", ".png", file.parentFile)
        try {
            BitmapTransforms.decode(file, context.budget.maxBitmapBytes).use { decoded ->
                // PNG preserves drawn-signature alpha while applying EXIF mirroring/rotation.
                BitmapTransforms.encode(decoded.bitmap, temp, ImageConversionSettings(format = "PNG"), context.budget.maxBitmapBytes)
            }
            return temp
        } catch (t: Throwable) { temp.delete(); throw t }
    }

    private fun pages(spec:String,count:Int):List<Int> = if(spec.trim().equals("all",ignoreCase=true)||spec.isBlank()) (1..count).toList() else PageSelection.parse(spec,count)

    private fun openUnencrypted(file: File): com.tom_roush.pdfbox.pdmodel.PDDocument {
        val doc = try {
            PdfBoxBackend.open(file, null)
        } catch (_: InvalidPasswordException) {
            throw OperationFailure("PDF_ENCRYPTED")
        } catch (failure: java.io.FileNotFoundException) {
            throw failure
        } catch (_: java.io.IOException) {
            throw OperationFailure("PDF_INVALID")
        }
        if (doc.isEncrypted) {
            doc.close()
            throw OperationFailure("PDF_ENCRYPTED")
        }
        return doc
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
        suspend fun publishOne(key:String,display:String,originalBytes:Long?=null,verify:((File)->Unit)?=null,stage:suspend (File)->Unit):List<ItemOutcome> {
            context.outputs.find(request.id,key)?.let { return listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) }
            val ticket=context.outputs.reserve(request.id,key,display,"application/pdf")
            stage(ticket.stageFile)
            // A password protected PDF cannot be opened by the renderer, so it is verified with the backend.
            val record=context.outputs.publish(ticket){ file ->
                if(verify!=null) { verify(file); OutputMetadata(originalSizeBytes=originalBytes) }
                else context.outputs.inspect(file,"application/pdf",originalSizeBytes=originalBytes)
            }
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
                            // A custom range stays one file: "1-3" is a single three-page part.
                            // "all" keeps the plain meaning: one file per page.
                            "RANGES" -> {
                                val spec=s.optString("pages","all")
                                if(spec.isBlank() || spec.trim().equals("all",true)) (1..count).map { (it-1) to 1 }
                                else PageSelection.groups(spec,count).map { group -> (group.first()-1) to group.size }
                            }
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
                                if(ticket.stageFile.exists()) require(ticket.stageFile.delete()) { "OUTPUT_STAGE_CLEANUP_FAILED" }
                                val entries=groups.mapIndexed { gi,group -> ArchiveInput(
                                    "Part ${(gi+1).toString().padStart(6,'0')}.pdf"
                                ) {
                                    context.checkCancellation()
                                    val temp=File.createTempFile("split-page-",".pdf",ticket.stageFile.parentFile)
                                    try {
                                        PdfBoxBackend.open(src,null).use { d -> PdfBoxBackend.split(d,listOf(PageGroup(group.first,group.second)),listOf(temp)) }
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
                                    if(requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace<context.budget.minFreeBytes)
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
                                    PdfBoxBackend.open(src,null).use { d -> PdfBoxBackend.split(d,listOf(PageGroup(g.first,g.second)),listOf(ticket.stageFile)) }
                                    // Validate the published part, not just that a file exists.
                                    PdfBoxBackend.open(ticket.stageFile,null).use { part ->
                                        require(part.numberOfPages == g.second) { "PDF_VERIFY_FAILED" }
                                    }
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
                                val settings=Json.decodeFromString<PdfOrganizeSettings>(request.settingsJson)
                                val ops=if(settings.pageEdits.isNotEmpty()) {
                                    require(settings.pageEdits.map { it.instanceId }.distinct().size==settings.pageEdits.size && settings.pageEdits.all { it.instanceId.isNotBlank() }) { "INVALID_PAGE_INSTANCE" }
                                    settings.pageEdits.map { edit ->
                                        require(edit.sourcePage in 1..count) { "INVALID_PAGE_INDEX" }
                                        require(edit.rotationDegrees in setOf(0,90,180,270)) { "INVALID_ROTATION" }
                                        OrganizeOp(edit.sourcePage-1,edit.rotationDegrees)
                                    }
                                } else {
                                    // Legacy serialized order/rotatePages retain their exact semantics.
                                    val order=PdfPageOrder.parse(s.optString("order",""),count)
                                    val rotateSpec=s.optString("rotatePages","")
                                    val rotateSet=if(rotateSpec.isBlank()) emptySet() else pages(rotateSpec,count).toSet()
                                    val degrees=s.optInt("rotateDegrees",90)
                                    // PDF rotations are quarter turns only; a free angle would write an invalid page.
                                    require(degrees % 90 == 0) { "INVALID_ROTATION" }
                                    order.map { OrganizeOp(it-1,if(it in rotateSet) degrees else 0) }
                                }
                                PdfBoxBackend.organize(doc,ops,out)
                            }
                            context.report(JobProgress(JobStage.RUNNING,2L,2L))
                        }
                    }
                }
            }
            "crop" -> singleDoc(request,context,s,name("Cropped.pdf")){ doc,out ->
                val selected=pages(s.optString("pages","all"),doc.numberOfPages)
                val settings=Json.decodeFromString<PdfCropSettings>(request.settingsJson)
                require(settings.overrides.size<=10000 && settings.overrides.map {it.page}.distinct().size==settings.overrides.size) { "INVALID_CROP" }
                settings.overrides.forEach {crop ->
                    require(crop.page in 1..doc.numberOfPages) { "INVALID_PAGE_INDEX" }
                    Box(crop.left,crop.top,crop.right,crop.bottom).normalized()
                }
                val overrides=settings.overrides.associateBy {it.page}
                selected.forEachIndexed{ idx,p ->
                    context.checkCancellation()
                    val crop=overrides[p]
                    PdfBoxBackend.setCrop(doc,p-1,crop?.left ?: settings.left,crop?.top ?: settings.top,crop?.right ?: settings.right,crop?.bottom ?: settings.bottom)
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
                var compressed=false
                var skippedImages=0
                context.sources.withSeekable(request.sourceIds.single(),request.id){ src ->
                    originalSize=src.length()
                    skippedImages=compressPdfGuaranteedSmaller(src,ticket.stageFile,s.optInt("maxDpi",150),s.optInt("jpegQuality",80),s.optLong("targetBytes",0L),context)
                    compressed=ticket.stageFile.length() < originalSize
                }
                val record=context.outputs.publish(ticket){ context.outputs.inspect(it,"application/pdf",originalSizeBytes=originalSize) }
                // A copy that could not be made smaller is reported as unchanged, not as a compression.
                // Pages that could not be re-encoded do not lose the rest of the document:
                // the file is published and the count is reported.
                val note = when {
                    skippedImages>0 -> "PDF_IMAGE_COMPRESSION_PARTIAL"
                    compressed -> null
                    else -> "PDF_NOT_COMPRESSIBLE"
                }
                listOf(ItemOutcome(key,if(compressed) ItemStatus.CREATED else ItemStatus.UNCHANGED,record.id,note))
            }
            "stamp" -> {
                val text=s.optString("text","")
                val useImage=s.optBoolean("useImage",false)
                val addNumbers = s.optBoolean("pageNumbers",false) || (!useImage && text.isBlank())
                val style = PdfWatermarkStyle(
                    centerX = s.optDouble("centerX",0.5),
                    centerY = s.optDouble("centerY",0.5),
                    fontSizePt = s.optInt("fontSizePt",42),
                    opacityPercent = s.optInt("opacityPercent",28),
                    rotationDegrees = s.optInt("rotationDegrees",-35),
                    tiled = s.optBoolean("tiled",false),
                    colorHex = s.optString("colorHex","#64748B"),
                    fontFamily = s.optString("fontFamily","sans"),
                    imageScale = s.optDouble("imageScale",1.0)
                )
                if(useImage) {
                    require(request.sourceIds.size==2) { "PDF_AND_IMAGE_REQUIRED" }
                    val ordered=PdfOverlaySourcePolicy.ordered(request.sourceIds.map {it to context.sources.get(it).mimeType})
                    publishOne("document",name("Stamped.pdf")) {out ->
                        seekAll(context,request,ordered) {files ->
                            openUnencrypted(files[0]).use {doc ->
                                val selected=pages(s.optString("pages","all"),doc.numberOfPages).map {it-1}
                                context.checkCancellation()
                                BitmapTransforms.decodeSampled(files[1],minOf(context.budget.maxBitmapBytes/4,16L*1024*1024),1600).use {decoded ->
                                    val logo=com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc,decoded.bitmap)
                                    PdfBoxBackend.stampWatermarkLogo(doc,selected,logo,style,context.checkCancellation)
                                }
                                if(addNumbers) PdfBoxBackend.stampWatermarkAndNumbers(doc,selected,"",true,style,context.checkCancellation)
                                context.checkCancellation();doc.save(out)
                            }
                        }
                    }
                } else singleDoc(request,context,s,name("Stamped.pdf")){ doc,out ->
                    val selected=pages(s.optString("pages","all"),doc.numberOfPages).map{it-1}
                    PdfBoxBackend.stampWatermarkAndNumbers(doc,selected,text,addNumbers,style,context.checkCancellation)
                    runBlocking {context.report(JobProgress(JobStage.RUNNING,selected.size.toLong(),selected.size.toLong()))}
                    doc.save(out)
                }
            }
            "annotate" -> singleDoc(request,context,s,name("Annotated.pdf")){ doc,out ->
                val page=s.optInt("page",1).coerceIn(1,doc.numberOfPages.coerceAtLeast(1))
                val kind=com.jumlo.app.core.PdfAnnotationKind.from(s.optString("kind","text"))
                val text=s.optString("text","").ifBlank { if(kind==com.jumlo.app.core.PdfAnnotationKind.NOTE) "Note" else "" }
                if(kind==com.jumlo.app.core.PdfAnnotationKind.INK) {
                    val multiple=s.optJSONArray("inkStrokes")
                    require(!s.has("inkStrokes") || multiple!=null) {"INVALID_ANNOTATION"}
                    val arrays=if(multiple!=null && multiple.length()>0) {
                        require(multiple.length()<=com.jumlo.app.core.PdfInkDrawing.MAX_STROKES) {"INVALID_ANNOTATION"}
                        (0 until multiple.length()).map {multiple.getJSONArray(it)}
                    } else {
                        val legacy=s.optJSONArray("inkPoints")
                        require(legacy!=null) {"INVALID_ANNOTATION"}
                        listOf(legacy)
                    }
                    var total=0L
                    val strokes=arrays.map {flat ->
                        require(flat.length() in 4..com.jumlo.app.core.PdfInkDrawing.MAX_POINTS_PER_STROKE*2 && flat.length()%2==0) {"INVALID_ANNOTATION"}
                        total+=flat.length()/2
                        require(total<=com.jumlo.app.core.PdfInkDrawing.MAX_TOTAL_POINTS) {"INVALID_ANNOTATION"}
                        (0 until flat.length() step 2).map {i ->
                            com.jumlo.app.core.PdfInkPoint(flat.getDouble(i),flat.getDouble(i+1))
                        }
                    }
                    PdfBoxBackend.addInkStrokes(doc,page-1,strokes,text,s.optString("colorHex",""),s.optDouble("thicknessPt",2.0).toFloat())
                    runBlocking { context.report(JobProgress(JobStage.RUNNING,1L,1L)) }
                    doc.save(out)
                    return@singleDoc
                }
                if(kind!=com.jumlo.app.core.PdfAnnotationKind.TEXT) {
                    // A real annotation, placed and sized by direct manipulation.
                    PdfBoxBackend.addAnnotation(doc,page-1,kind,
                        s.optDouble("left",0.1),s.optDouble("top",0.1),
                        s.optDouble("right",0.34),s.optDouble("bottom",0.24),text,s.optString("colorHex",""),s.optDouble("thicknessPt",2.0).toFloat())
                    runBlocking { context.report(JobProgress(JobStage.RUNNING,1L,1L)) }
                    doc.save(out)
                    return@singleDoc
                }
                val x=s.optDouble("left",s.optDouble("x",0.1))
                val y=s.optDouble("top",s.optDouble("y",0.1))
                // Standard-14 Helvetica cannot encode Arabic/non-ASCII glyphs in content streams;
                // store non-ASCII text in a Unicode PDF text annotation so user text is never lost.
                val requiresUnicodeAnnotation = text.any { it !in ' '..'~' }
                if(s.optBoolean("asAnnotation",false) || requiresUnicodeAnnotation) PdfBoxBackend.addTextAnnotation(doc,page-1,text,x,y,s.optString("colorHex",""))
                else PdfBoxBackend.overlayText(doc,listOf(page-1),text,x,y,12f,0.1f,s.optString("colorHex",""))
                runBlocking { context.report(JobProgress(JobStage.RUNNING,1L,1L)) }
                doc.save(out)
            }
            "overlayImage" -> {
                require(request.sourceIds.size==2) { "PDF_AND_IMAGE_REQUIRED" }
                val ordered = PdfOverlaySourcePolicy.ordered(
                    request.sourceIds.map { it to context.sources.get(it).mimeType }
                )
                // Allow images and PDFs to be selected in either order.
                publishOne("document",name("ImageOverlay.pdf")) { out ->
                    seekAll(context,request,ordered) { files ->
                        openUnencrypted(files[0]).use { doc ->
                            val target = (s.optInt("page",1)-1)
                                .coerceIn(0,(doc.numberOfPages-1).coerceAtLeast(0))
                            val upright=uprightImage(files[1],context)
                            try {
                                PdfBoxBackend.stampImage(doc,target,upright,
                                    s.optDouble("left",.15),s.optDouble("top",.2),
                                    s.optDouble("right",.75),s.optDouble("bottom",.7),s.optDouble("rotationDegrees",0.0).toFloat())
                            } finally { if(upright!==files[1]) upright.delete() }
                            doc.save(out)
                        }
                    }
                }
            }
            "signature" -> {
                require(request.sourceIds.size==2){"PDF_AND_SIGNATURE_IMAGE_REQUIRED"}
                val ordered = PdfOverlaySourcePolicy.ordered(
                    request.sourceIds.map { it to context.sources.get(it).mimeType }
                )
                publishOne("signed",name("Signed.pdf")){ out ->
                    seekAll(context,request,ordered){ files ->
                        openUnencrypted(files[0]).use{ doc ->
                            val settings=Json.decodeFromString<PdfSignatureSettings>(request.settingsJson)
                            val placements=settings.placements.ifEmpty {listOf(PdfSignaturePlacement(page=settings.page,left=settings.left,top=settings.top,right=settings.right,bottom=settings.bottom,rotationDegrees=settings.rotationDegrees))}
                                .map {PdfImageOverlay(it.page-1,it.left,it.top,it.right,it.bottom,it.rotationDegrees)}
                            val upright=uprightImage(files[1],context)
                            try {
                                PdfBoxBackend.stampImages(doc,upright,placements,context.checkCancellation)
                            } finally { if(upright!==files[1]) upright.delete() }
                            doc.save(out)
                        }
                    }
                }
            }
            "clean" -> {
                require(request.sourceIds.size==1){"ONE_PDF_REQUIRED"}
                val removed = mutableListOf<String>()
                val keepTitle = !s.optBoolean("removeTitle",false)
                val outcomes = singleDoc(request,context,s,name("Cleaned.pdf")){ doc,out ->
                    removed += PdfBoxBackend.scrubMetadata(doc,keepTitle)
                    doc.save(out)
                }
                // The sidecar report states exactly which keys were removed, so the
                // "clean" claim can be checked instead of trusted.
                val reportKey="report"
                if(context.outputs.find(request.id,reportKey)==null){
                    val reportName=SafeNames.fromDisplayName(name("Cleaned.txt"),"Cleaned.txt")
                    val ticket=context.outputs.reserve(request.id,reportKey,reportName,"text/plain")
                    ticket.stageFile.writeText(
                        "removed=" + removed.size + "\n" + removed.joinToString("\n"){ "key=" + it } + "\n"
                    )
                    val record=context.outputs.publish(ticket){ OutputMetadata() }
                    outcomes + ItemOutcome(reportKey,ItemStatus.CREATED,record.id)
                } else outcomes
            }
            "metadata" -> {
                require(request.sourceIds.size in 1..2){"ONE_OR_TWO_PDFS_REQUIRED"}
                // Only typed fields are written: a blank field must not erase existing metadata.
                val metaMap=mapOf("title" to s.optString("title",""),"author" to s.optString("author",""),"subject" to s.optString("subject",""),"keywords" to s.optString("keywords",""))
                    .filterValues { it.isNotBlank() }
                if(request.sourceIds.size==2){
                    publishOne("document",name("Document.pdf")){ out ->
                        seekAll(context,request,request.sourceIds){ files ->
                            val tempInserted=File(out.parentFile,"inserted-tmp.pdf")
                            try {
                                openUnencrypted(files[0]).use{ baseDoc ->
                                    openUnencrypted(files[1]).close()
                                    val insertPos=s.optInt("insertAtPage",1)-1
                                    require(insertPos in 0..baseDoc.numberOfPages) { "INVALID_INSERT_POSITION" }
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
                    publishOne("document",name(if(remove) "Unlocked.pdf" else "Protected.pdf"),verify={ file ->
                        val checkPassword=if(remove) null else (user.ifEmpty { owner }).toCharArray()
                        try { PdfBoxBackend.open(file,checkPassword).use { require(it.numberOfPages>0) { "EMPTY_PDF" } } }
                        finally { checkPassword?.fill('\u0000') }
                    }){ out ->
                        context.sources.withSeekable(request.sourceIds.single(),request.id){ src ->
                            val openPw=(user.ifEmpty { owner }).toCharArray()
                            try {
                                val opened=try { PdfBoxBackend.open(src,if(remove) openPw else null) }
                                catch(_: InvalidPasswordException) { throw OperationFailure("PDF_PASSWORD_INCORRECT") }
                                opened.use{ doc ->
                                    if(remove) PdfBoxBackend.removeProtection(doc,out)
                                    else {
                                        require(owner.isNotEmpty()){"OWNER_PASSWORD_REQUIRED"}
                                        val userArr=user.toCharArray(); val ownerArr=owner.toCharArray()
                                        try { PdfBoxBackend.protect(doc,userArr,ownerArr,out, PdfProtectionPermissions(
                                            allowPrinting = s.optBoolean("allowPrinting", true),
                                            allowCopy = s.optBoolean("allowCopy", true),
                                            allowModify = s.optBoolean("allowModify", false),
                                            allowAnnotations = s.optBoolean("allowAnnotations", true),
                                            allowForms = s.optBoolean("allowForms", true))) }
                                        finally { userArr.fill('\u0000'); ownerArr.fill('\u0000') }
                                    }
                                }
                            } finally { openPw.fill('\u0000') }
                        }
                    }
                } finally { rawChars.fill('\u0000') }
            }
            "forms" -> singleDoc(request,context,s,name("Form.pdf")){ doc,out ->
                val values = FormInputParser.parse(s.optString("fieldsJson","{}"))
                if (values.isNotEmpty()) {
                    PdfBoxBackend.fillForm(doc,values)
                }
                if(s.optBoolean("flatten",false)) PdfBoxBackend.flattenForm(doc,out) else doc.save(out)
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
    /**
     * Compress [source] into [out], never publishing a file that is larger or that
     * lost a page. Returns how many images could not be re-encoded.
     */
    private suspend fun compressPdfGuaranteedSmaller(
        source: File, out: File, requestedDpi: Int, requestedQuality: Int, targetBytes: Long,
        context: ExecutionContext
    ): Int = PdfCompressionRunner.compress(source, out, requestedDpi, requestedQuality,
        codec = AndroidImageCodec(context.budget.maxBitmapBytes),
        maxWorkingBytes = context.budget.maxBitmapBytes, targetBytes = targetBytes,
        minFreeBytes = context.budget.minFreeBytes,
        onProgress = { completed, total -> runBlocking { context.report(JobProgress(JobStage.RUNNING, completed, total)) } },
        checkCancel = context.checkCancellation).skippedImages

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
                        val placement=ImagePdfLayout.resolve(bitmap.width,bitmap.height,geometry,settings.marginMm,settings.pageMode=="AUTO",settings.fill)
                        val pageMargin=placement.marginPt
                        val pdPage=PDPage(PDRectangle(w,h))
                        outDoc.addPage(pdPage)
                        if (settings.fill) {
                            val renderScale=minOf(2048f/maxOf(w,h), 2.5f).coerceAtLeast(1f)
                            val canvasW=(w*renderScale).roundToInt().coerceAtLeast(64)
                            val canvasH=(h*renderScale).roundToInt().coerceAtLeast(64)
                            ImageWorkPolicy.requireFits(canvasW,canvasH,bitmap.allocationByteCount.toLong(),context.budget.maxBitmapBytes)
                            val pageBmp=Bitmap.createBitmap(canvasW,canvasH,Bitmap.Config.ARGB_8888)
                            try {
                                val canvas=Canvas(pageBmp)
                                canvas.drawColor(Color.WHITE)
                                val mPx=pageMargin*renderScale
                                val left=placement.leftPt*renderScale; val top=placement.topPt*renderScale
                                val dw=placement.widthPt*renderScale; val dh=placement.heightPt*renderScale
                                canvas.save()
                                canvas.clipRect(mPx,mPx,canvasW-mPx,canvasH-mPx)
                                canvas.drawBitmap(bitmap,null,RectF(left,top,left+dw,top+dh),Paint(Paint.FILTER_BITMAP_FLAG))
                                canvas.restore()
                                val pdImage=JPEGFactory.createFromImage(outDoc,pageBmp,0.85f)
                                PDPageContentStream(outDoc,pdPage).use { cs ->
                                    cs.drawImage(pdImage,0f,0f,w.toFloat(),h.toFloat())
                                }
                            } finally { pageBmp.recycle() }
                        } else {
                            val dw=placement.widthPt; val dh=placement.heightPt
                            val x=placement.leftPt; val y=h-placement.topPt-dh
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
                val renderer = PdfRendererFactory.open(fd, requireNotNull(source.parentFile), context.checkCancellation)
                renderer.use { renderer ->
                    val pages=PageSelection.parse(if(settings.pages.isBlank() || settings.pages.trim().equals("all",true)) "1-${renderer.pageCount}" else settings.pages,renderer.pageCount)
                    val outputMime=BitmapTransforms.mime(settings.format)

                    // One bitmap at a time: no full-document rasterization in resident memory.
                    fun renderPage(number:Int, target:File) {
                        context.checkCancellation()
                        renderer.openPage(number-1).use { page ->
                            val width=(page.width.toDouble()*settings.dpi/72.0).roundToInt().coerceAtLeast(1)
                            val height=(page.height.toDouble()*settings.dpi/72.0).roundToInt().coerceAtLeast(1)
                            val rowBytes=if(settings.grayscale) width.toLong()*4L else 0L
                            ImageWorkPolicy.requireFits(width,height,rowBytes,context.budget.maxBitmapBytes)
                            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                // The rendered page already has an opaque white
                                // background. JPEG encoding can use this bitmap
                                // directly instead of allocating a second matte.
                                bitmap.setHasAlpha(false)
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
                            if(ticket.stageFile.exists()) require(ticket.stageFile.delete()) { "OUTPUT_STAGE_CLEANUP_FAILED" }
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
                                if(requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace<context.budget.minFreeBytes)
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
