package com.jumlo.app.core

import java.net.URI
import java.util.Locale

/** Parse only canonical Google Drive/Docs links or opaque Drive IDs; never search inside arbitrary text. */
object DriveLink {
    private val ID = Regex("[A-Za-z0-9_-]{10,128}")
    private val DRIVE_FILE = Regex("/file/d/([A-Za-z0-9_-]{10,128})(?:/.*)?")
    private val DOC_FILE = Regex("/(?:document|spreadsheets|presentation)/d/([A-Za-z0-9_-]{10,128})(?:/.*)?")

    fun fileId(input: String): String {
        val value = input.trim()
        if (ID.matches(value)) return value
        val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("INVALID_DRIVE_FILE_ID") }
        val host = uri.host?.lowercase(Locale.ROOT)
        require(uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            uri.port == -1 && uri.fragment == null &&
            (host == "drive.google.com" || host == "docs.google.com")) { "INVALID_DRIVE_FILE_ID" }
        val path = uri.rawPath ?: throw IllegalArgumentException("INVALID_DRIVE_FILE_ID")
        val candidate = when (host) {
            "drive.google.com" -> DRIVE_FILE.matchEntire(path)?.groupValues?.get(1) ?: run {
                if (path != "/open") null
                else uri.rawQuery?.split('&')?.mapNotNull { part ->
                    part.takeIf { it.startsWith("id=") }?.removePrefix("id=")
                }?.singleOrNull()
            }
            "docs.google.com" -> DOC_FILE.matchEntire(path)?.groupValues?.get(1)
            else -> null
        }
        require(candidate != null && ID.matches(candidate)) { "INVALID_DRIVE_FILE_ID" }
        return candidate
    }
}
