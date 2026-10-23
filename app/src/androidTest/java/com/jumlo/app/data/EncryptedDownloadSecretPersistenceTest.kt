package com.jumlo.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.source.EncryptedDownloadSecrets
import com.jumlo.app.data.source.EphemeralSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

/** Exercises the real Android Keystore and disk adapter, not the memory-only test constructor. */
@RunWith(AndroidJUnit4::class)
class EncryptedDownloadSecretPersistenceTest {
    @Test fun submittedUrlSurvivesStoreRecreationWhileDocumentPasswordStaysInMemory() = withFixture {
        val original = store()
        val url = "https://example.invalid/clip?signature=fixture-private-token-%D8%B3"
        val handle = download(original, url)
        val password = original.put("document-password-fixture".toCharArray()).also(handles::add)

        val bytes = record(handle).readBytes()
        assertFalse("The persisted record must not contain the submitted URL", contains(bytes, url.toByteArray()))
        assertFalse("Document passwords must not be persisted", record(password).exists())
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey("${app.packageName}.download-inputs.v1", null)
        assertNotNull(key)
        assertNull("The download encryption key must remain non-exportable", key.encoded)

        val recreated = store()
        assertSecret(recreated, handle, url)
        assertNull(recreated.copy(password))
        val copy = requireNotNull(recreated.copy(handle))
        copy.fill('\u0000')
        assertSecret(recreated, handle, url)
    }

    @Test fun tamperedRecordIsRejectedWithoutRemovingAnotherDownload() = withFixture {
        val original = store()
        val damaged = download(original, "https://example.invalid/damaged?token=first")
        val siblingUrl = "https://example.invalid/retained?token=second"
        val sibling = download(original, siblingUrl)
        val siblingBytes = record(sibling).readBytes()
        val bytes = record(damaged).readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        record(damaged).writeBytes(bytes)

        val recreated = store()
        assertNull("An authentication-tag failure must not return a URL", recreated.copy(damaged))
        assertFalse("The invalid record should be discarded", record(damaged).exists())
        assertArrayEquals(siblingBytes, record(sibling).readBytes())
        assertSecret(recreated, sibling, siblingUrl)
        assertSecret(store(), sibling, siblingUrl)
    }

    @Test fun removingOnePersistedDownloadLeavesItsSiblingReadableAfterRecreation() = withFixture {
        val original = store()
        val removed = download(original, "https://example.invalid/remove?token=one")
        val retainedUrl = "https://example.invalid/keep?token=two"
        val retained = download(original, retainedUrl)
        val retainedBytes = record(retained).readBytes()

        original.remove(removed)

        val recreated = store()
        assertNull(recreated.copy(removed))
        assertFalse(record(removed).exists())
        assertArrayEquals(retainedBytes, record(retained).readBytes())
        assertSecret(recreated, retained, retainedUrl)
    }

    private fun withFixture(block: suspend Fixture.() -> Unit): Unit = runBlocking(Dispatchers.IO) {
        val fixture = Fixture(ApplicationProvider.getApplicationContext())
        try { fixture.block() } finally { fixture.close() }
    }

    private class Fixture(val app: Context) {
        val handles = mutableSetOf<String>()
        private val stores = mutableListOf<EphemeralSecretStore>()
        fun store() = EphemeralSecretStore(EncryptedDownloadSecrets(app)).also(stores::add)
        suspend fun download(store: EphemeralSecretStore, value: String): String {
            val chars = value.toCharArray()
            return try { store.putDownload(chars).also(handles::add) } finally { chars.fill('\u0000') }
        }
        fun record(handle: String) = File(app.noBackupFilesDir, "download-inputs/$handle.bin")
        fun close() {
            // Never call clear() or delete the shared Keystore alias: another job may own a secret.
            stores.forEach { store -> handles.forEach(store::remove) }
        }
    }

    private fun assertSecret(store: EphemeralSecretStore, handle: String, expected: String) {
        val actual = store.copy(handle)
        try { assertArrayEquals(expected.toCharArray(), actual) } finally { actual?.fill('\u0000') }
    }

    private fun contains(bytes: ByteArray, value: ByteArray): Boolean =
        value.size <= bytes.size && (0..bytes.size - value.size).any { offset ->
            value.indices.all { index -> bytes[offset + index] == value[index] }
        }
}
