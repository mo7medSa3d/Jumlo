package com.jumlo.app.core

/** Visual split markers denote boundaries after one-based page numbers.
 * Output groups are zero-based start + length for PdfBoxBackend.split(). */
object PdfSplitPlan {
    fun splitAfter(pageCount: Int, pages: String): List<Pair<Int, Int>> {
        require(pageCount > 1) { "INVALID_PAGE_COUNT" }
        require(pages.isNotBlank() && !pages.equals("all", ignoreCase = true)) { "SPLIT_BOUNDARY_REQUIRED" }
        val marks = PageSelection.parse(pages, pageCount).sorted()
        require(marks.isNotEmpty() && marks.all { it < pageCount }) { "INVALID_SPLIT_BOUNDARY" }
        val groups = ArrayList<Pair<Int,Int>>(marks.size + 1)
        var start = 0
        for (end in marks) {
            val length = end - start
            require(length > 0) { "INVALID_SPLIT_BOUNDARY" }
            groups += start to length
            start = end
        }
        groups += start to (pageCount - start)
        return groups
    }
}
