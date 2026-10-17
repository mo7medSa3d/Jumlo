package com.jumlo.app.data.files

import com.jumlo.app.core.ArchiveLimits
import com.jumlo.app.core.SafeNames
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class EncryptedZipEntrySource(val displayName: String, val file: File)

private val FileHeader.isSymbolicLink: Boolean
    get() {
        val attrs = externalFileAttributes ?: return false
        if (attrs.size >= 4) {
            val unixMode = ((attrs[3].toInt() and 0xFF) shl 8) or (attrs[2].toInt() and 0xFF)
            return (unixMode and 0xF000) == 0xA000
        }
        return false
    }

/**
 * AES-256 encrypted ZIP creation and extraction via zip4j (Apache-2.0).
 * Extraction enforces strict pre-extraction and streaming guards against ZIP-slip
 * (rejecting absolute paths, "..", backslashes, drive colons, symlinks, and any
 * canonical path escaping the target directory), entry-count limits, and streamed
 * total-byte limits.
 */
object EncryptedZip {

    fun create(
        outZip: File,
        sources: List<EncryptedZipEntrySource>,
        password: CharArray,
        limits: ArchiveLimits = ArchiveLimits(2L * 1024 * 1024 * 1024, 10000, 32),
        checkCancel: () -> Unit = {}
    ) {
        require(password.isNotEmpty()) { "ZIP_PASSWORD_REQUIRED" }
        require(sources.isNotEmpty() && sources.size <= limits.maxEntries) { "ARCHIVE_ENTRY_LIMIT" }
        outZip.parentFile?.mkdirs()
        if (outZip.exists()) outZip.delete()
        val stagingDir = File(outZip.parentFile, ".ezip-stage-${System.nanoTime()}").apply { mkdirs() }
        try {
            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            val zip = ZipFile(outZip, password)
            val usedNames = mutableSetOf<String>()
            var totalBytes = 0L
            for ((index, src) in sources.withIndex()) {
                checkCancel()
                require(src.file.isFile) { "SOURCE_NOT_FOUND" }
                totalBytes += src.file.length()
                require(totalBytes <= limits.maxBytes) { "ARCHIVE_SIZE_LIMIT" }
                val desired = SafeNames.fromDisplayName(src.displayName, "file-${index + 1}.bin")
                val unique = SafeNames.unique(usedNames, desired).also { usedNames += it.lowercase(Locale.ROOT) }
                val staged = File(stagingDir, unique)
                src.file.copyTo(staged, overwrite = true)
                zip.addFile(staged, params)
                staged.delete()
            }
        } catch (t: Throwable) {
            outZip.delete()
            throw t
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    fun extract(
        sourceZip: File,
        targetDir: File,
        password: CharArray,
        limits: ArchiveLimits = ArchiveLimits(2L * 1024 * 1024 * 1024, 10000, 32),
        checkCancel: () -> Unit = {}
    ): List<File> {
        require(password.isNotEmpty()) { "ZIP_PASSWORD_REQUIRED" }
        require(sourceZip.isFile) { "INVALID_ARCHIVE_REQUEST" }
        if (targetDir.exists()) targetDir.deleteRecursively()
        require(targetDir.mkdirs()) { "OUTPUT_CREATE_FAILED" }
        val canonicalDir = targetDir.canonicalFile
        val extracted = mutableListOf<File>()
        try {
            val zip = ZipFile(sourceZip, password)
            val headers = zip.fileHeaders
            require(headers.isNotEmpty()) { "ARCHIVE_HAS_NO_FILES" }
            require(headers.size <= limits.maxEntries) { "ARCHIVE_ENTRY_LIMIT" }

            var declaredBytes = 0L
            val foldedNames = HashSet<String>()
            for (header in headers) {
                checkCancel()
                require(!header.isSymbolicLink) { "UNSAFE_ARCHIVE_PATH" }
                val name = header.fileName
                require(
                    name.isNotBlank() &&
                        !name.startsWith("/") &&
                        !name.startsWith("\\") &&
                        '\\' !in name &&
                        ':' !in name &&
                        '\u0000' !in name
                ) { "UNSAFE_ARCHIVE_PATH" }
                val segments = name.removeSuffix("/").split('/')
                require(segments.isNotEmpty() && segments.size <= limits.maxDepth) { "UNSAFE_ARCHIVE_PATH" }
                require(segments.none { it.isEmpty() || it == "." || it == ".." }) { "UNSAFE_ARCHIVE_PATH" }
                val dest = SafeNames.entry(canonicalDir, name, limits.maxDepth)
                require(dest.path.startsWith(canonicalDir.path + File.separator)) { "UNSAFE_ARCHIVE_PATH" }
                val normalizedKey = segments.joinToString("/").lowercase(Locale.ROOT)
                require(foldedNames.add(normalizedKey)) { "DUPLICATE_ARCHIVE_ENTRY" }
                if (!header.isDirectory) {
                    val uncompressed = header.uncompressedSize
                    require(uncompressed >= 0L && uncompressed <= limits.maxBytes - declaredBytes) { "ARCHIVE_SIZE_LIMIT" }
                    declaredBytes += uncompressed
                }
            }

            var actualBytes = 0L
            val buffer = ByteArray(64 * 1024)
            for (header in headers) {
                checkCancel()
                val dest = SafeNames.entry(canonicalDir, header.fileName, limits.maxDepth)
                if (header.isDirectory) {
                    require(dest.mkdirs() || dest.isDirectory) { "OUTPUT_CREATE_FAILED" }
                } else {
                    require(dest.parentFile.mkdirs() || dest.parentFile.isDirectory) { "OUTPUT_CREATE_FAILED" }
                    require(!dest.exists()) { "ARCHIVE_COLLISION" }
                    zip.getInputStream(header).use { input ->
                        FileOutputStream(dest).use { output ->
                            while (true) {
                                checkCancel()
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (n > 0) {
                                    require(n.toLong() <= limits.maxBytes - actualBytes) { "ARCHIVE_SIZE_LIMIT" }
                                    output.write(buffer, 0, n)
                                    actualBytes += n
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    extracted.add(dest)
                }
            }
            require(extracted.isNotEmpty()) { "ARCHIVE_HAS_NO_FILES" }
            return extracted
        } catch (t: Throwable) {
            targetDir.deleteRecursively()
            throw t
        }
    }
}
