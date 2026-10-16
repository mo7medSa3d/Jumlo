package com.universalutilities.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.universalutilities.app.data.jobs.*
import com.universalutilities.app.data.local.AppDatabase
import com.universalutilities.app.data.output.OutputStore
import com.universalutilities.app.data.processing.*
import com.universalutilities.app.data.source.SourceStore
import com.universalutilities.app.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ArchiveHandlerTest {
    private val context:Context=ApplicationProvider.getApplicationContext()
    @Test fun archiveContainsImportedBytesInsteadOfDisplayText()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val sourceStore=SourceStore(context,db,WorkBudget())
            val outputs=OutputStore(context,db)
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
}
