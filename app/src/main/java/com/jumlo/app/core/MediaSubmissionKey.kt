package com.jumlo.app.core

/** Complete user intent for idempotent media submission in a single ViewModel session.
 * The length prefixes make distinct names/URLs unambiguous without stripping Unicode. */
object MediaSubmissionKey {
    fun key(
        selection: String,
        url: String,
        name: String,
        wifiOnly: Boolean,
        unmetered: Boolean,
        allowHttp: Boolean
    ): String = buildString {
        append("media-v2")
        listOf(selection, url, name, wifiOnly.toString(), unmetered.toString(), allowHttp.toString()).forEach { value ->
            append('|').append(value.length).append(':').append(value)
        }
    }
}
