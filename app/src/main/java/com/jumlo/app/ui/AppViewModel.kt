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
import com.jumlo.app.core.ExternalIntakePolicy
import com.jumlo.app.printing.PrintSource
import com.jumlo.app.printing.PrintPage
import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.DownloadLinkPolicy
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.OutputFileChoice
import com.jumlo.app.core.OutputWorkflowPolicy
import com.jumlo.app.core.media.MediaLinks
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
import kotlinx.coroutines.channels.Channel
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

enum class DataLoadStatus { LOADING, READY, ERROR }
data class RenameUndo(val id:String,val previousName:String?,val newName:String)

data class PreviewFrame(val bitmap:Bitmap,val page:Int,val count:Int,val width:Int,val height:Int)
data class StorageUsage(val sources:Long=0,val outputs:Long=0,val partials:Long=0,val temporary:Long=0,val trash:Long=0)
/** What the compression slider needs to estimate the output size. */
data class CompressionProfile(val originalBytes:Long,val imageBytes:Long)

@OptIn(FlowPreview::class,ExperimentalCoroutinesApi::class)
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
    private val reloadRecords=MutableStateFlow(0)
    private val _sourceLoad=MutableStateFlow(DataLoadStatus.LOADING)
    private val _outputLoad=MutableStateFlow(DataLoadStatus.LOADING)
    private val _jobLoad=MutableStateFlow(DataLoadStatus.LOADING)
    private val _trashLoad=MutableStateFlow(DataLoadStatus.LOADING)
    val sourceLoad=_sourceLoad.asStateFlow()
    val outputLoad=_outputLoad.asStateFlow()
    val jobLoad=_jobLoad.asStateFlow()
    val trashLoad=_trashLoad.asStateFlow()
    fun reloadTrash() { viewModelScope.launch {
        _trashLoad.value=DataLoadStatus.LOADING
        try { outputStore.refreshTrash(); _trashLoad.value=DataLoadStatus.READY }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { _trashLoad.value=DataLoadStatus.ERROR; _message.value=stableError(error) }
    } }
    fun reloadSavedData() {
        _sourceLoad.value=DataLoadStatus.LOADING; _outputLoad.value=DataLoadStatus.LOADING; _jobLoad.value=DataLoadStatus.LOADING
        reloadRecords.value++
    }
    val settings=preferences.settingsFlow.catch { error ->
        if (error is java.io.IOException) { _message.value="SETTINGS_READ_FAILED"; emit(AppSettingsState(isLoaded=true)) }
        else throw error
    }.stateIn(viewModelScope,SharingStarted.Eagerly,AppSettingsState())
    val processingPresets=preferences.processingPresetsFlow.catch { error ->
        if(error is CancellationException) throw error
        _message.value="SETTINGS_READ_FAILED"
        emit(emptyList())
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    val imported=reloadRecords.flatMapLatest { sources.records.onEach { _sourceLoad.value=DataLoadStatus.READY }
        .catch { error -> if(error is CancellationException) throw error; _sourceLoad.value=DataLoadStatus.ERROR; emit(emptyList()) }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val outputs=reloadRecords.flatMapLatest {
        combine(outputStore.records,settings) { records,prefs ->
            records.map { record ->
                val label=prefs.outputNames[record.id]
                if(label==null) record else {
                    val payload=File(File(File(app.filesDir,"processing/results"),record.id),com.jumlo.app.core.SafeNames.basename(record.displayName))
                    record.copy(displayName=label,uri=androidx.core.content.FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",payload,label).toString())
                }
            }
        }.onEach { _outputLoad.value=DataLoadStatus.READY }
            .catch { error -> if(error is CancellationException) throw error; _outputLoad.value=DataLoadStatus.ERROR; emit(emptyList()) }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val trashed=outputStore.trashed
    val jobs=reloadRecords.flatMapLatest { jobStore.snapshots.onEach { _jobLoad.value=DataLoadStatus.READY }
        .catch { error -> if(error is CancellationException) throw error; _jobLoad.value=DataLoadStatus.ERROR; emit(emptyList()) }
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
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
    // One-shot request survives intake before Navigation Compose starts collecting.
    private val sharedLinkChannel = Channel<Unit>(Channel.CONFLATED)
    val sharedLinkOpenRequests: Flow<Unit> = sharedLinkChannel.receiveAsFlow()
    private val printChannel = Channel<Unit>(Channel.CONFLATED)
    val openPrintRequests = printChannel.receiveAsFlow()
    private val _printSource = MutableStateFlow(
        savedState.get<String>("print_source")?.let { raw ->
            runCatching { Json.decodeFromString<PrintSource>(raw) }.getOrNull()
        })
    val printSource = _printSource.asStateFlow()
    private val jobOpenChannel = Channel<String>(Channel.CONFLATED)
    val jobOpenRequests = jobOpenChannel.receiveAsFlow()
    fun openNotificationJob(rawId:String?) {
        ExternalIntakePolicy.jobId(rawId)?.let { jobOpenChannel.trySend(it) }
    }
    fun clearPrintSource() { _printSource.value=null; savedState["print_source"]=null }
    private fun openPrint(source:PrintSource) {
        _printSource.value=source
        savedState["print_source"]=Json.encodeToString(source)
        printChannel.trySend(Unit)
    }
    private suspend fun printableSource(source:SourceRef,pages:List<Pair<Int,Int>> = emptyList()):PrintSource {
        require(source.mimeType.equals("application/pdf",true) || source.mimeType?.startsWith("image/",true)==true) { "PRINT_UNSUPPORTED_FORMAT" }
        require(pages.size<=com.jumlo.app.printing.PrintRanges.MAX_PAGES) { "PRINT_PAGE_COUNT" }
        return sources.withSeekable(source.id,UUID.randomUUID().toString()) { file ->
            // Legacy SAF-only sources may have a temporary seek file. Adopt a durable
            // snapshot before returning so the print UI never points at deleted bytes.
            val inbound=File(app.filesDir,"inbound").canonicalFile
            val stable=if(file.canonicalFile.parentFile==inbound) file else {
                val copy=sources.importLocalFile(file,source.displayName,source.mimeType)
                File(inbound,"${copy.id}.bin")
            }
            PrintSource(stable.absolutePath,source.displayName,source.mimeType.orEmpty(),pages.map { PrintPage(it.first,it.second) })
        }
    }
    fun printSource(sourceId:String,pages:List<Pair<Int,Int>> = emptyList()) {
        viewModelScope.launch {
            initialized.await(); beginBusy()
            try { openPrint(printableSource(sources.get(sourceId),pages.toList())) }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { notifyMessage(stableError(error)) }
            finally { endBusy() }
        }
    }
    fun printOutput(record:OutputRecord) {
        viewModelScope.launch {
            initialized.await(); beginBusy()
            try {
                val verified=outputStore.find(record.jobId,record.itemKey)
                require(verified?.id==record.id && verified.sha256==record.sha256) { "OUTPUT_NOT_AVAILABLE" }
                // Reuse private canonical bytes. The print repository takes its own
                // bounded lifetime snapshot when it starts a spooler job.
                val file=File(File(File(app.filesDir,"processing/results"),verified.id),com.jumlo.app.core.SafeNames.basename(verified.displayName))
                openPrint(PrintSource(file.absolutePath,record.displayName,verified.mimeType))
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { notifyMessage(stableError(error)) }
            finally { endBusy() }
        }
    }
    fun importForPrint(intake:Intake) {
        val uri=intake.uris.singleOrNull() ?: return
        viewModelScope.launch {
            initialized.await(); beginBusy()
            try { openPrint(printableSource(sources.inspect(uri,intake.offeredFlags))) }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { notifyMessage(stableError(error)) }
            finally { endBusy() }
        }
    }
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
    private val _treeLoad=MutableStateFlow(DataLoadStatus.LOADING)
    val treeLoad=_treeLoad.asStateFlow()
    private val treeOperations=Mutex()
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
            try {
                outputStore.reconcile()
                _trashLoad.value=DataLoadStatus.READY
                jobStore.recoverPending()
            }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                if(_trashLoad.value!=DataLoadStatus.READY) _trashLoad.value=DataLoadStatus.ERROR
                _message.value=stableError(error)
            }
        }
        reloadTree()
        refreshStorage()
    }
    fun dismissMessage() { _message.value=null; _undoRename.value=null; _undoTrashId.value=null; _undoTrashIds.value=emptyList() }
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
        if (firstLine.isNotBlank() && DownloadLinkPolicy.isDirect(firstLine)) {
            val candidate = DownloadLinkPolicy.cleanName(firstLine)
            if (candidate.isNotBlank() && candidate != "download") {
                updateForm { it.copy(outputName = candidate) }
            }
        }
    }
    fun updateForm(change:(WorkspaceDraft)->WorkspaceDraft) { _form.update(change); lastFingerprint=null; lastJobId=null }

    private fun smartDefaultsForTool(draft: WorkspaceDraft): WorkspaceDraft = when (draft.toolId) {
        "pdf_stamp" -> draft.copy(flag1 = true)
        "pdf_annotate" -> draft.copy(text1 = "Jumlo", num1 = "1")
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
        val wifiDefault=if(id.startsWith("dl_")) settings.value.wifiOnlyDefault else false
        _form.value=smartDefaultsForTool(WorkspaceDraft.forTool(id,emptyList()).copy(wifiOnly=wifiDefault))
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
    fun intake(intake:Intake, attachToWorkspace:Boolean=true, externalShare:Boolean=false) {
        viewModelScope.launch {
            initialized.await()
            if(intake.uris.isEmpty()) intake.sharedText?.takeIf { it.isNotBlank() }?.let { text ->
                // Social shares may include titles, captions and multiple URLs in one paragraph.
                // Extract only syntactically valid, non-local links; never guess a filename from text.
                val lines = try { MediaLinks.sharedUrls(text) } catch(error:Exception) {
                    notifyMessage(stableError(error)); return@let
                }
                if (lines.size > 1 && lines.any(DownloadLinkPolicy::prefersMediaAnalysis)) {
                    // A web page needs per-link format analysis; passing it to direct batch
                    // downloads would save HTML or fail after promising a working transfer.
                    notifyMessage("MEDIA_BATCH_ANALYSIS_REQUIRED")
                } else if (lines.isNotEmpty()) {
                    val tool = when {
                        lines.size > 1 -> "dl_03"
                        DownloadLinkPolicy.prefersMediaAnalysis(lines.single()) -> "dl_media"
                        else -> "dl_02"
                    }
                    if (chooseTool(tool)) {
                        setUrls(lines.joinToString("\n"))
                        sharedLinkChannel.trySend(Unit)
                    } else notifyMessage("CAPABILITY_UNAVAILABLE")
                } else notifyMessage("INVALID_DOWNLOAD_LIST")
            }
            if(intake.uris.isEmpty()) return@launch
            beginBusy()
            try {
                val received=mutableListOf<SourceRef>()
                for(uri in intake.uris) {
                    try {
                        val source=sources.inspect(uri,intake.offeredFlags)
                        received.add(source)
                        if(attachToWorkspace && !externalShare) addSource(source.id,source.displayName)
                    }
                    catch(cancel:CancellationException) { throw cancel }
                    catch(error:Exception) { _message.value=stableError(error) }
                }
                if(externalShare && received.isNotEmpty()) {
                    val tool=ExternalIntakePolicy.toolFor(received.map { it.mimeType })
                    if(tool!=null && chooseToolWithSources(tool,received.map { it.id })) sharedLinkChannel.trySend(Unit)
                }
                refreshStorage()
            } finally { endBusy() }
        }
    }
    /** Imports a SAF provider entry and opens the matching tool only after true MIME inspection finishes. */
    fun importProviderEntryToWorkspace(uri:String,offeredFlags:Int,onReady:()->Unit) {
        viewModelScope.launch {
            initialized.await()
            beginBusy()
            try {
                val source=sources.inspect(uri,offeredFlags)
                val tool=OutputWorkflowPolicy.reuseTool(source.mimeType.orEmpty())
                require(chooseToolWithSources(tool,listOf(source.id))) { "CAPABILITY_UNAVAILABLE" }
                refreshStorage()
                onReady()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { notifyMessage(stableError(error)) }
            finally { endBusy() }
        }
    }
    fun intakeLocalFile(file:java.io.File,displayName:String,mimeType:String?) { viewModelScope.launch { initialized.await();beginBusy();try { val source=sources.importLocalFile(file,displayName,mimeType);addSource(source.id,source.displayName); refreshStorage() } catch(cancel:CancellationException) { throw cancel } catch(error:Exception) { _message.value=stableError(error) } finally {endBusy()} } }
    /** Import a multi-page scanner result in capture order, with rollback and cache cleanup.
     * Each page is copied to immutable SourceStore before deleting the scanner-owned temp.
     */
    fun intakeLocalFiles(files:List<java.io.File>) {
        val selected=files.toList()
        if(selected.isEmpty()) return
        viewModelScope.launch {
            initialized.await()
            beginBusy()
            val copied=mutableListOf<String>()
            suspend fun rollback() = withContext(NonCancellable) {
                copied.forEach { id ->
                    removeSource(id)
                    try { sources.discardNewImport(id) } catch (_: Exception) { }
                }
            }
            try {
                selected.forEachIndexed { index,file ->
                    currentCoroutineContext().ensureActive()
                    val source=sources.importLocalFile(file,"Scan ${index+1}.jpg","image/jpeg",
                        onCommitted = { committed -> copied.add(committed.id) })
                    addSource(source.id,source.displayName)
                }
                refreshStorage()
            } catch(cancel:CancellationException) {
                rollback()
                throw cancel
            } catch(error:Exception) {
                rollback()
                _message.value=stableError(error)
            } finally {
                selected.forEach { it.delete() }
                endBusy()
            }
        }
    }

    fun importOutput(record:OutputRecord) { intake(Intake(listOf(record.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION,null)) }

    private val _undoRename=MutableStateFlow<RenameUndo?>(null)
    val undoRename=_undoRename.asStateFlow()
    fun renameOutput(record:OutputRecord,newName:String) {
        viewModelScope.launch {
            try {
                val verified=outputStore.find(record.jobId,record.itemKey) ?: error("OUTPUT_NOT_AVAILABLE")
                require(verified.id==record.id) { "OUTPUT_NOT_AVAILABLE" }
                val name=com.jumlo.app.core.DocumentNamePolicy.rename(verified.displayName,newName)
                val before=settings.value.outputNames[record.id]
                preferences.setOutputName(record.id,name)
                _undoTrashIds.value=emptyList(); _undoTrashId.value=null
                _undoRename.value=RenameUndo(record.id,before,name)
                _message.value="OUTPUT_RENAMED"
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { notifyMessage("OUTPUT_RENAME_FAILED") }
        }
    }
    fun undoOutputRename(action:RenameUndo) {
        viewModelScope.launch {
            try {
                if(settings.value.outputNames[action.id]==action.newName) preferences.setOutputName(action.id,action.previousName)
                dismissMessage()
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { notifyMessage("OUTPUT_RENAME_FAILED") }
        }
    }

    fun toggleOutputFavorite(id:String) = savePreference { preferences.toggleFavoriteOutput(id) }

    /** Reuse an immutable imported source in an operation that actually accepts its MIME type. */
    fun prepareSourceWorkflow(sourceId:String,onReady:()->Unit) { viewModelScope.launch {
        try {
            initialized.await()
            val source=sources.get(sourceId)
            val tool=OutputWorkflowPolicy.reuseTool(source.mimeType.orEmpty())
            require(chooseToolWithSources(tool,listOf(sourceId))) { "CAPABILITY_UNAVAILABLE" }
            onReady()
        } catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { notifyMessage(stableError(error)) }
    } }

    /** Imported sources already are immutable app-owned snapshots. Reuse their IDs directly,
     * without duplicating bytes or silently changing the selected order. */
    fun prepareSourceBatchWorkflow(targetTool:String,chosen:List<SourceRef>,onReady:()->Unit) {
        val captured=chosen.toList()
        viewModelScope.launch {
            initialized.await()
            beginBusy()
            try {
                OutputWorkflowPolicy.validate(captured.map {
                    OutputFileChoice(it.id,it.displayName,it.mimeType.orEmpty(),it.sizeBytes ?: -1L)
                },targetTool)
                require(CapabilityRegistry.canProcess(targetTool)) { "CAPABILITY_UNAVAILABLE" }
                withContext(Dispatchers.IO) {
                    captured.forEach { expected ->
                        currentCoroutineContext().ensureActive()
                        val source=sources.get(expected.id)
                        require(source.readable && source.id==expected.id &&
                            source.mimeType==expected.mimeType && source.sizeBytes==expected.sizeBytes) {
                            "SOURCE_NOT_FOUND"
                        }
                        sources.openRead(source.id).use { it.read() } // Verify each snapshot still opens.
                    }
                }
                require(chooseToolWithSources(targetTool,captured.map { it.id })) { "CAPABILITY_UNAVAILABLE" }
                if(targetTool=="file_07") updateForm { it.copy(outputName="Jumlo_Collection.zip") }
                onReady()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { notifyMessage(stableError(error)) }
            finally { endBusy() }
        }
    }

    /** User-selected outputs become owned source snapshots, then use the existing durable job pipeline.
     * Original output bytes and records are never mutated. A failed multi-file handoff rolls back
     * only copies created by this call. The underlying source files are rechecked by SHA-256.
     */
    fun prepareOutputWorkflow(targetTool:String, chosen:List<OutputRecord>,onReady:()->Unit) {
        val captured=chosen.toList() // Freeze the actual selection before any suspension.
        viewModelScope.launch {
            initialized.await()
            beginBusy()
            val staged=mutableListOf<String>()
            var attached=false
            try {
                OutputWorkflowPolicy.validate(captured.map {
                    OutputFileChoice(it.id,it.displayName,it.mimeType,it.sizeBytes)
                },targetTool)
                require(CapabilityRegistry.canProcess(targetTool)) { "CAPABILITY_UNAVAILABLE" }
                withContext(Dispatchers.IO) {
                    for(record in captured) {
                        currentCoroutineContext().ensureActive()
                        val verified=outputStore.find(record.jobId,record.itemKey)
                        require(verified!=null && verified.id==record.id && verified.sha256==record.sha256 &&
                            verified.mimeType==record.mimeType) { "OUTPUT_NOT_AVAILABLE" }
                        // The ContentProvider copies the private, verified published output.
                        // No original output is moved, renamed, overwritten or removed.
                        val snapshot=sources.inspect(verified.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        staged.add(snapshot.id)
                    }
                }
                require(chooseToolWithSources(targetTool,staged.toList())) { "CAPABILITY_UNAVAILABLE" }
                if(targetTool=="file_07") updateForm { it.copy(outputName="Jumlo_Collection.zip") }
                attached=true
                onReady()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { notifyMessage(stableError(error)) }
            finally {
                if(!attached) withContext(NonCancellable + Dispatchers.IO) {
                    staged.forEach { id -> runCatching { sources.discardNewImport(id) } }
                }
                endBusy()
            }
        }
    }
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
                    _undoRename.value=null; _message.value="MOVED_TO_TRASH"
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
                if(moved!=null) { _undoTrashIds.value=listOf(moved.id);_undoTrashId.value=moved.id; _undoRename.value=null; _message.value="MOVED_TO_TRASH"; refreshStorage() }
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
                // A batch must not fetch the same link twice.
                val effectiveUrlText=if(download){
                    val seen=mutableSetOf<String>()
                    val lines=urlSnapshot.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    lines.filter { seen.add(it.lowercase()) }.joinToString("\n")
                } else urlSnapshot
                val fingerprint=Hashing.sha256(ByteArrayInputStream((Json.encodeToString(state)+"\n"+if(download) urlSnapshot else "").toByteArray()))
                if(lastFingerprint==fingerprint && lastJobId!=null) { onQueued(lastJobId!!); return@launch }
                newHandle=when {
                    download -> secrets.put(effectiveUrlText.toCharArray())
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
    fun cancel(job:String) { viewModelScope.launch {
        try { jobStore.cancel(job) }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { notifyMessage(stableError(error)) }
    } }
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
    fun saveProcessingPreset(name: String) = savePreference {
        val snapshot=form.value
        preferences.setProcessingPreset(ProcessingPreset.capture(name,snapshot))
    }
    fun applyProcessingPreset(preset: ProcessingPreset) {
        if(preset.toolId != form.value.toolId) {notifyMessage("CAPABILITY_UNAVAILABLE");return}
        updateForm { preset.apply(it) }
    }
    fun deleteProcessingPreset(preset: ProcessingPreset) = savePreference {
        preferences.removeProcessingPreset(preset.toolId,preset.name)
    }

    fun finishOnboarding() = savePreference { preferences.setOnboardingCompleted(true) }
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
    fun setMediaServiceUrl(url:String) = savePreference { preferences.setMediaServiceUrl(url) }
    fun setMediaServiceToken(token:String) = savePreference { preferences.setMediaServiceToken(token) }
    fun setDefaultVideoQuality(value:String) = savePreference { preferences.setDefaultVideoQuality(value) }
    fun setDefaultAudioQuality(value:String) = savePreference { preferences.setDefaultAudioQuality(value) }
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
    fun resetDownloadDestinationFolder() = savePreference { preferences.resetDownloadDestinationFolder() }
    fun setWifiOnlyDefault(enabled:Boolean) = savePreference {
        preferences.setWifiOnlyDefault(enabled)
        if(_form.value.toolId.startsWith("dl_")) {
            _form.update { it.copy(wifiOnly = enabled) }
        }
    }
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
    fun reloadTree() { treeRequest { tree.value?.let { treeStore.list(it.treeUri) } ?: treeStore.restore() } }
    fun acceptTree(uri:String,flags:Int) { treeRequest { treeStore.accept(uri,flags) } }
    fun createFolder(name:String) { treeOperation { treeStore.createFolder(it.treeUri,name) } }
    fun renameFile(entry:DocumentEntry,name:String) { treeOperation { treeStore.rename(entry.uri,name) } }
    fun deleteFile(entry:DocumentEntry) { treeOperation { treeStore.deleteFile(entry.uri) } }
    fun copyOrMoveFile(entry:DocumentEntry,move:Boolean,policy:CollisionPolicy) {
        treeOperation { current ->
            val created=treeStore.copyOrMove(entry.uri,current.treeUri,entry.name,move,policy)
            _message.value=if(created==null) "COLLISION_SKIPPED" else if(move) "FILE_MOVED_VERIFIED" else "FILE_COPIED_VERIFIED"
        }
    }
    private fun treeOperation(block:suspend (DocumentListing)->Unit) { treeRequest {
        val current=tree.value ?: throw OperationFailure("NO_FOLDER_SELECTED")
        block(current)
        treeStore.list(current.treeUri)
    } }
    private fun treeRequest(load:suspend ()->DocumentListing?) { viewModelScope.launch {
        treeOperations.withLock {
            _treeLoad.value=DataLoadStatus.LOADING
            try { _tree.value=load(); _treeLoad.value=DataLoadStatus.READY }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { _treeLoad.value=DataLoadStatus.ERROR; _message.value=stableError(error) }
        }
    } }
    /** Only old reproducible previews/extraction cache. Drafts, trash and resumable jobs stay intact. */
    fun cleanTemporary() { viewModelScope.launch(Dispatchers.IO) {
        beginBusy()
        try {
            val cutoff=System.currentTimeMillis()+1000L
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
            bytes(File(app.filesDir,"transfers")) + bytes(File(app.noBackupFilesDir,"media-transfers")),
            bytes(app.cacheDir),
            bytes(File(app.filesDir,"processing/trash"))
        )
    } }
    suspend fun preview(sourceId:String,page:Int):PreviewFrame = previewSized(sourceId, page, 1600)

    /** Read real AcroForm controls from the immutable source without modifying it. */
    suspend fun pdfFormFields(sourceId:String):List<com.jumlo.app.data.pdf.PdfFormFieldInfo> =
        withContext(Dispatchers.IO) {
            val active = currentCoroutineContext()
            sources.withSeekable(sourceId, UUID.randomUUID().toString()) { file ->
                active.ensureActive()
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(app)
                com.jumlo.app.data.pdf.PdfBoxBackend.open(file, null).use { doc ->
                    active.ensureActive()
                    com.jumlo.app.data.pdf.PdfBoxBackend.formFields(doc).also {
                        active.ensureActive()
                    }
                }
            }
        }

    /**
     * The original size of a source and how much of it is embedded images, so the
     * compression slider can show a live estimate before the job runs.
     */
    suspend fun compressionProfile(sourceId:String):CompressionProfile? = withContext(Dispatchers.IO) {
        runCatching {
            sources.withSeekable(sourceId,UUID.randomUUID().toString()) { file ->
                val size=file.length()
                if(size<=0L) return@withSeekable null
                val images=runCatching {
                    com.jumlo.app.data.pdf.PdfBoxBackend.open(file,null).use { doc ->
                        com.jumlo.app.data.pdf.PdfBoxBackend.embeddedImageSummary(doc).first
                    }
                }.getOrDefault(0L)
                CompressionProfile(size,images)
            }
        }.getOrNull()
    }
    suspend fun thumbnail(sourceId:String,page:Int):PreviewFrame = previewSized(sourceId, page, 240)
    private suspend fun previewSized(sourceId:String,page:Int,maxEdge:Int):PreviewFrame {
        var pending:PreviewFrame?=null
        try {
            val delivered=withContext(Dispatchers.IO) { decodePreview(sourceId,page,maxEdge).also { pending=it } }
            pending=null
            return delivered
        } finally {
            pending?.bitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }
    private suspend fun decodePreview(sourceId:String,page:Int,maxEdge:Int):PreviewFrame {
        return sources.withSeekable(sourceId,UUID.randomUUID().toString()) { file ->
            currentCoroutineContext().ensureActive()
            val pdf=file.inputStream().use { val signature=ByteArray(5);it.read(signature)==5 && String(signature,Charsets.US_ASCII)=="%PDF-" }
            if(pdf) ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = try { PdfRenderer(fd) } catch (_: SecurityException) { throw OperationFailure("PDF_ENCRYPTED") }
                renderer.use { renderer ->
                require(page in 1..renderer.pageCount) { "PAGE_OUT_OF_RANGE" }
                renderer.openPage(page-1).use { p ->
                    val scale=minOf(2.0,maxEdge.toDouble()/maxOf(p.width,p.height).coerceAtLeast(1))
                    var w=(p.width*scale).roundToInt().coerceAtLeast(1)
                    var h=(p.height*scale).roundToInt().coerceAtLeast(1)
                    val maxPixels=(budget.maxBitmapBytes/4L).coerceAtLeast(512L*512L)
                    if(w.toLong()*h>maxPixels) {
                        val down=sqrt(maxPixels.toDouble()/(w.toDouble()*h))
                        w=(w*down).roundToInt().coerceAtLeast(1)
                        h=(h*down).roundToInt().coerceAtLeast(1)
                    }
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    try { bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);PreviewFrame(bitmap,page,renderer.pageCount,p.width,p.height) }
                    catch(t:Throwable) { bitmap.recycle();throw t }
                }
            } }
            else {
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth>0 && options.outHeight>0) { "PREVIEW_UNAVAILABLE" }
                val width=options.outWidth;val height=options.outHeight;var sample=1
                while(maxOf(width,height)/sample>maxEdge) sample*=2
                options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888
                val original=BitmapFactory.decodeFile(file.path,options) ?: throw OperationFailure("PREVIEW_UNAVAILABLE")
                try {
                    val orientation=try { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) } catch(_:Exception) { 1 }
                    val matrix=Matrix().apply { when(orientation) { 2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f) } }
                    val normalized=if(orientation in 2..8) Bitmap.createBitmap(original,0,0,original.width,original.height,matrix,true) else original
                    if(normalized!==original) original.recycle()
                    PreviewFrame(normalized,1,1,if(orientation in 5..8) height else width,if(orientation in 5..8) width else height)
                } catch(t:Throwable) { original.recycle();throw t }
            }
        }
    }
    override fun onCleared() { super.onCleared() }
}
