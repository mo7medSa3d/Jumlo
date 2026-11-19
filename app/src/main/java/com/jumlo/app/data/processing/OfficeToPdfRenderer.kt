package com.jumlo.app.data.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import com.jumlo.app.core.ImageWorkPolicy
import com.jumlo.app.core.office.OfficeBlock
import com.jumlo.app.core.office.OfficeCell
import com.jumlo.app.core.office.OfficeDocument
import com.jumlo.app.core.office.OfficeParagraph
import com.jumlo.app.core.office.OfficeRun
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.Closeable
import kotlin.math.min

/** Result of a render: the document plus what had to be degraded. */
data class OfficeRenderStats(val rasterizedBlocks: Int, val droppedImages: Int)
class RenderedOfficePdf(val document: PDDocument, val stats: OfficeRenderStats) : Closeable {
    override fun close() = document.close()
}

/**
 * Renders a parsed [OfficeDocument] to PDF. Latin text stays selectable PDF
 * text; any block containing a character Standard-14 Helvetica cannot encode
 * (notably Arabic script, which also needs shaping PDFBox cannot do) is
 * rasterized through Android's text stack, so shaping stays correct at the
 * cost of selectability for exactly those blocks. Tables keep borders and a
 * bold header row that repeats across pages; images keep aspect and fit the
 * page. Raster blocks taller than one page are sliced across pages.
 */
object OfficeToPdfRenderer {
    private const val SCALE = 2.0f // raster pixels per point (144 dpi)
    private const val MAX_RASTER_TABLE_COLUMNS = 512
    private const val MAX_RASTER_CELL_CHARS = 100_000
    private const val MAX_RASTER_ROW_CHARS = 250_000

    fun render(doc: OfficeDocument, maxBitmapBytes: Long, checkCancel: () -> Unit): RenderedOfficePdf {
        val document = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        try {
            val engine = Engine(document, doc, maxBitmapBytes, checkCancel)
            engine.run()
            return RenderedOfficePdf(document, engine.stats)
        } catch (t: Throwable) {
            document.close()
            throw t
        }
    }

    private class Engine(
        private val document: PDDocument,
        private val doc: OfficeDocument,
        private val maxBitmapBytes: Long,
        private val checkCancel: () -> Unit
    ) {
        var rasterized = 0
        var dropped = 0
        val stats get() = OfficeRenderStats(rasterized, dropped)

        private val pageWidth = doc.pageWidthPt.toFloat()
        private val pageHeight = doc.pageHeightPt.toFloat()
        // A custom page smaller than four margins would leave no text area;
        // clamp so the available width and height stay positive.
        private val margin = doc.marginPt.toFloat()
            .coerceAtMost(minOf(pageWidth, pageHeight) / 4f)
        private val availWidth = pageWidth - 2 * margin
        private val availHeight = pageHeight - 2 * margin
        private var page: PDPage? = null
        private var stream: PDPageContentStream? = null
        private var cursorY = 0f
        private var hasContent = false

        fun run() {
            try {
                newPage()
                doc.blocks.forEach { block ->
                    checkCancel()
                    when (block) {
                        is OfficeBlock.PageBreak -> newPageIfNeeded()
                        is OfficeBlock.Section -> {
                            newPageIfNeeded()
                            drawParagraph(OfficeBlock.ParagraphBlock(
                                OfficeParagraph(listOf(OfficeRun(block.title, bold = true)), level = 1)))
                        }
                        is OfficeBlock.ParagraphBlock -> drawParagraph(block)
                        is OfficeBlock.TableBlock -> drawTable(block)
                        is OfficeBlock.ImageBlock -> drawImage(block)
                    }
                }
            } finally {
                runCatching { stream?.close() }
                stream = null
            }
        }

        private fun newPage() {
            stream?.close()
            page = PDPage(PDRectangle(pageWidth, pageHeight))
            document.addPage(page)
            stream = PDPageContentStream(document, page)
            cursorY = pageHeight - margin
            hasContent = false
        }

        private fun newPageIfNeeded() {
            if (hasContent) newPage()
        }

        private fun needSpace(height: Float) {
            if (cursorY - height < margin) newPage()
        }

        private fun font(bold: Boolean, italic: Boolean): PDFont = when {
            bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            bold -> PDType1Font.HELVETICA_BOLD
            italic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }

        private fun widthOf(text: String, bold: Boolean, italic: Boolean, size: Float): Float =
            font(bold, italic).getStringWidth(text) / 1000f * size

        private fun isLatin(text: String): Boolean =
            text.all { ch -> ch == '\n' || ch == '\t' || ch in ' '..'~' || (ch.code >= 0xA0 && WinAnsiEncoding.INSTANCE.contains(ch.code)) }

        private fun isRtlText(text: String): Boolean {
            for (ch in text) when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
            }
            return false
        }

        private fun sizeFor(level: Int): Float = when (level) {
            1 -> 22f; 2 -> 16f; 3 -> 13.5f; 4 -> 12f; else -> 11f
        }

        private fun beforeFor(level: Int): Float = when (level) {
            1 -> 14f; 2 -> 12f; 3 -> 10f; 4 -> 8f; else -> 0f
        }

        private data class Word(val text: String, val bold: Boolean, val italic: Boolean)

        private fun drawParagraph(block: OfficeBlock.ParagraphBlock) {
            val paragraph = block.paragraph
            if (paragraph.isBlank) {
                cursorY -= 6f
                return
            }
            if (!isLatin(paragraph.text)) {
                drawRasterParagraph(paragraph)
                return
            }
            val size = sizeFor(paragraph.level)
            val lineHeight = size * 1.35f
            val spaceWidth = widthOf(" ", false, false, size)
            val baseIndent = paragraph.listIndent * 18f
            val marker = paragraph.listMarker
            val markerWidth = marker?.let { widthOf(it, true, false, size) } ?: 0f
            val spaceBefore = if (hasContent) beforeFor(paragraph.level) else 0f
            needSpace(spaceBefore + lineHeight)
            if (hasContent) cursorY -= spaceBefore
            val lines = ArrayList<List<Word>>()
            var current = ArrayList<Word>()
            var currentWidth = 0f
            val lineWidth = (availWidth - baseIndent - markerWidth).coerceAtLeast(36f)
            fun flushLine() {
                if (current.isNotEmpty()) {
                    lines += current.toList()
                    current = ArrayList(); currentWidth = 0f
                }
            }
            for (run in paragraph.runs) {
                val segments = run.text.replace('\t', ' ').split('\n')
                for ((segIndex, segment) in segments.withIndex()) {
                    for (word in segment.split(' ').filter { it.isNotEmpty() }) {
                        var remaining = word
                        while (remaining.isNotEmpty()) {
                            val wordWidth = widthOf(remaining, run.bold, run.italic, size)
                            if (currentWidth + wordWidth <= lineWidth) {
                                current += Word(remaining, run.bold, run.italic)
                                currentWidth += wordWidth + spaceWidth
                                remaining = ""
                            } else if (current.isNotEmpty() && wordWidth <= lineWidth) {
                                flushLine()
                            } else {
                                val available = (lineWidth - currentWidth).coerceAtLeast(size)
                                val cut = cutWord(remaining, run.bold, run.italic, size, available)
                                current += Word(cut, run.bold, run.italic)
                                remaining = remaining.substring(cut.length)
                                flushLine()
                            }
                        }
                    }
                    if (segIndex < segments.lastIndex) flushLine()
                }
            }
            flushLine()
            var first = true
            val ascent = size * 0.85f
            for (line in lines) {
                checkCancel()
                needSpace(lineHeight)
                val wordsWidth = line.sumOf { widthOf(it.text, it.bold, it.italic, size).toDouble() }.toFloat() +
                    spaceWidth * (line.size - 1).coerceAtLeast(0)
                val fullWidth = wordsWidth + if (first && marker != null) markerWidth else 0f
                // cursorY tracks the TOP edge of the next block consistently for both vector and raster blocks.
                // Offset by ascent so PDF text baseline sits inside [cursorY - lineHeight, cursorY].
                val startX = when (paragraph.align) {
                    "center" -> margin + baseIndent + (availWidth - baseIndent - fullWidth) / 2f
                    "right" -> margin + availWidth - fullWidth
                    else -> margin + baseIndent + if (first) 0f else markerWidth
                }
                val content = stream!!
                content.beginText()
                content.newLineAtOffset(startX, cursorY - ascent)
                if (first && marker != null) {
                    content.setFont(font(true, false), size)
                    content.showText(marker)
                }
                for ((index, word) in line.withIndex()) {
                    content.setFont(font(word.bold, word.italic), size)
                    if (index > 0 || (first && marker != null)) content.showText(" ")
                    content.showText(word.text)
                }
                content.endText()
                cursorY -= lineHeight
                first = false
                hasContent = true
            }
            cursorY -= 4f
        }

        private fun cutWord(word: String, bold: Boolean, italic: Boolean, size: Float, room: Float): String {
            var taken = 0
            var width = 0f
            while (taken < word.length) {
                val w = widthOf(word[taken].toString(), bold, italic, size)
                if (width + w > room && taken > 0) break
                width += w
                taken++
            }
            return word.substring(0, taken.coerceAtLeast(1))
        }

        private fun drawRasterParagraph(paragraph: OfficeParagraph) {
            val size = sizeFor(paragraph.level)
            val baseIndent = paragraph.listIndent * 18f
            val blockWidthPt = (availWidth - baseIndent).coerceAtLeast(48f)
            val widthPx = (blockWidthPt * SCALE).toInt().coerceAtLeast(1)
            val displayText = buildString {
                paragraph.listMarker?.let { append(it).append(' ') }
                append(paragraph.text)
            }
            val spaceBefore = if (hasContent) beforeFor(paragraph.level) else 0f
            if (hasContent && cursorY - spaceBefore - size * 1.35f < margin) {
                newPage()
            } else if (hasContent) {
                cursorY -= spaceBefore
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = size * SCALE
                typeface = Typeface.create(Typeface.DEFAULT,
                    if (paragraph.runs.any { it.bold }) Typeface.BOLD else Typeface.NORMAL)
                if (paragraph.runs.any { it.italic }) textSkewX = -0.25f
            }
            val rtl = isRtlText(displayText)
            val align = when (paragraph.align) {
                "center" -> android.text.Layout.Alignment.ALIGN_CENTER
                "right" -> if (rtl) android.text.Layout.Alignment.ALIGN_NORMAL else android.text.Layout.Alignment.ALIGN_OPPOSITE
                else -> android.text.Layout.Alignment.ALIGN_NORMAL
            }
            val layout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, paint, widthPx)
                .setAlignment(align)
                .setTextDirection(if (rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1.35f).build()
            drawSliced(layout, widthPx, layout.height, if (rtl) margin else margin + baseIndent, blockWidthPt)
            cursorY -= 4f
            rasterized++
        }

        /**
         * Draws a layout in vertical slices aligned to StaticLayout line boundaries so
         * characters are never sliced horizontally across page breaks.
         */
        private fun drawSliced(
            layout: StaticLayout,
            widthPx: Int,
            totalHeightPx: Int,
            xPt: Float = margin,
            drawWidthPt: Float = availWidth
        ) {
            var lineIdx = 0
            val lineCount = layout.lineCount
            while (lineIdx < lineCount) {
                checkCancel()
                val startY = layout.getLineTop(lineIdx)
                var roomPt = cursorY - margin
                val firstLineHeightPx = (layout.getLineBottom(lineIdx) - startY).coerceAtLeast(1)
                if (roomPt * SCALE < firstLineHeightPx && hasContent) {
                    newPage()
                    roomPt = cursorY - margin
                }
                val roomPx = (roomPt * SCALE).toInt().coerceAtLeast(1)
                var endLine = lineIdx
                while (endLine + 1 < lineCount && layout.getLineBottom(endLine + 1) - startY <= roomPx) {
                    endLine++
                }
                val endY = layout.getLineBottom(endLine).coerceAtMost(totalHeightPx)
                val slicePx = (endY - startY).coerceAtLeast(1)
                require(widthPx.toLong() * slicePx * 4L <= maxBitmapBytes / 2L) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
                val bitmap = Bitmap.createBitmap(widthPx, slicePx, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.save()
                    canvas.translate(0f, -startY.toFloat())
                    layout.draw(canvas)
                    canvas.restore()
                    val heightPt = slicePx / SCALE
                    val image = JPEGFactory.createFromImage(document, bitmap, 0.92f)
                    stream!!.drawImage(image, xPt, cursorY - heightPt, drawWidthPt, heightPt)
                    cursorY -= heightPt
                    hasContent = true
                } finally {
                    bitmap.recycle()
                }
                lineIdx = endLine + 1
                if (lineIdx < lineCount) newPage()
            }
        }

        private fun drawTable(block: OfficeBlock.TableBlock) {
            val rows = block.rows
            if (rows.isEmpty()) return
            if (rows.any { row -> row.any { !isLatin(it.text) } }) {
                drawRasterTable(block)
                return
            }
            val size = 10f
            val lineHeight = size * 1.35f
            val spaceWidth = widthOf(" ", false, false, size)
            val pad = 4f
            require(rows.all { row -> row.all { it.colSpan >= 1 } }) { "OFFICE_INVALID" }
            val colCount = rows.maxOf { row -> row.sumOf { it.colSpan } }
            if (colCount <= 0) return
            val weights = DoubleArray(colCount)
            for (row in rows) {
                var col = 0
                for (cell in row) {
                    val longest = cell.text.replace('\t', ' ').split('\n', ' ').maxOfOrNull { widthOf(it, cell.bold, false, size).toDouble() } ?: 0.0
                    for (k in 0 until cell.colSpan) {
                        if (col + k < colCount) weights[col + k] = maxOf(weights[col + k], longest / cell.colSpan)
                    }
                    col += cell.colSpan
                }
            }
            val totalWeight = weights.sum().takeIf { it > 0 } ?: colCount.toDouble()
            val colWidths = weights.map { (it / totalWeight * availWidth).toFloat().coerceAtLeast(40f) }.toMutableList()
            val sumWidths = colWidths.sum()
            for (i in colWidths.indices) colWidths[i] = colWidths[i] / sumWidths * availWidth

            fun wrapCell(text: String, bold: Boolean, width: Float): List<String> {
                val inner = (width - 2 * pad).coerceAtLeast(10f)
                val out = ArrayList<String>()
                for (segment in text.replace('\t', ' ').split('\n')) {
                    var line = StringBuilder()
                    var lineWidth = 0f
                    for (word in segment.split(' ').filter { it.isNotEmpty() }) {
                        var remaining = word
                        while (remaining.isNotEmpty()) {
                            val w = widthOf(remaining, bold, false, size)
                            val needed = if (line.isNotEmpty()) spaceWidth + w else w
                            if (lineWidth + needed <= inner) {
                                if (line.isNotEmpty()) { line.append(' '); lineWidth += spaceWidth }
                                line.append(remaining); lineWidth += w
                                remaining = ""
                            } else if (line.isNotEmpty() && w <= inner) {
                                out += line.toString(); line = StringBuilder(); lineWidth = 0f
                            } else {
                                val avail = (inner - if (line.isNotEmpty()) lineWidth + spaceWidth else 0f).coerceAtLeast(size)
                                val cut = cutWord(remaining, bold, false, size, avail)
                                if (line.isNotEmpty()) { line.append(' '); lineWidth += spaceWidth }
                                line.append(cut)
                                remaining = remaining.substring(cut.length)
                                out += line.toString(); line = StringBuilder(); lineWidth = 0f
                            }
                        }
                    }
                    if (line.isNotEmpty() || segment.isEmpty()) out += line.toString()
                }
                if (out.isEmpty()) out += ""
                return out
            }

            fun cellWidth(row: List<OfficeCell>, upto: Int): Float {
                var col = 0
                for (cell in row) {
                    if (col == upto) return (0 until cell.colSpan).sumOf { colWidths[col + it].toDouble() }.toFloat()
                    col += cell.colSpan
                }
                return colWidths[0]
            }

            fun drawRow(row: List<OfficeCell>, yTop: Float, height: Float, header: Boolean) {
                val content = stream!!
                var col = 0
                for (cell in row) {
                    val w = cellWidth(row, col)
                    val x = margin + (0 until col).sumOf { colWidths[it].toDouble() }.toFloat()
                    val lines = wrapCell(cell.text, cell.bold || header, w)
                    if (header) {
                        content.setNonStrokingColor(0.93f, 0.93f, 0.93f)
                        content.addRect(x, yTop - height, w, height)
                        content.fill()
                        content.setNonStrokingColor(0f, 0f, 0f)
                    }
                    content.beginText()
                    content.setFont(font(cell.bold || header, false), size)
                    lines.forEachIndexed { li, text ->
                        if (li == 0) content.newLineAtOffset(x + pad, yTop - pad - size)
                        else content.newLineAtOffset(0f, -lineHeight)
                        content.showText(text)
                    }
                    content.endText()
                    content.setStrokingColor(0.75f, 0.75f, 0.75f)
                    content.setLineWidth(0.5f)
                    content.addRect(x, yTop - height, w, height)
                    content.stroke()
                    col += cell.colSpan
                }
            }

            fun headerHeight(): Float {
                if (!block.headerRow) return 0f
                val header = rows[0]
                var col = 0
                var maxLines = 1
                for (cell in header) {
                    maxLines = maxOf(maxLines, wrapCell(cell.text, true, cellWidth(header, col)).size)
                    col += cell.colSpan
                }
                return maxLines * lineHeight + 2 * pad
            }

            val cachedHeaderHeight = if (block.headerRow) headerHeight() else 0f
            var rowIndex = 0
            var headerDrawnOnPage = false
            while (rowIndex < rows.size) {
                checkCancel()
                val row = rows[rowIndex]
                var col = 0
                var maxLines = 1
                for (cell in row) {
                    maxLines = maxOf(maxLines, wrapCell(cell.text, cell.bold, cellWidth(row, col)).size)
                    col += cell.colSpan
                }
                val height = maxLines * lineHeight + 2 * pad
                // Reserve the header too, so a row never starts under a header
                // that pushed it off the page. A single row taller than a whole
                // page still draws (and may clip) instead of looping forever.
                val needed = height + if (block.headerRow && !headerDrawnOnPage) cachedHeaderHeight else 0f
                if (cursorY - needed < margin && needed <= availHeight) {
                    newPage()
                    headerDrawnOnPage = false
                    continue
                }
                if (block.headerRow && !headerDrawnOnPage) {
                    val hh = cachedHeaderHeight
                    drawRow(rows[0], cursorY, hh, header = true)
                    cursorY -= hh
                    hasContent = true
                    headerDrawnOnPage = true
                    if (rowIndex == 0) {
                        rowIndex++
                        continue
                    }
                }
                drawRow(row, cursorY, height, header = false)
                cursorY -= height
                hasContent = true
                rowIndex++
            }
            cursorY -= 4f
        }

        private data class RasterCellLayout(val span: Int, val layout: StaticLayout)
        private data class RasterRowLayout(val cells: List<RasterCellLayout>, val height: Int)

        private fun drawRasterTable(block: OfficeBlock.TableBlock) {
            val rows = block.rows
            if (rows.isEmpty()) return
            val widthPx = (availWidth * SCALE).toInt().coerceAtLeast(1)
            val pageHeightLimit = (availHeight * SCALE).toInt().coerceAtLeast(1)
            val bitmapHeightLimit = (maxBitmapBytes / 2L / (widthPx.toLong() * 4L))
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            require(bitmapHeightLimit > 0) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
            val colCount = rows.maxOf { row -> row.sumOf { it.colSpan.toLong() } }
            require(colCount in 1L..MAX_RASTER_TABLE_COLUMNS.toLong()) { "OFFICE_SIZE_LIMIT" }
            val colWidthPx = widthPx / colCount.toInt()
            require(colWidthPx > 0) { "OFFICE_SIZE_LIMIT" }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 10f * SCALE
            }
            val boldPaint = TextPaint(paint).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            fun layoutRow(row: List<OfficeCell>): RasterRowLayout {
                var col = 0
                var rowChars = 0L
                val cells = ArrayList<RasterCellLayout>(row.size)
                var rowHeight = 1
                for (cell in row) {
                    checkCancel()
                    rowChars += cell.text.length.toLong()
                    require(cell.text.length <= MAX_RASTER_CELL_CHARS &&
                        rowChars <= MAX_RASTER_ROW_CHARS) { "OFFICE_SIZE_LIMIT" }
                    require(cell.colSpan in 1..MAX_RASTER_TABLE_COLUMNS &&
                        col + cell.colSpan <= MAX_RASTER_TABLE_COLUMNS) { "OFFICE_SIZE_LIMIT" }
                    val cellPad = minOf(8, colWidthPx * cell.colSpan / 4)
                    val cellWidth = (colWidthPx * cell.colSpan - 2 * cellPad).coerceAtLeast(1)
                    val rtl = isRtlText(cell.text)
                    val layout = StaticLayout.Builder.obtain(
                        cell.text, 0, cell.text.length,
                        if (cell.bold) boldPaint else paint, cellWidth
                    ).setTextDirection(if (rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR)
                        .setLineSpacing(0f, 1.35f).build()
                    cells += RasterCellLayout(cell.colSpan, layout)
                    rowHeight = maxOf(rowHeight, layout.height + 2 * cellPad)
                    col += cell.colSpan
                }
                return RasterRowLayout(cells, rowHeight)
            }

            // Keep only the layouts needed for the current page-sized bitmap.
            // The previous implementation created StaticLayout for every cell in
            // the workbook/slide deck before rendering the first page.
            var start = 0
            while (start < rows.size) {
                val chunk = ArrayList<RasterRowLayout>()
                var chunkHeight = 0L
                while (start + chunk.size < rows.size) {
                    checkCancel()
                    val rowLayout = layoutRow(rows[start + chunk.size])
                    val nextHeight = rowLayout.height.toLong()
                    if (chunk.isNotEmpty() && chunkHeight + nextHeight > pageHeightLimit) break
                    require(nextHeight <= bitmapHeightLimit.toLong() - chunkHeight) {
                        "IMAGE_MEMORY_BUDGET_EXCEEDED"
                    }
                    chunk += rowLayout
                    chunkHeight += nextHeight
                    if (chunkHeight >= pageHeightLimit) break
                }
                require(chunk.isNotEmpty() && chunkHeight in 1..Int.MAX_VALUE.toLong()) {
                    "IMAGE_MEMORY_BUDGET_EXCEEDED"
                }
                val chunkHeightPx = chunkHeight.toInt()
                val bitmap = Bitmap.createBitmap(widthPx, chunkHeightPx, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    val linePaint = Paint().apply {
                        color = Color.LTGRAY
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    var y = 0
                    for (rowLayout in chunk) {
                        var x = 0
                        for (cell in rowLayout.cells) {
                            val cellWidth = colWidthPx * cell.span
                            val cellPad = minOf(8, cellWidth / 4)
                            canvas.save()
                            canvas.clipRect(x.toFloat(), y.toFloat(), (x + cellWidth).toFloat(), (y + rowLayout.height).toFloat())
                            canvas.translate((x + cellPad).toFloat(), (y + cellPad).toFloat())
                            cell.layout.draw(canvas)
                            canvas.restore()
                            canvas.drawRect(
                                x.toFloat(), y.toFloat(),
                                (x + cellWidth).toFloat(), (y + rowLayout.height).toFloat(),
                                linePaint
                            )
                            x += cellWidth
                        }
                        y += rowLayout.height
                    }
                    val heightPt = chunkHeightPx / SCALE
                    needSpace(heightPt)
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    stream!!.drawImage(image, margin, cursorY - heightPt, availWidth, heightPt)
                    cursorY -= heightPt
                    hasContent = true
                } finally {
                    bitmap.recycle()
                }
                start += chunk.size
                if (start < rows.size) newPage()
            }
            cursorY -= 4f
            rasterized++
        }

        /**
         * Decode embedded raster images only after a bounds-only pass proves the
         * allocation fits. LosslessFactory may hold both the bitmap and encoded
         * image data at once, so reserve half the caller's bitmap budget.
         */
        private fun decodeEmbeddedRaster(bytes: ByteArray): Bitmap {
            val decodeBudget = maxBitmapBytes / 2L
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "IMAGE_DECODE_FAILED" }
            ImageWorkPolicy.requireFits(bounds.outWidth, bounds.outHeight, 0L, decodeBudget)
            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
            ) ?: throw IllegalArgumentException("IMAGE_DECODE_FAILED")
            try {
                require(bitmap.width == bounds.outWidth && bitmap.height == bounds.outHeight) {
                    "IMAGE_DECODE_DIMENSIONS_CHANGED"
                }
                require(bitmap.allocationByteCount.toLong() <= decodeBudget) {
                    "IMAGE_MEMORY_BUDGET_EXCEEDED"
                }
                return bitmap
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        }

        private fun drawImage(block: OfficeBlock.ImageBlock) {
            checkCancel()
            try {
                val image = if (block.extension.equals("jpg", true) || block.extension.equals("jpeg", true)) {
                    JPEGFactory.createFromByteArray(document, block.bytes)
                } else {
                    val bitmap = decodeEmbeddedRaster(block.bytes)
                    try {
                        LosslessFactory.createFromImage(document, bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
                require(image.width > 0 && image.height > 0) { "IMAGE_DECODE_FAILED" }
                val maxHeight = pageHeight - 2 * margin
                val scale = min(availWidth / image.width, maxHeight / image.height).coerceAtMost(1f)
                val w = image.width * scale
                val h = image.height * scale
                needSpace(h)
                stream!!.drawImage(image, margin + (availWidth - w) / 2f, cursorY - h, w, h)
                cursorY -= h + 4f
                hasContent = true
            } catch (cancel: java.util.concurrent.CancellationException) {
                throw cancel
            } catch (failure: IllegalArgumentException) {
                if (failure.message == "IMAGE_MEMORY_BUDGET_EXCEEDED" ||
                    failure.message == "IMAGE_DIMENSIONS_UNREPRESENTABLE") throw failure
                dropped++
            } catch (_: Exception) {
                dropped++
            }
        }
    }
}
