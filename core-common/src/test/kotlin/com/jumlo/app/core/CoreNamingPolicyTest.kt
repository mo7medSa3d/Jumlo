package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoreNamingPolicyTest {
    @Test fun renameRetainsOriginalExtensionAndRejectsTraversal() {
        assertEquals("Quarterly report.pdf", DocumentNamePolicy.rename("source.pdf", "Quarterly report"))
        assertEquals("Quarterly report.pdf", DocumentNamePolicy.rename("source.pdf", "Quarterly report.pdf"))
        assertThrows(IllegalArgumentException::class.java) {
            DocumentNamePolicy.rename("source.pdf", "../private")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DocumentNamePolicy.rename("source.pdf", "   ")
        }
    }

    @Test fun arabicNumeralsAndSeparatorsAreValidPageRanges() {
        assertEquals(listOf(1, 2, 3, 5), PageSelection.parse("١-٣،٥", 5))
        assertEquals(listOf(1, 2, 3, 5), PageSelection.parse("1-3,5", 5))
    }

    @Test fun filenameCollisionIsCaseInsensitiveAndDeterministic() {
        assertEquals("Report (1).pdf", SafeNames.unique(setOf("report.pdf"), "Report.pdf"))
        assertThrows(IllegalArgumentException::class.java) {
            SafeNames.basename("../escape")
        }
    }
}
