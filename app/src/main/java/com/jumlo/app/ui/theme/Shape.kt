package com.jumlo.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared physical sizes. Document coordinates deliberately do not use these UI tokens.
 *  2026-11-01 refresh: softer, larger radii across every screen — cards, fields and
 *  thumbnails all read from here, so this single change restyles Home, Tools, Files,
 *  Settings, workspaces, viewer and print together. */
object JumloRadii {
    val thumbnail = 12.dp
    val field = 16.dp
    val card = 20.dp
    val sheet = 28.dp
    val indicator = 14.dp
}

@Immutable
data class QuietPrecisionElevation(
    val flat: Dp = 0.dp,
    val raised: Dp = 2.dp,
    val floating: Dp = 10.dp,
    val overlay: Dp = 16.dp
)

/**
 * Jumlo Studio shape language: crisp architectural radii with disciplined hierarchy.
 *
 * - Thumbnails and tags: 12dp
 * - Inputs, search, and buttons: 16dp
 * - Cards and tiles: 20dp
 * - Sheets and overlays: 28dp top radius
 * - Floating navigation dock: 24dp rounded pill
 */
@Immutable
data class QuietPrecisionShapes(
    val thumbnail: RoundedCornerShape = RoundedCornerShape(JumloRadii.thumbnail),
    val inputField: RoundedCornerShape = RoundedCornerShape(JumloRadii.field),
    val button: RoundedCornerShape = RoundedCornerShape(JumloRadii.field),
    val card: RoundedCornerShape = RoundedCornerShape(JumloRadii.card),
    val modalSheetTop: RoundedCornerShape = RoundedCornerShape(topStart = JumloRadii.sheet, topEnd = JumloRadii.sheet),
    val dockPill: RoundedCornerShape = RoundedCornerShape(24.dp),
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
    val dockHeight: Dp = 70.dp,
    val dockBottomOffset: Dp = 12.dp,
    val listBottomBuffer: Dp = 36.dp,
    val primaryButtonHeight: Dp = 54.dp,
    val tonalButtonHeight: Dp = 48.dp,
    val fileRowStandardHeight: Dp = 74.dp,
    val fileRowCompactHeight: Dp = 60.dp,
    val touchTarget: Dp = 48.dp
)

/** Icon tiles and avatars use crisp squircles / circles from here. */
val IconTileShape = RoundedCornerShape(12.dp)
val AvatarShape = CircleShape

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalQuietShapes = staticCompositionLocalOf { QuietPrecisionShapes() }
val LocalQuietSpacing = staticCompositionLocalOf { QuietPrecisionSpacing() }
val LocalQuietElevation = staticCompositionLocalOf { QuietPrecisionElevation() }
