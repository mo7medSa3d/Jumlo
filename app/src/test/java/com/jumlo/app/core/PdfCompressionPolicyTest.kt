package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

class PdfCompressionPolicyTest {
    @Test fun quality50OffersMultipleResolutionPassesWithoutChangingJpegQuality() {
        val profiles = PdfCompressionPolicy.profiles(240, 50)
        assertEquals(listOf(240 to 50, 220 to 50, 170 to 50, 135 to 50, 108 to 50, 96 to 50, 72 to 50, 60 to 50), profiles)
        // The rungs differ only in resolution: the quality the user asked for
        // is never changed on the way down.
        assertEquals(1, profiles.map { it.second }.distinct().size)
    }

    @Test fun preserveQualityPresetDoesNotForceVeryLowDpi() {
        // A high quality preset keeps a high floor: the ladder does not drag the
        // resolution down to 60 dpi unless the user asked for a small file.
        assertEquals(listOf(240 to 90, 220 to 90, 170 to 90), PdfCompressionPolicy.profiles(240, 90))
        assertEquals(listOf(240 to 75, 220 to 75, 170 to 75, 135 to 75, 108 to 75, 96 to 75), PdfCompressionPolicy.profiles(240, 75))
    }

    @Test fun theSmallestPresetCanGoBelowItsOwnResolution() {
        // The floor used to be the chosen resolution itself, so every preset of
        // 170 dpi or less collapsed onto a single rung and a page that resisted
        // it was left alone. The floor is now below the choice.
        val profiles = PdfCompressionPolicy.profiles(96, 55)
        assertEquals(listOf(96 to 55, 72 to 55, 60 to 55), profiles)
        assertTrue("the ladder must reach below the chosen resolution", profiles.last().first < 96)
        assertEquals(listOf(150 to 80, 135 to 80, 108 to 80, 96 to 80), PdfCompressionPolicy.profiles(150, 80))
    }

    @Test fun subsamplingKeepsLargeScansWithinBitmapBudget() {
        val heap = 256L * 1024 * 1024
        assertEquals(4, PdfCompressionPolicy.decodeSubsampling(7000, 5000, heap))
        assertEquals(1, PdfCompressionPolicy.decodeSubsampling(3000, 2000, heap))
        assertEquals(0, PdfCompressionPolicy.decodeSubsampling(-1, 2000, heap))
        assertEquals(0, PdfCompressionPolicy.decodeSubsampling(80000, 2000, heap))
    }

    @Test fun comparesEveryCandidateAndChoosesOnlyTheSmallestValidOne() {
        val original=20_000_000L
        assertTrue(PdfCompressionPolicy.betterCandidate(original,original,18_000_000L,4,4))
        assertTrue(PdfCompressionPolicy.betterCandidate(original,18_000_000L,12_000_000L,4,4))
        assertFalse(PdfCompressionPolicy.betterCandidate(original,12_000_000L,17_000_000L,4,4))
        assertFalse(PdfCompressionPolicy.betterCandidate(original,original,5_000_000L,4,3))
        assertFalse(PdfCompressionPolicy.betterCandidate(original,original,22_000_000L,4,4))
    }

    @Test fun neverClaimsGuaranteed50PercentFileSizeReduction() {
        assertFalse(PdfCompressionPolicy.accept(100L,100L,1,1))
        assertFalse(PdfCompressionPolicy.accept(100L,0L,1,1))
        assertFalse(PdfCompressionPolicy.accept(100L,50L,2,1))
        assertTrue(PdfCompressionPolicy.accept(100L,99L,1,1))
    }
}
