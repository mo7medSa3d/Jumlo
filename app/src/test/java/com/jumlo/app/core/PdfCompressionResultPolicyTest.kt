package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCompressionResultPolicyTest {
    @Test fun alreadyOptimizedPdfReportsUnchangedWithoutFalsePartialSuccess() {
        val outcome = PdfCompressionResultPolicy.classify(98_765, 98_765, skippedImages = 4)
        assertFalse(outcome.smaller)
        assertEquals(0L, outcome.savedBytes)
        assertEquals("PDF_NOT_COMPRESSIBLE", outcome.notice)
    }

    @Test fun skippedImagesAreDisclosedWhenMeasuredBytesActuallyDecrease() {
        val outcome = PdfCompressionResultPolicy.classify(1_000_000, 600_000, skippedImages = 2)
        assertTrue(outcome.smaller)
        assertEquals(400_000L, outcome.savedBytes)
        assertEquals("PDF_IMAGE_COMPRESSION_PARTIAL", outcome.notice)
    }

    @Test fun aSmallerOutputWithoutSkipsHasNoWarning() {
        val outcome = PdfCompressionResultPolicy.classify(1024, 510, skippedImages = 0)
        assertTrue(outcome.smaller)
        assertEquals(514L, outcome.savedBytes)
        assertNull(outcome.notice)
    }

    @Test fun unchangedOutputWithoutSkipsReportsNoSaving() {
        val outcome = PdfCompressionResultPolicy.classify(12345, 12345, skippedImages = 0)
        assertFalse(outcome.smaller)
        assertEquals("PDF_NOT_COMPRESSIBLE", outcome.notice)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyOutputIsNeverClassifiedAsSuccess() {
        PdfCompressionResultPolicy.classify(1024, 0, skippedImages = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun largerOutputIsRejectedRatherThanReportedAsCompressed() {
        PdfCompressionResultPolicy.classify(1024, 1025, skippedImages = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSkippedCountIsRejected() {
        PdfCompressionResultPolicy.classify(1024, 1023, skippedImages = -1)
    }
}
