package com.jumlo.app.data.output

import com.jumlo.app.data.pdf.PdfRendererFactory

import android.content.Context
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.local.*
import com.jumlo.app.diagnostics.AppDiagnostics
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
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class OutputStore internal constructor(private val context:Context,private val db:AppDatabase,private val publicOutputs:PublicOutputPublisher) {
    @Inject constructor(@ApplicationContext context:Context,db:AppDatabase,publicOutputs:PublicOutputStore) :
        this(context,db,publicOutputs as PublicOutputPublisher)
    private val rootDir get()=File(context.filesDir,"processing")
    private val resultsDir get()=File(rootDir,"results").apply { mkdirs() }
    private val journalDir get()=File(rootDir,"journal").apply { mkdirs() }
    private val trashDir get()=File(rootDir,"trash").apply { mkdirs() }
    private val trashStorage=ManagedTrashStorage(rootDir)
    private val mutations=Mutex()
    private val publisher=DurablePublication(File(context.filesDir,"processing"))
    private val json=Json { ignoreUnknownKeys=true }
    val records=db.processingDao().outputs().map { rows -> rows.map { json.decodeFromString<OutputRecord>(it.recordJson) } }
    private val _trashed=MutableStateFlow<List<OutputRecord>>(emptyList())
    val trashed=_trashed.asStateFlow()

    suspend fun find(job:String,key:String):OutputRecord?=withContext(Dispatchers.IO) { db.processingDao().output(job,key)?.let {
        json.decodeFromString<OutputRecord>(it.recordJson).also { record ->
            val file=File(File(resultsDir,record.id),SafeNames.basename(record.displayName))
            require(file.isFile && file.length()==record.sizeBytes && Hashing.sha256(file)==record.sha256) { "PUBLICATION_INTEGRITY_ERROR" }
        }
    } }
    suspend fun reserve(jobId:String,itemKey:String,displayName:String,mimeType:String):OutputTicket=withContext(Dispatchers.IO) {
        publisher.reserve(jobId,itemKey,displayName,mimeType).let { OutputTicket(it.jobId,it.itemKey,it.stageFile,it.displayName,it.mimeType) }
    }
    suspend fun publish(ticket:OutputTicket,validate:suspend (File)->OutputMetadata):OutputRecord=withContext(Dispatchers.IO) { mutations.withLock {
        val existing=find(ticket.jobId,ticket.itemKey)
        if(existing!=null && !ticket.stageFile.exists()) return@withLock existing
        currentCoroutineContext().ensureActive()
        val metadata=validate(ticket.stageFile)
        currentCoroutineContext().ensureActive()
        val file=publisher.publish(PublicationTicket(ticket.jobId,ticket.itemKey,ticket.stageFile,ticket.displayName,ticket.mimeType,json.encodeToString(metadata)))
        persist(file,metadata)
    } }
    private suspend fun persist(file:PublishedFile,metadata:OutputMetadata):OutputRecord {
        val privateRecord=OutputRecord(file.id,file.jobId,file.itemKey,FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file.file).toString(),file.displayName,file.mimeType,file.sizeBytes,file.sha256,metadata,file.createdAtEpochMs)
        // Make the verified private bytes discoverable before contacting a provider.
        db.processingDao().insertOutput(OutputEntity(privateRecord.id,privateRecord.jobId,privateRecord.itemKey,json.encodeToString(privateRecord)))
        val found=find(privateRecord.jobId,privateRecord.itemKey) ?: error("OUTPUT_DATABASE_FAILED")
        check(found.sha256==privateRecord.sha256 && found.id==privateRecord.id) { "OUTPUT_DIGEST_CONFLICT" }
        return if(found.publicUri.isNullOrBlank()) recordPublicCopy(found,file.file) else found
    }
    private suspend fun withPublicCopy(record:OutputRecord,file:File):OutputRecord = try {
        val uri=publicOutputs.publish(file,record.displayName,record.mimeType,record.sha256,record.metadata.subfolder)
        record.copy(publicUri=uri,publicSaveError=null)
    } catch(cancelled:CancellationException) { throw cancelled }
    catch(error:Exception) {
        // Keep the verified private file discoverable and retain an actionable retry state.
        record.copy(publicUri=null,publicSaveError=stableError(error))
    }
    private suspend fun recordPublicCopy(record:OutputRecord,file:File):OutputRecord {
        val copied=withPublicCopy(record,file)
        // Once the provider has committed a copy, coroutine cancellation must not
        // separate it from its Room row. This does not claim force-kill atomicity
        // across the provider and app database, which are separate storage systems.
        withContext(NonCancellable) {
            try {
                require(db.processingDao().updateOutputRecord(copied.id,json.encodeToString(copied))==1) { "OUTPUT_DATABASE_FAILED" }
            } catch(error:Throwable) {
                if(copied.publicUri!=record.publicUri) {
                    try { publicOutputs.deleteIfOwned(copied.publicUri) }
                    catch(cleanup:Throwable) { AppDiagnostics.report("publication_rollback",cleanup) }
                }
                throw error
            }
        }
        currentCoroutineContext().ensureActive()
        return copied
    }
    /** Retry just the visible copy, preserving the private bytes, identity and result row. */
    suspend fun retryPublicSaves(jobId:String):Unit=withContext(Dispatchers.IO) { mutations.withLock {
        for(row in db.processingDao().outputsForJob(jobId)) {
            currentCoroutineContext().ensureActive()
            val record=json.decodeFromString<OutputRecord>(row.recordJson)
            if(!record.publicUri.isNullOrBlank()) continue
            val verified=find(record.jobId,record.itemKey) ?: continue
            val file=File(File(resultsDir,verified.id),SafeNames.basename(verified.displayName))
            recordPublicCopy(verified,file)
        }
    } }
    /** A created private file with no visible copy is a partial save, never chosen-folder success. */
    suspend fun reportPublicSaveFailures(outcomes:List<ItemOutcome>):List<ItemOutcome> = outcomes.map { outcome ->
        if(outcome.outputId==null || outcome.status in setOf(ItemStatus.FAILED,ItemStatus.CANCELLED)) outcome
        else {
            val record=db.processingDao().outputById(outcome.outputId)?.let { json.decodeFromString<OutputRecord>(it.recordJson) }
            if(record!=null && record.publicUri.isNullOrBlank()) outcome.copy(status=ItemStatus.FAILED,errorCode="DESTINATION_SAVE_FAILED")
            else outcome
        }
    }
    suspend fun discardIncomplete(jobId:String):Unit=withContext(Dispatchers.IO) { publisher.discardIncomplete(jobId); Unit }
    private fun trashEntry(record:OutputRecord)=TrashEntry(record.id,record.displayName,record.sizeBytes,record.sha256,json.encodeToString(record),
        Hashing.sha256(java.io.ByteArrayInputStream("${record.jobId}\u0000${record.itemKey}".toByteArray())))
    private fun trashRecord(entry:TrashEntry):OutputRecord = json.decodeFromString<OutputRecord>(entry.recordJson).also {
        require(trashEntry(it)==entry) { "INVALID_TRASH_RECORD" }
    }
    private fun migrateLegacyTrash() {
        trashDir.listFiles { f -> f.isDirectory }.orEmpty().forEach { folder ->
            val legacy=File(folder,"record.json")
            if(!File(folder,"entry.properties").exists() && legacy.isFile) {
                val record=json.decodeFromString<OutputRecord>(legacy.readText())
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
                val privateRecord=record.copy(publicUri=null,publicSaveError=null)
                db.processingDao().insertOutput(OutputEntity(record.id,record.jobId,record.itemKey,json.encodeToString(privateRecord)))
                trashStorage.acknowledgeRestore(record.id)
            } else db.processingDao().deleteOutput(record.id)
        }
    }
    suspend fun reconcile():Unit=withContext(Dispatchers.IO) { mutations.withLock {
        recoverTrash()
        publisher.reconcile().forEach { file ->
            if(find(file.jobId,file.itemKey)==null) persist(file,json.decodeFromString<OutputMetadata>(file.metadataJson))
        }
        refreshTrashUnlocked();Unit
    } }
    /** Durable intent precedes payload movement; recovery completes interrupted transitions. */
    suspend fun moveToTrash(outputId:String):OutputRecord?=withContext(Dispatchers.IO) { mutations.withLock {
        val row=db.processingDao().outputById(outputId) ?: return@withLock null
        val record=json.decodeFromString<OutputRecord>(row.recordJson)
        require(record.id==outputId) { "INVALID_TRASH_RECORD" }
        trashStorage.move(trashEntry(record))
        runCatching { publicOutputs.deleteIfOwned(record.publicUri) }
        db.processingDao().deleteOutput(outputId)
        refreshTrashUnlocked();record
    } }
    suspend fun restoreFromTrash(outputId:String):OutputRecord?=withContext(Dispatchers.IO) { mutations.withLock {
        migrateLegacyTrash()
        val record=trashStorage.restore(outputId)?.let(::trashRecord) ?: return@withLock null
        val privateRecord=record.copy(publicUri=null,publicSaveError=null)
        withContext(NonCancellable) {
            db.processingDao().insertOutput(OutputEntity(privateRecord.id,privateRecord.jobId,privateRecord.itemKey,json.encodeToString(privateRecord)))
            trashStorage.acknowledgeRestore(outputId)
        }
        val restored=recordPublicCopy(privateRecord,File(File(resultsDir,record.id),SafeNames.basename(record.displayName)))
        refreshTrashUnlocked();restored
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
    suspend fun inspect(file:File,mime:String,originalSizeBytes:Long?=null,subfolder:String?=null):OutputMetadata=withContext(Dispatchers.IO) {
        require(file.isFile) { "OUTPUT_MISSING" }
        val coroutine = currentCoroutineContext()
        when {
            mime=="application/pdf" -> ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRendererFactory.open(fd, context.cacheDir) { coroutine.ensureActive() }
                renderer.use { r ->
                    require(r.pageCount>0) { "EMPTY_PDF" }
                    OutputMetadata(pageCount=r.pageCount,originalSizeBytes=originalSizeBytes,subfolder=subfolder)
                }
            }
            mime.startsWith("image/") -> {
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth>0 && options.outHeight>0) { "IMAGE_VERIFY_FAILED" }
                OutputMetadata(width=options.outWidth,height=options.outHeight,originalSizeBytes=originalSizeBytes,subfolder=subfolder)
            }
            mime=="application/zip" -> ZipFile(file).use { OutputMetadata(entryCount=it.size(),originalSizeBytes=originalSizeBytes,subfolder=subfolder) }
            else -> OutputMetadata(originalSizeBytes=originalSizeBytes,subfolder=subfolder)
        }
    }
}
