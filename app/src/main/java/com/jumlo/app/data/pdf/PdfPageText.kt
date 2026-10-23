package com.jumlo.app.data.pdf

import com.jumlo.app.core.office.OfficePackageWriter
import com.jumlo.app.core.office.OfficeTextLayout
import com.jumlo.app.core.office.OfficeTextPage
import com.jumlo.app.core.office.OfficeTextSpan
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.IOException
import java.io.Writer
import kotlin.math.roundToInt

/** Page-at-a-time extraction keeps paragraph separators and fails before materializing unbounded text. */
object PdfPageText {
    fun open(file: File): PDDocument {
        val doc = try { PdfBoxBackend.open(file, null) }
        catch (error: InvalidPasswordException) { throw IllegalArgumentException("PDF_ENCRYPTED", error) }
        catch (error: java.io.FileNotFoundException) { throw error }
        catch (error: IOException) { throw IllegalArgumentException("PDF_INVALID", error) }
        try {
            require(!doc.isEncrypted) { "PDF_ENCRYPTED" }
            require(doc.numberOfPages > 0) { "EMPTY_PDF" }
            return doc
        } catch (error: Throwable) { doc.close(); throw error }
    }

    fun extract(doc: PDDocument, page: Int, checkCancel: () -> Unit = {}): String {
        return extractPage(doc, page, false, checkCancel).first.trim()
    }

    fun extractStructured(doc: PDDocument, page: Int, checkCancel: () -> Unit = {}): OfficeTextPage {
        val (text, spans) = extractPage(doc, page, true, checkCancel)
        return OfficeTextLayout.fromExtracted(text, spans)
    }

    private fun extractPage(doc: PDDocument, page: Int, structured: Boolean, checkCancel: () -> Unit): Pair<String, List<OfficeTextSpan>> {
        require(page in 0 until doc.numberOfPages) { "INVALID_PAGE_INDEX" }
        val result = StringBuilder()
        val spans = mutableListOf<OfficeTextSpan>()
        val bounded = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, length: Int) {
                checkCancel()
                require(length <= OfficePackageWriter.MAX_PAGE_TEXT - result.length) { "OFFICE_TEXT_BUDGET_EXCEEDED" }
                result.append(buffer, offset, length)
            }
            override fun flush() = Unit
            override fun close() = Unit
        }
        val stripper = object : PDFTextStripper() {
            private var processedCharacters = 0

            override fun processTextPosition(position: TextPosition) {
                checkCancel()
                // PDFTextStripper retains page glyphs before it writes to our bounded Writer.
                // Bound that earlier stage too, including overlapping/repeated glyphs.
                val count = position.unicode.orEmpty().length.coerceAtLeast(1)
                require(count <= OfficePackageWriter.MAX_PAGE_TEXT - processedCharacters) { "OFFICE_TEXT_BUDGET_EXCEEDED" }
                processedCharacters += count
                super.processTextPosition(position)
            }

            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val start = result.length
                super.writeString(text, textPositions)
                if (structured && result.length > start && textPositions.isNotEmpty()) {
                    val fonts = textPositions.groupBy { position ->
                        val descriptor = position.font.fontDescriptor
                        val bold = descriptor?.isForceBold == true || (descriptor?.fontWeight ?: 0f) >= 600f ||
                            position.font.name.orEmpty().contains("bold", ignoreCase = true)
                        val size = position.fontSizeInPt.takeIf { it.isFinite() && it > 0f } ?: 0f
                        (size * 2).roundToInt() / 2.0 to bold
                    }
                    val dominant = fonts.maxByOrNull { (_, positions) -> positions.sumOf { it.unicode.orEmpty().length.coerceAtLeast(1) } }!!.key
                    spans += OfficeTextSpan(start, result.length, dominant.first, dominant.second)
                }
            }
        }.apply {
            sortByPosition = true
            startPage = page + 1
            endPage = page + 1
            lineSeparator = "\n"
            paragraphEnd = "\n\n"
        }
        stripper.writeText(doc, bounded)
        return result.toString() to spans
    }
}
