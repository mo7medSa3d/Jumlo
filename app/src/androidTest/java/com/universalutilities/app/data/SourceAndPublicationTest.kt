package com.universalutilities.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.universalutilities.app.data.jobs.WorkBudget
import com.universalutilities.app.data.local.AppDatabase
import com.universalutilities.app.data.output.OutputStore
import com.universalutilities.app.data.source.SourceStore
import com.universalutilities.app.model.OutputMetadata
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
    @Test fun missingSourceNeverBecomesSyntheticContent() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val store=SourceStore(context,db,WorkBudget())
            try { store.openRead(UUID.randomUUID().toString()); fail("Missing source accepted") }
            catch(expected: java.io.IOException) { assertEquals("SOURCE_NOT_FOUND",expected.message) }
        } finally { db.close() }
    }
    @Test fun repeatedPublicationHasOneIdentityAndRealDigest() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            val store=OutputStore(context,db)
            val ticket=store.reserve(UUID.randomUUID().toString(),"fixture","result.bin","application/octet-stream")
            ticket.stageFile.writeText("real bytes")
            val first=store.publish(ticket) { OutputMetadata() }
            val second=store.publish(ticket) { OutputMetadata() }
            assertEquals(first.id,second.id); assertEquals(10L,first.sizeBytes)
            assertEquals(64,first.sha256.length)
        } finally { db.close() }
    }
}
