package com.jumlo.app.printing

import java.io.File
import java.util.UUID

/** An in-flight snapshot lease closes the copy -> PrintManager -> persistent-history race. */
data class PrintSnapshot(val source:PrintSource,val leaseId:String,val created:Boolean)
class PrintSnapshotPolicy(private val directory:File) {
    private val lock=Any()
    private val leases=mutableMapOf<String,String>()
    fun acquire(source:PrintSource):PrintSnapshot=synchronized(lock) {
        directory.mkdirs()
        val original=File(source.path).canonicalFile
        val created=!original.toPath().startsWith(directory.canonicalFile.toPath())
        val file=if(created) File(directory,"${UUID.randomUUID()}.bin") else original
        val token=UUID.randomUUID().toString()
        leases[token]=file.canonicalPath
        PrintSnapshot(source.copy(path=file.canonicalPath),token,created)
    }
    fun release(snapshot:PrintSnapshot,referenced:Set<String>)=synchronized(lock) {
        leases.remove(snapshot.leaseId)
        cleanupLocked(referenced)
    }
    fun cleanup(referenced:Set<String>)=synchronized(lock) {cleanupLocked(referenced)}
    private fun cleanupLocked(referenced:Set<String>) {
        val keep=referenced.map {File(it).canonicalPath}.toSet()+leases.values
        directory.listFiles()?.forEach {file ->
            // Do not follow/delete foreign entries or originals. Partial files are protected by
            // the lease for their eventual .bin destination until copying has really terminated.
            if(!file.name.matches(Regex("[0-9a-f-]{36}\\.bin(?:\\.part)?"))) return@forEach
            val destination=file.canonicalPath.removeSuffix(".part")
            if(destination !in keep && file.canonicalFile.parentFile==directory.canonicalFile) file.delete()
        }
    }
    companion object {
        /** Retain every known active job; bound inactive history separately. */
        fun retain(history:List<PrintHistoryEntry>):List<PrintHistoryEntry> = history.filter {PrintHistoryPolicy.canCancel(it.state)}+
            history.filterNot {PrintHistoryPolicy.canCancel(it.state)}.take(100)
    }
}
