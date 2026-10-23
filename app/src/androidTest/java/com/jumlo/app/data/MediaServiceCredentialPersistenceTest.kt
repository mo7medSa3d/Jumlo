package com.jumlo.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.preferences.KeystoreMediaServiceTokenCipher
import com.jumlo.app.data.preferences.MediaServiceSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Uses the real Android Keystore, real Preferences DataStore and app-private disk. */
@RunWith(AndroidJUnit4::class)
class MediaServiceCredentialPersistenceTest {
    @Test fun legacyPlaintextMigratesToRandomizedCiphertextAndSurvivesAdapterRecreation() = withFixture {
        val token = "fixture-service-token-123456"
        preferences.edit {
            it[MediaServiceSettingsStore.URL] = "https://media.example.invalid"
            it[MediaServiceSettingsStore.LEGACY_TOKEN] = token
        }
        assertEquals(token, adapter().refresh().token)
        val saved = preferences.data.first()
        assertNull(saved[MediaServiceSettingsStore.LEGACY_TOKEN])
        val encrypted = requireNotNull(saved[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
        assertFalse(encrypted.contains(token))
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(token))
        assertNotEquals(cipher().encrypt(token), cipher().encrypt(token))
        val key = keyStore().getKey(alias, null)
        assertNotNull(key)
        assertNull("The backend credential key must remain non-exportable", key.encoded)
        assertEquals(token, adapter().refresh().token)
    }

    @Test fun tamperingClearsCiphertextAndLegacyFallbackAndAllowsExplicitReentry() = withFixture {
        val original = adapter()
        original.setToken("original-fixture-token")
        val fields = requireNotNull(preferences.data.first()[MediaServiceSettingsStore.ENCRYPTED_TOKEN]).split(':')
        val damaged = Base64.decode(fields[2], Base64.NO_WRAP)
        damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
        preferences.edit {
            it[MediaServiceSettingsStore.ENCRYPTED_TOKEN] = "${fields[0]}:${fields[1]}:${Base64.encodeToString(damaged, Base64.NO_WRAP)}"
            it[MediaServiceSettingsStore.LEGACY_TOKEN] = "stale-legacy-token"
        }
        val recreated = adapter()
        assertEquals("", recreated.refresh().token)
        val cleared = preferences.data.first()
        assertNull(cleared[MediaServiceSettingsStore.LEGACY_TOKEN])
        assertNull(cleared[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
        assertTrue(events.contains("media_token_unreadable"))
        recreated.setToken("replacement-fixture-token")
        assertEquals("replacement-fixture-token", adapter().refresh().token)
    }

    @Test fun missingKeyDiscardsTheUnreadableCredentialWithoutSilentlyRegeneratingForDecryption() = withFixture {
        adapter().setToken("before-key-loss")
        keyStore().deleteEntry(alias)
        assertEquals("", adapter().refresh().token)
        assertNull(preferences.data.first()[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
        assertFalse(keyStore().containsAlias(alias))
        adapter().setToken("after-explicit-reentry")
        assertEquals("after-explicit-reentry", adapter().refresh().token)
    }

    private fun withFixture(block: suspend Fixture.() -> Unit): Unit = runBlocking(Dispatchers.IO) {
        val fixture = Fixture(ApplicationProvider.getApplicationContext())
        try { fixture.block() } finally { fixture.close() }
    }

    private class Fixture(app: Context) {
        private val id = UUID.randomUUID().toString()
        val alias = "${app.packageName}.media-token-test.$id"
        private val directory = File(app.noBackupFilesDir, "media-token-tests/$id").apply { mkdirs() }
        val file = File(directory, "settings.preferences_pb")
        private val job = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val events = mutableListOf<String>()
        fun cipher() = KeystoreMediaServiceTokenCipher(alias)
        fun adapter() = MediaServiceSettingsStore(preferences, cipher(), allowDebugLoopback = false) { area, _ -> events += area }
        fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        suspend fun close() {
            job.cancelAndJoin()
            keyStore().deleteEntry(alias)
            directory.deleteRecursively()
        }
    }
}
