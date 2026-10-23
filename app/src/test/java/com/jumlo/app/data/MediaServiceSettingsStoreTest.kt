package com.jumlo.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.preferences.MediaServiceSettingsStore
import com.jumlo.app.data.preferences.MediaServiceTokenCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Tests persistence failure/migration policy; Keystore itself is exercised by instrumentation. */
class MediaServiceSettingsStoreTest {
    @Test fun credentialsStayWithTheirOriginAndAreClearedWhenItChanges() = runBlocking {
        val prefs = MemoryPreferences()
        val store = store(prefs)
        store.setEndpoint("https://media.example.com/v1")
        store.setToken("origin-bound-token")
        store.setEndpoint("https://MEDIA.EXAMPLE.COM:443/v2")
        assertEquals("origin-bound-token", store.state.value.token)
        store.setEndpoint("https://different.example.com/v2")
        assertEquals("", store.state.value.token)
        assertNull(prefs.value[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
        assertNull(prefs.value[MediaServiceSettingsStore.LEGACY_TOKEN])
        store.setToken("new-origin-token")
        store.setEndpoint("https://different.example.com:8443/v2")
        assertEquals("", store.state.value.token)
    }

    @Test fun legacyTokenIsRemovedBeforeItBecomesAvailableAndOtherPreferencesRemain() = runBlocking {
        val prefs = MemoryPreferences()
        val theme = stringPreferencesKey("theme_mode")
        prefs.edit {
            it[MediaServiceSettingsStore.URL] = "https://media.example.com/"
            it[MediaServiceSettingsStore.LEGACY_TOKEN] = "old-token"
            it[theme] = "DARK"
        }
        val cipher = FixtureCipher()
        val store = store(prefs, cipher)
        assertEquals("", store.state.value.token)
        val result = store.refresh()
        assertEquals("old-token", result.token)
        assertEquals("https://media.example.com", result.url)
        assertNull(prefs.value[MediaServiceSettingsStore.LEGACY_TOKEN])
        assertNotNull(prefs.value[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
        assertEquals("DARK", prefs.value[theme])
        assertFalse(result.toString().contains("old-token"))
        assertFalse(result.toString().contains("media.example.com"))
        assertEquals("old-token", store(prefs, cipher).refresh().token)
    }

    @Test fun unreadableCiphertextNeverFallsBackToPlaintextAndIsCleared() = runBlocking {
        val prefs = MemoryPreferences()
        val errors = mutableListOf<String>()
        prefs.edit {
            it[MediaServiceSettingsStore.ENCRYPTED_TOKEN] = "damaged"
            it[MediaServiceSettingsStore.LEGACY_TOKEN] = "stale-plaintext"
        }
        val store = store(prefs, errors = errors)
        assertEquals("", store.refresh().token)
        assertNull(prefs.value[MediaServiceSettingsStore.LEGACY_TOKEN])
        assertNull(prefs.value[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
        assertEquals(listOf("media_token_unreadable"), errors)
    }

    @Test fun unavailableEncryptionClearsTheLegacyTokenWithoutPlaintextFallback() = runBlocking {
        val prefs = MemoryPreferences()
        prefs.edit { it[MediaServiceSettingsStore.LEGACY_TOKEN] = "legacy" }
        val cipher = FixtureCipher().apply { failEncryption = true }
        val store = store(prefs, cipher)
        assertEquals("", store.refresh().token)
        assertNull(prefs.value[MediaServiceSettingsStore.LEGACY_TOKEN])
        assertNull(prefs.value[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
        val failure = runCatching { store.setToken("replacement") }.exceptionOrNull()
        assertTrue(failure is OperationFailure)
        assertEquals("MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE", failure?.message)
        assertEquals("", store.state.value.token)
    }

    @Test fun aFailedPersistenceCommitCannotExposeAnUncommittedToken() = runBlocking {
        val prefs = MemoryPreferences()
        val store = store(prefs)
        store.setEndpoint("https://media.example.com")
        store.setToken("first")
        prefs.failWrites = true
        val failure = runCatching { store.setToken("second") }.exceptionOrNull()
        assertEquals("MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE", failure?.message)
        assertEquals("", store.state.value.url)
        assertEquals("", store.state.value.token)
        assertNull(prefs.value[MediaServiceSettingsStore.LEGACY_TOKEN])
    }

    @Test fun clearingATokenRemovesBothPersistedRepresentations() = runBlocking {
        val prefs = MemoryPreferences()
        val store = store(prefs)
        store.setToken("first")
        store.setToken("  ")
        assertEquals("", store.state.value.token)
        assertNull(prefs.value[MediaServiceSettingsStore.LEGACY_TOKEN])
        assertNull(prefs.value[MediaServiceSettingsStore.ENCRYPTED_TOKEN])
    }

    @Test fun invalidSavedEndpointIsDisabledAndInvalidNewEndpointDoesNotReplaceAValidOne() = runBlocking {
        val prefs = MemoryPreferences()
        prefs.edit { it[MediaServiceSettingsStore.URL] = "http://untrusted.example.com?token=private" }
        val store = store(prefs)
        assertEquals("", store.refresh().url)
        assertNull(prefs.value[MediaServiceSettingsStore.URL])
        store.setEndpoint("https://media.example.com")
        val failure = runCatching { store.setEndpoint("http://untrusted.example.com") }.exceptionOrNull()
        assertEquals("INVALID_MEDIA_SERVICE_URL", failure?.message)
        assertEquals("https://media.example.com", store.state.value.url)
    }

    @Test fun snapshotReadFailureClearsBothValuesAndReportsOnlyItsScope() = runBlocking {
        val prefs = MemoryPreferences()
        val errors = mutableListOf<String>()
        val store = store(prefs, errors = errors)
        store.setEndpoint("https://media.example.com")
        store.setToken("first")
        prefs.readFailure = IOException("fixture read failure")
        store.observe()
        assertEquals("", store.state.value.url)
        assertEquals("", store.state.value.token)
        assertEquals(listOf("media_settings_snapshot"), errors)
    }

    @Test fun cancellationIsNeverReportedAsASettingsFailure() = runBlocking {
        val prefs = MemoryPreferences()
        val errors = mutableListOf<String>()
        prefs.readFailure = CancellationException("fixture cancelled")
        val failure = runCatching { store(prefs, errors = errors).observe() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(errors.isEmpty())
    }

    @Test fun invalidAuthorizationHeaderValuesAreRejectedBeforePersistence() = runBlocking {
        val prefs = MemoryPreferences()
        val store = store(prefs)
        for (token in listOf("one\ntwo", "one two", "one\u0000two", "a".repeat(301))) {
            val failure = runCatching { store.setToken(token) }.exceptionOrNull()
            assertEquals("INVALID_MEDIA_SERVICE_TOKEN", failure?.message)
        }
        assertTrue(prefs.value.asMap().isEmpty())
    }

    private fun store(prefs: MemoryPreferences, cipher: FixtureCipher = FixtureCipher(), errors: MutableList<String> = mutableListOf()) =
        MediaServiceSettingsStore(prefs, cipher, allowDebugLoopback = false) { scope, _ -> errors += scope }

    private class FixtureCipher : MediaServiceTokenCipher {
        private val records = mutableMapOf<String, String>()
        var failEncryption = false
        override fun encrypt(token: String): String {
            if (failEncryption) throw IOException("fixture unavailable key")
            return "opaque-${records.size}".also { records[it] = token }
        }
        override fun decrypt(record: String): String = records[record] ?: throw IOException("fixture authentication failure")
    }

    private class MemoryPreferences : DataStore<Preferences> {
        private val mutex = Mutex()
        private val values = MutableStateFlow(emptyPreferences())
        val value: Preferences get() = values.value
        var failWrites = false
        var readFailure: Exception? = null
        override val data: Flow<Preferences> get() = readFailure?.let { error -> flow { throw error } } ?: values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            val next = transform(values.value)
            if (failWrites) throw IOException("fixture failed commit")
            next.also { values.value = it }
        }
    }
}
