package com.jumlo.app.core

/** Concatenate several documents into one, renumbering pages 1..N and carrying
 *  per-page rotation forward. Preserves each document's internal order. */
object PageDocumentMerger {
    fun merge(docs: List<PdfPageEdit>): PdfPageEdit {
        require(docs.isNotEmpty()) { "NO_DOCUMENTS" }
        require(docs.all { it.order.isNotEmpty() }) { "EMPTY_DOCUMENT" }
        val order = ArrayList<Int>(docs.sumOf { it.order.size })
        val rotations = mutableMapOf<Int, Int>()
        var next = 1
        for (doc in docs) {
            for (srcPage in doc.order) {
                order += next
                val rot = doc.rotations[srcPage]
                if (rot != null) rotations[next] = rot
                next++
            }
        }
        return PdfPageEdit(order, emptySet(), rotations)
    }
}

/** Undo/redo stack over immutable [PdfPageEdit] snapshots. Non-destructive:
 *  applying an edit pushes the previous state; undo/redo move a cursor. */
class PageEditHistory(initial: PdfPageEdit) {
    require(initial.order.isNotEmpty()) { "EMPTY_DOCUMENT" }
    private val undoStack = ArrayDeque<PdfPageEdit>()
    private val redoStack = ArrayDeque<PdfPageEdit>()
    init { undoStack.addLast(initial) }

    val current: PdfPageEdit get() = undoStack.last()
    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val depth: Int get() = undoStack.size

    fun apply(edit: PdfPageEdit): PdfPageEdit {
        require(edit.order.isNotEmpty()) { "EMPTY_DOCUMENT" }
        undoStack.addLast(edit)
        redoStack.clear()
        return edit
    }

    fun undo(): PdfPageEdit {
        require(canUndo) { "CANNOT_UNDO" }
        redoStack.addLast(undoStack.removeLast())
        return undoStack.last()
    }

    fun redo(): PdfPageEdit {
        require(canRedo) { "CANNOT_REDO" }
        undoStack.addLast(redoStack.removeLast())
        return undoStack.last()
    }

    fun reset(edit: PdfPageEdit) {
        require(edit.order.isNotEmpty()) { "EMPTY_DOCUMENT" }
        undoStack.clear()
        redoStack.clear()
        undoStack.addLast(edit)
    }
}
