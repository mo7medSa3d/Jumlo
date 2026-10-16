package com.universalutilities.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalutilities.app.core.Hashing
import com.universalutilities.app.core.HttpPolicy
import com.universalutilities.app.core.MemoryBudget
import com.universalutilities.app.data.files.*
import com.universalutilities.app.data.jobs.*
import com.universalutilities.app.data.output.OutputStore
import com.universalutilities.app.data.preferences.UserPreferencesRepository
import com.universalutilities.app.data.processing.DownloadSettings
import com.universalutilities.app.data.source.*
import com.universalutilities.app.model.*
import com.universalutilities.app.ui.intake.Intake
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.roundToInt

data class PreviewFrame(val bitmap:Bitmap,val page:Int,val count:Int,val width:Int,val height:Int)
data class StorageUsage(val sources:Long=0,val outputs:Long=0,val partials:Long=0,val temporary:Long=0)

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
    private val budget:WorkBudget
):AndroidViewModel(app) {
    val settings=preferences.settingsFlow.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppSettingsState())
    val imported=sources.records.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val outputs=outputStore.records.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val jobs=jobStore.snapshots.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _form=MutableStateFlow(WorkspaceDraft())
    val form=_form.asStateFlow()
    private val _busy=MutableStateFlow(false)
    val busy=_busy.asStateFlow()
    private val _message=MutableStateFlow<String?>(null)
    val message=_message.asStateFlow()
    private val _urls=MutableStateFlow("")
    val urls=_urls.asStateFlow()
    private val _tree=MutableStateFlow<DocumentListing?>(null)
    val tree=_tree.asStateFlow()
    private val _storage=MutableStateFlow(StorageUsage())
    val storage=_storage.asStateFlow()
    private val initialized=CompletableDeferred<Unit>()
    private val submitting=AtomicBoolean(false)
    private var lastFingerprint:String?=null
    private var lastJobId:String?=null
    var pendingExport:OutputRecord?=null
        private set

    init {
        viewModelScope.launch {
            try { drafts.loadWorkspace()?.takeIf { it.version==1 }?.let { _form.value=it } }
            catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { _message.value="DRAFT_RESTORE_FAILED" }
            finally { initialized.complete(Unit) }
            _form.debounce(300).collect { snapshot ->
                try { drafts.saveWorkspace(snapshot) }
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
    fun dismissMessage() { _message.value=null }
    fun notifyMessage(code:String) { _message.value=code }
    fun setUrls(value:String) { _urls.value=value.take(100000) }
    fun updateForm(change:(WorkspaceDraft)->WorkspaceDraft) { _form.update(change); lastFingerprint=null; lastJobId=null }
    fun chooseTool(id:String):Boolean {
        if(!CapabilityRegistry.enabled(id)) { _message.value=CapabilityRegistry.reason(id); return false }
        if(_form.value.toolId!=id) updateForm { WorkspaceDraft.forTool(id,it.sourceIds) }
        return true
    }
    fun addSource(id:String) { updateForm { it.copy(sourceIds=(it.sourceIds+id).distinct().take(1000)) } }
    fun removeSource(id:String) { updateForm { it.copy(sourceIds=it.sourceIds-id) } }
    fun clearQueue() { updateForm { it.copy(sourceIds=emptyList()) } }
    fun moveSource(index:Int,delta:Int) { updateForm { old ->
        val list=old.sourceIds.toMutableList();val to=index+delta
        if(index in list.indices && to in list.indices) list.add(to,list.removeAt(index))
        old.copy(sourceIds=list)
    } }
    fun intake(intake:Intake) {
        intake.sharedText?.takeIf { it.isNotBlank() }?.let(::setUrls)
        if(intake.uris.isEmpty()) return
        viewModelScope.launch {
            initialized.await();_busy.value=true
            try {
                for(uri in intake.uris) {
                    try { addSource(sources.inspect(uri,intake.offeredFlags).id) }
                    catch(cancel:CancellationException) { throw cancel }
                    catch(error:Exception) { _message.value=stableError(error) }
                }
                refreshStorage()
            } finally { _busy.value=false }
        }
    }
    fun importOutput(record:OutputRecord) { intake(Intake(listOf(record.uri),Intent.FLAG_GRANT_READ_URI_PERMISSION,null)) }
    fun submit(onQueued:(String)->Unit) {
        if(!submitting.compareAndSet(false,true)) return
        _busy.value=true
        viewModelScope.launch {
            var newHandle:String?=null
            try {
                initialized.await()
                val state=form.value
                require(CapabilityRegistry.enabled(state.toolId)) { "CAPABILITY_UNAVAILABLE" }
                val download=state.toolId.startsWith("dl_")
                require(download || state.sourceIds.isNotEmpty()) { "NO_SOURCES" }
                if(state.toolId in setOf("pdf_07","file_08")) require(state.sourceIds.size==1) { "ONE_SOURCE_REQUIRED" }
                val urlSnapshot=urls.value
                if(download) {
                    val lines=urlSnapshot.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    require(lines.isNotEmpty() && lines.size<=100 && (state.toolId=="dl_03" || lines.size==1)) { "INVALID_DOWNLOAD_LIST" }
                    lines.forEach { HttpPolicy.url(it,state.cleartextConsent) }
                }
                val fingerprint=Hashing.sha256(ByteArrayInputStream((Json.encodeToString(state)+"\n"+if(download) urlSnapshot else "").toByteArray()))
                if(lastFingerprint==fingerprint && lastJobId!=null) { onQueued(lastJobId!!); return@launch }
                newHandle=if(download) secrets.put(urlSnapshot.toCharArray()) else null
                val settings=state.executionSettings(newHandle)
                val request=JobRequest(UUID.randomUUID().toString(),state.toolId,if(download) emptyList() else state.sourceIds,1,settings,System.currentTimeMillis())
                val id=jobStore.enqueue(request)
                lastFingerprint=fingerprint;lastJobId=id;newHandle=null
                onQueued(id) // Queued is not completed. UI observes Room for every later transition.
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { _message.value=stableError(error) }
            finally { newHandle?.let(secrets::remove);_busy.value=false;submitting.set(false) }
        }
    }
    fun allowNewRun() { lastFingerprint=null;lastJobId=null }
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
    fun setThemeMode(mode:ThemeMode) { viewModelScope.launch { preferences.setThemeMode(mode) } }
    fun setLanguage(language:AppLanguage) { viewModelScope.launch { preferences.setLanguage(language) } }
    fun notificationsGranted(granted:Boolean) { viewModelScope.launch { preferences.setNotificationsGranted(granted) } }
    fun prepareExport(output:OutputRecord) { pendingExport=output }
    fun export(target:String?) {
        val output=pendingExport ?: return;pendingExport=null
        if(target==null) return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value=true
            try {
                require(Uri.parse(target).scheme=="content") { "CONTENT_URI_REQUIRED" }
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
            finally { _busy.value=false }
        }
    }
    fun acceptTree(uri:String,flags:Int) { viewModelScope.launch { try { _tree.value=treeStore.accept(uri,flags) } catch(cancel:CancellationException) { throw cancel } catch(error:Exception) { _message.value=stableError(error) } } }
    fun createFolder(name:String) { treeOperation { treeStore.createFolder(it.treeUri,name) } }
    fun renameFile(entry:DocumentEntry,name:String) { treeOperation { treeStore.rename(entry.uri,name) } }
    fun deleteFile(entry:DocumentEntry) { treeOperation { treeStore.deleteFile(entry.uri) } }
    private fun treeOperation(block:suspend (DocumentListing)->Unit) { viewModelScope.launch {
        try { val current=tree.value ?: throw OperationFailure("NO_FOLDER_SELECTED");block(current);_tree.value=treeStore.list(current.treeUri) }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) { _message.value=stableError(error) }
    } }
    fun refreshStorage() { viewModelScope.launch(Dispatchers.IO) {
        fun bytes(file:File):Long { var total=0L;file.walkTopDown().forEach { if(it.isFile) total+=it.length() };return total }
        _storage.value=StorageUsage(bytes(File(app.filesDir,"inbound")),bytes(File(app.filesDir,"processing/results")),bytes(File(app.filesDir,"transfers")),bytes(app.cacheDir))
    } }
    suspend fun preview(sourceId:String,page:Int):PreviewFrame {
        var pending:PreviewFrame?=null
        try {
            val delivered=withContext(Dispatchers.IO) { decodePreview(sourceId,page).also { pending=it } }
            pending=null
            return delivered
        } finally {
            // withContext has prompt cancellation: reclaim a decoded frame not delivered to Compose.
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
                    val scale=minOf(1.8,1280.0/maxOf(p.width,p.height));val w=(p.width*scale).roundToInt().coerceAtLeast(1);val h=(p.height*scale).roundToInt().coerceAtLeast(1)
                    MemoryBudget.requireBitmaps(w,h,1,budget.maxBitmapBytes)
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    try { bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,Matrix().apply { setScale(w.toFloat()/p.width,h.toFloat()/p.height) },PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);PreviewFrame(bitmap,page,renderer.pageCount,p.width,p.height) }
                    catch(t:Throwable) { bitmap.recycle();throw t }
                }
            } }
            else {
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth>0 && options.outHeight>0) { "PREVIEW_UNAVAILABLE" }
                val width=options.outWidth;val height=options.outHeight;var sample=1
                while(maxOf(width,height)/sample>1280) sample*=2
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
    override fun onCleared() { super.onCleared(); /* Worker-owned secret handles remain until job completion or process death. */ }
}
