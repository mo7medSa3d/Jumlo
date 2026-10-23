package com.jumlo.app.printing

import com.jumlo.app.diagnostics.viewModelErrorBoundary

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.print.PrintJobInfo
import android.print.PrintManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

data class PrintUiState(
    val loaded:Boolean=false,val saved:PrintSavedData=PrintSavedData(),val source:PrintSource?=null,
    val options:PrintOptions=PrintOptions(),val info:PrintDocumentInfo?=null,val pages:List<PrintPage> = emptyList(),
    val orderedPages:List<PrintPage> = emptyList(),
    val layout:PrintLayout?=null,val inspecting:Boolean=false,val error:String?=null,val rangeError:Boolean=false,
    val preparing:Boolean=false,val preparedPages:Int=0,val preparationTotal:Int=0,
    val serviceAvailable:Boolean=true,val historyError:Boolean=false,
    val numericDrafts:Map<String,String> = emptyMap(), val inputErrors:Set<String> = emptySet(),
    val canUndo:Boolean=false,val canRedo:Boolean=false,
    val spoolingPages:Int=0,val spoolingTotal:Int=0,val activeJobId:String?=null,
    val printServicesInstalled:Boolean=true
)
@HiltViewModel class PrintViewModel internal constructor(
    app:Application,private val repository:PrintStore,private val composer:PrintEngine,private val savedState:SavedStateHandle,private val ioDispatcher:CoroutineDispatcher
):AndroidViewModel(app) {
    @Inject constructor(app:Application,repository:PrintRepository,composer:PrintPdfComposer,savedState:SavedStateHandle):this(app,repository,composer,savedState,Dispatchers.IO)
    private val json=Json {ignoreUnknownKeys=true;encodeDefaults=true}
    private val restoredOptions=savedState.get<String>("print_options")?.let {runCatching {json.decodeFromString<PrintOptions>(it)}.getOrNull()}
    private val restoredSource=savedState.get<String>("print_source")?.let {runCatching {json.decodeFromString<PrintSource>(it)}.getOrNull()}
    private val restoredNumericDrafts = savedState.get<String>("print_numeric_drafts")?.let { raw ->
        runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
    }.orEmpty().filter { (key, value) -> value.length <= 24 && key in setOf(
        "customWidth", "customHeight", "imageDpi", "marginLeft", "marginRight", "marginTop", "marginBottom"
    ) }
    private val _state=MutableStateFlow(PrintUiState(source=restoredSource,
        options=restoredOptions ?: PrintOptions(),numericDrafts=restoredNumericDrafts,
        inputErrors=PrintDraftPolicy.errors(restoredNumericDrafts)))
    val state=_state.asStateFlow()
    private val operationScope=CoroutineScope(viewModelScope.coroutineContext + viewModelErrorBoundary("printing") { error ->
        _state.update { it.copy(error=errorCode(error),inspecting=false,preparing=false) }
    })
    private var optionsEdited=restoredOptions!=null
    private val undoStack = ArrayDeque<PrintOptions>()
    private val redoStack = ArrayDeque<PrintOptions>()
    private var activeGesture: String? = null
    private var gestureStart: PrintOptions? = null

    private fun pushUndo(before: PrintOptions) {
        if(undoStack.size >= 32) undoStack.removeFirst()
        undoStack.addLast(before)
        redoStack.clear()
        updateHistoryAvailability()
    }
    private fun updateHistoryAvailability() {
        _state.update { it.copy(canUndo=undoStack.isNotEmpty() || it.inputErrors.isNotEmpty(),
            canRedo=redoStack.isNotEmpty()) }
    }
    /** Each gesture owns its transaction; an older pointer release cannot finish a newer edit. */
    fun beginContinuousEdit(id: String) {
        if(activeGesture == id) return
        activeGesture?.let { endContinuousEdit(it, true) }
        activeGesture = id
        gestureStart = _state.value.options
    }
    fun endContinuousEdit(id: String, commit: Boolean) {
        if(activeGesture != id) return
        val start = gestureStart
        activeGesture = null
        gestureStart = null
        if(start != null && _state.value.options != start) {
            if(commit) pushUndo(start) else edit(start, recordHistory=false)
        }
        updateHistoryAvailability()
    }
    fun undo() {
        if(_state.value.inputErrors.isNotEmpty()) { resetInvalidNumericDrafts(); return }
        if(activeGesture!=null) activeGesture?.let {endContinuousEdit(it, true)}
        if(undoStack.isEmpty()) return
        val old = undoStack.removeLast()
        if(redoStack.size>=32) redoStack.removeFirst()
        redoStack.addLast(_state.value.options)
        edit(old,recordHistory=false)
        updateHistoryAvailability()
    }
    fun redo() {
        if(activeGesture!=null) activeGesture?.let {endContinuousEdit(it, true)}
        if(redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        if(undoStack.size>=32) undoStack.removeFirst()
        undoStack.addLast(_state.value.options)
        edit(next,recordHistory=false)
        updateHistoryAvailability()
    }
    private fun saveNumericDrafts(drafts: Map<String, String>) {
        savedState["print_numeric_drafts"] = json.encodeToString(drafts)
        _state.update { it.copy(numericDrafts=drafts,
            inputErrors=PrintDraftPolicy.errors(drafts)) }
        updateHistoryAvailability()
    }
    fun resetInvalidNumericDrafts() {
        saveNumericDrafts(_state.value.numericDrafts.filterKeys { it !in _state.value.inputErrors })
    }
    fun setNumericDraft(key: String, raw: String) {
        val nextRaw = raw.take(24)
        val next = _state.value.numericDrafts + (key to nextRaw)
        saveNumericDrafts(next)
        val value=PrintDraftPolicy.parse(key,nextRaw) ?: return
        val options=_state.value.options
        val adjusted = when(key) {
            "customWidth" -> options.copy(customWidthMm=value)
            "customHeight" -> options.copy(customHeightMm=value)
            "imageDpi" -> options.copy(imageDpi=value.toInt())
            "marginLeft" -> options.copy(margins=options.margins.copy(left=value*72/25.4))
            "marginRight" -> options.copy(margins=options.margins.copy(right=value*72/25.4))
            "marginTop" -> options.copy(margins=options.margins.copy(top=value*72/25.4))
            "marginBottom" -> options.copy(margins=options.margins.copy(bottom=value*72/25.4))
            else -> return
        }
        if(adjusted != options) edit(adjusted,keepNumericKey=key)
    }
    private var loadJob:Job?=null
    private var saveJob:Job?=null
    private var submitJob:Job?=null
    private val renderLock=Semaphore(1)
    private var activeJob:android.print.PrintJob?=null
    private var lastStates:Map<String,PrintJobState>?=null
    private var dataJob: Job? = null
    init {
        observeSavedData()
        _state.update { it.copy(printServicesInstalled = detectPrintServices()) }
    }

    /**
     * Android exposes no printer list to apps, so the system dialog performs discovery.
     * We can still tell the user up front when no print service is installed at all.
     */
    private fun detectPrintServices(): Boolean = runCatching {
        val intent = android.content.Intent("android.print.service")
        getApplication<Application>().packageManager.queryIntentServices(intent, 0).isNotEmpty()
    }.getOrDefault(true)
    /** Retry the failing persistence observer, not just the PDF inspection stage. */
    private fun observeSavedData() {
        dataJob?.cancel()
        dataJob = operationScope.launch {
            try {
                repository.data.collect { data ->
                    val first = !_state.value.loaded
                    _state.update { current -> current.copy(
                        loaded=true, saved=data,
                        source=current.source ?: if(first && savedState.get<Boolean>("print_source_large")==true) data.sessionSource else null,
                        options=if(first && !optionsEdited) data.lastOptions else current.options,
                        error=if(current.error=="PRINT_STORAGE") null else current.error
                    ) }
                    if(first && _state.value.source!=null) inspect()
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) {_state.update {it.copy(loaded=true,error="PRINT_STORAGE")}}
        }
    }
    /** Recomposition/configuration do not reset choices. A different external source does. */
    fun attach(source:PrintSource?) {
        val identity=PrintSourceIdentity.key(source)
        val same=savedState.get<String>("print_input")==identity
        if(same && (_state.value.source!=null || source==null)) return
        savedState["print_input"]=identity
        open(source,resetRange=!same)
    }
    fun open(source:PrintSource?,resetRange:Boolean=true) {
        loadJob?.cancel();saveJob?.cancel()
        activeGesture = null; gestureStart = null
        undoStack.clear(); redoStack.clear()
        saveNumericDrafts(emptyMap())
        val options=if(resetRange) _state.value.options.reusable() else _state.value.options
        val encoded=source?.let {json.encodeToString(it)}
        // Avoid duplicating large editor selections in the activity's 1 MiB saved-state Binder
        // transaction. Durable session data stores the full selection; the Bundle keeps a key.
        savedState["print_source"]=encoded?.takeIf {it.length<=16_384}
        savedState["print_source_large"]=encoded!=null && encoded.length>16_384
        savedState["print_options"]=json.encodeToString(options)
        _state.update {it.copy(source=source,options=options,info=null,pages=emptyList(),layout=null,error=null,rangeError=false,inspecting=false)}
        launchStore {repository.session(source)}
        if(source!=null && _state.value.loaded) inspect()
    }
    fun edit(options:PrintOptions,keepNumericKey:String?=null,recordHistory:Boolean=true) {
        val previous=_state.value.options
        if(previous!=options && recordHistory && activeGesture==null) pushUndo(previous)
        optionsEdited=true
        val stale=buildSet {
            if(previous.customWidthMm!=options.customWidthMm) add("customWidth")
            if(previous.customHeightMm!=options.customHeightMm) add("customHeight")
            if(previous.imageDpi!=options.imageDpi) add("imageDpi")
            if(previous.margins.left!=options.margins.left) add("marginLeft")
            if(previous.margins.right!=options.margins.right) add("marginRight")
            if(previous.margins.top!=options.margins.top) add("marginTop")
            if(previous.margins.bottom!=options.margins.bottom) add("marginBottom")
            if(previous.paper!=options.paper && options.paper!=PrintPaper.CUSTOM) {
                add("customWidth"); add("customHeight")
            }
        } - listOfNotNull(keepNumericKey).toSet()
        if(stale.isNotEmpty()) saveNumericDrafts(_state.value.numericDrafts - stale)
        savedState["print_options"]=json.encodeToString(options)
        val dpiChanged=options.imageDpi!=previous.imageDpi
        _state.update {it.copy(options=options,error=null)}
        updateHistoryAvailability()
        if(dpiChanged && _state.value.source?.mimeType?.startsWith("image/")==true) inspect() else recalculate()
        saveJob?.cancel();saveJob=operationScope.launch {delay(300)
            try {repository.settings(options)}
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) {_state.update {it.copy(error="PRINT_STORAGE")}}
        }
    }
    private fun recalculate() {
        val current=_state.value;val source=current.source ?: return;val info=current.info ?: return
        try {
            current.options.validate()
            val base=PrintRanges.sourcePages(source.pages,info.pages.size)
            val ordered=PrintPageSelection.arrange(base,current.options.pageOrder,current.options.pageRotations)
            val pages=PrintRanges.select(ordered,current.options.range)
            val layout=composer.initialLayout(current.options,info,pages)
            pages.forEach { page ->PrintGeometry.place(info.pages[page.sourcePage-1].size(page.rotationDegrees),layout.paper,layout.margins,current.options.scale,current.options.scalePercent)}
            _state.update {it.copy(orderedPages=ordered,pages=pages,layout=layout,rangeError=false,error=null)}
        } catch(e:Exception) {_state.update {it.copy(pages=emptyList(),orderedPages=emptyList(),layout=null,rangeError=e.message=="PRINT_RANGE",error=e.message ?: "PRINT_ERROR")}}
    }

    /** Visual page selection. The range string stays the single source of truth. */
    fun setPageSelection(positions:List<Int>) {
        val current=_state.value
        val range=PrintPageSelection.rangeText(positions,current.orderedPages.size)
        if(range==current.options.range) return
        edit(current.options.copy(range=range))
    }

    /** Move the page at [from] to [to] in the visible order. */
    fun movePage(from:Int,to:Int) {
        val current=_state.value
        val size=current.orderedPages.size
        val next=PrintPageSelection.move(current.options.pageOrder,size,from,to)
        if(next==current.options.pageOrder) return
        edit(current.options.copy(pageOrder=next))
    }

    /**
     * Commit a visible reorder. The selection follows the pages, not the positions:
     * moving a page must never silently print a different set of pages.
     */
    fun applyPageOrder(permutation:List<Int>) {
        val current=_state.value
        val size=current.orderedPages.size
        if(size==0 || permutation.size!=size || permutation.toSet()!=(0 until size).toSet()) return
        val base=current.options.pageOrder.ifEmpty { (1..size).toList() }
        val nextBase=permutation.map { base[it] }
        if(nextBase==base && current.options.pageOrder.isNotEmpty()) return
        val selected=PrintPageSelection.positions(current.options.range,size).toSet()
        val newSelected=permutation.mapIndexedNotNull { index, old -> if(old in selected) index else null }
        edit(current.options.copy(pageOrder=nextBase,range=PrintPageSelection.rangeText(newSelected,size)))
    }

    /** Rotate one page a quarter turn. The rotation is stored against the source order. */
    fun rotatePage(position:Int) {
        val current=_state.value
        val page=current.orderedPages.getOrNull(position) ?: return
        val order=current.options.pageOrder.ifEmpty { (1..current.orderedPages.size).toList() }
        val basePosition=order.getOrNull(position) ?: return
        val next=(page.rotationDegrees+90)%360
        edit(current.options.copy(pageRotations=current.options.pageRotations+(basePosition to next)))
    }
    private fun inspect() {
        loadJob?.cancel();val source=_state.value.source ?: return;val options=_state.value.options
        _state.update {it.copy(inspecting=true,error=null)}
        loadJob=operationScope.launch {
            try {
                val info=withContext(ioDispatcher) {val owner=currentCoroutineContext();composer.inspect(source,options) {owner.ensureActive()}}
                ensureActive();_state.update {it.copy(info=info,inspecting=false)};recalculate();repository.recent(source)
            } catch(cancel:CancellationException) { throw cancel } catch(e:Exception) {_state.update {it.copy(inspecting=false,info=null,error=errorCode(e))}}
        }
    }
    fun retry() {
        if(_state.value.error=="PRINT_STORAGE") observeSavedData()
        else if(_state.value.source!=null) inspect()
    }
    fun clearError() {_state.update {it.copy(error=null)}}
    suspend fun renderPage(source:PrintSource,options:PrintOptions,page:PrintPage,layout:PrintLayout,maxEdge:Int):Bitmap {
        var bitmap:Bitmap?=null
        try {
            return withContext(ioDispatcher) {renderLock.withPermit {
                val owner=currentCoroutineContext();val temp=File.createTempFile("print-preview-",".pdf",getApplication<Application>().cacheDir)
                try {
                    composer.compose(source,options,listOf(page),layout,temp,{owner.ensureActive()})
                    owner.ensureActive();composer.preview(temp,0,maxEdge).also {bitmap=it;owner.ensureActive()}
                } finally {temp.delete()}
            }}
        } catch(t:Throwable) {bitmap?.recycle();throw t}
    }
    fun savePreset(name:String) {
        if(_state.value.inputErrors.isNotEmpty()) {
            _state.update {it.copy(error="PRINT_INVALID_INPUT")};return
        }
        launchStore {repository.preset(name,_state.value.options)}
    }
    fun deletePreset(name:String) = launchStore {repository.deletePreset(name)}
    fun favorite(source:PrintSource) = launchStore {repository.favorite(source)}
    fun applyPreset(preset:PrintPreset) = edit(preset.options.reusable())
    private fun launchStore(action:suspend ()->Unit) {operationScope.launch {try {action()} catch(cancel:CancellationException) {throw cancel} catch(e:Exception) {_state.update {it.copy(error=errorCode(e))}}}}
    fun submit(activity:Activity) {
        val current=_state.value;val source=current.source ?: return;val info=current.info ?: return;val layout=current.layout ?: return
        if(current.preparing || current.pages.isEmpty() || current.inputErrors.isNotEmpty()) return
        val manager=activity.getSystemService(PrintManager::class.java)
        if(manager==null) {_state.update {it.copy(serviceAvailable=false,error="PRINT_UNAVAILABLE")};return}
        _state.update {it.copy(preparing=true,preparedPages=0,preparationTotal=0,error=null)}
        submitJob=operationScope.launch {
            var snapshot:PrintSnapshot?=null
            var submitted=false
            var recorded=false
            try {
                val owned=repository.snapshot(source).also {snapshot=it}.source;ensureActive()
                val adapter=JumloPrintAdapter(getApplication(),owned,current.options,info,composer,
                    progress={done,total ->_state.update {it.copy(spoolingPages=done,spoolingTotal=total)}},
                    finished={_state.update {it.copy(spoolingPages=0,spoolingTotal=0)}})
                val job=manager.print(source.name,adapter,PrintAndroidAttributes.initial(layout,current.options))
                    ?: throw IllegalStateException("PRINT_UNAVAILABLE")
                submitted=true;activeJob=job
                repository.record(PrintHistoryEntry(job.id.toString(),owned,current.options,job.info.state.toPrintState(),System.currentTimeMillis()))
                recorded=true;activeJob=job;lastStates=null
                _state.update {it.copy(preparing=false,activeJobId=job.id.toString())}
            } catch(cancel:CancellationException) {_state.update {it.copy(preparing=false)};throw cancel}
            catch(e:Exception) {_state.update {it.copy(preparing=false,error=errorCode(e))}}
            finally {
                // If the spooler accepted but history persistence failed, keep the lease for the
                // active job rather than delete its source. Normal recorded jobs release safely.
                withContext(NonCancellable) {
                    snapshot?.let {ticket ->
                        if(!submitted || recorded)
                            runCatching {repository.releaseSnapshot(ticket)}
                    }
                }
            }
        }
    }
    fun cancelPreparation() {if(submitJob?.isActive==true) submitJob?.cancel() else runCatching {activeJob?.cancel()};_state.update {it.copy(preparing=false)}}
    /** Public PrintManager has no job listener/progress API. Called while UI lifecycle is STARTED. */
    suspend fun refreshJobs() {
        try {
            val manager=getApplication<Application>().getSystemService(PrintManager::class.java)
            if(manager==null) {_state.update {it.copy(serviceAvailable=false)};return}
            val states=manager.printJobs.associate {it.id.toString() to it.info.state.toPrintState()}
            if(states!=lastStates) {repository.reconcile(states);lastStates=states}
            _state.update {it.copy(historyError=false)}
        } catch(_:CancellationException) {throw CancellationException()}
        catch(_:Exception) {_state.update {it.copy(historyError=true)}}
    }
    fun cancelJob(id:String) {try {getApplication<Application>().getSystemService(PrintManager::class.java)?.printJobs?.firstOrNull {it.id.toString()==id}?.cancel();lastStates=null} catch(_:Exception) {_state.update {it.copy(historyError=true)}}}
    fun retryJob(entry:PrintHistoryEntry) {
        try {
            val job=getApplication<Application>().getSystemService(PrintManager::class.java)?.printJobs?.firstOrNull {it.id.toString()==entry.id}
            if(job?.isFailed==true) {job.restart();lastStates=null;return}
            repository.resolve(entry.source);open(entry.source);edit(entry.options)
        } catch(e:Exception) {_state.update {it.copy(error=errorCode(e))}}
    }
    private fun Int.toPrintState()=when(this) {
        PrintJobInfo.STATE_CREATED->PrintJobState.CREATED;PrintJobInfo.STATE_QUEUED->PrintJobState.QUEUED
        PrintJobInfo.STATE_STARTED->PrintJobState.STARTED;PrintJobInfo.STATE_BLOCKED->PrintJobState.BLOCKED
        PrintJobInfo.STATE_COMPLETED->PrintJobState.COMPLETED;PrintJobInfo.STATE_FAILED->PrintJobState.FAILED
        PrintJobInfo.STATE_CANCELED->PrintJobState.CANCELED;else->PrintJobState.UNKNOWN
    }
    private fun errorCode(e:Exception):String=when {
        e.javaClass.simpleName=="InvalidPasswordException"->"PRINT_PASSWORD"
        e.message?.startsWith("PRINT_")==true->e.message!!
        e.message?.contains("ANIMATED")==true->"PRINT_UNSUPPORTED"
        else->"PRINT_ERROR"
    }
}
