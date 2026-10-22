package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

class DownloadRestartPolicyTest {
    @Test fun explicitFailedRetryRestartsLegacyJournalExactlyOnce() {
        val generation = DownloadRestartPolicy.retryGeneration("dl_02", "WAITING_FOR_INPUT", 0, "DOWNLOAD_RESTART_REQUIRED")
        assertEquals(1L, generation)
        assertTrue(DownloadRestartPolicy.shouldDiscardPartial(generation, 0))
        assertFalse(DownloadRestartPolicy.shouldDiscardPartial(generation, generation))
    }
    @Test fun pauseResumeKeepsValidPartialsAndBatchFailureGetsFreshGeneration() {
        assertEquals(3L, DownloadRestartPolicy.retryGeneration("dl_03", "PAUSED", 3))
        assertEquals(3L, DownloadRestartPolicy.retryGeneration("dl_02", "INTERRUPTED", 3))
        assertEquals(3L, DownloadRestartPolicy.retryGeneration("dl_02", "WAITING_FOR_INPUT", 3, "SECRET_EXPIRED"))
        assertEquals(3L, DownloadRestartPolicy.retryGeneration("dl_02", "WAITING_FOR_INPUT", 3, "WIFI_REQUIRED"))
        assertEquals(4L, DownloadRestartPolicy.retryGeneration("dl_03", "PARTIAL", 3))
        assertEquals(3L, DownloadRestartPolicy.retryGeneration("img_04", "FAILED", 3))
    }
    @Test fun failedRetryRemovesStaleBytesAndJournalButRetainsCommittedOutput() {
        val folder=java.nio.file.Files.createTempDirectory("download-restart").toFile()
        try {
            val partial=java.io.File(folder,"0.part").apply { writeText("old resource") }
            val journal=java.io.File(folder,"0.properties").apply { writeText("old validators") }
            val pending=java.io.File(folder,"0.pending").apply { writeText("pending validators") }
            val committed=java.io.File(folder,"committed.bin").apply { writeText("verified output") }
            var leaseChecks=0
            assertTrue(DownloadRestartPolicy.resetUncommittedFiles(1,0,partial,journal,pending) { leaseChecks++ })
            assertEquals(3,leaseChecks)
            assertFalse(partial.exists());assertFalse(journal.exists());assertFalse(pending.exists())
            assertEquals("verified output",committed.readText())
            partial.writeText("new validated partial")
            journal.writeText("restartGeneration=1")
            assertFalse(DownloadRestartPolicy.resetUncommittedFiles(1,1,partial,journal,pending) { throw AssertionError("must not reset paused work") })
            assertEquals("new validated partial",partial.readText())
        } finally { folder.deleteRecursively() }
    }
    @Test fun lostLeasePreventsRestartMutation() {
        val folder=java.nio.file.Files.createTempDirectory("download-lease").toFile()
        try {
            val partial=java.io.File(folder,"0.part").apply { writeText("keep") }
            try {
                DownloadRestartPolicy.resetUncommittedFiles(1,0,partial,java.io.File(folder,"0.properties"),java.io.File(folder,"0.pending")) { throw IllegalStateException("lease lost") }
                throw AssertionError("must abort")
            } catch(expected:IllegalStateException) { assertEquals("lease lost",expected.message) }
            assertEquals("keep",partial.readText())
        } finally { folder.deleteRecursively() }
    }
}
