package com.jumlo.app.data.source

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Only explicitly submitted download URLs cross this boundary, never document passwords. */
interface DownloadSecretPersistence {
    fun save(handle: String, secret: CharArray)
    fun load(handle: String): CharArray?
    fun remove(handle: String)
    fun clear()
}

private object MemoryOnly : DownloadSecretPersistence {
    override fun save(handle: String, secret: CharArray) = Unit
    override fun load(handle: String): CharArray? = null
    override fun remove(handle: String) = Unit
    override fun clear() = Unit
}

/** Passwords remain in memory; download URLs use the encrypted, no-backup Android adapter. */
@Singleton class EphemeralSecretStore @Inject constructor(private val downloads: DownloadSecretPersistence) {
    constructor() : this(MemoryOnly)
    private val secrets = ConcurrentHashMap<String, CharArray>()
    fun put(secret: CharArray): String = UUID.randomUUID().toString().also { replace(it, secret) }
    suspend fun putDownload(secret: CharArray): String {
        val handle=UUID.randomUUID().toString()
        try {
            return withContext(Dispatchers.IO) { replaceDownloadBlocking(handle, secret); handle }
        } catch(error: Exception) {
            withContext(NonCancellable+Dispatchers.IO) { remove(handle) }
            throw error
        }
    }
    suspend fun replaceDownload(handle: String, secret: CharArray) = withContext(Dispatchers.IO) {
        replaceDownloadBlocking(handle, secret)
    }
    /** Called from worker I/O paths; a cache miss may decrypt persisted download input. */
    @Synchronized fun copy(handle: String): CharArray? {
        secrets[handle]?.let { return it.copyOf() }
        val restored = downloads.load(handle) ?: return null
        secrets[handle] = restored
        return restored.copyOf()
    }
    @Synchronized fun replace(handle: String, secret: CharArray) {
        secrets.put(handle, secret.copyOf())?.fill('\u0000')
    }
    @Synchronized internal fun replaceDownloadBlocking(handle: String, secret: CharArray) {
        downloads.save(handle, secret)
        replace(handle, secret)
    }
    @Synchronized fun remove(handle: String) {
        secrets.remove(handle)?.fill('\u0000')
        downloads.remove(handle)
    }
    @Synchronized fun consume(handle: String): CharArray? {
        val value = secrets.remove(handle) ?: downloads.load(handle)
        downloads.remove(handle)
        return value
    }
    @Synchronized fun clear() {
        secrets.values.forEach { it.fill('\u0000') }
        secrets.clear()
        downloads.clear()
    }
}
