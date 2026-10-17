package com.jumlo.app.core

import java.io.File

/**
 * Local-only error log. Entries are stable error CODES plus a timestamp and an
 * optional tool id — never URLs, file names, passwords, or user content.
 * Sanitization is enforced here so callers cannot accidentally log secrets.
 */
class ErrorLog(private val file: File, private val maxBytes: Long = 256L * 1024) {

    fun record(code: String, toolId: String? = null) {
        val safeCode = sanitize(code) ?: return
        val safeTool = toolId?.takeIf { TOOL_ID.matches(it) }
        val line = buildString {
            append(System.currentTimeMillis())
            append(' ')
            append(safeCode)
            if (safeTool != null) {
                append(" tool=")
                append(safeTool)
            }
            append('\n')
        }
        synchronized(lock) {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > maxBytes) rotate()
            file.appendText(line)
        }
    }

    // Include the previous bounded rotation in a user-initiated export. Neither
    // content is ever sent or exported without the user's explicit action.
    fun read(): String = synchronized(lock) {
        val archived = File(file.parentFile, file.name + ".1")
        (if (archived.isFile) archived.readText() else "") +
            (if (file.isFile) file.readText() else "")
    }

    // Clearing diagnostics must also clear the previous rotation. Keeping .1
    // would contradict the user's expectation that local log data was removed.
    fun clear() = synchronized(lock) {
        if (file.exists()) file.delete()
        val archived = File(file.parentFile, file.name + ".1")
        if (archived.exists()) archived.delete()
    }

    private fun rotate() {
        val archive = File(file.parentFile, file.name + ".1")
        if (archive.exists()) archive.delete()
        file.renameTo(archive)
    }

    companion object {
        private val lock = Any()
        private val CODE = Regex("[A-Z][A-Z_0-9]{2,60}")
        private val TOOL_ID = Regex("[a-z0-9_]{2,40}")

        /** Accepts only stable SNAKE_CASE codes; anything carrying data is dropped. */
        fun sanitize(raw: String?): String? {
            val candidate = raw?.trim()?.uppercase() ?: return null
            if (!CODE.matches(candidate)) return null
            // Defense in depth: reject anything that looks like a URL or key=value payload.
            if (candidate.contains("HTTP") && candidate.contains("://")) return null
            return candidate
        }
    }
}
