package com.jumlo.app.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.core.office.DocumentRenderPolicy
import com.jumlo.app.data.jobs.OperationFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException

/** A single serialized PdfRenderer with a byte-bounded cache. Displayed bitmaps are GC-owned. */
class PdfReaderSession private constructor(
    private val renderer: PdfRenderer,
    private val copiedInput: File?,
    val pageCount: Int,
    val firstPageRatio: Float
) {
    data class Frame(val bitmap: Bitmap, val width: Int, val height: Int)
    private data class Key(val page: Int, val maxEdge: Int)
    private val lock = Mutex()
    private val cache = LinkedHashMap<Key, Frame>(8, .75f, true)
    private var bytes = 0L
    private var closed = false
    private val cacheLimit = minOf(Runtime.getRuntime().maxMemory() / 8, 32L * 1024 * 1024).coerceAtLeast(4L * 1024 * 1024)

    suspend fun render(page: Int, maxEdge: Int): Frame = withContext(Dispatchers.IO) {
        lock.withLock {
            currentCoroutineContext().ensureActive()
            check(!closed) { "PREVIEW_UNAVAILABLE" }
            require(page in 0 until pageCount) { "PAGE_OUT_OF_RANGE" }
            val key = Key(page, maxEdge.coerceIn(96, 2400))
            cache[key]?.let { return@withLock it }
            val frame = renderer.openPage(page).use { input ->
                val (width, height) = DocumentRenderPolicy.dimensions(input.width, input.height, key.maxEdge, minOf(cacheLimit / 2, 16L * 1024 * 1024))
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    input.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.setHasAlpha(false)
                    Frame(bitmap, input.width, input.height)
                } catch (error: Throwable) { bitmap.recycle(); throw error }
            }
            cache[key] = frame
            bytes += frame.bitmap.allocationByteCount
            while (bytes > cacheLimit && cache.size > 1) {
                val iterator = cache.entries.iterator()
                val old = iterator.next().value
                bytes -= old.bitmap.allocationByteCount
                iterator.remove()
                // Compose/RenderThread may still display an evicted page. Never recycle it here.
            }
            frame
        }
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        lock.withLock {
            if (!closed) {
                closed = true
                cache.clear()
                bytes = 0
                try { renderer.close() } finally { copiedInput?.delete() }
            }
        }
    }

    companion object {
        /** Keep the initiating failure while still attempting every owned-resource cleanup. */
        internal fun cleanupFailure(failure: Throwable, privateCopy: File?, closeRenderer: () -> Unit) {
            try { closeRenderer() }
            catch (closeFailure: Throwable) { if (closeFailure !== failure) failure.addSuppressed(closeFailure) }
            try { privateCopy?.delete() }
            catch (deleteFailure: Throwable) { if (deleteFailure !== failure) failure.addSuppressed(deleteFailure) }
        }

        suspend fun open(context: Context, location: String): PdfReaderSession {
            var pending: PdfReaderSession? = null
            try {
                val opened = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    val uri = Uri.parse(location)
                    require(uri.scheme in setOf("content", "file")) { "PREVIEW_UNAVAILABLE" }
                    var copied: File? = null
                    var renderer: PdfRenderer? = null
                    try {
                        // SAF providers may return a pipe. A renderer requires a seekable descriptor.
                        try {
                            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("ACCESS_LOST")
                            renderer = PdfRendererFactory.open(descriptor, context.cacheDir) { coroutine.ensureActive() }
                        } catch (failure: Exception) {
                            if (failure is OperationFailure || (failure !is IOException && failure !is IllegalArgumentException)) throw failure
                            // Android throws IllegalArgumentException for non-seekable provider pipes.
                            copied = File.createTempFile("pdf-reader-", ".pdf", context.cacheDir)
                            val coroutine = currentCoroutineContext()
                            (context.contentResolver.openInputStream(uri) ?: error("ACCESS_LOST")).use { input ->
                                FileOutputStream(copied).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        coroutine.ensureActive()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        require(context.cacheDir.usableSpace >= ResourceBudgetPolicy.minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
                                        if (n > 0) output.write(buffer, 0, n)
                                    }
                                }
                            }
                            val descriptor = ParcelFileDescriptor.open(copied, ParcelFileDescriptor.MODE_READ_ONLY)
                            renderer = PdfRendererFactory.open(descriptor, context.cacheDir) { coroutine.ensureActive() }
                        }
                        val active = requireNotNull(renderer)
                        require(active.pageCount > 0) { "EMPTY_PDF" }
                        val ratio = active.openPage(0).use { it.width.toFloat() / it.height.coerceAtLeast(1) }
                        PdfReaderSession(active, copied, active.pageCount, ratio).also { pending = it }
                    } catch (failure: Throwable) {
                        cleanupFailure(failure, copied) { renderer?.close() }
                        when (failure) {
                            is OperationFailure -> throw failure
                            is SecurityException, is FileNotFoundException -> throw OperationFailure("ACCESS_LOST")
                            is IOException -> throw IllegalArgumentException("PDF_INVALID", failure)
                            else -> throw failure
                        }
                    }
                }
                pending = null
                return opened
            } finally { pending?.close() }
        }

    }
}
