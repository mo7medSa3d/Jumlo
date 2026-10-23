package com.jumlo.app.data.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadSecretPersistenceTest {
    private class FakeDisk : DownloadSecretPersistence {
        val data=mutableMapOf<String,CharArray>()
        override fun save(handle:String,secret:CharArray) { data[handle]=secret.copyOf() }
        override fun load(handle:String)=data[handle]?.copyOf()
        override fun remove(handle:String) { data.remove(handle)?.fill('\u0000') }
        override fun clear() { data.clear() }
    }
    @Test fun queuedDownloadSurvivesRecreationWhilePasswordsStayEphemeral()=runBlocking {
        val disk=FakeDisk()
        val before=EphemeralSecretStore(disk)
        val url=before.putDownload("https://example.org/film?token=private".toCharArray())
        val password=before.put("document-password".toCharArray())
        val after=EphemeralSecretStore(disk)
        assertEquals("https://example.org/film?token=private",String(after.copy(url)!!))
        assertNull(after.copy(password))
        after.remove(url)
        assertNull(EphemeralSecretStore(disk).copy(url))
    }
    @Test fun retryReplacementAndConsumeSurviveRecreation()=runBlocking {
        val disk=FakeDisk();val before=EphemeralSecretStore(disk)
        val handle=before.putDownload("https://example.org/old".toCharArray())
        before.replaceDownload(handle,"https://example.org/new".toCharArray())
        assertEquals("https://example.org/new",String(EphemeralSecretStore(disk).consume(handle)!!))
        assertNull(EphemeralSecretStore(disk).copy(handle))
    }
}
