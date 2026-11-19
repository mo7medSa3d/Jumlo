package com.jumlo.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.local.DownloadTaskEntity
import com.jumlo.app.data.local.JobEntity
import com.jumlo.app.data.local.LegacyDownloadPrivacyCallback
import com.jumlo.app.data.local.OutputEntity
import com.jumlo.app.data.local.SourceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO behavior on the current database. The v1 structural fixture was retired;
 * migration SQL vs. v2 schema consistency is covered by scripts/test-storage-sql.py.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationAndDaoTest {

    @Test
    fun downloadDao_neverPersistsSignedUrlsAndScrubsLegacyRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "download-privacy-open-test.db"
        context.deleteDatabase(dbName)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(LegacyDownloadPrivacyCallback).build()
        try {
            val first = open()
            try {
                val row = DownloadTaskEntity(
                    "signed", "file.zip", "zip", "example.test", "https://example.test/file?token=very-private",
                    "application/zip", 0L, 100L, 0f, "", "QUEUED", ""
                )
                first.downloadTaskDao().insert(row)
                assertEquals("", first.downloadTaskDao().observeAllDownloads().first().single().fullUrl)
                // Emulate a database written by an older app version. This must be
                // cleared on the NEXT open without losing progress or job status.
                first.openHelper.writableDatabase.execSQL(
                    "UPDATE download_tasks SET fullUrl = 'https://example.test/legacy?sig=private', downloadedBytes = 42 WHERE id = 'signed'"
                )
            } finally { first.close() }
            val restored = open()
            try {
                val download = restored.downloadTaskDao().observeAllDownloads().first().single()
                assertEquals("", download.fullUrl)
                assertEquals(42L, download.downloadedBytes)
                assertEquals("QUEUED", download.statusName)
            } finally { restored.close() }
        } finally { context.deleteDatabase(dbName) }
    }

    @Test
    fun processingDao_roundTrips_jobs_outputs_sources_and_trash_delete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.processingDao()
            dao.insertJob(JobEntity("job1", "{}", "{}", "QUEUED"))
            dao.putSource(SourceEntity("src1", "content://x", "a.pdf", "application/pdf", 3L, true, false, false, null, null))
            dao.insertOutput(OutputEntity("out1", "job1", "document", "{}"))
            assertEquals("job1", dao.job("job1")?.id)
            assertEquals("a.pdf", dao.source("src1")?.displayName)
            assertEquals("out1", dao.output("job1", "document")?.id)
            assertEquals("out1", dao.outputById("out1")?.id)
            assertEquals(1, dao.jobs().first().size)
            assertEquals(1, dao.deleteOutput("out1"))
            assertNull(dao.outputById("out1"))
        } finally {
            db.close()
        }
    }
}
