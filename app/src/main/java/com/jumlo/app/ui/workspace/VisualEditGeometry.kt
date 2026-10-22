package com.jumlo.app.ui.workspace

import kotlin.math.min

/**
 * Coordinates follow the existing engine contract: normalized [0, 1], top-left origin.
 * The geometry model has no Compose or Android dependencies so hit testing and
 * edge constraints can be verified on the host JVM.
 */
@kotlinx.serialization.Serializable
internal data class VisualRect(
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    companion object {
        const val MIN_EDGE = .04f
        val WHOLE = VisualRect(0f, 0f, 1f, 1f)
        val SIGNATURE_START = VisualRect(.6f, .75f, .95f, .95f)

        fun fitAspect(sourceWidth: Int, sourceHeight: Int, desiredAspect: Float): VisualRect {
            if (sourceWidth <= 0 || sourceHeight <= 0 || !desiredAspect.isFinite() || desiredAspect <= 0f) return WHOLE
            val sourceAspect = sourceWidth.toFloat() / sourceHeight
            val h = min(.88f, .88f * sourceAspect / desiredAspect)
            val w = min(.88f, h * desiredAspect / sourceAspect)
            val finalH = min(.88f, w * sourceAspect / desiredAspect)
            return VisualRect((1f-w)/2f, (1f-finalH)/2f, (1f+w)/2f, (1f+finalH)/2f).valid()
        }
    }

    fun valid(): VisualRect {
        if (listOf(left, top, right, bottom).any { !it.isFinite() }) return WHOLE
        val l = left.coerceIn(0f, 1f-MIN_EDGE)
        val t = top.coerceIn(0f, 1f-MIN_EDGE)
        val r = right.coerceIn(l+MIN_EDGE, 1f)
        val b = bottom.coerceIn(t+MIN_EDGE, 1f)
        return VisualRect(l, t, r, b)
    }

    fun dragged(handle: VisualHandle, dx: Float, dy: Float): VisualRect = when(handle) {
        VisualHandle.BODY -> {
            val x = dx.coerceIn(-left, 1f-right)
            val y = dy.coerceIn(-top, 1f-bottom)
            VisualRect(left+x, top+y, right+x, bottom+y)
        }
        VisualHandle.TOP_LEFT ->
            copy(left = (left+dx).coerceIn(0f, right-MIN_EDGE),
                top = (top+dy).coerceIn(0f, bottom-MIN_EDGE))
        VisualHandle.TOP_RIGHT ->
            copy(right = (right+dx).coerceIn(left+MIN_EDGE, 1f),
                top = (top+dy).coerceIn(0f, bottom-MIN_EDGE))
        VisualHandle.BOTTOM_LEFT ->
            copy(left = (left+dx).coerceIn(0f, right-MIN_EDGE),
                bottom = (bottom+dy).coerceIn(top+MIN_EDGE, 1f))
        VisualHandle.BOTTOM_RIGHT ->
            copy(right = (right+dx).coerceIn(left+MIN_EDGE, 1f),
                bottom = (bottom+dy).coerceIn(top+MIN_EDGE, 1f))
        VisualHandle.NONE -> this
    }

    /**
     * Hit testing is done in *actual preview pixels*, not physical document pixels.
     * Corners take priority over moving the rectangle.
     */
    fun hitHandle(x: Float, y: Float, widthPx: Float, heightPx: Float, radiusPx: Float): VisualHandle {
        if (widthPx <= 0 || heightPx <= 0) return VisualHandle.NONE
        val corners = listOf(
            Triple(VisualHandle.TOP_LEFT, left, top),
            Triple(VisualHandle.TOP_RIGHT, right, top),
            Triple(VisualHandle.BOTTOM_LEFT, left, bottom),
            Triple(VisualHandle.BOTTOM_RIGHT, right, bottom)
        )
        val closest = corners.minByOrNull {
            val dx = (it.second-x)*widthPx
            val dy = (it.third-y)*heightPx
            dx*dx + dy*dy
        }
        if(closest != null) {
            val dx = (closest.second-x)*widthPx
            val dy = (closest.third-y)*heightPx
            if(dx*dx+dy*dy <= radiusPx*radiusPx) return closest.first
        }
        return if(x in left..right && y in top..bottom) VisualHandle.BODY else VisualHandle.NONE
    }
}

internal enum class VisualHandle {
    NONE, BODY, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

internal fun sourceCropAspect(displayAspect:Float,quarterTurns:Int):Float =
    if(displayAspect>0f && quarterTurns%2!=0) 1f/displayAspect else displayAspect

/**
 * The result of a snapped drag: the rectangle, and the guide line that was hit.
 * The UI draws the guide and fires a haptic tick, so the user feels the snap
 * instead of guessing whether it happened.
 */
internal data class SnapResult(
    val rect: VisualRect,
    val guideX: Float? = null,
    val guideY: Float? = null
) {
    val snapped: Boolean get() = guideX != null || guideY != null
}

/**
 * Snapping for direct manipulation.
 *
 * Edges snap to the page centre, the thirds and the page borders. A dragged body
 * snaps as a whole, so the rectangle never jumps under the finger.
 */
internal object SnapGuides {
    /** How close, in normalized page units, an edge must be to snap. */
    const val TOLERANCE = 0.012f

    /** The lines an edge can snap to, in normalized page units. */
    fun targets(): List<Float> = listOf(0f, 1f / 3f, .5f, 2f / 3f, 1f)

    fun snap(rect: VisualRect, handle: VisualHandle, tolerance: Float = TOLERANCE): SnapResult {
        val lines = targets()
        if (handle == VisualHandle.NONE) return SnapResult(rect)
        if (handle == VisualHandle.BODY) {
            val (dx, gx) = nearestDelta(listOf(rect.left, rect.right, (rect.left + rect.right) / 2f), lines, tolerance)
            val (dy, gy) = nearestDelta(listOf(rect.top, rect.bottom, (rect.top + rect.bottom) / 2f), lines, tolerance)
            val moved = rect.copy(
                left = rect.left + dx, top = rect.top + dy,
                right = rect.right + dx, bottom = rect.bottom + dy
            ).valid()
            return SnapResult(moved, gx, gy)
        }
        val movesLeft = handle == VisualHandle.TOP_LEFT || handle == VisualHandle.BOTTOM_LEFT
        val movesRight = handle == VisualHandle.TOP_RIGHT || handle == VisualHandle.BOTTOM_RIGHT
        val movesTop = handle == VisualHandle.TOP_LEFT || handle == VisualHandle.TOP_RIGHT
        val movesBottom = handle == VisualHandle.BOTTOM_LEFT || handle == VisualHandle.BOTTOM_RIGHT
        val (dl, gl) = if (movesLeft) nearestDelta(listOf(rect.left), lines, tolerance) else 0f to null
        val (dr, gr) = if (movesRight) nearestDelta(listOf(rect.right), lines, tolerance) else 0f to null
        val (dt, gt) = if (movesTop) nearestDelta(listOf(rect.top), lines, tolerance) else 0f to null
        val (db, gb) = if (movesBottom) nearestDelta(listOf(rect.bottom), lines, tolerance) else 0f to null
        val moved = rect.copy(
            left = rect.left + dl, right = rect.right + dr,
            top = rect.top + dt, bottom = rect.bottom + db
        ).valid()
        return SnapResult(moved, gl ?: gr, gt ?: gb)
    }

    /** The smallest correction that brings one of the edges onto a guide line. */
    private fun nearestDelta(edges: List<Float>, targets: List<Float>, tolerance: Float): Pair<Float, Float?> {
        var bestDelta = 0f
        var bestTarget: Float? = null
        var bestDistance = Float.MAX_VALUE
        edges.forEach { edge ->
            targets.forEach { target ->
                val delta = target - edge
                val distance = kotlin.math.abs(delta)
                if (distance <= tolerance && distance < bestDistance) {
                    bestDistance = distance
                    bestDelta = delta
                    bestTarget = target
                }
            }
        }
        return bestDelta to bestTarget
    }
}

/** Backend order: crop source, flip horizontally/vertically, then rotate clockwise. */
internal data class VisualTransform(val turns:Int=0,val flipX:Boolean=false,val flipY:Boolean=false) {
    fun toDisplay(x:Float,y:Float):Pair<Float,Float> {
        val a=if(flipX) 1f-x else x;val b=if(flipY) 1f-y else y
        return when((turns%4+4)%4) { 1 -> 1f-b to a;2 -> 1f-a to 1f-b;3 -> b to 1f-a;else -> a to b }
    }
    fun toSource(x:Float,y:Float):Pair<Float,Float> {
        val (a,b)=when((turns%4+4)%4) { 1 -> y to 1f-x;2 -> 1f-x to 1f-y;3 -> 1f-y to x;else -> x to y }
        return (if(flipX) 1f-a else a) to (if(flipY) 1f-b else b)
    }
}

/** Fixed opposite corner; aspect is normalized width/height. BODY stays translation-only. */
internal fun VisualRect.draggedLocked(handle:VisualHandle,dx:Float,dy:Float,aspect:Float):VisualRect {
    if(handle==VisualHandle.BODY || handle==VisualHandle.NONE || aspect<=0f) return dragged(handle,dx,dy)
    val leftHandle=handle==VisualHandle.TOP_LEFT || handle==VisualHandle.BOTTOM_LEFT
    val topHandle=handle==VisualHandle.TOP_LEFT || handle==VisualHandle.TOP_RIGHT
    val anchorX=if(leftHandle) right else left;val anchorY=if(topHandle) bottom else top
    val movingX=(if(leftHandle) left else right)+dx
    val movingY=(if(topHandle) top else bottom)+dy
    // Crossing the fixed corner shrinks to the minimum. Absolute distances made
    // the rectangle grow again after the pointer crossed its opposite corner.
    val requestedW=kotlin.math.max(if(leftHandle) anchorX-movingX else movingX-anchorX,
        (if(topHandle) anchorY-movingY else movingY-anchorY)*aspect)
    val maxW=if(leftHandle) anchorX else 1f-anchorX
    val maxH=if(topHandle) anchorY else 1f-anchorY
    val w=requestedW.coerceIn(minOf(kotlin.math.max(VisualRect.MIN_EDGE,VisualRect.MIN_EDGE*aspect),maxW,maxH*aspect),minOf(maxW,maxH*aspect))
    val h=w/aspect
    return VisualRect(if(leftHandle) anchorX-w else anchorX,if(topHandle) anchorY-h else anchorY,if(leftHandle) anchorX else anchorX+w,if(topHandle) anchorY else anchorY+h)
}

/** Independent edge snapping must never break a selected crop aspect ratio. */
internal fun VisualRect.draggedWithSnap(handle:VisualHandle,dx:Float,dy:Float,aspect:Float):SnapResult {
    val moved=draggedLocked(handle,dx,dy,aspect)
    return if(aspect>0f && handle!=VisualHandle.BODY) SnapResult(moved)
        else SnapGuides.snap(moved.valid(),handle)
}

/** Only editor values enter a history snapshot; source metadata and secrets are excluded. */
@kotlinx.serialization.Serializable
internal data class VisualEditSnapshot(val rect:VisualRect,val turns:Int,val flipX:Boolean,val flipY:Boolean,val aspect:Float,
    val inkPoints:List<Float> = emptyList(),val inkStrokes:List<List<Float>> = emptyList())
