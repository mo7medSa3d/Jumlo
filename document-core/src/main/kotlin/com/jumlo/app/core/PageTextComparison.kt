package com.jumlo.app.core

/** On-demand page-text search for large text-bearing documents.
 * The supplied reader is called only for required pages; no full-document
 * page-text collection or duplicate StringBuilder is allocated. Indices are zero-based. */
object PageTextComparison {
    fun anyPageContains(pageCount: Int, needle: String, reader: (Int) -> String): Boolean {
        require(pageCount >= 0 && needle.isNotBlank()) { "INVALID_SEARCH_TEXT" }
        for (index in 0 until pageCount) {
            if (reader(index).contains(needle)) return true
        }
        return false
    }
}
