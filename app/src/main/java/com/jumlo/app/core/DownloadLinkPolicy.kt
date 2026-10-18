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
        "youtube.com", "youtu.be", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com",
        "tiktok.com", "vm.tiktok.com", "vt.tiktok.com", "tiktokv.com",
        "instagram.com", "instagr.am", "ig.me",
        "facebook.com", "fb.watch", "fb.com", "fb.gg", "m.facebook.com",
        "linkedin.com", "lnkd.in",
        "twitter.com", "x.com", "t.co", "fxtwitter.com", "vxtwitter.com",
        "vimeo.com", "player.vimeo.com",
        "dailymotion.com", "dai.ly",
        "soundcloud.com", "on.soundcloud.com",
        "reddit.com", "redd.it", "v.redd.it", "redditmedia.com",
        "twitch.tv", "clips.twitch.tv",
        "pinterest.com", "pin.it",
        "bilibili.com", "b23.tv",
        "threads.net", "snapchat.com",
        "drive.google.com", "docs.google.com", "drive.usercontent.google.com"
    )

    /** Identifies the human-readable platform name from a URL for UI badges. */
    fun detectPlatformName(url: String): String? {
        val uri = runCatching { URI(url.trim().substringBefore('#')) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        fun matches(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }
        return when {
            matches("youtube.com", "youtu.be", "youtube-nocookie.com") -> "YouTube"
            matches("tiktok.com", "tiktokv.com") -> "TikTok"
            matches("instagram.com", "instagr.am", "ig.me") -> "Instagram"
            matches("facebook.com", "fb.watch", "fb.com", "fb.gg") -> "Facebook"
            matches("twitter.com", "x.com", "t.co", "fxtwitter.com", "vxtwitter.com") -> "X (Twitter)"
            matches("linkedin.com", "lnkd.in") -> "LinkedIn"
            matches("soundcloud.com") -> "SoundCloud"
            matches("vimeo.com") -> "Vimeo"
            matches("reddit.com", "redd.it", "redditmedia.com") -> "Reddit"
            matches("twitch.tv") -> "Twitch"
            matches("dailymotion.com", "dai.ly") -> "Dailymotion"
            matches("pinterest.com", "pin.it") -> "Pinterest"
            matches("bilibili.com", "b23.tv") -> "Bilibili"
            matches("threads.net", "snapchat.com") -> "Threads"
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

    /** A platform page needs metadata inspection, never a direct HTML download. */
    fun requiresMediaExtractor(url: String): Boolean =
        detectPlatformName(url)?.let { it != "Google Drive" } == true

    /** Route non-direct public pages into inspect/probe; the media workspace still
     * probes direct files with no extension before trying a supported page extractor. */
    fun prefersMediaAnalysis(url: String): Boolean =
        requiresMediaExtractor(url) || (!isDirect(url) && !isGoogleDriveDownload(url))

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
            val decoded = encoded?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
            decoded ?: Regex("filename\\s*=\\s*\"?([^\";]+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
        }
        val path = runCatching { URI(url).path.orEmpty().substringAfterLast('/') }.getOrDefault("")
        val base = (fromHeader ?: path).takeUnless { it in setOf("download", "uc", "open", "view", "edit", "export") }.orEmpty().ifBlank { "download" }
        val clean = base.replace(Regex("[\\p{Cc}\\p{Cf}/\\\\:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim(' ', '.')
            .ifBlank { "download" }
        val extension = clean.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val type = mime?.substringBefore(';')?.lowercase(Locale.ROOT)
        val expected = when (type) {
            "application/pdf" -> "pdf"; "application/zip", "application/x-zip-compressed" -> "zip"
            "application/x-7z-compressed" -> "7z"; "application/vnd.rar", "application/x-rar-compressed" -> "rar"
            "application/epub+zip" -> "epub"; "application/vnd.android.package-archive" -> "apk"
            "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"
            "image/gif" -> "gif"; "image/heic" -> "heic"; "image/svg+xml" -> "svg"
            "video/mp4" -> "mp4"; "video/quicktime" -> "mov"; "video/webm" -> "webm"; "video/x-matroska" -> "mkv"
            "audio/mpeg" -> "mp3"; "audio/mp4", "audio/x-m4a" -> "m4a"; "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"; "audio/flac", "audio/x-flac" -> "flac"; "audio/aac" -> "aac"
            "audio/wav", "audio/x-wav" -> "wav"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "text/csv" -> "csv"; "application/xml", "text/xml" -> "xml"
            "application/json" -> "json"; "text/plain" -> "txt"; else -> ""
        }
        val name = when {
            expected.isBlank() || extension == expected || (extension == "jpeg" && expected == "jpg") -> clean
            extension in directTypes -> "${clean.substringBeforeLast('.')}.$expected"
            else -> "$clean.$expected"
        }
        return SafeNames.fitUtf8(name.trimEnd(' ', '.'))
    }

    fun unexpectedHtml(mime: String?, prefix: ByteArray): Boolean {
        if (mime?.lowercase(Locale.ROOT)?.substringBefore(';') in setOf("text/html", "application/xhtml+xml")) return true
        val head = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase(Locale.ROOT)
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }
}
