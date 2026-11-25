package com.jumlo.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** CompositionLocals preserved for caller compatibility; icons render in-slot without overlay detachment. */
val LocalToolSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalToolTransitionVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalToolTransitionOrigin = staticCompositionLocalOf { "tools" }

@Composable
fun Modifier.toolSharedElement(toolId: String): Modifier = this
