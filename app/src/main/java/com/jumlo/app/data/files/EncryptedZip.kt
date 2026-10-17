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
import java.nio.file.Files
import java.util.Locale
import java.util.UUID

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
 * AES-256 ZIP handling. Both directions write in private sibling staging paths.
 * An existing destination is NEVER removed or overwritten, even after an invalid
 * password, malicious entry, cancellation, or failed publish.
 *
 * Callers must use dedicated, app-owned output paths and supply a session-only
 * password. Extraction is intentionally unavailable in the public capability
 * registry until device, corruption, and password round trips are qualified.
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
        require(!outZip.exists()) { "OUTPUT_ALREADY_EXISTS" }
        val parent = outZip.absoluteFile.parentFile
        require(parent.mkdirs() || parent.isDirectory) { "OUTPUT_CREATE_FAILED" }
        val stagedInputs = Files.createTempDirectory(parent.toPath(), ".ezip-input-").toFile()
        val stagedZip = File(parent, ".ezip-output-${UUID.randomUUID()}.zip")
        try {
            val zip = ZipFile(stagedZip, password)
            val usedNames = mutableSetOf<String>()
            var copiedBytes = 0L
            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            for ((index, src) in sources.withIndex()) {
                checkCancel()
                require(src.file.isFile) { "SOURCE_NOT_FOUND" }
                val desired = SafeNames.fromDisplayName(src.displayName, "file-${index + 1}.bin")
                val unique = SafeNames.unique(usedNames, desired)
                usedNames += unique.lowercase(Locale.ROOT)
                val staged = File(stagedInputs, unique)
                src.file.inputStream().use { input ->
                    FileOutputStream(staged).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            checkCancel()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(count.toLong() <= limits.maxBytes - copiedBytes) { "ARCHIVE_SIZE_LIMIT" }
                            if (count > 0) {
                                output.write(buffer, 0, count)
                                copiedBytes += count
                            }
                        }
                        output.fd.sync()
                    }
                }
                checkCancel()
                zip.addFile(staged, params)
                require(staged.delete()) { "TEMP_CLEANUP_FAILED" }
            }
            // zip4j must have produced the complete, encrypted central directory.
            require(stagedZip.isFile && stagedZip.length() > 0) { "ARCHIVE_VERIFICATION_FAILED" }
            val headers = ZipFile(stagedZip, password).fileHeaders
            require(headers.size == sources.size && headers.all {
                !it.isDirectory && it.isEncrypted && it.encryptionMethod == EncryptionMethod.AES &&
                    it.aesExtraDataRecord?.aesKeyStrength == AesKeyStrength.KEY_STRENGTH_256
            }) { "ARCHIVE_VERIFICATION_FAILED" }
            checkCancel()
            require(!outZip.exists()) { "OUTPUT_ALREADY_EXISTS" }
            // A move WITHOUT REPLACE_EXISTING refuses a destination created while we staged.
            // ATOMIC_MOVE alone can replace a destination on some providers, despite a precheck.
            // Both paths are siblings; native filesystems generally implement this as rename.
            Files.move(stagedZip.toPath(), outZip.toPath())
        } finally {
            stagedInputs.deleteRecursively()
            stagedZip.delete()
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
        require(sourceZip.isFile && !targetDir.exists()) { "INVALID_ARCHIVE_REQUEST" }
        val parent = targetDir.absoluteFile.parentFile
        require(parent.mkdirs() || parent.isDirectory) { "OUTPUT_CREATE_FAILED" }
        // Stage BEFORE inspecting names. On ANY failure only our temporary tree is removed.
        val stageDir = Files.createTempDirectory(parent.toPath(), ".ezip-extract-").toFile()
        try {
            val zip = ZipFile(sourceZip, password)
            val headers = zip.fileHeaders
            require(headers.isNotEmpty()) { "ARCHIVE_HAS_NO_FILES" }
            require(headers.size <= limits.maxEntries) { "ARCHIVE_ENTRY_LIMIT" }
            val planned = ArrayList<Pair<FileHeader, File>>(headers.size)
            val foldedPaths = HashSet<String>()
            val filePaths = HashSet<String>()
            var declaredBytes = 0L
            for (header in headers) {
                checkCancel()
                require(!header.isSymbolicLink) { "UNSAFE_ARCHIVE_PATH" }
                val name = header.fileName
                require(name.isNotBlank() && !name.startsWith("/") && !name.startsWith("\\") &&
                    '\\' !in name && ':' !in name && '\u0000' !in name) { "UNSAFE_ARCHIVE_PATH" }
                val dest = SafeNames.entry(stageDir, name, limits.maxDepth)
                // Validate path safety BEFORE checking encryption: an archive whose
                // name is malicious must be rejected for that reason, even when its
                // payload is plaintext. This ordering makes malicious-header tests
                // exercise the real ZIP-slip guard rather than a shortcut.
                require(header.isDirectory || (header.isEncrypted &&
                    header.encryptionMethod == EncryptionMethod.AES &&
                    header.aesExtraDataRecord?.aesKeyStrength == AesKeyStrength.KEY_STRENGTH_256)) {
                    "ARCHIVE_ENCRYPTION_REQUIRED"
                }
                val folded = dest.relativeTo(stageDir.canonicalFile).invariantSeparatorsPath.lowercase(Locale.ROOT)
                require(foldedPaths.add(folded)) { "DUPLICATE_ARCHIVE_ENTRY" }
                if (!header.isDirectory) {
                    val count = header.uncompressedSize
                    require(count >= 0 && count <= limits.maxBytes - declaredBytes) { "ARCHIVE_SIZE_LIMIT" }
                    declaredBytes += count
                    filePaths += folded
                } else {
                    require(header.uncompressedSize == 0L) { "ARCHIVE_DIRECTORY_PAYLOAD" }
                }
                planned += header to dest
            }
            // Reject file/directory prefix conflicts BEFORE any byte is extracted.
            for ((_, dest) in planned) {
                var ancestor = dest.parentFile
                while (ancestor != null && ancestor != stageDir.canonicalFile) {
                    val prefix = ancestor.relativeTo(stageDir.canonicalFile).invariantSeparatorsPath.lowercase(Locale.ROOT)
                    require(prefix !in filePaths) { "ARCHIVE_COLLISION" }
                    ancestor = ancestor.parentFile
                }
            }
            var actualBytes = 0L
            val buffer = ByteArray(64 * 1024)
            val relativeOutputs = ArrayList<String>()
            for ((header, dest) in planned) {
                checkCancel()
                if (header.isDirectory) {
                    require(dest.mkdirs() || dest.isDirectory) { "OUTPUT_CREATE_FAILED" }
                } else {
                    require(dest.parentFile.mkdirs() || dest.parentFile.isDirectory) { "OUTPUT_CREATE_FAILED" }
                    require(!dest.exists()) { "ARCHIVE_COLLISION" }
                    zip.getInputStream(header).use { input ->
                        FileOutputStream(dest).use { output ->
                            while (true) {
                                checkCancel()
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(count.toLong() <= limits.maxBytes - actualBytes) { "ARCHIVE_SIZE_LIMIT" }
                                if (count > 0) {
                                    output.write(buffer, 0, count)
                                    actualBytes += count
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    require(dest.length() == header.uncompressedSize) { "ARCHIVE_SIZE_MISMATCH" }
                    relativeOutputs += dest.relativeTo(stageDir.canonicalFile).path
                }
            }
            require(relativeOutputs.isNotEmpty()) { "ARCHIVE_HAS_NO_FILES" }
            checkCancel()
            require(!targetDir.exists()) { "OUTPUT_ALREADY_EXISTS" }
            // No REPLACE_EXISTING: do not overwrite a preexisting directory, symlink,
            // or a destination produced concurrently by another attempt.
            Files.move(stageDir.toPath(), targetDir.toPath())
            return relativeOutputs.map { File(targetDir, it) }
        } finally {
            stageDir.deleteRecursively()
        }
    }
}
