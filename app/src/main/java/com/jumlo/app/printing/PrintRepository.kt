package com.jumlo.app.printing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

private val Context.printDataStore by preferencesDataStore(name="jumlo_print_v1")

/** Only immutable snapshots/published private results may be reopened from persisted history. */
object PrintSourcePolicy {
    fun resolve(filesDir:File,path:String):File {
        val file=File(path).canonicalFile
        val roots=listOf(File(filesDir,"inbound"),File(filesDir,"processing/results"),File(filesDir,"printing/sources")).map {it.canonicalFile}
        require(roots.any {file.toPath().startsWith(it.toPath()) && file!=it}) { "PRINT_SOURCE_UNSAFE" }
        require(file.isFile && file.canRead() && file.length()>0 && !file.name.endsWith(".part")) { "PRINT_SOURCE_MISSING" }
        return file
    }
}
@Singleton class PrintRepository @Inject constructor(@ApplicationContext private val context:Context):PrintStore {
    private val key=stringPreferencesKey("print_data")
    private val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    override val data=context.printDataStore.data.map { prefs -> decode(prefs[key]) }
    override fun resolve(source:PrintSource)=PrintSourcePolicy.resolve(context.filesDir,source.path)
    private val snapshotMutations=Mutex()
    private val snapshots=PrintSnapshotPolicy(File(context.filesDir,"printing/sources"))
    /** Original sources are never deleted. Snapshot leases survive concurrent reconciliation. */
    override suspend fun snapshot(source:PrintSource):PrintSnapshot {
        var lease:PrintSnapshot?=null
        try {
            return withContext(Dispatchers.IO) {
                val original=resolve(source)
                val acquired=snapshots.acquire(source).also {lease=it}
                if(!acquired.created) return@withContext acquired
                val destination=File(acquired.source.path)
                val partial=File(destination.parentFile,"${destination.name}.part")
                try {
                    original.inputStream().use {input -> FileOutputStream(partial).use {output ->
                        val buffer=ByteArray(64*1024);var total=0L
                        while(true) {
                            currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0) break
                            total+=n;require(total<=1024L*1024*1024 && partial.parentFile!!.usableSpace>64L*1024*1024) { "PRINT_STORAGE" }
                            output.write(buffer,0,n)
                        }
                        output.fd.sync()
                    } }
                    currentCoroutineContext().ensureActive()
                    java.nio.file.Files.move(partial.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                    acquired
                } finally {partial.delete()}
            }
        } catch(t:Throwable) {
            // Also handles prompt cancellation after the IO block moved the file but before its
            // return value reached the caller. Preserve bytes if persisted references cannot be read.
            withContext(kotlinx.coroutines.NonCancellable) {lease?.let {runCatching {releaseSnapshot(it)}}}
            throw t
        }
    }
    override suspend fun releaseSnapshot(snapshot:PrintSnapshot) = withContext(Dispatchers.IO) {
        snapshotMutations.withLock {
            val current=data.first()
            snapshots.release(snapshot,current.snapshotReferences())
        }
    }
    private suspend fun cleanupSnapshots() = withContext(Dispatchers.IO) {
        snapshotMutations.withLock {snapshots.cleanup(data.first().snapshotReferences())}
    }
    private fun decode(raw:String?):PrintSavedData = if(raw==null) PrintSavedData() else json.decodeFromString<PrintSavedData>(raw)
    private suspend fun update(change:(PrintSavedData)->PrintSavedData) {
        context.printDataStore.edit { prefs -> prefs[key]=json.encodeToString(change(decode(prefs[key]))) }
    }
    private fun PrintSavedData.snapshotReferences()=(history.map {it.source.path}+recent.map {it.source.path}+listOfNotNull(sessionSource?.path)).toSet()
    override suspend fun session(source:PrintSource?) = update {it.copy(sessionSource=source)}
    override suspend fun settings(options:PrintOptions) { options.validate(); update {it.copy(lastOptions=options.reusable())} }
    override suspend fun recent(source:PrintSource) {
        resolve(source)
        update { old ->
            val identity=PrintSourceIdentity.documentKey(source)
            val matching=old.recent.firstOrNull {PrintSourceIdentity.documentKey(it.source)==identity}
            val next=listOf(PrintRecent(source,matching?.favorite ?: false,System.currentTimeMillis()))+old.recent.filterNot {PrintSourceIdentity.documentKey(it.source)==identity}
            old.copy(recent=(next.filter {it.favorite}+next.filterNot {it.favorite}.take(24)).take(60))
        }
    }
    override suspend fun favorite(source:PrintSource) = update { old -> old.copy(recent=old.recent.map { if(it.source==source) it.copy(favorite=!it.favorite) else it }) }
    override suspend fun preset(name:String,options:PrintOptions) {
        val preset=PrintPreset.create(name,options)
        update {it.copy(presets=(listOf(preset)+it.presets.filterNot {p->p.name.equals(preset.name,true)}).take(24))}
    }
    override suspend fun deletePreset(name:String) = update {it.copy(presets=it.presets.filterNot {p->p.name==name})}
    override suspend fun record(entry:PrintHistoryEntry) {
        update {it.copy(history=PrintSnapshotPolicy.retain(listOf(entry)+it.history.filterNot {h->h.id==entry.id}))}
        cleanupSnapshots()
    }
    override suspend fun reconcile(states:Map<String,PrintJobState>) {
        update {old ->old.copy(history=PrintSnapshotPolicy.retain(old.history.map {entry ->entry.copy(state=states[entry.id] ?: PrintHistoryPolicy.missing(entry.state))}))}
        cleanupSnapshots()
    }
}
