package com.jumlo.app.data.pdf

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/** Only opaque document fingerprints and page indexes are persisted; no content URI
 * (which may contain access tokens or document titles) is stored in DataStore. */
private val Context.readerPositionsDataStore by preferencesDataStore(name = "pdf_reader_positions_v1")

internal object PdfReaderPositionStore {
    private const val PAGE_PREFIX = "page_"
    private const val VISITED_PREFIX = "visited_"
    private const val MAX_POSITIONS = 128

    internal fun fingerprint(uri: String): String =
        MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun pageKey(digest: String): Preferences.Key<Int> =
        intPreferencesKey(PAGE_PREFIX + digest)

    private fun visitedKey(digest: String): Preferences.Key<Long> =
        longPreferencesKey(VISITED_PREFIX + digest)

    suspend fun load(context: Context, uri: String): Int? {
        val digest = fingerprint(uri)
        return context.applicationContext.readerPositionsDataStore.data.first()[pageKey(digest)]
            ?.takeIf { it >= 0 }
    }

    suspend fun save(context: Context, uri: String, page: Int) {
        require(page >= 0) { "READER_PAGE_OUT_OF_RANGE" }
        val digest = fingerprint(uri)
        context.applicationContext.readerPositionsDataStore.edit { preferences ->
            preferences[pageKey(digest)] = page
            preferences[visitedKey(digest)] = System.currentTimeMillis()
            val current = preferences.asMap().keys.map { it.name }
                .filter { it.startsWith(PAGE_PREFIX) }
            val excess = (current.size - MAX_POSITIONS).coerceAtLeast(0)
            if (excess > 0) {
                current.sortedBy { key ->
                    preferences[visitedKey(key.removePrefix(PAGE_PREFIX))] ?: Long.MIN_VALUE
                }.take(excess).forEach { key ->
                    val oldDigest = key.removePrefix(PAGE_PREFIX)
                    preferences.remove(pageKey(oldDigest))
                    preferences.remove(visitedKey(oldDigest))
                }
            }
        }
    }
}
