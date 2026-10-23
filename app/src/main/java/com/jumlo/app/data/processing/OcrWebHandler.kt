package com.jumlo.app.data.processing

import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.OcrTextPolicy
import com.jumlo.app.core.PageSelection
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.data.ocr.MlKitOcrEngine
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.data.pdf.PdfPageRasterizer
import com.jumlo.app.data.pdf.PdfPageText
import com.jumlo.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** OCR writes lossless UTF-8 text. A PDF text layer is added only when it can preserve every glyph. */
@Singleton
class OcrWebHandler @Inject constructor(private val ocr: MlKitOcrEngine) : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> = withContext(Dispatchers.IO) {
        require(request.toolId == "pdf_ocr") { "CAPABILITY_UNAVAILABLE" }
        require(request.sourceIds.size == 1) { "ONE_FILE_REQUIRED" }
        val source = context.sources.get(request.sourceIds.single())
        val settings = JSONObject(request.settingsJson)
        val base = AutoOutputName.cleanBaseName(source.displayName, "Document") + " - OCR"
        val textTicket = context.outputs.reserve(request.id, "text", SafeNames.basename("$base.txt"), "text/plain")
        var pdfTicket: OutputTicket? = null
        var count = 1
        var originalBytes = 0L
        var detected = false
        var textOnly = false
        try {
            context.sources.withSeekable(source.id, request.id) { input ->
                originalBytes = input.length()
                val isPdf = input.inputStream().use { stream ->
                    val signature = ByteArray(5)
                    stream.read(signature) == 5 && String(signature, Charsets.US_ASCII) == "%PDF-"
                }
                textTicket.stageFile.bufferedWriter(Charsets.UTF_8).use { textOutput ->
                    if (isPdf) PdfPageText.open(input).use { document ->
                        val selection = settings.optString("pages", "all")
                        val pages = if (selection.isBlank() || selection.equals("all", true)) (1..document.numberOfPages).toList()
                            else PageSelection.parse(selection, document.numberOfPages)
                        require(pages.isNotEmpty()) { "NO_PAGES" }
                        count = document.numberOfPages
                        var rasterizer: PdfPageRasterizer? = null
                        try {
                            pages.forEachIndexed { index, number ->
                                context.checkCancellation()
                                require(requireNotNull(input.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace >= context.budget.minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
                                var text = PdfPageText.extract(document, number - 1, context.checkCancellation)
                                if (text.isBlank()) {
                                    val renderer = rasterizer ?: PdfPageRasterizer(input, context.checkCancellation).also { rasterizer = it }
                                    val bitmap = renderer.render(number - 1, 2400, minOf(context.budget.maxBitmapBytes / 2, 32L * 1024 * 1024))
                                    try { text = ocr.recognize(bitmap) } finally { bitmap.recycle() }
                                    if (text.isNotBlank() && !textOnly) {
                                        if (runCatching { OcrTextPolicy.requireRepresentable(text) }.isSuccess) {
                                            PdfBoxBackend.stampInvisibleText(document, number - 1, text, .04, .04, 9f, strict = true)
                                        } else textOnly = true
                                    }
                                }
                                detected = detected || text.isNotBlank()
                                if (index > 0) textOutput.write("\n\n\u000c\n\n")
                                textOutput.write(text)
                                context.report(JobProgress(JobStage.RUNNING, (index + 1).toLong(), pages.size.toLong()))
                            }
                            if (!textOnly && detected && settings.optBoolean("searchablePdf", true)) {
                                val ticket = context.outputs.reserve(request.id, "document", SafeNames.basename("$base.pdf"), "application/pdf")
                                pdfTicket = ticket
                                document.save(ticket.stageFile)
                                PdfPageText.open(ticket.stageFile).use { check(it.numberOfPages == count) { "PDF_VERIFY_FAILED" } }
                            }
                        } finally { rasterizer?.close() }
                    } else {
                        BitmapTransforms.decode(input, context.budget.maxBitmapBytes).use { image ->
                            val text = ocr.recognize(image.bitmap)
                            detected = text.isNotBlank()
                            textOutput.write(text)
                        }
                        context.report(JobProgress(JobStage.RUNNING, 1, 1))
                    }
                }
            }
            context.checkCancellation()
            require(detected) { "OCR_NO_TEXT" }
            val outcomes = mutableListOf<ItemOutcome>()
            val textRecord = context.outputs.publish(textTicket) { file ->
                require(file.length() > 0) { "OCR_NO_TEXT" }
                OutputMetadata(originalSizeBytes = originalBytes)
            }
            outcomes += ItemOutcome("text", ItemStatus.CREATED, textRecord.id, if (textOnly) "OCR_TEXT_ONLY" else null)
            pdfTicket?.let { ticket ->
                val record = context.outputs.publish(ticket) { context.outputs.inspect(it, "application/pdf", originalSizeBytes = originalBytes) }
                outcomes += ItemOutcome("document", ItemStatus.CREATED, record.id)
            }
            outcomes
        } catch (error: Throwable) {
            textTicket.stageFile.delete()
            pdfTicket?.stageFile?.delete()
            if (error is OutOfMemoryError) throw OperationFailure("LOW_MEMORY")
            throw error
        }
    }
}
