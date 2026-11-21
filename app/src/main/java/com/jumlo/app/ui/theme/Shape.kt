package com.jumlo.app.ui.theme

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
    val hairline = 4.dp
    val extraSmall = 8.dp
    val badge = 10.dp
    val thumbnail = 12.dp
    val indicator = 14.dp
    val iconTile = 15.dp
    val field = 16.dp
    val surface = 18.dp
    val card = 21.dp
    val hero = 24.dp
    val dock = 999.dp
    val sheet = 28.dp
    val splashIcon = 38.dp
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
 * - Hairline bars & accents: 4dp
 * - Compact badges & chips: 10dp
 * - Thumbnails and tags: 12dp
 * - Active indicators & pills: 14dp
 * - Tool & action icon tiles: 15dp
 * - Inputs, search, and buttons: 16dp
 * - Nested inner surfaces & panels: 18dp
 * - Cards and tiles: 20dp
 * - Hero / spotlight cards & floating navigation dock: 24dp
 * - Sheets and overlays: 28dp top radius
 */
@Immutable
data class QuietPrecisionShapes(
    val bar: RoundedCornerShape = RoundedCornerShape(JumloRadii.hairline),
    val badge: RoundedCornerShape = RoundedCornerShape(JumloRadii.badge),
    val thumbnail: RoundedCornerShape = RoundedCornerShape(JumloRadii.thumbnail),
    val activeIndicatorPill: RoundedCornerShape = RoundedCornerShape(JumloRadii.indicator),
    val iconTile: RoundedCornerShape = RoundedCornerShape(JumloRadii.iconTile),
    val inputField: RoundedCornerShape = RoundedCornerShape(JumloRadii.field),
    val button: RoundedCornerShape = RoundedCornerShape(JumloRadii.field),
    val innerPanel: RoundedCornerShape = RoundedCornerShape(JumloRadii.surface),
    val card: RoundedCornerShape = RoundedCornerShape(JumloRadii.card),
    val heroCard: RoundedCornerShape = RoundedCornerShape(JumloRadii.hero),
    val dockPill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val modalSheetTop: RoundedCornerShape = RoundedCornerShape(topStart = JumloRadii.sheet, topEnd = JumloRadii.sheet),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val sheet: RoundedCornerShape = RoundedCornerShape(JumloRadii.sheet),
    val chip: RoundedCornerShape = RoundedCornerShape(JumloRadii.badge),
    val splashIcon: RoundedCornerShape = RoundedCornerShape(JumloRadii.splashIcon)
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
    val listBottomBuffer: Dp = 104.dp,
    val primaryButtonHeight: Dp = 54.dp,
    val tonalButtonHeight: Dp = 48.dp,
    val fileRowStandardHeight: Dp = 74.dp,
    val fileRowCompactHeight: Dp = 60.dp,
    val touchTarget: Dp = 48.dp
)

/** Icon tiles and avatars use crisp squircles / circles from here. */
val IconTileShape = RoundedCornerShape(JumloRadii.thumbnail)

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(JumloRadii.extraSmall),
    small = RoundedCornerShape(JumloRadii.thumbnail),
    medium = RoundedCornerShape(JumloRadii.field),
    large = RoundedCornerShape(JumloRadii.card),
    extraLarge = RoundedCornerShape(JumloRadii.sheet)
)

val LocalQuietShapes = staticCompositionLocalOf { QuietPrecisionShapes() }
val LocalQuietSpacing = staticCompositionLocalOf { QuietPrecisionSpacing() }
val LocalQuietElevation = staticCompositionLocalOf { QuietPrecisionElevation() }
