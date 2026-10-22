package com.jumlo.app.core

/** On-demand comparison and verification for large text-bearing documents.
 * The supplied readers are called only for required pages; no full-document
 * page-text collection or duplicate StringBuilder is allocated. Indices are
 * zero-based; the returned mismatching page number is one-based. */
object PageTextComparison {
    private val whitespace = Regex("\\s+")


    fun anyPageContains(pageCount: Int, needle: String, reader: (Int) -> String): Boolean {
        require(pageCount >= 0 && needle.isNotBlank()) { "INVALID_SEARCH_TEXT" }
        for (index in 0 until pageCount) {
            if (reader(index).contains(needle)) return true
        }
        return false
    }

    fun firstMismatch(pageCount: Int, left: (Int) -> String, right: (Int) -> String): Int? {
        require(pageCount >= 0) { "INVALID_PAGE_COUNT" }
        for (index in 0 until pageCount) {
            val a = whitespace.replace(left(index).trim(), " ")
            val b = whitespace.replace(right(index).trim(), " ")
            if (a != b) return index + 1
        }
        return null
    }
}
