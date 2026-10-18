package com.jumlo.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/** Transform/opacity-only interaction motion. No new animation framework or bitmap allocations. */
object JumloMotion {
    const val pressScale = 0.965f
    const val staggerMs = 44L
    const val maxStaggered = 12
    const val pressDamping = 0.72f
    const val enterDamping = 0.87f
    const val indicatorDamping = 0.76f
}

/** An observer, not a gesture consumer: existing clickable/combinedClickable semantics remain intact. */
fun Modifier.pressFeedback(): Modifier = composed {
    val reduced = LocalReducedMotion.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed && !reduced) JumloMotion.pressScale else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = JumloMotion.pressDamping, stiffness = Spring.StiffnessHigh),
        label = "press-scale"
    )
    val brightness by animateFloatAsState(
        if (pressed) .93f else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "press-highlight"
    )
    this.pointerInput(reduced) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed = true
            try { waitForUpOrCancellation() } finally { pressed = false }
        }
    }.graphicsLayer { scaleX = scale; scaleY = scale; alpha = brightness }
}

/** Lightweight entrance choreography: key by row identity to avoid replay on every recomposition or scroll recycle. */
@Composable
fun StaggeredReveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    var shown by rememberSaveable { mutableStateOf(reduced) }
    LaunchedEffect(reduced) {
        if (!shown) {
            if (!reduced) delay(index.coerceIn(0, JumloMotion.maxStaggered).toLong() * JumloMotion.staggerMs)
            shown = true
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f,
        animationSpec = if (reduced) snap() else spring(stiffness = Spring.StiffnessMedium, dampingRatio = JumloMotion.enterDamping),
        label = "reveal-opacity")
    val travel by animateFloatAsState(if (shown || reduced) 0f else 18f,
        animationSpec = if (reduced) snap() else spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = JumloMotion.enterDamping),
        label = "reveal-position")
    Box(modifier.graphicsLayer { this.alpha = alpha; translationY = travel }, content = { content() })
}
