package com.jumlo.app.data.processing

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton class PdfOperationHandler @Inject constructor():OperationHandler {
    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = when(request.toolId) {
        "pdf_02" -> imagesToPdf(request,context)
        "pdf_07" -> pagesToImages(request,context)
        else -> throw OperationFailure("STRUCTURAL_PDF_BACKEND_UNQUALIFIED")
    }
    private suspend fun imagesToPdf(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        val settings=Json.decodeFromString<ImagesToPdfSettings>(request.settingsJson)
        require(request.sourceIds.isNotEmpty() && settings.paperWidthPt in 72..14400 && settings.paperHeightPt in 72..14400 && settings.marginMm>=0) { "INVALID_PDF_SETTINGS" }
        val margin=settings.marginMm*72f/25.4f
        require(margin*2<minOf(settings.paperWidthPt,settings.paperHeightPt) && settings.orientation in setOf("AUTO","PORTRAIT","LANDSCAPE")) { "INVALID_PDF_SETTINGS" }
        context.outputs.find(request.id,"document")?.let { return listOf(ItemOutcome("document",ItemStatus.CREATED,it.id)) }
        val ticket=context.outputs.reserve(request.id,"document",SafeNames.basename(settings.displayName.let { if(it.endsWith(".pdf",true)) it else "$it.pdf" }),"application/pdf")
        var retainedBytes=0L
        val document=PdfDocument()
        try {
        for ((index, id) in request.sourceIds.withIndex()) {
                context.checkCancellation()
                context.sources.withSeekable(id,request.id) { file ->
                    BitmapTransforms.decode(file,context.budget.maxBitmapBytes-retainedBytes).use { decoded ->
                        val bitmap=decoded.bitmap
                        retainedBytes+=bitmap.allocationByteCount.toLong()
                        require(retainedBytes<=context.budget.maxBitmapBytes) { "PDF_PAGE_MEMORY_BUDGET_EXCEEDED" }
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
        } finally {
            document.close()
        }
        val record=context.outputs.publish(ticket) { file ->
            context.outputs.inspect(file,"application/pdf").also { require(it.pageCount==request.sourceIds.size) { "PDF_VERIFY_FAILED" } }
        }
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
                                        // Rendering through a separate bitmap avoids overlapping self-draw.
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
