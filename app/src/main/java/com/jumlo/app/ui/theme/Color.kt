package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo 2026 semantic palette, mapped from the Color Designer edit-ready palette.
// Indigo drives actions, cool neutrals provide depth, category accents identify tools.
// Keep Material and pillar tokens aligned so every route supports light/dark mode.
// ============================================================================

// ---- Light palette: clear violet-on-white hierarchy ----
val LightBackground = Color(0xFFF7F7FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFF0EFF8)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF3F2FA)
val LightSurfaceContainer = Color(0xFFEAE8F3)
val LightSurfaceContainerHigh = Color(0xFFE4E1EF)
val LightSurfaceContainerHighest = Color(0xFFDCD8EB)
val LightOnSurface = Color(0xFF17152B)
val LightOnSurfaceVariant = Color(0xFF666178)
val LightOutline = Color(0xFF827B96)
val LightOutlineVariant = Color(0xFFDDD9E9)
val LightPrimary = Color(0xFF5638D8)
val LightPrimaryButton = Color(0xFF5638D8)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE9E3FF)
val LightOnPrimaryContainer = Color(0xFF31206F)
val LightPrimaryFixed = Color(0xFFF0ECFF)
val LightPrimaryFixedDim = Color(0xFFDCD4FF)
val LightOnPrimaryFixed = Color(0xFF21114F)
val LightOnPrimaryFixedVariant = Color(0xFF4A2DA2)
val LightSecondary = Color(0xFF285D9B)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE6F0FF)
val LightOnSecondaryContainer = Color(0xFF173E71)
val LightTertiary = Color(0xFF116F60)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFDFF7EF)
val LightOnTertiaryContainer = Color(0xFF075648)
val LightError = Color(0xFFB72E47)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFE8EC)
val LightOnErrorContainer = Color(0xFF7B1630)
val LightCardBorder = Color(0xFFDDD9E9)

// ---- Dark palette: violet on charcoal rather than pure black ----
val DarkBackground = Color(0xFF11111D)
val DarkSurface = Color(0xFF201F30)
val DarkSurfaceDim = Color(0xFF11111D)
val DarkSurfaceBright = Color(0xFF302F44)
val DarkSurfaceContainerLowest = Color(0xFF0D0C17)
val DarkSurfaceContainerLow = Color(0xFF191828)
val DarkSurfaceContainer = Color(0xFF252439)
val DarkSurfaceContainerHigh = Color(0xFF302E43)
val DarkSurfaceContainerHighest = Color(0xFF3C3952)
val DarkOnSurface = Color(0xFFF6F3FF)
val DarkOnSurfaceVariant = Color(0xFFC2BED2)
val DarkOutline = Color(0xFFA19BB9)
val DarkOutlineVariant = Color(0xFF44425A)
val DarkPrimary = Color(0xFFB6A5FF)
val DarkPrimaryButton = Color(0xFFB6A5FF)
val DarkOnPrimary = Color(0xFF201047)
val DarkPrimaryContainer = Color(0xFF413077)
val DarkOnPrimaryContainer = Color(0xFFF2ECFF)
val DarkPrimaryFixed = Color(0xFFF0ECFF)
val DarkPrimaryFixedDim = Color(0xFFDCD4FF)
val DarkOnPrimaryFixed = Color(0xFF21114F)
val DarkOnPrimaryFixedVariant = Color(0xFF4A2DA2)
val DarkSecondary = Color(0xFF93BFFF)
val DarkOnSecondary = Color(0xFF062B55)
val DarkSecondaryContainer = Color(0xFF1D3862)
val DarkOnSecondaryContainer = Color(0xFFDEEBFF)
val DarkTertiary = Color(0xFF75DEC0)
val DarkOnTertiary = Color(0xFF003E2C)
val DarkTertiaryContainer = Color(0xFF174B3D)
val DarkOnTertiaryContainer = Color(0xFFC5FFEE)
val DarkError = Color(0xFFFF9DAA)
val DarkOnError = Color(0xFF611327)
val DarkErrorContainer = Color(0xFF6A2338)
val DarkOnErrorContainer = Color(0xFFFFE4EA)
val DarkCardBorder = Color(0xFF44425A)

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
    primaryActionBackground = Color(0xFF5638D8),
    primaryActionText = Color(0xFFFFFFFF),
    dockSurface = Color(0xF5FFFFFF),
    dockBorder = Color(0x1E0F172A),
    activePillContainer = Color(0xFFE9E3FF),
    activePillContent = Color(0xFF31206F)
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
    cardSurface = Color(0xFF201F30),
    cardBorder = Color(0xFF44425A),
    primaryActionBackground = Color(0xFFB6A5FF),
    primaryActionText = Color(0xFF201047),
    dockSurface = Color(0xF2111827),
    dockBorder = Color(0x28FFFFFF),
    activePillContainer = Color(0xFF413077),
    activePillContent = Color(0xFFF2ECFF)
)

val LocalPillarColors = staticCompositionLocalOf { LightPillarColors }
