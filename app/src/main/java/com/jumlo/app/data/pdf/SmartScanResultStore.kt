package com.jumlo.app.data.pdf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.jobs.BitmapWorkGate
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.processing.BitmapTransforms
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Takes ownership of a scanner result URI by making a validated, cancellation-safe private copy. */
object SmartScanResultStore {
    /** CameraX fallback: one bounded upright image at a time, with validated private output. */
    suspend fun createPdfFromImages(context: Context, pages: List<File>): File {
        var pending: File? = null
        try {
            val file = withContext(Dispatchers.IO) {
                require(pages.size in 1..100) { "SCAN_PAGE_LIMIT" }
                BitmapWorkGate.run {
                    val coroutine = currentCoroutineContext()
                    fun check() {
                        coroutine.ensureActive()
                        require(context.cacheDir.usableSpace >= ResourceBudgetPolicy.minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
                    }
                    check()
                    PDFBoxResourceLoader.init(context)
                    val target = File.createTempFile("jumlo-scan-", ".pdf", context.cacheDir).also { pending = it }
                    val memory = MemoryUsageSetting.setupTempFileOnly(context.cacheDir.usableSpace - ResourceBudgetPolicy.minFreeBytes)
                        .setTempDir(context.cacheDir)
                    val bitmapBudget = minOf(Runtime.getRuntime().maxMemory() / 4, 64L * 1024 * 1024)
                    PDDocument(memory).use { document ->
                        for (image in pages) {
                            check()
                            BitmapTransforms.decodeSampled(image, bitmapBudget).use { decoded ->
                                check()
                                val bitmap = decoded.bitmap
                                val scale = 842f / maxOf(bitmap.width, bitmap.height)
                                val width = bitmap.width * scale
                                val height = bitmap.height * scale
                                val page = PDPage(PDRectangle(width, height))
                                document.addPage(page)
                                val embedded = JPEGFactory.createFromImage(document, bitmap, .9f)
                                PDPageContentStream(document, page).use { it.drawImage(embedded, 0f, 0f, width, height) }
                            }
                        }
                        check()
                        document.save(target)
                    }
                    check()
                    ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRendererFactory.open(descriptor, context.cacheDir, ::check).use { renderer ->
                            require(renderer.pageCount == pages.size) { "SCAN_PDF_INVALID" }
                            // Open each generated page and validate its geometry before publication.
                            repeat(renderer.pageCount) { index -> check(); renderer.openPage(index).use { page ->
                                require(page.width > 0 && page.height > 0) { "SCAN_PDF_INVALID" }
                            } }
                        }
                    }
                    target
                }
            }
            pending = null
            return file
        } catch (_: OutOfMemoryError) { throw OperationFailure("LOW_MEMORY") }
        finally { pending?.delete() }
    }

    suspend fun copyPdf(context: Context, uri: Uri, expectedPages: Int): File {
        var pending: File? = null
        try {
            val file = withContext(Dispatchers.IO) {
                require(expectedPages in 1..100) { "SCAN_PAGE_LIMIT" }
                val target = File.createTempFile("jumlo-scan-", ".pdf", context.cacheDir).also { pending = it }
                val coroutine = currentCoroutineContext()
                val source = context.contentResolver.openInputStream(uri) ?: error("SCAN_PDF_UNREADABLE")
                source.use { input -> FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        require(context.cacheDir.usableSpace >= ResourceBudgetPolicy.minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
                        if (read > 0) output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                } }
                coroutine.ensureActive()
                ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRendererFactory.open(descriptor, context.cacheDir) { coroutine.ensureActive() }.use {
                        require(it.pageCount == expectedPages) { "SCAN_PDF_INVALID" }
                    }
                }
                target
            }
            pending = null
            return file
        } catch (_: OutOfMemoryError) { throw OperationFailure("LOW_MEMORY") }
        finally { pending?.delete() }
    }
}
