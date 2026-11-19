package com.jumlo.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
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

/** Transform/opacity-only interaction motion tuned to fluid iOS-style spring physics. */
object JumloMotion {
    const val fastMs = 120
    const val standardMs = 220
    const val exitMs = 160
    const val stateChangeMs = 180
    const val loadingPulseMs = 900
    const val pressScale = 0.96f
    const val pressAlpha = 0.93f
    const val revealTravelPx = 18f
    const val staggerMs = 38L
    const val maxStaggered = 12
    const val pressDamping = 0.68f
    const val enterDamping = 0.84f
    const val indicatorDamping = 0.72f
    const val fluidDamping = 0.78f
    const val fluidStiffness = 380f
    const val bouncyDamping = 0.62f
    const val bouncyStiffness = 460f
    const val navDamping = 0.82f
    const val navStiffness = 420f
    const val printScaleDamping = 0.9f

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
        animationSpec = if (reduced) snap() else spring(dampingRatio = JumloMotion.pressDamping, stiffness = Spring.StiffnessHigh),
        label = "press-scale"
    )
    val brightnessState = animateFloatAsState(
        if (pressed) JumloMotion.pressAlpha else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
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
