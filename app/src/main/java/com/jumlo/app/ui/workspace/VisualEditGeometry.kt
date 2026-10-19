package com.jumlo.app.ui.workspace

import kotlin.math.min

/**
 * Coordinates follow the existing engine contract: normalized [0, 1], top-left origin.
 * The geometry model has no Compose or Android dependencies so hit testing and
 * edge constraints can be verified on the host JVM.
 */
internal data class VisualRect(
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    companion object {
        const val MIN_EDGE = .04f
        val WHOLE = VisualRect(0f, 0f, 1f, 1f)
        val REDACTION_START = VisualRect(.1f, .1f, .9f, .25f)
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
