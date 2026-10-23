package com.jumlo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.core.Hashing
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputPublisher
import com.jumlo.app.model.ItemOutcome
import com.jumlo.app.model.ItemStatus
import com.jumlo.app.model.OutputMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

/** Real private files and Room rows; only the external provider boundary is substituted. */
@RunWith(AndroidJUnit4::class)
class PublicSaveRecoveryTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test fun providerCreateWriteAndCommitFailuresKeepOneVerifiedPrivateOutputAndRetryOnlyTheCopy() = runBlocking {
        for (code in listOf("DESTINATION_CREATE_FAILED", "DESTINATION_WRITE_UNAVAILABLE", "DESTINATION_COMMIT_FAILED")) {
            val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
            val job = UUID.randomUUID().toString()
            val publisher = FailingPublisher(code)
            val outputs = OutputStore(app, db, publisher)
            var producedId: String? = null
            try {
                val ticket = outputs.reserve(job, "download:0", "retained.bin", "application/octet-stream")
                val bytes = "verified private bytes for $code".toByteArray()
                ticket.stageFile.writeBytes(bytes)
                val first = outputs.publish(ticket) { OutputMetadata() }
                producedId = first.id
                val privateFile = File(app.filesDir, "processing/results/${first.id}/${first.displayName}")
                assertEquals(code, first.publicSaveError)
                assertNull(first.publicUri)
                assertArrayEquals(bytes, privateFile.readBytes())
                for (status in listOf(ItemStatus.CREATED, ItemStatus.SKIPPED, ItemStatus.UNCHANGED, ItemStatus.LARGER)) {
                    val pending = outputs.reportPublicSaveFailures(listOf(ItemOutcome("download:0", status, first.id))).single()
                    assertEquals(ItemStatus.FAILED, pending.status)
                    assertEquals(first.id, pending.outputId)
                    assertEquals("DESTINATION_SAVE_FAILED", pending.errorCode)
                }

                publisher.failure = null
                outputs.retryPublicSaves(job)
                val retried = outputs.find(job, "download:0")!!
                assertEquals(first.id, retried.id)
                assertEquals(first.sha256, retried.sha256)
                assertNull(retried.publicSaveError)
                assertNotNull(retried.publicUri)
                assertArrayEquals(bytes, privateFile.readBytes())
                assertEquals(1, db.processingDao().outputs().first().size)
                assertEquals(2, publisher.attempts)
                val completed = outputs.reportPublicSaveFailures(listOf(ItemOutcome("download:0", ItemStatus.CREATED, first.id))).single()
                assertEquals(ItemStatus.CREATED, completed.status)
                outputs.retryPublicSaves(job)
                assertEquals("an already visible copy must not be duplicated", 2, publisher.attempts)
            } finally {
                try {
                    producedId?.let { id ->
                        outputs.moveToTrash(id)
                        outputs.purgeTrash(id)
                    }
                } finally { db.close() }
            }
        }
    }

    @Test fun cancellationAfterProviderCommitRecordsTheUriBeforeRetryCanCreateAnotherCopy() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val job = UUID.randomUUID().toString()
        var attempts = 0
        val publisher = object : PublicOutputPublisher {
            override suspend fun publish(file: File, name: String, mime: String, sha256: String, subfolder: String?): String {
                attempts++
                check(Hashing.sha256(file) == sha256)
                // The provider committed synchronously; cancel before the following
                // suspend Room update would normally resume in its caller's context.
                currentCoroutineContext().cancel(CancellationException("fixture cancellation after commit"))
                return "content://fixture-publisher/committed/$sha256"
            }
            override fun deleteIfOwned(publicUri: String?) = Unit
        }
        val outputs = OutputStore(app, db, publisher)
        var producedId: String? = null
        try {
            val ticket = outputs.reserve(job, "download:0", "retained.bin", "application/octet-stream")
            ticket.stageFile.writeText("verified bytes survive cancellation")
            supervisorScope {
                val operation = async(Dispatchers.IO) { outputs.publish(ticket) { OutputMetadata() } }
                try { operation.await(); fail("Cancellation must propagate") }
                catch (_: CancellationException) { /* Expected; persistence must still finish. */ }
            }
            val saved = outputs.find(job, "download:0")!!
            producedId = saved.id
            assertNotNull(saved.publicUri)
            assertNull(saved.publicSaveError)
            outputs.retryPublicSaves(job)
            assertEquals("a cancelled caller must not duplicate a committed copy", 1, attempts)
            assertEquals(1, db.processingDao().outputs().first().size)
        } finally {
            try { producedId?.let { outputs.moveToTrash(it); outputs.purgeTrash(it) } }
            finally { db.close() }
        }
    }

    private class FailingPublisher(var failure: String?) : PublicOutputPublisher {
        var attempts = 0
        override suspend fun publish(file: File, name: String, mime: String, sha256: String, subfolder: String?): String {
            attempts++
            // The external provider can fail at allocation, writing or pending-row commit.
            // Private publication and the retry state remain production code under test.
            failure?.let { throw IOException(it) }
            check(file.isFile && Hashing.sha256(file) == sha256)
            return "content://fixture-publisher/verified/$sha256"
        }
        override fun deleteIfOwned(publicUri: String?) = Unit
    }
}
