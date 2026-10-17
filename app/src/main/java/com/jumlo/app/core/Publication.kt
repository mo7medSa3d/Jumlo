package com.jumlo.app.core

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class PublicationTicket(val jobId: String,val itemKey: String,val stageFile: File,val displayName: String,val mimeType: String,val metadataJson: String="{}")
data class PublishedFile(val id: String,val jobId: String,val itemKey: String,val file: File,val displayName: String,val mimeType: String,val sizeBytes: Long,val sha256: String,val createdAtEpochMs: Long,val metadataJson: String="{}")

/** Recoverable app-private publication. Room records are reconciled from this durable journal. */
class DurablePublication(private val root: File) {
    companion object { private val locks=ConcurrentHashMap<String,ReentrantLock>() }
    private val lock=locks.getOrPut(root.canonicalPath) { ReentrantLock() }
    private val staging=File(root,"staging")
    private val journal=File(root,"journal")
    private val results=File(root,"results")
    init { require(root.mkdirs() || root.isDirectory); journal.mkdirs(); results.mkdirs(); staging.mkdirs() }
    private fun identity(job: String,key: String): String {
        require(UUID.fromString(job).toString()==job && key.isNotBlank() && key.length<=512) { "INVALID_OUTPUT_IDENTITY" }
        return Hashing.sha256(ByteArrayInputStream("$job\u0000$key".toByteArray()))
    }
    private fun <T> locked(block: () -> T): T = lock.withLock {
        RandomAccessFile(File(root,"publication.lock"),"rw").use { f -> f.channel.lock().use { block() } }
    }
    fun reserve(job: String,key: String,name: String,mime: String): PublicationTicket = locked {
        val id=identity(job,key); SafeNames.basename(name)
        require(mime.isNotBlank() && mime.length<200 && mime.none { it<' ' }) { "INVALID_MIME_TYPE" }
        val parent=File(staging,job); require(parent.mkdirs() || parent.isDirectory)
        PublicationTicket(job,key,File(parent,"$id.part"),name,mime)
    }
    private fun checked(ticket: PublicationTicket): String {
        val id=identity(ticket.jobId,ticket.itemKey)
        require(ticket.stageFile.canonicalFile==File(File(staging,ticket.jobId),"$id.part").canonicalFile) { "INVALID_STAGING_PATH" }
        SafeNames.basename(ticket.displayName)
        require(ticket.metadataJson.length<=16384) { "OUTPUT_METADATA_TOO_LARGE" }
        return id
    }
    fun prepare(ticket: PublicationTicket): PublishedFile = locked { prepareLocked(ticket) }
    private fun prepareLocked(ticket: PublicationTicket): PublishedFile {
        val key=checked(ticket); val log=File(journal,"$key.properties")
        if(log.exists()) {
            val record=read(log)
            check(record.jobId==ticket.jobId && record.itemKey==ticket.itemKey && record.displayName==ticket.displayName && record.mimeType==ticket.mimeType && record.metadataJson==ticket.metadataJson) { "OUTPUT_IDENTITY_CONFLICT" }
            if(ticket.stageFile.exists() && Hashing.sha256(ticket.stageFile)!=record.sha256) {
                // This is disposable job staging, never the source or published result.
                check(ticket.stageFile.delete()) { "CONFLICT_CLEANUP_FAILED" }
                error("OUTPUT_DIGEST_CONFLICT")
            }
            return record
        }
        require(ticket.stageFile.isFile) { "OUTPUT_MISSING" }
        // Caller has already validated format. Empty files can be legitimate downloaded/archive members.
        RandomAccessFile(ticket.stageFile,"rw").use { it.fd.sync() }
        val recordId=UUID.randomUUID().toString()
        val record=PublishedFile(recordId,ticket.jobId,ticket.itemKey,File(File(results,recordId),ticket.displayName),ticket.displayName,ticket.mimeType,ticket.stageFile.length(),Hashing.sha256(ticket.stageFile),System.currentTimeMillis(),ticket.metadataJson)
        val data=Properties().apply {
            setProperty("id",record.id); setProperty("job",record.jobId); setProperty("key",record.itemKey)
            setProperty("name",record.displayName); setProperty("mime",record.mimeType)
            setProperty("metadata",record.metadataJson)
            setProperty("size",record.sizeBytes.toString()); setProperty("sha256",record.sha256); setProperty("created",record.createdAtEpochMs.toString())
        }
        val temp=File(journal,"$key.pending")
        FileOutputStream(temp).use { data.store(it,"Jumlo publication v1"); it.fd.sync() }
        Files.move(temp.toPath(),log.toPath(),StandardCopyOption.ATOMIC_MOVE)
        return record
    }
    fun publish(ticket: PublicationTicket): PublishedFile = locked {
        val record=prepareLocked(ticket)
        finish(record,ticket.stageFile)
        record
    }
    private fun finish(record: PublishedFile,stage: File) {
        if(!record.file.exists()) {
            check(stage.isFile && stage.length()==record.sizeBytes && Hashing.sha256(stage)==record.sha256) { "PUBLICATION_INTEGRITY_ERROR" }
            require(record.file.parentFile.mkdirs() || record.file.parentFile.isDirectory)
            Files.move(stage.toPath(),record.file.toPath(),StandardCopyOption.ATOMIC_MOVE)
        }
        check(record.file.isFile && record.file.length()==record.sizeBytes && Hashing.sha256(record.file)==record.sha256) { "PUBLICATION_INTEGRITY_ERROR" }
        if(stage.exists()) {
            check(Hashing.sha256(stage)==record.sha256) { "OUTPUT_DIGEST_CONFLICT" }
            stage.delete()
        }
    }
    private fun read(file: File): PublishedFile {
        val p=Properties().apply { file.inputStream().use(::load) }
        fun field(name: String)=p.getProperty(name) ?: error("CORRUPT_PUBLICATION_JOURNAL")
        val id=field("id"); require(UUID.fromString(id).toString()==id)
        val job=field("job"); val key=field("key"); require(file.name==identity(job,key)+".properties")
        val name=SafeNames.basename(field("name")); val hash=field("sha256"); require(Regex("[0-9a-f]{64}").matches(hash))
        return PublishedFile(id,job,key,File(File(results,id),name),name,field("mime"),field("size").toLong().also { require(it>=0) },hash,field("created").toLong(),p.getProperty("metadata","{}"))
    }
    fun reconcile(): List<PublishedFile> = locked {
        journal.listFiles { f -> f.extension=="properties" }.orEmpty().sortedBy { it.name }.map { f ->
            val record=read(f); val stage=File(File(staging,record.jobId),"${identity(record.jobId,record.itemKey)}.part")
            finish(record,stage); record
        }
    }
    fun discardIncomplete(job: String) = locked {
        require(UUID.fromString(job).toString()==job)
        // Finish any journaled publication first; never delete the only durable candidate.
        journal.listFiles { f -> f.extension=="properties" }.orEmpty().map(::read).filter { it.jobId==job }.forEach { r ->
            finish(r,File(File(staging,job),"${identity(job,r.itemKey)}.part"))
        }
        File(staging,job).deleteRecursively()
    }
}
