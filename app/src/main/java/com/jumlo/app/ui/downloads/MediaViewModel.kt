package com.jumlo.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumlo.app.core.DirectFileKind
import com.jumlo.app.core.DownloadLinkPolicy
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.PlatformHeaderPolicy
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.media.MediaRuntime
import com.jumlo.app.data.media.MediaSessionStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.processing.*
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.DownloadQuality
import com.jumlo.app.model.JobRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import javax.inject.Inject

data class DirectPreview(
    val title: String,
    val sizeBytes: Long?,
    val mime: String?,
    val kind: DirectFileKind = DirectFileKind.OTHER
)

data class MediaUiState(
    val url: String = "",
    val asset: MediaAsset? = null,
    val direct: DirectPreview? = null,
    val choices: List<MediaChoice> = emptyList(),
    val selectedKey: String? = null,
    val mode: MediaMode = MediaMode.VIDEO,
    val audio: AudioOutput = AudioOutput.MP3,
    val bitrate: Int = 192,
    val directMode: String = "ORIGINAL",
    val platformBadge: String? = null,
    val hasSavedSession: Boolean = false,
    val activeDomains: List<String> = emptyList(),
    val customUserAgent: String = "",
    val extraHeaders: String = "",
    val preferDesktopUa: Boolean = true,
    val name: String = "",
    val allowHttp: Boolean = false,
    val wifiOnly: Boolean = false,
    val unmetered: Boolean = false,
    val analyzing: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null
)

@HiltViewModel class MediaViewModel @Inject constructor(
    private val runtime: MediaRuntime,
    private val jobs: JobStore,
    private val secrets: EphemeralSecretStore,
    private val preferences: UserPreferencesRepository,
    private val client: OkHttpClient,
    private val sessionStore: MediaSessionStore
): ViewModel() {
    private val _state=MutableStateFlow(MediaUiState())
    val state=_state.asStateFlow()
    private var analysis: Job?=null
    private var generation=0
    private var analyzedUrl: String?=null
    private var lastSignature: String?=null
    private var lastJob: String?=null
    private val submissions=MediaSubmissionSlot<JobRequest>()
    private val probeClient=client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private fun qualityCap(quality:DownloadQuality):Int=when(quality) {
        DownloadQuality.BALANCED -> 1080;DownloadQuality.BEST -> 0;DownloadQuality.DATA_SAVER -> 480
    }
    private fun probeDirect(url:String,allowHttp:Boolean,check:()->Unit):DirectPreview {
        var uri=HttpPolicy.url(DownloadLinkPolicy.resolveDirectUrl(url),allowHttp)
        var useRangeGet = false
        val extraMap = sessionStore.getExtraHeaders().lineSequence()
            .map { it.trim() }
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        repeat(11) { hops ->
            check()
            val reqBuilder = Request.Builder().url(uri.toString())
            PlatformHeaderPolicy.headersFor(
                url = uri.toString(),
                customUserAgent = sessionStore.getCustomUserAgent(),
                preferDesktop = sessionStore.isPreferDesktopUa(),
                cookieHeader = sessionStore.cookieHeaderFor(uri.toString()),
                customHeaders = extraMap
            ).forEach { (k, v) -> reqBuilder.header(k, v) }
            val req = if (useRangeGet) reqBuilder.get().header("Range", "bytes=0-0").build() else reqBuilder.head().build()
            probeClient.newCall(req).execute().use { response ->
                if(response.code in setOf(301,302,303,307,308)) {
                    require(hops<10) { "TOO_MANY_REDIRECTS" }
                    val location=response.header("Location") ?: throw IllegalArgumentException("INVALID_REDIRECT")
                    val next=HttpPolicy.url(uri.resolve(location).toString(),allowHttp)
                    require(uri.scheme!="https" || next.scheme=="https") { "INSECURE_REDIRECT" }
                    uri=next
                } else if (!useRangeGet && response.code in setOf(400, 403, 405, 501)) {
                    useRangeGet = true
                } else {
                    if (response.code !in 200..299) throw OperationFailure("HTTP_${response.code}")
                    val mime=response.header("Content-Type")?.substringBefore(';')
                    require(!mime.equals("text/html",true) && !mime.equals("application/xhtml+xml",true)) { "DOWNLOAD_NOT_A_FILE" }
                    val rangeTotal = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.takeIf { it >= 0 }
                    val size = rangeTotal ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 && !useRangeGet }
                    val cleanTitle = DownloadLinkPolicy.cleanName(url,response.header("Content-Disposition"),mime)
                    val kind = DownloadLinkPolicy.classifyDirectFile(cleanTitle, mime)
                    return DirectPreview(cleanTitle, size, mime, kind)
                }
            }
        }
        throw IllegalArgumentException("TOO_MANY_REDIRECTS")
    }

    init {
        refreshSessionState()
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

    private fun refreshSessionState() {
        _state.update {
            it.copy(
                hasSavedSession = sessionStore.hasSavedCookies(),
                activeDomains = sessionStore.activeDomainsSummary(),
                customUserAgent = sessionStore.getCustomUserAgent(),
                extraHeaders = sessionStore.getExtraHeaders(),
                preferDesktopUa = sessionStore.isPreferDesktopUa()
            )
        }
    }

    /** Reset ViewModel-scoped media state on every workspace entry, including same-route reentry. */
    fun beginSession(initialUrl:String) {
        generation++
        analysis?.cancel()
        analyzedUrl=null
        newCopy()
        val trimmed = initialUrl.trim().take(32768)
        _state.value=MediaUiState(
            url=trimmed,
            mode=_state.value.mode,
            platformBadge=DownloadLinkPolicy.detectPlatformName(trimmed),
            hasSavedSession=sessionStore.hasSavedCookies(),
            activeDomains=sessionStore.activeDomainsSummary(),
            customUserAgent=sessionStore.getCustomUserAgent(),
            extraHeaders=sessionStore.getExtraHeaders(),
            preferDesktopUa=sessionStore.isPreferDesktopUa()
        )
    }

    fun syncWebViewSession(currentWebViewUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sessionStore.syncFromWebView(currentWebViewUrl, _state.value.url)
            withContext(Dispatchers.Main) {
                refreshSessionState()
                if (_state.value.url.isNotBlank()) analyze()
            }
        }
    }

    fun importCookies(rawCookies: String, targetDomainOrUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sessionStore.importCookiesText(rawCookies, targetDomainOrUrl.ifBlank { _state.value.url })
            withContext(Dispatchers.Main) {
                refreshSessionState()
                if (_state.value.url.isNotBlank()) analyze()
            }
        }
    }

    fun saveHeaderConfig(userAgent: String, extraHeaders: String, preferDesktop: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            sessionStore.setCustomConfig(userAgent, extraHeaders, preferDesktop)
            withContext(Dispatchers.Main) {
                refreshSessionState()
                if (_state.value.url.isNotBlank()) analyze()
            }
        }
    }

    fun clearSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            sessionStore.clearAllSessions()
            withContext(Dispatchers.Main) { refreshSessionState() }
        }
    }

    fun newCopy() { lastSignature=null;lastJob=null;submissions.clear() }

    fun editUrl(value:String) {
        if(_state.value.submitting) return
        generation++;analysis?.cancel();analyzedUrl=null
        val trimmed = value.take(32768)
        _state.update {
            it.copy(
                url=trimmed,
                asset=null,
                direct=null,
                choices=emptyList(),
                selectedKey=null,
                directMode="ORIGINAL",
                platformBadge=DownloadLinkPolicy.detectPlatformName(trimmed),
                analyzing=false,
                error=null
            )
        }
    }
    fun cancelAnalysis() { generation++;analysis?.cancel();_state.update { it.copy(analyzing=false) } }
    fun allowHttp(value:Boolean) { val url=_state.value.url;editUrl(url);_state.update { it.copy(allowHttp=value) } }
    fun wifi(value:Boolean) { _state.update { it.copy(wifiOnly=value) } }
    fun unmetered(value:Boolean) { _state.update { it.copy(unmetered=value) } }
    fun name(value:String) { _state.update { it.copy(name=value.take(200)) } }
    fun audio(value:AudioOutput) { newCopy(); _state.update { it.copy(audio=value) } }
    fun bitrate(value:Int) { if(value in setOf(64,96,128,160,192,256,320)) { newCopy(); _state.update { it.copy(bitrate=value) } } }
    fun directMode(value:String) {
        if(value in setOf("ORIGINAL","PDF_RECOMMENDED","PDF_EXTREME","PDF_TO_IMAGES","IMAGE_COMPRESSED","IMAGE_TO_PDF")) {
            newCopy()
            _state.update { it.copy(directMode=value) }
        }
    }
    fun select(key:String) { if(_state.value.choices.any { it.key==key }) { newCopy(); _state.update { it.copy(selectedKey=key) } } }
    fun mode(value:MediaMode) {
        newCopy()
        val asset=_state.value.asset
        val choices=asset?.let { MediaCatalog.choices(it,value) }.orEmpty()
        _state.update { it.copy(mode=value,choices=choices,selectedKey=choices.firstOrNull()?.key,error=null) }
    }

    /** Forces full media-extractor inspection on a direct video/audio link so the user can pick qualities or extract MP3 audio. */
    fun analyzeAsMediaStream() {
        analyze(forceExtractor = true)
    }

    fun analyze(forceExtractor: Boolean = false) {
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
                val detectedPlatform=DownloadLinkPolicy.detectPlatformName(url)
                val active=currentCoroutineContext()
                if(!forceExtractor && (DownloadLinkPolicy.isDirect(url) || DownloadLinkPolicy.isGoogleDriveDownload(url))) {
                    val direct=withContext(Dispatchers.IO) { probeDirect(url, snapshot.allowHttp) { active.ensureActive() } }
                    if(token==generation) {
                        analyzedUrl=url
                        _state.update { it.copy(direct=direct,platformBadge=detectedPlatform,name=direct.title,selectedKey=null,error=null) }
                    }
                } else {
                    val tryDirectFirst = !forceExtractor && detectedPlatform == null
                    val probedDirect = if (tryDirectFirst) {
                        runCatching {
                            withContext(Dispatchers.IO) { probeDirect(url, snapshot.allowHttp) { active.ensureActive() } }
                        }.getOrNull()
                    } else null

                    if (probedDirect != null) {
                        if (token == generation) {
                            analyzedUrl = url
                            _state.update { it.copy(direct = probedDirect, platformBadge = detectedPlatform, name = probedDirect.title, selectedKey = null, error = null) }
                        }
                    } else {
                        val asset=withContext(Dispatchers.IO) { runtime.inspect(url,snapshot.allowHttp) { active.ensureActive() } }
                        val hasVideo = asset.formats.any { it.hasVideo }
                        val effectiveMode = if (!hasVideo && asset.formats.any { it.hasAudio }) MediaMode.AUDIO else _state.value.mode
                        val choices=MediaCatalog.choices(asset,effectiveMode)
                        val cap=qualityCap(preferences.settingsFlow.first().downloadQuality)
                        val default=MediaCatalog.preferred(choices,effectiveMode,cap)
                        if(token==generation) {
                            analyzedUrl=url
                            _state.update {
                                it.copy(
                                    asset=asset,
                                    mode=effectiveMode,
                                    platformBadge=detectedPlatform ?: asset.extractor.takeIf { e -> !e.equals("generic", true) },
                                    choices=choices,
                                    selectedKey=default?.key,
                                    name=asset.title.ifBlank { "media" },
                                    error=if(default==null) "MEDIA_NO_USABLE_FORMAT" else null
                                )
                            }
                        }
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
                val customName = s.name.trim().ifBlank { direct?.title ?: asset?.title.orEmpty().ifBlank { "download" } }
                val signature=(selection?.let { Json.encodeToString(it) } ?: "direct:${s.directMode}")+"|"+url+"|"+customName
                if(signature==lastSignature && lastJob!=null) { onQueued(lastJob!!);return@launch }
                val request=submissions.prepare(signature) {
                    val chars=url.toCharArray()
                    val handle=try { secrets.put(chars) } finally { chars.fill('\u0000') }
                    val settings=DownloadSettings(handle,customName,s.wifiOnly,s.unmetered,s.allowHttp,selection,s.directMode)
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
