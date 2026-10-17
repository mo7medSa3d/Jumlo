package com.jumlo.app.core

import java.io.*
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.*

object Hashing {
    fun sha256(file: File): String = file.inputStream().use { sha256(it) }
    fun sha256(input: InputStream, checkCancel: () -> Unit = {}): String {
        val digest=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(64*1024)
        while(true) { checkCancel(); val n=input.read(buffer); if(n<0) break; if(n>0) digest.update(buffer,0,n) }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun matches(file: File, digest: String): Boolean {
        val expected=digest.trim().lowercase(Locale.ROOT)
        require(Regex("[0-9a-f]{64}").matches(expected)) { "INVALID_SHA256" }
        return MessageDigest.isEqual(sha256(file).toByteArray(Charsets.US_ASCII),expected.toByteArray(Charsets.US_ASCII))
    }
}

data class ArchiveInput(val name: String, val open: () -> InputStream)
data class ArchiveLimits(val maxBytes: Long, val maxEntries: Int, val maxDepth: Int) {
    init { require(maxBytes>0 && maxEntries>0 && maxDepth in 1..128) }
}

/** Pure byte engine shared by Android handlers and executable host tests. */
object ArchiveCore {
    fun create(out: File, inputs: List<ArchiveInput>, level: Int, limits: ArchiveLimits, checkCancel: () -> Unit = {}) {
        require(!out.exists() && inputs.isNotEmpty() && inputs.size<=limits.maxEntries && level in 0..9) { "INVALID_ARCHIVE_REQUEST" }
        val names=HashSet<String>()
        inputs.forEach { input ->
            SafeNames.entry(out.parentFile,input.name,limits.maxDepth)
            require(names.add(input.name.lowercase(Locale.ROOT))) { "DUPLICATE_ARCHIVE_ENTRY" }
        }
        out.parentFile.mkdirs()
        var total=0L
        try {
            FileOutputStream(out).use { file ->
                ZipOutputStream(BufferedOutputStream(file)).use { zip ->
                    zip.setLevel(level)
                    inputs.forEach { input ->
                        checkCancel(); zip.putNextEntry(ZipEntry(input.name))
                        input.open().use { source ->
                            val buf=ByteArray(64*1024)
                            while(true) {
                                checkCancel(); val n=source.read(buf); if(n<0) break
                                require(n.toLong() <= limits.maxBytes-total) { "ARCHIVE_BYTE_BUDGET_EXCEEDED" }
                                if(n>0) { zip.write(buf,0,n); total+=n }
                            }
                        }
                        zip.closeEntry()
                    }
                    zip.finish(); zip.flush(); file.fd.sync()
                }
            }
            // Re-open the completed central directory; never publish an unfinished ZIP.
            ZipFile(out).use { require(it.size()==inputs.size) { "ARCHIVE_VERIFICATION_FAILED" } }
        } catch(t: Throwable) { out.delete(); throw t }
    }

    fun extract(input: File, out: File, limits: ArchiveLimits, checkCancel: () -> Unit = {}): List<File> {
        require(input.isFile && !out.exists()) { "INVALID_ARCHIVE_REQUEST" }
        // Validate the central directory, including paths, before creating anything.
        val planned=linkedSetOf<String>(); val folded=HashSet<String>()
        ZipFile(input).use { zip ->
            require(zip.size()<=limits.maxEntries) { "ARCHIVE_ENTRY_BUDGET_EXCEEDED" }
            val entries=zip.entries()
            while(entries.hasMoreElements()) {
                checkCancel(); val e=entries.nextElement()
                SafeNames.entry(out,e.name,limits.maxDepth)
                require(!e.isDirectory || e.size==0L) { "ARCHIVE_DIRECTORY_PAYLOAD" }
                val key=e.name.removeSuffix("/")
                require(folded.add(key.lowercase(Locale.ROOT))) { "DUPLICATE_ARCHIVE_ENTRY" }
                require(e.size<=limits.maxBytes) { "ARCHIVE_BYTE_BUDGET_EXCEEDED" }
                planned.add(e.name)
            }
        }
        val files=mutableListOf<File>(); var total=0L; var count=0
        try {
            require(out.mkdirs()) { "OUTPUT_CREATE_FAILED" }
            ZipInputStream(BufferedInputStream(input.inputStream())).use { zip ->
                while(true) {
                    checkCancel(); val e=zip.nextEntry ?: break
                    count++; require(count<=limits.maxEntries && planned.remove(e.name)) { "ARCHIVE_DIRECTORY_MISMATCH" }
                    val target=SafeNames.entry(out,e.name,limits.maxDepth)
                    if(e.isDirectory) {
                        require(zip.read()==-1) { "ARCHIVE_DIRECTORY_PAYLOAD" }
                        require(target.mkdirs() || target.isDirectory) { "OUTPUT_CREATE_FAILED" }
                    } else {
                        require(target.parentFile.mkdirs() || target.parentFile.isDirectory) { "OUTPUT_CREATE_FAILED" }
                        require(!target.exists()) { "ARCHIVE_COLLISION" }
                        FileOutputStream(target).use { sink ->
                            val buf=ByteArray(64*1024)
                            while(true) {
                                checkCancel(); val n=zip.read(buf); if(n<0) break
                                require(n.toLong()<=limits.maxBytes-total) { "ARCHIVE_BYTE_BUDGET_EXCEEDED" }
                                if(n>0) { sink.write(buf,0,n); total+=n }
                            }
                            sink.fd.sync()
                        }
                        files.add(target)
                    }
                    // ZipInputStream verifies CRC and uncompressed length while reading/closing.
                    zip.closeEntry()
                }
            }
            require(planned.isEmpty()) { "ARCHIVE_DIRECTORY_MISMATCH" }
            return files
        } catch(t: Throwable) { out.deleteRecursively(); throw t }
    }
}
