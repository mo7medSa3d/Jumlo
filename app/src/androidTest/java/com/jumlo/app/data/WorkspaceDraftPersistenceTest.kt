package com.jumlo.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.jobs.DraftStore
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.model.JobRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceDraftPersistenceTest {
    @Test fun submissionIdentitySurvivesRepositoryRecreationUntilExplicitNewRun() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val draft = WorkspaceDraft(sourceIds = listOf("input"))
            val original = JobRequest("11111111-1111-4111-8111-111111111111", "pdf_02", listOf("input"), 1, "{}", 10L)
            val duplicate = original.copy(id = "22222222-2222-4222-8222-222222222222", submittedAtEpochMs = 20L)
            assertEquals(original, DraftStore(db).reserveSubmission("same-input", original, draft))
            val recreated = DraftStore(db)
            assertEquals(original, recreated.reserveSubmission("same-input", duplicate, draft))
            assertEquals(draft, recreated.loadRecovery())
            recreated.clearSubmission()
            assertEquals(duplicate, recreated.reserveSubmission("same-input", duplicate, draft))
        } finally { db.close() }
    }

    @Test fun recoveryDoesNotOverwriteExplicitlySavedDraft() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val store = DraftStore(db)
            val saved = WorkspaceDraft(sourceIds = listOf("first"), outputName = "Saved.pdf")
            store.saveWorkspaceAndRecovery(saved)
            val editing = saved.copy(outputName = "Unsaved.pdf", sourceIds = listOf("second", "first"))
            store.saveRecovery(editing)
            // A fresh repository represents process recreation using the same durable database.
            val restored = DraftStore(db)
            assertEquals(saved, restored.loadWorkspace())
            assertEquals(editing, restored.loadRecovery())
            restored.saveRecovery(saved)
            assertEquals(saved, restored.loadRecovery())
        } finally { db.close() }
    }

    @Test fun explicitAndRecoveryDraftsNeverStorePasswordsOrTokens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val store = DraftStore(db)
            val sensitive = WorkspaceDraft(secret1 = "test-password", secret2 = "test-token", outputName = "Private.pdf")
            store.saveWorkspaceAndRecovery(sensitive)
            assertTrue(store.loadWorkspace()!!.secret1.isEmpty())
            assertTrue(store.loadRecovery()!!.secret2.isEmpty())
            store.saveRecovery(sensitive.copy(outputName = "Editing.pdf"))
            assertTrue(store.loadRecovery()!!.secret1.isEmpty())
            assertEquals("Private.pdf", store.loadWorkspace()!!.outputName)
        } finally { db.close() }
    }
}
