package com.jumlo.app.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.pdf.PdfReaderSession
import com.jumlo.app.data.pdf.PdfRendererFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PdfRendererPreflightTest {
    @Test @SdkSuppress(minSdkVersion = 26, maxSdkVersion = 27)
    fun cancellationDuringStrictParsingPreservesItsCauseAndCleansScratchFiles() {
        withFixture { source, scratch ->
            val original = source.readBytes()
            val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            val cancelled = CancellationException("cancel strict PDF preflight")
            var afterCopyChecks = 0
            var renderer: PdfRenderer? = null
            val failure = try {
                runCatching {
                    renderer = PdfRendererFactory.open(descriptor, scratch) {
                        // dup shares the source offset. Only begin counting after the actual
                        // input has reached EOF; copy-only checks cannot reach ten here.
                        if (Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR) == source.length()) {
                            afterCopyChecks++
                            if (afterCopyChecks == 10) throw cancelled
                        }
                    }
                }.exceptionOrNull()
            } finally { renderer?.close() }
            assertSame("Strict parsing must observe cancellation after the copy phase", cancelled, failure)
            assertEquals(10, afterCopyChecks)
            assertFalse("Failure must close the caller-owned descriptor", descriptor.fileDescriptor.valid())
            assertTrue("Failed parsing must release its scratch files", scratch.listFiles().orEmpty().isEmpty())
            assertArrayEquals(original, source.readBytes())
        }
    }

    @Test @SdkSuppress(minSdkVersion = 26, maxSdkVersion = 27)
    fun aRealScratchLimitFailureIsReportedAsDiskBudgetAndKeepsTheInput() {
        withFixture(pageCount = 700) { source, scratch ->
            val original = source.readBytes()
            // Deterministic filesystem budget; do not fill the device's real disk to test this.
            val limitedDirectory = object : File(scratch.path) {
                override fun getUsableSpace(): Long = ResourceBudgetPolicy.minFreeBytes + 4096L
            }
            assertTrue(
                "Scratch-limit fixture must exceed PDFBox's 64 KiB allocation quantum",
                source.length() > 64L * 1024L
            )
            val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            var renderer: PdfRenderer? = null
            val failure = try {
                runCatching { renderer = PdfRendererFactory.open(descriptor, limitedDirectory) }.exceptionOrNull()
            } finally { renderer?.close() }
            assertTrue("Expected OperationFailure; actual failure: $failure", failure is OperationFailure)
            assertEquals("DISK_BUDGET_EXCEEDED", failure?.message)
            assertFalse(descriptor.fileDescriptor.valid())
            assertTrue(scratch.listFiles().orEmpty().isEmpty())
            assertArrayEquals(original, source.readBytes())
        }
    }

    @Test fun readerFailureCleanupDeletesItsCopyEvenWhenNativeCloseThrows() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val privateCopy = File.createTempFile("pdf-reader-cleanup-test-", ".pdf", app.cacheDir)
        val original = IOException("original reader failure")
        val closeFailure = IOException("native close failure")
        try {
            val delivered = runCatching {
                PdfReaderSession.cleanupFailure(original, privateCopy) { throw closeFailure }
                throw original
            }.exceptionOrNull()
            assertSame(original, delivered)
            assertFalse("Close failure must not skip private-copy deletion", privateCopy.exists())
            assertArrayEquals(arrayOf(closeFailure), original.suppressed)
        } finally { privateCopy.delete() }
    }

    private fun withFixture(pageCount: Int = 60, block: (File, File) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val directory = File(app.cacheDir, "pdf-preflight-test-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "source.pdf")
        val scratch = File(directory, "scratch").apply { mkdirs() }
        try {
            PDDocument().use { doc ->
                repeat(pageCount) { doc.addPage(PDPage(PDRectangle(300f, 400f))) }
                doc.save(source)
            }
            block(source, scratch)
        } finally { directory.deleteRecursively() }
    }
}
