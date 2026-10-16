package com.universalutilities.app.data.output

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.universalutilities.app.core.*
import com.universalutilities.app.data.local.*
import com.universalutilities.app.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class OutputStore @Inject constructor(@ApplicationContext private val context:Context,private val db:AppDatabase) {
    private val publisher=DurablePublication(File(context.filesDir,"processing"))
    val records=db.processingDao().outputs().map { rows -> rows.map { Json.decodeFromString<OutputRecord>(it.recordJson) } }
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
        publisher.reconcile().forEach { file -> if(find(file.jobId,file.itemKey)==null) persist(file,Json.decodeFromString<OutputMetadata>(file.metadataJson)) }
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
