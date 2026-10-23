package com.jumlo.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jumlo.app.core.media.MediaServiceEndpoint
import com.jumlo.app.data.jobs.OperationFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface MediaServiceTokenCipher {
    fun encrypt(token: String): String
    fun decrypt(record: String): String
}

internal data class MediaServiceSettings(val url: String = "", val token: String = "") {
    override fun toString() = "MediaServiceSettings(configured=${url.isNotBlank()}, authenticated=${token.isNotBlank()})"
}

/** Durable ciphertext and atomic legacy migration; plaintext exists only in the in-memory state. */
internal class MediaServiceSettingsStore(
    private val preferences: DataStore<Preferences>,
    private val cipher: MediaServiceTokenCipher,
    private val allowDebugLoopback: Boolean,
    private val report: (String, Exception) -> Unit
) {
    private val mutations = Mutex()
    private val mutableState = MutableStateFlow(MediaServiceSettings())
    val state = mutableState.asStateFlow()

    suspend fun observe() {
        try {
            // Re-read transactionally: a queued, older flow emission cannot replace a newer save.
            preferences.data.collect { refresh() }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            mutableState.value = MediaServiceSettings()
            report("media_settings_snapshot", error)
        }
    }

    suspend fun refresh(): MediaServiceSettings = update()

    suspend fun setEndpoint(value: String) {
        val endpoint = MediaServiceEndpoint.normalize(value, allowDebugLoopback)
        try { update(endpoint = endpoint) }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { report("media_settings_save", error); throw error }
    }

    suspend fun setToken(value: String) {
        val token = normalizeToken(value)
        try { update(replacementToken = token) }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            report("media_token_save", error)
            throw OperationFailure("MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE")
        }
    }

    private suspend fun update(endpoint: String? = null, replacementToken: String? = null): MediaServiceSettings =
        withContext(Dispatchers.IO) {
            mutations.withLock {
                try {
                    var next = MediaServiceSettings()
                    var replacementFailed = false
                    preferences.edit { prefs ->
                        if (endpoint != null) {
                            val oldOrigin = try { MediaServiceEndpoint.origin(prefs[URL].orEmpty(), allowDebugLoopback) }
                                catch (_: IllegalArgumentException) { "" }
                            if (oldOrigin != MediaServiceEndpoint.origin(endpoint, allowDebugLoopback)) {
                                // A service change is not permission to send its predecessor's credential.
                                prefs.remove(LEGACY_TOKEN)
                                prefs.remove(ENCRYPTED_TOKEN)
                            }
                            if (endpoint.isEmpty()) prefs.remove(URL) else prefs[URL] = endpoint
                        }
                        if (replacementToken != null) {
                            prefs.remove(LEGACY_TOKEN)
                            prefs.remove(ENCRYPTED_TOKEN)
                            if (replacementToken.isNotEmpty()) {
                                try { prefs[ENCRYPTED_TOKEN] = cipher.encrypt(replacementToken) }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (error: Exception) {
                                    replacementFailed = true
                                    report("media_token_encrypt", error)
                                }
                            }
                        }
                        val url = try { MediaServiceEndpoint.normalize(prefs[URL].orEmpty(), allowDebugLoopback) }
                        catch (error: IllegalArgumentException) {
                            prefs.remove(URL)
                            report("media_endpoint_invalid", error)
                            ""
                        }
                        if (url.isEmpty()) prefs.remove(URL) else prefs[URL] = url
                        var token = ""
                        val encrypted = prefs[ENCRYPTED_TOKEN]
                        val legacy = prefs[LEGACY_TOKEN]
                        try {
                            when {
                                encrypted != null -> token = normalizeToken(cipher.decrypt(encrypted))
                                legacy != null -> {
                                    token = normalizeToken(legacy)
                                    if (token.isNotEmpty()) prefs[ENCRYPTED_TOKEN] = cipher.encrypt(token)
                                }
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            // Lost keys and tampered/restored records require re-entry. Never fall back
                            // to a legacy token, even when a damaged ciphertext exists beside it.
                            prefs.remove(ENCRYPTED_TOKEN)
                            token = ""
                            if (replacementToken != null) replacementFailed = true
                            report("media_token_unreadable", error)
                        }
                        prefs.remove(LEGACY_TOKEN)
                        next = MediaServiceSettings(url, token)
                    }
                    // Expose a credential only after the migration/save is durably committed.
                    mutableState.value = next
                    if (replacementFailed) throw OperationFailure("MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE")
                    next
                } catch (error: Exception) {
                    mutableState.value = MediaServiceSettings()
                    throw error
                }
            }
        }

    internal companion object {
        val URL = stringPreferencesKey("media_service_url_v1")
        val LEGACY_TOKEN = stringPreferencesKey("media_service_token_v1")
        val ENCRYPTED_TOKEN = stringPreferencesKey("media_service_token_encrypted_v1")

        fun normalizeToken(value: String): String = value.trim().also { token ->
            require(token.length <= 300 && token.all { it in '!'..'~' }) { "INVALID_MEDIA_SERVICE_TOKEN" }
        }
    }
}
