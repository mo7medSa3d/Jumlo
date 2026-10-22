package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo Prism / 2026: quiet, premium document surfaces + expressive indigo.
// All screens consume the same light/dark semantic roles. Never tint document
// previews or real paper: the chroma belongs to the UI, not the file content.
// ============================================================================

// Light: cool porcelain canvas, ink text and confident indigo actions.
val LightBackground = Color(0xFFF6F7FD)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFEFF2FA)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF0F2FC)
val LightSurfaceContainerHigh = Color(0xFFE8EDFA)
val LightSurfaceContainerHighest = Color(0xFFDEE5F6)
val LightOnSurface = Color(0xFF17213E)
val LightOnSurfaceVariant = Color(0xFF505C77)
val LightOutline = Color(0xFF7B88A4)
val LightOutlineVariant = Color(0xFFDCE3F2)
val LightPrimary = Color(0xFF2F46C6)
val LightPrimaryButton = Color(0xFF2F46C6)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE5E9FF)
val LightOnPrimaryContainer = Color(0xFF19266C)
val LightPrimaryFixed = Color(0xFFE5E9FF)
val LightPrimaryFixedDim = Color(0xFFC3CEFF)
val LightOnPrimaryFixed = Color(0xFF16246E)
val LightOnPrimaryFixedVariant = Color(0xFF35469D)
val LightSecondary = Color(0xFF276D77)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDBF5F2)
val LightOnSecondaryContainer = Color(0xFF124F52)
val LightTertiary = Color(0xFF7050AB)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF0E8FC)
val LightOnTertiaryContainer = Color(0xFF38216B)
val LightError = Color(0xFFBA334A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFE5E8)
val LightOnErrorContainer = Color(0xFF781D32)
val LightCardBorder = Color(0xFFDCE3F2)

// Dark: pure black (#000000) for OLED battery savings + neutral gray tonal surfaces with zero blue tint.
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceDim = Color(0xFF000000)
val DarkSurfaceBright = Color(0xFF2C2C2C)
val DarkSurfaceContainerLowest = Color(0xFF000000)
val DarkSurfaceContainerLow = Color(0xFF161616)
val DarkSurfaceContainer = Color(0xFF1E1E1E)
val DarkSurfaceContainerHigh = Color(0xFF262626)
val DarkSurfaceContainerHighest = Color(0xFF303030)
val DarkOnSurface = Color(0xFFF5F5F5)
val DarkOnSurfaceVariant = Color(0xFFC4C4C4)
val DarkOutline = Color(0xFF8E8E8E)
val DarkOutlineVariant = Color(0xFF2E2E2E)
val DarkPrimary = Color(0xFFA9B7FF)
val DarkPrimaryButton = Color(0xFFA9B7FF)
val DarkOnPrimary = Color(0xFF13205A)
val DarkPrimaryContainer = Color(0xFF29376E)
val DarkOnPrimaryContainer = Color(0xFFE5E9FF)
val DarkPrimaryFixed = Color(0xFFE0E5FF)
val DarkPrimaryFixedDim = Color(0xFFB8C4FF)
val DarkOnPrimaryFixed = Color(0xFF111D5A)
val DarkOnPrimaryFixedVariant = Color(0xFF293B91)
val DarkSecondary = Color(0xFF92DCD8)
val DarkOnSecondary = Color(0xFF083C41)
val DarkSecondaryContainer = Color(0xFF164D50)
val DarkOnSecondaryContainer = Color(0xFFDCF9F5)
val DarkTertiary = Color(0xFFD3B7FC)
val DarkOnTertiary = Color(0xFF361D65)
val DarkTertiaryContainer = Color(0xFF4C336D)
val DarkOnTertiaryContainer = Color(0xFFF0E4FF)
val DarkError = Color(0xFFFFB5C1)
val DarkOnError = Color(0xFF690D26)
val DarkErrorContainer = Color(0xFF72273C)
val DarkOnErrorContainer = Color(0xFFFFE1E7)
val DarkCardBorder = Color(0xFF262626)

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

/**
 * The category hues. They are deliberately desaturated next to the scan blue:
 * a category is told apart by its tint, not by its brightness.
 */
val LightPillarColors = PillarColors(
    pdfPrimary = Color(0xFFBC3555),
    pdfContainer = Color(0xFFFFE7EC),
    pdfOnContainer = Color(0xFF7E2340),
    pdfSubtitle = Color(0xFF922744),
    imagesPrimary = Color(0xFFA76218),
    imagesContainer = Color(0xFFFFF1D6),
    imagesOnContainer = Color(0xFF78350F),
    imagesSubtitle = Color(0xFF92400E),
    downloadsPrimary = Color(0xFF087E77),
    downloadsContainer = Color(0xFFDDF8F3),
    downloadsOnContainer = Color(0xFF115E59),
    downloadsSubtitle = Color(0xFF087E77),
    filesPrimary = Color(0xFF2F46C6),
    filesContainer = Color(0xFFE5E9FF),
    filesOnContainer = Color(0xFF19266C),
    filesSubtitle = Color(0xFF2F46C6),
    vaultPrimary = Color(0xFF7152B3),
    vaultContainer = Color(0xFFF0E8FC),
    successPrimary = Color(0xFF15803D),
    successContainer = Color(0xFFDCFCE7),
    warningPrimary = Color(0xFFA76218),
    warningContainer = Color(0xFFFFF1D6),
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDCE3F2),
    primaryActionBackground = Color(0xFF17213E),
    primaryActionText = Color(0xFFFFFFFF),
    dockSurface = Color(0xF7FFFFFF),
    dockBorder = Color(0x1A0F172A),
    activePillContainer = Color(0xFFDCE3F2),
    activePillContent = Color(0xFF17213E)
)

val DarkPillarColors = PillarColors(
    pdfPrimary = Color(0xFFFFB5C1),
    pdfContainer = Color(0xFF4C0519),
    pdfOnContainer = Color(0xFFFFE7EC),
    pdfSubtitle = Color(0xFFFECDD3),
    imagesPrimary = Color(0xFFFCD34D),
    imagesContainer = Color(0xFF451A03),
    imagesOnContainer = Color(0xFFFFF1D6),
    imagesSubtitle = Color(0xFFFDE68A),
    downloadsPrimary = Color(0xFF92DCD8),
    downloadsContainer = Color(0xFF042F2E),
    downloadsOnContainer = Color(0xFFDDF8F3),
    downloadsSubtitle = Color(0xFF99F6E4),
    filesPrimary = Color(0xFF93C5FD),
    filesContainer = Color(0xFF172554),
    filesOnContainer = Color(0xFFE5E9FF),
    filesSubtitle = Color(0xFFBFDBFE),
    vaultPrimary = Color(0xFFC4B5FD),
    vaultContainer = Color(0xFF2E1065),
    successPrimary = Color(0xFF4ADE80),
    successContainer = Color(0xFF052E16),
    warningPrimary = Color(0xFFFCD34D),
    warningContainer = Color(0xFF451A03),
    cardSurface = Color(0xFF161616),
    cardBorder = Color(0xFF262626),
    primaryActionBackground = Color(0xFFDCE3F2),
    primaryActionText = Color(0xFF17213E),
    dockSurface = Color(0xF0161616),
    dockBorder = Color(0x26FFFFFF),
    activePillContainer = Color(0xFF262626),
    activePillContent = Color(0xFFF8FAFC)
)

val LocalPillarColors = staticCompositionLocalOf { LightPillarColors }
