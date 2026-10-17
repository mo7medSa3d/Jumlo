package com.jumlo.app.data.files

import com.jumlo.app.core.HttpTransport
import com.jumlo.app.core.SafeNames
import com.jumlo.app.core.TransportResponse
import com.jumlo.app.data.jobs.OperationFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder

data class DriveFileMetadata(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?
)

/**
 * Official Google Drive v3 REST API client for importing (downloading) and exporting (uploading)
 * files that the signed-in user can access.
 *
 * Security invariants:
 * - Access tokens are passed as CharArray from EphemeralSecretStore and never logged or persisted.
 * - Only HTTPS endpoints on www.googleapis.com are contacted.
 * - Errors map to stable user-facing error categories: LOGIN_REQUIRED, PRIVATE_CONTENT,
 *   REMOVED, RATE_LIMITED, UNSUPPORTED, NETWORK_ERROR.
 */
class GoogleDriveClient(
    private val transport: HttpTransport,
    private val okHttpClient: OkHttpClient? = null
) {
    companion object {
        private val FILE_ID_REGEX = Regex("^[A-Za-z0-9_-]{10,128}$")
        private val URL_PATTERNS = listOf(
            Regex("drive\\.google\\.com/file/d/([A-Za-z0-9_-]{10,128})"),
            Regex("drive\\.google\\.com/open\\?id=([A-Za-z0-9_-]{10,128})"),
            Regex("docs\\.google\\.com/(?:document|spreadsheets|presentation)/d/([A-Za-z0-9_-]{10,128})")
        )
        private val json = Json { ignoreUnknownKeys = true }

        fun extractFileId(rawInput: String): String {
            val trimmed = rawInput.trim()
            for (pattern in URL_PATTERNS) {
                val match = pattern.find(trimmed)
                if (match != null) return match.groupValues[1]
            }
            require(FILE_ID_REGEX.matches(trimmed)) { "INVALID_DRIVE_FILE_ID" }
            return trimmed
        }

        fun classifyStatus(code: Int, bodySnippet: String = ""): String = when (code) {
            401 -> "LOGIN_REQUIRED"
            403 -> if (bodySnippet.contains("rateLimitExceeded", ignoreCase = true) ||
                bodySnippet.contains("userRateLimitExceeded", ignoreCase = true)
            ) "RATE_LIMITED" else "PRIVATE_CONTENT"
            404 -> "REMOVED"
            429 -> "RATE_LIMITED"
            400, 415 -> "UNSUPPORTED"
            else -> if (code in 500..599) "NETWORK_ERROR" else "DRIVE_API_FAILED"
        }
    }

    fun fetchMetadata(fileIdOrUrl: String, accessToken: CharArray): DriveFileMetadata {
        require(accessToken.isNotEmpty()) { "LOGIN_REQUIRED" }
        val fileId = extractFileId(fileIdOrUrl)
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType,size&supportsAllDrives=false"
        val authHeader = "Bearer " + String(accessToken)
        try {
            transport.open(url, mapOf("Authorization" to authHeader, "Accept" to "application/json")).use { resp ->
                val text = resp.body.bufferedReader().use { it.readText() }
                if (resp.code != 200) {
                    throw OperationFailure(classifyStatus(resp.code, text))
                }
                val obj = json.parseToJsonElement(text).jsonObject
                val id = obj.str("id").ifBlank { fileId }
                val name = SafeNames.fromDisplayName(obj.str("name").ifBlank { "drive-$fileId.bin" }, "drive-$fileId.bin")
                val mime = obj.str("mimeType").ifBlank { "application/octet-stream" }
                val size = (obj["size"] as? JsonPrimitive)?.longOrNull
                return DriveFileMetadata(id, name, mime, size)
            }
        } catch (op: OperationFailure) {
            throw op
        } catch (_: IOException) {
            throw OperationFailure("NETWORK_ERROR")
        }
    }

    fun downloadFile(
        fileIdOrUrl: String,
        accessToken: CharArray,
        targetFile: File,
        maxBytes: Long,
        minFreeBytes: Long,
        checkCancel: () -> Unit = {}
    ): DriveFileMetadata {
        require(accessToken.isNotEmpty()) { "LOGIN_REQUIRED" }
        checkCancel()
        val meta = fetchMetadata(fileIdOrUrl, accessToken)
        if (meta.sizeBytes != null && meta.sizeBytes > maxBytes) {
            throw OperationFailure("DOWNLOAD_BYTE_BUDGET_EXCEEDED")
        }
        val isGoogleWorkspaceDoc = meta.mimeType.startsWith("application/vnd.google-apps.")
        if (meta.mimeType == "application/vnd.google-apps.folder") {
            throw OperationFailure("UNSUPPORTED")
        }
        val downloadUrl = if (isGoogleWorkspaceDoc) {
            val exportMime = URLEncoder.encode("application/pdf", "UTF-8")
            "https://www.googleapis.com/drive/v3/files/${meta.id}/export?mimeType=$exportMime"
        } else {
            "https://www.googleapis.com/drive/v3/files/${meta.id}?alt=media&supportsAllDrives=false"
        }
        val resolvedName = if (isGoogleWorkspaceDoc && !meta.name.endsWith(".pdf", true)) {
            SafeNames.fromDisplayName("${meta.name}.pdf", "document.pdf")
        } else meta.name
        val resolvedMime = if (isGoogleWorkspaceDoc) "application/pdf" else meta.mimeType
        val authHeader = "Bearer " + String(accessToken)
        targetFile.parentFile?.mkdirs()
        try {
            transport.open(downloadUrl, mapOf("Authorization" to authHeader)).use { resp ->
                if (resp.code != 200) {
                    val errText = runCatching { resp.body.bufferedReader().use { it.readText().take(512) } }.getOrDefault("")
                    throw OperationFailure(classifyStatus(resp.code, errText))
                }
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                FileOutputStream(targetFile).use { out ->
                    while (true) {
                        checkCancel()
                        val n = resp.body.read(buffer)
                        if (n < 0) break
                        if (n > 0) {
                            if (n.toLong() > maxBytes - total) throw OperationFailure("DOWNLOAD_BYTE_BUDGET_EXCEEDED")
                            val parent = targetFile.parentFile
                            if (parent != null && parent.usableSpace > 0L && parent.usableSpace < minFreeBytes + n) {
                                throw OperationFailure("DISK_BUDGET_EXCEEDED")
                            }
                            out.write(buffer, 0, n)
                            total += n
                        }
                    }
                    out.fd.sync()
                }
                return DriveFileMetadata(meta.id, resolvedName, resolvedMime, total)
            }
        } catch (t: Throwable) {
            targetFile.delete()
            if (t is OperationFailure) throw t
            if (t is IOException) throw OperationFailure("NETWORK_ERROR")
            throw t
        }
    }

    fun uploadFile(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        folderId: String?,
        accessToken: CharArray,
        checkCancel: () -> Unit = {}
    ): DriveFileMetadata {
        require(accessToken.isNotEmpty()) { "LOGIN_REQUIRED" }
        require(sourceFile.isFile) { "SOURCE_NOT_FOUND" }
        checkCancel()
        val safeName = SafeNames.fromDisplayName(displayName, sourceFile.name)
        val parentsClause = folderId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val cleanFolder = extractFileId(it)
            ",\"parents\":[\"$cleanFolder\"]"
        } ?: ""
        val metaJson = "{\"name\":${ Json.encodeToString(kotlinx.serialization.serializer(), safeName) },\"mimeType\":${ Json.encodeToString(kotlinx.serialization.serializer(), mimeType) }$parentsClause}"
        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size"
        val authHeader = "Bearer " + String(accessToken)

        val client = okHttpClient
        if (client != null) {
            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metaJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(sourceFile.asRequestBody(mimeType.toMediaType()))
                .build()
            val req = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", authHeader)
                .post(multipart)
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val bodyText = resp.body?.string().orEmpty()
                    if (resp.code !in 200..201) {
                        throw OperationFailure(classifyStatus(resp.code, bodyText))
                    }
                    val obj = json.parseToJsonElement(bodyText).jsonObject
                    return DriveFileMetadata(
                        id = obj.str("id").ifBlank { "uploaded" },
                        name = obj.str("name").ifBlank { safeName },
                        mimeType = obj.str("mimeType").ifBlank { mimeType },
                        sizeBytes = (obj["size"] as? JsonPrimitive)?.longOrNull ?: sourceFile.length()
                    )
                }
            } catch (op: OperationFailure) {
                throw op
            } catch (_: IOException) {
                throw OperationFailure("NETWORK_ERROR")
            }
        } else {
            // Transport fallback for unit tests
            try {
                transport.open(
                    uploadUrl,
                    mapOf(
                        "Authorization" to authHeader,
                        "X-Upload-Metadata" to metaJson,
                        "X-Upload-Content-Length" to sourceFile.length().toString()
                    )
                ).use { resp: TransportResponse ->
                    val text = resp.body.bufferedReader().use { it.readText() }
                    if (resp.code !in 200..201) {
                        throw OperationFailure(classifyStatus(resp.code, text))
                    }
                    val obj = json.parseToJsonElement(text).jsonObject
                    return DriveFileMetadata(
                        id = obj.str("id").ifBlank { "uploaded" },
                        name = obj.str("name").ifBlank { safeName },
                        mimeType = obj.str("mimeType").ifBlank { mimeType },
                        sizeBytes = (obj["size"] as? JsonPrimitive)?.longOrNull ?: sourceFile.length()
                    )
                }
            } catch (op: OperationFailure) {
                throw op
            } catch (_: IOException) {
                throw OperationFailure("NETWORK_ERROR")
            }
        }
    }

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
}
