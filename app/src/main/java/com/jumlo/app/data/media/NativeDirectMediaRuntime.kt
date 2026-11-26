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
import org.json.JSONArray
import org.json.JSONObject
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
        const val LINKEDIN_REFERER = "https://www.linkedin.com/"
        const val LINKEDIN_DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val LINKEDIN_FALLBACK_UAS = listOf(
            LINKEDIN_DESKTOP_UA,
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
        )
        val LINKEDIN_TRACKING_PARAMS = setOf(
            "rcm", "trk", "trkinfo", "lipi", "trackingid", "originalsubdomain",
            "refid", "midtoken", "midsig", "original_referer", "lici", "utm_source",
            "utm_medium", "utm_campaign", "utm_content", "utm_term"
        )
        val LINKEDIN_URN_REGEX = Regex("urn:li:(?:activity|ugcPost|share):\\d+")
        val LINKEDIN_POST_ACTIVITY_REGEX = Regex("(?:activity|ugcPost|share)[-:]([0-9]{15,22})", RegexOption.IGNORE_CASE)
        val LINKEDIN_DATA_SOURCES_REGEX = Regex("""data-sources\s*=\s*["'](\[[\s\S]*?\])["']""", RegexOption.IGNORE_CASE)
        val LINKEDIN_JSONLD_SCRIPT_REGEX = Regex("""<script[^>]+type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        val LINKEDIN_PROGRESSIVE_STREAMS_REGEX = Regex(""""progressiveStreams"\s*:\s*(\[[\s\S]*?\])\s*[,}]""")
        val LINKEDIN_VIDEO_REGEX = Regex("https://(?:dms|media)\\.licdn\\.com/(?:playlist/|dms/)?(?:prv/)?vid/[^\\s\"'<>\\\\]+")
        val LINKEDIN_DOC_REGEX = Regex("https://media\\.licdn\\.com/dms/document/[^\\s\"'<>\\\\]+")
        val LINKEDIN_IMG_REGEX = Regex("https://media\\.licdn\\.com/dms/image/[^\\s\"'<>\\\\]+(?:feedshare|article-cover|document-images)[^\\s\"'<>\\\\]*")
        val LINKEDIN_POST_URL_REGEX = Regex("""https://(?:www\.|m\.)?linkedin\.com/(?:posts|feed/update|embed/feed/update|pulse|video)/[^\s"'<>]+""")
        val LINKEDIN_JSONLD_VIDEO_REGEX = Regex("\\\\?\"(?:contentUrl|src)\\\\?\"\\s*:\\s*\\\\?\"(https://[^\"\\\\]+(?:licdn\\.com|\\.mp4)[^\"\\\\]*)")
        val LINKEDIN_OG_VIDEO_REGEX = Regex("<meta[^>]+property=[\"']og:video(?::secure_url)?[\"'][^>]+content=[\"'](https://[^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val LINKEDIN_RES_HINT_REGEX = Regex("""mp4-(\d{3,4})p""", RegexOption.IGNORE_CASE)
        val LINKEDIN_HLS_REGEX = Regex("https://(?:dms|media)\\.licdn\\.com/[^\\s\"'<>\\\\]+(?:\\.m3u8|/hls-[^\\s\"'<>\\\\]*)", RegexOption.IGNORE_CASE)
        val LINKEDIN_SESSION_REDIRECT_REGEX = Regex("""(?:sessionRedirect|session_redirect)=([^&"'\s<>]+)""", RegexOption.IGNORE_CASE)
        val LINKEDIN_HTTP_AUTH_CODES = setOf("HTTP_401", "HTTP_403", "HTTP_999")
        val LINKEDIN_POST_CONTENT_MARKERS = listOf(
            "feed-shared-update", "main-feed-activity-card", "attributed-text-segment-list",
            "share-update-card", "embed-feed-update", "\"SocialMediaPosting\"", "\"VideoObject\""
        )
        val LINKEDIN_AUTHWALL_HTML_MARKERS = listOf(
            "authwall", "session_redirect", "sessionredirect", "sign in to view",
            "sign in to linkedin", "join linkedin to view", "security verification", "cold-join"
        )
        val LINKEDIN_EMBED_UNAVAILABLE_MARKERS = listOf(
            "this post cannot be displayed", "post is no longer available", "unavailable-post"
        )
        val UNIVERSAL_MEDIA_URL_REGEX = Regex("""https://[^\s"'<>\\]+\.(?:mp4|m4v|webm|m4a|mp3|pdf)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
        val UNIVERSAL_META_IMAGE_REGEX = Regex("""(?:og:image|twitter:image)["'][^>]*content=["'](https://[^"']+)["']|content=["'](https://[^"']+)["'][^>]*property=["'](?:og:image|twitter:image)["']""", RegexOption.IGNORE_CASE)
        val UNIVERSAL_MEDIA_EXTS = setOf("mp4", "m4v", "webm", "m4a", "mp3", "pdf")
        val PLATFORM_JSON_VIDEO_REGEX = Regex(""""(?:playable_url_quality_hd|browser_native_hd_url|playable_url|browser_native_sd_url|video_url|fallback_url|contentUrl|hd_src|sd_src)"\s*:\s*"(https://[^"\s<>]+)"""")
    }
    private data class Remote(val url: String, val extension: String, val title: String, val size: Long?)
    private data class RankedVideo(val url: String, val bitrate: Long, val resolution: Int, val isProgressive: Boolean)
    private data class Inspection(val name: String, val extractor: String, val streams: List<Remote>, val thumbnailUrl: String? = null)
    private data class PageRead(val url: String, val html: String, val profileIndex: Int)
    private data class LinkedInScanResult(val streams: List<Remote>, val hasHlsOnly: Boolean)

    private fun headers(url: String, profileIndex: Int = 0, origin: String = url): Map<String, String> {
        val custom = sessions.getExtraHeaders().lineSequence().map(String::trim)
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        val resolved = PlatformHeaderPolicy.headersFor(
            url = url, customUserAgent = sessions.getCustomUserAgent(),
            preferDesktop = sessions.isPreferDesktopUa(),
            cookieHeader = sessions.cookieHeaderFor(url), customHeaders = custom,
            compatibilityAttempt = profileIndex,
            customHeaderOrigin = origin
        ).toMutableMap()
        if (isLinkedInMediaHost(url)) {
            resolved["Referer"] = LINKEDIN_REFERER
            if (sessions.getCustomUserAgent().isBlank()) {
                resolved["User-Agent"] = LINKEDIN_DESKTOP_UA
            }
        }
        return resolved
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
        preferredProfileIndex: Int = 0, userAgentOverride: String? = null, maxBytes: Int = 262144): PageRead {
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
                if (userAgentOverride != null) header("User-Agent", userAgentOverride)
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
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
                if (userAgentOverride == null && BrowserProfilePolicy.tryNext(response.code, profileIndex, BrowserProfilePolicy.forUrl(current.toString()).size)) {
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
                    val toWrite = minOf(count, maxBytes - data.size())
                    if (toWrite > 0) data.write(buffer, 0, toWrite)
                    if (data.size() >= maxBytes) break
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

    private fun isLnkdInHost(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "lnkd.in"
    }

    private fun isLinkedInMediaHost(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "lnkd.in" || host == "linkedin.com" || host.endsWith(".linkedin.com") ||
            host == "licdn.com" || host.endsWith(".licdn.com")
    }

    private fun hasLinkedInPostContent(html: String): Boolean =
        LINKEDIN_POST_CONTENT_MARKERS.any { html.contains(it, ignoreCase = true) }

    private fun isLinkedInEmbedUnavailable(html: String): Boolean =
        LINKEDIN_EMBED_UNAVAILABLE_MARKERS.any { html.contains(it, ignoreCase = true) }

    private fun isLinkedInAuthwall(url: String, html: String): Boolean {
        val lowerUrl = url.lowercase(Locale.ROOT)
        if (lowerUrl.contains("/authwall") || lowerUrl.contains("/uas/login") ||
            lowerUrl.contains("/login") || lowerUrl.contains("/checkpoint") ||
            lowerUrl.contains("session_redirect=") || lowerUrl.contains("sessionredirect=")) {
            return true
        }
        // Authwall pages still load static assets from static.licdn.com; check for real post markup instead.
        if (hasLinkedInPostContent(html)) return false
        return LINKEDIN_AUTHWALL_HTML_MARKERS.any { html.contains(it, ignoreCase = true) }
    }

    /** Strip tracking query parameters and normalize mobile/bare linkedin.com hosts to www.linkedin.com. */
    private fun normalizeLinkedInUrl(rawUrl: String, allowHttp: Boolean): String {
        val base = runCatching { MediaLinks.normalize(rawUrl, allowHttp) }.getOrDefault(rawUrl)
        val uri = runCatching { URI(base) }.getOrNull() ?: return base
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        if (host == "lnkd.in") return base
        val cleanHost = when {
            host == "linkedin.com" || host == "m.linkedin.com" || host == "mobile.linkedin.com" -> "www.linkedin.com"
            else -> host
        }
        val keptQuery = uri.rawQuery?.split('&')?.filter { part ->
            val key = part.substringBefore('=').lowercase(Locale.ROOT)
            key.isNotBlank() && key !in LINKEDIN_TRACKING_PARAMS && !key.startsWith("utm_")
        }?.joinToString("&")?.takeIf { it.isNotBlank() }
        val rebuilt = URI(
            uri.scheme ?: "https",
            null,
            cleanHost,
            uri.port,
            uri.rawPath?.ifBlank { "/" } ?: "/",
            keptQuery,
            null
        ).toString()
        return runCatching { MediaLinks.normalize(rebuilt, allowHttp) }.getOrDefault(base)
    }

    private fun decodeUrlSafe(raw: String): String {
        var current = raw
        // Decode up to twice for double-encoded sessionRedirect URNs (e.g. %253A -> %3A -> :).
        repeat(2) {
            val next = runCatching { java.net.URLDecoder.decode(current, "UTF-8") }.getOrDefault(current)
            if (next == current) return current
            current = next
        }
        return current
    }

    private fun extractLinkedInUrns(text: String): List<String> {
        val urns = linkedSetOf<String>()
        val normalized = decodeUrlSafe(decodeHtmlEntities(text))
        for (m in LINKEDIN_URN_REGEX.findAll(normalized)) {
            urns += m.value
            if (urns.size >= 6) return urns.toList()
        }
        for (m in LINKEDIN_POST_ACTIVITY_REGEX.findAll(normalized)) {
            val kind = when {
                m.value.startsWith("ugcPost", ignoreCase = true) -> "ugcPost"
                m.value.startsWith("share", ignoreCase = true) -> "share"
                else -> "activity"
            }
            urns += "urn:li:$kind:${m.groupValues[1]}"
            if (urns.size >= 6) return urns.toList()
        }
        return urns.toList()
    }

    private fun resolveLinkedInInterstitial(pageUrl: String, html: String, allowHttp: Boolean): String? {
        if (!pageUrl.contains("lnkd.in", ignoreCase = true) && !isLinkedInAuthwall(pageUrl, html)) return null
        val decodedUrl = decodeUrlSafe(pageUrl)
        LINKEDIN_SESSION_REDIRECT_REGEX.find(pageUrl)?.groupValues?.getOrNull(1)?.let { encoded ->
            val candidate = decodeUrlSafe(encoded).trimEnd('"', '\'', ')', ']')
            if (LINKEDIN_POST_URL_REGEX.containsMatchIn(candidate)) {
                return normalizeLinkedInUrl(candidate, allowHttp)
            }
        }
        if (decodedUrl != pageUrl) {
            LINKEDIN_POST_URL_REGEX.find(decodedUrl)?.value?.trimEnd('"', '\'', ')', ']')?.let {
                return normalizeLinkedInUrl(it, allowHttp)
            }
        }
        val decoded = decodeHtmlEntities(html)
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("\\u0026", "&")
            .replace("\\u003D", "=")
            .replace("\\u003d", "=")
        val target = LINKEDIN_POST_URL_REGEX.find(decoded)?.value?.trimEnd('"', '\'', ')', ']')
            ?: LINKEDIN_SESSION_REDIRECT_REGEX.find(decoded)?.groupValues?.getOrNull(1)
                ?.let { decodeUrlSafe(it).trimEnd('"', '\'', ')', ']') }
                ?.takeIf { LINKEDIN_POST_URL_REGEX.containsMatchIn(it) }
            ?: return null
        return normalizeLinkedInUrl(target, allowHttp)
    }

    private fun decodeHtmlEntities(raw: String): String = raw
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#x22;", "\"")
        .replace("&amp;", "&")
        .replace("&#38;", "&")
        .replace("\\u002F", "/")
        .replace("\\u0026", "&")
        .replace("\\/", "/")

    private fun inferResolutionHint(url: String): Int {
        val m = LINKEDIN_RES_HINT_REGEX.find(url)
        if (m != null) return m.groupValues[1].toIntOrNull() ?: 0
        return when {
            url.contains("1080") -> 1080
            url.contains("720") -> 720
            url.contains("480") -> 480
            url.contains("360") -> 360
            else -> 0
        }
    }

    private fun isHlsUrl(url: String, mimeType: String = ""): Boolean {
        val lowerUrl = url.substringBefore('?').lowercase(Locale.ROOT)
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        return lowerUrl.endsWith(".m3u8") || lowerUrl.contains("/hls-") ||
            lowerMime.contains("mpegurl") || lowerMime.contains("application/vnd.apple")
    }

    /** Extract public LinkedIn video streams (sorted by highest bitrate/resolution), PDF carousel documents, and photo posts. */
    private fun extractLinkedInCandidates(pageUrl: String, html: String, allowHttp: Boolean): LinkedInScanResult {
        val decoded = decodeHtmlEntities(html)
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("\\u0026", "&")
            .replace("\\u003D", "=")
            .replace("\\u003d", "=")
        val title = Html5MediaDiscovery.title(html)?.take(180)?.ifBlank { "LinkedIn media" } ?: "LinkedIn media"
        val videosByUrl = linkedMapOf<String, RankedVideo>()
        var sawHls = false

        fun addVideo(rawUrl: String, bitrate: Long = 0L, resolution: Int = 0, mimeType: String = "") {
            val clean = decodeHtmlEntities(rawUrl).trim().trimEnd(',', '}', ']', '"', '\'')
            if (!clean.startsWith("https://")) return
            if (isHlsUrl(clean, mimeType)) {
                sawHls = true
                return
            }
            if (runCatching { MediaLinks.normalize(clean, allowHttp) }.isFailure) return
            val effectiveRes = if (resolution > 0) resolution else inferResolutionHint(clean)
            val isProg = mimeType.contains("mp4", ignoreCase = true) || clean.contains("mp4", ignoreCase = true)
            val existing = videosByUrl[clean]
            val candidate = RankedVideo(clean, bitrate, effectiveRes, isProg)
            if (existing == null || candidate.bitrate > existing.bitrate || candidate.resolution > existing.resolution) {
                videosByUrl[clean] = candidate
            }
        }

        // 1. <video data-sources="[{&quot;src&quot;:&quot;...&quot;,&quot;type&quot;:&quot;video/mp4&quot;,&quot;data-bitrate&quot;:...}]">
        for (m in LINKEDIN_DATA_SOURCES_REGEX.findAll(html)) {
            val rawJson = decodeHtmlEntities(m.groupValues[1])
            val arr = runCatching { JSONArray(rawJson) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val src = obj.optString("src").ifBlank { obj.optString("url") }
                if (src.isBlank()) continue
                val type = obj.optString("type")
                val bitrate = obj.optLong("data-bitrate", obj.optLong("bitrate", obj.optLong("bitRate", 0L)))
                val height = obj.optInt("height", obj.optInt("data-height", 0))
                addVideo(src, bitrate = bitrate, resolution = height, mimeType = type)
            }
        }

        // 2. progressiveStreams JSON arrays embedded in LinkedIn Voyager/SSR payloads
        for (m in LINKEDIN_PROGRESSIVE_STREAMS_REGEX.findAll(decoded)) {
            val arr = runCatching { JSONArray(m.groupValues[1]) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val bitrate = obj.optLong("bitRate", obj.optLong("bitrate", 0L))
                val height = obj.optInt("height", 0)
                val mediaType = obj.optString("mediaType", "video/mp4")
                val locations = obj.optJSONArray("streamingLocations")
                if (locations != null) {
                    for (j in 0 until locations.length()) {
                        val loc = locations.optJSONObject(j) ?: continue
                        val u = loc.optString("url")
                        if (u.isNotBlank()) addVideo(u, bitrate = bitrate, resolution = height, mimeType = mediaType)
                    }
                } else {
                    val u = obj.optString("url").ifBlank { obj.optString("src") }
                    if (u.isNotBlank()) addVideo(u, bitrate = bitrate, resolution = height, mimeType = mediaType)
                }
            }
        }

        // 3. JSON-LD <script type="application/ld+json"> VideoObject / SocialMediaPosting
        for (m in LINKEDIN_JSONLD_SCRIPT_REGEX.findAll(html)) {
            val jsonText = m.groupValues[1].trim()
            val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: continue
            fun scanJsonLd(obj: JSONObject) {
                val contentUrl = obj.optString("contentUrl")
                if (contentUrl.isNotBlank()) addVideo(contentUrl, mimeType = obj.optString("encodingFormat"))
                val videoObj = obj.optJSONObject("video")
                if (videoObj != null) scanJsonLd(videoObj)
                val shared = obj.optJSONObject("sharedContent")
                if (shared != null) scanJsonLd(shared)
            }
            scanJsonLd(root)
        }

        // 4. Direct dms.licdn.com / media.licdn.com video URLs, JSON-LD regex fallback, and og:video
        for (m in LINKEDIN_VIDEO_REGEX.findAll(decoded)) {
            addVideo(m.value)
        }
        for (m in LINKEDIN_JSONLD_VIDEO_REGEX.findAll(decoded)) {
            addVideo(m.groupValues[1])
        }
        for (m in LINKEDIN_OG_VIDEO_REGEX.findAll(decoded)) {
            addVideo(m.groupValues[1])
        }
        if (videosByUrl.isEmpty() && !sawHls) {
            if (LINKEDIN_HLS_REGEX.containsMatchIn(decoded) ||
                decoded.contains("application/vnd.apple.mpegurl", ignoreCase = true)) {
                sawHls = true
            }
        }

        val found = linkedMapOf<String, Remote>()
        videosByUrl.values
            .sortedWith(
                compareByDescending<RankedVideo> { it.isProgressive }
                    .thenByDescending { it.bitrate }
                    .thenByDescending { it.resolution }
            )
            .forEach { v -> found.putIfAbsent(v.url, Remote(v.url, "mp4", title, null)) }

        for (m in LINKEDIN_DOC_REGEX.findAll(decoded)) {
            val u = m.value.trimEnd(',', '}', ']')
            if (runCatching { MediaLinks.normalize(u, allowHttp) }.isSuccess) found.putIfAbsent(u, Remote(u, "pdf", title, null))
        }
        // Only fall back to photo extraction when no HLS-only video was detected (avoids saving a video poster as a photo).
        if (found.isEmpty() && !sawHls) {
            // Deduplicate distinct photos by asset segment so we keep one high-res URL per photo in multi-photo posts.
            val byAsset = linkedMapOf<String, String>()
            for (m in LINKEDIN_IMG_REGEX.findAll(decoded)) {
                val u = m.value.trimEnd(',', '}', ']')
                if (runCatching { MediaLinks.normalize(u, allowHttp) }.isFailure) continue
                val assetKey = u.substringBefore("/feedshare").substringAfterLast('/')
                val prev = byAsset[assetKey]
                if (prev == null || u.contains("shrink_2048") || (u.contains("shrink_1280") && !prev.contains("shrink_2048"))) {
                    byAsset[assetKey] = u
                }
                if (byAsset.size >= 20) break
            }
            val images = byAsset.values.toList()
            if (images.size >= 2) {
                found["bundle"] = Remote("bundle:" + images.joinToString("\n"), "zip", "$title (${images.size} photos)", null)
            } else if (images.size == 1) {
                found[images[0]] = Remote(images[0], "jpg", title, null)
            } else {
                val ogThumb = runCatching { Html5MediaDiscovery.thumbnail(pageUrl, html) }.getOrNull()
                if (ogThumb != null && ogThumb.contains("licdn.com", ignoreCase = true) && !ogThumb.contains("profile-displayphoto", ignoreCase = true)) {
                    found[ogThumb] = Remote(ogThumb, "jpg", title, null)
                }
            }
        }
        return LinkedInScanResult(found.values.toList(), hasHlsOnly = found.isEmpty() && sawHls)
    }

    /** Multi-stage resilient LinkedIn extractor: URL normalization -> embed endpoint -> UA fallbacks. */
    private fun inspectLinkedIn(rawUrl: String, allowHttp: Boolean, check: () -> Unit): Inspection? {
        var canonicalUrl = normalizeLinkedInUrl(rawUrl, allowHttp)
        val triedEmbedUrns = mutableSetOf<String>()
        var lastTitle: String? = null
        var lastThumb: String? = null
        var sawLinkedInHost = !isLnkdInHost(canonicalUrl)
        var sawAuthwall = false
        var sawHlsOnly = false
        var sawPublicPage = false
        var lastHttpError: OperationFailure? = null

        fun readLinkedInPage(targetUrl: String, ua: String): PageRead? = try {
            readPage(targetUrl, allowHttp, check, userAgentOverride = ua, maxBytes = 1_048_576)
        } catch (cancel: java.util.concurrent.CancellationException) {
            throw cancel
        } catch (failure: OperationFailure) {
            val code = failure.message.orEmpty()
            if (code in LINKEDIN_HTTP_AUTH_CODES) {
                sawAuthwall = true
            } else if (code != "MEDIA_SITE_UNSUPPORTED") {
                lastHttpError = failure
            }
            null
        } catch (_: Exception) {
            null
        }

        fun analyzePage(page: PageRead): Inspection? {
            if (isLinkedInHost(page.url) && !isLnkdInHost(page.url)) sawLinkedInHost = true
            val scan = extractLinkedInCandidates(page.url, page.html, allowHttp)
            if (scan.streams.isNotEmpty()) {
                val title = Html5MediaDiscovery.title(page.html)?.takeIf { it.isNotBlank() } ?: lastTitle ?: scan.streams.first().title
                val thumb = runCatching { Html5MediaDiscovery.thumbnail(page.url, page.html) }.getOrNull() ?: lastThumb
                return Inspection(title, "Html5", scan.streams, thumbnailUrl = thumb)
            }
            if (scan.hasHlsOnly) {
                sawHlsOnly = true
            } else if (isLinkedInAuthwall(page.url, page.html) || isLinkedInEmbedUnavailable(page.html)) {
                sawAuthwall = true
            } else if (isLinkedInHost(page.url) && !isLnkdInHost(page.url)) {
                sawPublicPage = true
            }
            return null
        }

        fun tryEmbed(urn: String): Inspection? {
            if (!triedEmbedUrns.add(urn)) return null
            val embedUrl = "https://www.linkedin.com/embed/feed/update/$urn"
            for (ua in LINKEDIN_FALLBACK_UAS) {
                check()
                val page = readLinkedInPage(embedUrl, ua) ?: continue
                analyzePage(page)?.let { return it }
            }
            return null
        }

        fun tryEmbedsFrom(text: String): Inspection? {
            for (urn in extractLinkedInUrns(text)) {
                tryEmbed(urn)?.let { return it }
            }
            return null
        }

        // Stage 1: If a URN is already in the URL (including /posts/...-activity-<digits>), try the public embed endpoint first.
        tryEmbedsFrom(canonicalUrl)?.let { return it }

        // Stage 2: Fetch canonical/short URL with Desktop, Mobile, Googlebot, and FacebookExternalHit UAs.
        for (ua in LINKEDIN_FALLBACK_UAS) {
            check()
            val page = readLinkedInPage(canonicalUrl, ua) ?: continue
            // If a lnkd.in short link redirected off LinkedIn, let the generic extractor handle it.
            if (!isLinkedInHost(page.url)) return null

            val resolvedInterstitial = resolveLinkedInInterstitial(page.url, page.html, allowHttp)
            if (resolvedInterstitial != null && resolvedInterstitial != canonicalUrl) {
                canonicalUrl = resolvedInterstitial
                sawLinkedInHost = true
                tryEmbedsFrom(canonicalUrl)?.let { return it }
                val targetPage = readLinkedInPage(canonicalUrl, ua)
                if (targetPage != null) {
                    analyzePage(targetPage)?.let { return it }
                    tryEmbedsFrom(targetPage.url + "\n" + targetPage.html)?.let { return it }
                }
            }

            if (lastTitle.isNullOrBlank()) lastTitle = Html5MediaDiscovery.title(page.html)
            if (lastThumb.isNullOrBlank()) lastThumb = runCatching { Html5MediaDiscovery.thumbnail(page.url, page.html) }.getOrNull()

            // Stage 3: Check for URN inside the fetched URL or HTML and query the embed endpoint if unseen.
            tryEmbedsFrom(page.url)?.let { return it }
            analyzePage(page)?.let { return it }
            tryEmbedsFrom(page.html)?.let { return it }
        }

        // If lnkd.in never resolved to a LinkedIn HTML page (e.g. redirected to a direct media file), allow generic fallback.
        if (!sawLinkedInHost) return null
        if (sawHlsOnly) throw OperationFailure("LINKEDIN_UNSUPPORTED_STREAM")
        if (sawPublicPage) throw OperationFailure("LINKEDIN_NO_VIDEO")
        if (sawAuthwall) throw OperationFailure("LINKEDIN_AUTH_REQUIRED")
        lastHttpError?.let { throw it }
        throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
    }

    /** Universal fallback extractor for any public webpage: scans decoded HTML/JSON for direct
     *  video/audio/PDF URLs and falls back to high-res OpenGraph/Twitter images when no stream exists. */
    private fun extractUniversalPageMedia(pageUrl: String, html: String, allowHttp: Boolean): List<Remote> {
        val decoded = html.replace("&quot;", "\"").replace("&amp;", "&").replace("\\u002F", "/").replace("\\/", "/")
        val title = Html5MediaDiscovery.title(html)?.take(180)?.ifBlank { "Web media" } ?: "Web media"
        val found = linkedMapOf<String, Remote>()
        for (m in PLATFORM_JSON_VIDEO_REGEX.findAll(decoded)) {
            val u = m.groupValues[1].trim()
            if (runCatching { MediaLinks.normalize(u, allowHttp) }.isFailure) continue
            val ext = u.substringBefore('?').substringAfterLast('.', "mp4").lowercase(Locale.ROOT)
                .takeIf { it in UNIVERSAL_MEDIA_EXTS } ?: "mp4"
            found.putIfAbsent(u, Remote(u, ext, title, null))
            if (found.size >= 6) break
        }
        for (m in UNIVERSAL_MEDIA_URL_REGEX.findAll(decoded)) {
            val u = m.value.trimEnd(',', '}', ']', ')')
            if (runCatching { MediaLinks.normalize(u, allowHttp) }.isFailure) continue
            val ext = u.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext in UNIVERSAL_MEDIA_EXTS) {
                found.putIfAbsent(u, Remote(u, ext, title, null))
            }
            if (found.size >= 6) break
        }
        if (found.isEmpty()) {
            for (m in UNIVERSAL_META_IMAGE_REGEX.findAll(decoded)) {
                val u = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
                if (u.isNotBlank() && runCatching { MediaLinks.normalize(u, allowHttp) }.isSuccess) {
                    val lower = u.substringBefore('?').lowercase(Locale.ROOT)
                    val ext = when {
                        lower.endsWith(".png") -> "png"
                        lower.endsWith(".webp") -> "webp"
                        else -> "jpg"
                    }
                    found.putIfAbsent(u, Remote(u, ext, title, null))
                    break
                }
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
            http.executeCancellable(req, check, 20_000L).use { resp ->
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
        val root = runCatching { JSONObject(bodyText) }.getOrNull() ?: return null
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
        if (isLinkedInHost(url)) {
            inspectLinkedIn(url, allowHttp, check)?.let { return it }
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
            if (streams.isEmpty() && isLinkedInHost(pageUrl)) {
                inspectLinkedIn(pageUrl, allowHttp, check)?.let { return it }
            }
            if (streams.isEmpty()) {
                streams += extractUniversalPageMedia(pageUrl, html, allowHttp)
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
                protocol = if (source.url.startsWith("bundle:")) "https" else URI(source.url).scheme.lowercase(Locale.ROOT))
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
        if (source.url.startsWith("bundle:")) {
            val urls = source.url.removePrefix("bundle:").lines().filter { it.isNotBlank() }
            val target = File(directory, "media.zip")
            val partialZip = File(directory, "native-bundle.part")
            var bundleCompleted = false
            try {
                var writtenCount = 0
                java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(partialZip))).use { zos ->
                    urls.forEachIndexed { idx, imgUrl ->
                        check()
                        var currentImg = HttpPolicy.url(MediaLinks.normalize(imgUrl, allowHttp), allowHttp)
                        for (hop in 0..5) {
                            check()
                            val imgReq = Request.Builder().url(currentImg.toString()).apply {
                                headers(currentImg.toString(), 0, normalized).forEach { (k, v) -> header(k, v) }
                            }.get().build()
                            var redirected = false
                            http.executeCancellable(imgReq, check).use { resp ->
                                if (resp.code in REDIRECT_CODES) {
                                    val loc = resp.header("Location")
                                    if (loc != null && hop < 5) {
                                        val next = HttpPolicy.url(MediaLinks.normalize(currentImg.resolve(loc).toString(), allowHttp), allowHttp)
                                        if (currentImg.scheme == "https" && next.scheme != "https") throw OperationFailure("INSECURE_REDIRECT")
                                        currentImg = next
                                        redirected = true
                                    }
                                } else if (resp.code in 200..299) {
                                    val bodyStream = resp.body?.byteStream()
                                    val bytes = if (bodyStream == null) ByteArray(0) else {
                                        val out = ByteArrayOutputStream()
                                        val buf = ByteArray(16 * 1024)
                                        while (true) {
                                            check()
                                            val n = bodyStream.read(buf)
                                            if (n < 0) break
                                            if (out.size() + n > 25 * 1024 * 1024) throw OperationFailure("MEDIA_OUTPUT_LIMIT")
                                            if (n > 0) out.write(buf, 0, n)
                                        }
                                        out.toByteArray()
                                    }
                                    if (bytes.isNotEmpty()) {
                                        val ctype = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                                        val ext = when {
                                            ctype.contains("png") -> "png"
                                            ctype.contains("webp") -> "webp"
                                            else -> "jpg"
                                        }
                                        zos.putNextEntry(java.util.zip.ZipEntry("photo_${(idx + 1).toString().padStart(2, '0')}.$ext"))
                                        zos.write(bytes)
                                        zos.closeEntry()
                                        writtenCount++
                                    }
                                }
                            }
                            if (!redirected) break
                        }
                        progress(MediaProgress(id, (idx + 1).toLong(), urls.size.toLong(), null, null, null))
                    }
                }
                if (writtenCount == 0) {
                    throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                }
                moveSafely(partialZip, target)
                try { MediaOutputPolicy.inspect(target) } catch (error: Exception) { target.delete(); throw error }
                check()
                bundleCompleted = true
            } finally {
                if (!bundleCompleted) partialZip.delete()
            }
            return
        }
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
        var completed = false
        try {
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
                throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
            }
            moveSafely(partial, target)
            journal.delete()
            try { MediaOutputPolicy.inspect(target) }
            catch (error: Exception) { target.delete(); throw error }
            check()
            completed = true
        } finally {
            if (!completed) {
                partial.delete()
                journal.delete()
            }
        }
    }

    private fun moveSafely(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
