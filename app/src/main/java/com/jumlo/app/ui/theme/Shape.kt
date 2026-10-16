package com.jumlo.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Jumlo 2026 shape language: generous "expressive" radii, pill controls, and
 * large soft containers. Field names are stable across the app.
 *
 * - Thumbnails and tags: 12dp
 * - Inputs and search: 28dp (pill-leaning)
 * - Buttons: 999dp pill
 * - Cards and tiles: 28dp
 * - Sheets and overlays: 32dp top radius
 * - Floating navigation: 36dp pill
 */
@Immutable
data class QuietPrecisionShapes(
    val thumbnail: RoundedCornerShape = RoundedCornerShape(12.dp),
    val inputField: RoundedCornerShape = RoundedCornerShape(28.dp),
    val button: RoundedCornerShape = RoundedCornerShape(50),
    val card: RoundedCornerShape = RoundedCornerShape(28.dp),
    val modalSheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    val dockPill: RoundedCornerShape = RoundedCornerShape(36.dp),
    val activeIndicatorPill: RoundedCornerShape = RoundedCornerShape(50)
)

@Immutable
data class QuietPrecisionSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val canvasGutterCompact: Dp = 20.dp,
    val canvasGutterMedium: Dp = 28.dp,
    val cardInteriorPadding: Dp = 18.dp,
    val interCardGap: Dp = 12.dp,
    val sectionGap: Dp = 28.dp,
    val dockWidth: Dp = 400.dp,
    val dockHeight: Dp = 72.dp,
    val dockBottomOffset: Dp = 14.dp,
    val listBottomBuffer: Dp = 120.dp,
    val primaryButtonHeight: Dp = 56.dp,
    val tonalButtonHeight: Dp = 48.dp,
    val fileRowStandardHeight: Dp = 76.dp,
    val fileRowCompactHeight: Dp = 60.dp
)

/** Icon tiles and avatars use full circles / squircles from here. */
val IconTileShape = RoundedCornerShape(18.dp)
val AvatarShape = CircleShape

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

val LocalQuietShapes = staticCompositionLocalOf { QuietPrecisionShapes() }
val LocalQuietSpacing = staticCompositionLocalOf { QuietPrecisionSpacing() }
