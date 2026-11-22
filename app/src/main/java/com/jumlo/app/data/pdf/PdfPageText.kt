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
import java.text.Bidi
import java.text.Normalizer
import kotlin.math.roundToInt

/** Page-at-a-time extraction keeps paragraph separators and fails before materializing unbounded text. */
object PdfPageText {
    private val ZERO_WIDTH_REGEX = Regex("[\u200B-\u200D\u202A-\u202E\uFEFF]")
    private val TRAILING_SPACES_REGEX = Regex("[ \t]+\n")

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

    /** Returns true when extracted text contains replacement glyphs or PUA noise from a broken CMap. */
    internal fun isLikelyGarbled(raw: String): Boolean {
        var nonSpace = 0
        var garbled = 0
        for (ch in raw) {
            if (ch.isWhitespace()) continue
            nonSpace++
            if (ch == '\uFFFD' || ch in '\uE000'..'\uF8FF' || (ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r')) {
                garbled++
            }
        }
        return nonSpace > 0 && (garbled >= 8 || garbled * 100 / nonSpace >= 15)
    }

    private fun extractPage(doc: PDDocument, page: Int, structured: Boolean, checkCancel: () -> Unit): Pair<String, List<OfficeTextSpan>> {
        require(page in 0 until doc.numberOfPages) { "INVALID_PAGE_INDEX" }
        val rawBuffer = StringBuilder()
        var currentLineIndex = 0
        val lineFontWeights = HashMap<Int, HashMap<Pair<Double, Boolean>, Int>>()

        val bounded = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, length: Int) {
                checkCancel()
                require(length <= OfficePackageWriter.MAX_PAGE_TEXT - rawBuffer.length) { "OFFICE_TEXT_BUDGET_EXCEEDED" }
                for (i in offset until offset + length) {
                    val ch = buffer[i]
                    rawBuffer.append(ch)
                    if (ch == '\n') currentLineIndex++
                }
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
                if (structured && textPositions.isNotEmpty()) {
                    val weights = lineFontWeights.getOrPut(currentLineIndex) { HashMap() }
                    for (position in textPositions) {
                        val descriptor = position.font.fontDescriptor
                        val bold = descriptor?.isForceBold == true || (descriptor?.fontWeight ?: 0f) >= 600f ||
                            position.font.name.orEmpty().contains("bold", ignoreCase = true)
                        val size = position.fontSizeInPt.takeIf { it.isFinite() && it > 0f } ?: 0f
                        val key = (size * 2).roundToInt() / 2.0 to bold
                        weights[key] = (weights[key] ?: 0) + position.unicode.orEmpty().length.coerceAtLeast(1)
                    }
                }
                super.writeString(text, textPositions)
            }
        }.apply {
            sortByPosition = true
            startPage = page + 1
            endPage = page + 1
            lineSeparator = "\n"
            paragraphEnd = "\n\n"
        }
        stripper.writeText(doc, bounded)

        val rawText = rawBuffer.toString()
        if (rawText.isBlank() || isLikelyGarbled(rawText)) return "" to emptyList()

        val result = StringBuilder(rawText.length)
        val spans = mutableListOf<OfficeTextSpan>()
        val rawLines = rawText.split('\n')
        for ((index, rawLine) in rawLines.withIndex()) {
            checkCancel()
            if (index > 0) result.append('\n')
            val start = result.length
            // PdfBox-Android's PDFTextStripper already applies java.text.Bidi to emit logical Arabic;
            // only reverse lines that still carry unnormalized visual Arabic Presentation Forms.
            val cleaned = cleanLine(rawLine, restoreVisualRtl = hasArabicPresentationForms(rawLine))
            if (cleaned.isNotEmpty()) {
                require(cleaned.length <= OfficePackageWriter.MAX_PAGE_TEXT - result.length) { "OFFICE_TEXT_BUDGET_EXCEEDED" }
                result.append(cleaned)
                if (structured) {
                    val dominant = lineFontWeights[index]?.maxByOrNull { it.value }?.key
                    if (dominant != null && dominant.first > 0.0) {
                        spans += OfficeTextSpan(start, result.length, dominant.first, dominant.second)
                    }
                }
            }
        }

        val finalText = result.toString()
        return (if (structured) finalText else finalText.replace(TRAILING_SPACES_REGEX, "\n").trim()) to spans
    }

    /** Restore visual-reversed RTL runs before NFKC so Arabic ligatures (e.g. Lam-Alef) expand in logical order. */
    internal fun cleanLine(raw: String, restoreVisualRtl: Boolean = hasArabicPresentationForms(raw)): String {
        if (raw.isEmpty()) return ""
        val ordered = if (restoreVisualRtl) restoreVisualRtlToLogical(raw) else raw
        return Normalizer.normalize(ordered, Normalizer.Form.NFKC)
            .replace("\uFFFD", "")
            .replace("\u00AD", "")
            .replace(ZERO_WIDTH_REGEX, "")
    }

    internal fun cleanExtractedText(raw: String): String {
        if (raw.isBlank() || isLikelyGarbled(raw)) return ""
        return raw.split('\n')
            .joinToString("\n") { line -> cleanLine(line, restoreVisualRtl = hasArabicPresentationForms(line)) }
            .replace(TRAILING_SPACES_REGEX, "\n")
            .trim()
    }

    internal fun hasArabicPresentationForms(text: String): Boolean = text.any { ch ->
        ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFC'
    }

    private fun isRtlChar(ch: Char): Boolean {
        val dir = Character.getDirectionality(ch)
        return dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT || dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun isLtrChar(ch: Char): Boolean =
        Character.getDirectionality(ch) == Character.DIRECTIONALITY_LEFT_TO_RIGHT

    private fun isCombiningMark(ch: Char): Boolean {
        val type = Character.getType(ch)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun mirrorBracket(ch: Char): Char = when (ch) {
        '(' -> ')'; ')' -> '('
        '[' -> ']'; ']' -> '['
        '{' -> '}'; '}' -> '{'
        '<' -> '>'; '>' -> '<'
        '\u00AB' -> '\u00BB'; '\u00BB' -> '\u00AB'
        else -> ch
    }

    private fun appendReversedRtlRun(out: StringBuilder, text: String, start: Int, end: Int) {
        var i = end - 1
        while (i >= start) {
            val clusterEnd = i + 1
            while (i > start && isCombiningMark(text[i])) i--
            if (i > start && Character.isLowSurrogate(text[i]) && Character.isHighSurrogate(text[i - 1])) {
                out.append(text[i - 1]).append(text[i])
                for (m in (i + 1) until clusterEnd) out.append(text[m])
                i -= 2
            } else {
                out.append(mirrorBracket(text[i]))
                for (m in (i + 1) until clusterEnd) out.append(text[m])
                i--
            }
        }
    }

    /** Reorders a visual left-to-right line extracted by PDFBox back to logical Unicode order while keeping LTR runs intact. */
    internal fun restoreVisualRtlToLogical(visual: String): String {
        var rtlCount = 0
        var ltrCount = 0
        for (ch in visual) {
            if (isRtlChar(ch)) rtlCount++
            else if (isLtrChar(ch)) ltrCount++
        }
        if (rtlCount == 0) return visual

        val baseDir = if (rtlCount >= ltrCount) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT
        val bidi = Bidi(visual, baseDir)
        val runCount = bidi.runCount
        val out = StringBuilder(visual.length)
        val runIndices = if (baseDir == Bidi.DIRECTION_RIGHT_TO_LEFT) (runCount - 1 downTo 0) else (0 until runCount)
        for (r in runIndices) {
            val start = bidi.getRunStart(r)
            val end = bidi.getRunLimit(r)
            if ((bidi.getRunLevel(r) and 1) == 1) {
                appendReversedRtlRun(out, visual, start, end)
            } else {
                out.append(visual, start, end)
            }
        }
        return out.toString()
    }
}
