package com.jumlo.app.data

import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.jumlo.app.data.files.DocumentTreeStore
import com.jumlo.app.data.jobs.DownloadAdmission
import com.jumlo.app.data.jobs.DraftStore
import com.jumlo.app.data.jobs.JobStore
import com.jumlo.app.data.jobs.WorkBudget
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.intake.Intake
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** A real provider pipe holds the copy open while the actual ViewModel changes tool session. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap(ContentProvider) was added in API 29.
class ImportSessionIsolationTest {
    @Test fun lateImportStaysInLibraryWithoutAttachingToTheNewTool() = exerciseImport(switchSession = true)

    @Test fun lateImportStillAttachesWhenItsOriginalToolSessionIsCurrent() = exerciseImport(switchSession = false)

    private fun exerciseImport(switchSession: Boolean): Unit = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val root = File(app.cacheDir, "import-session-fixture-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val opened = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val writerError = AtomicReference<Throwable?>(null)
        val writer = AtomicReference<Thread?>(null)
        val bytes = blankPdf()
        val authority = "jumlo.fixture.import.${UUID.randomUUID()}"
        val uri = "content://$authority/late.pdf"
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/pdf"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                    addRow(arrayOf("late.pdf", bytes.size))
                }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                val pipe = ParcelFileDescriptor.createPipe()
                writer.set(thread(isDaemon = true, name = "jumlo-import-fixture") {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                            check(release.await(20, TimeUnit.SECONDS)) { "Import fixture was not released" }
                            output.write(bytes)
                        }
                    } catch (failure: Throwable) { writerError.set(failure) }
                })
                opened.complete(Unit)
                return pipe[0]
            }
        }
        provider.attachInfo(app, ProviderInfo().apply { this.authority = authority })
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(app) {
            override fun getContentResolver() = resolver
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val budget = WorkBudget()
        val preferences = UserPreferencesRepository(app)
        val sources = SourceStore(context, db, budget)
        val outputs = OutputStore(context, db, PublicOutputStore(context, preferences))
        val secrets = EphemeralSecretStore()
        val jobs = JobStore(context, db, sources, outputs, budget, preferences, DownloadAdmission(), secrets)
        var vm: AppViewModel? = null
        try {
            val active = withContext(Dispatchers.Main) {
                AppViewModel(app, preferences, sources, outputs, jobs, DraftStore(db), secrets,
                    DocumentTreeStore(app), budget, com.jumlo.app.data.ocr.MlKitOcrEngine(app), SavedStateHandle()).also { vm = it }
            }
            withTimeout(15_000) { active.workspaceReady.first { it } }
            val finished = CompletableDeferred<Unit>()
            withContext(Dispatchers.Main) {
                assertTrue(active.chooseTool("pdf_03"))
                active.intake(Intake(listOf(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION, null),
                    onFinished = { finished.complete(Unit) })
            }
            withTimeout(15_000) { opened.await() }
            withContext(Dispatchers.Main) {
                if (switchSession) assertTrue(active.chooseTool("pdf_compress"))
                assertTrue(active.form.value.sourceIds.isEmpty())
            }
            release.countDown()
            withTimeout(15_000) { finished.await() }

            assertNull(writerError.get())
            val source = sources.records.first().single()
            assertEquals("late.pdf", source.displayName)
            assertEquals(bytes.size.toLong(), source.sizeBytes)
            sources.openRead(source.id).use { assertArrayEquals(bytes, it.readBytes()) }
            withContext(Dispatchers.Main) {
                assertEquals(if (switchSession) "pdf_compress" else "pdf_03", active.form.value.toolId)
                assertEquals(if (switchSession) emptyList<String>() else listOf(source.id), active.form.value.sourceIds)
            }
        } finally {
            release.countDown()
            vm?.let { active ->
                val scope = active.viewModelScope
                withContext(Dispatchers.Main) { scope.cancel() }
                scope.coroutineContext[Job]?.join()
            }
            writer.get()?.join(5_000)
            db.close()
            root.deleteRecursively()
        }
    }

    private fun blankPdf(): ByteArray = ByteArrayOutputStream().use { output ->
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(120, 160, 1).create())
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
        output.toByteArray()
    }
}
