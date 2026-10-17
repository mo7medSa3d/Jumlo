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
import com.jumlo.app.data.local.JobEntity
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
 * Room 1 -> 2 migration tests (both MigrationTestHelper against exported 1.json/2.json
 * and direct SQLite DDL verification) plus ProcessingDao round-trip and delete tests.
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
    fun migration_1_to_2_validates_exported_schemas_with_MigrationTestHelper() {
        val dbName = "migration-helper-test.db"
        migrationHelper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO managed_files (id,name,path,folder,extension,mimeType,sizeBytes,formattedSize,pageCount,dimensions,itemCount,modifiedLabelEn,modifiedLabelAr,isDraft,isArchive) VALUES ('m1','a.pdf','/x','f','pdf','application/pdf',10,'10 B',NULL,NULL,NULL,'now','now',0,0)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(dbName, 2, true, DatabaseMigrations.FROM_1_TO_2)
        migrated.query("SELECT name FROM managed_files WHERE id='m1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("a.pdf", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migration_1_to_2_keeps_legacy_rows_and_adds_processing_tables() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration-test.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `managed_files` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `path` TEXT NOT NULL, `folder` TEXT NOT NULL, `extension` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `formattedSize` TEXT NOT NULL, `pageCount` INTEGER, `dimensions` TEXT, `itemCount` INTEGER, `modifiedLabelEn` TEXT NOT NULL, `modifiedLabelAr` TEXT NOT NULL, `isDraft` INTEGER NOT NULL, `isArchive` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `download_tasks` (`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `extension` TEXT NOT NULL, `domain` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `speedMbps` REAL NOT NULL, `etaLabel` TEXT NOT NULL, `statusName` TEXT NOT NULL, `destinationPath` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO managed_files (id,name,path,folder,extension,mimeType,sizeBytes,formattedSize,pageCount,dimensions,itemCount,modifiedLabelEn,modifiedLabelAr,isDraft,isArchive) VALUES ('m1','a.pdf','/x','f','pdf','application/pdf',10,'10 B',NULL,NULL,NULL,'now','now',0,0)")
        DatabaseMigrations.FROM_1_TO_2.migrate(db)
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sources','jobs','outputs','drafts')").use { cursor ->
            assertEquals(4, cursor.count)
        }
        db.query("SELECT name FROM managed_files WHERE id='m1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("a.pdf", cursor.getString(0))
        }
        db.close()
        context.deleteDatabase("migration-test.db")
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
