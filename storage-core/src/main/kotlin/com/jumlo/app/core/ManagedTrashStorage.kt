package com.jumlo.app.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

data class TrashEntry(val id:String,val displayName:String,val sizeBytes:Long,val sha256:String,val recordJson:String,val journalKey:String)
data class TrashRecovery(val entry:TrashEntry,val restored:Boolean)

/** App-private trash transactions. Metadata never shares the payload's filename. */
class ManagedTrashStorage(private val root:File) {
    private val trash=File(root,"trash").apply { mkdirs() }
    private fun folder(id:String):File {
        require(runCatching { UUID.fromString(id).toString()==id }.getOrDefault(false)) { "INVALID_OUTPUT_IDENTITY" }
        val folder=File(trash,id)
        require(!Files.isSymbolicLink(folder.toPath()) && folder.canonicalFile.parentFile==trash.canonicalFile) { "INVALID_TRASH_PATH" }
        return folder
    }
    private fun validate(e:TrashEntry) {
        folder(e.id);require(SafeNames.basename(e.displayName)==e.displayName) { "INVALID_FILENAME" }
        require(e.sizeBytes>=0 && Regex("[0-9a-f]{64}").matches(e.sha256) && Regex("[0-9a-f]{64}").matches(e.journalKey)) { "INVALID_TRASH_RECORD" }
        require(e.recordJson.length<=1000000) { "INVALID_TRASH_RECORD" }
    }
    private fun active(e:TrashEntry):File {
        val parent=File(root,"results/${e.id}")
        require(!Files.isSymbolicLink(parent.toPath()) && parent.canonicalFile.parentFile==File(root,"results").canonicalFile) { "INVALID_TRASH_PATH" }
        return File(parent,e.displayName)
    }
    private fun payload(e:TrashEntry)=File(folder(e.id),"payload.bin")
    private fun verify(file:File,e:TrashEntry) {
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length()==e.sizeBytes && Hashing.sha256(file)==e.sha256) { "TRASH_INTEGRITY_ERROR" }
    }
    private fun write(e:TrashEntry,state:String) {
        validate(e)
        val dir=folder(e.id);require(dir.mkdirs() || dir.isDirectory) { "TRASH_CREATE_FAILED" }
        val p=Properties().apply {
            setProperty("id",e.id);setProperty("name",e.displayName);setProperty("size",e.sizeBytes.toString())
            setProperty("sha256",e.sha256);setProperty("record",e.recordJson);setProperty("journal",e.journalKey);setProperty("state",state)
        }
        val pending=File(dir,"entry.pending")
        FileOutputStream(pending).use { p.store(it,"Jumlo managed trash v2");it.fd.sync() }
        Files.move(pending.toPath(),File(dir,"entry.properties").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }
    private fun read(id:String):TrashRecovery? {
        val file=File(folder(id),"entry.properties");if(!file.exists()) return null
        require(!Files.isSymbolicLink(file.toPath()) && file.length()<=2000000) { "INVALID_TRASH_RECORD" }
        val p=Properties().apply { file.inputStream().use(::load) }
        fun field(k:String)=requireNotNull(p.getProperty(k)) { "INVALID_TRASH_RECORD" }
        val e=TrashEntry(field("id"),field("name"),field("size").toLong(),field("sha256"),field("record"),field("journal"))
        validate(e);require(e.id==id) { "INVALID_TRASH_RECORD" }
        val state=field("state");require(state in setOf("TRASH","RESTORE")) { "INVALID_TRASH_RECORD" }
        return TrashRecovery(e,state=="RESTORE")
    }
    private fun moveVerified(from:File,to:File,e:TrashEntry) {
        if(to.exists()) { verify(to,e);require(!from.exists()) { "TRASH_COLLISION" };return }
        verify(from,e);require(to.parentFile.mkdirs() || to.parentFile.isDirectory)
        Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE)
        verify(to,e)
    }
    private fun journal(e:TrashEntry,restore:Boolean) {
        val live=File(root,"journal/${e.journalKey}.properties")
        val backup=File(folder(e.id),"journal.properties")
        val from=if(restore) backup else live;val to=if(restore) live else backup
        if(from.exists()) {
            require(!to.exists() && !Files.isSymbolicLink(from.toPath())) { "TRASH_JOURNAL_COLLISION" }
            to.parentFile.mkdirs();Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE)
        }
    }
    private fun finish(r:TrashRecovery):TrashRecovery {
        val e=r.entry
        if(r.restored) { moveVerified(payload(e),active(e),e);journal(e,true) }
        else { moveVerified(active(e),payload(e),e);journal(e,false);active(e).parentFile.delete() }
        return r
    }
    @Synchronized fun move(e:TrashEntry):TrashEntry {
        validate(e)
        val old=read(e.id)
        if(old!=null) { require(old.entry==e && !old.restored) { "TRASH_IDENTITY_CONFLICT" };finish(old);return e }
        verify(active(e),e)
        require(!folder(e.id).exists()) { "TRASH_COLLISION" }
        write(e,"TRASH");finish(TrashRecovery(e,false));return e
    }
    /** Converts an existing v1 trash entry only after checking its original bytes. */
    @Synchronized fun importLegacy(e:TrashEntry) {
        validate(e);if(read(e.id)!=null) return
        val old=File(folder(e.id),e.displayName)
        val dest=payload(e)
        if(!old.exists() && dest.isFile) { verify(dest,e);write(e,"TRASH");return }
        verify(old,e)
        if(old!=dest) { require(!dest.exists()) { "TRASH_COLLISION" };Files.move(old.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE) }
        write(e,"TRASH")
        if(e.displayName!="record.json") File(folder(e.id),"record.json").delete()
    }
    @Synchronized fun restore(id:String):TrashEntry? {
        val r=read(id) ?: return null
        if(!r.restored) { finish(r);write(r.entry,"RESTORE") }
        finish(TrashRecovery(r.entry,true));return r.entry
    }
    @Synchronized fun acknowledgeRestore(id:String) {
        val r=read(id) ?: return
        require(r.restored) { "TRASH_STATE_CONFLICT" };verify(active(r.entry),r.entry)
        require(folder(id).deleteRecursively()) { "TRASH_CLEANUP_FAILED" }
    }
    @Synchronized fun recover():List<TrashRecovery> = ids().mapNotNull { read(it)?.let(::finish) }
    @Synchronized fun entries():List<TrashEntry> = ids().mapNotNull { read(it)?.takeUnless { r->r.restored }?.entry }
    private fun ids()=trash.listFiles().orEmpty().filter { it.isDirectory && runCatching { UUID.fromString(it.name).toString()==it.name }.getOrDefault(false) }.map { it.name }.sorted()
    @Synchronized fun purge(ids:Set<String>):Int {
        // Validate the entire confirmation snapshot before any deletion.
        val candidates=ids.map { id -> folder(id);read(id) }.filterNotNull()
        candidates.forEach { r ->
            require(!r.restored && !active(r.entry).exists()) { "TRASH_STATE_CONFLICT" }
            verify(payload(r.entry),r.entry)
            require(folder(r.entry.id).walkTopDown().none { Files.isSymbolicLink(it.toPath()) }) { "INVALID_TRASH_PATH" }
        }
        return candidates.count { require(folder(it.entry.id).deleteRecursively()) { "TRASH_DELETE_FAILED" };true }
    }
}
