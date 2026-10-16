package com.universalutilities.app.core.media

/** A linear resource guard, not a JSON parser. Syntax remains the JSON library's job. */
object MediaJsonGuard {
    private const val MAX_SIZE = 4 * 1024 * 1024
    private const val MAX_DEPTH = 64

    fun check(text: String) {
        require(text.length <= MAX_SIZE && text.toByteArray(Charsets.UTF_8).size <= MAX_SIZE) {
            "MEDIA_METADATA_LIMIT"
        }
        var inString = false
        var escaped = false
        var depth = 0
        for (character in text) {
            if (inString) {
                if (escaped) escaped = false
                else when (character) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
            } else when (character) {
                '"' -> inString = true
                '[', '{' -> {
                    depth++
                    require(depth <= MAX_DEPTH) { "MEDIA_METADATA_DEPTH" }
                }
                ']', '}' -> depth--
            }
        }
        // Unbalanced delimiters, escapes and trailing data are rejected by the parser.
    }
}
