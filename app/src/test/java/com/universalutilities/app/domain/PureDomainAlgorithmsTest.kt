package com.universalutilities.app.domain

import com.universalutilities.app.data.engine.PureDomainAlgorithms
import com.universalutilities.app.model.FittingStrategy
import com.universalutilities.app.model.ResizeMode
import com.universalutilities.app.model.UrlValidationStatus
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
