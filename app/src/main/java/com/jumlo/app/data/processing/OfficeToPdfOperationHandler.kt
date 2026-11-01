package com.jumlo.app.data.processing

import com.jumlo.app.core.SafeNames
import com.jumlo.app.core.office.OfficeBlock
import com.jumlo.app.core.office.OfficeDocumentReader
import com.jumlo.app.core.office.OfficeKind
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.ItemOutcome
import com.jumlo.app.data.jobs.ItemStatus
import com.jumlo.app.data.jobs.JobRequest
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.model.AutoOutputName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Office to PDF: Word, Excel and PowerPoint become one PDF. The conversion is
 * structural (text, headings, lists, tables, pictures), not a pixel-perfect
 * render of the original app: headers, footers, footnotes and charts are
 * dropped and reported, and non-Latin paragraphs are rasterized so their
 * shaping stays correct. Legacy .doc files are not Office Open XML and fail
 * closed with OFFICE_INVALID.
 */
@Singleton class OfficeToPdfOperationHandler @Inject constructor() : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> =
        withContext(Dispatchers.IO) {
            require(request.toolId == "office_to_pdf") { "CAPABILITY_UNAVAILABLE" }
            require(request.sourceIds.size == 1) { "ONE_SOURCE_REQUIRED" }
            val key = "document"
            context.outputs.find(request.id, key)?.let {
                return@withContext listOf(ItemOutcome(key, ItemStatus.CREATED, it.id))
            }
            val settings = Json.decodeFromString<OfficeToPdfSettings>(request.settingsJson)
            val source = context.sources.get(request.sourceIds.single())
            val sourceName = source.displayName
            val base = AutoOutputName.cleanBaseName(sourceName, "Document")
            val displayName = SafeNames.basename(
                settings.displayName.trim().takeUnless { it.isEmpty() || it == "download.bin" }
                    ?.let { if (it.endsWith(".pdf", true)) it else "$it.pdf" } ?: "$base.pdf")
            val ticket = context.outputs.reserve(request.id, key, displayName, "application/pdf")
            var originalBytes = 0L
            var pageCount = 0
            var rasterized = 0
            var dropped = 0
            var unsupported: List<String> = emptyList()
            context.sources.withSeekable(request.sourceIds.single(), request.id) { input ->
                originalBytes = input.length()
                val kind = OfficeDocumentReader.detect(input, sourceName)
                val document = OfficeDocumentReader.read(input, kind, context.budget.maxTemporaryBytes) {
                    context.checkCancellation()
                }
                val renderable = document.blocks.any { block ->
                    when (block) {
                        is OfficeBlock.ParagraphBlock -> !block.paragraph.isBlank
                        is OfficeBlock.TableBlock -> block.rows.isNotEmpty()
                        is OfficeBlock.ImageBlock -> true
                        is OfficeBlock.Section -> true
                        is OfficeBlock.PageBreak -> false
                    }
                }
                require(renderable) { "OFFICE_NO_CONTENT" }
                unsupported = document.unsupported
                OfficeToPdfRenderer.render(document, context.budget.maxBitmapBytes) {
                    context.checkCancellation()
                }.use { rendered ->
                    rasterized = rendered.stats.rasterizedBlocks
                    dropped = rendered.stats.droppedImages
                    pageCount = rendered.document.numberOfPages
                    require(pageCount >= 1) { "PDF_VERIFY_FAILED" }
                    rendered.document.save(ticket.stageFile)
                }
            }
            val record = context.outputs.publish(ticket) { file ->
                PdfBoxBackend.open(file, null).use {
                    check(it.numberOfPages == pageCount && pageCount >= 1) { "PDF_VERIFY_FAILED" }
                }
                context.outputs.inspect(file, "application/pdf", originalSizeBytes = originalBytes)
            }
            val warning = when {
                unsupported.isNotEmpty() || dropped > 0 -> "OFFICE_UNSUPPORTED_CONTENT"
                rasterized > 0 -> "OFFICE_RASTER_TEXT"
                else -> null
            }
            listOf(ItemOutcome(key, ItemStatus.CREATED, record.id, warning))
        }
}
