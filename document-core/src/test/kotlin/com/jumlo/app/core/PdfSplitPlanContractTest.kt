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
}
