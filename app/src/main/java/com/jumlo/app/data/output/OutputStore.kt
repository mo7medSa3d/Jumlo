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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val trashStorage=ManagedTrashStorage(rootDir)
    private val mutations=Mutex()
    private val publisher=DurablePublication(File(context.filesDir,"processing"))
    val records=db.processingDao().outputs().map { rows -> rows.map { Json.decodeFromString<OutputRecord>(it.recordJson) } }
    private val _trashed=MutableStateFlow<List<OutputRecord>>(emptyList())
    val trashed=_trashed.asStateFlow()

    suspend fun find(job:String,key:String):OutputRecord?=db.processingDao().output(job,key)?.let {
        Json.decodeFromString<OutputRecord>(it.recordJson).also { record ->
            val file=File(File(resultsDir,record.id),SafeNames.basename(record.displayName))
            require(file.isFile && file.length()==record.sizeBytes && Hashing.sha256(file)==record.sha256) { "PUBLICATION_INTEGRITY_ERROR" }
        }
    }
    suspend fun reserve(jobId:String,itemKey:String,displayName:String,mimeType:String):OutputTicket=withContext(Dispatchers.IO) {
        publisher.reserve(jobId,itemKey,displayName,mimeType).let { OutputTicket(it.jobId,it.itemKey,it.stageFile,it.displayName,it.mimeType) }
    }
    suspend fun publish(ticket:OutputTicket,validate:suspend (File)->OutputMetadata):OutputRecord=withContext(Dispatchers.IO) { mutations.withLock {
        val existing=find(ticket.jobId,ticket.itemKey)
        if(existing!=null && !ticket.stageFile.exists()) return@withLock existing
        currentCoroutineContext().ensureActive()
        val metadata=validate(ticket.stageFile)
        currentCoroutineContext().ensureActive()
        val file=publisher.publish(PublicationTicket(ticket.jobId,ticket.itemKey,ticket.stageFile,ticket.displayName,ticket.mimeType,Json.encodeToString(metadata)))
        persist(file,metadata)
    } }
    private suspend fun persist(file:PublishedFile,metadata:OutputMetadata):OutputRecord {
        val record=OutputRecord(file.id,file.jobId,file.itemKey,FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file.file).toString(),file.displayName,file.mimeType,file.sizeBytes,file.sha256,metadata,file.createdAtEpochMs)
        db.processingDao().insertOutput(OutputEntity(record.id,record.jobId,record.itemKey,Json.encodeToString(record)))
        val found=find(record.jobId,record.itemKey) ?: error("OUTPUT_DATABASE_FAILED")
        check(found.sha256==record.sha256) { "OUTPUT_DIGEST_CONFLICT" }
        return found
    }
    suspend fun discardIncomplete(jobId:String):Unit=withContext(Dispatchers.IO) { publisher.discardIncomplete(jobId); Unit }
    private fun trashEntry(record:OutputRecord)=TrashEntry(record.id,record.displayName,record.sizeBytes,record.sha256,Json.encodeToString(record),
        Hashing.sha256(java.io.ByteArrayInputStream("${record.jobId}\u0000${record.itemKey}".toByteArray())))
    private fun trashRecord(entry:TrashEntry):OutputRecord = Json.decodeFromString<OutputRecord>(entry.recordJson).also {
        require(trashEntry(it)==entry) { "INVALID_TRASH_RECORD" }
    }
    private fun migrateLegacyTrash() {
        trashDir.listFiles { f -> f.isDirectory }.orEmpty().forEach { folder ->
            val legacy=File(folder,"record.json")
            if(!File(folder,"entry.properties").exists() && legacy.isFile) {
                val record=Json.decodeFromString<OutputRecord>(legacy.readText())
                require(folder.name==record.id) { "INVALID_TRASH_RECORD" }
                trashStorage.importLegacy(trashEntry(record))
            }
        }
    }
    private suspend fun recoverTrash() {
        migrateLegacyTrash()
        trashStorage.recover().forEach { recovered ->
            val record=trashRecord(recovered.entry)
            if(recovered.restored) {
                db.processingDao().insertOutput(OutputEntity(record.id,record.jobId,record.itemKey,Json.encodeToString(record)))
                trashStorage.acknowledgeRestore(record.id)
            } else db.processingDao().deleteOutput(record.id)
        }
    }
    suspend fun reconcile():Unit=withContext(Dispatchers.IO) { mutations.withLock {
        recoverTrash()
        publisher.reconcile().forEach { file ->
            if(find(file.jobId,file.itemKey)==null) persist(file,Json.decodeFromString<OutputMetadata>(file.metadataJson))
        }
        refreshTrashUnlocked();Unit
    } }
    /** Durable intent precedes payload movement; recovery completes interrupted transitions. */
    suspend fun moveToTrash(outputId:String):OutputRecord?=withContext(Dispatchers.IO) { mutations.withLock {
        val row=db.processingDao().outputById(outputId) ?: return@withLock null
        val record=Json.decodeFromString<OutputRecord>(row.recordJson)
        require(record.id==outputId) { "INVALID_TRASH_RECORD" }
        trashStorage.move(trashEntry(record))
        db.processingDao().deleteOutput(outputId)
        refreshTrashUnlocked();record
    } }
    suspend fun restoreFromTrash(outputId:String):OutputRecord?=withContext(Dispatchers.IO) { mutations.withLock {
        migrateLegacyTrash()
        val record=trashStorage.restore(outputId)?.let(::trashRecord) ?: return@withLock null
        db.processingDao().insertOutput(OutputEntity(record.id,record.jobId,record.itemKey,Json.encodeToString(record)))
        trashStorage.acknowledgeRestore(outputId)
        refreshTrashUnlocked();record
    } }
    /** Only the exact IDs shown in a confirmation may be purged; new trash is never included. */
    suspend fun purgeTrashIds(outputIds:Set<String>):Int=withContext(Dispatchers.IO) { mutations.withLock {
        migrateLegacyTrash()
        val records=trashStorage.entries().associateBy { it.id }
        outputIds.forEach { id -> records[id]?.let(::trashRecord) }
        val purged=trashStorage.purge(outputIds.toSet())
        refreshTrashUnlocked();purged
    } }
    /** Compatibility for callers deleting one explicitly selected item; bulk deletion is refused. */
    suspend fun purgeTrash(outputId:String?=null,cutoffEpochMs:Long=Long.MAX_VALUE):Int {
        require(outputId!=null) { "EXPLICIT_TRASH_IDS_REQUIRED" }
        return purgeTrashIds(setOf(outputId))
    }
    private fun refreshTrashUnlocked():List<OutputRecord> {
        val items=trashStorage.entries().map(::trashRecord).sortedByDescending { it.createdAtEpochMs }
        _trashed.value=items
        return items
    }
    suspend fun refreshTrash():List<OutputRecord> = withContext(Dispatchers.IO) { mutations.withLock {
        recoverTrash();refreshTrashUnlocked()
    } }
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
