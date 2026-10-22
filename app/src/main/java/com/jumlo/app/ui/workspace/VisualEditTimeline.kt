package com.jumlo.app.ui.workspace

/** Immutable snapshots share completed stroke lists; history never enters a Bundle. */
internal data class VisualEditTimeline(
    val past:List<VisualEditSnapshot> = emptyList(),
    val future:List<VisualEditSnapshot> = emptyList()
) {
    fun record(current:VisualEditSnapshot)=copy(past=(past+current).takeLast(25),future=emptyList())
    fun undo(current:VisualEditSnapshot):VisualEditRestore?=past.lastOrNull()?.let {
        VisualEditRestore(it,copy(past=past.dropLast(1),future=(future+current).takeLast(25)))
    }
    fun redo(current:VisualEditSnapshot):VisualEditRestore?=future.lastOrNull()?.let {
        VisualEditRestore(it,copy(past=(past+current).takeLast(25),future=future.dropLast(1)))
    }
    fun restore(index:Int):VisualEditRestore?=past.getOrNull(index)?.let {
        VisualEditRestore(it,copy(past=past.take(index),future=emptyList()))
    }
}

internal data class VisualEditRestore(val snapshot:VisualEditSnapshot,val timeline:VisualEditTimeline)
