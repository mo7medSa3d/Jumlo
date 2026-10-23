package com.jumlo.app.data.jobs

import com.jumlo.app.data.processing.DownloadSettings
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.JobRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Preserve durable request identity while restoring freshly re-entered ephemeral input.
 * Called only after DraftStore has matched the complete submitted fingerprint.
 * Returns the existing handle; the caller still owns/removes the unused candidate handle.
 */
internal fun rebindDownloadSubmissionSecret(request: JobRequest, candidate: JobRequest,
    secrets: EphemeralSecretStore): String? {
    if (request.id == candidate.id || !request.toolId.startsWith("dl_")) return null
    require(request.toolId == candidate.toolId) { "JOB_IDENTITY_CONFLICT" }
    val existing = Json.decodeFromString<DownloadSettings>(request.settingsJson)
    val fresh = Json.decodeFromString<DownloadSettings>(candidate.settingsJson)
    require(existing.copy(urlSecretHandle = fresh.urlSecretHandle) == fresh) { "JOB_IDENTITY_CONFLICT" }
    val input = secrets.copy(fresh.urlSecretHandle) ?: throw OperationFailure("SECRET_EXPIRED")
    try { secrets.replaceDownloadBlocking(existing.urlSecretHandle, input) }
    finally { input.fill('\u0000') }
    return existing.urlSecretHandle
}
