package com.jumlo.app.ui.workspace

import com.jumlo.app.core.PageSelection
import com.jumlo.app.core.PdfSplitPlan

/** An empty execution spec means all pages, so a visual selection must keep one page. */
internal fun toggleSelectedPage(selected:Set<Int>,page:Int):Set<Int> =
    if(page in selected && selected.size>1) selected-page else selected+page

internal fun selectedPageNumbers(spec:String,total:Int):Set<Int> =
    if(spec.isBlank() || spec.trim().equals("all",true)) (1..total).toSet()
    else runCatching { PageSelection.parse(spec,total).toSet() }.getOrDefault(emptySet())

internal fun pageSelectionSpec(selected:Set<Int>):String {
    require(selected.isNotEmpty()) { "PAGE_SELECTION_REQUIRED" }
    val ordered=selected.sorted()
    val ranges=mutableListOf<String>()
    var start=ordered.first()
    var end=start
    fun addRange() { ranges+=if(start==end) "$start" else "$start-$end" }
    for(page in ordered.drop(1)) {
        if(page==end+1) end=page else { addRange();start=page;end=page }
    }
    addRange()
    return ranges.joinToString(",")
}

/** Preview delegates boundary/range parsing to the execution policies. */
internal fun previewSplitGroups(total:Int,everyMode:Boolean,boundaryMode:Boolean,spec:String,every:Int):List<IntRange> =
    when {
        everyMode -> (1..total step every.coerceAtLeast(1)).map { it..minOf(total,it+every.coerceAtLeast(1)-1) }
        boundaryMode -> PdfSplitPlan.splitAfter(total,spec).map { (start,size) -> (start+1)..(start+size) }
        spec.isBlank() || spec.trim().equals("all",true) -> (1..total).map { it..it }
        else -> PageSelection.groups(spec,total).map { it.first()..it.last() }
    }
