package com.jumlo.app.data.media

import android.content.Context
import android.util.AtomicFile
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.media.MediaNativeLibraries
import com.jumlo.app.data.jobs.OperationFailure
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.Properties

/** APK-owned same-version repair for the five unaligned libraries in FFmpeg 0.18.1. */
internal object BundledFfmpegLibraries {
    private fun invalid(): Nothing = throw OperationFailure("MEDIA_NATIVE_REPAIR_INVALID")
    private fun unavailable(): Nothing = throw OperationFailure("MEDIA_NATIVE_REPAIR_UNAVAILABLE")

    @Synchronized fun install(context: Context) {
        val native = File(context.applicationInfo.nativeLibraryDir)
        val header = ByteArray(20)
        try {
            DataInputStream(File(native, "libffmpeg.so").inputStream()).use { it.readFully(header) }
        } catch (e: FileNotFoundException) {
            throw OperationFailure("MEDIA_NATIVE_ABI_UNAVAILABLE")
        } catch (e: IOException) {
            invalid()
        }
        // A non-ELF header or a misaligned repair asset is a repair mismatch;
        // an unknown processor keeps its own ABI code instead of collapsing.
        val abi = try {
            MediaNativeLibraries.processAbi(header)
        } catch (e: IllegalArgumentException) {
            if (e.message == "MEDIA_NATIVE_ABI_UNAVAILABLE") throw OperationFailure("MEDIA_NATIVE_ABI_UNAVAILABLE")
            invalid()
        } ?: return
        val manifest = try {
            Properties().apply {
                context.assets.open("media/native-overrides/manifest.properties").use(::load)
            }
        } catch (e: FileNotFoundException) {
            unavailable()
        } catch (e: IOException) {
            invalid()
        }
        if (!(manifest.getProperty("source.version") == "1.6.0" &&
            manifest.getProperty("ffmpeg.version") == "0.18.1")) invalid()
        fun expected(key: String) = manifest.getProperty(key).orEmpty().also {
            if (!it.matches(Regex("[a-f0-9]{64}"))) invalid()
        }
        // Refuse to apply an ABI contract to a different upstream dependency payload.
        val archiveHash = File(native, "libffmpeg.zip.so").inputStream().use { Hashing.sha256(it) }
        if (archiveHash != expected("$abi.ffmpegArchive.sha256")) invalid()
        val directory = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        if (!(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))) invalid()
        for (name in MediaNativeLibraries.repairedNames) {
            val checksum = expected("$abi.$name.sha256")
            // Repair assets are debug-only for some ABIs (notably x86_64): a
            // missing asset is its own diagnosis, not a generic init failure.
            val bytes = try {
                context.assets.open("media/native-overrides/$abi/$name").use { it.readBytes() }
            } catch (e: FileNotFoundException) {
                unavailable()
            }
            if (!(bytes.size in 1..2 * 1024 * 1024 &&
                Hashing.sha256(ByteArrayInputStream(bytes)) == checksum)) invalid()
            try {
                MediaNativeLibraries.require16KiB(bytes, abi)
            } catch (e: IllegalArgumentException) {
                invalid()
            }
            val installed = File(directory, name)
            if (Files.isSymbolicLink(installed.toPath())) invalid()
            val same = installed.isFile && installed.inputStream().use { Hashing.sha256(it) } == checksum
            if (same) continue
            val sink = AtomicFile(installed)
            val output = sink.startWrite()
            try { output.write(bytes); sink.finishWrite(output) }
            catch (error: Exception) { sink.failWrite(output); throw error }
        }
    }
}
