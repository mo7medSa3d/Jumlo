package com.jumlo.app.data.pdf

import com.jumlo.app.core.PdfCompressionPolicy
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The shared measured-preview and publication path. Call on an IO dispatcher, with the same
 * codec and bitmap budget for both. A preview performs real compression and can take time;
 * the caller must show loading and cancel stale requests instead of treating it as a formula.
 * Every run owns its temporary directory and leaves the input and an earlier output intact.
 */
object PdfCompressionRunner {
    data class Result(
        val originalBytes: Long,
        val outputBytes: Long,
        val pageCount: Int,
        val replacedImages: Int,
        val skippedImages: Int,
        val usedDpi: Int?,
        val usedQuality: Int?
    )

    fun measure(
        source: File,
        scratchDirectory: File,
        dpi: Int,
        quality: Int,
        codec: PdfImageCodec,
        maxWorkingBytes: Long,
        targetBytes: Long = 0L,
        minFreeBytes: Long = 64L * 1024 * 1024,
        checkCancel: () -> Unit = {}
    ): Result {
        checkCancel()
        require(scratchDirectory.isDirectory) { "DISK_BUDGET_EXCEEDED" }
        val directory = Files.createTempDirectory(scratchDirectory.toPath(), "pdf-measure-").toFile()
        try {
            return compress(source, File(directory, "measured.pdf"), dpi, quality, codec,
                maxWorkingBytes, targetBytes, minFreeBytes, checkCancel = checkCancel)
        } finally {
            directory.deleteRecursively()
        }
    }

    fun compress(
        source: File,
        destination: File,
        dpi: Int,
        quality: Int,
        codec: PdfImageCodec,
        maxWorkingBytes: Long,
        targetBytes: Long = 0L,
        minFreeBytes: Long = 64L * 1024 * 1024,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        checkCancel: () -> Unit = {}
    ): Result {
        require(dpi in 36..600 && quality in 1..100 && targetBytes >= 0L) { "INVALID_COMPRESS_SETTINGS" }
        require(maxWorkingBytes > 0 && minFreeBytes >= 0L) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        require(source.canonicalFile != destination.canonicalFile) { "OUTPUT_SOURCE_CONFLICT" }
        val parent = requireNotNull(destination.absoluteFile.parentFile)
        require(parent.isDirectory) { "DISK_BUDGET_EXCEEDED" }
        fun check() {
            checkCancel()
            require(parent.usableSpace >= minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
        }
        check()
        val directory = Files.createTempDirectory(parent.toPath(), "pdf-compress-").toFile()
        try {
            val originalBytes = source.length()
            val pageCount = open(source, directory, minFreeBytes).use { it.numberOfPages }
            require(pageCount > 0 && originalBytes > 0L) { "EMPTY_PDF" }
            val profiles = if (targetBytes > 0L) PdfCompressionPolicy.targetProfiles(quality)
                else PdfCompressionPolicy.profiles(dpi, quality)
            val candidate = File(directory, "candidate.pdf")
            val best = File(directory, "best.pdf")
            var bestBytes = originalBytes
            var selected: Pair<Int, Int>? = null
            var selectedReport: PdfImageCompressor.Report? = null
            var lastReport: PdfImageCompressor.Report? = null
            for ((pass, profile) in profiles.withIndex()) {
                check()
                candidate.delete()
                open(source, directory, minFreeBytes).use { document ->
                    lastReport = PdfImageCompressor.compress(document, profile.first, profile.second,
                        onPage = { done, total ->
                            check()
                            onProgress(pass.toLong() * total + done, profiles.size.toLong() * total)
                        }, codec = codec, maxWorkingBytes = maxWorkingBytes, checkCancel = ::check)
                    check()
                    document.save(candidate)
                }
                check()
                // Validation has no broad runCatching: cancellation and disk failures must
                // propagate rather than silently becoming an invalid-candidate fallback.
                val candidatePages = open(candidate, directory, minFreeBytes).use { it.numberOfPages }
                if (PdfCompressionPolicy.betterCandidate(originalBytes, bestBytes, candidate.length(), pageCount, candidatePages)) {
                    Files.move(candidate.toPath(), best.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    bestBytes = best.length()
                    selected = profile
                    selectedReport = lastReport
                    if (PdfCompressionPolicy.meetsTarget(bestBytes, targetBytes)) break
                }
            }
            check()
            val ready = if (best.isFile) best else File(directory, "unchanged.pdf").also { copy(source, it, ::check) }
            require(ready.length() in 1..originalBytes) { "PDF_VERIFY_FAILED" }
            val report = selectedReport ?: lastReport
            val result = Result(originalBytes, ready.length(), pageCount,
                if (selected == null) 0 else report?.replaced ?: 0, report?.skipped?.size ?: 0,
                selected?.first, selected?.second)
            check()
            Files.move(ready.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return result
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun open(file: File, scratch: File, minFreeBytes: Long): PDDocument {
        val storageLimit = scratch.usableSpace - minFreeBytes
        require(storageLimit > 0) { "DISK_BUDGET_EXCEEDED" }
        val document = try {
            PDDocument.load(file, "", MemoryUsageSetting.setupTempFileOnly(storageLimit).setTempDir(scratch))
        } catch (failure: InvalidPasswordException) {
            throw IllegalArgumentException("PDF_ENCRYPTED", failure)
        } catch (failure: FileNotFoundException) {
            throw failure
        } catch (failure: IOException) {
            val exhausted = generateSequence<Throwable>(failure) { it.cause }.take(8).any {
                it is IOException && it.message == "Maximum allowed scratch file memory exceeded."
            }
            throw IllegalArgumentException(if (exhausted) "DISK_BUDGET_EXCEEDED" else "PDF_INVALID", failure)
        }
        if (document.isEncrypted) {
            document.close()
            throw IllegalArgumentException("PDF_ENCRYPTED")
        }
        return document
    }

    private fun copy(source: File, destination: File, check: () -> Unit) {
        source.inputStream().buffered().use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    check()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) output.write(buffer, 0, count)
                }
            }
        }
    }
}
