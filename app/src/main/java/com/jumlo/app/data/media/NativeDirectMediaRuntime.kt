package com.jumlo.app.data.media
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.PlatformHeaderPolicy
import com.jumlo.app.core.BrowserProfilePolicy
import com.jumlo.app.core.StreamTransfer
import com.jumlo.app.core.TransferRequest
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.processing.OkHttpTransport
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
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
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private companion object {
        val AUDIO_EXT = setOf("m4a", "mp3", "ogg", "opus", "flac", "wav", "aac")
        val VIDEO_EXT = setOf("mp4", "m4v", "mov", "webm", "mkv", "ts")
        val MIME_TO_EXT = mapOf(
            "video/mp4" to "mp4", "video/quicktime" to "mov", "video/webm" to "webm",
            "video/x-matroska" to "mkv", "video/mp2t" to "ts", "audio/mp4" to "m4a",
            "audio/mpeg" to "mp3", "audio/ogg" to "ogg", "audio/opus" to "opus",
            "audio/flac" to "flac", "audio/wav" to "wav", "audio/aac" to "aac"
        )
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val RANGE_FALLBACK_CODES = setOf(400, 403, 405, 501)
        val HTML_MIMES = setOf("text/html", "application/xhtml+xml")
        val LINKEDIN_VIDEO_REGEX = Regex("https://dms\\.licdn\\.com/playlist/vid/[^\\s\"'<>\\\\]+")
        val LINKEDIN_DOC_REGEX = Regex("https://media\\.licdn\\.com/dms/document/[^\\s\"'<>\\\\]+")
        val LINKEDIN_IMG_REGEX = Regex("https://media\\.licdn\\.com/dms/image/[^\\s\"'<>\\\\]+(?:feedshare|article-cover)[^\\s\"'<>\\\\]*")
    }
    private data class Remote(val url: String, val extension: String, val title: String, val size: Long?)
    private data class Inspection(val name: String, val extractor: String, val streams: List<Remote>, val thumbnailUrl: String? = null)
    private data class PageRead(val url: String, val html: String, val profileIndex: Int)

    private fun headers(url: String, profileIndex: Int = 0, origin: String = url): Map<String, String> {
        val custom = sessions.getExtraHeaders().lineSequence().map(String::trim)
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        return PlatformHeaderPolicy.headersFor(
            url = url, customUserAgent = sessions.getCustomUserAgent(),
            preferDesktop = sessions.isPreferDesktopUa(),
            cookieHeader = sessions.cookieHeaderFor(url), customHeaders = custom,
            compatibilityAttempt = profileIndex,
            customHeaderOrigin = origin
        )
    }

    private fun request(url: String, rangeGet: Boolean = false, profileIndex: Int = 0, origin: String = url): Request = Request.Builder().url(url).apply {
        headers(url, profileIndex, origin).forEach { (name, value) -> header(name, value) }
        if (rangeGet) get().header("Range", "bytes=0-0") else head()
    }.build()

    private fun inspectDirect(url: String, allowHttp: Boolean, check: () -> Unit): Remote {
        val initial = HttpPolicy.url(MediaLinks.normalize(url,allowHttp),allowHttp)
        val initialOrigin = initial.toString()
        var current = initial
        var rangeGet = false
        var profileIndex = 0
        repeat(14) { hop ->
            check()
            http.executeCancellable(request(current.toString(), rangeGet, profileIndex, initialOrigin),check).use { response ->
                if (response.code in REDIRECT_CODES) {
                    if (hop >= 10) throw OperationFailure("TOO_MANY_REDIRECTS")
                    val location = response.header("Location") ?: throw OperationFailure("INVALID_REDIRECT")
                    val next = HttpPolicy.url(MediaLinks.normalize(current.resolve(location).toString(),allowHttp),allowHttp)
                    if (current.scheme == "https" && next.scheme != "https") throw OperationFailure("INSECURE_REDIRECT")
                    current = next
                    profileIndex = 0
                    return@repeat
                }
                if (BrowserProfilePolicy.tryNext(response.code, profileIndex, BrowserProfilePolicy.forUrl(current.toString()).size)) {
                    profileIndex++
                    return@repeat
                }
                if (!rangeGet && response.code in RANGE_FALLBACK_CODES) {
                    rangeGet = true
                    return@repeat
                }
                if (response.code !in 200..299) throw OperationFailure("HTTP_${response.code}")
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                    ?.lowercase(Locale.ROOT).orEmpty()
                if (mime in HTML_MIMES)
                    throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                val pathExt = current.path.orEmpty().substringAfterLast('/')
                    .substringAfterLast('.', "").lowercase(Locale.ROOT)
                val extension = pathExt.takeIf { it in AUDIO_EXT || it in VIDEO_EXT } ?: MIME_TO_EXT[mime]
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
        val initial = HttpPolicy.url(MediaLinks.normalize(url,allowHttp),allowHttp)
        val initialOrigin = initial.toString()
        var current = initial
        var profileIndex = preferredProfileIndex
        repeat(13) { hop ->
            check()
            val req = Request.Builder().url(current.toString()).get().apply {
                val hopHeaders = if (current == initial) headers(current.toString(), profileIndex)
                    else headers(current.toString(), profileIndex, initialOrigin)
                hopHeaders.forEach { (k, v) -> header(k, v) }
                header("Accept", "text/html,application/xhtml+xml")
            }.build()
            http.executeCancellable(req,check).use { response ->
                if (response.code in REDIRECT_CODES) {
                    if (hop >= 10) throw OperationFailure("TOO_MANY_REDIRECTS")
                    val location = response.header("Location") ?: throw OperationFailure("INVALID_REDIRECT")
                    val next = HttpPolicy.url(MediaLinks.normalize(current.resolve(location).toString(),allowHttp),allowHttp)
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
                if (mime !in HTML_MIMES) throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                val input = response.body?.byteStream() ?: throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                val buffer = ByteArray(8192)
                val data = ByteArrayOutputStream()
                while (true) {
                    check()
                    val count = input.read(buffer)
                    if (count < 0) break
                    val toWrite = minOf(count, 262144 - data.size())
                    if (toWrite > 0) data.write(buffer, 0, toWrite)
                    if (data.size() >= 262144) break
                }
                return PageRead(current.toString(), data.toString(Charsets.UTF_8.name()), profileIndex)
            }
        }
        throw OperationFailure("TOO_MANY_REDIRECTS")
    }

    private fun isTikTokHost(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    private fun isLinkedInHost(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "lnkd.in" || host == "linkedin.com" || host.endsWith(".linkedin.com")
    }

    /** Extract public LinkedIn video streams, PDF carousel documents, and post images from page HTML. */
    private fun extractLinkedInCandidates(pageUrl: String, html: String, allowHttp: Boolean): List<Remote> {
        val decoded = html.replace("&quot;", "\"").replace("&amp;", "&").replace("\\u002F", "/").replace("\\/", "/")
        val title = Html5MediaDiscovery.title(html)?.take(180)?.ifBlank { "LinkedIn media" } ?: "LinkedIn media"
        val found = linkedMapOf<String, Remote>()
        for (m in LINKEDIN_VIDEO_REGEX.findAll(decoded)) {
            val u = m.value.trimEnd(',', '}', ']')
            if (runCatching { MediaLinks.normalize(u, allowHttp) }.isSuccess) found.putIfAbsent(u, Remote(u, "mp4", title, null))
        }
        for (m in LINKEDIN_DOC_REGEX.findAll(decoded)) {
            val u = m.value.trimEnd(',', '}', ']')
            if (runCatching { MediaLinks.normalize(u, allowHttp) }.isSuccess) found.putIfAbsent(u, Remote(u, "pdf", title, null))
        }
        if (found.isEmpty()) {
            for (m in LINKEDIN_IMG_REGEX.findAll(decoded)) {
                val u = m.value.trimEnd(',', '}', ']')
                if (runCatching { MediaLinks.normalize(u, allowHttp) }.isSuccess) found.putIfAbsent(u, Remote(u, "jpg", title, null))
                if (found.size >= 8) break
            }
        }
        return found.values.toList()
    }

    /** Direct HTTPS fallback for TikTok public links when the embedded yt-dlp extractor is blocked. */
    private fun inspectTikTokFallback(url: String, check: () -> Unit): Inspection? {
        if (!isTikTokHost(url)) return null
        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
        val req = Request.Builder()
            .url("https://www.tikwm.com/api/?url=$encoded&hd=1")
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            .get()
            .build()
        val bodyText = try {
            http.executeCancellable(req, check).use { resp ->
                if (resp.code !in 200..299) return null
                val input = resp.body?.byteStream() ?: return null
                val buf = ByteArray(8192)
                val out = ByteArrayOutputStream()
                while (true) {
                    check()
                    val n = input.read(buf)
                    if (n < 0) break
                    val take = minOf(n, 262144 - out.size())
                    if (take > 0) out.write(buf, 0, take)
                    if (out.size() >= 262144) break
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (cancel: java.util.concurrent.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        val root = runCatching { org.json.JSONObject(bodyText) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 0) return null
        val data = root.optJSONObject("data") ?: return null
        val title = data.optString("title").trim().take(180).ifBlank { "TikTok video" }
        val thumb = data.optString("cover").takeIf { it.startsWith("https://") && it.length <= 4096 }
        val streams = mutableListOf<Remote>()
        fun addIfHttps(rawUrl: String, ext: String, sizeKey: String) {
            val clean = rawUrl.trim()
            if (clean.startsWith("https://") && runCatching { MediaLinks.normalize(clean, false) }.isSuccess) {
                val sz = data.optLong(sizeKey, 0L).takeIf { it > 0L }
                streams += Remote(clean, ext, title, sz)
            }
        }
        val hd = data.optString("hdplay")
        val sd = data.optString("play")
        if (hd.isNotBlank()) addIfHttps(hd, "mp4", "hd_size")
        else if (sd.isNotBlank()) addIfHttps(sd, "mp4", "size")
        val music = data.optString("music")
        if (music.isNotBlank()) addIfHttps(music, "mp3", "music_size")
        if (streams.isEmpty()) return null
        return Inspection(title, "Html5", streams, thumbnailUrl = thumb)
    }

    private fun inspectCandidates(url: String, allowHttp: Boolean, check: () -> Unit): Inspection {
        if (isTikTokHost(url)) {
            inspectTikTokFallback(url, check)?.let { return it }
        }
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
                    if (stream.extension == source.extension || stream.extension in AUDIO_EXT || stream.extension in VIDEO_EXT)
                        streams += stream
                } catch (cancel: java.util.concurrent.CancellationException) { throw cancel }
                catch (_: Exception) { /* One inaccessible candidate must not hide another verified resource. */ }
            }
            if (streams.isEmpty() && (isLinkedInHost(url) || isLinkedInHost(pageUrl))) {
                streams += extractLinkedInCandidates(pageUrl, html, allowHttp)
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
            val isAudio = source.extension in AUDIO_EXT
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
        val journal = File(directory, "native-media.properties")
        val old = java.util.Properties().apply { if (journal.isFile) journal.inputStream().use(::load) }
        val urlDigest = Hashing.sha256(ByteArrayInputStream(source.url.toByteArray(Charsets.UTF_8)))
        // Direct transfers resume safely when validators match; reset only if URL or validator is absent.
        if (partial.exists() && (old.getProperty("urlDigest") != urlDigest ||
            (old.getProperty("etag").isNullOrBlank() && old.getProperty("modified").isNullOrBlank()))) {
            if (!partial.delete()) throw OperationFailure("MEDIA_WORKSPACE_LIMIT")
            journal.delete()
            old.clear()
        }
        val target = File(directory, "media.${source.extension}")
        val result = StreamTransfer(OkHttpTransport(http, { targetUrl, profileIndex ->
            headers(targetUrl, profileIndex, source.url)
        }, check)).execute(
            TransferRequest(
                url = source.url, partialFile = partial, offset = partial.length(),
                strongEtag = old.getProperty("etag"), lastModified = old.getProperty("modified"),
                knownTotalBytes = old.getProperty("total")?.toLongOrNull(),
                cleartextConsent = allowHttp, maxBytes = 8L * 1024 * 1024 * 1024,
                minFreeBytes = 64L * 1024 * 1024, finalUrlSha256 = old.getProperty("finalUrlSha256")
            ),
            checkCancel = check,
            progress = { done, total -> progress(MediaProgress(id, done, total, null, null, null)) },
            onMetadata = { metadata ->
                val p = java.util.Properties().apply {
                    setProperty("urlDigest", urlDigest)
                    metadata.finalUrlSha256?.let { setProperty("finalUrlSha256", it) }
                    metadata.strongEtag?.let { setProperty("etag", it) }
                    metadata.lastModified?.let { setProperty("modified", it) }
                    metadata.totalBytes?.let { setProperty("total", it.toString()) }
                }
                val pending = File(directory, "native-media.pending")
                java.io.FileOutputStream(pending).use { p.store(it, null); it.fd.sync() }
                moveSafely(pending, journal)
            }
        )
        check()
        if (result.mimeType in HTML_MIMES) {
            partial.delete(); journal.delete()
            throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
        }
        moveSafely(partial, target)
        journal.delete()
        try { MediaOutputPolicy.inspect(target) }
        catch (error: Exception) { target.delete(); throw error }
        check()
    }

    private fun moveSafely(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
