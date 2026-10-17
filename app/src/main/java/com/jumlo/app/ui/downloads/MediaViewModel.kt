package com.jumlo.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.media.MediaRuntime
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.processing.*
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.JobRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

data class MediaUiState(
    val url: String="",val asset: MediaAsset?=null,val choices: List<MediaChoice> = emptyList(),
    val selectedKey: String?=null,val mode: MediaMode=MediaMode.VIDEO,val audio: AudioOutput=AudioOutput.ORIGINAL,
    val bitrate: Int=192,val name: String="",val allowHttp: Boolean=false,val wifiOnly: Boolean=false,val unmetered: Boolean=false,
    val termsAccepted: Boolean=false,val analyzing: Boolean=false,val submitting: Boolean=false,val updatingExtractor: Boolean=false,
    val extractorStatus: String?=null,val error: String?=null
)

@HiltViewModel class MediaViewModel @Inject constructor(
    private val runtime: MediaRuntime,
    private val jobs: JobStore,
    private val secrets: EphemeralSecretStore,
    private val preferences: UserPreferencesRepository
): ViewModel() {
    private val _state=MutableStateFlow(MediaUiState())
    val state=_state.asStateFlow()
    private var analysis: Job?=null
    private var generation=0
    private var analyzedUrl: String?=null
    private var lastShared: String?=null
    private var lastSignature: String?=null
    private var lastJob: String?=null
    private val submissions=MediaSubmissionSlot<JobRequest>()

    init {
        viewModelScope.launch {
            preferences.settingsFlow.map { it.mediaTermsAccepted }.distinctUntilChanged().collect { accepted ->
                _state.update { it.copy(termsAccepted = accepted) }
            }
        }
    }

    fun acceptTerms(accepted: Boolean) {
        _state.update { it.copy(termsAccepted = accepted) }
        viewModelScope.launch { preferences.setMediaTermsAccepted(accepted) }
    }

    fun newCopy() { lastSignature=null;lastJob=null;submissions.clear() }

    fun acceptSharedUrl(value:String) {
        if(value.isNotBlank() && value!=lastShared) { lastShared=value;editUrl(value) }
    }
    fun editUrl(value:String) {
        if(_state.value.submitting) return
        generation++;analysis?.cancel();analyzedUrl=null
        _state.update { it.copy(url=value.take(32768),asset=null,choices=emptyList(),selectedKey=null,analyzing=false,error=null) }
    }
    fun cancelAnalysis() { generation++;analysis?.cancel();_state.update { it.copy(analyzing=false) } }
    fun allowHttp(value:Boolean) { val url=_state.value.url;editUrl(url);_state.update { it.copy(allowHttp=value) } }
    fun wifi(value:Boolean) { _state.update { it.copy(wifiOnly=value) } }
    fun unmetered(value:Boolean) { _state.update { it.copy(unmetered=value) } }
    fun name(value:String) { _state.update { it.copy(name=value.take(200)) } }
    fun audio(value:AudioOutput) { _state.update { it.copy(audio=value) } }
    fun bitrate(value:Int) { if(value in setOf(64,96,128,160,192,256,320)) _state.update { it.copy(bitrate=value) } }
    fun select(key:String) { if(_state.value.choices.any { it.key==key }) _state.update { it.copy(selectedKey=key) } }
    fun mode(value:MediaMode) {
        val asset=_state.value.asset
        val choices=asset?.let { MediaCatalog.choices(it,value) }.orEmpty()
        _state.update { it.copy(mode=value,choices=choices,selectedKey=choices.firstOrNull()?.key,error=null) }
    }
    fun refreshExtractor() {
        if(_state.value.updatingExtractor || _state.value.submitting) return
        _state.update { it.copy(updatingExtractor=true,extractorStatus=null,error=null) }
        viewModelScope.launch {
            try {
                val status=withContext(Dispatchers.IO) { runtime.refreshExtractor() }
                _state.update { it.copy(extractorStatus=status) }
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:Exception) { _state.update { it.copy(error=stableError(e)) } }
            finally { _state.update { it.copy(updatingExtractor=false) } }
        }
    }
    fun analyze() {
        if(_state.value.submitting) return
        if(!_state.value.termsAccepted) { _state.update { it.copy(error="MEDIA_TERMS_REQUIRED") };return }
        if(!CapabilityRegistry.enabled("dl_media")) { _state.update { it.copy(error=CapabilityRegistry.reason("dl_media")) };return }
        val snapshot=_state.value
        val token=++generation
        val previous=analysis
        previous?.cancel()
        _state.update { it.copy(analyzing=true,asset=null,choices=emptyList(),selectedKey=null,error=null) }
        analysis=viewModelScope.launch {
            previous?.join()
            try {
                val url=MediaLinks.normalize(snapshot.url,snapshot.allowHttp)
                val active=currentCoroutineContext()
                val asset=withContext(Dispatchers.IO) { runtime.inspect(url,snapshot.allowHttp) { active.ensureActive() } }
                val choices=MediaCatalog.choices(asset,_state.value.mode)
                if(token==generation) {
                    analyzedUrl=url
                    _state.update { it.copy(asset=asset,choices=choices,selectedKey=choices.firstOrNull()?.key,
                        name=asset.title.ifBlank { "media" },error=if(choices.isEmpty()) "MEDIA_NO_USABLE_FORMAT" else null) }
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:Exception) { if(token==generation) _state.update { it.copy(error=stableError(e)) } }
            finally { if(token==generation) _state.update { it.copy(analyzing=false) } }
        }
    }
    fun submit(onQueued:(String)->Unit) {
        val s=_state.value
        if(s.submitting || s.analyzing || !s.termsAccepted) return
        val asset=s.asset ?: return
        val choice=s.choices.firstOrNull { it.key==s.selectedKey } ?: return
        val url=analyzedUrl ?: return
        if(!CapabilityRegistry.enabled("dl_media")) return
        _state.update { it.copy(submitting=true,error=null) }
        viewModelScope.launch {
            try {
                val selection=MediaSelectionSettings(MediaCatalog.identity(asset,url),choice.key,MediaCatalog.fingerprint(choice),s.mode.name,s.audio.name,s.bitrate)
                val signature=Json.encodeToString(selection)+"|"+s.name+"|"+s.allowHttp+"|"+s.wifiOnly+"|"+s.unmetered
                if(signature==lastSignature && lastJob!=null) { onQueued(lastJob!!);return@launch }
                val request=submissions.prepare(signature) {
                    val chars=url.toCharArray()
                    val handle=try { secrets.put(chars) } finally { chars.fill('\u0000') }
                    val filename = s.name.ifBlank { asset.title.ifBlank { "media" } }
                    val settings=DownloadSettings(handle,filename,s.wifiOnly,s.unmetered,s.allowHttp,selection)
                    JobRequest(UUID.randomUUID().toString(),"dl_media",emptyList(),1,Json.encodeToString(settings),System.currentTimeMillis())
                }
                val job=jobs.enqueue(request)
                lastSignature=signature;lastJob=job
                onQueued(job)
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:Exception) { _state.update { it.copy(error=stableError(e)) } }
            finally { _state.update { it.copy(submitting=false) } }
        }
    }
}
