package com.jumlo.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumlo.app.core.DirectFileKind
import com.jumlo.app.core.DownloadLinkPolicy
import com.jumlo.app.core.MediaSubmissionKey
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.PlatformHeaderPolicy
import com.jumlo.app.core.BrowserProfilePolicy
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.media.MediaRuntime
import com.jumlo.app.data.media.MediaSessionStore
import com.jumlo.app.data.media.executeCancellable
import com.jumlo.app.data.processing.*
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.JobRequest
import com.jumlo.app.diagnostics.AppDiagnostics
import com.jumlo.app.diagnostics.viewModelErrorBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit
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
    val audio: AudioOutput = AudioOutput.ORIGINAL,
    val bitrate: Int = 192,
    val directMode: String = "ORIGINAL",
    val platformBadge: String? = null,
    val name: String = "",
    val allowHttp: Boolean = true,
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
    private val client: OkHttpClient,
    private val sessionStore: MediaSessionStore
): ViewModel() {
    private val _state=MutableStateFlow(MediaUiState())
    val state=_state.asStateFlow()
    private val errorBoundary=viewModelErrorBoundary("media_view_model") { error ->
        _state.update { it.copy(analyzing=false,submitting=false,error=stableError(error)) }
    }
    private var analysis: Job?=null
    private var sessionEpoch:Long?=null
    private var preferredVideoHeight=1080
    private var generation=0
    private var analyzedUrl: String?=null
    // Debounced URL changes must not restart a probe already requested by Paste,
    // the keyboard Go action, or Retry. Keep this marker after cancel/failure too.
    private var lastAnalysisInput: String?=null
    private var lastSignature: String?=null
    private var lastJob: String?=null
    private val submissions=MediaSubmissionSlot<JobRequest>()
    private val probeClient=client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private fun probeDirect(url:String,allowHttp:Boolean,check:()->Unit):DirectPreview {
        // A socket timeout alone cannot bound asynchronous DNS or a chain of
        // redirects. Keep one inspection budget and cancel the outstanding call
        // when its remaining time expires, leaving editing and Retry available.
        val started = System.nanoTime()
        fun remainingMillis(): Long {
            check()
            val remaining = 20_000L - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (remaining <= 0L) throw SocketTimeoutException("MEDIA_INSPECTION_TIMEOUT")
            return remaining
        }
        var uri=HttpPolicy.url(MediaLinks.normalize(DownloadLinkPolicy.resolveDirectUrl(url),allowHttp),allowHttp)
        var useRangeGet = false
        var profileIndex = 0
        val extraMap = sessionStore.getExtraHeaders().lineSequence()
            .map { it.trim() }
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        repeat(13) { hops ->
            remainingMillis()
            val reqBuilder = Request.Builder().url(uri.toString())
            PlatformHeaderPolicy.headersFor(
                url = uri.toString(),
                customUserAgent = sessionStore.getCustomUserAgent(),
                preferDesktop = sessionStore.isPreferDesktopUa(),
                cookieHeader = sessionStore.cookieHeaderFor(uri.toString()),
                customHeaders = extraMap,
                compatibilityAttempt = profileIndex,
                customHeaderOrigin = url // never forward custom headers to cross-origin redirect targets
            ).forEach { (k, v) -> reqBuilder.header(k, v) }
            val req = if (useRangeGet) reqBuilder.get().header("Range", "bytes=0-0").build() else reqBuilder.head().build()
            probeClient.executeCancellable(req,check,remainingMillis()).use { response ->
                if(response.code in setOf(301,302,303,307,308)) {
                    require(hops<10) { "TOO_MANY_REDIRECTS" }
                    val location=response.header("Location") ?: throw IllegalArgumentException("INVALID_REDIRECT")
                    val next=HttpPolicy.url(MediaLinks.normalize(uri.resolve(location).toString(),allowHttp),allowHttp)
                    require(uri.scheme!="https" || next.scheme=="https") { "INSECURE_REDIRECT" }
                    uri=next
                    profileIndex=0
                } else if (BrowserProfilePolicy.tryNext(response.code, profileIndex, BrowserProfilePolicy.forUrl(uri.toString()).size)) {
                    profileIndex++
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

    /** Reset ViewModel-scoped media state on every workspace entry, including same-route reentry. */
    fun beginSession(initialUrl:String) = beginSession(initialUrl, false)
    fun beginSession(initialUrl:String, @Suppress("UNUSED_PARAMETER") defaultWifiOnly:Boolean) {
        generation++
        analysis?.cancel()
        analyzedUrl=null
        lastAnalysisInput=null
        newCopy()
        val trimmed = initialUrl.trim().take(32768)
        _state.value=MediaUiState(
            url=trimmed,
            mode=MediaMode.VIDEO,
            platformBadge=DownloadLinkPolicy.detectPlatformName(trimmed)
        )
    }

    /** Recomposition/Activity recreation retains this session's fetched choices and filename. */
    fun beginSession(initialUrl:String,epoch:Long,defaultVideoQuality:String,defaultAudioQuality:Int) {
        if(sessionEpoch==epoch) return
        sessionEpoch=epoch
        preferredVideoHeight=when(defaultVideoQuality) { "BEST" -> 0;"720" -> 720;"480" -> 480;else -> 1080 }
        beginSession(initialUrl)
        _state.update { it.copy(bitrate=defaultAudioQuality.takeIf { rate -> rate in setOf(128,192,320) } ?: 192) }
    }

    fun newCopy() { lastSignature=null;lastJob=null;submissions.clear() }

    fun editUrl(value:String) {
        if(_state.value.submitting) return
        generation++;analysis?.cancel();analyzedUrl=null;lastAnalysisInput=null
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
    fun cancelAnalysis() {
        if(_state.value.analyzing) lastAnalysisInput=null
        generation++;analysis?.cancel();_state.update { it.copy(analyzing=false) }
    }
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
        if (_state.value.mode == value) return
        newCopy()
        val asset = _state.value.asset
        val choices = asset?.let { MediaCatalog.choices(it, value) }.orEmpty()
        _state.update { it.copy(mode = value, choices = choices, selectedKey = MediaCatalog.preferred(choices,value,preferredVideoHeight)?.key,
            error = if (asset != null && choices.isEmpty()) "MEDIA_NO_USABLE_FORMAT" else null) }
    }

    /** Inspect a media URL using the available runtime and its actual formats. */
    fun analyzeAsMediaStream() {
        analyze(forceExtractor = true)
    }

    fun analyze(forceExtractor: Boolean = false, automatic: Boolean = false) {
        if(_state.value.submitting) return
        if(!CapabilityRegistry.enabled("dl_media")) { _state.update { it.copy(error=CapabilityRegistry.reason("dl_media")) };return }
        val snapshot=_state.value
        if(automatic && lastAnalysisInput==snapshot.url) return
        lastAnalysisInput=snapshot.url
        val token=++generation
        val previous=analysis
        previous?.cancel()
        _state.update { it.copy(analyzing=true,asset=null,direct=null,choices=emptyList(),selectedKey=null,error=null) }
        analysis=viewModelScope.launch(errorBoundary) {
            previous?.join()
            try {
                val url=MediaLinks.normalize(snapshot.url,snapshot.allowHttp)
                val detectedPlatform=DownloadLinkPolicy.detectPlatformName(url)
                val active=currentCoroutineContext()
                val knownDirect=DownloadLinkPolicy.isDirect(url) || DownloadLinkPolicy.isGoogleDriveDownload(url)
                val direct=if(!forceExtractor && (knownDirect || detectedPlatform==null)) {
                    try { withContext(Dispatchers.IO) { probeDirect(url,snapshot.allowHttp) { active.ensureActive() } } }
                    catch(cancel:CancellationException) { throw cancel }
                    catch(error:Exception) { if(knownDirect) throw error else null }
                } else null
                fun showDirect(preview:DirectPreview,error:String?=null) {
                    if(token==generation) {
                        analyzedUrl=url
                        _state.update { it.copy(direct=preview,platformBadge=detectedPlatform,name=preview.title,
                            selectedKey=null,error=error) }
                    }
                }
                if(direct!=null && direct.kind !in setOf(DirectFileKind.VIDEO,DirectFileKind.AUDIO)) {
                    showDirect(direct)
                } else {
                    try {
                        val asset=withContext(Dispatchers.IO) { runtime.inspect(url,snapshot.allowHttp) { active.ensureActive() } }
                        val videos=MediaCatalog.choices(asset,MediaMode.VIDEO)
                        val audios=MediaCatalog.choices(asset,MediaMode.AUDIO)
                        val effectiveMode=when {
                            videos.isEmpty() -> MediaMode.AUDIO
                            audios.isEmpty() -> MediaMode.VIDEO
                            else -> snapshot.mode
                        }
                        val choices=if(effectiveMode==MediaMode.VIDEO) videos else audios
                        if(choices.isEmpty()) throw OperationFailure("MEDIA_NO_USABLE_FORMAT")
                        if(token==generation) {
                            analyzedUrl=url
                            _state.update {
                                it.copy(asset=asset,mode=effectiveMode,
                                    platformBadge=detectedPlatform ?: asset.extractor.takeIf { e -> !e.equals("generic",true) },
                                    choices=choices,selectedKey=MediaCatalog.preferred(choices,effectiveMode,preferredVideoHeight)?.key,
                                    name=asset.title.ifBlank { "media" },error=null)
                            }
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(error:Exception) {
                        // A validated direct file remains downloadable when its
                        // optional track probe fails. Never fabricate qualities.
                        if(direct!=null) showDirect(direct,stableError(error)) else throw error
                    }
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:Exception) {
                AppDiagnostics.report("media_analysis",e)
                if(token==generation) _state.update { it.copy(error=stableError(e)) }
            }
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
        viewModelScope.launch(errorBoundary) {
            val handles=mutableSetOf<String>()
            var prepared:JobRequest?=null
            var preparedSignature:String?=null
            var assignedHandle:String?=null
            var enqueued=false
            try {
                val selection=if(direct==null) MediaSelectionSettings(MediaCatalog.identity(requireNotNull(asset),url),requireNotNull(choice).key,MediaCatalog.fingerprint(choice),s.mode.name,s.audio.name,s.bitrate) else null
                val customName = s.name.trim().ifBlank { direct?.title ?: asset?.title.orEmpty().ifBlank { "download" } }
                val signature=MediaSubmissionKey.key(
                    selection?.let { Json.encodeToString(it) } ?: "direct:${s.directMode}",
                    url,customName,s.wifiOnly,s.unmetered,s.allowHttp
                )
                if(signature==lastSignature && lastJob!=null) { onQueued(lastJob!!);return@launch }
                val request=submissions.existing(signature) ?: run {
                    val chars=url.toCharArray()
                    val handle=try { secrets.putDownload(chars) } finally { chars.fill('\u0000') }
                    handles+=handle
                    val settings=DownloadSettings(handle,customName,s.wifiOnly,s.unmetered,s.allowHttp,selection,s.directMode)
                    submissions.prepare(signature) {
                        JobRequest(UUID.randomUUID().toString(),if(direct!=null) "dl_02" else "dl_media",emptyList(),1,Json.encodeToString(settings),System.currentTimeMillis())
                    }
                }
                prepared=request
                preparedSignature=signature
                assignedHandle=Json.decodeFromString<DownloadSettings>(request.settingsJson).urlSecretHandle
                handles+=requireNotNull(assignedHandle)
                val job=jobs.enqueue(request)
                enqueued=true
                lastSignature=signature;lastJob=job
                onQueued(job)
            } catch(cancel:CancellationException) { throw cancel }
            catch(e:Exception) {
                AppDiagnostics.report("media_submission",e)
                _state.update { it.copy(error=stableError(e)) }
            }
            finally {
                if(handles.isNotEmpty()) withContext(NonCancellable+Dispatchers.IO) {
                    // enqueue can commit Room and then fail while scheduling. The
                    // durable row owns its URL; a draft without a row does not.
                    val owned=enqueued || prepared?.let { request ->
                        runCatching { jobs.contains(request.id) }.getOrDefault(true)
                    }==true
                    handles.filter { !owned || it!=assignedHandle }.forEach { handle -> runCatching { secrets.remove(handle) } }
                    if(!owned && preparedSignature?.let { submissions.existing(it)?.id }==prepared?.id) submissions.clear()
                }
                _state.update { it.copy(submitting=false) }
            }
        }
    }
}
