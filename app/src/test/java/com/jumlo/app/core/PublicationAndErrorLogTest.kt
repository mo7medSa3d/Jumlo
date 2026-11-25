package com.jumlo.app.core

import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.data.pdf.PdfInput
import com.jumlo.app.pdf.PdfFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class PublicationRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun root(): File = temporary.newFolder()

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

    @Test
    fun replace_published_updates_bytes_digest_and_metadata_in_place_without_duplicating() {
        val dir = root()
        val jobId = "66666666-6666-4666-8666-666666666666"
        val pub = DurablePublication(dir)
        val ticket = pub.reserve(jobId, "document", "out.pdf", "application/pdf")
        ticket.stageFile.writeBytes("v1-bytes".toByteArray())
        val initial = pub.publish(ticket)

        val replacement = File(temporary.newFolder(), "replacement.pdf").apply { writeBytes("v2-updated-bytes".toByteArray()) }
        val updated = pub.replacePublished(jobId, "document", replacement, """{"pageCount":3}""")

        assertEquals(initial.id, updated.id)
        assertEquals(initial.file.canonicalPath, updated.file.canonicalPath)
        assertEquals(initial.createdAtEpochMs, updated.createdAtEpochMs)
        assertEquals("v2-updated-bytes".length.toLong(), updated.sizeBytes)
        assertEquals(Hashing.sha256(replacement), updated.sha256)
        assertEquals("""{"pageCount":3}""", updated.metadataJson)
        assertEquals("v2-updated-bytes", updated.file.readText())

        val reconciled = DurablePublication(dir).reconcile()
        assertEquals(1, reconciled.size)
        assertEquals(updated.sha256, reconciled.single().sha256)
        assertEquals("""{"pageCount":3}""", reconciled.single().metadataJson)
    }

    @Test
    fun replace_published_rejects_missing_file_missing_record_and_oversized_metadata() {
        val dir = root()
        val jobId = "77777777-7777-4777-8777-777777777777"
        val pub = DurablePublication(dir)
        val ticket = pub.reserve(jobId, "document", "out.pdf", "application/pdf")
        ticket.stageFile.writeBytes("v1".toByteArray())
        pub.publish(ticket)

        val missingFile = File(temporary.newFolder(), "absent.pdf")
        assertEquals(
            "OUTPUT_MISSING",
            runCatching { pub.replacePublished(jobId, "document", missingFile, "{}") }.exceptionOrNull()?.message
        )

        val validFile = File(temporary.newFolder(), "valid.pdf").apply { writeBytes("v2".toByteArray()) }
        assertEquals(
            "OUTPUT_MISSING",
            runCatching { pub.replacePublished(jobId, "unknown-key", validFile, "{}") }.exceptionOrNull()?.message
        )
        assertEquals(
            "OUTPUT_METADATA_TOO_LARGE",
            runCatching { pub.replacePublished(jobId, "document", validFile, "a".repeat(16385)) }.exceptionOrNull()?.message
        )
    }

    @Test
    fun download_resume_after_process_death_preserves_partial_bytes_with_matching_validator() {
        val dir = root()
        val partial = File(dir, "0.part")
        val payload = "0123456789ABCDEF".toByteArray()
        val url = "https://example.org/archive.bin"
        val finalUrlDigest = Hashing.sha256(ByteArrayInputStream(url.toByteArray()))
        // Simulate process death after first 8 bytes written
        partial.writeBytes(payload.copyOfRange(0, 8))
        val transport = object : HttpTransport {
            override fun open(url: String, headers: Map<String, String>): TransportResponse {
                assertEquals("bytes=8-", headers["Range"])
                assertEquals("\"etag-v1\"", headers["If-Range"])
                return TransportResponse(
                    code = 206,
                    headers = mapOf(
                        "content-range" to "bytes 8-15/16",
                        "content-length" to "8",
                        "etag" to "\"etag-v1\""
                    ),
                    body = ByteArrayInputStream(payload.copyOfRange(8, 16))
                )
            }
        }
        val result = StreamTransfer(transport).execute(
            TransferRequest(
                url = url,
                partialFile = partial,
                offset = 8L,
                strongEtag = "\"etag-v1\"",
                lastModified = null,
                knownTotalBytes = 16L,
                finalUrlSha256 = finalUrlDigest
            )
        )
        assertEquals(16L, result.bytes)
        assertEquals("0123456789ABCDEF", partial.readText())
    }

    @Test
    fun stream_transfer_retries_transient_503_with_backoff_and_succeeds() {
        val dir = root()
        val partial = File(dir, "retry.part")
        val attempts = AtomicInteger(0)
        val transport = object : HttpTransport {
            override fun open(url: String, headers: Map<String, String>): TransportResponse {
                val n = attempts.incrementAndGet()
                return if (n < 2) {
                    TransportResponse(503, emptyMap(), ByteArrayInputStream(ByteArray(0)))
                } else {
                    TransportResponse(200, mapOf("content-length" to "4", "etag" to "\"ok\""), ByteArrayInputStream("done".toByteArray()))
                }
            }
        }
        val res = StreamTransfer(transport).execute(
            TransferRequest("https://example.org/retry.bin", partial, maxRetries = 2, retryBackoffMs = 5L)
        )
        assertEquals(2, attempts.get())
        assertEquals(4L, res.bytes)
        assertEquals("done", partial.readText())
    }

    @Test
    fun process_death_during_pdf_merge_recovers_journal_or_reruns_idempotently() {
        val dir = root()
        val jobId = "44444444-4444-4444-8444-444444444444"
        val pdfA = PdfFixtures.simpleDoc(File(dir, "a.pdf"), listOf("merge-page-1"))
        val pdfB = PdfFixtures.simpleDoc(File(dir, "b.pdf"), listOf("merge-page-2"))
        val pub1 = DurablePublication(dir)
        val ticket1 = pub1.reserve(jobId, "merged", "Merged.pdf", "application/pdf")
        // Simulate process death partway through writing stageFile (before prepare/publish)
        ticket1.stageFile.writeBytes("%PDF-truncated".toByteArray())
        pub1.discardIncomplete(jobId)
        assertFalse("Unjournaled partial merge must be discarded", ticket1.stageFile.exists())

        // Worker restarts after process death, runs merge into stage, and crashes right after prepare()
        val pub2 = DurablePublication(dir)
        val ticket2 = pub2.reserve(jobId, "merged", "Merged.pdf", "application/pdf")
        PdfBoxBackend.merge(listOf(PdfInput(pdfA), PdfInput(pdfB)), ticket2.stageFile)
        pub2.prepare(ticket2)

        // Next restart reconciles the journaled merge without re-running or duplicating
        val recovered = DurablePublication(dir).reconcile()
        assertEquals(1, recovered.size)
        PdfBoxBackend.open(recovered.single().file, null).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(listOf("merge-page-1", "merge-page-2"), PdfBoxBackend.pageTexts(doc))
        }
    }

    @Test
    fun process_death_during_scan_discards_uncommitted_stage_and_preserves_completed_snapshot() {
        val dir = root()
        val jobId = "55555555-5555-4555-8555-555555555555"
        val pub = DurablePublication(dir)
        val ticket = pub.reserve(jobId, "scan", "scan.pdf", "application/pdf")
        PdfFixtures.simpleDoc(ticket.stageFile, listOf("scanned-page-1"))
        pub.prepare(ticket)
        // Simulate death before finish() move completes, followed by restart + discardIncomplete
        val restarted = DurablePublication(dir)
        restarted.discardIncomplete(jobId)
        val published = restarted.reconcile().single { it.jobId == jobId }
        assertTrue(published.file.isFile)
        PdfBoxBackend.open(published.file, null).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }
}

class ErrorLogTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun sanitize_accepts_only_stable_codes() {
        assertEquals("PROCESSING_FAILED", ErrorLog.sanitize("PROCESSING_FAILED"))
        assertEquals(null, ErrorLog.sanitize("https://example.com/x?sig=abc"))
        assertEquals(null, ErrorLog.sanitize("password=hunter2"))
        assertEquals(null, ErrorLog.sanitize("some free text"))
        assertEquals(null, ErrorLog.sanitize(null))
    }

    @Test
    fun clearing_log_removes_rotated_file_and_export_includes_previous_codes() {
        val file = File(temporary.newFolder(), "error-log.txt")
        val log = ErrorLog(file, 10L)
        log.record("FIRST_ERROR")
        log.record("SECOND_ERROR")
        assertTrue(File(file.parentFile, file.name + ".1").isFile)
        assertTrue(log.read().contains("FIRST_ERROR"))
        assertTrue(log.read().contains("SECOND_ERROR"))
        log.clear()
        assertEquals("", log.read())
        assertFalse(file.exists())
        assertFalse(File(file.parentFile, file.name + ".1").exists())
    }

    @Test
    fun log_records_codes_and_tool_ids_but_not_payloads() {
        val file = File(temporary.newFolder(), "error-log.txt")
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
