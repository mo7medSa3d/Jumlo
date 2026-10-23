package com.jumlo.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.jumlo.app.ui.theme.LocalReducedMotion

/** The NavHost owns both scopes so a card icon and its workspace header share real bounds. */
val LocalToolSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalToolTransitionVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalToolTransitionOrigin = staticCompositionLocalOf { "tools" }

@Composable
fun Modifier.toolSharedElement(toolId: String): Modifier {
    val shared = LocalToolSharedTransitionScope.current ?: return this
    val visibility = LocalToolTransitionVisibility.current ?: return this
    val origin = LocalToolTransitionOrigin.current
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@toolSharedElement.sharedElement(
            state = rememberSharedContentState("$origin/tool-icon/$toolId"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ ->
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            }
        )
    }
}
