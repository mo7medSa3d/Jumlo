package com.jumlo.app.data.source

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** AES-GCM with a non-exportable Keystore key; neither ciphertext nor key enters device backup. */
@Singleton class EncryptedDownloadSecrets @Inject constructor(@ApplicationContext context: Context) : DownloadSecretPersistence {
    private val directory = File(context.noBackupFilesDir, "download-inputs")
    private val alias = "${context.packageName}.download-inputs.v1"
    private fun file(handle: String): File {
        require(runCatching { UUID.fromString(handle).toString() == handle }.getOrDefault(false)) { "INVALID_SECRET_HANDLE" }
        return File(directory, "$handle.bin")
    }
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized override fun save(handle: String, secret: CharArray) {
        val target = file(handle)
        require(secret.isNotEmpty() && secret.size <= MAX_BYTES / 4) { "INVALID_DOWNLOAD_LIST" }
        check(directory.isDirectory || directory.mkdirs()) { "SECRET_STORAGE_FAILED" }
        val encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret))
        val plain = ByteArray(encoded.remaining()).also(encoded::get)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            cipher.updateAAD(handle.toByteArray(StandardCharsets.US_ASCII))
            val encrypted = cipher.doFinal(plain)
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try {
                stream.write(VERSION)
                stream.write(cipher.iv.size)
                stream.write(cipher.iv)
                stream.write(encrypted)
                atomic.finishWrite(stream)
            } catch (error: Exception) { atomic.failWrite(stream); throw error }
        } finally {
            plain.fill(0)
            if (encoded.hasArray()) encoded.array().fill(0)
        }
    }
    @Synchronized override fun load(handle: String): CharArray? {
        val target = runCatching { file(handle) }.getOrNull() ?: return null
        if (!target.exists() && !File(target.path + ".bak").exists()) return null
        return try {
            require(target.length() <= MAX_BYTES + 64) { "SECRET_STORAGE_INVALID" }
            val bytes = AtomicFile(target).openRead().use { input ->
                java.io.ByteArrayOutputStream().use { output ->
                    val buffer=ByteArray(8192)
                    while(true) {
                        val count=input.read(buffer)
                        if(count<0) break
                        require(output.size()+count<=MAX_BYTES+64) { "SECRET_STORAGE_INVALID" }
                        output.write(buffer,0,count)
                    }
                    output.toByteArray()
                }
            }
            require(bytes.size in 30..MAX_BYTES + 64 && bytes[0].toInt() == VERSION && bytes[1].toInt() == 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
                updateAAD(handle.toByteArray(StandardCharsets.US_ASCII))
            }
            val plain = cipher.doFinal(bytes, 14, bytes.size - 14)
            try {
                val decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain))
                CharArray(decoded.remaining()).also { result -> decoded.get(result); if (decoded.hasArray()) decoded.array().fill('\u0000') }
            } finally { plain.fill(0) }
        } catch (_: Exception) {
            // Lost/replaced Keystore keys and damaged ciphertext require the user to re-enter the URL.
            AtomicFile(target).delete()
            null
        }
    }
    @Synchronized override fun remove(handle: String) { runCatching { AtomicFile(file(handle)).delete() } }
    @Synchronized override fun clear() { directory.listFiles()?.forEach { if (it.isFile) it.delete() } }
    private companion object { const val VERSION = 1; const val MAX_BYTES = 1024 * 1024 }
}
