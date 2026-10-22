package com.jumlo.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared physical sizes. Document coordinates deliberately do not use these UI tokens. */
object JumloRadii {
    val thumbnail = 12.dp
    val field = 18.dp
    val card = 24.dp
    val sheet = 30.dp
    val indicator = 21.dp
}

@Immutable
data class QuietPrecisionElevation(
    val flat: Dp = 0.dp,
    val raised: Dp = 2.dp,
    val floating: Dp = 8.dp,
    val overlay: Dp = 14.dp
)

/**
 * Jumlo 2026 shape language: expressive controls without oversized stacked cards.
 * Field names remain stable so all routes share the same hierarchy.
 *
 * - Thumbnails and tags: 12dp
 * - Inputs and search: 18dp
 * - Buttons: 18dp
 * - Cards and tiles: 22dp
 * - Sheets and overlays: 28dp top radius
 * - Floating navigation: 28dp pill
 */
@Immutable
data class QuietPrecisionShapes(
    val thumbnail: RoundedCornerShape = RoundedCornerShape(JumloRadii.thumbnail),
    val inputField: RoundedCornerShape = RoundedCornerShape(JumloRadii.field),
    val button: RoundedCornerShape = RoundedCornerShape(JumloRadii.field),
    val card: RoundedCornerShape = RoundedCornerShape(JumloRadii.card),
    val modalSheetTop: RoundedCornerShape = RoundedCornerShape(topStart = JumloRadii.sheet, topEnd = JumloRadii.sheet),
    val dockPill: RoundedCornerShape = RoundedCornerShape(JumloRadii.sheet),
    val activeIndicatorPill: RoundedCornerShape = RoundedCornerShape(JumloRadii.indicator)
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
    val fileRowCompactHeight: Dp = 60.dp,
    val touchTarget: Dp = 48.dp
)

/** Icon tiles and avatars use full circles / squircles from here. */
val IconTileShape = RoundedCornerShape(14.dp)
val AvatarShape = CircleShape

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalQuietShapes = staticCompositionLocalOf { QuietPrecisionShapes() }
val LocalQuietSpacing = staticCompositionLocalOf { QuietPrecisionSpacing() }
val LocalQuietElevation = staticCompositionLocalOf { QuietPrecisionElevation() }
