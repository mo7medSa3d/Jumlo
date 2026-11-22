package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.office.DocumentRenderPolicy
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/** Only one page may be open at a time. Callers serialize access and run outside the UI thread. */
class PdfPageRasterizer(file: File, checkCancellation: () -> Unit = {}) : Closeable {
    private val scratchDir = requireNotNull(file.absoluteFile.parentFile)
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRendererFactory.open(descriptor, scratchDir, checkCancellation)
    val pageCount: Int get() = renderer.pageCount

    fun render(index: Int, maxEdge: Int, bitmapBudget: Long): Bitmap {
        require(index in 0 until renderer.pageCount) { "INVALID_PAGE_INDEX" }
        return renderer.openPage(index).use { page ->
            var currentEdge = maxEdge
            var currentBudget = bitmapBudget
            var lastOom: OutOfMemoryError? = null
            for (attempt in 0 until 4) {
                val (width, height) = DocumentRenderPolicy.dimensions(page.width, page.height, currentEdge, currentBudget)
                var bitmap: Bitmap? = null
                try {
                    val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap = created
                    created.eraseColor(Color.WHITE)
                    page.render(created, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    created.setHasAlpha(false)
                    return created
                } catch (oom: OutOfMemoryError) {
                    bitmap?.let { if (!it.isRecycled) it.recycle() }
                    lastOom = oom
                    currentEdge = (currentEdge * 0.75).roundToInt().coerceAtLeast(64)
                    currentBudget = (currentBudget / 2L).coerceAtLeast(4L * 1024 * 1024)
                    System.gc()
                } catch (error: Throwable) {
                    bitmap?.let { if (!it.isRecycled) it.recycle() }
                    throw error
                }
            }
            throw lastOom ?: OutOfMemoryError("PDF page rasterization exceeded available memory")
        }
    }
    fun pageSize(index: Int): Pair<Int, Int> = renderer.openPage(index).use { it.width to it.height }
    override fun close() { try { renderer.close() } finally { descriptor.close() } }
}
