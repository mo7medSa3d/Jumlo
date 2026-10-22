package com.jumlo.app.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import com.jumlo.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.min

/**
 * Zoom/pan with real inertia for the page preview.
 *
 * The transform itself uses the framework detector; a parallel observer measures the
 * release velocity and hands it to a frame-synced decay, so a flick keeps moving and
 * settles instead of stopping dead. The gesture layer stays outside the graphics
 * layer, so pan distance always equals finger distance regardless of zoom.
 */
object ZoomPanMath {
    /** Per-second friction. Higher stops sooner. */
    const val FRICTION = 6f
    /** Below this speed (px/s) a fling is over. */
    const val MIN_FLING_SPEED = 12f

    /** Largest pan that still keeps the scaled content covering the viewport. */
    fun maxOffset(scale: Float, viewportWidth: Float, viewportHeight: Float): Offset {
        if (scale <= 1.001f || viewportWidth <= 0f || viewportHeight <= 0f) return Offset.Zero
        return Offset(viewportWidth * (scale - 1f) / 2f, viewportHeight * (scale - 1f) / 2f)
    }

    fun clampOffset(offset: Offset, scale: Float, viewportWidth: Float, viewportHeight: Float): Offset {
        val limit = maxOffset(scale, viewportWidth, viewportHeight)
        return Offset(offset.x.coerceIn(-limit.x, limit.x), offset.y.coerceIn(-limit.y, limit.y))
    }

    /** Scale that fits a content box inside a viewport box, never below 1. */
    fun fitScale(contentWidth: Float, contentHeight: Float, viewportWidth: Float, viewportHeight: Float, maxScale: Float): Float {
        if (contentWidth <= 0f || contentHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) return 1f
        val raw = min(viewportWidth / contentWidth, viewportHeight / contentHeight)
        return raw.coerceIn(1f, maxScale)
    }

    /** Frame-rate independent friction decay. */
    fun decay(velocity: Float, dt: Float): Float = if (dt <= 0f) velocity else velocity * exp(-FRICTION * dt)
}

@Stable
class ZoomPanState internal constructor(
    private val scope: CoroutineScope,
    val minScale: Float,
    val maxScale: Float,
    private val reducedMotion: Boolean,
    val allowRotation: Boolean = false
) {
    var scale by mutableFloatStateOf(minScale)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    /** Two-finger rotation, in degrees. Only used when rotation is allowed. */
    var rotation by mutableFloatStateOf(0f)
        private set

    @Volatile
    private var viewportWidth = 0f

    @Volatile
    private var viewportHeight = 0f

    private var flingJob: Job? = null
    private var scaleJob: Job? = null

    fun onViewportSize(width: Float, height: Float) {
        viewportWidth = width
        viewportHeight = height
        offset = clamp(offset)
    }

    /** Apply one transform step from the gesture detector. Direct manipulation stays 1:1. */
    fun applyTransform(pan: Offset, zoom: Float, rotationDelta: Float = 0f) {
        flingJob?.cancel()
        scaleJob?.cancel()
        if (allowRotation && rotationDelta != 0f) {
            // A page is never turned upside down: the limit keeps it readable.
            rotation = (rotation + rotationDelta).coerceIn(-45f, 45f)
        }
        val next = (scale * zoom).coerceIn(minScale, maxScale)
        scale = next
        offset = clamp(offset + pan)
    }

    fun setZoom(value: Float) {
        flingJob?.cancel()
        scaleJob?.cancel()
        scale = value.coerceIn(minScale, maxScale)
        offset = clamp(offset)
    }

    /** Double-tap behaviour: zoom in to the target, or back out to fit. */
    fun toggleZoom(target: Float) {
        val goal = if (scale > minScale + 0.01f) minScale else target.coerceIn(minScale, maxScale)
        animateScaleTo(goal)
    }

    fun reset() {
        flingJob?.cancel()
        animateScaleTo(minScale)
    }

    private fun animateScaleTo(goal: Float) {
        flingJob?.cancel()
        scaleJob?.cancel()
        if (reducedMotion) {
            setZoom(goal)
            return
        }
        scaleJob = scope.launch {
            val animation = androidx.compose.animation.core.Animatable(scale, Float.VectorConverter)
            animation.animateTo(
                targetValue = goal,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
            ) {
                scale = value
                offset = clamp(offset)
            }
        }
    }

    fun stopFling() {
        flingJob?.cancel()
    }

    /** Continue the pan with inertia until friction brings it to rest. */
    fun fling(velocity: Offset) {
        flingJob?.cancel()
        if (velocity.getDistance() < ZoomPanMath.MIN_FLING_SPEED) return
        flingJob = scope.launch {
            var vx = velocity.x
            var vy = velocity.y
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = now
                offset = clamp(offset + Offset(vx * dt, vy * dt))
                vx = ZoomPanMath.decay(vx, dt)
                vy = ZoomPanMath.decay(vy, dt)
                if (vx * vx + vy * vy < ZoomPanMath.MIN_FLING_SPEED * ZoomPanMath.MIN_FLING_SPEED) break
            }
        }
    }

    private fun clamp(value: Offset) = ZoomPanMath.clampOffset(value, scale, viewportWidth, viewportHeight)
}

@Composable
fun rememberZoomPanState(minScale: Float = 1f, maxScale: Float = 6f, allowRotation: Boolean = false): ZoomPanState {
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    return remember(scope, minScale, maxScale, reduced, allowRotation) {
        ZoomPanState(scope, minScale, maxScale, reduced, allowRotation)
    }
}

/**
 * Attach pinch-zoom, pan, double-tap zoom and flick inertia to a preview.
 *
 * Place this modifier *outside* any graphicsLayer that applies the zoom, so the pan
 * distance stays equal to the finger distance at every zoom level.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.zoomPan(
    state: ZoomPanState,
    key: Any? = Unit,
    doubleTapZoom: Float = 2.5f,
    onDoubleTap: (() -> Unit)? = null
): Modifier {
    val reduced = LocalReducedMotion.current
    return this
        .onSizeChanged { state.onViewportSize(it.width.toFloat(), it.height.toFloat()) }
        .pointerInput(state, key) {
            detectTransformGestures { _, pan, zoom, rotation -> state.applyTransform(pan, zoom, rotation) }
        }
        .pointerInput(state, key, reduced) {
            // Measures how fast the pointer left the surface. Multi-touch gestures never fling.
            awaitPointerEventScope {
                var tracker: VelocityTracker? = null
                var trackedId: androidx.compose.ui.input.pointer.PointerId? = null
                var multiTouch = false
                while (true) {
                    val event = awaitPointerEvent()
                    val down = event.changes.firstOrNull { it.changedToDown() && it.id != trackedId }
                    if (down != null) {
                        tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        trackedId = down.id
                        multiTouch = false
                        continue
                    }
                    val id = trackedId ?: continue
                    val change = event.changes.firstOrNull { it.id == id } ?: continue
                    if (event.changes.count { it.pressed } > 1) multiTouch = true
                    if (!change.pressed) {
                        if (!multiTouch) {
                            val velocity = tracker?.calculateVelocity()
                            if (velocity != null) {
                                if (reduced) state.stopFling() else state.fling(Offset(velocity.x, velocity.y))
                            }
                        }
                        tracker = null
                        trackedId = null
                        continue
                    }
                    if (change.positionChanged()) tracker?.addPosition(change.uptimeMillis, change.position)
                }
            }
        }
        .pointerInput(state, key) {
            detectTapGestures(
                onDoubleTap = { onDoubleTap?.invoke() ?: state.toggleZoom(doubleTapZoom) }
            )
        }
}
