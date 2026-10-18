package com.jumlo.app.core

/** Strict one-based page order. A shorter valid list is an intentional page deletion,
 * but a malformed token or duplicate must never silently discard/duplicate user pages. */
object PdfPageOrder {
    fun parse(spec: String, count: Int): List<Int> {
        require(count > 0) { "INVALID_PAGE_COUNT" }
        if (spec.isBlank()) return (1..count).toList()
        require(spec.length <= 100000) { "INVALID_PAGE_ORDER" }
        val normalized = spec.map { ch -> when (ch) {
            in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
            in '\u06f0'..'\u06f9' -> '0' + (ch - '\u06f0')
            '\u060c' -> ','
            else -> ch
        } }.joinToString("")
        val parsed = normalized.split(',').map { entry ->
            val token = entry.trim()
            require(token.isNotEmpty() && token.all { it in '0'..'9' }) { "INVALID_PAGE_ORDER" }
            val number = token.toIntOrNull()
            require(number != null && number in 1..count) { "INVALID_PAGE_ORDER" }
            number
        }
        require(parsed.size == parsed.toSet().size) { "DUPLICATE_PAGE_ORDER" }
        return parsed
    }
}
