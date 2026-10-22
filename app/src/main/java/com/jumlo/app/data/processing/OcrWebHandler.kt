package com.jumlo.app.data.processing

import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.OoxmlDocxBuilder
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.data.pdf.OcrEngine
import com.jumlo.app.model.ItemOutcome
import com.jumlo.app.model.ItemStatus
import com.jumlo.app.model.JobProgress
import com.jumlo.app.model.JobRequest
import com.jumlo.app.model.JobStage
import com.jumlo.app.model.OutputMetadata
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device OCR handler using unbundled Google Play Services ML Kit Text Recognition
 * (`com.google.android.gms:play-services-mlkit-text-recognition`).
 * Produces both a clean UTF-8 `.txt` transcript and an editable `.docx` document.
 */
@Singleton class OcrWebHandler @Inject constructor() : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        if (request.toolId != "pdf_ocr") throw OperationFailure("CAPABILITY_UNAVAILABLE")
        require(request.sourceIds.size == 1) { "ONE_PDF_REQUIRED" }
        val s = JSONObject(request.settingsJson)
        val firstSourceName = runCatching { context.sources.get(request.sourceIds.single()).displayName }.getOrNull()
        val baseName = AutoOutputName.cleanBaseName(firstSourceName ?: "Document", "Document")
        val txtKey = "ocr-txt"
        val docxKey = "ocr-docx"
        val existingTxt = context.outputs.find(request.id, txtKey)
        val existingDocx = context.outputs.find(request.id, docxKey)
        if (existingTxt != null && existingDocx != null) {
            return listOf(
                ItemOutcome(txtKey, ItemStatus.CREATED, existingTxt.id),
                ItemOutcome(docxKey, ItemStatus.CREATED, existingDocx.id)
            )
        }
        val txtTicket = context.outputs.reserve(request.id, txtKey, SafeNames.basename("$baseName - OCR.txt"), "text/plain")
        val docxTicket = context.outputs.reserve(
            request.id,
            docxKey,
            SafeNames.basename("$baseName - OCR.docx"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        var pageCount = 0
        var originalSize = 0L
        context.sources.withSeekable(request.sourceIds.single(), request.id) { src ->
            originalSize = src.length()
            val pages = OcrEngine.extractPagesWithOcrFallback(
                pdfFile = src,
                pagesSpec = s.optString("pages", "all"),
                dpi = s.optInt("dpi", 200).coerceIn(120, 300),
                forceOcr = true,
                maxBitmapBytes = context.budget.maxBitmapBytes,
                onPageProgress = { done, total ->
                    context.report(JobProgress(JobStage.RUNNING, done.toLong(), total.toLong()))
                },
                checkCancel = context.checkCancellation
            )
            pageCount = pages.size
            val transcript = pages.joinToString("\n\n") { page ->
                "=== Page ${page.pageNumber} ===\n" + page.paragraphs.joinToString("\n\n")
            }
            txtTicket.stageFile.writeText(transcript, Charsets.UTF_8)
            OoxmlDocxBuilder.build(docxTicket.stageFile, pages, "$baseName (OCR)", context.checkCancellation)
        }
        val txtRecord = context.outputs.publish(txtTicket) { OutputMetadata(pageCount = pageCount, originalSizeBytes = originalSize) }
        val docxRecord = context.outputs.publish(docxTicket) { file ->
            OoxmlDocxBuilder.verifyDocx(file)
            OutputMetadata(pageCount = pageCount, originalSizeBytes = originalSize)
        }
        return listOf(
            ItemOutcome(txtKey, ItemStatus.CREATED, txtRecord.id),
            ItemOutcome(docxKey, ItemStatus.CREATED, docxRecord.id)
        )
    }
}
