package com.jumlo.app.data.pdf

import android.content.Context
import java.io.File
import java.util.UUID

data class SavedSignature(val id: String, val file: File, val createdAtMs: Long)

/** Private on-device PNG signature library capped at 12 signatures / 512 KB each. */
class SavedSignatureStore(context: Context) {
    private val dir = File(context.filesDir, "saved_signatures").apply { mkdirs() }

    companion object {
        const val MAX_SIGNATURES = 12
        const val MAX_BYTES = 512 * 1024
    }

    fun list(): List<SavedSignature> = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
        .orEmpty()
        .sortedByDescending { it.lastModified() }
        .map { SavedSignature(it.nameWithoutExtension, it, it.lastModified()) }

    fun save(pngBytes: ByteArray): SavedSignature {
        require(pngBytes.size in 8..MAX_BYTES) { "SIGNATURE_INVALID_SIZE" }
        dir.mkdirs()
        val existing = list()
        if (existing.size >= MAX_SIGNATURES) {
            existing.drop(MAX_SIGNATURES - 1).forEach { it.file.delete() }
        }
        val id = "sig_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val target = File(dir, "$id.png")
        target.writeBytes(pngBytes)
        return SavedSignature(id, target, target.lastModified())
    }

    fun delete(id: String) {
        val safeId = com.jumlo.app.core.SafeNames.basename(id)
        if (safeId != id || id.contains('/') || id.contains('\\') || id.contains("..")) return
        val base = dir.canonicalFile
        val target = File(base, "$safeId.png").canonicalFile
        if (target.parentFile == base) target.delete()
    }
}
