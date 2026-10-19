package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo 2026 semantic palette, mapped from the Color Designer edit-ready palette.
// Teal drives actions on charcoal; restrained gold and category hues clarify tools.
// Keep Material and pillar tokens aligned so every route supports light/dark mode.
// ============================================================================

// ---- Light palette: clear violet-on-white hierarchy ----
val LightBackground = Color(0xFFF5F9F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFEDF4F2)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEDF5F3)
val LightSurfaceContainer = Color(0xFFE2ECEA)
val LightSurfaceContainerHigh = Color(0xFFD7E7E3)
val LightSurfaceContainerHighest = Color(0xFFC9DDD8)
val LightOnSurface = Color(0xFF102521)
val LightOnSurfaceVariant = Color(0xFF50635F)
val LightOutline = Color(0xFF677E79)
val LightOutlineVariant = Color(0xFFD3E3DE)
val LightPrimary = Color(0xFF006F63)
val LightPrimaryButton = Color(0xFF006F63)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD0F4EA)
val LightOnPrimaryContainer = Color(0xFF064E44)
val LightPrimaryFixed = Color(0xFFF0ECFF)
val LightPrimaryFixedDim = Color(0xFFDCD4FF)
val LightOnPrimaryFixed = Color(0xFF21114F)
val LightOnPrimaryFixedVariant = Color(0xFF4A2DA2)
val LightSecondary = Color(0xFF2B5C97)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE6F0FF)
val LightOnSecondaryContainer = Color(0xFF173E71)
val LightTertiary = Color(0xFF8A5A12)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFDFF7EF)
val LightOnTertiaryContainer = Color(0xFF075648)
val LightError = Color(0xFFB72E47)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFE8EC)
val LightOnErrorContainer = Color(0xFF7B1630)
val LightCardBorder = Color(0xFFDDD9E9)

// ---- Dark palette: violet on charcoal rather than pure black ----
val DarkBackground = Color(0xFF080E12)
val DarkSurface = Color(0xFF151F25)
val DarkSurfaceDim = Color(0xFF080E12)
val DarkSurfaceBright = Color(0xFF273840)
val DarkSurfaceContainerLowest = Color(0xFF060B0E)
val DarkSurfaceContainerLow = Color(0xFF111B20)
val DarkSurfaceContainer = Color(0xFF19262C)
val DarkSurfaceContainerHigh = Color(0xFF24343B)
val DarkSurfaceContainerHighest = Color(0xFF32474D)
val DarkOnSurface = Color(0xFFF4FBF9)
val DarkOnSurfaceVariant = Color(0xFFB9CFC9)
val DarkOutline = Color(0xFF96ADA6)
val DarkOutlineVariant = Color(0xFF354B4D)
val DarkPrimary = Color(0xFF52E6D1)
val DarkPrimaryButton = Color(0xFF52E6D1)
val DarkOnPrimary = Color(0xFF002E2A)
val DarkPrimaryContainer = Color(0xFF134F48)
val DarkOnPrimaryContainer = Color(0xFFD7FFF6)
val DarkPrimaryFixed = Color(0xFFF0ECFF)
val DarkPrimaryFixedDim = Color(0xFFDCD4FF)
val DarkOnPrimaryFixed = Color(0xFF21114F)
val DarkOnPrimaryFixedVariant = Color(0xFF4A2DA2)
val DarkSecondary = Color(0xFF93BFFF)
val DarkOnSecondary = Color(0xFF062B55)
val DarkSecondaryContainer = Color(0xFF1D3862)
val DarkOnSecondaryContainer = Color(0xFFDEEBFF)
val DarkTertiary = Color(0xFFFFD68B)
val DarkOnTertiary = Color(0xFF003E2C)
val DarkTertiaryContainer = Color(0xFF514027)
val DarkOnTertiaryContainer = Color(0xFFC5FFEE)
val DarkError = Color(0xFFFF9DAA)
val DarkOnError = Color(0xFF611327)
val DarkErrorContainer = Color(0xFF6A2338)
val DarkOnErrorContainer = Color(0xFFFFE4EA)
val DarkCardBorder = Color(0xFF354B4D)

@Immutable
data class PillarColors(
    val pdfPrimary: Color,
    val pdfContainer: Color,
    val pdfOnContainer: Color,
    val pdfSubtitle: Color,
    val imagesPrimary: Color,
    val imagesContainer: Color,
    val imagesOnContainer: Color,
    val imagesSubtitle: Color,
    val downloadsPrimary: Color,
    val downloadsContainer: Color,
    val downloadsOnContainer: Color,
    val downloadsSubtitle: Color,
    val filesPrimary: Color,
    val filesContainer: Color,
    val filesOnContainer: Color,
    val filesSubtitle: Color,
    val vaultPrimary: Color,
    val vaultContainer: Color,
    val successPrimary: Color,
    val successContainer: Color,
    val warningPrimary: Color,
    val warningContainer: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val primaryActionBackground: Color,
    val primaryActionText: Color,
    val dockSurface: Color,
    val dockBorder: Color,
    val activePillContainer: Color,
    val activePillContent: Color
)

val LightPillarColors = PillarColors(
    pdfPrimary = Color(0xFFC43C52),
    pdfContainer = Color(0xFFFFF0F2),
    pdfOnContainer = Color(0xFF872638),
    pdfSubtitle = Color(0xFF8A394A),
    imagesPrimary = Color(0xFFA15A13),
    imagesContainer = Color(0xFFFFF4E7),
    imagesOnContainer = Color(0xFF784010),
    imagesSubtitle = Color(0xFF855019),
    downloadsPrimary = Color(0xFF107664),
    downloadsContainer = Color(0xFFE3F6EF),
    downloadsOnContainer = Color(0xFF075849),
    downloadsSubtitle = Color(0xFF116B58),
    filesPrimary = Color(0xFF2557AA),
    filesContainer = Color(0xFFEAF1FF),
    filesOnContainer = Color(0xFF173C7A),
    filesSubtitle = Color(0xFF285194),
    vaultPrimary = Color(0xFF6C45C0),
    vaultContainer = Color(0xFFF3EDFF),
    successPrimary = Color(0xFF14755F),
    successContainer = Color(0xFFDAF6EA),
    warningPrimary = Color(0xFF98610D),
    warningContainer = Color(0xFFFFF2D9),
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDDD9E9),
    primaryActionBackground = Color(0xFF006F63),
    primaryActionText = Color(0xFFFFFFFF),
    dockSurface = Color(0xF4FFFFFF),
    dockBorder = Color(0x1E0F172A),
    activePillContainer = Color(0xFFD0F4EA),
    activePillContent = Color(0xFF064E44)
)

val DarkPillarColors = PillarColors(
    pdfPrimary = Color(0xFFFFA1AE),
    pdfContainer = Color(0xFF43202F),
    pdfOnContainer = Color(0xFFFFE6EB),
    pdfSubtitle = Color(0xFFFFC5CE),
    imagesPrimary = Color(0xFFFFCD8A),
    imagesContainer = Color(0xFF402C1C),
    imagesOnContainer = Color(0xFFFFF1DE),
    imagesSubtitle = Color(0xFFFFDBA4),
    downloadsPrimary = Color(0xFF75DEC0),
    downloadsContainer = Color(0xFF174B3D),
    downloadsOnContainer = Color(0xFFC5FFEE),
    downloadsSubtitle = Color(0xFF9CECD6),
    filesPrimary = Color(0xFF93BFFF),
    filesContainer = Color(0xFF1D3862),
    filesOnContainer = Color(0xFFDEEBFF),
    filesSubtitle = Color(0xFFBBD6FF),
    vaultPrimary = Color(0xFFC1AAFF),
    vaultContainer = Color(0xFF30234F),
    successPrimary = Color(0xFF75DEC0),
    successContainer = Color(0xFF174B3D),
    warningPrimary = Color(0xFFFFCD8A),
    warningContainer = Color(0xFF402C1C),
    cardSurface = Color(0xFF151F25),
    cardBorder = Color(0xFF354B4D),
    primaryActionBackground = Color(0xFF52E6D1),
    primaryActionText = Color(0xFF002E2A),
    dockSurface = Color(0xF108171D),
    dockBorder = Color(0x28FFFFFF),
    activePillContainer = Color(0xFF134F48),
    activePillContent = Color(0xFFD7FFF6)
)

val LocalPillarColors = staticCompositionLocalOf { LightPillarColors }
