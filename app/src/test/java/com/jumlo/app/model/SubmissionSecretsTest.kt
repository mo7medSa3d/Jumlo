package com.jumlo.app.model

import com.jumlo.app.data.jobs.rebindDownloadSubmissionSecret
import com.jumlo.app.data.processing.DownloadSettings
import com.jumlo.app.data.source.EphemeralSecretStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SubmissionSecretsTest {
    @Test fun reenteredUrlRestoresOriginalHandleWithoutChangingPersistedRequest() {
        val restartedProcess = EphemeralSecretStore()
        val previousSettings = DownloadSettings("expired-handle", "report.pdf", false, false, false)
        val original = JobRequest("original", "dl_02", emptyList(), 1, Json.encodeToString(previousSettings), 1)
        val freshHandle = restartedProcess.put("https://example.org/report.pdf?token=test".toCharArray())
        val candidate = original.copy(id = "candidate", settingsJson = Json.encodeToString(previousSettings.copy(urlSecretHandle = freshHandle)))
        assertNull(restartedProcess.copy("expired-handle"))
        assertEquals("expired-handle", rebindDownloadSubmissionSecret(original, candidate, restartedProcess))
        restartedProcess.remove(freshHandle)
        assertEquals("https://example.org/report.pdf?token=test", String(restartedProcess.copy("expired-handle")!!))
        assertEquals(Json.encodeToString(previousSettings), original.settingsJson)
        assertFalse(original.settingsJson.contains("token=test"))
    }

    @Test fun differentNetworkPolicyCannotBeReboundToOldIdentity() {
        val secrets = EphemeralSecretStore()
        val fresh = secrets.put("https://example.org/file".toCharArray())
        val oldSettings = DownloadSettings("old", "file", false, false, false)
        val request = JobRequest("original", "dl_02", emptyList(), 1, Json.encodeToString(oldSettings), 1)
        val candidate = request.copy(id = "new", settingsJson = Json.encodeToString(oldSettings.copy(urlSecretHandle = fresh, cleartextConsent = true)))
        assertThrows(IllegalArgumentException::class.java) { rebindDownloadSubmissionSecret(request, candidate, secrets) }
        assertNull(secrets.copy("old"))
    }
}
