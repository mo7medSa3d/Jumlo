package com.jumlo.app.core.media

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** ABI comes from the executable that will run, including a 32-bit app on a 64-bit device. */
object MediaNativeLibraries {
    val repairedNames = listOf("libsharpyuv.so", "libwebp.so", "libwebpdecoder.so", "libwebpdemux.so", "libwebpmux.so")

    /**
     * Old extracted archives have an unused liblzma.so development alias that
     * shadows Android's incompatible liblzma.so API through LD_LIBRARY_PATH.
     * Private Python/FFmpeg consumers use liblzma.so.5, which stays untouched.
     * Run after both upstream initializers: their length-based cache and ignored
     * recursive-delete result alone do not guarantee removal after an APK update.
     */
    fun removePlatformLzmaAliases(packages: File) {
        val root = packages.canonicalFile.toPath()
        for (runtime in listOf("python", "ffmpeg")) {
            var directory = root
            for (part in listOf(runtime, "usr", "lib")) {
                directory = directory.resolve(part)
                require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "MEDIA_RUNTIME_INITIALIZATION_FAILED" }
            }
            val alias = directory.resolve("liblzma.so")
            if (Files.exists(alias, NOFOLLOW_LINKS)) {
                require(Files.isSymbolicLink(alias)) { "MEDIA_RUNTIME_INITIALIZATION_FAILED" }
                // Delete only the link, including a dangling link; never its target.
                Files.delete(alias)
            }
            check(!Files.exists(alias, NOFOLLOW_LINKS)) { "MEDIA_RUNTIME_INITIALIZATION_FAILED" }
        }
    }

    fun processAbi(header: ByteArray): String? {
        require(header.size >= 20 && header[0] == 0x7f.toByte() &&
            String(header, 1, 3, Charsets.US_ASCII) == "ELF" && header[5] == 1.toByte()) { "MEDIA_NATIVE_ELF_INVALID" }
        val machine = (header[18].toInt() and 255) or ((header[19].toInt() and 255) shl 8)
        return when (header[4].toInt() to machine) {
            2 to 183 -> "arm64-v8a"
            2 to 62 -> "x86_64"
            1 to 40, 1 to 3 -> null // The upstream 32-bit libraries remain in use.
            else -> throw IllegalArgumentException("MEDIA_NATIVE_ABI_UNAVAILABLE")
        }
    }

    /** Check real segment layout; changing only ELF p_align cannot satisfy this policy. */
    fun require16KiB(bytes: ByteArray, expectedAbi: String) {
        require(bytes.size >= 64 && processAbi(bytes) == expectedAbi &&
            expectedAbi in setOf("arm64-v8a", "x86_64")) { "MEDIA_NATIVE_ELF_INVALID" }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset = data.getLong(32)
        val entrySize = data.getShort(54).toInt() and 65535
        val count = data.getShort(56).toInt() and 65535
        require(offset >= 64 && entrySize in 56..128 && count in 1..128 &&
            offset <= bytes.size.toLong() - entrySize.toLong() * count) { "MEDIA_NATIVE_ELF_INVALID" }
        data class Segment(val type: Int, val flags: Int, val offset: Long, val address: Long,
            val fileBytes: Long, val memoryBytes: Long, val alignment: Long)
        val segments = (0 until count).map { index ->
            val at = (offset + index * entrySize).toInt()
            Segment(data.getInt(at), data.getInt(at + 4), data.getLong(at + 8),
                data.getLong(at + 16), data.getLong(at + 32), data.getLong(at + 40), data.getLong(at + 48))
        }
        val loads = segments.filter { it.type == 1 }
        require(loads.isNotEmpty()) { "MEDIA_NATIVE_ELF_INVALID" }
        for (load in loads) {
            require(load.offset >= 0 && load.address >= 0 && load.fileBytes >= 0 &&
                load.memoryBytes >= load.fileBytes && load.address <= Long.MAX_VALUE - load.memoryBytes &&
                load.offset <= bytes.size.toLong() - load.fileBytes) { "MEDIA_NATIVE_ELF_INVALID" }
            require(load.alignment >= 16384 && load.alignment and (load.alignment - 1) == 0L &&
                load.offset % load.alignment == load.address % load.alignment) { "MEDIA_NATIVE_ALIGNMENT" }
        }
        for (relro in segments.filter { it.type == 0x6474e552 }) {
            require(relro.address >= 0 && relro.memoryBytes >= 0 &&
                relro.address <= Long.MAX_VALUE - relro.memoryBytes - 16383) { "MEDIA_NATIVE_ELF_INVALID" }
            val end = relro.address + relro.memoryBytes
            val protectedEnd = (end + 16383) / 16384 * 16384
            // A partial final page is safe only when no writable LOAD data follows
            // RELRO within that same page (a separate RELRO-only LOAD is valid).
            require(loads.none { load ->
                load.flags and 2 != 0 && maxOf(load.address, end) < minOf(load.address + load.memoryBytes, protectedEnd)
            }) { "MEDIA_NATIVE_RELRO_ALIGNMENT" }
        }
    }
}
