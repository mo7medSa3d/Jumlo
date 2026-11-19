package com.jumlo.app.core

import java.io.ByteArrayInputStream
import java.io.File
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
        assertThrows(IllegalArgumentException::class.java) {
            PageSelection.parse("1-100001", 200000)
        }
    }

    @Test fun filenameCollisionIsCaseInsensitiveAndDeterministic() {
        assertEquals("Report (1).pdf", SafeNames.unique(setOf("report.pdf"), "Report.pdf"))
        assertThrows(IllegalArgumentException::class.java) {
            SafeNames.basename("../escape")
        }
    }

    @Test fun httpPolicyRejectsPortZero() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpPolicy.url("https://example.com:0/file.pdf")
        }
    }

    @Test fun archiveVerifyRejectsCumulativeDeclaredSizeOverBudget() {
        val zipFile = File.createTempFile("archive-budget-", ".zip")
        zipFile.delete()
        try {
            val payload = ByteArray(64) { 0x41 }
            ArchiveCore.create(
                out = zipFile,
                inputs = listOf(
                    ArchiveInput("a.bin") { ByteArrayInputStream(payload) },
                    ArchiveInput("b.bin") { ByteArrayInputStream(payload) }
                ),
                level = 1,
                limits = ArchiveLimits(maxBytes = 1024, maxEntries = 10, maxDepth = 4)
            )
            val failure = assertThrows(IllegalArgumentException::class.java) {
                ArchiveCore.verify(zipFile, ArchiveLimits(maxBytes = 100, maxEntries = 10, maxDepth = 4))
            }
            assertEquals("ARCHIVE_BYTE_BUDGET_EXCEEDED", failure.message)
        } finally {
            zipFile.delete()
        }
    }
}
