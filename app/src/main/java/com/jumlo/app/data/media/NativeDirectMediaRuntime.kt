package com.jumlo.app.data.media
import android.content.Context
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.PlatformHeaderPolicy
import com.jumlo.app.core.BrowserProfilePolicy
import com.jumlo.app.core.StreamTransfer
import com.jumlo.app.core.TransferRequest
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.processing.OkHttpTransport
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fetches verified original media resources. Static HTML5 sources are supported;
 * DRM, JavaScript-rendered social sites and adaptive manifests are not falsely advertised.
 */
internal class NativeDirectMediaRuntime(client: OkHttpClient, private val sessions: MediaSessionStore) : MediaRuntime {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val audioExt = setOf("m4a", "mp3", "ogg", "opus", "flac", "wav", "aac")
    private val videoExt = setOf("mp4", "m4v", "mov", "webm", "mkv", "ts")
    private val mimeToExt = mapOf(
        "video/mp4" to "mp4", "video/quicktime" to "mov", "video/webm" to "webm",
        "video/x-matroska" to "mkv", "video/mp2t" to "ts", "audio/mp4" to "m4a",
        "audio/mpeg" to "mp3", "audio/ogg" to "ogg", "audio/opus" to "opus",
        "audio/flac" to "flac", "audio/wav" to "wav", "audio/aac" to "aac"
    )
    private data class Remote(val url: String, val extension: String, val title: String, val size: Long?)
    private data class Inspection(val name: String, val extractor: String, val streams: List<Remote>, val thumbnailUrl: String? = null)
    private data class PageRead(val url: String, val html: String, val profileIndex: Int)

    private fun headers(url: String, profileIndex: Int = 0): Map<String, String> {
        val custom = sessions.getExtraHeaders().lineSequence().map(String::trim)
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        return PlatformHeaderPolicy.headersFor(
            url = url, customUserAgent = sessions.getCustomUserAgent(),
            preferDesktop = sessions.isPreferDesktopUa(),
            cookieHeader = sessions.cookieHeaderFor(url), customHeaders = custom,
            compatibilityAttempt = profileIndex
        )
    }

    private fun request(url: String, rangeGet: Boolean = false, profileIndex: Int = 0): Request = Request.Builder().url(url).apply {
        headers(url, profileIndex).forEach { (name, value) -> header(name, value) }
        if (rangeGet) get().header("Range", "bytes=0-0") else head()
    }.build()

    private fun inspectDirect(url: String, allowHttp: Boolean, check: () -> Unit): Remote {
        var current = HttpPolicy.url(url, allowHttp)
        var rangeGet = false
        var profileIndex = 0
        repeat(14) { hop ->
            check()
            http.newCall(request(current.toString(), rangeGet, profileIndex)).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    if (hop >= 10) throw OperationFailure("TOO_MANY_REDIRECTS")
                    val location = response.header("Location") ?: throw OperationFailure("INVALID_REDIRECT")
                    val next = HttpPolicy.url(current.resolve(location).toString(), allowHttp)
                    if (current.scheme == "https" && next.scheme != "https") throw OperationFailure("INSECURE_REDIRECT")
                    current = next
                    profileIndex = 0
                    return@repeat
                }
                if (BrowserProfilePolicy.tryNext(response.code, profileIndex, BrowserProfilePolicy.forUrl(current.toString()).size)) {
                    profileIndex++
                    return@repeat
                }
                if (!rangeGet && response.code in setOf(400, 403, 405, 501)) {
                    rangeGet = true
                    return@repeat
                }
                if (response.code !in 200..299) throw OperationFailure("HTTP_${response.code}")
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                    ?.lowercase(Locale.ROOT).orEmpty()
                if (mime in setOf("text/html", "application/xhtml+xml"))
                    throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                val pathExt = current.path.orEmpty().substringAfterLast('/')
                    .substringAfterLast('.', "").lowercase(Locale.ROOT)
                val extension = pathExt.takeIf { it in audioExt || it in videoExt } ?: mimeToExt[mime]
                    ?: throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                val filename = com.jumlo.app.core.DownloadLinkPolicy.cleanName(current.toString(),
                    response.header("Content-Disposition"), mime)
                val title = filename.substringBeforeLast('.', filename).take(180).ifBlank { "media" }
                val rangeTotal = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                val bytes = rangeTotal ?: if (!rangeGet) response.header("Content-Length")?.toLongOrNull() else null
                return Remote(current.toString(), extension, title, bytes?.takeIf { it > 0L })
            }
        }
        throw OperationFailure("TOO_MANY_REDIRECTS")
    }

    private fun readPage(url: String, allowHttp: Boolean, check: () -> Unit,
        preferredProfileIndex: Int = 0): PageRead {
        var current = HttpPolicy.url(url, allowHttp)
        var profileIndex = preferredProfileIndex
        repeat(13) { hop ->
            check()
            val req = Request.Builder().url(current.toString()).get().apply {
                headers(current.toString(), profileIndex).forEach { (k, v) -> header(k, v) }
                header("Accept", "text/html,application/xhtml+xml")
            }.build()
            http.newCall(req).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    if (hop >= 10) throw OperationFailure("TOO_MANY_REDIRECTS")
                    val location = response.header("Location") ?: throw OperationFailure("INVALID_REDIRECT")
                    val next = HttpPolicy.url(current.resolve(location).toString(), allowHttp)
                    if (current.scheme == "https" && next.scheme != "https") throw OperationFailure("INSECURE_REDIRECT")
                    current = next
                    profileIndex = 0
                    return@repeat
                }
                if (BrowserProfilePolicy.tryNext(response.code, profileIndex, BrowserProfilePolicy.forUrl(current.toString()).size)) {
                    profileIndex++
                    return@repeat
                }
                if (response.code !in 200..299) throw OperationFailure("HTTP_${response.code}")
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                if (mime !in setOf("text/html", "application/xhtml+xml")) throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                if ((response.body?.contentLength() ?: -1L) > 262144L) throw OperationFailure("MEDIA_PAGE_TOO_LARGE")
                val input = response.body?.byteStream() ?: throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                val buffer = ByteArray(8192)
                val data = ByteArrayOutputStream()
                while (true) {
                    check()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (data.size() + count > 262144) throw OperationFailure("MEDIA_PAGE_TOO_LARGE")
                    data.write(buffer, 0, count)
                }
                return PageRead(current.toString(), data.toString(Charsets.UTF_8.name()), profileIndex)
            }
        }
        throw OperationFailure("TOO_MANY_REDIRECTS")
    }

    private fun inspectCandidates(url: String, allowHttp: Boolean, check: () -> Unit): Inspection {
        val direct = try { inspectDirect(url, allowHttp, check) }
        catch (error: OperationFailure) {
            if (error.message != "MEDIA_SITE_UNSUPPORTED") throw error
            null
        }
        if (direct != null) return Inspection(direct.title, "DirectMedia", listOf(direct))
        // Public pages may return different static media markup for mobile vs
        // desktop clients. Attempt at most one alternative *after* 200 HTML
        // without any server-verified media. Authentication denials remain terminal.
        val profiles = BrowserProfilePolicy.forUrl(url)
        var nextProfile = 0
        repeat(profiles.size) {
            check()
            val currentPage = readPage(url, allowHttp, check, preferredProfileIndex = nextProfile)
            val pageUrl = currentPage.url
            val html = currentPage.html
            val sources = Html5MediaDiscovery.discover(pageUrl, html, allowHttp)
            val streams = mutableListOf<Remote>()
            for (source in sources) {
                check()
                try {
                    val stream = inspectDirect(source.url, allowHttp, check)
                    if (stream.extension == source.extension || stream.extension in audioExt + videoExt)
                        streams += stream
                } catch (cancel: java.util.concurrent.CancellationException) { throw cancel }
                catch (_: Exception) { /* One inaccessible candidate must not hide another verified resource. */ }
            }
            if (streams.isNotEmpty()) return Inspection(
                Html5MediaDiscovery.title(html) ?: streams.first().title, "Html5", streams,
                thumbnailUrl = runCatching { Html5MediaDiscovery.thumbnail(pageUrl, html) }.getOrNull())
            if (!BrowserProfilePolicy.tryAlternatePage(streams.isNotEmpty(), currentPage.profileIndex, profiles.size))
                throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
            nextProfile = currentPage.profileIndex + 1
        }
        throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
    }

    override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset {
        val normalized = MediaLinks.normalize(url, allowHttp)
        val found = inspectCandidates(normalized, allowHttp, check)
        val formats = found.streams.mapIndexed { index, source ->
            val isAudio = source.extension in audioExt
            MediaFormat(id = if (found.extractor == "DirectMedia") "source" else "page_$index",
                extension = source.extension, videoCodec = if (isAudio) "none" else "direct",
                audioCodec = if (isAudio) "direct" else "none", bytes = source.size,
                protocol = URI(source.url).scheme.lowercase(Locale.ROOT))
        }
        return MediaAsset(
            id = Hashing.sha256(ByteArrayInputStream(normalized.toByteArray(Charsets.UTF_8))),
            extractor = found.extractor, title = found.name, durationSeconds = null,
            formats = formats, thumbnailUrl = found.thumbnailUrl
        )
    }

    override fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit,
        progress: (MediaProgress) -> Unit) {
        check()
        if (audio != AudioOutput.ORIGINAL) throw OperationFailure("MEDIA_CONVERSION_UNAVAILABLE")
        if (choice.requiresMux || choice.formats.size != 1)
            throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
        if (!directory.isDirectory) throw OperationFailure("MEDIA_WORKSPACE_MISSING")
        val normalized = MediaLinks.normalize(url, allowHttp)
        val found = inspectCandidates(normalized, allowHttp, check)
        val id = choice.formatIds.single()
        val index = if (found.extractor == "DirectMedia" && id == "source") 0
            else if (found.extractor == "Html5") id.removePrefix("page_").toIntOrNull()?.takeIf { id == "page_$it" }
            else null
        val source = index?.let { found.streams.getOrNull(it) } ?: throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
        if (source.extension != choice.formats.single().extension) throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
        val partial = File(directory, "native-media.part")
        // Direct transfers can retry safely; incomplete sources are never published.
        if (partial.exists() && !partial.delete()) throw OperationFailure("MEDIA_WORKSPACE_LIMIT")
        val target = File(directory, "media.${source.extension}")
        val result = StreamTransfer(OkHttpTransport(http, ::headers)).execute(
            TransferRequest(url = source.url, partialFile = partial,
                cleartextConsent = allowHttp, maxBytes = 8L * 1024 * 1024 * 1024,
                minFreeBytes = 64L * 1024 * 1024),
            checkCancel = check,
            progress = { done, total -> progress(MediaProgress(id, done, total, null, null, null)) }
        )
        check()
        if (result.mimeType in setOf("text/html", "application/xhtml+xml")) {
            partial.delete()
            throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
        }
        Files.move(partial.toPath(), target.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        try { MediaOutputPolicy.inspect(target) }
        catch (error: Exception) { target.delete(); throw error }
        check()
    }
}
