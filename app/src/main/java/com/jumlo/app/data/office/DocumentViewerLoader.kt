package com.jumlo.app.data.office

import android.content.Context
import android.net.Uri
import com.jumlo.app.core.office.OfficeBlock
import com.jumlo.app.core.office.OfficeDocument
import com.jumlo.app.core.office.OfficeDocumentReader
import com.jumlo.app.core.office.OfficeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlin.coroutines.CoroutineContext

enum class DocumentViewerKind { PDF, WORD, PRESENTATION, SPREADSHEET, TEXT, IMAGE, AUDIO, VIDEO, ARCHIVE }

object DocumentViewerFormats {
    private val TEXT_MIME_TYPES = setOf(
        "application/json",
        "application/xml",
        "application/xhtml+xml",
        "application/javascript",
        "application/x-yaml"
    )

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "csv", "tsv", "json", "log",
        "xml", "html", "htm", "yaml", "yml", "ini", "conf",
        "sql", "kt", "java", "py", "js", "ts"
    )

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "3gp", "m4v")

    fun detect(mimeType: String?, fileName: String?): DocumentViewerKind? {
        val normalizedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }

        when {
            normalizedMime == "application/pdf" -> return DocumentViewerKind.PDF
            normalizedMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> return DocumentViewerKind.WORD
            normalizedMime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> return DocumentViewerKind.PRESENTATION
            normalizedMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> return DocumentViewerKind.SPREADSHEET
            normalizedMime == "application/zip" || normalizedMime == "application/x-zip-compressed" -> return DocumentViewerKind.ARCHIVE
            normalizedMime?.startsWith("image/") == true -> return DocumentViewerKind.IMAGE
            normalizedMime?.startsWith("audio/") == true -> return DocumentViewerKind.AUDIO
            normalizedMime?.startsWith("video/") == true -> return DocumentViewerKind.VIDEO
            normalizedMime?.startsWith("text/") == true || normalizedMime in TEXT_MIME_TYPES -> return DocumentViewerKind.TEXT
        }

        val ext = fileName
            ?.trim()
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }

        return when (ext) {
            "pdf" -> DocumentViewerKind.PDF
            "docx" -> DocumentViewerKind.WORD
            "pptx" -> DocumentViewerKind.PRESENTATION
            "xlsx" -> DocumentViewerKind.SPREADSHEET
            "zip" -> DocumentViewerKind.ARCHIVE
            in IMAGE_EXTENSIONS -> DocumentViewerKind.IMAGE
            in AUDIO_EXTENSIONS -> DocumentViewerKind.AUDIO
            in VIDEO_EXTENSIONS -> DocumentViewerKind.VIDEO
            in TEXT_EXTENSIONS -> DocumentViewerKind.TEXT
            else -> null
        }
    }

    fun supportsInAppViewer(mimeType: String?, fileName: String?): Boolean =
        detect(mimeType, fileName) != null
}

data class LoadedSlide(
    val index: Int,
    val title: String?,
    val blocks: List<OfficeBlock>
)

data class LoadedSheet(
    val title: String,
    val blocks: List<OfficeBlock>
)

data class OutlineHeading(
    val blockIndex: Int,
    val level: Int,
    val text: String
)

sealed interface LoadedDocument {
    data class Presentation(
        val displayName: String,
        val sizeBytes: Long,
        val aspectRatio: Float,
        val slides: List<LoadedSlide>,
        val unsupported: List<String>
    ) : LoadedDocument

    data class Word(
        val displayName: String,
        val sizeBytes: Long,
        val blocks: List<OfficeBlock>,
        val headings: List<OutlineHeading>,
        val wordCount: Int,
        val pageBreakCount: Int,
        val unsupported: List<String>
    ) : LoadedDocument

    data class Spreadsheet(
        val displayName: String,
        val sizeBytes: Long,
        val sheets: List<LoadedSheet>,
        val unsupported: List<String>
    ) : LoadedDocument

    data class PlainText(
        val displayName: String,
        val sizeBytes: Long,
        val extension: String,
        val lines: List<String>,
        val csvRows: List<List<String>>?
    ) : LoadedDocument
}

object DocumentViewerLoader {
    const val MAX_VIEWER_FILE_BYTES = 48L * 1024 * 1024
    const val MAX_VIEWER_IMAGE_BYTES = 32L * 1024 * 1024
    const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    const val MAX_TEXT_LINES = 20_000
    const val MAX_CSV_COLUMNS = 256
    const val MAX_CSV_CELLS = 50_000

    private const val COPY_BUFFER_SIZE = 16 * 1024

    suspend fun load(
        context: Context,
        uriString: String,
        displayName: String,
        mimeType: String?
    ): LoadedDocument = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uriString)
        require(parsed.scheme.equals("content", ignoreCase = true)) { "CONTENT_URI_REQUIRED" }

        val kind = DocumentViewerFormats.detect(mimeType, displayName)
        require(kind != null && kind != DocumentViewerKind.PDF) { "UNSUPPORTED_VIEWER_FORMAT" }

        val coroutine = currentCoroutineContext()
        if (kind == DocumentViewerKind.TEXT) {
            val input = context.contentResolver.openInputStream(parsed)
                ?: throw IllegalArgumentException("DOCUMENT_UNREADABLE")
            val bytes = input.use { readUpTo(it, MAX_TEXT_BYTES.toInt(), coroutine) }
            val text = decodeUtf8Lenient(bytes).removePrefix("\uFEFF")
            val lines = text.lineSequence().take(MAX_TEXT_LINES).toList()
            val ext = resolveTextExtension(displayName, mimeType)
            val isCsv = ext == "csv" ||
                mimeType?.substringBefore(';')?.trim()?.equals("text/csv", ignoreCase = true) == true
            return@withContext LoadedDocument.PlainText(
                displayName = displayName,
                sizeBytes = bytes.size.toLong(),
                extension = ext,
                lines = lines,
                csvRows = if (isCsv) parseCsvLines(lines) else null
            )
        }

        val temp = File.createTempFile("viewer_", ".tmp", context.cacheDir)
        try {
            val sizeBytes = copyUriBounded(context, parsed, temp, MAX_VIEWER_FILE_BYTES, coroutine)
            val officeKind = when (kind) {
                DocumentViewerKind.WORD -> OfficeKind.DOCX
                DocumentViewerKind.PRESENTATION -> OfficeKind.PPTX
                DocumentViewerKind.SPREADSHEET -> OfficeKind.XLSX
                else -> error("Unreachable")
            }
            val doc = OfficeDocumentReader.read(
                temp,
                officeKind,
                MAX_VIEWER_FILE_BYTES,
                MAX_VIEWER_IMAGE_BYTES
            ) {
                coroutine.ensureActive()
            }
            toLoadedDocument(doc, displayName, sizeBytes)
        } finally {
            temp.delete()
        }
    }

    internal fun toLoadedDocument(
        doc: OfficeDocument,
        displayName: String,
        sizeBytes: Long
    ): LoadedDocument = when (doc.kind) {
        OfficeKind.PPTX -> toPresentation(doc, displayName, sizeBytes)
        OfficeKind.DOCX -> toWord(doc, displayName, sizeBytes)
        OfficeKind.XLSX -> toSpreadsheet(doc, displayName, sizeBytes)
    }

    internal fun parseCsvLines(lines: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var currentRow = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var totalCells = 0

        for (line in lines) {
            if (totalCells >= MAX_CSV_CELLS) break
            if (!inQuotes && line.isEmpty()) continue
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                if (inQuotes) {
                    if (ch == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            field.append('"')
                            i += 2
                            continue
                        }
                        inQuotes = false
                    } else {
                        field.append(ch)
                    }
                } else {
                    when (ch) {
                        '"' -> inQuotes = true
                        ',' -> {
                            if (currentRow.size < MAX_CSV_COLUMNS && totalCells < MAX_CSV_CELLS) {
                                currentRow.add(field.toString())
                                totalCells++
                            }
                            field.setLength(0)
                        }
                        else -> field.append(ch)
                    }
                }
                i++
            }
            if (inQuotes) {
                field.append('\n')
            } else {
                if (currentRow.size < MAX_CSV_COLUMNS && totalCells < MAX_CSV_CELLS) {
                    currentRow.add(field.toString())
                    totalCells++
                }
                field.setLength(0)
                rows.add(currentRow)
                currentRow = ArrayList()
            }
        }
        if ((inQuotes || currentRow.isNotEmpty() || field.isNotEmpty()) && totalCells < MAX_CSV_CELLS) {
            if (currentRow.size < MAX_CSV_COLUMNS) {
                currentRow.add(field.toString())
            }
            rows.add(currentRow)
        }
        return rows
    }

    private fun decodeUtf8Lenient(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun toPresentation(
        doc: OfficeDocument,
        displayName: String,
        sizeBytes: Long
    ): LoadedDocument.Presentation {
        val groups = ArrayList<MutableList<OfficeBlock>>()
        var current = ArrayList<OfficeBlock>()
        for (block in doc.blocks) {
            if (block is OfficeBlock.PageBreak) {
                groups.add(current)
                current = ArrayList()
            } else {
                current.add(block)
            }
        }
        groups.add(current)
        while (groups.isNotEmpty() && groups.last().isEmpty()) {
            groups.removeAt(groups.lastIndex)
        }

        val slides = groups.mapIndexed { index, slideBlocks ->
            val paragraphs = slideBlocks
                .mapNotNull { (it as? OfficeBlock.ParagraphBlock)?.paragraph }
                .filter { !it.isBlank }
            val title = (paragraphs.firstOrNull { it.level in 1..4 } ?: paragraphs.firstOrNull())
                ?.text
                ?.trim()
            LoadedSlide(index = index, title = title, blocks = slideBlocks)
        }

        val rawRatio = if (doc.pageWidthPt > 0.0 && doc.pageHeightPt > 0.0) {
            doc.pageWidthPt / doc.pageHeightPt
        } else {
            Double.NaN
        }
        val aspectRatio = if (rawRatio.isFinite() && rawRatio > 0.0) {
            rawRatio.toFloat().coerceIn(0.65f, 2.2f)
        } else {
            16f / 9f
        }

        return LoadedDocument.Presentation(
            displayName = displayName,
            sizeBytes = sizeBytes,
            aspectRatio = aspectRatio,
            slides = slides,
            unsupported = doc.unsupported
        )
    }

    private fun toWord(
        doc: OfficeDocument,
        displayName: String,
        sizeBytes: Long
    ): LoadedDocument.Word {
        val headings = ArrayList<OutlineHeading>()
        var wordCount = 0
        var pageBreakCount = 0

        doc.blocks.forEachIndexed { index, block ->
            when (block) {
                is OfficeBlock.ParagraphBlock -> {
                    val p = block.paragraph
                    if (p.level in 1..4 && !p.isBlank) {
                        headings.add(OutlineHeading(blockIndex = index, level = p.level, text = p.text.trim()))
                    }
                    wordCount += countWords(p.text)
                }
                is OfficeBlock.TableBlock -> {
                    for (row in block.rows) {
                        for (cell in row) {
                            wordCount += countWords(cell.text)
                        }
                    }
                }
                is OfficeBlock.PageBreak -> pageBreakCount++
                is OfficeBlock.ImageBlock,
                is OfficeBlock.Section -> Unit
            }
        }

        return LoadedDocument.Word(
            displayName = displayName,
            sizeBytes = sizeBytes,
            blocks = doc.blocks,
            headings = headings,
            wordCount = wordCount,
            pageBreakCount = pageBreakCount,
            unsupported = doc.unsupported
        )
    }

    private fun toSpreadsheet(
        doc: OfficeDocument,
        displayName: String,
        sizeBytes: Long
    ): LoadedDocument.Spreadsheet {
        val hasSection = doc.blocks.any { it is OfficeBlock.Section }
        if (!hasSection) {
            return LoadedDocument.Spreadsheet(
                displayName = displayName,
                sizeBytes = sizeBytes,
                sheets = listOf(LoadedSheet(title = displayName.ifBlank { "Sheet 1" }, blocks = doc.blocks)),
                unsupported = doc.unsupported
            )
        }

        val sheets = ArrayList<LoadedSheet>()
        var currentTitle: String? = null
        var currentBlocks = ArrayList<OfficeBlock>()

        for (block in doc.blocks) {
            if (block is OfficeBlock.Section) {
                if (currentTitle != null) {
                    sheets.add(LoadedSheet(title = currentTitle, blocks = currentBlocks))
                } else if (currentBlocks.isNotEmpty()) {
                    sheets.add(LoadedSheet(title = displayName.ifBlank { "Sheet 1" }, blocks = currentBlocks))
                }
                currentTitle = block.title.ifBlank { "Sheet ${sheets.size + 1}" }
                currentBlocks = ArrayList()
            } else {
                currentBlocks.add(block)
            }
        }
        if (currentTitle != null) {
            sheets.add(LoadedSheet(title = currentTitle, blocks = currentBlocks))
        }

        return LoadedDocument.Spreadsheet(
            displayName = displayName,
            sizeBytes = sizeBytes,
            sheets = sheets,
            unsupported = doc.unsupported
        )
    }

    private fun countWords(text: String): Int {
        var count = 0
        var inWord = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    private fun resolveTextExtension(displayName: String, mimeType: String?): String {
        val fromName = displayName.trim().substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (fromName.isNotEmpty()) return fromName
        return when (mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
            "text/markdown" -> "md"
            "text/csv" -> "csv"
            "text/tab-separated-values" -> "tsv"
            "application/json" -> "json"
            else -> "txt"
        }
    }

    private fun copyUriBounded(
        context: Context,
        uri: Uri,
        dest: File,
        maxBytes: Long,
        coroutine: CoroutineContext
    ): Long {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("DOCUMENT_UNREADABLE")
        var total = 0L
        input.use { stream ->
            FileOutputStream(dest).use { out ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    coroutine.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "DOCUMENT_TOO_LARGE" }
                    out.write(buffer, 0, read)
                }
            }
        }
        return total
    }

    private fun readUpTo(
        stream: InputStream,
        maxBytes: Int,
        coroutine: CoroutineContext
    ): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, COPY_BUFFER_SIZE))
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = maxBytes
        while (remaining > 0) {
            coroutine.ensureActive()
            val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }
}
