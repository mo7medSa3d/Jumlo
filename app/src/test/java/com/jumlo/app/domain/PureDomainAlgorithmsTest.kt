package com.jumlo.app.domain

import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.model.FittingStrategy
import com.jumlo.app.model.ResizeMode
import com.jumlo.app.model.UrlValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PureDomainAlgorithmsTest {

    @Test
    fun `parsePageRangeSyntax parses inclusive ranges and single pages accurately`() {
        val result = PureDomainAlgorithms.parsePageRangeSyntax("1-3, 7, 10-12", maxPages = 24)
        assertTrue(result.isSuccess)
        assertEquals(listOf(1, 2, 3, 7, 10, 11, 12), result.getOrThrow())
    }

    @Test
    fun `parsePageRangeSyntax rejects out-of-bounds pages`() {
        val result = PureDomainAlgorithms.parsePageRangeSyntax("1-3, 28", maxPages = 24)
        assertTrue(result.isFailure)
    }

    @Test
    fun `formatPagesToRangeSyntax round-trips sorted page list`() {
        val formatted = PureDomainAlgorithms.formatPagesToRangeSyntax(listOf(1, 2, 3, 7, 10, 11, 12))
        assertEquals("1-3, 7, 10-12", formatted)
    }

    @Test
    fun `parseBatchUrls identifies valid links, duplicates, and unsupported ftp protocol`() {
        val input = """
            https://data.research.org/datasets/climate_2024_q1.csv
            https://data.research.org/datasets/climate_2024_q2.csv
            https://data.research.org/datasets/climate_2024_q1.csv
            ftp://broken.resource/sample.bin
        """.trimIndent()

        val parsed = PureDomainAlgorithms.parseBatchUrls(input)
        assertEquals(4, parsed.size)
        assertEquals(UrlValidationStatus.VALID, parsed[0].status)
        assertEquals(UrlValidationStatus.VALID, parsed[1].status)
        assertEquals(UrlValidationStatus.DUPLICATE, parsed[2].status)
        assertEquals(UrlValidationStatus.UNSUPPORTED_PROTOCOL, parsed[3].status)
    }

    @Test(expected = SecurityException::class)
    fun `validateZipEntryPath blocks Zip-Slip directory traversal attack`() {
        val sandboxDir = File("/tmp/universal_utilities_test_sandbox").apply { mkdirs() }
        PureDomainAlgorithms.validateZipEntryPath(sandboxDir, "../../etc/passwd")
    }

    @Test
    fun `validateZipEntryPath allows clean relative paths inside sandbox`() {
        val sandboxDir = File("/tmp/universal_utilities_test_sandbox").apply { mkdirs() }
        val resolved = PureDomainAlgorithms.validateZipEntryPath(sandboxDir, "Photos_Compressed_Batch/landscape_01.webp")
        assertTrue(resolved.canonicalPath.startsWith(sandboxDir.canonicalPath))
    }

    @Test
    fun `resolveUniqueFileName appends incrementing counter on collision`() {
        val existing = setOf("Lecture_Notes_Bundle.zip", "Lecture_Notes_Bundle (1).zip")
        val resolved = PureDomainAlgorithms.resolveUniqueFileName(existing, "Lecture_Notes_Bundle.zip")
        assertEquals("Lecture_Notes_Bundle (2).zip", resolved)
    }

    @Test
    fun `calculateTargetDimensions preserves 16-9 aspect ratio and respects upscaling guard`() {
        val (w, h) = PureDomainAlgorithms.calculateTargetDimensions(
            sourceWidth = 3840,
            sourceHeight = 2160,
            mode = ResizeMode.BOUNDING_BOX,
            strategy = FittingStrategy.FIT_WITHIN,
            targetWidth = 1920,
            targetHeight = 1080,
            allowUpscaling = false
        )
        assertEquals(1920, w)
        assertEquals(1080, h)

        // Smaller image should not upscale when allowUpscaling = false
        val (smallW, smallH) = PureDomainAlgorithms.calculateTargetDimensions(
            sourceWidth = 800,
            sourceHeight = 600,
            mode = ResizeMode.BOUNDING_BOX,
            strategy = FittingStrategy.FIT_WITHIN,
            targetWidth = 1920,
            targetHeight = 1080,
            allowUpscaling = false
        )
        assertEquals(800, smallW)
        assertEquals(600, smallH)
    }
}

class ExpansionLogicTest {
    @org.junit.Test fun batchRename_applies_pattern_and_uniquifies() {
        val out = com.jumlo.app.data.engine.PureDomainAlgorithms.batchRenamePreview(listOf("a.jpg","b.jpg"), "file-{n}", 1)
        org.junit.Assert.assertEquals(listOf("file-1.jpg","file-2.jpg"), out)
    }
    @org.junit.Test fun duplicateGroups_finds_exact_groups() {
        val groups = com.jumlo.app.data.engine.PureDomainAlgorithms.duplicateGroups(listOf("x","y","x","x","z"))
        org.junit.Assert.assertEquals(listOf(listOf(0,2,3)), groups)
    }
}

class ZipSlipTest {
    @org.junit.Test fun malicious_entry_names_are_rejected() {
        val root = java.nio.file.Files.createTempDirectory("zipslip").toFile()
        for (bad in listOf("../evil.txt", "..\\evil.txt", "/abs.txt", "a/../../evil.txt", "a//b.txt", "C:/evil.txt")) {
            var rejected = false
            try { com.jumlo.app.data.engine.PureDomainAlgorithms.validateZipEntryPath(root, bad) } catch (e: Exception) { rejected = true }
            org.junit.Assert.assertTrue("must reject: $bad", rejected)
        }
        // Safe nested entry resolves inside root.
        val ok = com.jumlo.app.data.engine.PureDomainAlgorithms.validateZipEntryPath(root, "docs/readme.txt")
        org.junit.Assert.assertTrue(ok.path.startsWith(root.canonicalPath))
    }
    @org.junit.Test fun page_range_fuzzish_inputs_never_throw_unexpectedly() {
        val inputs = listOf("", " ", "-", "1-", "-3", "1,,2", "0", "99999999999999999999", "١-٣", "1-3,7", "5-2")
        for (input in inputs) {
            val result = com.jumlo.app.data.engine.PureDomainAlgorithms.parsePageRangeSyntax(input, 10)
            if (result.isSuccess) org.junit.Assert.assertTrue(result.getOrThrow().all { it in 1..10 })
        }
    }
}

class EncryptedZipTest {
    @org.junit.Test fun zip4j_aes_roundtrip_and_wrong_password_rejected() {
        val dir = java.nio.file.Files.createTempDirectory("ezip").toFile()
        val src = java.io.File(dir, "hello.txt").apply { writeText("secret contents") }
        val zipFile = java.io.File(dir, "out.zip")
        val params = net.lingala.zip4j.model.ZipParameters().apply {
            compressionMethod = net.lingala.zip4j.model.enums.CompressionMethod.DEFLATE
            compressionLevel = net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
            isEncryptFiles = true
            encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
            aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
        }
        net.lingala.zip4j.ZipFile(zipFile, "correct horse".toCharArray()).addFile(src, params)
        // Wrong password must fail, not silently produce output.
        var rejected = false
        try {
            val wrongDir = java.io.File(dir, "wrong").apply { mkdirs() }
            net.lingala.zip4j.ZipFile(zipFile, "wrong".toCharArray()).extractAll(wrongDir.path)
            rejected = java.io.File(wrongDir, "hello.txt").let { !it.exists() || it.readText() != "secret contents" }
        } catch (e: Exception) { rejected = true }
        org.junit.Assert.assertTrue("wrong password must not yield plaintext", rejected)
        val okDir = java.io.File(dir, "ok").apply { mkdirs() }
        net.lingala.zip4j.ZipFile(zipFile, "correct horse".toCharArray()).extractAll(okDir.path)
        org.junit.Assert.assertEquals("secret contents", java.io.File(okDir, "hello.txt").readText())
    }
}
