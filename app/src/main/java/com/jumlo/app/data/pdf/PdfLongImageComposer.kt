package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.jumlo.app.core.ImageWorkPolicy
import com.jumlo.app.core.PageSelection
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Stitches selected PDF pages vertically into full-quality long image(s), splitting into Part 1..N instead of silently downscaling. */
object PdfLongImageComposer {
    private const val MAX_STITCH_PAGES = 500
    // JPEG SOF0 has a hard 16-bit 65,535 px height limit; PNG supports taller strips bounded by heap.
    private const val MAX_JPEG_CANVAS_HEIGHT = 65_500
    private const val MAX_PNG_CANVAS_HEIGHT = 131_000
    private const val MAX_OOM_RETRIES = 5
    private const val PAGE_GAP_PX = 0

    private data class SinglePageImage(val name: COSName, val width: Int, val height: Int, val rotation: Int)

    /** Backward-compatible single-file entry point; renders all selected pages into [outputFile]. */
    fun compose(
        inputPdf: File,
        outputFile: File,
        pagesSpec: String,
        asJpeg: Boolean,
        quality: Int,
        dpi: Int = 300,
        grayscale: Boolean = false,
        maxBitmapBytes: Long,
        checkCancel: () -> Unit = {},
        keepOriginalImageQuality: Boolean = true
    ): Int {
        val clampedDpi = dpi.coerceIn(72, 600)
        val clampedQuality = quality.coerceIn(1, 100)
        val effectiveBudget = ImageWorkPolicy.effectiveBudget(maxBitmapBytes)
        val maxCanvasBytes = maxOf(64L * 1024 * 1024, (effectiveBudget * 75L) / 100L)
        val maxCanvasHeight = if (asJpeg) MAX_JPEG_CANVAS_HEIGHT else MAX_PNG_CANVAS_HEIGHT

        PdfBoxBackend.open(inputPdf, null).use { doc ->
            val selected = selectPages(doc, pagesSpec)
            val singleImages = inspectSingleImages(doc, selected, keepOriginalImageQuality, checkCancel)
            PdfPageRasterizer(inputPdf, checkCancel).use { raster ->
                val dims = measurePages(selected, singleImages, raster, clampedDpi, checkCancel)
                renderChunk(
                    doc = doc,
                    raster = raster,
                    selected = selected,
                    singleImages = singleImages,
                    dims = dims,
                    chunkIndices = selected.indices.toList(),
                    outputFile = outputFile,
                    asJpeg = asJpeg,
                    clampedQuality = clampedQuality,
                    grayscale = grayscale,
                    effectiveBudget = effectiveBudget,
                    maxCanvasBytes = maxCanvasBytes,
                    maxCanvasHeight = maxCanvasHeight,
                    checkCancel = checkCancel
                )
                return selected.size
            }
        }
    }

    private fun selectPages(doc: PDDocument, pagesSpec: String): List<Int> {
        require(!doc.isEncrypted) { "PDF_ENCRYPTED" }
        val pageCount = doc.numberOfPages
        require(pageCount > 0) { "EMPTY_PDF" }
        val selected = if (pagesSpec.isBlank() || pagesSpec.equals("all", true)) {
            (1..minOf(pageCount, MAX_STITCH_PAGES)).toList()
        } else {
            PageSelection.parse(pagesSpec, pageCount).take(MAX_STITCH_PAGES)
        }
        require(selected.isNotEmpty()) { "NO_PAGES" }
        return selected
    }

    private fun inspectSingleImages(
        doc: PDDocument,
        selected: List<Int>,
        keepOriginalImageQuality: Boolean,
        checkCancel: () -> Unit
    ): List<SinglePageImage?> = selected.map { p ->
        checkCancel()
        if (keepOriginalImageQuality) findSinglePageImage(doc.getPage(p - 1)) else null
    }

    private fun measurePages(
        selected: List<Int>,
        singleImages: List<SinglePageImage?>,
        raster: PdfPageRasterizer,
        clampedDpi: Int,
        checkCancel: () -> Unit
    ): List<Pair<Int, Int>> = selected.mapIndexed { idx, p ->
        checkCancel()
        val single = singleImages[idx]
        if (single != null) {
            single.width to single.height
        } else {
            val (wPt, hPt) = raster.pageSize(p - 1)
            val targetW = ((wPt.toDouble() * clampedDpi) / 72.0).roundToInt().coerceAtLeast(1)
            val targetH = ((hPt.toDouble() * clampedDpi) / 72.0).roundToInt().coerceAtLeast(1)
            targetW to targetH
        }
    }

    private fun renderChunk(
        doc: PDDocument,
        raster: PdfPageRasterizer,
        selected: List<Int>,
        singleImages: List<SinglePageImage?>,
        dims: List<Pair<Int, Int>>,
        chunkIndices: List<Int>,
        outputFile: File,
        asJpeg: Boolean,
        clampedQuality: Int,
        grayscale: Boolean,
        effectiveBudget: Long,
        maxCanvasBytes: Long,
        maxCanvasHeight: Int,
        checkCancel: () -> Unit
    ): Pair<Int, Int> {
        val rawWidth = chunkIndices.maxOf { dims[it].first }.coerceAtLeast(1).toLong()
        val rawHeight = chunkIndices.sumOf { dims[it].second.toLong() } +
            PAGE_GAP_PX.toLong() * (chunkIndices.size - 1).coerceAtLeast(0)
        // Normal partitioned chunks always stay at scale == 1.0; scaling only triggers if a single page alone exceeds hardware/format limits.
        var scale = if (rawHeight > maxCanvasHeight) maxCanvasHeight.toDouble() / rawHeight.toDouble() else 1.0
        if (rawWidth * scale > ImageWorkPolicy.MAX_EDGE_PX) {
            scale = min(scale, ImageWorkPolicy.MAX_EDGE_PX.toDouble() / rawWidth.toDouble())
        }
        val projectedBytes = (rawWidth * scale).toLong().coerceAtLeast(1L) *
            (rawHeight * scale).toLong().coerceAtLeast(1L) * 4L
        if (projectedBytes > maxCanvasBytes) {
            scale *= sqrt(maxCanvasBytes.toDouble() / projectedBytes.toDouble())
        }
        scale = min(scale, 1.0)

        var lastOom: OutOfMemoryError? = null
        for (attempt in 0 until MAX_OOM_RETRIES) {
            checkCancel()
            val attemptScale = scale.toFloat()
            val finalW = (rawWidth * scale).toInt().coerceAtLeast(1)
            val finalH = (rawHeight * scale).toInt().coerceAtLeast(1)
            var stitched: Bitmap? = null
            try {
                val canvasBmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                stitched = canvasBmp
                val canvas = Canvas(canvasBmp)
                canvas.drawColor(Color.WHITE)
                canvasBmp.setHasAlpha(false)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    if (grayscale) {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    }
                }
                val perPageBudget = maxOf(16L * 1024 * 1024, effectiveBudget - canvasBmp.allocationByteCount.toLong())
                var y = 0f
                for (idx in chunkIndices) {
                    checkCancel()
                    val p = selected[idx]
                    val (tw, th) = dims[idx]
                    val drawW = (tw * attemptScale).coerceAtLeast(1f)
                    val drawH = (th * attemptScale).coerceAtLeast(1f)
                    val renderEdge = maxOf(drawW, drawH).roundToInt().coerceAtLeast(64)
                    val single = singleImages[idx]
                    val nativeCopies = if (single != null && single.rotation != 0) 2L else 1L
                    val canUseNative = single != null && attemptScale >= 0.5f &&
                        single.width.toLong() * single.height.toLong() * 4L * nativeCopies <= perPageBudget
                    val pageBmp = if (canUseNative) {
                        extractSinglePageBitmap(doc.getPage(p - 1), single!!)
                            ?: raster.render(p - 1, renderEdge, perPageBudget)
                    } else {
                        raster.render(p - 1, renderEdge, perPageBudget)
                    }
                    try {
                        val dst = android.graphics.RectF((finalW - drawW) / 2f, y, (finalW + drawW) / 2f, y + drawH)
                        canvas.drawBitmap(pageBmp, null, dst, paint)
                    } finally {
                        if (!pageBmp.isRecycled) pageBmp.recycle()
                    }
                    y += drawH + PAGE_GAP_PX * attemptScale
                }
                FileOutputStream(outputFile).use { out ->
                    val fmt = if (asJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    require(canvasBmp.compress(fmt, clampedQuality, out)) { "IMAGE_ENCODE_FAILED" }
                }
                return finalW to finalH
            } catch (oom: OutOfMemoryError) {
                lastOom = oom
                outputFile.delete()
                scale *= 0.75
                System.gc()
            } finally {
                stitched?.let { if (!it.isRecycled) it.recycle() }
            }
        }
        throw lastOom ?: OutOfMemoryError("Long image composition exceeded available memory")
    }

    private fun findSinglePageImage(page: PDPage): SinglePageImage? {
        if (!page.annotations.isNullOrEmpty()) return null
        val resources = page.resources ?: return null
        if (resources.fontNames?.iterator()?.hasNext() == true) return null
        val xNames = resources.xObjectNames?.toList().orEmpty()
        if (xNames.size != 1) return null
        val name = xNames.single()
        val img = runCatching { resources.getXObject(name) }.getOrNull() as? PDImageXObject ?: return null
        if (img.isStencil || img.width <= 0 || img.height <= 0) return null
        val rot = ((page.rotation % 360) + 360) % 360
        val effW = if (rot == 90 || rot == 270) img.height else img.width
        val effH = if (rot == 90 || rot == 270) img.width else img.height
        return SinglePageImage(name, effW, effH, rot)
    }

    private fun extractSinglePageBitmap(page: PDPage, info: SinglePageImage): Bitmap? = runCatching {
        val img = page.resources?.getXObject(info.name) as? PDImageXObject ?: return null
        val raw = img.image ?: return null
        if (info.rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(info.rotation.toFloat()) }
        val rotated = try {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } finally {
            if (!raw.isRecycled) raw.recycle()
        }
        rotated
    }.getOrNull()
}
