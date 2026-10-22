package com.jumlo.app.ui.workspace

import com.jumlo.app.core.PageSelection
import com.jumlo.app.core.PdfPageOrder
import com.jumlo.app.data.processing.PdfPageEdit
import java.util.UUID

/** Immutable, bounded history: snapshots contain identities, order AND per-instance transforms. */
internal data class PageEditHistory(
    val current:List<PdfPageEdit>,
    val past:List<List<PdfPageEdit>> = emptyList(),
    val future:List<List<PdfPageEdit>> = emptyList()
) {
    fun commit(next:List<PdfPageEdit>):PageEditHistory {
        if(next==current || next.isEmpty() || next.any { it.sourcePage<1 || it.rotationDegrees !in setOf(0,90,180,270) || it.instanceId.isBlank() } || next.map { it.instanceId }.toSet().size!=next.size) return this
        return copy(current=next,past=(past+listOf(current)).takeLast(30),future=emptyList())
    }
    fun move(id:String,index:Int):PageEditHistory {
        val page=current.find { it.instanceId==id } ?: return this
        val next=current.toMutableList().apply { remove(page);add(index.coerceIn(0,size),page) }
        return commit(next)
    }
    fun rotate(ids:Set<String>)=commit(current.map { if(it.instanceId in ids) it.copy(rotationDegrees=(it.rotationDegrees+90)%360) else it })
    fun delete(ids:Set<String>)=commit(current.filterNot { it.instanceId in ids })
    fun duplicate(ids:Set<String>)=commit(current.flatMap { if(it.instanceId in ids) listOf(it,it.copy(instanceId=UUID.randomUUID().toString())) else listOf(it) })
    fun undo():PageEditHistory=if(past.isEmpty()) this else copy(current=past.last(),past=past.dropLast(1),future=(future+listOf(current)).takeLast(30))
    fun redo():PageEditHistory=if(future.isEmpty()) this else copy(current=future.last(),past=(past+listOf(current)).takeLast(30),future=future.dropLast(1))
}

internal fun initialPageEdits(total:Int,order:String,rotatePages:String,rotation:Int):List<PdfPageEdit> {
    val sources=if(order.isBlank()) (1..total).toList() else runCatching { PdfPageOrder.parse(order,total) }.getOrDefault((1..total).toList())
    val rotated=if(rotatePages.isBlank()) emptySet() else if(rotatePages.trim().equals("all",true)) (1..total).toSet() else runCatching { PageSelection.parse(rotatePages,total).toSet() }.getOrDefault(emptySet())
    val angle=((rotation%360)+360)%360
    return sources.mapIndexed { index,page -> PdfPageEdit("legacy-$index-$page",page,if(page in rotated) angle else 0) }
}
