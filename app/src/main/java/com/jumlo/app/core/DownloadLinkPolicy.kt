package com.jumlo.app.core

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

enum class DirectFileKind {
    PDF, VIDEO, AUDIO, IMAGE, ARCHIVE, OTHER
}

/** Smart URL routing & classification for all social/video/audio platforms, Google Drive, and direct files. */
object DownloadLinkPolicy {
    private val directTypes = setOf(
        "pdf", "jpg", "jpeg", "png", "gif", "webp", "heic", "svg",
        "zip", "7z", "rar", "apk", "txt", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "mp4", "m4v", "mov", "webm", "mkv", "mp3", "m4a", "flac", "ogg", "opus", "wav", "aac",
        "epub", "json", "xml"
    )
    private val extractorDomains = setOf(
        "youtube.com", "youtu.be", "m.youtube.com", "music.youtube.com",
        "tiktok.com", "vm.tiktok.com", "vt.tiktok.com",
        "instagram.com", "instagr.am",
        "facebook.com", "fb.watch", "fb.com", "m.facebook.com",
        "linkedin.com", "lnkd.in",
        "twitter.com", "x.com", "t.co",
        "vimeo.com", "player.vimeo.com",
        "dailymotion.com", "dai.ly",
        "soundcloud.com", "on.soundcloud.com",
        "reddit.com", "redd.it", "v.redd.it",
        "twitch.tv", "clips.twitch.tv",
        "pinterest.com", "pin.it",
        "bilibili.com", "b23.tv",
        "threads.net",
        "drive.google.com", "docs.google.com"
    )

    /** Identifies the human-readable platform name from a URL for UI badges. */
    fun detectPlatformName(url: String): String? {
        val uri = runCatching { URI(url.trim().substringBefore('#')) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        fun matches(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }
        return when {
            matches("youtube.com", "youtu.be") -> "YouTube"
            matches("tiktok.com") -> "TikTok"
            matches("instagram.com", "instagr.am") -> "Instagram"
            matches("facebook.com", "fb.watch", "fb.com") -> "Facebook"
            matches("twitter.com", "x.com", "t.co") -> "X (Twitter)"
            matches("linkedin.com", "lnkd.in") -> "LinkedIn"
            matches("soundcloud.com") -> "SoundCloud"
            matches("vimeo.com") -> "Vimeo"
            matches("reddit.com", "redd.it") -> "Reddit"
            matches("twitch.tv") -> "Twitch"
            matches("dailymotion.com", "dai.ly") -> "Dailymotion"
            matches("pinterest.com", "pin.it") -> "Pinterest"
            matches("threads.net") -> "Threads"
            matches("drive.google.com", "docs.google.com", "drive.usercontent.google.com") -> "Google Drive"
            else -> null
        }
    }

    /** Classifies a direct file by its MIME type and filename extension so the UI can show tailored quality/format options. */
    fun classifyDirectFile(name: String, mime: String?): DirectFileKind {
        val cleanMime = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            cleanMime == "application/pdf" || ext == "pdf" -> DirectFileKind.PDF
            cleanMime.startsWith("video/") || ext in setOf("mp4", "m4v", "mov", "webm", "mkv") -> DirectFileKind.VIDEO
            cleanMime.startsWith("audio/") || ext in setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac") -> DirectFileKind.AUDIO
            cleanMime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") -> DirectFileKind.IMAGE
            cleanMime in setOf("application/zip", "application/x-zip-compressed", "application/x-rar-compressed", "application/x-7z-compressed") ||
                ext in setOf("zip", "rar", "7z", "tar", "gz") -> DirectFileKind.ARCHIVE
            else -> DirectFileKind.OTHER
        }
    }

    /** True when the URL is a valid shared Google Drive or Google Docs/Sheets/Slides file link. */
    fun isGoogleDriveDownload(url: String): Boolean = DriveLink.toDirectDownloadUrl(url) != null

    /** Rewrites Google Drive / Docs sharing URLs to their direct download endpoints; returns other URLs unchanged. */
    fun resolveDirectUrl(url: String): String = DriveLink.toDirectDownloadUrl(url) ?: url.trim()

    fun isDirect(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (extractorDomains.any { host == it || host.endsWith(".$it") }) return false
        val extension = uri.path.orEmpty().substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in directTypes
    }

    fun cleanName(url: String, contentDisposition: String? = null, mime: String? = null): String {
        val fromHeader = contentDisposition?.let { header ->
            val encoded = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
            val decoded = encoded?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            decoded ?: Regex("filename\\s*=\\s*\"?([^\";]+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
        }
        val path = runCatching { URI(url).path.orEmpty().substringAfterLast('/') }.getOrDefault("")
        val base = (fromHeader ?: path).takeUnless { it in setOf("download", "uc", "open", "view", "edit", "export") }.orEmpty().ifBlank { "download" }
        val clean = base.replace(Regex("[\\p{Cc}\\p{Cf}/\\\\:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim(' ', '.')
            .take(180).ifBlank { "download" }
        val extension = clean.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val type = mime?.substringBefore(';')?.lowercase(Locale.ROOT)
        val expected = when (type) {
            "application/pdf" -> "pdf"; "application/zip" -> "zip"; "application/vnd.android.package-archive" -> "apk"
            "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"
            "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/x-matroska" -> "mkv"
            "audio/mpeg" -> "mp3"; "audio/mp4" -> "m4a"; "audio/ogg" -> "ogg"; "audio/wav" -> "wav"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "application/json" -> "json"; "text/plain" -> "txt"; else -> ""
        }
        val name = when {
            expected.isBlank() || extension == expected || (extension == "jpeg" && expected == "jpg") -> clean
            extension in directTypes -> "${clean.substringBeforeLast('.')}.$expected"
            else -> "$clean.$expected"
        }
        return name.take(198).trimEnd(' ', '.')
    }

    fun unexpectedHtml(mime: String?, prefix: ByteArray): Boolean {
        if (mime?.lowercase(Locale.ROOT)?.substringBefore(';') in setOf("text/html", "application/xhtml+xml")) return true
        val head = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase(Locale.ROOT)
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }
}
