package com.jumlo.app.data.media

import android.content.Context
import android.util.AtomicFile
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.media.MediaNativeLibraries
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files
import java.util.Properties

/** APK-owned same-version repair for the five unaligned libraries in FFmpeg 0.18.1. */
internal object BundledFfmpegLibraries {
    @Synchronized fun install(context: Context) {
        val native = File(context.applicationInfo.nativeLibraryDir)
        val header = ByteArray(20)
        DataInputStream(File(native, "libffmpeg.so").inputStream()).use { it.readFully(header) }
        val abi = MediaNativeLibraries.processAbi(header) ?: return
        val manifest = Properties().apply {
            context.assets.open("media/native-overrides/manifest.properties").use(::load)
        }
        require(manifest.getProperty("source.version") == "1.6.0" &&
            manifest.getProperty("ffmpeg.version") == "0.18.1") { "MEDIA_NATIVE_REPAIR_INVALID" }
        fun expected(key: String) = manifest.getProperty(key).orEmpty().also {
            require(it.matches(Regex("[a-f0-9]{64}"))) { "MEDIA_NATIVE_REPAIR_INVALID" }
        }
        // Refuse to apply an ABI contract to a different upstream dependency payload.
        val archiveHash = File(native, "libffmpeg.zip.so").inputStream().use { Hashing.sha256(it) }
        require(archiveHash == expected("$abi.ffmpegArchive.sha256")) { "MEDIA_NATIVE_REPAIR_INVALID" }
        val directory = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) { "MEDIA_NATIVE_REPAIR_INVALID" }
        for (name in MediaNativeLibraries.repairedNames) {
            val checksum = expected("$abi.$name.sha256")
            val bytes = context.assets.open("media/native-overrides/$abi/$name").use { it.readBytes() }
            require(bytes.size in 1..2 * 1024 * 1024 &&
                Hashing.sha256(ByteArrayInputStream(bytes)) == checksum) { "MEDIA_NATIVE_REPAIR_INVALID" }
            MediaNativeLibraries.require16KiB(bytes, abi)
            val installed = File(directory, name)
            require(!Files.isSymbolicLink(installed.toPath())) { "MEDIA_NATIVE_REPAIR_INVALID" }
            val same = installed.isFile && installed.inputStream().use { Hashing.sha256(it) } == checksum
            if (same) continue
            val sink = AtomicFile(installed)
            val output = sink.startWrite()
            try { output.write(bytes); sink.finishWrite(output) }
            catch (error: Exception) { sink.failWrite(output); throw error }
        }
    }
}
