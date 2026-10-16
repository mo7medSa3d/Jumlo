package com.universalutilities.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Quiet Precision — Core Palette Tokens (from DESIGN.md & Stitch Exports)
// ============================================================================

// Light Mode Core Tokens
val LightBackground = Color(0xFFF9F9F8)
val LightSurface = Color(0xFFF9F9F8)
val LightSurfaceDim = Color(0xFFDADAD9)
val LightSurfaceBright = Color(0xFFF9F9F8)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF3F4F3)
val LightSurfaceContainer = Color(0xFFEEEEED)
val LightSurfaceContainerHigh = Color(0xFFE8E8E7)
val LightSurfaceContainerHighest = Color(0xFFE2E2E2)
val LightOnSurface = Color(0xFF1A1C1C)
val LightOnSurfaceVariant = Color(0xFF464557)
val LightOutline = Color(0xFF777588)
val LightOutlineVariant = Color(0xFFC7C4DA)
val LightPrimary = Color(0xFF340DE9)
val LightPrimaryButton = Color(0xFF4F3DFF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFECEAFF)
val LightOnPrimaryContainer = Color(0xFF1B1480)
val LightPrimaryFixed = Color(0xFFE3DFFF)
val LightPrimaryFixedDim = Color(0xFFC3C0FF)
val LightOnPrimaryFixed = Color(0xFF100069)
val LightOnPrimaryFixedVariant = Color(0xFF3000E4)
val LightSecondary = Color(0xFF574DC3)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFF9087FF)
val LightOnSecondaryContainer = Color(0xFF250D92)
val LightTertiary = Color(0xFF0044A8)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFF185BD1)
val LightOnTertiaryContainer = Color(0xFFD5DEFF)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF93000A)
val LightCardBorder = Color(0x0A000000) // rgba(0,0,0,0.04)

// Dark Mode Core Tokens
val DarkBackground = Color(0xFF0B0B0D)
val DarkSurface = Color(0xFF131316)
val DarkSurfaceDim = Color(0xFF0B0B0D)
val DarkSurfaceBright = Color(0xFF25252A)
val DarkSurfaceContainerLowest = Color(0xFF131316)
val DarkSurfaceContainerLow = Color(0xFF17171B)
val DarkSurfaceContainer = Color(0xFF1B1B1F)
val DarkSurfaceContainerHigh = Color(0xFF25252A)
val DarkSurfaceContainerHighest = Color(0xFF2F2F36)
val DarkOnSurface = Color(0xFFF4F4F5)
val DarkOnSurfaceVariant = Color(0xFFA6A7AE)
val DarkOutline = Color(0xFF85868E)
val DarkOutlineVariant = Color(0xFF2F2F36)
val DarkPrimary = Color(0xFF8F86FF)
val DarkPrimaryButton = Color(0xFF8F86FF)
val DarkOnPrimary = Color(0xFF0B0B0D)
val DarkPrimaryContainer = Color(0xFF23205A)
val DarkOnPrimaryContainer = Color(0xFFD9D5FF)
val DarkPrimaryFixed = Color(0xFF23205A)
val DarkPrimaryFixedDim = Color(0xFF342F80)
val DarkOnPrimaryFixed = Color(0xFFE3DFFF)
val DarkOnPrimaryFixedVariant = Color(0xFFC3C0FF)
val DarkSecondary = Color(0xFFC5C0FF)
val DarkOnSecondary = Color(0xFF140067)
val DarkSecondaryContainer = Color(0xFF3F32AA)
val DarkOnSecondaryContainer = Color(0xFFE3DFFF)
val DarkTertiary = Color(0xFFB2C5FF)
val DarkOnTertiary = Color(0xFF001847)
val DarkTertiaryContainer = Color(0xFF0040A0)
val DarkOnTertiaryContainer = Color(0xFFDAE2FF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkCardBorder = Color(0x0FFFFFFF) // rgba(255,255,255,0.06)

// ============================================================================
// Pillar Semantic Colors (PDF, Images, Downloads, Files, Vault, Success/Warn)
// ============================================================================

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
