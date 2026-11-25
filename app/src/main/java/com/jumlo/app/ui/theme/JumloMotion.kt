package com.jumlo.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.unit.IntOffset

/** Centralized iOS 27 spring-physics motion system (transform/opacity-only for 60/120fps). */
object JumloMotion {
    val iosEase = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    const val fastMs = 180
    const val standardMs = 280
    const val exitMs = 220
    const val stateChangeMs = 220
    const val tabCrossfadeMs = 150
    const val navEnterMs = 240
    const val navExitMs = 200
    const val loadingPulseMs = 1200
    const val pressScale = 0.965f
    const val pressAlpha = 0.94f
    const val revealTravelPx = 12f
    const val staggerMs = 36L
    const val maxStaggered = 10
    const val pressDamping = 0.72f
    const val pressStiffness = 560f
    const val enterDamping = 0.84f
    const val indicatorDamping = 0.78f
    const val fluidDamping = 0.84f
    const val fluidStiffness = 380f
    const val bouncyDamping = 0.76f
    const val bouncyStiffness = 440f
    const val navDamping = 0.86f
    const val navStiffness = 420f
    const val navSlideDamping = 0.86f
    const val navSlideStiffness = 420f
    const val printScaleDamping = 0.82f

    private val snapSpecAny: FiniteAnimationSpec<Any> = snap()
    private val fluidSpringSpecAny: FiniteAnimationSpec<Any> =
        spring(dampingRatio = fluidDamping, stiffness = fluidStiffness)
    private val bouncySpringSpecAny: FiniteAnimationSpec<Any> =
        spring(dampingRatio = bouncyDamping, stiffness = bouncyStiffness)
    private val navSpringSpecAny: FiniteAnimationSpec<Any> =
        spring(dampingRatio = navSlideDamping, stiffness = navSlideStiffness)
    private val fadeSpringSpec: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 460f)
    private val tabScaleSpringSpec: FiniteAnimationSpec<Float> =
        spring(dampingRatio = fluidDamping, stiffness = 460f)
    private val modalScaleSpringSpec: FiniteAnimationSpec<Float> =
        spring(dampingRatio = printScaleDamping, stiffness = navSlideStiffness)
    internal val pressScaleSpringSpec: FiniteAnimationSpec<Float> =
        spring(dampingRatio = pressDamping, stiffness = pressStiffness)
    internal val pressAlphaSpringSpec: FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = pressStiffness)

    @Suppress("UNCHECKED_CAST")
    fun <T> fluidSpring(reduced: Boolean): FiniteAnimationSpec<T> =
        (if (reduced) snapSpecAny else fluidSpringSpecAny) as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    fun <T> bouncySpring(reduced: Boolean): FiniteAnimationSpec<T> =
        (if (reduced) snapSpecAny else bouncySpringSpecAny) as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    fun <T> navSpring(reduced: Boolean): FiniteAnimationSpec<T> =
        (if (reduced) snapSpecAny else navSpringSpecAny) as FiniteAnimationSpec<T>

    @Suppress("UNCHECKED_CAST")
    fun fadeSpring(reduced: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) snapSpecAny as FiniteAnimationSpec<Float> else fadeSpringSpec

    fun tabSwitchEnter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(0))
        else fadeIn(tween(tabCrossfadeMs, easing = iosEase)) +
            scaleIn(tabScaleSpringSpec, initialScale = 0.985f)

    fun tabSwitchExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(0))
        else fadeOut(tween(tabCrossfadeMs, easing = iosEase))

    fun screenPushEnter(reduced: Boolean, rtl: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(0))
        else fadeIn(fadeSpring(false)) +
            slideInHorizontally(navSpring<IntOffset>(false)) { (if (rtl) -1 else 1) * it }

    fun screenPushExit(reduced: Boolean, rtl: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(0))
        else fadeOut(fadeSpring(false)) +
            slideOutHorizontally(navSpring<IntOffset>(false)) { (if (rtl) 1 else -1) * it / 4 } +
            scaleOut(navSpring<Float>(false), targetScale = 0.97f)

    fun screenPopEnter(reduced: Boolean, rtl: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(0))
        else fadeIn(fadeSpring(false)) +
            slideInHorizontally(navSpring<IntOffset>(false)) { (if (rtl) 1 else -1) * it / 4 } +
            scaleIn(navSpring<Float>(false), initialScale = 0.97f)

    fun screenPopExit(reduced: Boolean, rtl: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(0))
        else fadeOut(fadeSpring(false)) +
            slideOutHorizontally(navSpring<IntOffset>(false)) { (if (rtl) -1 else 1) * it }

    fun modalEnter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(0))
        else fadeIn(fadeSpring(false)) +
            scaleIn(modalScaleSpringSpec, initialScale = 0.95f)

    fun modalExit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(0))
        else fadeOut(fadeSpring(false)) +
            scaleOut(modalScaleSpringSpec, targetScale = 0.96f)

    fun overlayEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(fadeSpring(false)) + scaleIn(bouncySpring(false), initialScale = 0.94f)

    fun overlayExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None
        else fadeOut(fadeSpring(false)) + scaleOut(fluidSpring(false), targetScale = 0.95f)

    fun topChromeEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(fadeSpring(false)) + slideInVertically(fluidSpring(false)) { -it }

    fun topChromeExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None
        else fadeOut(fadeSpring(false)) + slideOutVertically(fluidSpring(false)) { -it }

    fun bottomChromeEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(fadeSpring(false)) + slideInVertically(fluidSpring(false)) { it }

    fun bottomChromeExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None
        else fadeOut(fadeSpring(false)) + slideOutVertically(fluidSpring(false)) { it }
}

/** An observer, not a gesture consumer: existing clickable/combinedClickable semantics remain intact. */
fun Modifier.pressFeedback(): Modifier = composed {
    val reduced = LocalReducedMotion.current
    var pressed by remember { mutableStateOf(false) }
    val scaleState = animateFloatAsState(
        if (pressed && !reduced) JumloMotion.pressScale else 1f,
        animationSpec = if (reduced) JumloMotion.fadeSpring(true) else JumloMotion.pressScaleSpringSpec,
        label = "press-scale"
    )
    val brightnessState = animateFloatAsState(
        if (pressed) JumloMotion.pressAlpha else 1f,
        animationSpec = if (reduced) JumloMotion.fadeSpring(true) else JumloMotion.pressAlphaSpringSpec,
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
