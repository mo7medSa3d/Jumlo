package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.jumlo.app.core.ImageWorkPolicy
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
import kotlin.math.roundToInt

/** Combines 2 or 4 PDF pages onto each ISO A4 sheet (vector-preserving in color; crisp 300 DPI grayscale ink-saver supported). */
object PdfNupComposer {
    private const val MARGIN_PT = 14f
    private const val GAP_PT = 10f
    private const val GRAYSCALE_MAX_EDGE = 3200

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
        val margin = MARGIN_PT
        val gap = GAP_PT
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
                                val rot = ((srcPage.rotation % 360) + 360) % 360
                                val effW = (if (rot == 90 || rot == 270) box.height else box.width).coerceAtLeast(1f)
                                val effH = (if (rot == 90 || rot == 270) box.width else box.height).coerceAtLeast(1f)
                                val form = layer.importPageAsForm(src, idx)
                                val c = slot % cols
                                val r = slot / cols
                                val ox = margin + c * (cellW + gap)
                                val oy = sheetH - margin - (r + 1) * cellH - r * gap
                                val scale = min(cellW / effW, cellH / effH)
                                val dx = ox + (cellW - effW * scale) / 2f
                                val dy = oy + (cellH - effH * scale) / 2f
                                cs.saveGraphicsState()
                                cs.addRect(ox, oy, cellW, cellH)
                                cs.clip()
                                val m = Matrix()
                                m.concatenate(Matrix.getTranslateInstance(dx, dy))
                                m.concatenate(Matrix.getScaleInstance(scale, scale))
                                when (rot) {
                                    90 -> { m.concatenate(Matrix.getTranslateInstance(0f, box.width)); m.concatenate(Matrix.getRotateInstance(-Math.PI / 2.0, 0f, 0f)) }
                                    180 -> { m.concatenate(Matrix.getTranslateInstance(box.width, box.height)); m.concatenate(Matrix.getRotateInstance(Math.PI, 0f, 0f)) }
                                    270 -> { m.concatenate(Matrix.getTranslateInstance(box.height, 0f)); m.concatenate(Matrix.getRotateInstance(Math.PI / 2.0, 0f, 0f)) }
                                }
                                m.concatenate(Matrix.getTranslateInstance(-box.lowerLeftX, -box.lowerLeftY))
                                cs.transform(m)
                                cs.drawForm(form)
                                cs.restoreGraphicsState()
                                idx++
                            }
                        }
                    }
                } else {
                    val effectiveBudget = ImageWorkPolicy.effectiveBudget(maxBitmapBytes)
                    val renderBudget = (effectiveBudget / 2).coerceAtLeast(4L)
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
                                    val img = renderGrayscalePageWithRetry(dst, raster, idx, GRAYSCALE_MAX_EDGE, renderBudget)
                                    val c = slot % cols
                                    val r = slot / cols
                                    val ox = margin + c * (cellW + gap)
                                    val oy = sheetH - margin - (r + 1) * cellH - r * gap
                                    val scale = min(cellW / img.width, cellH / img.height)
                                    val w = img.width * scale
                                    val h = img.height * scale
                                    cs.drawImage(img, ox + (cellW - w) / 2f, oy + (cellH - h) / 2f, w, h)
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

    private fun renderGrayscalePageWithRetry(
        dst: PDDocument,
        raster: PdfPageRasterizer,
        pageIndex: Int,
        maxEdge: Int,
        budget: Long
    ): com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject {
        var edge = maxEdge
        var currentBudget = budget
        var lastOom: OutOfMemoryError? = null
        for (attempt in 0 until 4) {
            var raw: Bitmap? = null
            var gray: Bitmap? = null
            try {
                raw = raster.render(pageIndex, edge, currentBudget)
                gray = toGrayscale(raw)
                if (!raw.isRecycled) raw.recycle()
                raw = null
                return JPEGFactory.createFromImage(dst, gray, 0.92f)
            } catch (oom: OutOfMemoryError) {
                lastOom = oom
                edge = (edge * 0.75).roundToInt().coerceAtLeast(256)
                currentBudget = (currentBudget / 2L).coerceAtLeast(4L * 1024 * 1024)
                System.gc()
            } finally {
                raw?.let { if (!it.isRecycled) it.recycle() }
                gray?.let { if (!it.isRecycled) it.recycle() }
            }
        }
        throw lastOom ?: OutOfMemoryError("Grayscale N-up render exceeded available memory")
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            canvas.drawBitmap(src, 0f, 0f, paint)
            out.setHasAlpha(false)
            return out
        } catch (t: Throwable) {
            if (!out.isRecycled) out.recycle()
            throw t
        }
    }
}
