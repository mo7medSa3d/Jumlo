package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.jumlo.app.core.PageSelection
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Stitches selected PDF pages vertically into one crisp, share-ready long image (100% on-device, streaming 1 page at a time). */
object PdfLongImageComposer {
    private const val MAX_STITCH_PAGES = 20
    private const val MAX_CANVAS_HEIGHT = 18_000

    fun compose(
        inputPdf: File,
        outputFile: File,
        pagesSpec: String,
        asJpeg: Boolean,
        quality: Int,
        dpi: Int = 144,
        grayscale: Boolean = false,
        maxBitmapBytes: Long,
        checkCancel: () -> Unit = {}
    ): Int {
        val pageCount = PdfBoxBackend.open(inputPdf, null).use { doc ->
            require(!doc.isEncrypted) { "PDF_ENCRYPTED" }
            doc.numberOfPages
        }
        require(pageCount > 0) { "EMPTY_PDF" }
        val selected = if (pagesSpec.isBlank() || pagesSpec.equals("all", true)) {
            (1..minOf(pageCount, MAX_STITCH_PAGES)).toList()
        } else {
            PageSelection.parse(pagesSpec, pageCount).take(MAX_STITCH_PAGES)
        }
        require(selected.isNotEmpty()) { "NO_PAGES" }

        val clampedDpi = dpi.coerceIn(36, 600)
        val clampedQuality = quality.coerceIn(1, 100)
        val gap = 12
        val maxCanvasBytes = maxOf(8L * 1024 * 1024, maxBitmapBytes / 2)

        // Two-pass streaming: measure target dimensions first, allocate one canvas, then render+draw+recycle 1 page at a time.
        PdfPageRasterizer(inputPdf, checkCancel).use { raster ->
            val dims = selected.map { p ->
                checkCancel()
                val (wPt, hPt) = raster.pageSize(p - 1)
                val targetW = ((wPt.toDouble() * clampedDpi) / 72.0).roundToInt().coerceAtLeast(1)
                val targetH = ((hPt.toDouble() * clampedDpi) / 72.0).roundToInt().coerceAtLeast(1)
                targetW to targetH
            }
            val rawWidth = dims.maxOf { it.first }.coerceAtLeast(1)
            val rawHeight = dims.sumOf { it.second } + gap * (dims.size - 1).coerceAtLeast(0)
            var scale = if (rawHeight > MAX_CANVAS_HEIGHT) MAX_CANVAS_HEIGHT.toFloat() / rawHeight else 1f
            val projectedBytes = (rawWidth * scale).toLong() * (rawHeight * scale).toLong() * 4L
            if (projectedBytes > maxCanvasBytes) {
                scale *= sqrt(maxCanvasBytes.toDouble() / projectedBytes.toDouble()).toFloat()
            }
            scale = min(scale, 1f)
            val finalW = (rawWidth * scale).toInt().coerceAtLeast(1)
            val finalH = (rawHeight * scale).toInt().coerceAtLeast(1)
            val stitched = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(stitched)
                canvas.drawColor(Color.WHITE)
                stitched.setHasAlpha(false)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    if (grayscale) {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    }
                }
                val perPageBudget = maxOf(4L * 1024 * 1024, maxBitmapBytes - stitched.allocationByteCount.toLong())
                var y = 0f
                selected.forEachIndexed { idx, p ->
                    checkCancel()
                    val (tw, th) = dims[idx]
                    val drawW = (tw * scale).coerceAtLeast(1f)
                    val drawH = (th * scale).coerceAtLeast(1f)
                    val renderEdge = maxOf(drawW, drawH).roundToInt().coerceAtLeast(64)
                    val pageBmp = raster.render(p - 1, renderEdge, perPageBudget)
                    try {
                        val dst = android.graphics.RectF((finalW - drawW) / 2f, y, (finalW + drawW) / 2f, y + drawH)
                        canvas.drawBitmap(pageBmp, null, dst, paint)
                    } finally {
                        pageBmp.recycle()
                    }
                    y += drawH + gap * scale
                }
                FileOutputStream(outputFile).use { out ->
                    val fmt = if (asJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    require(stitched.compress(fmt, clampedQuality, out)) { "IMAGE_ENCODE_FAILED" }
                }
            } finally {
                stitched.recycle()
            }
        }
        return selected.size
    }
}
