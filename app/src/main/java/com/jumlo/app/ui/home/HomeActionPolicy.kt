package com.jumlo.app.ui.home

/** User order stays local. Callers supply only available processing/viewer IDs. */
object HomeActionPolicy {
    val defaults=listOf("pdf_02","pdf_03","img_02","dl_media")
    fun resolve(saved:List<String>,eligible:List<String>):List<String> {
        val allowed=eligible.toSet()
        val chosen=saved.filter {it in allowed}.distinct().take(4)
        return chosen.ifEmpty {defaults.filter {it in allowed}}
    }
    /** Long-press pin toggle: unpin if present, else pin newest (max 4, oldest dropped). */
    fun togglePin(current:List<String>,toolId:String):List<String> =
        if(toolId in current) current - toolId else (current + toolId).takeLast(4)
}
