package com.jumlo.app.data.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A dedicated, non-exportable Android Keystore key protects optional-backend credentials. */
internal class KeystoreMediaServiceTokenCipher(private val alias: String) : MediaServiceTokenCipher {
    @Synchronized private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    override fun encrypt(token: String): String {
        val plain = MediaServiceSettingsStore.normalizeToken(token).toByteArray(StandardCharsets.UTF_8)
        require(plain.isNotEmpty()) { "INVALID_MEDIA_SERVICE_TOKEN" }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key(create = true))
                updateAAD(AAD)
            }
            val encrypted = cipher.doFinal(plain)
            check(cipher.iv.size == 12) { "MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE" }
            return "v1:${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } finally { plain.fill(0) }
    }

    override fun decrypt(record: String): String {
        require(record.length <= 2048) { "MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE" }
        val fields = record.split(':')
        require(fields.size == 3 && fields[0] == "v1") { "MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE" }
        val iv = Base64.decode(fields[1], Base64.NO_WRAP)
        val encrypted = Base64.decode(fields[2], Base64.NO_WRAP)
        require(iv.size == 12 && encrypted.size in 17..316) { "MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, iv))
            updateAAD(AAD)
        }
        val plain = cipher.doFinal(encrypted)
        return try { MediaServiceSettingsStore.normalizeToken(String(plain, StandardCharsets.UTF_8)) }
        finally { plain.fill(0) }
    }

    private companion object {
        val AAD = "com.jumlo.app.media-service-token.v1".toByteArray(StandardCharsets.US_ASCII)
    }
}
