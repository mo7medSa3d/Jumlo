package com.jumlo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.jobs.DownloadAdmission
import com.jumlo.app.data.jobs.JobStore
import com.jumlo.app.data.jobs.WorkBudget
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.local.JobEntity
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.processing.DownloadSettings
import com.jumlo.app.data.source.EncryptedDownloadSecrets
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.model.JobProgress
import com.jumlo.app.model.JobRequest
import com.jumlo.app.model.JobSnapshot
import com.jumlo.app.model.JobStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Room rows survive a close/reopen; recovery uses real encrypted records and transfer files. */
@RunWith(AndroidJUnit4::class)
class TerminalDownloadSecretCleanupTest {
    @Test fun startupRemovesCompletedAndCancelledInputsButRetainsPausedAndUnrelatedInputs() = withFixture {
        val completed = seed(JobStage.COMPLETED, "dl_02")
        val cancelled = seed(JobStage.CANCELLED, "dl_media")
        val paused = seed(JobStage.PAUSED, "dl_02")
        val unrelatedUrl = "https://example.invalid/unrelated?signature=keep"
        val unrelated = download(unrelatedUrl)
        val pausedBytes = record(paused.handle).readBytes()
        val unrelatedBytes = record(unrelated).readBytes()

        restart()
        jobs.recoverPending()

        for (terminal in listOf(completed, cancelled)) {
            assertNull(secrets.copy(terminal.handle))
            assertFalse(record(terminal.handle).exists())
            assertFalse(terminal.directFolder.exists())
            assertFalse(terminal.mediaFolder.exists())
            assertEquals(terminal.stage.name, db.processingDao().job(terminal.id)?.stage)
        }
        assertSecret(secrets, paused.handle, paused.url)
        assertArrayEquals(pausedBytes, record(paused.handle).readBytes())
        assertTrue(paused.directPartial.isFile)
        assertTrue(paused.mediaPartial.isFile)
        assertEquals(JobStage.PAUSED.name, db.processingDao().job(paused.id)?.stage)
        assertSecret(secrets, unrelated, unrelatedUrl)
        assertArrayEquals(unrelatedBytes, record(unrelated).readBytes())

        // A later startup must be idempotent and cannot turn a manual pause into automatic work.
        restart()
        jobs.recoverPending()
        assertNull(secrets.copy(completed.handle))
        assertNull(secrets.copy(cancelled.handle))
        assertSecret(secrets, paused.handle, paused.url)
        assertSecret(secrets, unrelated, unrelatedUrl)
        assertTrue(paused.directPartial.isFile)
    }

    @Test fun startupWaitsForTerminalLeaseReleaseAndCleansExpiredTerminalLeases() = withFixture {
        val owner = UUID.randomUUID().toString()
        val leased = seed(JobStage.CANCELLED, "dl_media", owner, System.currentTimeMillis() + 3_600_000L)
        val expired = seed(JobStage.COMPLETED, "dl_02", UUID.randomUUID().toString(), 1L)

        restart()
        jobs.recoverPending()

        assertSecret(secrets, leased.handle, leased.url)
        assertTrue(leased.directPartial.isFile)
        assertTrue(leased.mediaPartial.isFile)
        assertNull(secrets.copy(expired.handle))
        assertFalse(expired.directFolder.exists())
        assertFalse(expired.mediaFolder.exists())

        db.processingDao().release(leased.id, owner)
        jobs.recoverPending()
        assertNull(secrets.copy(leased.handle))
        assertFalse(leased.directFolder.exists())
        assertFalse(leased.mediaFolder.exists())
        assertEquals(JobStage.CANCELLED.name, db.processingDao().job(leased.id)?.stage)
    }

    private fun withFixture(block: suspend Fixture.() -> Unit): Unit = runBlocking(Dispatchers.IO) {
        val fixture = Fixture(ApplicationProvider.getApplicationContext())
        try { fixture.block() } finally { fixture.close() }
    }

    private class Fixture(val app: Context) {
        private val databaseName = "terminal-input-cleanup-${UUID.randomUUID()}.db"
        private val handles = mutableSetOf<String>()
        private val ownedFolders = mutableSetOf<File>()
        private val stores = mutableListOf<EphemeralSecretStore>()
        private val budget = WorkBudget()
        private val preferences = UserPreferencesRepository(app)
        var db: AppDatabase = openDatabase()
            private set
        var secrets: EphemeralSecretStore = newSecrets()
            private set
        var jobs: JobStore = newJobs()
            private set

        private fun openDatabase() = Room.databaseBuilder(app, AppDatabase::class.java, databaseName).build()
        private fun newSecrets() = EphemeralSecretStore(EncryptedDownloadSecrets(app)).also(stores::add)
        private fun newJobs(): JobStore {
            val sources = SourceStore(app, db, budget)
            val outputs = OutputStore(app, db, PublicOutputStore(app, preferences))
            return JobStore(app, db, sources, outputs, budget, preferences, DownloadAdmission(), secrets)
        }
        fun restart() {
            db.close()
            db = openDatabase()
            secrets = newSecrets()
            jobs = newJobs()
        }
        suspend fun download(value: String): String {
            val chars = value.toCharArray()
            return try { secrets.putDownload(chars).also(handles::add) } finally { chars.fill('\u0000') }
        }
        fun record(handle: String) = File(app.noBackupFilesDir, "download-inputs/$handle.bin")
        suspend fun seed(stage: JobStage, tool: String, owner: String? = null, leaseUntil: Long = 0): Seed {
            val id = UUID.randomUUID().toString()
            val url = "https://example.invalid/$id?signature=fixture"
            val handle = download(url)
            val request = JobRequest(id, tool, emptyList(), 1,
                Json.encodeToString(DownloadSettings(handle)), System.currentTimeMillis())
            val snapshot = JobSnapshot(request, JobProgress(stage, 64, 64), emptyList(), System.currentTimeMillis())
            db.processingDao().insertJob(JobEntity(id, Json.encodeToString(request), Json.encodeToString(snapshot),
                stage.name, owner, leaseUntil))
            val direct = File(app.filesDir, "transfers/$id").also { check(it.mkdirs()); ownedFolders += it }
            val media = File(app.noBackupFilesDir, "media-transfers/$id").also { check(it.mkdirs()); ownedFolders += it }
            File(direct, "0.part").writeText("direct resumable bytes")
            File(media, "track.part").writeText("media resumable bytes")
            return Seed(id, stage, handle, url, direct, media)
        }
        fun close() {
            db.close()
            app.deleteDatabase(databaseName)
            // Delete only UUID paths/handles created by this fixture; never clear app-wide secrets.
            stores.forEach { store -> handles.forEach(store::remove) }
            ownedFolders.forEach { it.deleteRecursively() }
        }
    }

    private data class Seed(val id: String, val stage: JobStage, val handle: String, val url: String,
        val directFolder: File, val mediaFolder: File) {
        val directPartial get() = File(directFolder, "0.part")
        val mediaPartial get() = File(mediaFolder, "track.part")
    }

    private fun assertSecret(store: EphemeralSecretStore, handle: String, expected: String) {
        val actual = store.copy(handle)
        try { assertArrayEquals(expected.toCharArray(), actual) } finally { actual?.fill('\u0000') }
    }
}
