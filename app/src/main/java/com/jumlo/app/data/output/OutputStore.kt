package com.jumlo.app.data.output

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.jumlo.app.core.*
import com.jumlo.app.data.local.*
import com.jumlo.app.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class OutputStore @Inject constructor(@ApplicationContext private val context:Context,private val db:AppDatabase) {
    private val rootDir get()=File(context.filesDir,"processing")
    private val resultsDir get()=File(rootDir,"results").apply { mkdirs() }
    private val journalDir get()=File(rootDir,"journal").apply { mkdirs() }
    private val trashDir get()=File(rootDir,"trash").apply { mkdirs() }
    private val publisher=DurablePublication(File(context.filesDir,"processing"))
    val records=db.processingDao().outputs().map { rows -> rows.map { Json.decodeFromString<OutputRecord>(it.recordJson) } }
    private val _trashed=MutableStateFlow<List<OutputRecord>>(emptyList())
    val trashed=_trashed.asStateFlow()

    suspend fun find(job:String,key:String):OutputRecord?=db.processingDao().output(job,key)?.let { Json.decodeFromString(it.recordJson) }
    suspend fun reserve(jobId:String,itemKey:String,displayName:String,mimeType:String):OutputTicket=withContext(Dispatchers.IO) {
        publisher.reserve(jobId,itemKey,displayName,mimeType).let { OutputTicket(it.jobId,it.itemKey,it.stageFile,it.displayName,it.mimeType) }
    }
    suspend fun publish(ticket:OutputTicket,validate:suspend (File)->OutputMetadata):OutputRecord=withContext(Dispatchers.IO) {
        val existing=find(ticket.jobId,ticket.itemKey)
        if(existing!=null && !ticket.stageFile.exists()) return@withContext existing
        currentCoroutineContext().ensureActive()
        val metadata=validate(ticket.stageFile)
        currentCoroutineContext().ensureActive()
        val file=publisher.publish(PublicationTicket(ticket.jobId,ticket.itemKey,ticket.stageFile,ticket.displayName,ticket.mimeType,Json.encodeToString(metadata)))
        persist(file,metadata)
    }
    private suspend fun persist(file:PublishedFile,metadata:OutputMetadata):OutputRecord {
        val record=OutputRecord(file.id,file.jobId,file.itemKey,FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file.file).toString(),file.displayName,file.mimeType,file.sizeBytes,file.sha256,metadata,file.createdAtEpochMs)
        db.processingDao().insertOutput(OutputEntity(record.id,record.jobId,record.itemKey,Json.encodeToString(record)))
        val found=find(record.jobId,record.itemKey) ?: error("OUTPUT_DATABASE_FAILED")
        check(found.sha256==record.sha256) { "OUTPUT_DIGEST_CONFLICT" }
        return found
    }
    suspend fun discardIncomplete(jobId:String):Unit=withContext(Dispatchers.IO) { publisher.discardIncomplete(jobId); Unit }
    suspend fun reconcile():Unit=withContext(Dispatchers.IO) {
        val trashedIds=trashDir.listFiles { f -> f.isDirectory }?.map { it.name }?.toSet().orEmpty()
        publisher.reconcile().filter { it.id !in trashedIds }.forEach { file ->
            if(find(file.jobId,file.itemKey)==null) persist(file,Json.decodeFromString<OutputMetadata>(file.metadataJson))
        }
        refreshTrash()
    }
    /** Moves a published output into app-managed trash so it can be restored (Undo) or purged. */
    suspend fun moveToTrash(outputId:String):OutputRecord?=withContext(Dispatchers.IO) {
        val row=db.processingDao().outputById(outputId) ?: return@withContext null
        val record=Json.decodeFromString<OutputRecord>(row.recordJson)
        val srcFolder=File(resultsDir,record.id)
        val dstFolder=File(trashDir,record.id)
        dstFolder.mkdirs()
        val srcFile=File(srcFolder,record.displayName)
        val dstFile=File(dstFolder,record.displayName)
        if(srcFile.exists()) {
            Files.move(srcFile.toPath(),dstFile.toPath(),StandardCopyOption.REPLACE_EXISTING)
            srcFolder.delete()
        }
        val journalKey=Hashing.sha256(java.io.ByteArrayInputStream("${record.jobId}\u0000${record.itemKey}".toByteArray()))
        val journalFile=File(journalDir,"$journalKey.properties")
        if(journalFile.exists()) Files.move(journalFile.toPath(),File(dstFolder,"journal.properties").toPath(),StandardCopyOption.REPLACE_EXISTING)
        File(dstFolder,"record.json").writeText(row.recordJson)
        db.processingDao().deleteOutput(outputId)
        refreshTrash()
        record
    }
    /** Restores a trashed output back to active results (used by Undo and Trash restore). */
    suspend fun restoreFromTrash(outputId:String):OutputRecord?=withContext(Dispatchers.IO) {
        val dstFolder=File(trashDir,outputId)
        val metaFile=File(dstFolder,"record.json")
        if(!metaFile.isFile) return@withContext null
        val record=Json.decodeFromString<OutputRecord>(metaFile.readText())
        val trashedBin=File(dstFolder,record.displayName)
        if(!trashedBin.isFile) return@withContext null
        val resFolder=File(resultsDir,record.id).apply { mkdirs() }
        val resFile=File(resFolder,record.displayName)
        Files.move(trashedBin.toPath(),resFile.toPath(),StandardCopyOption.REPLACE_EXISTING)
        val journalBackup=File(dstFolder,"journal.properties")
        val journalKey=Hashing.sha256(java.io.ByteArrayInputStream("${record.jobId}\u0000${record.itemKey}".toByteArray()))
        if(journalBackup.isFile) Files.move(journalBackup.toPath(),File(journalDir,"$journalKey.properties").toPath(),StandardCopyOption.REPLACE_EXISTING)
        dstFolder.deleteRecursively()
        db.processingDao().insertOutput(OutputEntity(record.id,record.jobId,record.itemKey,Json.encodeToString(record)))
        refreshTrash()
        record
    }
    /** Permanently deletes one trashed item or all trashed items older than [cutoffEpochMs]. */
    suspend fun purgeTrash(outputId:String?=null,cutoffEpochMs:Long=Long.MAX_VALUE):Int=withContext(Dispatchers.IO) {
        var purged=0
        val folders=if(outputId!=null) listOf(File(trashDir,outputId)) else trashDir.listFiles { f -> f.isDirectory }.orEmpty().toList()
        folders.forEach { folder ->
            if(folder.exists() && (outputId!=null || folder.lastModified()<=cutoffEpochMs)) {
                if(folder.deleteRecursively()) purged++
            }
        }
        refreshTrash()
        purged
    }
    suspend fun refreshTrash():List<OutputRecord> = withContext(Dispatchers.IO) {
        val items=trashDir.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { folder ->
            runCatching { Json.decodeFromString<OutputRecord>(File(folder,"record.json").readText()) }.getOrNull()
        }.sortedByDescending { it.createdAtEpochMs }
        _trashed.value=items
        items
    }
    suspend fun inspect(file:File,mime:String):OutputMetadata=withContext(Dispatchers.IO) {
        require(file.isFile) { "OUTPUT_MISSING" }
        when {
            mime=="application/pdf" -> ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> require(renderer.pageCount>0) { "EMPTY_PDF" }; OutputMetadata(pageCount=renderer.pageCount) } }
            mime.startsWith("image/") -> {
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth>0 && options.outHeight>0) { "IMAGE_VERIFY_FAILED" }
                OutputMetadata(width=options.outWidth,height=options.outHeight)
            }
            mime=="application/zip" -> ZipFile(file).use { OutputMetadata(entryCount=it.size()) }
            else -> OutputMetadata()
        }
    }
}
