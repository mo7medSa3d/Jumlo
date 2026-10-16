package com.jumlo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Quiet Precision Shape & 4-Unit Grid Spacing System (from DESIGN.md)
 * - Thumbnails & Badges: 8dp radius
 * - Input Fields & Search Bars: 12dp radius
 * - Buttons & Pillar Category Tags: 16dp radius
 * - Content Cards & Action Tiles: 20dp radius
 * - Modal Sheets & Overlays: 28dp top radiuses
 * - Floating Dock & Full Pills: 32dp radius
 */
@Immutable
data class QuietPrecisionShapes(
    val thumbnail: RoundedCornerShape = RoundedCornerShape(8.dp),
    val inputField: RoundedCornerShape = RoundedCornerShape(12.dp),
    val button: RoundedCornerShape = RoundedCornerShape(16.dp),
    val card: RoundedCornerShape = RoundedCornerShape(20.dp),
    val modalSheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val dockPill: RoundedCornerShape = RoundedCornerShape(32.dp),
    val activeIndicatorPill: RoundedCornerShape = RoundedCornerShape(20.dp)
)

@Immutable
data class QuietPrecisionSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val canvasGutterCompact: Dp = 20.dp,
    val canvasGutterMedium: Dp = 24.dp,
    val cardInteriorPadding: Dp = 16.dp,
    val interCardGap: Dp = 12.dp,
    val sectionGap: Dp = 24.dp,
    val dockWidth: Dp = 380.dp,
    val dockHeight: Dp = 80.dp,
    val dockBottomOffset: Dp = 12.dp,
    val listBottomBuffer: Dp = 112.dp,
    val primaryButtonHeight: Dp = 52.dp,
    val tonalButtonHeight: Dp = 44.dp,
    val fileRowStandardHeight: Dp = 68.dp,
    val fileRowCompactHeight: Dp = 56.dp
)

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalQuietShapes = staticCompositionLocalOf { QuietPrecisionShapes() }
val LocalQuietSpacing = staticCompositionLocalOf { QuietPrecisionSpacing() }
