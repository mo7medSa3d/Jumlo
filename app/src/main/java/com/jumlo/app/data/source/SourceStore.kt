package com.jumlo.app.data.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.local.*
import com.jumlo.app.model.SourceRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import java.io.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class SourceStore @Inject constructor(
    @ApplicationContext private val context:Context, private val db:AppDatabase, private val budget:WorkBudget
) {
    private val resolver get()=context.contentResolver
    private val inbound get()=File(context.filesDir,"inbound").apply { mkdirs() }
    private fun SourceEntity.ref()=SourceRef(id,uri,displayName,mimeType,sizeBytes,readable,writable,persistedReadGrant,modifiedAtEpochMs)
    val records=db.processingDao().sources().map { rows -> rows.map { it.ref() } }
    suspend fun get(id:String):SourceRef=db.processingDao().source(id)?.ref() ?: throw OperationFailure("SOURCE_NOT_FOUND")
    suspend fun inspect(uri:String,offeredGrantFlags:Int):SourceRef=withContext(Dispatchers.IO) {
        val parsed=Uri.parse(uri)
        if(parsed.scheme!="content") throw OperationFailure("CONTENT_URI_REQUIRED")
        val id=UUID.randomUUID().toString()
        var name="Document"; var size:Long?=null; var modified:Long?=null; var writable=false
        try {
            resolver.query(parsed,null,null,null,null)?.use { cursor ->
                if(cursor.moveToFirst()) {
                    fun string(column:String):String?=cursor.getColumnIndex(column).takeIf { it>=0 }?.let { if(cursor.isNull(it)) null else cursor.getString(it) }
                    name=string(OpenableColumns.DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: name
                    size=string(OpenableColumns.SIZE)?.toLongOrNull()?.takeIf { it>=0 }
                    modified=string(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.toLongOrNull()?.takeIf { it>0 }
                    val flags=string(DocumentsContract.Document.COLUMN_FLAGS)?.toLongOrNull() ?: 0L
                    writable=(offeredGrantFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0 && (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE.toLong())!=0L
                }
            }
            val offered=offeredGrantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if(offered!=0 && offeredGrantFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION!=0) {
                try { resolver.takePersistableUriPermission(parsed,offered) } catch(_:SecurityException) { /* Copy the transient grant instead. */ }
            }
            val persisted=resolver.persistedUriPermissions.any { it.uri==parsed && it.isReadPermission }
            var cached:String?=null
            run {
                val dest=File(inbound,"$id.bin"); val partial=File(inbound,"$id.part")
                try {
                    resolver.openInputStream(parsed)?.use { source ->
                        FileOutputStream(partial).use { sink -> boundedCopy(source,sink,partial); sink.fd.sync() }
                    } ?: throw OperationFailure("ACCESS_LOST")
                    java.nio.file.Files.move(partial.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    cached=dest.absolutePath; size=dest.length()
                } finally { partial.delete() }
            }
            val entity=SourceEntity(id,uri,name,resolver.getType(parsed),size,true,writable,persisted,modified,cached)
            db.processingDao().putSource(entity); entity.ref()
        } catch(cancel:CancellationException) { cleanupUnregistered(id,File(inbound,"$id.bin")); throw cancel }
        catch(e:Exception) { cleanupUnregistered(id,File(inbound,"$id.bin")); throw OperationFailure(stableError(e)) }
    }
    /** Imports an app-private local file (camera scan / drawn signature) as an immutable snapshot. */
    suspend fun importLocalFile(file:java.io.File,displayName:String,mimeType:String?):SourceRef=withContext(Dispatchers.IO) {
        require(file.isFile && file.length()>0) { "LOCAL_IMPORT_EMPTY" }
        val id=UUID.randomUUID().toString()
        val dest=File(inbound,"$id.bin"); val partial=File(inbound,"$id.part")
        try {
            file.inputStream().use { source -> FileOutputStream(partial).use { sink -> boundedCopy(source,sink,partial); sink.fd.sync() } }
            java.nio.file.Files.move(partial.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            val entity=SourceEntity(id,"local://$id",displayName,mimeType,dest.length(),true,false,false,System.currentTimeMillis(),dest.absolutePath)
            db.processingDao().putSource(entity); entity.ref()
        } catch(t:Throwable) { cleanupUnregistered(id,dest);throw t }
        finally { partial.delete() }
    }
    suspend fun openRead(sourceId:String):InputStream=withContext(Dispatchers.IO) {
        val source=db.processingDao().source(sourceId) ?: throw OperationFailure("SOURCE_NOT_FOUND")
        try {
            source.cachedPath?.let { path ->
                val f=File(path).canonicalFile
                if(!f.path.startsWith(inbound.canonicalPath+File.separator)) throw OperationFailure("INVALID_CACHED_SOURCE")
                return@withContext f.inputStream()
            }
            resolver.openInputStream(Uri.parse(source.uri)) ?: throw OperationFailure("ACCESS_LOST")
        } catch(cancel:CancellationException) { throw cancel }
        catch(e:Exception) { db.processingDao().accessLost(sourceId); throw OperationFailure(stableError(e)) }
    }
    suspend fun <T> withSeekable(sourceId:String,jobId:String,block:suspend (File)->T):T=withContext(Dispatchers.IO) {
        require(UUID.fromString(jobId).toString()==jobId)
        val dir=File(context.cacheDir,"seek/$jobId").apply { mkdirs() }
        val temp=File.createTempFile("source-",".bin",dir)
        try {
            openRead(sourceId).use { source -> FileOutputStream(temp).use { sink -> boundedCopy(source,sink,temp); sink.fd.sync() } }
            block(temp)
        } finally { temp.delete(); if(dir.list().isNullOrEmpty()) dir.delete() }
    }
    private suspend fun cleanupUnregistered(id:String,file:File) = withContext(NonCancellable+Dispatchers.IO) {
        // A cancelled DAO continuation may already have committed its immutable source row.
        // Keep the bytes if database state cannot be established.
        runCatching { if(db.processingDao().source(id)==null) file.delete() }
        Unit
    }
    private suspend fun boundedCopy(source:InputStream,sink:OutputStream,file:File) {
        var total=0L; val buffer=ByteArray(64*1024)
        while(true) {
            currentCoroutineContext().ensureActive(); val n=source.read(buffer); if(n<0) break
            if(n.toLong()>budget.maxTemporaryBytes-total || file.parentFile.usableSpace<budget.minFreeBytes+n) throw OperationFailure("DISK_BUDGET_EXCEEDED")
            if(n>0) { sink.write(buffer,0,n); total+=n }
        }
    }
}
