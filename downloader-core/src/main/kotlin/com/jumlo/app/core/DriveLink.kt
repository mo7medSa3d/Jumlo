package com.jumlo.app.core

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Parse only canonical Google Drive/Docs links or opaque Drive IDs; never search inside arbitrary text. */
object DriveLink {
    private val ID = Regex("[A-Za-z0-9_-]{10,128}")
    private val DRIVE_FILE = Regex("/file/(?:u/[0-9]+/)?d/([A-Za-z0-9_-]{10,128})(?:/.*)?")
    private val DOC_FILE = Regex("/(document|spreadsheets|presentation)/(?:u/[0-9]+/)?d/([A-Za-z0-9_-]{10,128})(?:/.*)?")
    private const val MAX_CONFIRM_HTML_CHARS = 262_144
    private val CONFIRM_LINK = Regex("""<a\b[^>]{0,4096}\bhref="([^"]{0,4096}(?:confirm=|download\?id=)[^"]{0,4096})"[^>]{0,4096}>""", RegexOption.IGNORE_CASE)
    private val CONFIRM_FORM = Regex("""<form\b[^>]{0,4096}\baction="([^"]{1,4096})"[^>]{0,4096}>([\s\S]{0,32768}?)</form>""", RegexOption.IGNORE_CASE)
    private val CONFIRM_INPUT = Regex("""<input\b[^>]{0,2048}\bname="([^"]{1,256})"[^>]{0,2048}\bvalue="([^"]{0,2048})"[^>]{0,2048}>""", RegexOption.IGNORE_CASE)
    private val QUERY_ID_PATHS = setOf("/open", "/uc", "/download")

    fun fileId(input: String): String {
        val value = input.trim()
        if (ID.matches(value)) return value
        val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("INVALID_DRIVE_FILE_ID") }
        val host = uri.host?.lowercase(Locale.ROOT)
        require(uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            uri.port == -1 && uri.fragment == null &&
            (host == "drive.google.com" || host == "docs.google.com" || host == "drive.usercontent.google.com")) { "INVALID_DRIVE_FILE_ID" }
        val path = uri.rawPath ?: throw IllegalArgumentException("INVALID_DRIVE_FILE_ID")
        val candidate = when (host) {
            "drive.google.com", "drive.usercontent.google.com" -> DRIVE_FILE.matchEntire(path)?.groupValues?.get(1) ?: run {
                if (path !in QUERY_ID_PATHS) null
                else uri.rawQuery?.split('&')?.mapNotNull { part ->
                    part.takeIf { it.startsWith("id=") }?.removePrefix("id=")
                }?.singleOrNull()
            }
            "docs.google.com" -> DOC_FILE.matchEntire(path)?.groupValues?.get(2)
            else -> null
        }
        require(candidate != null && ID.matches(candidate)) { "INVALID_DRIVE_FILE_ID" }
        return candidate
    }

    /** Convert a public/shared Google Drive or Docs URL into its direct file download endpoint. */
    fun toDirectDownloadUrl(input: String): String? {
        val trimmed = input.trim().substringBefore('#')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null || uri.port != -1) return null
        val path = uri.rawPath ?: return null
        val keyParameters = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "resourcekey" }
        if (keyParameters.size > 1) return null
        val resourceKey = keyParameters.singleOrNull()?.let { parameter ->
            runCatching { URLDecoder.decode(parameter.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: return null
        }
        if (resourceKey != null && (resourceKey.isBlank() || resourceKey.length > 512 || resourceKey.any { it.isISOControl() })) return null
        val endpoint = when (host) {
            "drive.google.com", "drive.usercontent.google.com" -> {
                val id = runCatching { fileId(trimmed) }.getOrNull() ?: return null
                "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"
            }
            "docs.google.com" -> {
                val match = DOC_FILE.matchEntire(path) ?: return null
                val kind = match.groupValues[1]
                val id = match.groupValues[2].takeIf { ID.matches(it) } ?: return null
                when (kind) {
                    "document" -> "https://docs.google.com/document/d/$id/export?format=pdf"
                    "spreadsheets" -> "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
                    "presentation" -> "https://docs.google.com/presentation/d/$id/export/pdf"
                    else -> null
                }
            }
            else -> null
        } ?: return null
        // A resource key is part of a link-shared file's authorization. Keep it
        // opaque and correctly encoded; unrelated tracking/query data is discarded.
        return if (resourceKey == null) endpoint else endpoint +
            (if ('?' in endpoint) "&" else "?") + "resourcekey=" + URLEncoder.encode(resourceKey, "UTF-8")
    }

    /** Extract large-file virus-scan download confirmation URL from Google Drive HTML warning page. */
    fun parseConfirmationUrl(html: String, baseUri: URI): String? {
        if (html.length > MAX_CONFIRM_HTML_CHARS) return null
        val linkMatch = CONFIRM_LINK.find(html)
        if (linkMatch != null) {
            val target = linkMatch.groupValues[1].replace("&amp;", "&")
            return runCatching { baseUri.resolve(target).toString() }.getOrNull()
        }
        val formMatch = CONFIRM_FORM.find(html)
        if (formMatch != null) {
            val action = formMatch.groupValues[1].replace("&amp;", "&")
            val formBody = formMatch.groupValues[2]
            val params = CONFIRM_INPUT.findAll(formBody).take(64).map {
                "${URLEncoder.encode(it.groupValues[1], "UTF-8")}=${URLEncoder.encode(it.groupValues[2], "UTF-8")}"
            }.joinToString("&")
            val full = if (params.isEmpty()) action else if ('?' in action) "$action&$params" else "$action?$params"
            return runCatching { baseUri.resolve(full).toString() }.getOrNull()
        }
        return null
    }
}
