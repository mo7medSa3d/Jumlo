package com.jumlo.app.core

import java.net.URI
import java.util.Locale

/** Parse only canonical Google Drive/Docs links or opaque Drive IDs; never search inside arbitrary text. */
object DriveLink {
    private val ID = Regex("[A-Za-z0-9_-]{10,128}")
    private val DRIVE_FILE = Regex("/file/d/([A-Za-z0-9_-]{10,128})(?:/.*)?")
    private val DOC_FILE = Regex("/(document|spreadsheets|presentation)/d/([A-Za-z0-9_-]{10,128})(?:/.*)?")

    fun fileId(input: String): String {
        val value = input.trim()
        if (ID.matches(value)) return value
        val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("INVALID_DRIVE_FILE_ID") }
        val host = uri.host?.lowercase(Locale.ROOT)
        require(uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            uri.port == -1 && uri.fragment == null &&
            (host == "drive.google.com" || host == "docs.google.com" || host == "drive.usercontent.google.com")) { "INVALID_DRIVE_FILE_ID" }
        val path = uri.rawPath ?: throw IllegalArgumentException("INVALID_DRIVE_FILE_ID")
        val candidate = when (host) {
            "drive.google.com", "drive.usercontent.google.com" -> DRIVE_FILE.matchEntire(path)?.groupValues?.get(1) ?: run {
                if (path !in setOf("/open", "/uc", "/download")) null
                else uri.rawQuery?.split('&')?.mapNotNull { part ->
                    part.takeIf { it.startsWith("id=") }?.removePrefix("id=")
                }?.singleOrNull()
            }
            "docs.google.com" -> DOC_FILE.matchEntire(path)?.groupValues?.get(2)
            else -> null
        }
        require(candidate != null && ID.matches(candidate)) { "INVALID_DRIVE_FILE_ID" }
        return candidate
    }

    /** Convert a public/shared Google Drive or Docs URL into its direct file download endpoint. */
    fun toDirectDownloadUrl(input: String): String? {
        val trimmed = input.trim().substringBefore('#')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null || uri.port != -1) return null
        val path = uri.rawPath ?: return null
        return when (host) {
            "drive.google.com", "drive.usercontent.google.com" -> {
                val id = runCatching { fileId(trimmed) }.getOrNull() ?: return null
                "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"
            }
            "docs.google.com" -> {
                val match = DOC_FILE.matchEntire(path) ?: return null
                val kind = match.groupValues[1]
                val id = match.groupValues[2].takeIf { ID.matches(it) } ?: return null
                when (kind) {
                    "document" -> "https://docs.google.com/document/d/$id/export?format=pdf"
                    "spreadsheets" -> "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
                    "presentation" -> "https://docs.google.com/presentation/d/$id/export/pdf"
                    else -> null
                }
            }
            else -> null
        }
    }
}
