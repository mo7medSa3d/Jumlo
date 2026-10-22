package com.jumlo.app.printing

/** Validates what is *typed*, independently of the last successfully applied print setting. */
object PrintDraftPolicy {
    private val ranges = mapOf(
        "customWidth" to (25.0..1000.0),
        "customHeight" to (25.0..1000.0),
        "imageDpi" to (36.0..1200.0),
        "marginLeft" to (0.0..450.0),
        "marginRight" to (0.0..450.0),
        "marginTop" to (0.0..450.0),
        "marginBottom" to (0.0..450.0)
    )

    fun parse(key: String, raw: String): Double? {
        val range = ranges[key] ?: return null
        if (raw.length > 24) return null
        val normalized = raw.trim().map { c ->
            when (c) {
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                in '\u06f0'..'\u06f9' -> '0' + (c - '\u06f0')
                '\u066b', ',' -> '.'
                else -> c
            }
        }.joinToString("")
        return normalized.toDoubleOrNull()?.takeIf {
            it.isFinite() && it in range && (key != "imageDpi" || it % 1.0 == 0.0)
        }
    }

    fun errors(drafts: Map<String, String>): Set<String> =
        drafts.keys.filterTo(mutableSetOf()) { parse(it, drafts[it].orEmpty()) == null }
}
