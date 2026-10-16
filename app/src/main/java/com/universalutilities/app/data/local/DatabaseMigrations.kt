package com.universalutilities.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Legacy records are retained, but are not interpreted as verified processing outputs. */
object DatabaseMigrations {
    val FROM_1_TO_2=object:Migration(1,2) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sources (id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT, sizeBytes INTEGER, readable INTEGER NOT NULL, writable INTEGER NOT NULL, persistedReadGrant INTEGER NOT NULL, modifiedAtEpochMs INTEGER, cachedPath TEXT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sources_uri ON sources(uri)")
            db.execSQL("CREATE TABLE IF NOT EXISTS jobs (id TEXT NOT NULL PRIMARY KEY, requestJson TEXT NOT NULL, snapshotJson TEXT NOT NULL, stage TEXT NOT NULL, leaseOwner TEXT, leaseUntil INTEGER NOT NULL, generation INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS outputs (id TEXT NOT NULL PRIMARY KEY, jobId TEXT NOT NULL, itemKey TEXT NOT NULL, recordJson TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_outputs_jobId_itemKey ON outputs(jobId,itemKey)")
            db.execSQL("CREATE TABLE IF NOT EXISTS drafts (id TEXT NOT NULL PRIMARY KEY, requestJson TEXT NOT NULL)")
        }
    }
}
