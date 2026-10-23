package com.jumlo.app.core

import java.util.UUID

/** A selected result is an immutable, verified output, never an arbitrary filesystem path. */
data class OutputFileChoice(val id: String, val name: String, val mime: String, val bytes: Long)

/** The same constraints govern the Files toolbar and the asynchronous ViewModel handoff. */
object OutputWorkflowPolicy {
    // Align with JobStore admission. Large byte counts are constrained by real free space at processing time.
    private const val maxFiles = 1000

    fun reuseTool(mimeType: String): String = when {
        mimeType.equals("application/pdf", ignoreCase = true) -> "pdf_04"
        mimeType.startsWith("image/", ignoreCase = true) -> "img_01"
        mimeType.lowercase() in setOf("application/zip", "application/x-zip-compressed") -> "file_08"
        else -> "file_07"
    }

    fun validate(files: List<OutputFileChoice>, toolId: String): List<OutputFileChoice> {
        require(toolId in setOf("file_07", "pdf_02", "pdf_03", "pdf_04", "img_01", "file_08")) { "UNSUPPORTED_OUTPUT_WORKFLOW" }
        require(files.isNotEmpty()) { "NO_SOURCES" }
        require(files.size <= maxFiles) { "BUNDLE_TOO_MANY_ITEMS" }
        require(files.all { isId(it.id) && it.bytes >= 0L && it.name.isNotBlank() }) { "BUNDLE_INVALID_SELECTION" }
        require(files.distinctBy { it.id }.size == files.size) { "BUNDLE_DUPLICATE_ITEM" }
        val one = files.size == 1
        val allPdf = files.all { it.mime.equals("application/pdf", ignoreCase = true) }
        val allImages = files.all { it.mime.startsWith("image/", ignoreCase = true) }
        val allArchives = files.all { it.mime.lowercase() in setOf("application/zip", "application/x-zip-compressed") }
        val compatible = when (toolId) {
            "file_07" -> true
            "pdf_02" -> allImages
            "pdf_03" -> files.size >= 2 && allPdf
            "pdf_04" -> one && allPdf
            "img_01" -> one && allImages
            "file_08" -> one && allArchives
            else -> false
        }
        require(compatible) { "BUNDLE_INCOMPATIBLE_SELECTION" }
        return files // Intentionally preserve the ordering shown to the user.
    }

    fun isId(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}

/** A bounded local-only favorite list. Deleting original outputs never grants file access. */
object OutputFavorites {
    const val maxItems = 500

    fun sanitize(ids: List<String>): List<String> = ids
        .filter(OutputWorkflowPolicy::isId).distinct().takeLast(maxItems)

    fun toggle(current: List<String>, id: String): List<String> {
        require(OutputWorkflowPolicy.isId(id)) { "INVALID_OUTPUT_ID" }
        val old = sanitize(current)
        return if (id in old) old - id else (old + id).takeLast(maxItems)
    }
}
