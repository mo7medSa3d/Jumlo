package com.jumlo.app.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.system.Os
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.BuildConfig
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.media.MediaArguments
import com.jumlo.app.core.media.MediaCatalog
import com.jumlo.app.core.media.MediaMode
import com.jumlo.app.core.media.MediaNativeLibraries
import com.jumlo.app.core.media.MediaOutputPolicy
import com.jumlo.app.core.media.MediaProcessRunner
import com.jumlo.app.core.media.MediaProcessSpec
import com.jumlo.app.data.media.MediaMetadata
import com.jumlo.app.data.media.MediaRuntimeFactory
import com.jumlo.app.data.media.MediaSessionStore
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.DataInputStream
import java.util.Properties
import java.util.UUID

/** No external server: exercise the APK's real Python, extractor, QuickJS and FFmpeg ABI. */
@RunWith(AndroidJUnit4::class)
class PackagedMediaRuntimeDeviceTest {
    @Test fun packagedRuntimeStartsSelectsAndMuxesRealTracks() {
        assumeTrue("The explicitly requested lite build omits the native engine", BuildConfig.EXTENDED_MEDIA)
        // Explicit allowance for slow software TCG emulators; the default remains 90 seconds.
        val processTimeoutMillis = InstrumentationRegistry.getArguments()
            .getString("mediaProcessTimeoutMillis")
            ?.let { requireNotNull(it.toLongOrNull()) { "mediaProcessTimeoutMillis must be an integer" } }
            ?: 90_000L
        require(processTimeoutMillis in 90_000L..300_000L) {
            "mediaProcessTimeoutMillis must be between 90000 and 300000"
        }
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "native-media-test-${UUID.randomUUID()}").apply { mkdirs() }
        val native = File(app.applicationInfo.nativeLibraryDir)
        val packages = File(app.noBackupFilesDir, "youtubedl-android/packages")
        val python = File(native, "libpython.so").absolutePath
        val ffmpeg = File(native, "libffmpeg.so").absolutePath
        val environment = mapOf(
            "PYTHONHOME" to File(packages, "python/usr").absolutePath,
            "PYTHONNOUSERSITE" to "1", "PYTHONDONTWRITEBYTECODE" to "1", "PYTHONUNBUFFERED" to "1",
            "LD_LIBRARY_PATH" to "${File(packages, "python/usr/lib")}:${File(packages, "ffmpeg/usr/lib")}",
            "SSL_CERT_FILE" to File(packages, "python/usr/etc/tls/cert.pem").absolutePath,
            "HOME" to directory.absolutePath, "TMPDIR" to directory.absolutePath,
            "PATH" to "${System.getenv("PATH") ?: "/system/bin"}:${native.absolutePath}",
            "UU_ALLOW_HTTP" to "0"
        )
        val runner = MediaProcessRunner { pid, force ->
            Os.kill(-pid, if (force) OsConstants.SIGKILL else OsConstants.SIGTERM)
        }
        fun run(command: List<String>): String {
            val result = runner.run(MediaProcessSpec(command, directory, environment,
                timeoutMillis = processTimeoutMillis, stdoutLimit = 256 * 1024))
            assertEquals("Native command failed: ${result.stderr.take(2000)}", 0, result.exitCode)
            return result.stdout.trim()
        }
        val childBootstrap = "import os,sys,subprocess;os.setsid();print('UU_PID:'+str(os.getpid()),flush=True);sys.exit(subprocess.run(sys.argv[1:]).returncode)"
        fun nativeCommand(args: List<String>) = run(listOf(python, "-u", "-c", childBootstrap) + args)
        try {
            // Initialize through the production factory, including asset integrity and native extraction.
            val runtime = MediaRuntimeFactory.create(app, OkHttpClient(), MediaSessionStore(app))
            assertEquals("EXTRACTOR_RESTORED_FROM_APK", runtime.refreshExtractor())
            val executableHeader = ByteArray(20)
            DataInputStream(File(native, "libffmpeg.so").inputStream()).use { it.readFully(executableHeader) }
            MediaNativeLibraries.processAbi(executableHeader)?.let { abi ->
                val repair = Properties().apply {
                    app.assets.open("media/native-overrides/manifest.properties").use(::load)
                }
                for (name in MediaNativeLibraries.repairedNames) {
                    val library = File(packages, "ffmpeg/usr/lib/$name")
                    assertEquals("Installed repair differs: $abi/$name", repair.getProperty("$abi.$name.sha256"),
                        library.inputStream().use { Hashing.sha256(it) })
                    MediaNativeLibraries.require16KiB(library.readBytes(), abi)
                }
            }
            val command = listOf(python, "-u", File(app.noBackupFilesDir, "media-launcher/bootstrap.py").absolutePath,
                File(app.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp").absolutePath,
                "--no-plugin-dirs", "--js-runtimes", "quickjs:${File(native, "libqjs.so").absolutePath}",
                "--ffmpeg-location", ffmpeg)
            assertEquals("2026.10.21", run(command + listOf("--ignore-config", "--version")))
            assertEquals("42", nativeCommand(listOf(File(native, "libqjs.so").absolutePath, "-e", "print(6*7)")))

            val metadata = File(directory, "fixture.info.json").apply { writeText("""
                {"id":"fixture","title":"Fixture","extractor":"fixture","webpage_url":"https://example.org/watch/fixture","formats":[
                 {"format_id":"137","url":"https://example.org/video.mp4","ext":"mp4","vcodec":"avc1","acodec":"none","width":160,"height":90},
                 {"format_id":"140","url":"https://example.org/audio.m4a","ext":"m4a","vcodec":"none","acodec":"mp4a"}]}
            """.trimIndent()) }
            val choice = MediaCatalog.choices(MediaMetadata.parse(metadata.readText()), MediaMode.VIDEO).single()
            val selection = MediaArguments.download("https://example.org/watch/fixture", choice).dropLast(2) +
                listOf("--simulate", "--load-info-json", metadata.absolutePath, "--print", "%(format_id)s")
            assertEquals("137+140", run(command + selection))

            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            for (name in listOf("video-only.mp4", "audio-only.m4a"))
                assets.open("media/$name").use { input -> File(directory, name).outputStream().use { output -> input.copyTo(output) } }
            val output = File(directory, "media.mp4")
            nativeCommand(listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-i", File(directory, "video-only.mp4").absolutePath,
                "-i", File(directory, "audio-only.m4a").absolutePath,
                "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", output.absolutePath))
            assertEquals("video/mp4", MediaOutputPolicy.inspect(output).mimeType)
            val tracks = nativeCommand(listOf(File(native, "libffprobe.so").absolutePath,
                "-v", "error", "-show_entries", "stream=codec_name,codec_type,width,height",
                "-of", "csv=p=0", output.absolutePath))
            assertTrue(tracks, tracks.contains("h264,video,160,90") && tracks.contains("aac,audio"))
            val decoder = MediaExtractor()
            try {
                decoder.setDataSource(output.absolutePath)
                val types = (0 until decoder.trackCount).map { decoder.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
                assertTrue(types.toString(), types.any { it.startsWith("video/") } && types.any { it.startsWith("audio/") })
            } finally { decoder.release() }
        } finally { directory.deleteRecursively() }
    }
}
