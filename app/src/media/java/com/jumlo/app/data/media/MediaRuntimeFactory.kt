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
import com.jumlo.app.core.media.MediaNativeLibraries
import com.jumlo.app.core.media.MediaFailurePolicy
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
        serviceToken: () -> String = { "" },
        serviceConfiguration: (() -> Pair<String, String>)? = null
    ): MediaRuntime {
        val engines = buildList {
            add(PackagedMediaRuntime(context.applicationContext,client))
            add(RemoteExtractorRuntime(serviceUrl, client, serviceToken,
                allowDebugLoopback = com.jumlo.app.BuildConfig.DEBUG, configurationProvider = serviceConfiguration))
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
private class PackagedMediaRuntime(private val context: Context,private val client:OkHttpClient) : MediaRuntime {
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
            MediaNativeLibraries.removePlatformLzmaAliases(packages)
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
        if (result.exitCode != 0) throw OperationFailure(MediaFailurePolicy.extractorMessage(result.stderr))
        return result.stdout
    }

    override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset {
        val normalized = MediaLinks.normalize(url, allowHttp)
        val root = File(context.cacheDir, "media-probes").apply { require(isDirectory || mkdirs()) }
        val dir = File(root, UUID.randomUUID().toString()).apply { require(mkdir()) }
        return try {
            val raw=execute(normalized, MediaArguments.probe(normalized, allowHttp), dir, allowHttp, true, check)
            val asset=MediaMetadata.parse(raw)
            val format=asset.formats.singleOrNull()
            if(asset.extractor.equals("generic",true) && format!=null && !format.hasVideo && !format.hasAudio) {
                val source=MediaMetadata.directProbeSource(raw,format.id) ?: throw OperationFailure("MEDIA_NO_USABLE_FORMAT")
                val sample=File(dir,"probe.sample")
                val bytes=MediaProbeSample(client).fetch(source.url,allowHttp,sample,source.headers,check)
                val python=File(nativeDir,"libpython.so")
                val ffprobe=File(nativeDir,"libffprobe.so")
                // The probe sees only a bounded local sample. OkHttp owns TLS,
                // redirects and cancellation; FFmpeg cannot fetch nested URLs.
                val command=listOf(python.absolutePath,"-u","-c",
                    "import os,sys; os.setsid(); print('UU_PID:'+str(os.getpid()),flush=True); os.execv(sys.argv[1],sys.argv[1:])",
                    ffprobe.absolutePath,"-v","error","-protocol_whitelist","file",
                    "-format_whitelist","mov,matroska,mp3,ogg,wav,flac,aac,mpegts",
                    "-probesize","2097152","-analyzeduration","1000000",
                    "-show_entries","stream=codec_type,codec_name,width,height,avg_frame_rate,bit_rate:stream_disposition=attached_pic:format=duration,bit_rate",
                    "-of","json","-i",sample.absolutePath)
                val environment=mapOf(
                    "PYTHONHOME" to File(packages,"python/usr").absolutePath,
                    "PYTHONNOUSERSITE" to "1","PYTHONDONTWRITEBYTECODE" to "1",
                    "LD_LIBRARY_PATH" to "${File(packages,"python/usr/lib")}:${File(packages,"ffmpeg/usr/lib")}",
                    "HOME" to dir.absolutePath,"TMPDIR" to dir.absolutePath)
                val result=runner.run(MediaProcessSpec(command,dir,environment,timeoutMillis=20_000,stdoutLimit=262144),check)
                if(result.exitCode!=0) throw OperationFailure("MEDIA_NO_USABLE_FORMAT")
                MediaProbeMetadata.enrich(asset,result.stdout,bytes)
            } else asset
        }
        finally { dir.deleteRecursively() }
    }

    override fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit, progress: (MediaProgress) -> Unit) {
        val normalized = MediaLinks.normalize(url, allowHttp)
        execute(normalized, MediaArguments.download(normalized, choice, audio, bitrate, allowHttp),
            directory, allowHttp, false, check, progress)
    }

}
