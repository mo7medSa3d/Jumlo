package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo Quiet Precision semantic palette.
// Quiet violet primary, warm neutral surfaces, high contrast text.
// Token names are kept stable so every screen picks up the new look.
// ============================================================================

// ---- Light Palette (Clean SaaS / 2026 Expressive) ----
val LightBackground = Color(0xFFFAFAF9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFE5E3E9)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF3F2F6)
val LightSurfaceContainer = Color(0xFFE5E3E9)
val LightSurfaceContainerHigh = Color(0xFFDCD9E3)
val LightSurfaceContainerHighest = Color(0xFFD3CFD9)
val LightOnSurface = Color(0xFF19171F)
val LightOnSurfaceVariant = Color(0xFF585361)
val LightOutline = Color(0xFF827A8F)
val LightOutlineVariant = Color(0xFFE5E3E9)
val LightPrimary = Color(0xFF4F3DFF)
val LightPrimaryButton = Color(0xFF4F3DFF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEEF2FF)
val LightOnPrimaryContainer = Color(0xFF312E81)
val LightPrimaryFixed = Color(0xFFEEF2FF)
val LightPrimaryFixedDim = Color(0xFFC7D2FE)
val LightOnPrimaryFixed = Color(0xFF1E1B4B)
val LightOnPrimaryFixedVariant = Color(0xFF3730A3)
val LightSecondary = Color(0xFF0369A1)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE0F2FE)
val LightOnSecondaryContainer = Color(0xFF0369A1)
val LightTertiary = Color(0xFF0F766E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCCFBF1)
val LightOnTertiaryContainer = Color(0xFF115E59)
val LightError = Color(0xFFDC2626)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF991B1B)
val LightCardBorder = Color(0xFFE5E3E9)

// ---- Dark Palette (Deep Obsidian / Luminous Accents) ----
val DarkBackground = Color(0xFF0B0B0D)
val DarkSurface = Color(0xFF141416)
val DarkSurfaceDim = Color(0xFF0B0B0D)
val DarkSurfaceBright = Color(0xFF1F2937)
val DarkSurfaceContainerLowest = Color(0xFF070A10)
val DarkSurfaceContainerLow = Color(0xFF19191D)
val DarkSurfaceContainer = Color(0xFF202025)
val DarkSurfaceContainerHigh = Color(0xFF29292F)
val DarkSurfaceContainerHighest = Color(0xFF33333B)
val DarkOnSurface = Color(0xFFFAFAF9)
val DarkOnSurfaceVariant = Color(0xFFAAA5B5)
val DarkOutline = Color(0xFF64748B)
val DarkOutlineVariant = Color(0xFF1E293B)
val DarkPrimary = Color(0xFF818CF8)
val DarkPrimaryButton = Color(0xFFB8ADFF)
val DarkOnPrimary = Color(0xFF0B0B0D)
val DarkPrimaryContainer = Color(0xFF2E2D68)
val DarkOnPrimaryContainer = Color(0xFFE0E7FF)
val DarkPrimaryFixed = Color(0xFFEEF2FF)
val DarkPrimaryFixedDim = Color(0xFFC7D2FE)
val DarkOnPrimaryFixed = Color(0xFF1E1B4B)
val DarkOnPrimaryFixedVariant = Color(0xFF3730A3)
val DarkSecondary = Color(0xFF38BDF8)
val DarkOnSecondary = Color(0xFF082F49)
val DarkSecondaryContainer = Color(0xFF0C4A6E)
val DarkOnSecondaryContainer = Color(0xFFBAE6FD)
val DarkTertiary = Color(0xFF2DD4BF)
val DarkOnTertiary = Color(0xFF042F2E)
val DarkTertiaryContainer = Color(0xFF134E4A)
val DarkOnTertiaryContainer = Color(0xFF99F6E4)
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

val LightPillarColors = PillarColors(
    pdfPrimary = Color(0xFFDC2626),
    pdfContainer = Color(0xFFFEF2F2),
    pdfOnContainer = Color(0xFF991B1B),
    pdfSubtitle = Color(0xFF7F1D1D),
    imagesPrimary = Color(0xFFD97706),
    imagesContainer = Color(0xFFFFFBEB),
    imagesOnContainer = Color(0xFF92400E),
    imagesSubtitle = Color(0xFF78350F),
    downloadsPrimary = Color(0xFF059669),
    downloadsContainer = Color(0xFFECFDF5),
    downloadsOnContainer = Color(0xFF065F46),
    downloadsSubtitle = Color(0xFF047857),
    filesPrimary = Color(0xFF2563EB),
    filesContainer = Color(0xFFEFF6FF),
    filesOnContainer = Color(0xFF1E40AF),
    filesSubtitle = Color(0xFF1D4ED8),
    vaultPrimary = Color(0xFF7C3AED),
    vaultContainer = Color(0xFFF5F3FF),
    successPrimary = Color(0xFF059669),
    successContainer = Color(0xFFD1FAE5),
    warningPrimary = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE5E3E9),
    primaryActionBackground = Color(0xFF4F3DFF),
    primaryActionText = Color(0xFFFFFFFF),
    dockSurface = Color(0xF5FFFFFF),
    dockBorder = Color(0x1E0F172A),
    activePillContainer = Color(0xFFEEF2FF),
    activePillContent = Color(0xFF312E81)
)

val DarkPillarColors = PillarColors(
    pdfPrimary = Color(0xFFF87171),
    pdfContainer = Color(0xFF2A1215),
    pdfOnContainer = Color(0xFFFEE2E2),
    pdfSubtitle = Color(0xFFFCA5A5),
    imagesPrimary = Color(0xFFFBBF24),
    imagesContainer = Color(0xFF2E1A08),
    imagesOnContainer = Color(0xFFFEF3C7),
    imagesSubtitle = Color(0xFFFCD34D),
    downloadsPrimary = Color(0xFF34D399),
    downloadsContainer = Color(0xFF062A1F),
    downloadsOnContainer = Color(0xFFD1FAE5),
    downloadsSubtitle = Color(0xFF6EE7B7),
    filesPrimary = Color(0xFF60A5FA),
    filesContainer = Color(0xFF0D2342),
    filesOnContainer = Color(0xFFDBEAFE),
    filesSubtitle = Color(0xFF93C5FD),
    vaultPrimary = Color(0xFFA78BFA),
    vaultContainer = Color(0xFF24153E),
    successPrimary = Color(0xFF34D399),
    successContainer = Color(0xFF062A1F),
    warningPrimary = Color(0xFFFBBF24),
    warningContainer = Color(0xFF2E1A08),
    cardSurface = Color(0xFF141416),
    cardBorder = Color(0xFF1E293B),
    primaryActionBackground = Color(0xFFB8ADFF),
    primaryActionText = Color(0xFF19123E),
    dockSurface = Color(0xF2111827),
    dockBorder = Color(0x28FFFFFF),
    activePillContainer = Color(0xFF2E2D68),
    activePillContent = Color(0xFFE0E7FF)
)

val LocalPillarColors = staticCompositionLocalOf { LightPillarColors }
