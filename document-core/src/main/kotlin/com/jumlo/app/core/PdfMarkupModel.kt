package com.jumlo.app.core

import kotlinx.serialization.Serializable
import kotlin.math.hypot

@Serializable
data class NormPoint(val x: Float, val y: Float)

@Serializable
data class PdfRect(val x: Float, val y: Float, val width: Float, val height: Float)

enum class PdfMarkupTool {
    NONE, PEN, HIGHLIGHTER, ERASER, TEXT, DATE, CHECK, SIGNATURE
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

    /** Returns true if a normalized eraser circle at (normX, normY) intersects the markup item. */
    fun hitTestEraser(item: PdfMarkupItem, normX: Float, normY: Float, radiusNorm: Float): Boolean {
        val r = radiusNorm.coerceIn(0.005f, 0.15f)
        return when (item) {
            is PdfMarkupItem.InkStroke -> hitTestPolyline(item.points, normX, normY, r + item.widthNorm * 0.5f)
            is PdfMarkupItem.HighlightStroke -> hitTestPolyline(item.points, normX, normY, r + item.widthNorm * 0.5f)
            is PdfMarkupItem.CheckMark -> {
                val s = item.sizeNorm
                normX in (item.normX - r)..(item.normX + s + r) && normY in (item.normY - r)..(item.normY + s + r)
            }
            is PdfMarkupItem.TextBox -> {
                val estW = (item.text.length.coerceIn(2, 24) * 0.012f * (item.fontSizePt / 14f)).coerceIn(0.05f, 0.55f)
                val estH = (0.028f * (item.fontSizePt / 14f)).coerceIn(0.02f, 0.12f)
                normX in (item.normX - r)..(item.normX + estW + r) && normY in (item.normY - r)..(item.normY + estH + r)
            }
            is PdfMarkupItem.SavedSignatureStamp -> {
                normX in (item.normX - r)..(item.normX + item.normW + r) && normY in (item.normY - r)..(item.normY + item.normH + r)
            }
        }
    }

    private fun hitTestPolyline(points: List<NormPoint>, x: Float, y: Float, threshold: Float): Boolean {
        if (points.isEmpty()) return false
        if (points.size == 1) return hypot(x - points[0].x, y - points[0].y) <= threshold
        for (i in 0 until points.size - 1) {
            if (distanceToSegment(x, y, points[i].x, points[i].y, points[i + 1].x, points[i + 1].y) <= threshold) return true
        }
        return false
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-8f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
