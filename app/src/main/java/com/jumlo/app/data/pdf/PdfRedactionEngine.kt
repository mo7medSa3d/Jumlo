package com.jumlo.app.data.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.jumlo.app.data.processing.PdfCropOverride
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File

/** Permanently burns opaque redaction boxes into rasterized target pages so underlying text/objects are destroyed,
 *  strips PDF metadata, and keeps unredacted pages lossless. */
object PdfRedactionEngine {
    fun burnRedactions(
        inputPdf: File,
        outputPdf: File,
        redactions: List<PdfCropOverride>,
        fillBlack: Boolean,
        maxBitmapBytes: Long,
        checkCancel: () -> Unit = {}
    ): Int {
        require(redactions.isNotEmpty()) { "REDACT_REGION_REQUIRED" }
        val byPage = redactions.groupBy { it.page - 1 }
        var totalPages = 0
        PdfBoxBackend.open(inputPdf, null).use { src ->
            require(!src.isEncrypted) { "PDF_ENCRYPTED" }
            totalPages = src.numberOfPages
            require(totalPages > 0) { "EMPTY_PDF" }
            redactions.forEach { r ->
                require(r.page in 1..totalPages) { "INVALID_PAGE_INDEX" }
                require(r.left < r.right && r.top < r.bottom) { "INVALID_CROP" }
            }
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { dst ->
                // Strip both documentInformation and XMP /Metadata streams for complete privacy.
                dst.documentInformation = PDDocumentInformation()
                dst.documentCatalog.metadata = null
                var rasterizer: PdfPageRasterizer? = null
                try {
                    for (pageIndex in 0 until totalPages) {
                        checkCancel()
                        val boxes = byPage[pageIndex]
                        if (boxes.isNullOrEmpty()) {
                            dst.importPage(src.getPage(pageIndex))
                        } else {
                            val raster = rasterizer ?: PdfPageRasterizer(inputPdf, checkCancel).also { rasterizer = it }
                            val size = raster.pageSize(pageIndex)
                            val bmp = raster.render(pageIndex, 2000, minOf(maxBitmapBytes / 2, 24L * 1024 * 1024))
                            try {
                                val mutable = if (bmp.isMutable) bmp else bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: throw OutOfMemoryError("LOW_MEMORY")
                                try {
                                    val canvas = Canvas(mutable)
                                    val paint = Paint().apply {
                                        style = Paint.Style.FILL
                                        color = if (fillBlack) Color.BLACK else Color.WHITE
                                    }
                                    boxes.forEach { r ->
                                        canvas.drawRect(
                                            (r.left.toFloat().coerceIn(0f, 1f)) * mutable.width,
                                            (r.top.toFloat().coerceIn(0f, 1f)) * mutable.height,
                                            (r.right.toFloat().coerceIn(0f, 1f)) * mutable.width,
                                            (r.bottom.toFloat().coerceIn(0f, 1f)) * mutable.height,
                                            paint
                                        )
                                    }
                                    val outPage = PDPage(PDRectangle(size.first.toFloat(), size.second.toFloat()))
                                    dst.addPage(outPage)
                                    val img = JPEGFactory.createFromImage(dst, mutable, 0.92f)
                                    PDPageContentStream(dst, outPage).use { cs ->
                                        cs.drawImage(img, 0f, 0f, size.first.toFloat(), size.second.toFloat())
                                    }
                                } finally {
                                    if (mutable !== bmp) mutable.recycle()
                                }
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                } finally {
                    rasterizer?.close()
                }
                dst.save(outputPdf)
            }
        }
        PdfBoxBackend.open(outputPdf, null).use { check(it.numberOfPages == totalPages) { "PDF_VERIFY_FAILED" } }
        return totalPages
    }
}
