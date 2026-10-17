package com.jumlo.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.DownloadLinkPolicy
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.data.files.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.processing.DownloadSettings
import com.jumlo.app.data.source.*
import com.jumlo.app.model.*
import com.jumlo.app.ui.intake.Intake
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PreviewFrame(val bitmap:Bitmap,val page:Int,val count:Int,val width:Int,val height:Int)
data class StorageUsage(val sources:Long=0,val outputs:Long=0,val partials:Long=0,val temporary:Long=0,val trash:Long=0)

@OptIn(FlowPreview::class)
@HiltViewModel class AppViewModel @Inject constructor(
    private val app:Application,
    private val preferences:UserPreferencesRepository,
    private val sources:SourceStore,
    private val outputStore:OutputStore,
    private val jobStore:JobStore,
    private val drafts:DraftStore,
    private val secrets:EphemeralSecretStore,
    private val treeStore:DocumentTreeStore,
    private val budget:WorkBudget,
    private val savedState:SavedStateHandle
):AndroidViewModel(app) {
    val settings=preferences.settingsFlow.catch { error ->
        if (error is java.io.IOException) { _message.value="SETTINGS_READ_FAILED"; emit(AppSettingsState(isLoaded=true)) }
        else throw error
    }.stateIn(viewModelScope,SharingStarted.Eagerly,AppSettingsState())
    val imported=sources.records.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val outputs=outputStore.records.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val trashed=outputStore.trashed
    val jobs=jobStore.snapshots.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _form=MutableStateFlow(WorkspaceDraft())
    val form=_form.asStateFlow()
    private val _workspaceReady=MutableStateFlow(false)
    val workspaceReady=_workspaceReady.asStateFlow()
    private val _busy=MutableStateFlow(false)
    val busy=_busy.asStateFlow()
    private val busyLock=Any()
    private var activeOperations=0
    private fun beginBusy()=synchronized(busyLock) { activeOperations++; _busy.value=true }
    private fun endBusy()=synchronized(busyLock) { activeOperations=(activeOperations-1).coerceAtLeast(0); _busy.value=activeOperations>0 }
    private val _message=MutableStateFlow<String?>(null)
    val message=_message.asStateFlow()
    private val _undoTrashId=MutableStateFlow<String?>(null)
    val undoTrashId=_undoTrashId.asStateFlow()
    private val _urls=MutableStateFlow("")
    val urls=_urls.asStateFlow()
    private val savedWorkspace=MutableStateFlow(WorkspaceDraft())
    private val savedUrls=MutableStateFlow("")
    private val draftMutex=Mutex()
    val hasUnsavedWorkspace=combine(_form,savedWorkspace,_urls,savedUrls) { current,saved,links,savedLinks ->
        val default=smartDefaultsForTool(WorkspaceDraft.forTool(current.toolId,emptyList()).copy(wifiOnly=current.wifiOnly))
        (current!=saved || links!=savedLinks) &&
            (current.sourceIds.isNotEmpty() || saved.sourceIds.isNotEmpty() || links.isNotBlank() || current!=default)
    }.stateIn(viewModelScope,SharingStarted.Eagerly,false)
    private val _tree=MutableStateFlow<DocumentListing?>(null)
    val tree=_tree.asStateFlow()
    private val _storage=MutableStateFlow(StorageUsage())
    val storage=_storage.asStateFlow()
    private val initialized=CompletableDeferred<Unit>()
    private var openedSession=false
    private val _sessionEpoch=MutableStateFlow(0L)
    val sessionEpoch=_sessionEpoch.asStateFlow()
    private val submitting=AtomicBoolean(false)
    private var storageRefresh:Job?=null
    private var lastFingerprint:String?=null
    private var lastJobId:String?=null
    private var submissionReset:Deferred<Unit>?=null
    var pendingExport:OutputRecord?
        get()=savedState.get<String>("pending_export")?.let { Json.decodeFromString<OutputRecord>(it) }
        set(value) { savedState["pending_export"]=value?.let { Json.encodeToString(it) } }
    private val errorLog=com.jumlo.app.core.ErrorLog(java.io.File(app.filesDir,"error-log.txt"))
    var pendingErrorLogExport:Boolean
        get()=savedState["pending_error_export"] ?: false
        private set(value) { savedState["pending_error_export"]=value }

    init {
        viewModelScope.launch {
            try {
                val checkpoint=drafts.loadWorkspace()?.takeIf { it.version==1 } ?: WorkspaceDraft()
                savedWorkspace.value=checkpoint
                if (!openedSession) {
                    _form.value=checkpoint
                    _form.value=drafts.loadRecovery()?.takeIf { it.version==1 } ?: checkpoint
                }
            }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { _message.value="DRAFT_RESTORE_FAILED" }
            finally { initialized.complete(Unit); _workspaceReady.value=true }
            _form.debounce(300).collect { snapshot ->
                try { draftMutex.withLock { if(snapshot==_form.value) drafts.saveRecovery(snapshot) } }
                catch(cancel:CancellationException) { throw cancel }
                catch(_:Exception) { _message.value="DRAFT_SAVE_FAILED" }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try { outputStore.reconcile(); jobStore.recoverPending() }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { _message.value=stableError(error) }
        }
        viewModelScope.launch { try { _tree.value=treeStore.restore() } catch(cancel:CancellationException) { throw cancel } catch(error:Exception) { _message.value=stableError(error) } }
        refreshStorage()
    }
    fun dismissMessage() { _message.value=null; _undoTrashId.value=null; _undoTrashIds.value=emptyList() }
    fun notifyMessage(code:String) {
        _message.value=code
        val tool=form.value.toolId
        viewModelScope.launch(Dispatchers.IO) { runCatching { errorLog.record(code,tool) } }
    }
    fun saveWorkspaceDraft(onSaved:()->Unit) { viewModelScope.launch {
        initialized.await(); beginBusy()
        try {
            val snapshot=form.value; val links=urls.value
            draftMutex.withLock { drafts.saveWorkspaceAndRecovery(snapshot);savedWorkspace.value=snapshot;savedUrls.value=links }
            _message.value="DRAFT_SAVED"
            if(form.value==snapshot && urls.value==links) onSaved()
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { _message.value="DRAFT_SAVE_FAILED" }
        finally { endBusy() }
    } }
    fun discardWorkspaceChanges(onDiscarded:()->Unit) { viewModelScope.launch {
        initialized.await(); beginBusy()
        try {
            draftMutex.withLock {
                val checkpoint=savedWorkspace.value
                drafts.saveRecovery(checkpoint)
                _form.value=checkpoint;_urls.value=savedUrls.value
                lastFingerprint=null;lastJobId=null
            }
            onDiscarded()
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { _message.value="DRAFT_SAVE_FAILED" }
        finally { endBusy() }
    } }
    fun setUrls(value:String) {
        _urls.value=value.take(100000)
        val firstLine = value.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        if (firstLine.isNotBlank()) {
            val candidate = firstLine.substringBefore('?').substringBefore('#').substringAfterLast('/').trim()
            if (candidate.isNotBlank() && candidate.contains('.') && !candidate.endsWith('.')) {
                updateForm { it.copy(outputName = candidate) }
            }
        }
    }
    fun updateForm(change:(WorkspaceDraft)->WorkspaceDraft) { _form.update(change); lastFingerprint=null; lastJobId=null }

    private fun smartDefaultsForTool(draft: WorkspaceDraft): WorkspaceDraft = when (draft.toolId) {
        "pdf_stamp" -> draft.copy(flag1 = true)
        "pdf_annotate" -> draft.copy(text1 = "ملاحظة", num1 = "1")
        "img_watermark" -> draft.copy(text1 = "Jumlo", num1 = "40")
        "file_batch_rename" -> draft.copy(text1 = "{name}_{n}", num1 = "1")
        "pdf_05" -> draft.copy(flag1 = true, num1 = "1", pages = "all")
        else -> draft
    }

    /** Each entry is a new session. Never pass a previous tool's selected input IDs. */
    fun chooseTool(id:String):Boolean {
        if(!CapabilityRegistry.enabled(id)) { _message.value=CapabilityRegistry.reason(id); return false }
        openedSession=true
        _sessionEpoch.value++
        savePreference { preferences.recordToolOpen(id) }
        _urls.value=""
        _form.value=smartDefaultsForTool(WorkspaceDraft.forTool(id,emptyList()))
        savedWorkspace.value=_form.value
        savedUrls.value=""
        lastFingerprint=null;lastJobId=null
        return true
    }
    /** Explicit handoff only, e.g. a result's "Use in another tool" action. */
    fun chooseToolWithSources(id:String,sourceIds:List<String>):Boolean {
        if(!chooseTool(id)) return false
        updateForm { it.copy(sourceIds=sourceIds.distinct().take(1000)) }
        return true
    }
    fun closeToolSession() {
        openedSession=true
        _sessionEpoch.value++
        _urls.value=""
        _form.value=smartDefaultsForTool(WorkspaceDraft.forTool("pdf_02",emptyList()))
        savedWorkspace.value=_form.value
        savedUrls.value=""
        lastFingerprint=null;lastJobId=null
    }
    fun addSource(id:String,displayName:String?=null) { updateForm { draft ->
        val names=(draft.sourceIds+id).distinct().take(1000)
        val sourceName=displayName ?: imported.value.firstOrNull { it.id==id }?.displayName
        val output=if(draft.sourceIds.isEmpty() && sourceName!=null)
            AutoOutputName.fromSource(draft.toolId,sourceName) ?: draft.outputName else draft.outputName
        draft.copy(sourceIds=names,outputName=output)
    } }
    fun removeSource(id:String) { updateForm { it.copy(sourceIds=it.sourceIds-id) } }
    fun clearQueue() { updateForm { it.copy(sourceIds=emptyList()) } }
    fun moveSource(index:Int,delta:Int) { updateForm { old ->
        val list=old.sourceIds.toMutableList();val to=index+delta
        if(index in list.indices && to in list.indices) list.add(to,list.removeAt(index))
        old.copy(sourceIds=list)
    } }
    fun intake(intake:Intake) {
        viewModelScope.launch {
            initialized.await()
            if(intake.uris.isEmpty()) intake.sharedText?.takeIf { it.isNotBlank() }?.let { text ->
                val lines=text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
                if(lines.all { it.startsWith("https://",true) || it.startsWith("http://",true) }) {
                    // Route social pages through the extractor so users see real format choices.
                    chooseTool(when {
                        lines.size>1 -> "dl_03"
                        DownloadLinkPolicy.requiresMediaExtractor(lines.single()) -> "dl_media"
                        else -> "dl_02"
                    })
                    setUrls(text)
                } else notifyMessage("INVALID_DOWNLOAD_LIST")
            }
            if(intake.uris.isEmpty()) return@launch
            beginBusy()
            try {
                for(uri in intake.uris) {
                    try { val source=sources.inspect(uri,intake.offeredFlags);addSource(source.id,source.displayName) }
                    catch(cancel:CancellationException) { throw cancel }
                    catch(error:Exception) { _message.value=stableError(error) }
                }
                refreshStorage()
            } finally { endBusy() }
        }
    }
    fun intakeLocalFile(file:java.io.File,displayName:String,mimeType:String?) { viewModelScope.launch { initialized.await();beginBusy();try { val source=sources.importLocalFile(file,displayName,mimeType);addSource(source.id,source.displayName); refreshStorage() } catch(cancel:CancellationException) { throw cancel } catch(error:Exception) { _message.value=stableError(error) } finally {endBusy()} } }
    fun importOutput(record:OutputRecord) { intake(Intake(listOf(record.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION,null)) }
    private val _undoTrashIds=MutableStateFlow<List<String>>(emptyList())
    val undoTrashIds=_undoTrashIds.asStateFlow()
    /** Captured IDs only; a single Undo reverses the complete selection. */
    fun trashOutputs(records:List<OutputRecord>) {
        val captured=records.map {it.id}.distinct()
        if(captured.isEmpty()) return
        viewModelScope.launch {
            beginBusy()
            try {
                val moved=captured.mapNotNull { outputStore.moveToTrash(it) }.map {it.id}
                if(moved.isNotEmpty()) {
                    _undoTrashIds.value=moved
                    _undoTrashId.value=moved.last()
                    _message.value="MOVED_TO_TRASH"
                    refreshStorage()
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {_message.value=stableError(error)}
            finally {endBusy()}
        }
    }
    fun restoreOutputs(ids:List<String>) {
        if(ids.isEmpty()) return
        viewModelScope.launch {
            beginBusy()
            try {
                ids.distinct().forEach {outputStore.restoreFromTrash(it)}
                _undoTrashIds.value=emptyList();_undoTrashId.value=null
                _message.value="RESTORED_FROM_TRASH"
                refreshStorage()
            } catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {_message.value=stableError(error)}
            finally {endBusy()}
        }
    }
    fun trashOutput(record:OutputRecord) {
        viewModelScope.launch {
            try {
                val moved=outputStore.moveToTrash(record.id)
                if(moved!=null) { _undoTrashIds.value=listOf(moved.id);_undoTrashId.value=moved.id; _message.value="MOVED_TO_TRASH"; refreshStorage() }
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { _message.value=stableError(error) }
        }
    }
    fun restoreOutput(outputId:String) {
        viewModelScope.launch {
            try {
                outputStore.restoreFromTrash(outputId)
                _undoTrashId.value=null
                _undoTrashIds.value=emptyList()
                _message.value="RESTORED_FROM_TRASH"
                refreshStorage()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { _message.value=stableError(error) }
        }
    }
    /** IDs are captured when the confirmation is shown, never expanded at deletion time. */
    fun purgeTrashIds(outputIds:Set<String>) {
        if(outputIds.isEmpty()) return
        viewModelScope.launch {
            beginBusy()
            try {
                outputStore.purgeTrashIds(outputIds.toSet())
                _message.value="TRASH_PURGED"
                refreshStorage()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { _message.value=stableError(error) }
            finally { endBusy() }
        }
    }
    fun submit(onQueued:(String)->Unit) {
        if(!submitting.compareAndSet(false,true)) return
        beginBusy()
        viewModelScope.launch {
            var newHandle:String?=null
            try {
                initialized.await()
                submissionReset?.await()
                val state=form.value
                require(CapabilityRegistry.canProcess(state.toolId)) { "CAPABILITY_UNAVAILABLE" }
                val download=state.toolId.startsWith("dl_")
                val driveImport=state.toolId=="file_drive" && !state.flag1
                require(download || state.toolId=="webpage_pdf" || driveImport || state.sourceIds.isNotEmpty()) { "NO_SOURCES" }
                if(state.toolId in setOf("pdf_07","file_08")) require(state.sourceIds.size==1) { "ONE_SOURCE_REQUIRED" }
                if(state.toolId=="file_drive") {
                    require(state.secret1.isNotBlank()) { "LOGIN_REQUIRED" }
                    if(!state.flag1) require(state.text1.isNotBlank()) { "INVALID_DRIVE_FILE_ID" }
                }
                val urlSnapshot=urls.value
                if(download) {
                    val lines=urlSnapshot.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    require(lines.isNotEmpty() && lines.size<=100 && (state.toolId=="dl_03" || lines.size==1)) { "INVALID_DOWNLOAD_LIST" }
                    lines.forEach { line ->
                        if(DownloadLinkPolicy.requiresMediaExtractor(line)) throw OperationFailure("MEDIA_SELECTION_REQUIRED")
                        HttpPolicy.url(DownloadLinkPolicy.resolveDirectUrl(line),state.cleartextConsent)
                    }
                }
                val fingerprint=Hashing.sha256(ByteArrayInputStream((Json.encodeToString(state)+"\n"+if(download) urlSnapshot else "").toByteArray()))
                if(lastFingerprint==fingerprint && lastJobId!=null) { onQueued(lastJobId!!); return@launch }
                newHandle=when {
                    download -> secrets.put(urlSnapshot.toCharArray())
                    state.toolId=="pdf_protect" -> secrets.put((state.secret1+"\n"+state.secret2).toCharArray())
                    state.toolId in setOf("file_encrypted_zip","file_drive") -> secrets.put(state.secret1.toCharArray())
                    else -> null
                }
                val settings=state.executionSettings(newHandle)
                val candidate=JobRequest(UUID.randomUUID().toString(),state.toolId,if(download || driveImport) emptyList() else state.sourceIds,1,settings,System.currentTimeMillis())
                val request=draftMutex.withLock { drafts.reserveSubmission(fingerprint,candidate,state) }
                val reboundHandle=rebindDownloadSubmissionSecret(request,candidate,secrets)
                val id=jobStore.enqueue(request)
                if(reboundHandle!=null) {
                    when(jobStore.observe(id).first().progress.stage) {
                        JobStage.WAITING_FOR_INPUT -> jobStore.retryFailed(id)
                        JobStage.COMPLETED,JobStage.CANCELLED -> secrets.remove(reboundHandle)
                        else -> Unit
                    }
                }
                lastFingerprint=fingerprint;lastJobId=id
                if(request.id==candidate.id) newHandle=null
                onQueued(id)
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { notifyMessage(stableError(error)) }
            finally { newHandle?.let(secrets::remove);endBusy();submitting.set(false) }
        }
    }
    fun allowNewRun() {
        lastFingerprint=null;lastJobId=null
        val previous=submissionReset
        submissionReset=viewModelScope.async { previous?.await();draftMutex.withLock { drafts.clearSubmission() } }
    }
    fun pause(job:String) { viewModelScope.launch { jobStore.pause(job) } }
    fun cancel(job:String) { viewModelScope.launch { jobStore.cancel(job) } }
    fun retry(job:JobSnapshot,replacementUrls:String?=null) { viewModelScope.launch {
        try {
            if(job.request.toolId.startsWith("dl_") && replacementUrls!=null) {
                val config=Json.decodeFromString<DownloadSettings>(job.request.settingsJson)
                secrets.replace(config.urlSecretHandle,replacementUrls.toCharArray())
            }
            jobStore.retryFailed(job.request.id)
        } catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { _message.value=stableError(error) }
    } }
    fun finishOnboarding() { viewModelScope.launch { preferences.setOnboardingCompleted(true) } }
    private fun savePreference(block:suspend ()->Unit) { viewModelScope.launch {
        try { block() } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { _message.value="SETTINGS_SAVE_FAILED" }
    } }
    fun setQuickActionIds(ids:List<String>) = savePreference {
        val eligible=CapabilityRegistry.tools.filter {CapabilityRegistry.visible(it.id) && CapabilityRegistry.canProcess(it.id)}.map {it.id}
        preferences.setQuickActionIds(com.jumlo.app.ui.home.HomeActionPolicy.resolve(ids,eligible))
    }
    fun setThemeMode(mode:ThemeMode) = savePreference { preferences.setThemeMode(mode) }
    fun setLanguage(language:AppLanguage) = savePreference { preferences.setLanguage(language) }
    fun setRespectReducedMotion(enabled:Boolean) = savePreference { preferences.setRespectReducedMotion(enabled) }
    fun setHapticFeedback(enabled:Boolean) = savePreference { preferences.setHapticOnCommit(enabled) }
    fun setHapticIntensity(intensity:com.jumlo.app.model.HapticIntensity) = savePreference { preferences.setHapticIntensity(intensity) }
    fun setSoundsEnabled(enabled:Boolean) = savePreference { preferences.setSoundsEnabled(enabled) }
    fun setFileViewMode(value:FileViewMode) = savePreference { preferences.setFileViewMode(value) }
    fun setAccentColor(value:String) = savePreference { preferences.setAccentColor(value) }
    fun setFontScale(value:String) = savePreference { preferences.setFontScale(value) }
    fun setMaxConcurrentDownloads(value:Int) = savePreference { preferences.setMaxConcurrentDownloads(value) }
    fun setDownloadDestinationFolder(treeUri:String,flags:Int) { viewModelScope.launch {
        try {
            val uri=Uri.parse(treeUri)
            require(uri.scheme=="content") { "INVALID_TREE_URI" }
            val writable=flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            require(writable and Intent.FLAG_GRANT_WRITE_URI_PERMISSION!=0) { "PROVIDER_READ_ONLY" }
            app.contentResolver.takePersistableUriPermission(uri,writable)
            preferences.setDownloadDestinationFolder(treeUri)
        } catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { _message.value=stableError(error) }
    } }
    fun setWifiOnlyDefault(enabled:Boolean) = savePreference { preferences.setWifiOnlyDefault(enabled) }
    fun notificationsGranted(granted:Boolean) { viewModelScope.launch { preferences.setNotificationsGranted(granted) } }
    fun prepareExport(output:OutputRecord) { pendingExport=output }
    fun prepareErrorLogExport() { pendingErrorLogExport=true }
    fun exportErrorLog(target:String?) {
        if(!pendingErrorLogExport) return; pendingErrorLogExport=false
        if(target==null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(Uri.parse(target),"wt")?.use { it.write(errorLog.read().toByteArray(Charsets.UTF_8)) }
                    ?: throw OperationFailure("EXPORT_WRITE_FAILED")
                _message.value="ERROR_LOG_EXPORTED"
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { _message.value="EXPORT_FAILED" }
        }
    }
    fun export(target:String?) {
        val output=pendingExport ?: return;pendingExport=null
        if(target==null) return
        viewModelScope.launch(Dispatchers.IO) {
            beginBusy()
            try {
                require(Uri.parse(target).scheme=="content") { "CONTENT_URI_REQUIRED" }
                require(Uri.parse(target)!=Uri.parse(output.uri)) { "EXPORT_SAME_DOCUMENT" }
                val resolver=app.contentResolver
                resolver.openInputStream(Uri.parse(output.uri))?.use { input ->
                    resolver.openOutputStream(Uri.parse(target),"wt")?.use { sink ->
                        val buffer=ByteArray(64*1024);var total=0L
                        while(true) { currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0) break
                            if(n>0) { sink.write(buffer,0,n);total+=n }
                        };sink.flush();check(total==output.sizeBytes) { "EXPORT_LENGTH_MISMATCH" }
                    } ?: throw OperationFailure("EXPORT_WRITE_FAILED")
                } ?: throw OperationFailure("OUTPUT_MISSING")
                val actual=resolver.openInputStream(Uri.parse(target))?.use { Hashing.sha256(it) } ?: throw OperationFailure("EXPORT_UNVERIFIED")
                check(actual==output.sha256) { "EXPORT_HASH_MISMATCH" }
                _message.value="EXPORT_VERIFIED"
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { _message.value="EXPORT_UNVERIFIED_TARGET_MAY_BE_PARTIAL" }
            finally { endBusy() }
        }
    }
    fun acceptTree(uri:String,flags:Int) { viewModelScope.launch { try { _tree.value=treeStore.accept(uri,flags) } catch(cancel:CancellationException) { throw cancel } catch(error:Exception) { _message.value=stableError(error) } } }
    fun createFolder(name:String) { treeOperation { treeStore.createFolder(it.treeUri,name) } }
    fun renameFile(entry:DocumentEntry,name:String) { treeOperation { treeStore.rename(entry.uri,name) } }
    fun deleteFile(entry:DocumentEntry) { treeOperation { treeStore.deleteFile(entry.uri) } }
    fun copyOrMoveFile(entry:DocumentEntry,move:Boolean,policy:CollisionPolicy) {
        treeOperation { current ->
            val created=treeStore.copyOrMove(entry.uri,current.treeUri,entry.name,move,policy)
            _message.value=if(created==null) "COLLISION_SKIPPED" else if(move) "FILE_MOVED_VERIFIED" else "FILE_COPIED_VERIFIED"
        }
    }
    private fun treeOperation(block:suspend (DocumentListing)->Unit) { viewModelScope.launch {
        try { val current=tree.value ?: throw OperationFailure("NO_FOLDER_SELECTED");block(current);_tree.value=treeStore.list(current.treeUri) }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { _message.value=stableError(error) }
    } }
    /** Only old reproducible previews/extraction cache. Drafts, trash and resumable jobs stay intact. */
    fun cleanTemporary() { viewModelScope.launch(Dispatchers.IO) {
        beginBusy()
        try {
            val cutoff=System.currentTimeMillis()-7L*24*3600*1000
            val context=currentCoroutineContext()
            com.jumlo.app.core.TemporaryCleanup.removeExpired(app.cacheDir,cutoff) { context.ensureActive() }
            _message.value="CLEANUP_DONE";refreshStorage()
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { _message.value="CLEANUP_FAILED" }
        finally { endBusy() }
    } }
    fun refreshStorage() { storageRefresh?.cancel();storageRefresh=viewModelScope.launch(Dispatchers.IO) {
        val context=currentCoroutineContext()
        fun bytes(file:File):Long { var total=0L;file.walkTopDown().forEach { context.ensureActive();if(it.isFile) total+=it.length() };return total }
        _storage.value=StorageUsage(
            bytes(File(app.filesDir,"inbound")),
            bytes(File(app.filesDir,"processing/results")),
            bytes(File(app.filesDir,"transfers")),
            bytes(app.cacheDir),
            bytes(File(app.filesDir,"processing/trash"))
        )
    } }
    suspend fun preview(sourceId:String,page:Int):PreviewFrame {
        var pending:PreviewFrame?=null
        try {
            val delivered=withContext(Dispatchers.IO) { decodePreview(sourceId,page).also { pending=it } }
            pending=null
            return delivered
        } finally {
            pending?.bitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }
    private suspend fun decodePreview(sourceId:String,page:Int):PreviewFrame {
        return sources.withSeekable(sourceId,UUID.randomUUID().toString()) { file ->
            currentCoroutineContext().ensureActive()
            val pdf=file.inputStream().use { val signature=ByteArray(5);it.read(signature)==5 && String(signature,Charsets.US_ASCII)=="%PDF-" }
            if(pdf) ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer ->
                require(page in 1..renderer.pageCount) { "PAGE_OUT_OF_RANGE" }
                renderer.openPage(page-1).use { p ->
                    val scale=minOf(2.0,1600.0/maxOf(p.width,p.height).coerceAtLeast(1))
                    var w=(p.width*scale).roundToInt().coerceAtLeast(1)
                    var h=(p.height*scale).roundToInt().coerceAtLeast(1)
                    val maxPixels=(budget.maxBitmapBytes/4L).coerceAtLeast(512L*512L)
                    if(w.toLong()*h>maxPixels) {
                        val down=sqrt(maxPixels.toDouble()/(w.toDouble()*h))
                        w=(w*down).roundToInt().coerceAtLeast(1)
                        h=(h*down).roundToInt().coerceAtLeast(1)
                    }
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    try { bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,Matrix().apply { setScale(w.toFloat()/p.width,h.toFloat()/p.height) },PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);PreviewFrame(bitmap,page,renderer.pageCount,p.width,p.height) }
                    catch(t:Throwable) { bitmap.recycle();throw t }
                }
            } }
            else {
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth>0 && options.outHeight>0) { "PREVIEW_UNAVAILABLE" }
                val width=options.outWidth;val height=options.outHeight;var sample=1
                while(maxOf(width,height)/sample>1600) sample*=2
                options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888
                val original=BitmapFactory.decodeFile(file.path,options) ?: throw OperationFailure("PREVIEW_UNAVAILABLE")
                try {
                    val orientation=try { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) } catch(_:Exception) { 1 }
                    val matrix=Matrix().apply { when(orientation) { 2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f) } }
                    val normalized=if(orientation in 2..8) Bitmap.createBitmap(original,0,0,original.width,original.height,matrix,true) else original
                    if(normalized!==original) original.recycle()
                    PreviewFrame(normalized,1,1,width,height)
                } catch(t:Throwable) { original.recycle();throw t }
            }
        }
    }
    override fun onCleared() { super.onCleared() }
}
