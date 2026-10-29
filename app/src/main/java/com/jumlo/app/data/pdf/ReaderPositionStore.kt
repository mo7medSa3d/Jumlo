package com.jumlo.app.data.pdf

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jumlo.app.diagnostics.AppDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/** Bounded app-private positions, without raw document URIs in preferences. */
private val Context.readerPositionDataStore by preferencesDataStore(name = "reader_position_v1")

object ReaderPositionStore {
    private const val MAX_SAVED_DOCUMENTS = 64
    private const val MAX_PAGE_INDEX = 100_000

    fun opaqueKey(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
        return "page_" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    suspend fun read(context: Context, uri: String): Int? {
        return try {
            val key = stringPreferencesKey(opaqueKey(uri))
            context.applicationContext.readerPositionDataStore.data.first()[key]
                ?.substringBefore('|')?.toIntOrNull()?.takeIf { it in 0..MAX_PAGE_INDEX }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            AppDiagnostics.report("reader_position_read", error)
            null
        }
    }

    suspend fun save(context: Context, uri: String, pageIndex: Int): Boolean {
        if (pageIndex !in 0..MAX_PAGE_INDEX) return false
        return try {
            context.applicationContext.readerPositionDataStore.edit { preferences ->
                preferences[stringPreferencesKey(opaqueKey(uri))] = pageIndex.toString() + "|" + System.currentTimeMillis()
                val stored = preferences.asMap().mapNotNull { (key, value) ->
                    val time = (value as? String)?.substringAfter('|')?.toLongOrNull()
                        ?: return@mapNotNull null
                    key.name to time
                }
                stored.sortedBy { it.second }
                    .take((stored.size - MAX_SAVED_DOCUMENTS).coerceAtLeast(0))
                    .forEach { (key, _) -> preferences.remove(stringPreferencesKey(key)) }
            }
            true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            AppDiagnostics.report("reader_position_save", error)
            false
        }
    }
}
