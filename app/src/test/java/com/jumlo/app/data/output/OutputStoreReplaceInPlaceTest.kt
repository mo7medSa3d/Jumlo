package com.jumlo.app.data.output

import android.content.Context
import android.os.Environment
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jumlo.app.core.DurablePublication
import com.jumlo.app.core.Hashing
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.local.OutputEntity
import com.jumlo.app.model.OutputMetadata
import com.jumlo.app.model.OutputRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutputStoreReplaceInPlaceTest {
    @get:Rule val temporary = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private val noOpPublisher = object : PublicOutputPublisher {
        override suspend fun publish(file: File, name: String, mime: String, sha256: String, subfolder: String?): String =
            error("Unexpected publish call")

        override fun deleteIfOwned(publicUri: String?) = Unit
    }

    @Test
    fun replaceInPlaceReturnsNullWhenSourceUriIsUnknown() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = OutputStore(context, db, noOpPublisher)
        val replacement = File(temporary.newFolder(), "new.pdf").apply { writeBytes("v2".toByteArray()) }
        try {
            val result = store.replaceInPlace("content://com.jumlo.app.fileprovider/outputs/missing.pdf", replacement) {
                OutputMetadata(pageCount = 1)
            }
            assertNull(result)
        } finally {
            db.close()
        }
    }

    @Test
    fun replaceInPlaceUpdatesPrivateAndPublicFileCopiesPreservesSubfolderAndAvoidsDuplicates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val processingRoot = File(context.filesDir, "processing")
        val downloadsDir = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val publicFile = File(downloadsDir, "Jumlo/in-place-target.pdf").apply {
            parentFile?.mkdirs()
            writeBytes("v1-bytes".toByteArray())
        }
        try {
            val jobId = UUID.randomUUID().toString()
            val publisher = DurablePublication(processingRoot)
            val ticket = publisher.reserve(jobId, "markup:0", "in-place-target.pdf", "application/pdf")
            ticket.stageFile.writeBytes("v1-bytes".toByteArray())
            val published = publisher.publish(ticket)

            val initialRecord = OutputRecord(
                id = published.id,
                jobId = published.jobId,
                itemKey = published.itemKey,
                uri = "content://${context.packageName}.fileprovider/outputs/${published.id}/${published.displayName}",
                displayName = published.displayName,
                mimeType = published.mimeType,
                sizeBytes = published.sizeBytes,
                sha256 = published.sha256,
                metadata = OutputMetadata(pageCount = 1, subfolder = "Jumlo/PDF"),
                createdAtEpochMs = published.createdAtEpochMs,
                publicUri = publicFile.toURI().toString(),
                publicSaveError = "DESTINATION_SAVE_FAILED"
            )
            db.processingDao().insertOutput(
                OutputEntity(initialRecord.id, initialRecord.jobId, initialRecord.itemKey, json.encodeToString(initialRecord))
            )

            val store = OutputStore(context, db, noOpPublisher)
            val replacement = File(temporary.newFolder(), "replacement.pdf").apply { writeBytes("v2-marked-up-pdf".toByteArray()) }
            val updated = checkNotNull(
                store.replaceInPlace(checkNotNull(initialRecord.publicUri), replacement) {
                    OutputMetadata(pageCount = 2)
                }
            )

            assertEquals(initialRecord.id, updated.id)
            assertEquals("v2-marked-up-pdf".length.toLong(), updated.sizeBytes)
            assertEquals(Hashing.sha256(replacement), updated.sha256)
            assertEquals(2, updated.metadata.pageCount)
            assertEquals("Jumlo/PDF", updated.metadata.subfolder)
            assertNull(updated.publicSaveError)
            assertEquals("v2-marked-up-pdf", published.file.readText())
            assertEquals("v2-marked-up-pdf", publicFile.readText())
            assertEquals(1, db.processingDao().outputs().first().size)
        } finally {
            publicFile.delete()
            processingRoot.deleteRecursively()
            db.close()
        }
    }

    @Test
    fun replaceInPlaceRecordsDestinationSaveFailedWhenPublicUriCannotBeSynced() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val processingRoot = File(context.filesDir, "processing")
        try {
            val jobId = UUID.randomUUID().toString()
            val publisher = DurablePublication(processingRoot)
            val ticket = publisher.reserve(jobId, "markup:1", "readonly-copy.pdf", "application/pdf")
            ticket.stageFile.writeBytes("v1".toByteArray())
            val published = publisher.publish(ticket)

            val privateUri = "content://${context.packageName}.fileprovider/outputs/${published.id}/${published.displayName}"
            val initialRecord = OutputRecord(
                id = published.id,
                jobId = published.jobId,
                itemKey = published.itemKey,
                uri = privateUri,
                displayName = published.displayName,
                mimeType = published.mimeType,
                sizeBytes = published.sizeBytes,
                sha256 = published.sha256,
                metadata = OutputMetadata(pageCount = 1),
                createdAtEpochMs = published.createdAtEpochMs,
                publicUri = "unsupported://readonly/item",
                publicSaveError = null
            )
            db.processingDao().insertOutput(
                OutputEntity(initialRecord.id, initialRecord.jobId, initialRecord.itemKey, json.encodeToString(initialRecord))
            )

            val store = OutputStore(context, db, noOpPublisher)
            val replacement = File(temporary.newFolder(), "replacement.pdf").apply { writeBytes("v2-private".toByteArray()) }
            val updated = checkNotNull(
                store.replaceInPlace(privateUri, replacement) { OutputMetadata(pageCount = 3) }
            )

            assertEquals("v2-private", published.file.readText())
            assertEquals("DESTINATION_SAVE_FAILED", updated.publicSaveError)
            assertEquals(3, updated.metadata.pageCount)
        } finally {
            processingRoot.deleteRecursively()
            db.close()
        }
    }
}
