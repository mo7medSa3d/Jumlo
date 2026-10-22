package com.jumlo.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.processing.*
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.data.files.EncryptedZip
import okhttp3.OkHttpClient

@RunWith(AndroidJUnit4::class)
class ArchiveHandlerTest {
    private val context:Context=ApplicationProvider.getApplicationContext()
    @Test fun archiveContainsImportedBytesInsteadOfDisplayText()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val sourceStore=SourceStore(context,db,WorkBudget())
            val outputs=OutputStore(context,db,PublicOutputStore(context,UserPreferencesRepository(context)))
            val source=File(context.filesDir,"processing/results/fixture/archive-input.bin").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(0,1,2,127,-1)) }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",source)
            val ref=sourceStore.inspect(uri.toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val job=JobRequest(UUID.randomUUID().toString(),"file_07",listOf(ref.id),1,Json.encodeToString(ArchiveSettings()),System.currentTimeMillis())
            val outcomes=ArchiveOperationHandler(context).execute(job,ExecutionContext(sourceStore,outputs,WorkBudget(),{},{}))
            assertEquals(1,outcomes.size); assertEquals(ItemStatus.CREATED,outcomes.single().status)
            val result=outputs.find(job.id,"archive")!!
            context.contentResolver.openInputStream(android.net.Uri.parse(result.uri))!!.use { stream -> ZipInputStream(stream).use { zip ->
                assertEquals(source.name,zip.nextEntry.name); assertArrayEquals(source.readBytes(),zip.readBytes()); assertNull(zip.nextEntry)
            } }
            assertArrayEquals(byteArrayOf(0,1,2,127,-1),source.readBytes())
        } finally { db.close() }
    }

    @Test fun archiveRestartsFromAnInterruptedPrivateStage()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val sources=SourceStore(context,db,WorkBudget())
            val outputs=OutputStore(context,db,PublicOutputStore(context,UserPreferencesRepository(context)))
            val source=File(context.filesDir,"processing/results/fixture/restart-${UUID.randomUUID()}.txt").apply {
                parentFile.mkdirs();writeText("all original bytes")
            }
            val ref=sources.inspect(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val job=JobRequest(UUID.randomUUID().toString(),"file_07",listOf(ref.id),1,Json.encodeToString(ArchiveSettings()),System.currentTimeMillis())
            outputs.reserve(job.id,"archive","Archive.zip","application/zip").stageFile.writeText("PK interrupted bytes")
            val outcomes=ArchiveOperationHandler(context).execute(job,ExecutionContext(sources,outputs,WorkBudget(),{},{}))
            assertEquals(ItemStatus.CREATED,outcomes.single().status)
            val record=outputs.find(job.id,"archive")!!
            context.contentResolver.openInputStream(android.net.Uri.parse(record.uri))!!.use { stream -> ZipInputStream(stream).use { zip ->
                assertNotNull(zip.nextEntry);assertEquals("all original bytes",zip.readBytes().toString(Charsets.UTF_8));assertNull(zip.nextEntry)
            } }
        } finally { db.close() }
    }

    @Test fun aesArchiveIsPublishedWithVerifiedMetadataAndRoundTrips()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val secretStore=EphemeralSecretStore()
        val password="ephemeral test password".toCharArray()
        val verifyDir=File(context.cacheDir,"encrypted-handler-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val sources=SourceStore(context,db,WorkBudget())
            val outputs=OutputStore(context,db,PublicOutputStore(context,UserPreferencesRepository(context)))
            val source=File(context.filesDir,"processing/results/fixture/encrypted-${UUID.randomUUID()}.bin").apply {
                parentFile.mkdirs();writeBytes(byteArrayOf(0,1,2,127,-1))
            }
            val ref=sources.inspect(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val settings=EncryptedArchiveSettings(secretHandle=secretStore.put(password))
            val job=JobRequest(UUID.randomUUID().toString(),"file_encrypted_zip",listOf(ref.id),1,Json.encodeToString(settings),System.currentTimeMillis())
            val outcomes=FileExtrasHandler(secretStore,OkHttpClient()).execute(job,ExecutionContext(sources,outputs,WorkBudget(),{},{}))
            assertEquals(ItemStatus.CREATED,outcomes.single().status)
            val record=outputs.find(job.id,"archive")!!
            assertEquals(1,record.metadata.entryCount)
            assertEquals(source.length(),record.metadata.originalSizeBytes)
            val archive=File(verifyDir,"published.zip")
            context.contentResolver.openInputStream(android.net.Uri.parse(record.uri))!!.use { input -> archive.outputStream().use { input.copyTo(it) } }
            val unpacked=EncryptedZip.extract(archive,File(verifyDir,"unpacked"),password)
            assertArrayEquals(source.readBytes(),unpacked.single().readBytes())
        } finally { password.fill('\u0000');secretStore.clear();verifyDir.deleteRecursively();db.close() }
    }
}
