package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo 2026 semantic palette: document grey and scan blue.
//
// The app is a tool for paper, so the surfaces are the greys of a page and the
// one saturated colour is the blue of a scanner's light. Everything else is
// ink, and the category hues stay quiet enough to sit next to it.
//
// The tokens are named for their role, not their colour, so a screen never
// hard-codes a value: Material and pillar tokens stay aligned, and every route
// supports light and dark mode.
// ============================================================================

// ---- Light palette: document grey, ink primary, scan blue accent ----
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFF1F5F9)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF8FAFC)
val LightSurfaceContainer = Color(0xFFF1F5F9)
val LightSurfaceContainerHigh = Color(0xFFE9EEF4)
val LightSurfaceContainerHighest = Color(0xFFE2E8F0)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF475569)
val LightOutline = Color(0xFF94A3B8)
val LightOutlineVariant = Color(0xFFE2E8F0)
val LightPrimary = Color(0xFF1E293B)
val LightPrimaryButton = Color(0xFF1E293B)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE2E8F0)
val LightOnPrimaryContainer = Color(0xFF0F172A)
val LightPrimaryFixed = Color(0xFFE2E8F0)
val LightPrimaryFixedDim = Color(0xFFCBD5E1)
val LightOnPrimaryFixed = Color(0xFF0F172A)
val LightOnPrimaryFixedVariant = Color(0xFF1D4ED8)
val LightSecondary = Color(0xFF334155)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE2E8F0)
val LightOnSecondaryContainer = Color(0xFF1E293B)
val LightTertiary = Color(0xFF1D4ED8)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFDBEAFE)
val LightOnTertiaryContainer = Color(0xFF1E3A8A)
val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF7F1D1D)
val LightCardBorder = Color(0xFFE2E8F0)

// ---- Dark palette: deep slate rather than pure black ----
val DarkBackground = Color(0xFF0B1120)
val DarkSurface = Color(0xFF0F172A)
val DarkSurfaceDim = Color(0xFF0B1120)
val DarkSurfaceBright = Color(0xFF1E293B)
val DarkSurfaceContainerLowest = Color(0xFF020617)
val DarkSurfaceContainerLow = Color(0xFF0F172A)
val DarkSurfaceContainer = Color(0xFF1E293B)
val DarkSurfaceContainerHigh = Color(0xFF334155)
val DarkSurfaceContainerHighest = Color(0xFF475569)
val DarkOnSurface = Color(0xFFF8FAFC)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF64748B)
val DarkOutlineVariant = Color(0xFF1E293B)
val DarkPrimary = Color(0xFFE2E8F0)
val DarkPrimaryButton = Color(0xFFE2E8F0)
val DarkOnPrimary = Color(0xFF0F172A)
val DarkPrimaryContainer = Color(0xFF1E293B)
val DarkOnPrimaryContainer = Color(0xFFF8FAFC)
val DarkPrimaryFixed = Color(0xFF1E293B)
val DarkPrimaryFixedDim = Color(0xFF334155)
val DarkOnPrimaryFixed = Color(0xFFF8FAFC)
val DarkOnPrimaryFixedVariant = Color(0xFF60A5FA)
val DarkSecondary = Color(0xFF94A3B8)
val DarkOnSecondary = Color(0xFF0F172A)
val DarkSecondaryContainer = Color(0xFF1E293B)
val DarkOnSecondaryContainer = Color(0xFFE2E8F0)
val DarkTertiary = Color(0xFF60A5FA)
val DarkOnTertiary = Color(0xFF0B1120)
val DarkTertiaryContainer = Color(0xFF1E3A8A)
val DarkOnTertiaryContainer = Color(0xFFDBEAFE)
val DarkError = Color(0xFFF87171)
val DarkOnError = Color(0xFF450A0A)
val DarkErrorContainer = Color(0xFF7F1D1D)
val DarkOnErrorContainer = Color(0xFFFEE2E2)
val DarkCardBorder = Color(0xFF1E293B)

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
    pdfPrimary = Color(0xFFE11D48),
    pdfContainer = Color(0xFFFFE4E6),
    pdfOnContainer = Color(0xFF881337),
    pdfSubtitle = Color(0xFF9F1239),
    imagesPrimary = Color(0xFFB45309),
    imagesContainer = Color(0xFFFEF3C7),
    imagesOnContainer = Color(0xFF78350F),
    imagesSubtitle = Color(0xFF92400E),
    downloadsPrimary = Color(0xFF0F766E),
    downloadsContainer = Color(0xFFCCFBF1),
    downloadsOnContainer = Color(0xFF115E59),
    downloadsSubtitle = Color(0xFF0F766E),
    filesPrimary = Color(0xFF1D4ED8),
    filesContainer = Color(0xFFDBEAFE),
    filesOnContainer = Color(0xFF1E3A8A),
    filesSubtitle = Color(0xFF1D4ED8),
    vaultPrimary = Color(0xFF6D28D9),
    vaultContainer = Color(0xFFEDE9FE),
    successPrimary = Color(0xFF15803D),
    successContainer = Color(0xFFDCFCE7),
    warningPrimary = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE2E8F0),
    primaryActionBackground = Color(0xFF1E293B),
    primaryActionText = Color(0xFFFFFFFF),
    dockSurface = Color(0xF7FFFFFF),
    dockBorder = Color(0x1A0F172A),
    activePillContainer = Color(0xFFE2E8F0),
    activePillContent = Color(0xFF0F172A)
)

val DarkPillarColors = PillarColors(
    pdfPrimary = Color(0xFFFDA4AF),
    pdfContainer = Color(0xFF4C0519),
    pdfOnContainer = Color(0xFFFFE4E6),
    pdfSubtitle = Color(0xFFFECDD3),
    imagesPrimary = Color(0xFFFCD34D),
    imagesContainer = Color(0xFF451A03),
    imagesOnContainer = Color(0xFFFEF3C7),
    imagesSubtitle = Color(0xFFFDE68A),
    downloadsPrimary = Color(0xFF5EEAD4),
    downloadsContainer = Color(0xFF042F2E),
    downloadsOnContainer = Color(0xFFCCFBF1),
    downloadsSubtitle = Color(0xFF99F6E4),
    filesPrimary = Color(0xFF93C5FD),
    filesContainer = Color(0xFF172554),
    filesOnContainer = Color(0xFFDBEAFE),
    filesSubtitle = Color(0xFFBFDBFE),
    vaultPrimary = Color(0xFFC4B5FD),
    vaultContainer = Color(0xFF2E1065),
    successPrimary = Color(0xFF4ADE80),
    successContainer = Color(0xFF052E16),
    warningPrimary = Color(0xFFFCD34D),
    warningContainer = Color(0xFF451A03),
    cardSurface = Color(0xFF0F172A),
    cardBorder = Color(0xFF1E293B),
    primaryActionBackground = Color(0xFFE2E8F0),
    primaryActionText = Color(0xFF0F172A),
    dockSurface = Color(0xF00F172A),
    dockBorder = Color(0x26FFFFFF),
    activePillContainer = Color(0xFF1E293B),
    activePillContent = Color(0xFFF8FAFC)
)

val LocalPillarColors = staticCompositionLocalOf { LightPillarColors }
