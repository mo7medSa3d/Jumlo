package com.jumlo.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.local.DatabaseMigrations
import com.jumlo.app.data.local.DownloadTaskEntity
import com.jumlo.app.data.local.JobEntity
import com.jumlo.app.data.local.LegacyDownloadPrivacyCallback
import com.jumlo.app.data.local.OutputEntity
import com.jumlo.app.data.local.SourceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Structural 1 -> 2 migration coverage. No authentic exported v1 identity hash was
 * found in repository history. The fixture preserves the two legacy tables from
 * known entity declarations; it is NOT an exported schema or old-install proof.
 * MigrationTestHelper validates the migrated structure against KSP's v2 export,
 * then Room itself opens the migrated database and verifies generated metadata.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationAndDaoTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration_1_to_2_preserves_legacy_records_and_opens_with_Room() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-structural-test.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val sql = InstrumentationRegistry.getInstrumentation().context.assets
                            .open("legacy-v1-structure.sql").bufferedReader().use { it.readText() }
                        sql.lineSequence().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
                            .split(';').map(String::trim).filter(String::isNotEmpty).forEach(db::execSQL)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO managed_files VALUES ('m1','أصل.pdf','/x','f','pdf','application/pdf',10,'10 B',NULL,NULL,NULL,'now','الآن',0,0)")
            execSQL("INSERT INTO managed_files VALUES ('draft1','draft.pdf','/d','f','pdf','application/pdf',0,'0 B',2,'20x30',NULL,'old','قديم',1,0)")
            execSQL("INSERT INTO download_tasks VALUES ('d1','a.zip','zip','example.test','https://example.test/a','application/zip',7,10,1.5,'3s','PAUSED','/downloads/a')")
        }
        helper.close()
        try {
            // Only v2 export is required. A made-up v1 identity hash is never written.
            migrationHelper.runMigrationsAndValidate(dbName, 2, true, DatabaseMigrations.FROM_1_TO_2).close()
            val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(DatabaseMigrations.FROM_1_TO_2)
                .addCallback(LegacyDownloadPrivacyCallback).build()
            try {
                val files = room.managedFileDao().observeAllFiles().first()
                assertEquals(2, files.size)
                assertEquals("أصل.pdf", files.single { it.id == "m1" }.name)
                assertNull(files.single { it.id == "m1" }.pageCount)
                assertTrue(files.single { it.id == "draft1" }.isDraft)
                val download = room.downloadTaskDao().observeAllDownloads().first().single()
                assertEquals(7L, download.downloadedBytes)
                assertEquals("PAUSED", download.statusName)
                assertEquals("", download.fullUrl) // Legacy URL scrubbed on first Room open.
                assertTrue(room.processingDao().jobs().first().isEmpty())
                room.processingDao().insertJob(JobEntity("new-job", "{}", "{}", "QUEUED"))
                assertEquals("new-job", room.processingDao().job("new-job")?.id)
            } finally { room.close() }
        } finally { context.deleteDatabase(dbName) }
    }

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
