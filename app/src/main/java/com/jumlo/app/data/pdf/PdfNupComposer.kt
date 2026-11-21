package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.min

/** Combines 2 or 4 PDF pages onto each ISO A4 sheet (vector-preserving in color; grayscale ink-saver supported). */
object PdfNupComposer {
    fun compose(
        inputPdf: File,
        outputPdf: File,
        pagesPerSheet: Int,
        grayscale: Boolean,
        maxBitmapBytes: Long,
        checkCancel: () -> Unit = {}
    ): Int {
        require(pagesPerSheet in setOf(2, 4)) { "INVALID_NUP_COUNT" }
        val sheetW = if (pagesPerSheet == 2) 842f else 595f
        val sheetH = if (pagesPerSheet == 2) 595f else 842f
        val cols = 2
        val rows = if (pagesPerSheet == 2) 1 else 2
        val margin = 18f
        val gap = 12f
        val cellW = (sheetW - margin * 2 - gap * (cols - 1)) / cols
        val cellH = (sheetH - margin * 2 - gap * (rows - 1)) / rows
        var outPages = 0

        PdfBoxBackend.open(inputPdf, null).use { src ->
            val total = src.numberOfPages
            require(total > 0) { "EMPTY_PDF" }
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { dst ->
                if (!grayscale) {
                    val layer = LayerUtility(dst)
                    var idx = 0
                    while (idx < total) {
                        checkCancel()
                        val outPage = PDPage(PDRectangle(sheetW, sheetH))
                        dst.addPage(outPage)
                        outPages++
                        PDPageContentStream(dst, outPage).use { cs ->
                            for (slot in 0 until pagesPerSheet) {
                                if (idx >= total) break
                                checkCancel()
                                val srcPage = src.getPage(idx)
                                val box = srcPage.cropBox ?: srcPage.mediaBox
                                val form = layer.importPageAsForm(src, idx)
                                val c = slot % cols
                                val r = slot / cols
                                val ox = margin + c * (cellW + gap)
                                val oy = sheetH - margin - (r + 1) * cellH - r * gap
                                val scale = min(cellW / box.width.coerceAtLeast(1f), cellH / box.height.coerceAtLeast(1f))
                                val dx = ox + (cellW - box.width * scale) / 2f
                                val dy = oy + (cellH - box.height * scale) / 2f
                                cs.saveGraphicsState()
                                cs.transform(Matrix(scale, 0f, 0f, scale, dx, dy))
                                cs.drawForm(form)
                                cs.restoreGraphicsState()
                                idx++
                            }
                        }
                    }
                } else {
                    PdfPageRasterizer(inputPdf, checkCancel).use { raster ->
                        var idx = 0
                        while (idx < total) {
                            checkCancel()
                            val outPage = PDPage(PDRectangle(sheetW, sheetH))
                            dst.addPage(outPage)
                            outPages++
                            PDPageContentStream(dst, outPage).use { cs ->
                                for (slot in 0 until pagesPerSheet) {
                                    if (idx >= total) break
                                    checkCancel()
                                    val raw = raster.render(idx, 1800, minOf(maxBitmapBytes / 2, 24L * 1024 * 1024))
                                    val gray = try { toGrayscale(raw) } finally { raw.recycle() }
                                    try {
                                        val img = JPEGFactory.createFromImage(dst, gray, 0.92f)
                                        val c = slot % cols
                                        val r = slot / cols
                                        val ox = margin + c * (cellW + gap)
                                        val oy = sheetH - margin - (r + 1) * cellH - r * gap
                                        val scale = min(cellW / img.width, cellH / img.height)
                                        val w = img.width * scale
                                        val h = img.height * scale
                                        cs.drawImage(img, ox + (cellW - w) / 2f, oy + (cellH - h) / 2f, w, h)
                                    } finally {
                                        gray.recycle()
                                    }
                                    idx++
                                }
                            }
                        }
                    }
                }
                dst.save(outputPdf)
            }
        }
        PdfBoxBackend.open(outputPdf, null).use { check(it.numberOfPages == outPages && outPages > 0) { "PDF_VERIFY_FAILED" } }
        return outPages
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        try {
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
        } catch (t: Throwable) {
            out.recycle()
            throw t
        }
    }
}
