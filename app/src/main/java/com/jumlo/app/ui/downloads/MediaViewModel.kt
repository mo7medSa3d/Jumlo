package com.jumlo.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumlo.app.core.media.*
import com.jumlo.app.core.DownloadLinkPolicy
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.model.DownloadQuality
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
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

data class DirectPreview(val title:String,val sizeBytes:Long?,val mime:String?)

data class MediaUiState(
    val url: String="",val asset: MediaAsset?=null,val direct: DirectPreview?=null,val choices: List<MediaChoice> = emptyList(),
    val selectedKey: String?=null,val mode: MediaMode=MediaMode.VIDEO,val audio: AudioOutput=AudioOutput.ORIGINAL,
    val bitrate: Int=192,val name: String="",val allowHttp: Boolean=false,val wifiOnly: Boolean=false,val unmetered: Boolean=false,
    val termsAccepted: Boolean=false,val analyzing: Boolean=false,val submitting: Boolean=false,val updatingExtractor: Boolean=false,
    val extractorStatus: String?=null,val error: String?=null
)

@HiltViewModel class MediaViewModel @Inject constructor(
    private val runtime: MediaRuntime,
    private val jobs: JobStore,
    private val secrets: EphemeralSecretStore,
    private val preferences: UserPreferencesRepository,
    private val client: OkHttpClient
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
    private val probeClient=client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private fun qualityCap(quality:DownloadQuality):Int=when(quality) {
        DownloadQuality.BALANCED -> 1080;DownloadQuality.BEST -> 0;DownloadQuality.DATA_SAVER -> 480
    }
    private fun probeDirect(url:String,check:()->Unit):DirectPreview {
        var uri=HttpPolicy.url(url,false)
        repeat(6) { hops ->
            check()
            probeClient.newCall(Request.Builder().url(uri.toString()).head().build()).execute().use { response ->
                if(response.code in setOf(301,302,303,307,308)) {
                    require(hops<5) { "TOO_MANY_REDIRECTS" }
                    val location=response.header("Location") ?: throw IllegalArgumentException("INVALID_REDIRECT")
                    val next=HttpPolicy.url(uri.resolve(location).toString(),false)
                    require(uri.scheme!="https" || next.scheme=="https") { "INSECURE_REDIRECT" }
                    uri=next
                } else {
                    val mime=response.header("Content-Type")?.substringBefore(';')
                    require(!mime.equals("text/html",true) && !mime.equals("application/xhtml+xml",true)) { "DOWNLOAD_NOT_A_FILE" }
                    val size=response.header("Content-Length")?.toLongOrNull()?.takeIf { it>=0 }
                    return DirectPreview(DownloadLinkPolicy.cleanName(uri.toString(),response.header("Content-Disposition"),mime),size,mime)
                }
            }
        }
        throw IllegalArgumentException("TOO_MANY_REDIRECTS")
    }

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collect { pref ->
                val mode=if(pref.audioOnlyDefault) MediaMode.AUDIO else MediaMode.VIDEO
                val current=_state.value
                if(current.asset == null || current.mode != mode) {
                    val choices=current.asset?.let { MediaCatalog.choices(it,mode) }.orEmpty()
                    _state.update { it.copy(mode=mode,choices=choices,selectedKey=MediaCatalog.preferred(choices,mode,qualityCap(pref.downloadQuality))?.key) }
                }
            }
        }
    }

    fun acceptTerms(accepted: Boolean) {
        _state.update { it.copy(termsAccepted = accepted) }
        viewModelScope.launch { preferences.setMediaTermsAccepted(accepted) }
    }

    /** Reset ViewModel-scoped media state on every workspace entry, including same-route reentry. */
    fun beginSession(initialUrl:String) {
        generation++
        analysis?.cancel()
        analyzedUrl=null
        lastShared=null
        newCopy()
        _state.value=MediaUiState(url=initialUrl.trim().take(32768),mode=_state.value.mode)
    }

    fun newCopy() { lastSignature=null;lastJob=null;submissions.clear() }

    fun acceptSharedUrl(value:String) {
        if(value.isNotBlank() && value!=lastShared) { lastShared=value;editUrl(value) }
    }
    fun editUrl(value:String) {
        if(_state.value.submitting) return
        generation++;analysis?.cancel();analyzedUrl=null
        _state.update { it.copy(url=value.take(32768),asset=null,direct=null,choices=emptyList(),selectedKey=null,analyzing=false,error=null) }
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
        if(!CapabilityRegistry.enabled("dl_media")) { _state.update { it.copy(error=CapabilityRegistry.reason("dl_media")) };return }
        val snapshot=_state.value
        val token=++generation
        val previous=analysis
        previous?.cancel()
        _state.update { it.copy(analyzing=true,asset=null,direct=null,choices=emptyList(),selectedKey=null,error=null) }
        analysis=viewModelScope.launch {
            previous?.join()
            try {
                val url=MediaLinks.normalize(snapshot.url,snapshot.allowHttp)
                val active=currentCoroutineContext()
                if(DownloadLinkPolicy.isDirect(url)) {
                    val direct=try { withContext(Dispatchers.IO) { probeDirect(url) { active.ensureActive() } } }
                        catch(cancel:CancellationException) { throw cancel }
                        catch(_:Exception) { DirectPreview(DownloadLinkPolicy.cleanName(url),null,null) }
                    if(token==generation) {
                        analyzedUrl=url
                        _state.update { it.copy(direct=direct,name=direct.title,selectedKey=null,error=null) }
                    }
                } else {
                    val asset=withContext(Dispatchers.IO) { runtime.inspect(url,snapshot.allowHttp) { active.ensureActive() } }
                    val choices=MediaCatalog.choices(asset,_state.value.mode)
                    val cap=qualityCap(preferences.settingsFlow.first().downloadQuality)
                    val default=MediaCatalog.preferred(choices,_state.value.mode,cap)
                    if(token==generation) {
                        analyzedUrl=url
                        _state.update { it.copy(asset=asset,choices=choices,selectedKey=default?.key,
                            name=asset.title.ifBlank { "media" },error=if(default==null) "MEDIA_NO_USABLE_FORMAT" else null) }
                    }
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:Exception) { if(token==generation) _state.update { it.copy(error=stableError(e)) } }
            finally { if(token==generation) _state.update { it.copy(analyzing=false) } }
        }
    }
    fun submit(onQueued:(String)->Unit) {
        val s=_state.value
        if(s.submitting || s.analyzing) return
        val url=analyzedUrl ?: return
        val direct=s.direct
        val asset=s.asset
        val choice=s.choices.firstOrNull { it.key==s.selectedKey }
        if(direct==null && (asset==null || choice==null)) return
        if(!CapabilityRegistry.enabled("dl_media")) return
        _state.update { it.copy(submitting=true,error=null) }
        viewModelScope.launch {
            try {
                val selection=if(direct==null) MediaSelectionSettings(MediaCatalog.identity(requireNotNull(asset),url),requireNotNull(choice).key,MediaCatalog.fingerprint(choice),s.mode.name,s.audio.name,s.bitrate) else null
                val signature=(selection?.let { Json.encodeToString(it) } ?: "direct")+"|"+url
                if(signature==lastSignature && lastJob!=null) { onQueued(lastJob!!);return@launch }
                val request=submissions.prepare(signature) {
                    val chars=url.toCharArray()
                    val handle=try { secrets.put(chars) } finally { chars.fill('\u0000') }
                    val filename = direct?.title ?: asset?.title.orEmpty().ifBlank { "media" }
                    val settings=DownloadSettings(handle,filename,false,false,false,selection)
                    JobRequest(UUID.randomUUID().toString(),if(direct!=null) "dl_02" else "dl_media",emptyList(),1,Json.encodeToString(settings),System.currentTimeMillis())
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
