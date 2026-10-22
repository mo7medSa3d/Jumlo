package com.jumlo.app.data.media

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.jumlo.app.core.media.AudioOutput
import com.jumlo.app.core.media.MediaArguments
import com.jumlo.app.core.media.MediaAsset
import com.jumlo.app.core.media.MediaChoice
import com.jumlo.app.core.media.MediaProcessRunner
import com.jumlo.app.core.media.MediaProcessSpec
import com.jumlo.app.core.media.MediaLinks
import com.jumlo.app.core.media.MediaProgress
import com.jumlo.app.core.media.MediaProgressParser
import com.jumlo.app.data.jobs.OperationFailure
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/**
 * The APK-bundled multi-site engine.
 *
 * Public pages use the bundled local extractor. A user-configured service remains
 * available as an alternate; plain media links can also use the direct fetcher.
 */
object MediaRuntimeFactory {
    fun create(
        context: Context,
        client: OkHttpClient,
        sessions: MediaSessionStore,
        serviceUrl: () -> String = { "" },
        serviceToken: () -> String = { "" }
    ): MediaRuntime {
        val engines = buildList {
            add(PackagedMediaRuntime(context.applicationContext))
            add(RemoteExtractorRuntime(serviceUrl, client, serviceToken))
            add(NativeDirectMediaRuntime(client, sessions))
        }
        return RoutingMediaRuntime(engines)
    }
}

/**
 * This is an APK-pinned GPL native-runtime adapter, not a site-coverage guarantee.
 * ProcessRunner provides bounded output, cancellation and process-group teardown.
 * No WebView cookies or extra authentication headers are exported to it.
 */
private class PackagedMediaRuntime(private val context: Context) : MediaRuntime {
    @Volatile private var initialized = false
    private val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
    private val launcher = File(context.noBackupFilesDir, "media-launcher")
    private val nativeDir get() = File(context.applicationInfo.nativeLibraryDir)
    private val runner = MediaProcessRunner { pid, force ->
        Os.kill(-pid, if (force) OsConstants.SIGKILL else OsConstants.SIGTERM)
    }

    @Synchronized private fun initialize() {
        if (initialized) return
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            // FFmpeg.init only extracts its dependency ZIP. Repair the known 64-bit
            // WebP alignment defect before Python can launch FFmpeg or ffprobe.
            BundledFfmpegLibraries.install(context)
            require(launcher.isDirectory || launcher.mkdirs()) { "MEDIA_RUNTIME_INITIALIZATION_FAILED" }
            // Never fetch a remote executable. Restore the vetted resource from the installed APK.
            refreshBundledPayload()
            for (name in listOf("bootstrap.py", "network_guard.py")) {
                val file = File(launcher, name)
                context.assets.open("media/$name").use { input ->
                    val sink = AtomicFile(file)
                    val output = sink.startWrite()
                    try { input.copyTo(output); sink.finishWrite(output) }
                    catch (error: Exception) { sink.failWrite(output); throw error }
                }
            }
            for (name in listOf("libpython.so", "libffmpeg.so", "libffprobe.so", "libqjs.so"))
                if (!File(nativeDir, name).canExecute()) throw OperationFailure("MEDIA_NATIVE_ABI_UNAVAILABLE")
            initialized = true
        } catch (error: OperationFailure) { throw error }
        catch (_: Exception) { throw OperationFailure("MEDIA_RUNTIME_INITIALIZATION_FAILED") }
    }

    private fun sha256(input: java.io.InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    private fun refreshBundledPayload() {
        val installed = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        require(installed.parentFile?.isDirectory == true || installed.parentFile?.mkdirs() == true) {
            "MEDIA_RUNTIME_INITIALIZATION_FAILED"
        }
        // Native dependencies and the extractor have separate release cadences. Do not
        // restore the older extractor carried by the native runtime's Maven artifact.
        val properties = Properties().apply {
            context.assets.open("media/extractor.properties").use(::load)
        }
        val expected = properties.getProperty("sha256").orEmpty()
        require(expected.matches(Regex("[a-f0-9]{64}"))) { "MEDIA_RUNTIME_INITIALIZATION_FAILED" }
        val bundledHash = context.assets.open("media/yt-dlp").use(::sha256)
        val hash = bundledHash.joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == expected) { "MEDIA_RUNTIME_INITIALIZATION_FAILED" }
        // Avoid repeated multi-megabyte rewrites when the installed payload matches the APK.
        val same = installed.isFile && runCatching { installed.inputStream().use(::sha256).contentEquals(bundledHash) }.getOrDefault(false)
        if (same) return
        val sink = AtomicFile(installed)
        val output = sink.startWrite()
        try {
            context.assets.open("media/yt-dlp").use { it.copyTo(output) }
            sink.finishWrite(output)
        } catch (error: Exception) { sink.failWrite(output); throw error }
    }

    override fun refreshExtractor(): String {
        initialize()
        refreshBundledPayload()
        return "EXTRACTOR_RESTORED_FROM_APK"
    }

    private fun execute(url: String, args: List<String>, directory: File, allowHttp: Boolean,
        probe: Boolean, check: () -> Unit, progress: (MediaProgress) -> Unit = {}): String {
        check(); initialize(); check()
        val normalized = MediaLinks.normalize(url, allowHttp)
        require(args.takeLast(2) == listOf("--", normalized)) { "INVALID_MEDIA_COMMAND" }
        require(directory.isDirectory && !java.nio.file.Files.isSymbolicLink(directory.toPath())) { "MEDIA_WORKSPACE_MISSING" }
        val python = File(nativeDir, "libpython.so")
        val ffmpeg = File(nativeDir, "libffmpeg.so")
        val quickJs = File(nativeDir, "libqjs.so")
        val archive = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
        val command = listOf(python.absolutePath, "-u", File(launcher, "bootstrap.py").absolutePath,
            archive.absolutePath, "--no-plugin-dirs", "--js-runtimes", "quickjs:${quickJs.absolutePath}",
            "--ffmpeg-location", ffmpeg.absolutePath) + args
        val environment = mapOf(
            "PYTHONHOME" to File(packages, "python/usr").absolutePath,
            "PYTHONNOUSERSITE" to "1", "PYTHONDONTWRITEBYTECODE" to "1", "PYTHONUNBUFFERED" to "1",
            "LD_LIBRARY_PATH" to "${File(packages, "python/usr/lib")}:${File(packages,"ffmpeg/usr/lib")}",
            "SSL_CERT_FILE" to File(packages, "python/usr/etc/tls/cert.pem").absolutePath,
            "HOME" to directory.absolutePath, "TMPDIR" to directory.absolutePath,
            "PATH" to "${System.getenv("PATH") ?: "/system/bin"}:${nativeDir.absolutePath}",
            "UU_ALLOW_HTTP" to if (allowHttp) "1" else "0"
        )
        val result = try {
            runner.run(MediaProcessSpec(command, directory, environment,
                timeoutMillis = if (probe) 90_000L else 6 * 60 * 60 * 1000L,
                stdoutLimit = if (probe) 4 * 1024 * 1024 else 64 * 1024, tailOutput = !probe), check) { line ->
                    MediaProgressParser.parse(line)?.let(progress)
                }
        } catch (error: com.jumlo.app.core.media.MediaProcessException) {
            throw OperationFailure(error.code)
        }
        if (result.exitCode != 0) throw OperationFailure(classify(result.stderr))
        return result.stdout
    }

    override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset {
        val normalized = MediaLinks.normalize(url, allowHttp)
        val root = File(context.cacheDir, "media-probes").apply { require(isDirectory || mkdirs()) }
        val dir = File(root, UUID.randomUUID().toString()).apply { require(mkdir()) }
        return try { MediaMetadata.parse(execute(normalized, MediaArguments.probe(normalized, allowHttp), dir, allowHttp, true, check)) }
        finally { dir.deleteRecursively() }
    }

    override fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit, progress: (MediaProgress) -> Unit) {
        val normalized = MediaLinks.normalize(url, allowHttp)
        execute(normalized, MediaArguments.download(normalized, choice, audio, bitrate, allowHttp),
            directory, allowHttp, false, check, progress)
    }

    private fun classify(error: String): String {
        val message = error.lowercase(java.util.Locale.ROOT)
        return when {
            "media_http_consent_required" in message -> "CLEARTEXT_CONSENT_REQUIRED"
            "media_tls_downgrade" in message || "certificate" in message -> "MEDIA_TLS_FAILED"
            "unsupported url" in message || "unsupported site" in message -> "MEDIA_SITE_UNSUPPORTED"
            "requested format is not available" in message -> "MEDIA_REANALYSIS_REQUIRED"
            "private video" in message || "this video is private" in message || "members-only" in message -> "MEDIA_PRIVATE_CONTENT"
            "not available in your country" in message || "geo-restricted" in message -> "MEDIA_GEO_BLOCKED"
            "video unavailable" in message || "has been removed" in message || "404" in message -> "MEDIA_REMOVED"
            "sign in" in message || "login" in message || "cookies" in message || "not a bot" in message -> "MEDIA_SESSION_REQUIRED"
            "drm" in message || "encrypted" in message -> "MEDIA_ENCRYPTED_SOURCE"
            "429" in message || "rate limit" in message -> "MEDIA_RATE_LIMITED"
            "timed out" in message || "connection reset" in message || "network is unreachable" in message -> "MEDIA_NETWORK_ERROR"
            "403" in message || "401" in message -> "MEDIA_SOURCE_REJECTED"
            else -> "MEDIA_EXTRACTION_FAILED"
        }
    }
}
