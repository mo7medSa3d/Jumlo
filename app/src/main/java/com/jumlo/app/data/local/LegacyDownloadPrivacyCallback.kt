package com.jumlo.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Drops legacy stored download URLs without a destructive database migration. */
object LegacyDownloadPrivacyCallback : RoomDatabase.Callback() {
    // v1/v2 schema retains fullUrl for old database identity; this build never needs it.
    const val REDACT_LEGACY_URLS = "UPDATE download_tasks SET fullUrl = '' WHERE fullUrl <> ''"
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(REDACT_LEGACY_URLS)
    }
}
