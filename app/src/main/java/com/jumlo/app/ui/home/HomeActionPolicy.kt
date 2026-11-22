package com.jumlo.app.ui.home

import com.jumlo.app.core.ToolCatalogue

/** User order stays local. Callers supply only available processing/viewer IDs. */
object HomeActionPolicy {
    val defaults = listOf("pdf_02", "pdf_03", "img_02", "dl_media")

    fun resolve(saved: List<String>, eligible: List<String>): List<String> {
        val allowed = eligible.map(ToolCatalogue::canonicalId).toSet()
        val chosen = saved.map(ToolCatalogue::canonicalId).filter { it in allowed }.distinct()
        return chosen.ifEmpty { defaults.filter { it in allowed } }
    }

    /** Long-press pin toggle: unpin if present, else append with no pin cap. */
    fun togglePin(current: List<String>, toolId: String): List<String> {
        val canonicalCurrent = current.map(ToolCatalogue::canonicalId).distinct()
        val canonicalTarget = ToolCatalogue.canonicalId(toolId)
        return if (canonicalTarget in canonicalCurrent) canonicalCurrent - canonicalTarget
        else canonicalCurrent + canonicalTarget
    }
}
