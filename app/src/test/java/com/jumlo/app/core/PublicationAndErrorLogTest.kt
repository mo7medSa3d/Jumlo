package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PublicationRecoveryTest {
    private fun root(): File = Files.createTempDirectory("pub-test").toFile()

    @Test
    fun interrupted_publication_is_recovered_by_reconcile_after_simulated_death() {
        val dir = root()
        val first = DurablePublication(dir)
        val ticket = first.reserve("11111111-1111-4111-8111-111111111111", "document", "out.pdf", "application/pdf")
        ticket.stageFile.writeBytes("%PDF-fake".toByteArray())
        // Simulate process death after prepare() but before the caller persisted:
        // a fresh instance over the same root must reconcile the journal entry.
        first.prepare(ticket)
        val restarted = DurablePublication(dir)
        val recovered = restarted.reconcile()
        assertTrue(recovered.any { it.jobId == "11111111-1111-4111-8111-111111111111" && it.itemKey == "document" })
        val published = recovered.first { it.itemKey == "document" }
        assertTrue(published.file.isFile)
        assertEquals("%PDF-fake", published.file.readText())
    }

    @Test
    fun incomplete_stage_is_not_published() {
        val dir = root()
        val pub = DurablePublication(dir)
        val ticket = pub.reserve("22222222-2222-4222-8222-222222222222", "document", "out.pdf", "application/pdf")
        ticket.stageFile.writeBytes("partial".toByteArray())
        // No prepare/publish: reconcile must not surface an unfinished stage file.
        val recovered = DurablePublication(dir).reconcile()
        assertFalse(recovered.any { it.jobId == "22222222-2222-4222-8222-222222222222" })
    }

    @Test
    fun publish_is_idempotent_for_same_job_and_key() {
        val dir = root()
        val pub = DurablePublication(dir)
        val ticket = pub.reserve("33333333-3333-4333-8333-333333333333", "document", "out.pdf", "application/pdf")
        ticket.stageFile.writeBytes("data".toByteArray())
        val a = pub.publish(ticket)
        val b = DurablePublication(dir).publish(ticket)
        assertEquals(a.sha256, b.sha256)
        assertEquals(a.id, b.id)
    }
}

class ErrorLogTest {
    @Test
    fun sanitize_accepts_only_stable_codes() {
        assertEquals("PROCESSING_FAILED", ErrorLog.sanitize("PROCESSING_FAILED"))
        assertEquals(null, ErrorLog.sanitize("https://example.com/x?sig=abc"))
        assertEquals(null, ErrorLog.sanitize("password=hunter2"))
        assertEquals(null, ErrorLog.sanitize("some free text"))
        assertEquals(null, ErrorLog.sanitize(null))
    }

    @Test
    fun log_records_codes_and_tool_ids_but_not_payloads() {
        val file = File(Files.createTempDirectory("errlog").toFile(), "error-log.txt")
        val log = ErrorLog(file)
        log.record("DOWNLOAD_FAILED", "dl_02")
        log.record("https://signed.example/?token=SECRET")
        val text = log.read()
        assertTrue(text.contains("DOWNLOAD_FAILED"))
        assertTrue(text.contains("tool=dl_02"))
        assertFalse(text.contains("SECRET"))
        assertFalse(text.contains("http"))
    }
}
