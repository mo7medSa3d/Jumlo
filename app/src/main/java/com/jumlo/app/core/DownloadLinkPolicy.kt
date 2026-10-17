package com.jumlo.app.core

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Pure, conservative routing: HTML pages are never treated as finished media payloads. */
object DownloadLinkPolicy {
    private val directTypes = setOf("pdf", "jpg", "jpeg", "png", "gif", "webp", "heic", "svg", "zip", "7z", "rar", "apk", "txt", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "mp4", "m4v", "mov", "webm", "mp3", "m4a", "flac", "ogg", "wav", "epub", "json", "xml")
    private val extractorDomains = setOf("youtube.com", "youtu.be", "tiktok.com", "instagram.com", "facebook.com", "fb.watch", "linkedin.com", "twitter.com", "x.com", "drive.google.com")
    fun isDirect(url:String):Boolean {
        val uri=runCatching { URI(url) }.getOrNull() ?: return false
        if(uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return false
        val host=uri.host?.lowercase(Locale.ROOT) ?: return false
        if(extractorDomains.any { host==it || host.endsWith(".$it") }) return false
        val extension=uri.path.orEmpty().substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in directTypes
    }
    fun cleanName(url:String, contentDisposition:String?=null, mime:String?=null):String {
        val fromHeader = contentDisposition?.let { header ->
            val encoded=Regex("filename\\*=UTF-8''([^;]+)",RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
            val decoded=encoded?.let { runCatching { URLDecoder.decode(it,"UTF-8") }.getOrNull() }
            decoded ?: Regex("filename\\s*=\\s*\"?([^\";]+)",RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
        }
        val path=runCatching { URI(url).path.orEmpty().substringAfterLast('/') }.getOrDefault("")
        val base=(fromHeader ?: path).ifBlank { "download" }
        val clean=base.replace(Regex("[\\p{Cc}\\p{Cf}/\\\\:*?\"<>|]")," ").replace(Regex("\\s+")," ").trim(' ', '.')
            .take(180).ifBlank { "download" }
        val extension=clean.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val type=mime?.substringBefore(';')?.lowercase(Locale.ROOT)
        val expected=when(type) {
            "application/pdf" -> "pdf";"application/zip" -> "zip";"application/vnd.android.package-archive" -> "apk"
            "image/jpeg" -> "jpg";"image/png" -> "png";"image/webp" -> "webp"
            "video/mp4" -> "mp4";"video/webm" -> "webm";"audio/mpeg" -> "mp3";"audio/mp4" -> "m4a"
            "application/json" -> "json";"text/plain" -> "txt";else -> ""
        }
        val name=when {
            expected.isBlank() || extension==expected || (extension=="jpeg" && expected=="jpg") -> clean
            extension in directTypes -> "${clean.substringBeforeLast('.') }.$expected"
            else -> "$clean.$expected"
        }
        return name.take(198).trimEnd(' ', '.')
    }
    fun unexpectedHtml(mime:String?,prefix:ByteArray):Boolean {
        if(mime?.lowercase(Locale.ROOT)?.substringBefore(';') in setOf("text/html", "application/xhtml+xml")) return true
        val head=prefix.toString(Charsets.UTF_8).trimStart('\uFEFF',' ','\r','\n','\t').lowercase(Locale.ROOT)
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }
}
