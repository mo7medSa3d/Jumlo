package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.office.DocumentRenderPolicy
import java.io.Closeable
import java.io.File

/** Only one page may be open at a time. Callers serialize access and run outside the UI thread. */
class PdfPageRasterizer(file: File, checkCancellation: () -> Unit = {}) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRendererFactory.open(descriptor, requireNotNull(file.absoluteFile.parentFile), checkCancellation)
    val pageCount: Int get() = renderer.pageCount

    fun render(index: Int, maxEdge: Int, bitmapBudget: Long): Bitmap {
        require(index in 0 until renderer.pageCount) { "INVALID_PAGE_INDEX" }
        return renderer.openPage(index).use { page ->
            val (width, height) = DocumentRenderPolicy.dimensions(page.width, page.height, maxEdge, bitmapBudget)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.setHasAlpha(false)
                bitmap
            } catch (error: Throwable) { bitmap.recycle(); throw error }
        }
    }
    fun pageSize(index: Int): Pair<Int, Int> = renderer.openPage(index).use { it.width to it.height }
    override fun close() { try { renderer.close() } finally { descriptor.close() } }
}
