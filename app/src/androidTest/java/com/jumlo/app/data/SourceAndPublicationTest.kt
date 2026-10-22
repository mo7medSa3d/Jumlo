package com.jumlo.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.jobs.WorkBudget
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.model.OutputMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SourceAndPublicationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun shareIntakeUsesRealUri() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val file=File(context.filesDir,"processing/results/fixture/input.bin")
            file.parentFile.mkdirs(); val bytes=ByteArray(16384) { it.toByte() }; file.writeBytes(bytes)
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
            val store=SourceStore(context,db,WorkBudget())
            val source=store.inspect(uri.toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertArrayEquals(bytes,store.openRead(source.id).use { it.readBytes() })
            assertArrayEquals(bytes,file.readBytes())
        } finally { db.close() }
    }
    @Test fun reimportDoesNotMutateEarlierSnapshot() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val f=File(context.filesDir,"processing/results/fixture/changing.bin").apply { parentFile.mkdirs(); writeText("original") }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",f)
            val store=SourceStore(context,db,WorkBudget())
            val first=store.inspect(uri.toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            f.writeText("replacement")
            val second=store.inspect(uri.toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertNotEquals(first.id,second.id)
            assertEquals("original",store.openRead(first.id).bufferedReader().use { it.readText() })
            assertEquals("replacement",store.openRead(second.id).bufferedReader().use { it.readText() })
        } finally { db.close() }
    }
    @Test fun genericLocalFileIsIdentifiedFromItsBytesNotItsExtension() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val input=File(context.cacheDir,"jumlo-sniff-${UUID.randomUUID()}.pdf")
        try {
            val store=SourceStore(context,db,WorkBudget())
            input.writeBytes("%PDF-1.7\nfixture".toByteArray())
            val pdf=store.importLocalFile(input,"received.pdf","application/octet-stream")
            assertEquals("application/pdf",pdf.mimeType)
            assertArrayEquals(input.readBytes(),store.openRead(pdf.id).use { it.readBytes() })
            input.writeBytes("invalid document payload".toByteArray())
            val invalid=store.importLocalFile(input,"wrong.pdf",null)
            assertEquals("application/octet-stream",invalid.mimeType)
        } finally { input.delete();db.close() }
    }
    @Test fun abandonedBatchImportRollbackNeverDeletesTheOriginalOutput() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val original=File(context.filesDir,"processing/results/fixture/${UUID.randomUUID()}.pdf")
        original.parentFile!!.mkdirs()
        val bytes="%PDF-1.4\ntest content preserved".toByteArray()
        original.writeBytes(bytes)
        try {
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",original)
            val store=SourceStore(context,db,WorkBudget())
            val imported=store.inspect(uri.toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertNotNull(db.processingDao().source(imported.id))
            store.discardNewImport(imported.id)
            assertNull(db.processingDao().source(imported.id))
            assertArrayEquals(bytes,original.readBytes())
        } finally { original.delete();db.close() }
    }
    @Test fun missingSourceNeverBecomesSyntheticContent() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val store=SourceStore(context,db,WorkBudget())
            try { store.openRead(UUID.randomUUID().toString()); fail("Missing source accepted") }
            catch(expected: java.io.IOException) { assertEquals("SOURCE_NOT_FOUND",expected.message) }
        } finally { db.close() }
    }
    @Test fun reservedTrashMetadataFilenameRoundTripsWithoutChangingBytes()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val store=OutputStore(context,db,PublicOutputStore(context,UserPreferencesRepository(context)))
            val ticket=store.reserve(UUID.randomUUID().toString(),"reserved-name","record.json","application/octet-stream")
            val bytes="original payload, never trash metadata".toByteArray()
            ticket.stageFile.writeBytes(bytes)
            val record=store.publish(ticket) { OutputMetadata() }
            store.moveToTrash(record.id)
            assertNull(db.processingDao().outputById(record.id))
            assertTrue(store.refreshTrash().any { it.id==record.id })
            val restored=store.restoreFromTrash(record.id)!!
            assertEquals(record.sha256,restored.sha256)
            assertArrayEquals(bytes,context.contentResolver.openInputStream(android.net.Uri.parse(restored.uri))!!.use { it.readBytes() })
        } finally { db.close() }
    }
    @Test fun repeatedPublicationRevalidatesMissingPublishedBytes()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val store=OutputStore(context,db,PublicOutputStore(context,UserPreferencesRepository(context)))
            val ticket=store.reserve(UUID.randomUUID().toString(),"missing","missing.bin","application/octet-stream")
            ticket.stageFile.writeText("real")
            val record=store.publish(ticket) { OutputMetadata() }
            assertTrue(File(context.filesDir,"processing/results/${record.id}/${record.displayName}").delete())
            try { store.publish(ticket) { OutputMetadata() };fail("Missing output accepted as success") }
            catch(expected:IllegalArgumentException) { assertEquals("PUBLICATION_INTEGRITY_ERROR",expected.message) }
            finally { File(context.filesDir,"processing/results/${record.id}/${record.displayName}").writeText("real") }
        } finally { db.close() }
    }
    @Test fun repeatedPublicationHasOneIdentityAndRealDigest() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val store=OutputStore(context,db,PublicOutputStore(context,UserPreferencesRepository(context)))
            val ticket=store.reserve(UUID.randomUUID().toString(),"fixture","result.bin","application/octet-stream")
            ticket.stageFile.writeText("real bytes")
            val first=store.publish(ticket) { OutputMetadata() }
            val second=store.publish(ticket) { OutputMetadata() }
            assertEquals(first.id,second.id); assertEquals(10L,first.sizeBytes)
            assertEquals(64,first.sha256.length)
        } finally { db.close() }
    }
}
