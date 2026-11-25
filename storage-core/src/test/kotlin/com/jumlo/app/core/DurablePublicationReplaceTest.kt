package com.jumlo.app.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DurablePublicationReplaceTest {

    private fun listTempFiles(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".tmp") || it.name.endsWith(".pending")) }
            .toList()

    @Test
    fun replacePublishedUpdatesBytesAndJournalWhilePreservingIdentity() {
        val root = Files.createTempDirectory("pub-replace-happy-").toFile()
        val replacement = Files.createTempFile("pub-replacement-", ".pdf").toFile()
        try {
            val pub = DurablePublication(root)
            val jobId = UUID.randomUUID().toString()
            val ticket = pub.reserve(jobId, "doc-1", "report.pdf", "application/pdf")
            ticket.stageFile.writeText("%PDF-1.7 original payload")
            val initial = pub.publish(ticket)

            val newPayload = "%PDF-1.7 flattened markup payload v2"
            replacement.writeText(newPayload)
            val updatedMeta = """{"markupFlattened":true}"""

            val updated = pub.replacePublished(jobId, "doc-1", replacement, updatedMeta)

            assertEquals(initial.id, updated.id)
            assertEquals(initial.jobId, updated.jobId)
            assertEquals(initial.itemKey, updated.itemKey)
            assertEquals(initial.file.canonicalFile, updated.file.canonicalFile)
            assertEquals(initial.displayName, updated.displayName)
            assertEquals(initial.mimeType, updated.mimeType)
            assertEquals(initial.createdAtEpochMs, updated.createdAtEpochMs)
            assertEquals(newPayload.toByteArray().size.toLong(), updated.sizeBytes)
            assertEquals(Hashing.sha256(replacement), updated.sha256)
            assertEquals(updatedMeta, updated.metadataJson)
            assertEquals(newPayload, updated.file.readText())

            val reconciled = pub.reconcile()
            assertEquals(1, reconciled.size)
            assertEquals(updated, reconciled.single())
            assertTrue("No staging or backup temp files may leak", listTempFiles(root).isEmpty())
        } finally {
            replacement.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun replacePublishedRollsBackPayloadAndCleansTempFilesWhenJournalCommitFails() {
        val root = Files.createTempDirectory("pub-replace-rollback-").toFile()
        val replacement = Files.createTempFile("pub-replacement-", ".pdf").toFile()
        try {
            val pub = DurablePublication(root)
            val jobId = UUID.randomUUID().toString()
            val ticket = pub.reserve(jobId, "doc-1", "report.pdf", "application/pdf")
            val originalPayload = "%PDF-1.7 original durable bytes"
            ticket.stageFile.writeText(originalPayload)
            val initial = pub.publish(ticket)

            replacement.writeText("%PDF-1.7 new bytes that fail before journal commit")
            val failure = assertThrows(IOException::class.java) {
                pub.replacePublishedInternal(jobId, "doc-1", replacement, """{"v":2}""") {
                    throw IOException("SIMULATED_JOURNAL_IO_FAILURE")
                }
            }
            assertEquals("SIMULATED_JOURNAL_IO_FAILURE", failure.message)

            // Original published bytes and journal must be cleanly restored.
            assertEquals(originalPayload, initial.file.readText())
            val reconciled = pub.reconcile()
            assertEquals(1, reconciled.size)
            assertEquals(initial, reconciled.single())
            assertTrue("No staging or backup temp files may leak after rollback", listTempFiles(root).isEmpty())
        } finally {
            replacement.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun replacePublishedRethrowsCancellationExceptionCleanlyAfterRollback() {
        val root = Files.createTempDirectory("pub-replace-cancel-").toFile()
        val replacement = Files.createTempFile("pub-replacement-", ".pdf").toFile()
        try {
            val pub = DurablePublication(root)
            val jobId = UUID.randomUUID().toString()
            val ticket = pub.reserve(jobId, "doc-1", "report.pdf", "application/pdf")
            val originalPayload = "%PDF-1.7 original before cancel"
            ticket.stageFile.writeText(originalPayload)
            val initial = pub.publish(ticket)

            replacement.writeText("%PDF-1.7 cancelled replacement")
            val cancelled = assertThrows(CancellationException::class.java) {
                pub.replacePublishedInternal(jobId, "doc-1", replacement, """{"cancelled":true}""") {
                    throw CancellationException("USER_CANCELLED")
                }
            }
            assertEquals("USER_CANCELLED", cancelled.message)
            assertEquals(originalPayload, initial.file.readText())
            assertEquals(listOf(initial), pub.reconcile())
            assertTrue("No temp files may leak on cancellation", listTempFiles(root).isEmpty())
        } finally {
            replacement.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun replacePublishedRejectsInvalidInputsWithoutMutatingExistingPublication() {
        val root = Files.createTempDirectory("pub-replace-invalid-").toFile()
        val missing = File(root, "does-not-exist.pdf")
        val replacement = Files.createTempFile("pub-replacement-", ".pdf").toFile()
        try {
            val pub = DurablePublication(root)
            val jobId = UUID.randomUUID().toString()
            val ticket = pub.reserve(jobId, "doc-1", "report.pdf", "application/pdf")
            val originalPayload = "%PDF-1.7 untouched"
            ticket.stageFile.writeText(originalPayload)
            val initial = pub.publish(ticket)

            val missingErr = assertThrows(IllegalArgumentException::class.java) {
                pub.replacePublished(jobId, "doc-1", missing, "{}")
            }
            assertEquals("OUTPUT_MISSING", missingErr.message)

            replacement.writeText("replacement")
            val metaErr = assertThrows(IllegalArgumentException::class.java) {
                pub.replacePublished(jobId, "doc-1", replacement, "x".repeat(16385))
            }
            assertEquals("OUTPUT_METADATA_TOO_LARGE", metaErr.message)

            assertEquals(originalPayload, initial.file.readText())
            assertEquals(listOf(initial), pub.reconcile())
            assertTrue(listTempFiles(root).isEmpty())
        } finally {
            replacement.delete()
            root.deleteRecursively()
        }
    }
}
