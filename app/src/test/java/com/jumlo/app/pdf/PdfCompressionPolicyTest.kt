package com.jumlo.app.pdf

import com.jumlo.app.core.PdfCompressionPolicy
import com.jumlo.app.data.pdf.PdfBoxBackend
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The compression tool must never claim a saving it did not make, and the byte
 * budget must only ever be reported as met by a file that really is that small.
 */
class PdfCompressionPolicyTest {
    private lateinit var dir: File

    @Before fun setup() {
        dir = File(System.getProperty("java.io.tmpdir"), "pdfcompress-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun teardown() {
        dir.deleteRecursively()
    }

    @Test fun presetsRunFromSmallestToHighestQuality() {
        val presets = PdfCompressionPolicy.Preset.entries
        assertEquals(4, presets.size)
        val email = PdfCompressionPolicy.Preset.STRONG
        val balanced = PdfCompressionPolicy.Preset.BALANCED
        val high = PdfCompressionPolicy.Preset.BEST_QUALITY
        assertTrue("the email preset must be the smallest", email.dpi < balanced.dpi && balanced.dpi < high.dpi)
        assertTrue("the email preset must be the lowest quality", email.quality < balanced.quality && balanced.quality < high.quality)
        presets.forEach {
            assertTrue("${it.name} DPI must be in range", it.dpi in 36..600)
            assertTrue("${it.name} quality must be in range", it.quality in 10..95)
            // Every preset tells the user what it is expected to save, and the
            // range comes from a measurement, not from a guess.
            assertTrue("${it.name} must carry a measured range", it.measuredSavingPercent.first >= 0)
            assertTrue("${it.name} must carry a measured range", it.measuredSavingPercent.last <= 100)
        }
    }

    @Test fun theTargetLadderStartsAtTheChosenQualityAndOnlyDescends() {
        val ladder = PdfCompressionPolicy.targetProfiles(85)
        assertEquals(Pair(300, 85), ladder.first())
        var previousDpi = Int.MAX_VALUE
        var previousQuality = Int.MAX_VALUE
        ladder.forEach { (dpi, quality) ->
            assertTrue("DPI must not increase along the ladder", dpi <= previousDpi)
            assertTrue("quality must not increase along the ladder", quality <= previousQuality)
            assertTrue("the chosen quality is the ceiling", quality <= 85)
            previousDpi = dpi
            previousQuality = quality
        }
        assertTrue("the ladder must offer more than one step", ladder.size > 1)
        assertEquals("no duplicate candidates", ladder.size, ladder.distinct().size)
    }

    @Test fun theTargetLadderNeverExceedsTheChosenQuality() {
        val ladder = PdfCompressionPolicy.targetProfiles(20)
        ladder.forEach { (_, quality) -> assertTrue("quality $quality exceeded the ceiling", quality <= 20) }
    }

    @Test fun theLadderIsSafeForAnOutOfRangeQuality() {
        PdfCompressionPolicy.targetProfiles(0).forEach { (_, quality) -> assertTrue(quality >= 10) }
        PdfCompressionPolicy.targetProfiles(999).forEach { (_, quality) -> assertTrue(quality <= 95) }
    }

    @Test fun aBudgetOfZeroMeansNoBudget() {
        assertTrue(PdfCompressionPolicy.meetsTarget(9_000_000L, 0L))
    }

    @Test fun aBudgetIsOnlyMetByAFileThatIsActuallyThatSmall() {
        assertTrue(PdfCompressionPolicy.meetsTarget(1_000_000L, 1_000_000L))
        assertTrue(PdfCompressionPolicy.meetsTarget(999_999L, 1_000_000L))
        assertFalse(PdfCompressionPolicy.meetsTarget(1_000_001L, 1_000_000L))
    }

    @Test fun recompressingAnImageDocumentReallyShrinksItAndKeepsEveryPage() {
        val source = PdfFixtures.imageDoc(File(dir, "scan.pdf"), textPage = true)
        val originalSize = source.length()
        val originalPages = PdfBoxBackend.open(source, null).use { it.numberOfPages }

        val candidate = File(dir, "candidate.pdf")
        PdfBoxBackend.open(source, null).use { doc ->
            PdfBoxBackend.compressImages(doc, 96, 55)
            doc.save(candidate)
        }

        val candidatePages = PdfBoxBackend.open(candidate, null).use { it.numberOfPages }
        assertEquals("the page count must be preserved", originalPages, candidatePages)
        assertTrue("the recompressed file must be smaller", candidate.length() < originalSize)
        assertTrue(
            "the policy must accept the candidate it produced",
            PdfCompressionPolicy.accept(originalSize, candidate.length(), originalPages, candidatePages)
        )
    }

    @Test fun aCandidateThatGrowsOrLosesPagesIsNeverAccepted() {
        assertFalse(PdfCompressionPolicy.accept(1000L, 1001L, 2, 2))
        assertFalse(PdfCompressionPolicy.accept(1000L, 500L, 2, 1))
        assertFalse(PdfCompressionPolicy.accept(1000L, 500L, 0, 1))
        assertFalse(PdfCompressionPolicy.accept(1000L, 0L, 2, 2))
        assertTrue(PdfCompressionPolicy.accept(1000L, 999L, 2, 2))
    }

    @Test fun theBestCandidateIsTheSmallestValidOne() {
        // betterCandidate(original, best, candidate, originalPages, candidatePages)
        assertTrue(PdfCompressionPolicy.betterCandidate(1000L, 1000L, 800L, 2, 2))
        assertFalse("an equal size is not an improvement", PdfCompressionPolicy.betterCandidate(1000L, 800L, 800L, 2, 2))
        assertFalse("a larger candidate is not an improvement", PdfCompressionPolicy.betterCandidate(1000L, 600L, 700L, 2, 2))
        assertFalse("a page-count change is never an improvement", PdfCompressionPolicy.betterCandidate(1000L, 900L, 500L, 2, 3))
        assertTrue("a smaller valid candidate wins", PdfCompressionPolicy.betterCandidate(1000L, 800L, 500L, 2, 2))
    }

    @Test fun theSliderRunsFromSmallestFileToBestQuality() {
        val steps = PdfCompressionPolicy.sliderSteps()
        assertTrue("there must be steps", steps.size >= 3)
        steps.forEach { (dpi, quality) ->
            assertTrue("the steps must stay in range", dpi in 36..600)
            assertTrue("the quality must stay in range", quality in 10..95)
        }
        assertTrue("the first step is the smallest", steps.first().first < steps.last().first)
        assertTrue("the last step is the best quality", steps.last().second > steps.first().second)
    }

    @Test fun theSliderPositionAndTheStepAreInverses() {
        PdfCompressionPolicy.sliderSteps().forEach { (dpi, quality) ->
            val position = PdfCompressionPolicy.sliderPosition(dpi, quality)
            val (backDpi, backQuality) = PdfCompressionPolicy.sliderStep(position)
            assertEquals("the dpi must survive the round trip", dpi, backDpi)
            assertEquals("the quality must survive the round trip", quality, backQuality)
        }
    }

    @Test fun theSliderPositionIsClamped() {
        assertEquals(0f, PdfCompressionPolicy.sliderStep(-1f).let { PdfCompressionPolicy.sliderPosition(it.first, it.second) }, 0.001f)
        assertEquals(1f, PdfCompressionPolicy.sliderStep(2f).let { PdfCompressionPolicy.sliderPosition(it.first, it.second) }, 0.001f)
    }

    @Test fun theEstimateNeverExceedsTheOriginalAndNeverGoesBelowTheText() {
        val original = 1_000_000L
        val images = 800_000L
        val small = PdfCompressionPolicy.estimateBytes(original, images, 72, 35)
        val large = PdfCompressionPolicy.estimateBytes(original, images, 400, 94)
        assertTrue("an estimate may never claim a bigger file: $small", small <= original)
        assertTrue("a better quality means a bigger file", large > small)
        // The non-image part of the file is never shrunk away.
        assertTrue("the text part must survive: $small", small >= original - images)
    }

    @Test fun aFileWithNoImagesIsNeverEstimatedToShrink() {
        val original = 100_000L
        assertEquals(original, PdfCompressionPolicy.estimateBytes(original, 0L, 72, 35))
    }

    @Test fun aTextOnlyDocumentIsNeverTurnedIntoAScreenshot() {
        // The policy must not accept a page-count-preserving but content-destroying result;
        // the backend refuses to rasterize text pages, so the candidate stays valid.
        val source = PdfFixtures.simpleDoc(File(dir, "text.pdf"), listOf("plain text page"))
        val candidate = File(dir, "text-out.pdf")
        PdfBoxBackend.open(source, null).use { doc ->
            PdfBoxBackend.compressImages(doc, 96, 55)
            doc.save(candidate)
        }
        PdfBoxBackend.open(candidate, null).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue("the text must survive compression", PdfBoxBackend.containsText(doc, "plain text page"))
        }
    }
}
