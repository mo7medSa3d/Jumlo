package com.jumlo.app.data.pdf

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.jobs.OperationFailure
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccessRead
import com.tom_roush.pdfbox.io.ScratchFile
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException

/**
 * Owns [descriptor] on every path, as PdfRenderer does on success. Run outside the UI thread.
 *
 * AOSP 8.0/8.1 checks PDFium's stale last-error value even after successful initialization,
 * page-count and close calls. A rejected document can therefore poison later valid sessions.
 * Android 9 removes those unconditional checks. On SDK 26/27, reject encrypted or structurally
 * invalid PDFs with strict PDFBox parsing before entering framework PDFium. This is one
 * preflight per renderer, never one per rendered page. PDFBox/PDFium parser disagreement is
 * still possible; this guard cannot reset already-poisoned framework state.
 */
object PdfRendererFactory {
    fun open(
        descriptor: ParcelFileDescriptor,
        scratchDirectory: File,
        checkCancellation: () -> Unit = {}
    ): PdfRenderer {
        try {
            checkCancellation()
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.O_MR1) {
                preflight(descriptor, scratchDirectory, checkCancellation)
            }
            checkCancellation()
            return PdfRenderer(descriptor)
        } catch (failure: Throwable) {
            runCatching { descriptor.close() }
            when (failure) {
                is OperationFailure -> throw failure
                is InvalidPasswordException, is SecurityException -> throw OperationFailure("PDF_ENCRYPTED")
                is IOException -> {
                    // Exact message from the pinned PDFBox 2.0.27.0 ScratchFile cap. Other
                    // parse/read errors remain PDF_INVALID; do not infer budget from any IO error.
                    val exhausted = generateSequence<Throwable>(failure) { it.cause }.take(8).any {
                        it is IOException && it.message == "Maximum allowed scratch file memory exceeded."
                    }
                    throw OperationFailure(if (exhausted) "DISK_BUDGET_EXCEEDED" else "PDF_INVALID")
                }
                else -> throw failure
            }
        }
    }

    private fun preflight(descriptor: ParcelFileDescriptor, scratchDirectory: File, checkCancellation: () -> Unit) {
        // dup shares an open-file offset. Preserve it explicitly; never consume/close the caller's
        // descriptor while PDFBox reads its duplicate. Pipes are rejected before touching PDFium,
        // allowing the reader's existing bounded private-copy fallback to handle them.
        val position = try { Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR) }
        catch (failure: ErrnoException) { throw IllegalArgumentException("file descriptor not seekable", failure) }
        val storageLimit = scratchDirectory.usableSpace - ResourceBudgetPolicy.minFreeBytes
        if (storageLimit <= 0) throw OperationFailure("DISK_BUDGET_EXCEEDED")
        var primaryFailure: Throwable? = null
        try {
            ParcelFileDescriptor.dup(descriptor.fileDescriptor).use { duplicate ->
                Os.lseek(duplicate.fileDescriptor, 0, OsConstants.SEEK_SET)
                ParcelFileDescriptor.AutoCloseInputStream(duplicate).use { raw ->
                    val checked = object : FilterInputStream(raw) {
                        private fun checkBudget() {
                            checkCancellation()
                            if (scratchDirectory.usableSpace < ResourceBudgetPolicy.minFreeBytes) {
                                throw OperationFailure("DISK_BUDGET_EXCEEDED")
                            }
                        }
                        override fun read(): Int { checkBudget(); return super.read() }
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            checkBudget(); return super.read(buffer, offset, length)
                        }
                    }
                    val memory = MemoryUsageSetting.setupTempFileOnly(storageLimit).setTempDir(scratchDirectory)
                    ScratchFile(memory).use { scratch ->
                        scratch.createBuffer(checked).use { input ->
                            checkCancellation()
                            val parser = PDFParser(CancellablePdfRead(input, checkCancellation), "", scratch)
                            parser.setLenient(false)
                            parser.parse()
                            parser.getPDDocument().use { document ->
                                checkCancellation()
                                if (document.isEncrypted) throw OperationFailure("PDF_ENCRYPTED")
                                if (document.numberOfPages <= 0) throw OperationFailure("PDF_INVALID")
                            }
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try { Os.lseek(descriptor.fileDescriptor, position, OsConstants.SEEK_SET) }
            catch (restoreFailure: Throwable) {
                val original = primaryFailure
                if (original == null) throw restoreFailure
                if (original !== restoreFailure) original.addSuppressed(restoreFailure)
            }
        }
    }
}

/** Cheap coroutine checks during PDFBox parsing; closing is deliberately never cancellation-gated. */
internal class CancellablePdfRead(
    private val input: RandomAccessRead,
    private val checkCancellation: () -> Unit
) : RandomAccessRead by input {
    private inline fun <T> checked(read: () -> T): T { checkCancellation(); return read() }
    override fun read(): Int = checked { input.read() }
    override fun read(buffer: ByteArray): Int = checked { input.read(buffer) }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checked { input.read(buffer, offset, length) }
    override fun getPosition(): Long = checked { input.position }
    override fun seek(position: Long) = checked { input.seek(position) }
    override fun length(): Long = checked { input.length() }
    override fun peek(): Int = checked { input.peek() }
    override fun rewind(bytes: Int) = checked { input.rewind(bytes) }
    override fun isEOF(): Boolean = checked { input.isEOF }
    override fun available(): Int = checked { input.available() }
    override fun readFully(length: Int): ByteArray {
        checkCancellation()
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = read(bytes, offset, minOf(length - offset, 64 * 1024))
            if (count < 0) throw EOFException()
            offset += count
        }
        return bytes
    }
}
