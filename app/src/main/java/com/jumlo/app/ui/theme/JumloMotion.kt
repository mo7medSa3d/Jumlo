package com.jumlo.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/** Transform/opacity-only interaction motion tuned to calm, weighted iOS 27 fluid physics. */
object JumloMotion {
    val iosEase = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    const val fastMs = 240
    const val standardMs = 420
    const val exitMs = 340
    const val stateChangeMs = 360
    const val loadingPulseMs = 1200
    const val pressScale = 0.975f
    const val pressAlpha = 0.95f
    const val revealTravelPx = 12f
    const val staggerMs = 42L
    const val maxStaggered = 10
    const val pressDamping = 0.92f
    const val pressStiffness = 420f
    const val enterDamping = 0.94f
    const val indicatorDamping = 0.90f
    const val fluidDamping = 0.92f
    const val fluidStiffness = 210f
    const val bouncyDamping = 0.88f
    const val bouncyStiffness = 240f
    const val navDamping = 0.96f
    const val navStiffness = 190f
    const val printScaleDamping = 0.94f

    fun <T> fluidSpring(reduced: Boolean): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = fluidDamping, stiffness = fluidStiffness)

    fun <T> bouncySpring(reduced: Boolean): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = bouncyDamping, stiffness = bouncyStiffness)
}

/** An observer, not a gesture consumer: existing clickable/combinedClickable semantics remain intact. */
fun Modifier.pressFeedback(): Modifier = composed {
    val reduced = LocalReducedMotion.current
    var pressed by remember { mutableStateOf(false) }
    val scaleState = animateFloatAsState(
        if (pressed && !reduced) JumloMotion.pressScale else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = JumloMotion.pressDamping, stiffness = JumloMotion.pressStiffness),
        label = "press-scale"
    )
    val brightnessState = animateFloatAsState(
        if (pressed) JumloMotion.pressAlpha else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = JumloMotion.pressStiffness),
        label = "press-highlight"
    )
    this.pointerInput(reduced) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed = true
            try { waitForUpOrCancellation() } finally { pressed = false }
        }
    }.graphicsLayer {
        val s = scaleState.value
        scaleX = s
        scaleY = s
        alpha = brightnessState.value
    }
}
