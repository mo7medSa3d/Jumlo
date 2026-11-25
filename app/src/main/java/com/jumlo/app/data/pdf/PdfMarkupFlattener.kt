package com.jumlo.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.jumlo.app.core.NormPoint
import com.jumlo.app.core.PdfDisplayCoordinates
import com.jumlo.app.core.PdfDisplayFrame
import com.jumlo.app.core.PdfMarkupItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.UUID
import kotlin.math.ceil

/** Flattens ink, highlights, Arabic-safe text boxes, checkmarks, and saved signatures into a verified PDF. */
object PdfMarkupFlattener {
    fun flatten(
        inputPdf: File,
        outputPdf: File,
        items: List<PdfMarkupItem>,
        checkCancel: () -> Unit = {}
    ): Int {
        require(items.isNotEmpty()) { "NO_MARKUP_ITEMS" }
        val byPage = items.groupBy { it.pageIndex }
        val pageCount: Int
        PdfBoxBackend.open(inputPdf, null).use { doc ->
            require(!doc.isEncrypted) { "PDF_ENCRYPTED" }
            pageCount = doc.numberOfPages
            require(pageCount > 0) { "EMPTY_PDF" }
            for ((pageIndex, pageItems) in byPage) {
                checkCancel()
                if (pageIndex !in 0 until pageCount) continue
                val page = doc.getPage(pageIndex)
                val frame = displayFrame(page)
                val matrix = displayMatrix(frame)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    // Transform once into upright display space (bottom-left origin, 0..widthPt x 0..heightPt)
                    // so strokes, checkmarks, text, and signatures render upright on 0/90/180/270 rotated pages.
                    cs.transform(matrix)
                    try {
                        for (item in pageItems) {
                            checkCancel()
                            when (item) {
                                is PdfMarkupItem.InkStroke -> drawStroke(cs, frame, item.points, item.colorArgb, item.widthNorm, 1f)
                                is PdfMarkupItem.HighlightStroke -> drawStroke(cs, frame, item.points, item.colorArgb, item.widthNorm, item.alpha)
                                is PdfMarkupItem.CheckMark -> drawCheckMark(cs, frame, item)
                                is PdfMarkupItem.TextBox -> drawTextBox(doc, cs, frame, item)
                                is PdfMarkupItem.SavedSignatureStamp -> drawSignature(doc, cs, frame, item)
                            }
                        }
                    } finally {
                        cs.restoreGraphicsState()
                    }
                }
            }
            doc.save(outputPdf)
        }
        PdfBoxBackend.open(outputPdf, null).use { verified ->
            check(verified.numberOfPages == pageCount) { "PDF_VERIFY_FAILED" }
        }
        return pageCount
    }

    /**
     * Flattens markup and writes directly back to [uri] via ContentResolver ("wt").
     * Returns true if overwritten in-place, or false if the URI is read-only so callers can fall back to saving a copy.
     */
    fun flattenInPlace(
        context: Context,
        uri: Uri,
        items: List<PdfMarkupItem>,
        checkCancel: () -> Unit = {}
    ): Boolean {
        if (items.isEmpty()) return false
        val id = UUID.randomUUID().toString()
        val tempIn = File(context.cacheDir, "jumlo-inplace-in-$id.pdf")
        val tempOut = File(context.cacheDir, "jumlo-inplace-out-$id.pdf")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { src ->
                tempIn.outputStream().use { dst ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        checkCancel()
                        val n = src.read(buf)
                        if (n < 0) break
                        if (n > 0) dst.write(buf, 0, n)
                    }
                }
            }
            flatten(tempIn, tempOut, items, checkCancel)
            val output = try {
                context.contentResolver.openOutputStream(uri, "wt")
            } catch (_: SecurityException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: return false
            output.use { dst ->
                tempOut.inputStream().use { src -> src.copyTo(dst) }
                dst.flush()
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        } finally {
            tempIn.delete()
            tempOut.delete()
        }
    }

    private fun displayFrame(page: PDPage): PdfDisplayFrame {
        val box = page.cropBox ?: page.mediaBox
        return PdfDisplayCoordinates.frame(
            box.lowerLeftX.toDouble(),
            box.lowerLeftY.toDouble(),
            box.width.toDouble(),
            box.height.toDouble(),
            page.rotation,
            page.userUnit.toDouble()
        )
    }

    private fun displayMatrix(frame: PdfDisplayFrame): Matrix = frame.displayToPage.let { m ->
        Matrix(m.a.toFloat(), m.b.toFloat(), m.c.toFloat(), m.d.toFloat(), m.e.toFloat(), m.f.toFloat())
    }

    private fun toDisplayPoint(frame: PdfDisplayFrame, normX: Float, normY: Float): Pair<Float, Float> {
        val x = normX.coerceIn(0f, 1f) * frame.widthPt.toFloat()
        val y = (1f - normY.coerceIn(0f, 1f)) * frame.heightPt.toFloat()
        return x to y
    }

    private fun drawStroke(
        cs: PDPageContentStream,
        frame: PdfDisplayFrame,
        points: List<NormPoint>,
        colorArgb: Int,
        widthNorm: Float,
        alpha: Float
    ) {
        if (points.isEmpty()) return
        val fw = frame.widthPt.toFloat()
        val fh = frame.heightPt.toFloat()
        cs.saveGraphicsState()
        val gs = PDExtendedGraphicsState().apply {
            setStrokingAlphaConstant(alpha.coerceIn(0.05f, 1f))
            setNonStrokingAlphaConstant(alpha.coerceIn(0.05f, 1f))
        }
        cs.setGraphicsStateParameters(gs)
        cs.setStrokingColor(Color.red(colorArgb), Color.green(colorArgb), Color.blue(colorArgb))
        cs.setLineWidth((widthNorm * minOf(fw, fh)).coerceIn(0.75f, 48f))
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        val first = toDisplayPoint(frame, points.first().x, points.first().y)
        cs.moveTo(first.first, first.second)
        when (points.size) {
            1 -> cs.lineTo(first.first + 0.05f, first.second)
            2 -> {
                val pt = toDisplayPoint(frame, points[1].x, points[1].y)
                cs.lineTo(pt.first, pt.second)
            }
            else -> {
                var prev = first
                for (i in 1 until points.size - 1) {
                    val ctrl = toDisplayPoint(frame, points[i].x, points[i].y)
                    val next = toDisplayPoint(frame, points[i + 1].x, points[i + 1].y)
                    val midX = (ctrl.first + next.first) * 0.5f
                    val midY = (ctrl.second + next.second) * 0.5f
                    val c1x = prev.first + (2f / 3f) * (ctrl.first - prev.first)
                    val c1y = prev.second + (2f / 3f) * (ctrl.second - prev.second)
                    val c2x = midX + (2f / 3f) * (ctrl.first - midX)
                    val c2y = midY + (2f / 3f) * (ctrl.second - midY)
                    cs.curveTo(c1x, c1y, c2x, c2y, midX, midY)
                    prev = midX to midY
                }
                val end = toDisplayPoint(frame, points.last().x, points.last().y)
                cs.lineTo(end.first, end.second)
            }
        }
        cs.stroke()
        cs.restoreGraphicsState()
    }

    private fun drawCheckMark(
        cs: PDPageContentStream,
        frame: PdfDisplayFrame,
        item: PdfMarkupItem.CheckMark
    ) {
        val fw = frame.widthPt.toFloat()
        val fh = frame.heightPt.toFloat()
        val side = (item.sizeNorm * minOf(fw, fh)).coerceIn(8f, 96f)
        val (x, yTop) = toDisplayPoint(frame, item.normX, item.normY)
        val y = (yTop - side).coerceAtLeast(0f)
        cs.saveGraphicsState()
        cs.setStrokingColor(Color.red(item.colorArgb), Color.green(item.colorArgb), Color.blue(item.colorArgb))
        cs.setLineWidth((side * 0.14f).coerceIn(1.5f, 6f))
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        if (item.isCross) {
            cs.moveTo(x, y)
            cs.lineTo(x + side, y + side)
            cs.moveTo(x, y + side)
            cs.lineTo(x + side, y)
        } else {
            cs.moveTo(x, y + side * 0.45f)
            cs.lineTo(x + side * 0.38f, y)
            cs.lineTo(x + side, y + side)
        }
        cs.stroke()
        cs.restoreGraphicsState()
    }

    /** Renders upright ASCII text as vector PDF text, or rasterizes Arabic/RTL/Unicode via 300 DPI StaticLayout. */
    private fun drawTextBox(
        doc: PDDocument,
        cs: PDPageContentStream,
        frame: PdfDisplayFrame,
        item: PdfMarkupItem.TextBox
    ) {
        val text = item.text.trim()
        if (text.isEmpty()) return
        val fw = frame.widthPt.toFloat()
        val fh = frame.heightPt.toFloat()
        val fontSize = item.fontSizePt.coerceIn(8f, 48f)
        val (tx, ty) = toDisplayPoint(frame, item.normX, item.normY)
        if (text.all { it in ' '..'~' || it == '\n' }) {
            val lines = text.lines()
            cs.saveGraphicsState()
            cs.setNonStrokingColor(Color.red(item.colorArgb), Color.green(item.colorArgb), Color.blue(item.colorArgb))
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
            cs.newLineAtOffset(tx, (ty - fontSize).coerceAtLeast(0f))
            lines.forEachIndexed { idx, line ->
                if (idx > 0) cs.newLineAtOffset(0f, -(fontSize * 1.25f))
                cs.showText(line)
            }
            cs.endText()
            cs.restoreGraphicsState()
            return
        }
        val scale = 4.167f // 300 DPI for crisp Arabic/RTL StaticLayout rendering
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.colorArgb
            textSize = fontSize * scale
            typeface = Typeface.DEFAULT
        }
        val maxWidthPx = ((fw * 0.65f) * scale).toInt().coerceIn(120, 2400)
        val measuredWidth = text.lines().maxOfOrNull { ceil(paint.measureText(it)).toInt() } ?: 120
        val widthPx = (measuredWidth + 16).coerceIn(32, maxWidthPx)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .build()
        val heightPx = (layout.height + 12).coerceIn(24, 1600)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.translate(8f, 6f)
            layout.draw(canvas)
            val img = LosslessFactory.createFromImage(doc, bitmap)
            val wPt = (widthPx / scale).coerceAtMost(fw * 0.9f)
            val hPt = (heightPx / scale).coerceAtMost(fh * 0.5f)
            cs.drawImage(img, tx, (ty - hPt).coerceAtLeast(0f), wPt, hPt)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawSignature(
        doc: PDDocument,
        cs: PDPageContentStream,
        frame: PdfDisplayFrame,
        item: PdfMarkupItem.SavedSignatureStamp
    ) {
        val file = File(item.signaturePath)
        if (!file.isFile) return
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        try {
            val fw = frame.widthPt.toFloat()
            val fh = frame.heightPt.toFloat()
            val boxW = (item.normW * fw).coerceAtLeast(1f)
            val boxH = (item.normH * fh).coerceAtLeast(1f)
            val (x, yTop) = toDisplayPoint(frame, item.normX, item.normY)
            val boxY = (yTop - boxH).coerceAtLeast(0f)
            val imgRatio = if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height else 1f
            val boxRatio = boxW / boxH
            val drawW: Float
            val drawH: Float
            if (imgRatio >= boxRatio) {
                drawW = boxW
                drawH = (boxW / imgRatio).coerceAtLeast(1f)
            } else {
                drawH = boxH
                drawW = (boxH * imgRatio).coerceAtLeast(1f)
            }
            val drawX = x + (boxW - drawW) * 0.5f
            val drawY = boxY + (boxH - drawH) * 0.5f
            val img = LosslessFactory.createFromImage(doc, bitmap)
            cs.drawImage(img, drawX, drawY, drawW, drawH)
        } finally {
            bitmap.recycle()
        }
    }
}
