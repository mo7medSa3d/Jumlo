package com.jumlo.app.data.media

import android.content.Context
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.OperationFailure
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.util.UUID

object MediaRuntimeFactory {
    fun create(context: Context): MediaRuntime = PackagedMediaRuntime(context.applicationContext)
}

/** Adapts the pinned 0.18.1 runtime layout, not its unbounded process wrapper. */
private class PackagedMediaRuntime(private val context: Context) : MediaRuntime {
    @Volatile private var initialized = false
    private val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
    private val tools = File(context.noBackupFilesDir, "media-launcher")
    private val bin get() = File(context.applicationInfo.nativeLibraryDir)
    private val runner = MediaProcessRunner { pid, force ->
        Os.kill(-pid, if (force) OsConstants.SIGKILL else OsConstants.SIGTERM)
    }
    @Synchronized private fun initialize() {
        if (initialized) return
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            refreshBundledPayload()
            check(tools.mkdirs() || tools.isDirectory)
            for (name in listOf("bootstrap.py", "network_guard.py")) {
                context.assets.open("media/$name").use { input -> File(tools, name).outputStream().use { output -> input.copyTo(output) } }
            }
            for (name in listOf("libpython.so", "libffmpeg.so", "libqjs.so"))
                if (!File(bin, name).canExecute()) throw OperationFailure("MEDIA_NATIVE_ABI_UNAVAILABLE")
            initialized = true
        } catch (e: OperationFailure) { throw e }
        catch (_: Exception) { throw OperationFailure("MEDIA_RUNTIME_INITIALIZATION_FAILED") }
    }
    private fun refreshBundledPayload() {
        val archive = AtomicFile(File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp"))
        val output = archive.startWrite()
        try {
            context.resources.openRawResource(com.yausername.youtubedl_android.R.raw.ytdlp).use { it.copyTo(output) }
            archive.finishWrite(output)
        } catch (error: Exception) { archive.failWrite(output); throw error }
    }
    override fun refreshExtractor(): String {
        initialize()
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            status?.name ?: "EXTRACTOR_UP_TO_DATE"
        } catch (_: Exception) {
            refreshBundledPayload()
            "EXTRACTOR_RESTORED_FROM_APK"
        }
    }
    private fun execute(args: List<String>, directory: File, allowHttp: Boolean, probe: Boolean,
        check: () -> Unit, progress: (MediaProgress) -> Unit = {}): String {
        check(); initialize(); check()
        require(directory.isDirectory) { "MEDIA_WORKSPACE_MISSING" }
        val archive = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        val native = listOf("--no-plugin-dirs", "--js-runtimes", "quickjs:${File(bin,"libqjs.so").absolutePath}",
            "--ffmpeg-location", File(bin, "libffmpeg.so").absolutePath)
        val command = listOf(File(bin, "libpython.so").absolutePath, "-u", File(tools,"bootstrap.py").absolutePath, archive.absolutePath) + native + args
        val environment = mapOf(
            "PYTHONHOME" to File(packages,"python/usr").absolutePath,
            "PYTHONNOUSERSITE" to "1", "PYTHONDONTWRITEBYTECODE" to "1", "PYTHONUNBUFFERED" to "1",
            "LD_LIBRARY_PATH" to "${File(packages,"python/usr/lib")}:${File(packages,"ffmpeg/usr/lib")}",
            "SSL_CERT_FILE" to File(packages,"python/usr/etc/tls/cert.pem").absolutePath,
            "HOME" to directory.absolutePath, "TMPDIR" to directory.absolutePath,
            "PATH" to "${System.getenv("PATH") ?: "/system/bin"}:${bin.absolutePath}",
            "UU_ALLOW_HTTP" to if (allowHttp) "1" else "0")
        val result = runner.run(MediaProcessSpec(command, directory, environment,
            timeoutMillis = if (probe) 90_000 else 6 * 60 * 60 * 1000L,
            stdoutLimit = if (probe) 4 * 1024 * 1024 else 64 * 1024, tailOutput = !probe), check) { line ->
                MediaProgressParser.parse(line)?.let(progress)
            }
        if (result.exitCode != 0) throw OperationFailure(classify(result.stderr))
        return result.stdout
    }
    override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset {
        val root = File(context.cacheDir, "media-probes").apply { mkdirs() }
        val dir = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        return try { MediaMetadata.parse(execute(MediaArguments.probe(url,allowHttp),dir,allowHttp,true,check)) }
        finally { dir.deleteRecursively() }
    }
    override fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit, progress: (MediaProgress) -> Unit) {
        execute(MediaArguments.download(url,choice,audio,bitrate,allowHttp),directory,allowHttp,false,check,progress)
    }
    private fun classify(stderr: String): String {
        val text = stderr.lowercase()
        return when {
            "media_http_consent_required" in text -> "CLEARTEXT_CONSENT_REQUIRED"
            "media_tls_downgrade" in text || "certificate" in text -> "MEDIA_TLS_FAILED"
            "unsupported url" in text -> "MEDIA_SITE_UNSUPPORTED"
            "requested format is not available" in text -> "MEDIA_REANALYSIS_REQUIRED"
            "private video" in text || "this video is private" in text || "members-only" in text -> "MEDIA_PRIVATE_CONTENT"
            "not available in your country" in text || "geo-restricted" in text || "geoblocked" in text -> "MEDIA_GEO_BLOCKED"
            "video unavailable" in text || "has been removed" in text || "404" in text || "does not exist" in text -> "MEDIA_REMOVED"
            "sign in" in text || "login" in text || "cookies" in text -> "MEDIA_SESSION_REQUIRED"
            "drm" in text -> "MEDIA_ENCRYPTED_SOURCE"
            "429" in text || "too many requests" in text || "rate limit" in text -> "MEDIA_RATE_LIMITED"
            "timed out" in text || "connection reset" in text || "name or service not known" in text || "network is unreachable" in text -> "MEDIA_NETWORK_ERROR"
            "403" in text || "401" in text -> "MEDIA_SOURCE_REJECTED"
            else -> "MEDIA_EXTRACTION_FAILED"
        }
    }
}
