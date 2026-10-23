package com.jumlo.app.data.processing

import android.graphics.Bitmap
import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.PageSelection
import com.jumlo.app.core.SafeNames
import com.jumlo.app.core.office.OfficePackageValidator
import com.jumlo.app.core.office.OfficePackageWriter
import com.jumlo.app.core.office.OfficeSlide
import com.jumlo.app.core.office.OfficeTextPage
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.data.ocr.MlKitOcrEngine
import com.jumlo.app.data.pdf.PdfPageRasterizer
import com.jumlo.app.data.pdf.PdfPageText
import com.jumlo.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Android adapters feed the reusable streaming OOXML writer. Input PDFs are always read-only. */
@Singleton
class PdfOfficeOperationHandler @Inject constructor(private val ocr: MlKitOcrEngine) : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> = withContext(Dispatchers.IO) {
        // The writer is synchronous; its suspend adapters must remain children of this worker.
        val workerContext = currentCoroutineContext()
        require(request.sourceIds.size == 1) { "ONE_PDF_REQUIRED" }
        require(request.toolId in setOf("pdf_to_docx", "pdf_to_pptx")) { "CAPABILITY_UNAVAILABLE" }
        val key = "document"
        context.outputs.find(request.id, key)?.let { return@withContext listOf(ItemOutcome(key, ItemStatus.CREATED, it.id)) }
        val settings = JSONObject(request.settingsJson)
        val source = context.sources.get(request.sourceIds.single())
        val word = request.toolId == "pdf_to_docx"
        val extension = if (word) "docx" else "pptx"
        val mime = if (word) OfficePackageWriter.DOCX_MIME else OfficePackageWriter.PPTX_MIME
        val base = settings.optString("displayName").trim().takeUnless { it.isBlank() || it in setOf("Document.pdf", "Document.docx", "Document.pptx", "download.bin") }
            ?.substringBeforeLast('.') ?: AutoOutputName.cleanBaseName(source.displayName, "Document")
        val ticket = context.outputs.reserve(request.id, key, SafeNames.fromDisplayName("$base.$extension", "Document.$extension"), mime)
        var pageCount = 0
        var ocrPages = 0
        var emptyPages = 0
        var originalBytes = 0L
        try {
            context.sources.withSeekable(source.id, request.id) { input ->
                require(input.canonicalPath != ticket.stageFile.canonicalPath) { "OUTPUT_SOURCE_CONFLICT" }
                originalBytes = input.length()
                PdfPageText.open(input).use { doc ->
                    val spec = settings.optString("pages", "all")
                    val pages = if (spec.isBlank() || spec.equals("all", true)) (1..doc.numberOfPages).toList() else PageSelection.parse(spec, doc.numberOfPages)
                    pageCount = pages.size
                    require(pageCount in 1..OfficePackageWriter.MAX_PAGES) { "OFFICE_PAGE_LIMIT" }
                    var rasterizer: PdfPageRasterizer? = null
                    fun raster() = rasterizer ?: PdfPageRasterizer(input, context.checkCancellation).also { rasterizer = it }
                    fun check() {
                        context.checkCancellation()
                        require(requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace >= context.budget.minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
                    }
                    fun progress(index: Int) { runBlocking(workerContext) { context.report(JobProgress(JobStage.RUNNING, (index + 1).toLong(), pageCount.toLong())) } }
                    try {
                        if (word) OfficePackageWriter.writeStructuredDocx(ticket.stageFile, pageCount, { index ->
                            check()
                            val page = pages[index] - 1
                            var text = PdfPageText.extractStructured(doc, page, context.checkCancellation)
                            if (text.isBlank) {
                                ocrPages++
                                val bitmap = raster().render(page, 2400, minOf(context.budget.maxBitmapBytes / 2, 32L * 1024 * 1024))
                                try { text = OfficeTextPage.fromPlainText(runBlocking(workerContext) { ocr.recognize(bitmap) }) } finally { bitmap.recycle() }
                            }
                            if (text.isBlank) emptyPages++
                            progress(index)
                            text
                        }, ::check)
                        else OfficePackageWriter.writePptx(ticket.stageFile, pageCount, { index ->
                            check()
                            val page = pages[index] - 1
                            val text = PdfPageText.extract(doc, page, context.checkCancellation)
                            val size = raster().pageSize(page)
                            val bitmap = raster().render(page, 2000, minOf(context.budget.maxBitmapBytes / 2, 24L * 1024 * 1024))
                            val jpeg = try {
                                ByteArrayOutputStream().use { bytes ->
                                    require(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)) { "OFFICE_IMAGE_INVALID" }
                                    bytes.toByteArray()
                                }
                            } finally { bitmap.recycle() }
                            progress(index)
                            OfficeSlide(size.first.toDouble(), size.second.toDouble(), jpeg, text)
                        }, ::check)
                    } finally { rasterizer?.close() }
                }
            }
            context.checkCancellation()
            require(!word || emptyPages < pageCount) { "OCR_NO_TEXT" }
            val record = context.outputs.publish(ticket) { output ->
                if (word) OfficePackageValidator.validateDocx(output, pageCount, context.checkCancellation)
                else OfficePackageValidator.validatePptx(output, pageCount, context.checkCancellation)
                OutputMetadata(pageCount = pageCount, originalSizeBytes = originalBytes)
            }
            listOf(ItemOutcome(key, ItemStatus.CREATED, record.id, when {
                emptyPages > 0 -> "OFFICE_EMPTY_TEXT_PAGES"
                ocrPages > 0 -> "OFFICE_OCR_REVIEW"
                else -> null
            }))
        } catch (error: Throwable) {
            ticket.stageFile.delete()
            if (error is OutOfMemoryError) throw OperationFailure("LOW_MEMORY")
            throw error
        }
    }
}
