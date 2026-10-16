package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo "Aurora" palette: a 2026-style tonal system.
// Vivid indigo primary, teal tertiary, soft layered surfaces, high contrast text.
// Token names are kept stable so every screen picks up the new look.
// ============================================================================

// ---- Light ----
val LightBackground = Color(0xFFF6F5FB)
val LightSurface = Color(0xFFF6F5FB)
val LightSurfaceDim = Color(0xFFDCDAE6)
val LightSurfaceBright = Color(0xFFFCFBFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF1F0F8)
val LightSurfaceContainer = Color(0xFFEBEAF4)
val LightSurfaceContainerHigh = Color(0xFFE5E4F0)
val LightSurfaceContainerHighest = Color(0xFFDEDDEA)
val LightOnSurface = Color(0xFF15141F)
val LightOnSurfaceVariant = Color(0xFF57556E)
val LightOutline = Color(0xFF77758F)
val LightOutlineVariant = Color(0xFFD3D1E2)
val LightPrimary = Color(0xFF5B4CF5)
val LightPrimaryButton = Color(0xFF5B4CF5)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE4E0FF)
val LightOnPrimaryContainer = Color(0xFF1A0F7A)
val LightPrimaryFixed = Color(0xFFE4E0FF)
val LightPrimaryFixedDim = Color(0xFFC7BFFF)
val LightOnPrimaryFixed = Color(0xFF13006E)
val LightOnPrimaryFixedVariant = Color(0xFF3C2FB4)
val LightSecondary = Color(0xFF5C5A7A)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE3E1F4)
val LightOnSecondaryContainer = Color(0xFF1B1A33)
val LightTertiary = Color(0xFF00897B)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC8F3EC)
val LightOnTertiaryContainer = Color(0xFF00302A)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightCardBorder = Color(0xFFE6E4F2)

// ---- Dark ----
val DarkBackground = Color(0xFF0D0D14)
val DarkSurface = Color(0xFF0D0D14)
val DarkSurfaceDim = Color(0xFF0D0D14)
val DarkSurfaceBright = Color(0xFF2C2B38)
val DarkSurfaceContainerLowest = Color(0xFF07070C)
val DarkSurfaceContainerLow = Color(0xFF15141D)
val DarkSurfaceContainer = Color(0xFF1A1924)
val DarkSurfaceContainerHigh = Color(0xFF222130)
val DarkSurfaceContainerHighest = Color(0xFF2B2A3A)
val DarkOnSurface = Color(0xFFE6E4F2)
val DarkOnSurfaceVariant = Color(0xFFC5C2D9)
val DarkOutline = Color(0xFF8E8BA6)
val DarkOutlineVariant = Color(0xFF47455A)
val DarkPrimary = Color(0xFFC5BDFF)
val DarkPrimaryButton = Color(0xFF7C6DFF)
val DarkOnPrimary = Color(0xFF2A1B9E)
val DarkPrimaryContainer = Color(0xFF3F31D6)
val DarkOnPrimaryContainer = Color(0xFFE4E0FF)
val DarkPrimaryFixed = Color(0xFFE4E0FF)
val DarkPrimaryFixedDim = Color(0xFFC5BDFF)
val DarkOnPrimaryFixed = Color(0xFF13006E)
val DarkOnPrimaryFixedVariant = Color(0xFF3C2FB4)
val DarkSecondary = Color(0xFFC7C4DD)
val DarkOnSecondary = Color(0xFF2F2E44)
val DarkSecondaryContainer = Color(0xFF45435C)
val DarkOnSecondaryContainer = Color(0xFFE3E1F4)
val DarkTertiary = Color(0xFF5FDCC8)
val DarkOnTertiary = Color(0xFF00382F)
val DarkTertiaryContainer = Color(0xFF00514A)
val DarkOnTertiaryContainer = Color(0xFFC8F3EC)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkCardBorder = Color(0xFF2A2938)

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
    pdfPrimary = Color(0xFFBC2926),
    pdfContainer = Color(0xFFFDEAE7),
    pdfOnContainer = Color(0xFF561413),
    pdfSubtitle = Color(0xFF752D2B),
    imagesPrimary = Color(0xFFAC4C08),
    imagesContainer = Color(0xFFFFF0E4),
    imagesOnContainer = Color(0xFF4E2204),
    imagesSubtitle = Color(0xFF763910),
    downloadsPrimary = Color(0xFF007368),
    downloadsContainer = Color(0xFFE0F5F2),
    downloadsOnContainer = Color(0xFF003833),
    downloadsSubtitle = Color(0xFF005149),
    filesPrimary = Color(0xFF2663D9),
    filesContainer = Color(0xFFE9EFFB),
    filesOnContainer = Color(0xFF0C2B6A),
    filesSubtitle = Color(0xFF1E4A9C),
    vaultPrimary = Color(0xFF4B37FB),
    vaultContainer = Color(0xFFECEAFF),
    successPrimary = Color(0xFF0D7A5F),
    successContainer = Color(0xFFDDF7EF),
    warningPrimary = Color(0xFFB25E00),
    warningContainer = Color(0xFFFFF2DF),
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = LightCardBorder,
    primaryActionBackground = LightPrimaryButton,
    primaryActionText = Color(0xFFFFFFFF),
    dockSurface = Color(0xF5FFFFFF),
    dockBorder = Color(0x140B0B0D),
    activePillContainer = Color(0xFFECEAFF),
    activePillContent = Color(0xFF1B1480)
)

val DarkPillarColors = PillarColors(
    pdfPrimary = Color(0xFFFF7A6E),
    pdfContainer = Color(0xFF331F20),
    pdfOnContainer = Color(0xFFFFDAD6),
    pdfSubtitle = Color(0xFFE5A49D),
    imagesPrimary = Color(0xFFFFB374),
    imagesContainer = Color(0xFF352A20),
    imagesOnContainer = Color(0xFFFFDBC2),
    imagesSubtitle = Color(0xFFDAB08D),
    downloadsPrimary = Color(0xFF2EE6CF),
    downloadsContainer = Color(0xFF15312F),
    downloadsOnContainer = Color(0xFFA8F7EB),
    downloadsSubtitle = Color(0xFF7BC8BD),
    filesPrimary = Color(0xFF88B5FF),
    filesContainer = Color(0xFF1F2A3D),
    filesOnContainer = Color(0xFFDAE2FF),
    filesSubtitle = Color(0xFF9EB8E6),
    vaultPrimary = Color(0xFF8F86FF),
    vaultContainer = Color(0xFF23205A),
    successPrimary = Color(0xFF4CE0B3),
    successContainer = Color(0xFF13332B),
    warningPrimary = Color(0xFFFFB951),
    warningContainer = Color(0xFF362916),
    cardSurface = Color(0xFF131316),
    cardBorder = DarkCardBorder,
    primaryActionBackground = DarkPrimaryButton,
    primaryActionText = Color(0xFF0B0B0D),
    dockSurface = Color(0xF21B1B1F),
    dockBorder = Color(0x14FFFFFF),
    activePillContainer = Color(0xFF23205A),
    activePillContent = Color(0xFFD9D5FF)
)

val LocalPillarColors = staticCompositionLocalOf { LightPillarColors }
