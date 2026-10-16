package com.jumlo.app.data.source

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** No passwords are serialized in drafts, Room, saved state, or WorkManager. */
@Singleton class EphemeralSecretStore @Inject constructor() {
    private val secrets=ConcurrentHashMap<String,CharArray>()
    fun put(secret:CharArray):String=UUID.randomUUID().toString().also { secrets[it]=secret.copyOf() }
    fun copy(handle:String):CharArray?=secrets[handle]?.copyOf()
    fun replace(handle:String,secret:CharArray) { secrets.put(handle,secret.copyOf())?.fill('\u0000') }
    fun remove(handle:String) { secrets.remove(handle)?.fill('\u0000') }
    fun consume(handle:String):CharArray?=secrets.remove(handle)
    fun clear() { secrets.values.forEach { it.fill('\u0000') }; secrets.clear() }
}
