package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.jumlo.app.core.PdfMarkupItem
import com.jumlo.app.core.PdfMarkupPolicy
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import kotlin.math.ceil

/** Flattens ink, highlights, Arabic-safe text boxes, checkmarks, and saved signatures into a verified PDF copy. */
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
            pageCount = doc.numberOfPages
            require(pageCount > 0) { "EMPTY_PDF" }
            for ((pageIndex, pageItems) in byPage) {
                checkCancel()
                if (pageIndex !in 0 until pageCount) continue
                val page = doc.getPage(pageIndex)
                val box = page.cropBox ?: page.mediaBox
                val rot = page.rotation
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (item in pageItems) {
                        checkCancel()
                        when (item) {
                            is PdfMarkupItem.InkStroke -> drawStroke(cs, box.lowerLeftX, box.lowerLeftY, box.width, box.height, rot, item.points, item.colorArgb, item.widthNorm, 1f)
                            is PdfMarkupItem.HighlightStroke -> drawStroke(cs, box.lowerLeftX, box.lowerLeftY, box.width, box.height, rot, item.points, item.colorArgb, item.widthNorm, item.alpha)
                            is PdfMarkupItem.CheckMark -> drawCheckMark(cs, box.lowerLeftX, box.lowerLeftY, box.width, box.height, rot, item)
                            is PdfMarkupItem.TextBox -> drawTextBox(doc, cs, box.lowerLeftX, box.lowerLeftY, box.width, box.height, rot, item)
                            is PdfMarkupItem.SavedSignatureStamp -> drawSignature(doc, cs, box.lowerLeftX, box.lowerLeftY, box.width, box.height, rot, item)
                        }
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

    private fun drawStroke(
        cs: PDPageContentStream,
        bx: Float, by: Float, bw: Float, bh: Float, rot: Int,
        points: List<com.jumlo.app.core.NormPoint>,
        colorArgb: Int, widthNorm: Float, alpha: Float
    ) {
        if (points.size < 2) return
        cs.saveGraphicsState()
        val gs = PDExtendedGraphicsState().apply {
            setStrokingAlphaConstant(alpha.coerceIn(0.05f, 1f))
            setNonStrokingAlphaConstant(alpha.coerceIn(0.05f, 1f))
        }
        cs.setGraphicsStateParameters(gs)
        cs.setStrokingColor(Color.red(colorArgb), Color.green(colorArgb), Color.blue(colorArgb))
        cs.setLineWidth((widthNorm * minOf(bw, bh)).coerceIn(0.75f, 48f))
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        val first = PdfMarkupPolicy.toPdfPoint(bx, by, bw, bh, rot, points.first().x, points.first().y)
        cs.moveTo(first.first, first.second)
        if (points.size == 2) {
            val pt = PdfMarkupPolicy.toPdfPoint(bx, by, bw, bh, rot, points[1].x, points[1].y)
            cs.lineTo(pt.first, pt.second)
        } else {
            var prev = first
            for (i in 1 until points.size - 1) {
                val ctrl = PdfMarkupPolicy.toPdfPoint(bx, by, bw, bh, rot, points[i].x, points[i].y)
                val next = PdfMarkupPolicy.toPdfPoint(bx, by, bw, bh, rot, points[i + 1].x, points[i + 1].y)
                val midX = (ctrl.first + next.first) * 0.5f
                val midY = (ctrl.second + next.second) * 0.5f
                val c1x = prev.first + (2f / 3f) * (ctrl.first - prev.first)
                val c1y = prev.second + (2f / 3f) * (ctrl.second - prev.second)
                val c2x = midX + (2f / 3f) * (ctrl.first - midX)
                val c2y = midY + (2f / 3f) * (ctrl.second - midY)
                cs.curveTo(c1x, c1y, c2x, c2y, midX, midY)
                prev = midX to midY
            }
            val end = PdfMarkupPolicy.toPdfPoint(bx, by, bw, bh, rot, points.last().x, points.last().y)
            cs.lineTo(end.first, end.second)
        }
        cs.stroke()
        cs.restoreGraphicsState()
    }

    private fun drawCheckMark(
        cs: PDPageContentStream,
        bx: Float, by: Float, bw: Float, bh: Float, rot: Int,
        item: PdfMarkupItem.CheckMark
    ) {
        val rect = PdfMarkupPolicy.toPdfRect(bx, by, bw, bh, rot, item.normX, item.normY, item.sizeNorm, item.sizeNorm)
        cs.saveGraphicsState()
        cs.setStrokingColor(Color.red(item.colorArgb), Color.green(item.colorArgb), Color.blue(item.colorArgb))
        cs.setLineWidth((rect.width * 0.14f).coerceIn(1.5f, 5f))
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        if (item.isCross) {
            cs.moveTo(rect.x, rect.y)
            cs.lineTo(rect.x + rect.width, rect.y + rect.height)
            cs.moveTo(rect.x, rect.y + rect.height)
            cs.lineTo(rect.x + rect.width, rect.y)
        } else {
            cs.moveTo(rect.x, rect.y + rect.height * 0.45f)
            cs.lineTo(rect.x + rect.width * 0.38f, rect.y)
            cs.lineTo(rect.x + rect.width, rect.y + rect.height)
        }
        cs.stroke()
        cs.restoreGraphicsState()
    }

    /** Rasterizes text via Android StaticLayout at 300 DPI so Arabic RTL & multilingual shaping stay 100% crisp. */
    private fun drawTextBox(
        doc: com.tom_roush.pdfbox.pdmodel.PDDocument,
        cs: PDPageContentStream,
        bx: Float, by: Float, bw: Float, bh: Float, rot: Int,
        item: PdfMarkupItem.TextBox
    ) {
        val text = item.text.trim()
        if (text.isEmpty()) return
        if (rot == 0 && item.text.all { it in ' '..'~' || it == '\n' }) {
            val fontSize = item.fontSizePt.coerceIn(8f, 48f)
            val lines = item.text.lines()
            val (tx, ty) = PdfMarkupPolicy.toPdfPoint(bx, by, bw, bh, rot, item.normX, item.normY)
            cs.saveGraphicsState()
            cs.setNonStrokingColor((item.colorArgb shr 16) and 0xFF, (item.colorArgb shr 8) and 0xFF, item.colorArgb and 0xFF)
            cs.beginText()
            cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, fontSize)
            cs.newLineAtOffset(tx, ty - fontSize)
            lines.forEachIndexed { idx, line ->
                if (idx > 0) cs.newLineAtOffset(0f, -(fontSize * 1.25f))
                cs.showText(line)
            }
            cs.endText()
            cs.restoreGraphicsState()
            return
        }
        val scale = 4.167f // True 300 DPI for crisp Arabic/RTL StaticLayout rendering
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.colorArgb
            textSize = item.fontSizePt.coerceIn(8f, 48f) * scale
            typeface = Typeface.DEFAULT
        }
        val maxWidthPx = ((bw * 0.65f) * scale).toInt().coerceIn(120, 2400)
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
            val wPt = widthPx / scale
            val hPt = heightPx / scale
            val rect = PdfMarkupPolicy.toPdfRect(bx, by, bw, bh, rot, item.normX, item.normY, (wPt / bw).coerceAtMost(0.9f), (hPt / bh).coerceAtMost(0.5f))
            cs.drawImage(img, rect.x, rect.y, rect.width, rect.height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawSignature(
        doc: com.tom_roush.pdfbox.pdmodel.PDDocument,
        cs: PDPageContentStream,
        bx: Float, by: Float, bw: Float, bh: Float, rot: Int,
        item: PdfMarkupItem.SavedSignatureStamp
    ) {
        val file = File(item.signaturePath)
        if (!file.isFile) return
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        try {
            val img = LosslessFactory.createFromImage(doc, bitmap)
            val rect = PdfMarkupPolicy.toPdfRect(bx, by, bw, bh, rot, item.normX, item.normY, item.normW, item.normH)
            cs.drawImage(img, rect.x, rect.y, rect.width, rect.height)
        } finally {
            bitmap.recycle()
        }
    }
}
