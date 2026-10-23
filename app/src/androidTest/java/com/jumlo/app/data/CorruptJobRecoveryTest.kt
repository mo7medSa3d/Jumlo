package com.jumlo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.local.JobEntity
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputPublisher
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CorruptJobRecoveryTest {
    @Test fun malformedSnapshotNeverClaimsLeaseAndStillGetsADurableFailureResult() = runBlocking {
        val app: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val budget = WorkBudget()
        val publisher = object : PublicOutputPublisher {
            override suspend fun publish(file: File, name: String, mime: String, sha256: String, subfolder: String?): String =
                error("PUBLICATION_MUST_NOT_START")
            override fun deleteIfOwned(publicUri: String?) = Unit
        }
        val jobs = JobStore(app, db, SourceStore(app, db, budget), OutputStore(app, db, publisher), budget,
            UserPreferencesRepository(app), DownloadAdmission(), EphemeralSecretStore())
        val id = UUID.randomUUID().toString()
        val request = JobRequest(id, "dl_02", emptyList(), 1, "{}", System.currentTimeMillis())
        try {
            db.processingDao().insertJob(JobEntity(id, Json.encodeToString(request), "{incomplete", JobStage.QUEUED.name))
            val dispatcher = object : OperationHandler {
                override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> =
                    error("OPERATION_MUST_NOT_START")
            }
            try { jobs.execute(id, dispatcher); fail("Malformed persisted data must not execute") }
            catch (_: SerializationException) { /* Worker reports this startup failure. */ }
            assertNull(db.processingDao().job(id)!!.leaseOwner)
            assertEquals(0L, db.processingDao().job(id)!!.generation)
            jobs.recordInfrastructureFailure(id)
            val row = db.processingDao().job(id)!!
            val snapshot = Json.decodeFromString<JobSnapshot>(row.snapshotJson)
            assertEquals(JobStage.FAILED.name, row.stage)
            assertEquals(JobStage.FAILED, snapshot.progress.stage)
            assertEquals(request, snapshot.request)
            assertEquals("WORKER_START_FAILED", snapshot.outcomes.single().errorCode)
            assertNull(row.leaseOwner)
        } finally { db.close() }
    }
}
