package com.jumlo.app.core

import kotlinx.serialization.Serializable

@Serializable
data class NormPoint(val x: Float, val y: Float)

@Serializable
data class PdfRect(val x: Float, val y: Float, val width: Float, val height: Float)

enum class PdfMarkupTool {
    NONE, PEN, HIGHLIGHTER, TEXT, DATE, CHECK, SIGNATURE
}

@Serializable
sealed interface PdfMarkupItem {
    val id: String
    val pageIndex: Int

    @Serializable
    data class InkStroke(
        override val id: String,
        override val pageIndex: Int,
        val points: List<NormPoint>,
        val colorArgb: Int,
        val widthNorm: Float = 0.0045f
    ) : PdfMarkupItem

    @Serializable
    data class HighlightStroke(
        override val id: String,
        override val pageIndex: Int,
        val points: List<NormPoint>,
        val colorArgb: Int,
        val widthNorm: Float = 0.024f,
        val alpha: Float = 0.35f
    ) : PdfMarkupItem

    @Serializable
    data class TextBox(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val text: String,
        val colorArgb: Int,
        val fontSizePt: Float = 14f
    ) : PdfMarkupItem

    @Serializable
    data class CheckMark(
        override val id: String,
        override val pageIndex: Int,
        val normX: Float,
        val normY: Float,
        val isCross: Boolean = false,
        val colorArgb: Int,
        val sizeNorm: Float = 0.042f
    ) : PdfMarkupItem

    @Serializable
    data class SavedSignatureStamp(
        override val id: String,
        override val pageIndex: Int,
        val signaturePath: String,
        val normX: Float,
        val normY: Float,
        val normW: Float = 0.28f,
        val normH: Float = 0.10f
    ) : PdfMarkupItem
}

class UndoRedoStack<T>(private val maxDepth: Int = 50) {
    private val undoStack = ArrayDeque<List<T>>()
    private val redoStack = ArrayDeque<List<T>>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(snapshot: List<T>) {
        undoStack.addLast(snapshot)
        while (undoStack.size > maxDepth) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: List<T>): List<T>? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return prev
    }

    fun redo(current: List<T>): List<T>? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

object PdfMarkupPolicy {
    /** Maps normalized screen coordinates (top-left origin) into PDF user space (bottom-left origin),
     *  accounting for page rotation (0, 90, 180, 270) and cropBox offsets. */
    fun toPdfPoint(
        boxLeft: Float,
        boxBottom: Float,
        boxWidth: Float,
        boxHeight: Float,
        rotationDeg: Int,
        normX: Float,
        normY: Float
    ): Pair<Float, Float> {
        val nx = normX.coerceIn(0f, 1f)
        val ny = normY.coerceIn(0f, 1f)
        val rot = ((rotationDeg % 360) + 360) % 360
        return when (rot) {
            90 -> Pair(boxLeft + ny * boxWidth, boxBottom + nx * boxHeight)
            180 -> Pair(boxLeft + (1f - nx) * boxWidth, boxBottom + ny * boxHeight)
            270 -> Pair(boxLeft + (1f - ny) * boxWidth, boxBottom + (1f - nx) * boxHeight)
            else -> Pair(boxLeft + nx * boxWidth, boxBottom + (1f - ny) * boxHeight)
        }
    }

    fun toPdfRect(
        boxLeft: Float,
        boxBottom: Float,
        boxWidth: Float,
        boxHeight: Float,
        rotationDeg: Int,
        normX: Float,
        normY: Float,
        normW: Float,
        normH: Float
    ): PdfRect {
        val p1 = toPdfPoint(boxLeft, boxBottom, boxWidth, boxHeight, rotationDeg, normX, normY)
        val p2 = toPdfPoint(boxLeft, boxBottom, boxWidth, boxHeight, rotationDeg, normX + normW, normY + normH)
        val left = minOf(p1.first, p2.first)
        val bottom = minOf(p1.second, p2.second)
        val right = maxOf(p1.first, p2.first)
        val top = maxOf(p1.second, p2.second)
        return PdfRect(left, bottom, (right - left).coerceAtLeast(1f), (top - bottom).coerceAtLeast(1f))
    }
}
