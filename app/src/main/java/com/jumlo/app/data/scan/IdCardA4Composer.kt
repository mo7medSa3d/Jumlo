package com.jumlo.app.data.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import kotlin.math.min

/** Places front and back of an ID card or passport onto one exact ISO A4 (595x842 pt) PDF page without watermarks. */
object IdCardA4Composer {
    const val A4_WIDTH_PT = 595f
    const val A4_HEIGHT_PT = 842f
    private const val CARD_MAX_W = 242.65f
    private const val CARD_MAX_H = 153.01f

    private fun ensureLandscape(src: Bitmap): Bitmap {
        require(!src.isRecycled) { "BITMAP_RECYCLED" }
        if (src.width >= src.height) return src
        val m = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun encodeCard(doc: PDDocument, source: Bitmap, filter: ScanFilter): PDImageXObject {
        require(!source.isRecycled) { "BITMAP_RECYCLED" }
        val land = ensureLandscape(source)
        var clean: Bitmap? = null
        try {
            clean = DocumentFilterEngine.apply(land, filter)
            return JPEGFactory.createFromImage(doc, clean, 0.95f)
        } finally {
            if (clean != null && clean !== land) clean.recycle()
            if (land !== source) land.recycle()
        }
    }

    fun compose(front: Bitmap, back: Bitmap, filter: ScanFilter, outputPdf: File, checkCancel: () -> Unit = {}) {
        checkCancel()
        require(!front.isRecycled && !back.isRecycled) { "BITMAP_RECYCLED" }
        try {
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val page = PDPage(PDRectangle(A4_WIDTH_PT, A4_HEIGHT_PT))
                doc.addPage(page)
                // Encode one side at a time and recycle intermediates immediately to avoid peak 6-bitmap OOM.
                val fImg = encodeCard(doc, front, filter)
                checkCancel()
                val bImg = encodeCard(doc, back, filter)
                checkCancel()
                PDPageContentStream(doc, page).use { cs ->
                    fun drawCentered(img: PDImageXObject, centerY: Float) {
                        val scale = min(CARD_MAX_W / img.width, CARD_MAX_H / img.height)
                        val w = img.width * scale
                        val h = img.height * scale
                        cs.drawImage(img, (A4_WIDTH_PT - w) / 2f, centerY - h / 2f, w, h)
                    }
                    drawCentered(fImg, A4_HEIGHT_PT * 0.72f)
                    drawCentered(bImg, A4_HEIGHT_PT * 0.28f)
                }
                checkCancel()
                doc.save(outputPdf)
            }
            PdfBoxBackend.open(outputPdf, null).use { check(it.numberOfPages == 1) { "PDF_VERIFY_FAILED" } }
        } catch (oom: OutOfMemoryError) {
            outputPdf.delete()
            throw IllegalStateException("LOW_MEMORY", oom)
        } catch (t: Throwable) {
            outputPdf.delete()
            throw t
        }
    }
}
