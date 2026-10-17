package com.jumlo.app.data.media

import android.content.Context
import com.jumlo.app.core.DownloadLinkPolicy
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.PlatformHeaderPolicy
import com.jumlo.app.core.StreamTransfer
import com.jumlo.app.core.TransferRequest
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.processing.OkHttpTransport
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Native direct-media transfers without Python, yt-dlp, FFmpeg or cloud processing. */
object MediaRuntimeFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context,
               client: OkHttpClient, sessions: MediaSessionStore): MediaRuntime =
        NativeDirectMediaRuntime(client, sessions)
}

/** Download only original, directly served audio/video files, never social landing pages. */
private class NativeDirectMediaRuntime(client: OkHttpClient, private val sessions: MediaSessionStore) : MediaRuntime {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val audioExt = setOf("m4a", "mp3", "ogg", "opus", "flac", "wav", "aac")
    private val videoExt = setOf("mp4", "m4v", "mov", "webm", "mkv", "ts")
    private val mimeToExt = mapOf(
        "video/mp4" to "mp4", "video/quicktime" to "mov", "video/webm" to "webm",
        "video/x-matroska" to "mkv", "video/mp2t" to "ts",
        "audio/mp4" to "m4a", "audio/mpeg" to "mp3", "audio/ogg" to "ogg",
        "audio/opus" to "opus", "audio/flac" to "flac", "audio/wav" to "wav",
        "audio/aac" to "aac"
    )

    private fun headers(url: String): Map<String, String> {
        val custom = sessions.getExtraHeaders().lineSequence().map(String::trim)
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        // Refresh on each redirected host; never copy a site's cookies to another host.
        return PlatformHeaderPolicy.headersFor(
            url = url, customUserAgent = sessions.getCustomUserAgent(),
            preferDesktop = sessions.isPreferDesktopUa(),
            cookieHeader = sessions.cookieHeaderFor(url), customHeaders = custom
        )
    }

    private data class Remote(val extension: String, val title: String, val size: Long?)

    private fun inspectDirect(url: String, allowHttp: Boolean, check: () -> Unit): Remote {
        if (DownloadLinkPolicy.requiresMediaExtractor(url)) throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
        var current = HttpPolicy.url(url, allowHttp)
        var rangeGet = false
        repeat(12) { hop ->
            check()
            val builder = Request.Builder().url(current.toString())
            headers(current.toString()).forEach { (name, value) -> builder.header(name, value) }
            val request = if (rangeGet) builder.get().header("Range", "bytes=0-0").build()
                else builder.head().build()
            http.newCall(request).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    if (hop >= 10) throw OperationFailure("TOO_MANY_REDIRECTS")
                    val location = response.header("Location") ?: throw OperationFailure("INVALID_REDIRECT")
                    val next = HttpPolicy.url(current.resolve(location).toString(), allowHttp)
                    if (current.scheme == "https" && next.scheme != "https") throw OperationFailure("INSECURE_REDIRECT")
                    current = next
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
                val extension = if (pathExt in audioExt || pathExt in videoExt)
                    pathExt else mimeToExt[mime]
                if (extension == null) throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
                val filename = DownloadLinkPolicy.cleanName(current.toString(),
                    response.header("Content-Disposition"), mime)
                val title = filename.substringBeforeLast('.', filename).take(180).ifBlank { "media" }
                val rangeTotal = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                val bytes = rangeTotal ?: if (!rangeGet) response.header("Content-Length")?.toLongOrNull() else null
                return Remote(extension, title, bytes?.takeIf { it > 0L })
            }
        }
        throw OperationFailure("TOO_MANY_REDIRECTS")
    }

    override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset {
        val normalized = MediaLinks.normalize(url, allowHttp)
        val remote = inspectDirect(normalized, allowHttp, check)
        val isAudio = remote.extension in audioExt
        // Do not invent resolutions, bitrates or audio tracks on MP4 files.
        return MediaAsset(
            id = Hashing.sha256(ByteArrayInputStream(normalized.toByteArray(Charsets.UTF_8))),
            extractor = "DirectMedia", title = remote.title, durationSeconds = null,
            formats = listOf(MediaFormat(
                id = "source", extension = remote.extension,
                videoCodec = if (isAudio) "none" else "direct",
                audioCodec = if (isAudio) "direct" else "none",
                bytes = remote.size, protocol = URI(normalized).scheme.lowercase(Locale.ROOT)
            ))
        )
    }

    override fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit,
        progress: (MediaProgress) -> Unit) {
        check()
        if (audio != AudioOutput.ORIGINAL) throw OperationFailure("MEDIA_CONVERSION_UNAVAILABLE")
        if (choice.requiresMux || choice.formats.size != 1 || choice.formatIds != listOf("source"))
            throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
        val extension = choice.formats.single().extension
        if (extension !in audioExt && extension !in videoExt)
            throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
        if (!directory.isDirectory) throw OperationFailure("MEDIA_WORKSPACE_MISSING")
        val normalized = MediaLinks.normalize(url, allowHttp)
        if (DownloadLinkPolicy.requiresMediaExtractor(normalized))
            throw OperationFailure("MEDIA_SITE_UNSUPPORTED")
        val partial = File(directory, "native-media.part")
        // Retry restarts unpublished transfers; no false promise of resumability.
        if (partial.exists() && !partial.delete()) throw OperationFailure("MEDIA_WORKSPACE_LIMIT")
        val target = File(directory, "media.$extension")
        val result = StreamTransfer(OkHttpTransport(http, ::headers)).execute(
            TransferRequest(url = normalized, partialFile = partial,
                cleartextConsent = allowHttp, maxBytes = 8L * 1024 * 1024 * 1024,
                minFreeBytes = 64L * 1024 * 1024),
            checkCancel = check,
            progress = { done, total ->
                progress(MediaProgress("source", done, total, null, null, null))
            }
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
