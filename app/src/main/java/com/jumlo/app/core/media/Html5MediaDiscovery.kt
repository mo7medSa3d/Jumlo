package com.jumlo.app.core.media

import com.jumlo.app.core.HttpPolicy
import java.net.URI
import java.util.Locale

/**
 * Bounded, passive HTML5 source discovery. It does not execute page JavaScript,
 * impersonate provider APIs, decrypt DRM, or assume that a link is downloadable.
 * Each returned URL still needs a real response probe before it can be offered.
 */
object Html5MediaDiscovery {
    data class Source(val url: String, val extension: String, val mimeType: String?)

    private val tag = Regex("""<\s*(video|audio|source|meta|a)\b[^>]{0,4096}>""", RegexOption.IGNORE_CASE)
    private val attr = Regex("""([a-zA-Z_:][a-zA-Z0-9_:.-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private val ext = setOf("mp4", "m4v", "webm", "mov", "mkv", "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "ts")
    private val mimeExt = mapOf(
        "video/mp4" to "mp4", "video/webm" to "webm", "video/quicktime" to "mov",
        "video/mp2t" to "ts", "audio/mp4" to "m4a", "audio/mpeg" to "mp3",
        "audio/ogg" to "ogg", "audio/flac" to "flac", "audio/wav" to "wav",
        "audio/aac" to "aac", "audio/opus" to "opus"
    )
    private val jsonLdScript = Regex("""<script\b([^>]{0,4096})>([\s\S]{0,32768}?)</script\s*>""", RegexOption.IGNORE_CASE)
    private val jsonContent = Regex(""""contentUrl"\s*:\s*"((?:\\.|[^"\\]){1,4096})"""", RegexOption.IGNORE_CASE)
    private val metaTypes = setOf("og:video", "og:video:url", "og:video:secure_url", "og:audio",
        "og:audio:url", "twitter:player:stream")

    fun discover(pageUrl: String, html: String, allowHttp: Boolean = false): List<Source> {
        require(html.length <= 262144) { "MEDIA_PAGE_TOO_LARGE" }
        val base = HttpPolicy.url(pageUrl, allowHttp)
        val result = LinkedHashMap<String, Source>()
        fun add(rawInput: String, type: String? = null, declaredMedia: Boolean = false) {
            val raw = unescape(rawInput).trim()
            if (raw.length !in 1..4096) return
            val target = runCatching { base.resolve(URI(raw)).toString() }.getOrNull() ?: return
            val uri = runCatching { HttpPolicy.url(target, allowHttp) }.getOrNull() ?: return
            if (base.scheme == "https" && uri.scheme != "https") return
            if (!safeEmbeddedHost(uri.host.orEmpty())) return
            val mime = type?.substringBefore(';')?.lowercase(Locale.ROOT)
            val pathExt = uri.path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
            // Signature-only URLs are *candidates*, never classified as a playable file
            // until a later HTTPS HEAD/Range response confirms an actual media MIME.
            val extension = pathExt.takeIf { it in ext } ?: mimeExt[mime] ?: if (declaredMedia) "" else return
            if (result.size < 16) result.putIfAbsent(uri.toString(), Source(uri.toString(), extension, mime))
        }
        for (match in tag.findAll(html).take(256)) {
            val kind = match.groupValues[1].lowercase(Locale.ROOT)
            val attributes = attributes(match.value)
            val candidate = when (kind) {
                "video", "audio", "source" -> attributes["src"]
                "a" -> attributes["href"]?.takeIf { Regex("""\bdownload(?:\s|=|>)""", RegexOption.IGNORE_CASE).containsMatchIn(match.value) }
                "meta" -> attributes["content"]?.takeIf {
                    attributes["property"]?.lowercase(Locale.ROOT) in metaTypes || attributes["name"]?.lowercase(Locale.ROOT) in metaTypes
                }
                else -> null
            } ?: continue
            val declared = kind == "meta" && (attributes["property"]?.lowercase(Locale.ROOT) in metaTypes ||
                attributes["name"]?.lowercase(Locale.ROOT) in metaTypes)
            add(candidate, attributes["type"], declaredMedia = declared)
            if (result.size == 16) break
        }
        // JSON-LD VideoObject.contentUrl is a publicly declared asset address;
        // embedUrl and scripts are NOT treated as media downloads.
        if (result.size < 16) for (script in jsonLdScript.findAll(html).take(16)) {
            if (attributes("<script " + script.groupValues[1] + ">")["type"]?.lowercase(Locale.ROOT) != "application/ld+json") continue
            for (match in jsonContent.findAll(script.groupValues[2]).take(16)) {
                add(unescapeJsonString(match.groupValues[1]), declaredMedia = true)
            }
        }
        return result.values.toList()
    }

    /** Thumbnail is optional and must be HTTPS; it is never an extracted media stream. */
    fun thumbnail(pageUrl: String, html: String): String? {
        require(html.length <= 262144) { "MEDIA_PAGE_TOO_LARGE" }
        val base = HttpPolicy.url(pageUrl)
        for (match in tag.findAll(html).take(256)) {
            if (!match.groupValues[1].equals("meta", true)) continue
            val a = attributes(match.value)
            if (a["property"]?.lowercase(Locale.ROOT) !in setOf("og:image", "og:image:secure_url") &&
                a["name"]?.lowercase(Locale.ROOT) != "twitter:image") continue
            val resolved = runCatching { base.resolve(URI(unescape(a["content"].orEmpty()))).toString() }.getOrNull() ?: continue
            val uri = runCatching { HttpPolicy.url(resolved) }.getOrNull() ?: continue
            if (safeEmbeddedHost(uri.host.orEmpty())) return uri.toString()
        }
        return null
    }

    private fun safeEmbeddedHost(host: String): Boolean {
        val value = host.lowercase(Locale.ROOT)
        if (value.isBlank() || value == "localhost" || value.endsWith(".localhost") ||
            value.endsWith(".local") || value.endsWith(".internal") || ':' in value) return false
        // Never let embedded metadata probe IP literals or a local admin service.
        if (value.matches(Regex("[0-9.]+"))) return false
        return '.' in value && value.length <= 253
    }

    private fun unescapeJsonString(value: String): String {
        val unicode = Regex("""\\u([0-9a-fA-F]{4})""")
        val decoded = unicode.replace(value) { match ->
            val point = match.groupValues[1].toInt(16)
            if (point in 32..0xd7ff || point in 0xe000..0xffff) point.toChar().toString() else match.value
        }
        return decoded.replace("\\/", "/").replace("\\\"", "\"")
    }

    fun title(html: String): String? {
        require(html.length <= 262144) { "MEDIA_PAGE_TOO_LARGE" }
        for (match in tag.findAll(html).take(256)) {
            if (!match.groupValues[1].equals("meta", true)) continue
            val a = attributes(match.value)
            if (a["property"]?.equals("og:title",true) == true || a["name"]?.equals("twitter:title",true) == true) {
                val text = unescape(a["content"].orEmpty()).trim().take(180)
                if (text.isNotBlank()) return text
            }
        }
        val t = Regex("""<title\b[^>]*>([^<]{1,1024})</title\s*>""",RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        return t?.let(::unescape)?.trim()?.take(180)?.takeIf(String::isNotBlank)
    }

    private fun attributes(tag: String): Map<String,String> {
        val start = tag.indexOf(' ')
        if (start == -1) return emptyMap()
        return attr.findAll(tag.substring(start)).take(32).associate { match ->
            match.groupValues[1].lowercase(Locale.ROOT) to (match.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: "")
        }
    }

    private fun unescape(text: String): String = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|quot|apos|lt|gt);""").replace(text) { match ->
        val key = match.groupValues[1]
        when (key.lowercase(Locale.ROOT)) {
            "amp" -> "&"; "quot" -> "\""; "apos" -> "'"; "lt" -> "<"; "gt" -> ">"
            else -> runCatching {
                val point = if (key.startsWith("#x", true)) key.drop(2).toInt(16) else key.drop(1).toInt()
                if (point !in 32..0x10ffff || point in 0xd800..0xdfff) match.value else String(Character.toChars(point))
            }.getOrDefault(match.value)
        }
    }
}
