package com.jumlo.app.hosttest

import com.jumlo.app.data.source.DownloadSecretPersistence
import com.jumlo.app.data.source.EphemeralSecretStore
import kotlinx.coroutines.runBlocking

private class MemoryPersistence : DownloadSecretPersistence {
    private val values = mutableMapOf<String, CharArray>()
    override fun save(handle: String, secret: CharArray) { values[handle] = secret.copyOf() }
    override fun load(handle: String): CharArray? = values[handle]?.copyOf()
    override fun remove(handle: String) { values.remove(handle)?.fill('\u0000') }
    override fun clear() { values.values.forEach { it.fill('\u0000') }; values.clear() }
}

fun main() = runBlocking {
    val disk = MemoryPersistence()
    val firstProcess = EphemeralSecretStore(disk)
    val input = "https://example.org/video?signature=private".toCharArray()
    val url = firstProcess.putDownload(input)
    val password = firstProcess.put("pdf-password".toCharArray())
    input.fill('x')
    val secondProcess = EphemeralSecretStore(disk)
    check(String(secondProcess.copy(url)!!) == "https://example.org/video?signature=private")
    check(secondProcess.copy(password) == null) { "PDF passwords must remain process-local" }
    secondProcess.replaceDownload(url, "https://example.org/new".toCharArray())
    check(String(EphemeralSecretStore(disk).copy(url)!!) == "https://example.org/new")
    secondProcess.remove(url)
    check(EphemeralSecretStore(disk).copy(url) == null) { "Cancellation must delete durable input" }
    val consumed = secondProcess.putDownload("https://example.org/once".toCharArray())
    check(String(EphemeralSecretStore(disk).consume(consumed)!!) == "https://example.org/once")
    check(EphemeralSecretStore(disk).consume(consumed) == null)
    println("PASS: download input survives process recreation; passwords do not; replace/remove/consume persist")
}
