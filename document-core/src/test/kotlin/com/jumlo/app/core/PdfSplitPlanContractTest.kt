package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfSplitPlanContractTest {
    @Test fun splitsAtVisualPageBoundariesWithoutDroppingPages() {
        assertEquals(listOf(0 to 2, 2 to 2, 4 to 1), PdfSplitPlan.splitAfter(5, "2,4"))
        assertEquals(listOf(0 to 3, 3 to 2), PdfSplitPlan.splitAfter(5, "3"))
        assertEquals(listOf(0 to 2, 2 to 2, 4 to 1), PdfSplitPlan.splitAfter(5, "٢،٤"))
    }

    @Test fun invalidBoundariesCannotProduceEmptyOrMissingDocuments() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfSplitPlan.splitAfter(1, "1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfSplitPlan.splitAfter(5, "5")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfSplitPlan.splitAfter(5, "all")
        }
    }

    @Test fun compressionEstimateAndPageEditsHandleBoundaryInputsSafely() {
        assertEquals(99, PdfCompressionPolicy.estimatedSavingPercent(1_000L, Long.MIN_VALUE))
        val edit = PdfPageEdit.start(4).rotate(2, 90).rotate(4, 180).selectRange(1, 2).deleteSelected()
        assertEquals(listOf(3, 4), edit.order)
        assertEquals(mapOf(4 to 180), edit.rotations)
        val parts = PdfPageEdit.start(4).rotate(1, 90).rotate(3, 270).splitEvery(2)
        assertEquals(listOf(mapOf(1 to 90), mapOf(3 to 270)), parts.map { it.rotations })
        assertEquals(listOf("pdf-id", "img-id"), PdfOverlaySourcePolicy.ordered(listOf("img-id" to "IMAGE/PNG", "pdf-id" to "APPLICATION/PDF")))
    }
}
