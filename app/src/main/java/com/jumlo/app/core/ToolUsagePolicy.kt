package com.jumlo.app.core

/** No fabricated usage: tool rankings derive exclusively from persisted open counts. */
object ToolUsagePolicy {
    private val idPattern=Regex("[a-z0-9_]{1,64}")
    fun recent(previous:List<String>,opened:String):List<String> =
        if(!idPattern.matches(opened)) previous else (listOf(opened)+previous.filter {it!=opened}).take(8)
    fun count(previous:Map<String,Int>,opened:String):Map<String,Int> {
        if(!idPattern.matches(opened)) return previous
        val current=previous[opened] ?: 0
        return previous+(opened to (current+1).coerceAtMost(1000000))
    }
    fun top(counts:Map<String,Int>,allowed:Set<String>):List<String> =
        counts.filter {it.key in allowed && it.value>0}.entries.sortedWith(compareByDescending<Map.Entry<String,Int>> {it.value}.thenBy {it.key}).take(5).map {it.key}
}
