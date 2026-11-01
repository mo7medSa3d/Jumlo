package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo Clay / 2026: warm cream paper, deep terracotta actions, espresso nights.
// All screens consume the same light/dark semantic roles. Never tint document
// previews or real paper: the chroma belongs to the UI, not the file content.
// The default accent seed is CLAY (terracotta); the full preset list still
// offers the classic blue family for anyone who prefers it.
// ============================================================================

// Light: warm cream surfaces with deep terracotta actions.
val LightBackground = Color(0xFFFBF7F0)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFF1E9DC)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFDFBF6)
val LightSurfaceContainer = Color(0xFFF5EEE2)
val LightSurfaceContainerHigh = Color(0xFFEAE0CF)
val LightSurfaceContainerHighest = Color(0xFFDCCFB9)
val LightOnSurface = Color(0xFF241B12)
val LightOnSurfaceVariant = Color(0xFF6E6155)
val LightOutline = Color(0xFFA39484)
val LightOutlineVariant = Color(0xFFE3D7C3)
val LightPrimary = Color(0xFF9C3D1E)
val LightPrimaryButton = Color(0xFF9C3D1E)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFBE3D3)
val LightOnPrimaryContainer = Color(0xFF5E230D)
val LightPrimaryFixed = Color(0xFFFBE3D3)
val LightPrimaryFixedDim = Color(0xFFF2B48F)
val LightOnPrimaryFixed = Color(0xFF4A1C08)
val LightOnPrimaryFixedVariant = Color(0xFF7A2E12)
val LightSecondary = Color(0xFF7A4A0E)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFBEFD2)
val LightOnSecondaryContainer = Color(0xFF4A2E05)
val LightTertiary = Color(0xFF8E2B4B)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFADCE4)
val LightOnTertiaryContainer = Color(0xFF57152E)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFDE6E0)
val LightOnErrorContainer = Color(0xFF6E150C)
val LightCardBorder = Color(0xFFE3D7C3)

// Espresso: deep warm-brown canvas and a warm elevation ladder. Accent belongs to actions only.
val DarkBackground = Color(0xFF150E09)
val DarkSurface = Color(0xFF1F1712)
val DarkSurfaceDim = Color(0xFF150E09)
val DarkSurfaceBright = Color(0xFF3B2F26)
val DarkSurfaceContainerLowest = Color(0xFF150E09)
val DarkSurfaceContainerLow = Color(0xFF1D1510)
val DarkSurfaceContainer = Color(0xFF2A1E15)
val DarkSurfaceContainerHigh = Color(0xFF36281D)
val DarkSurfaceContainerHighest = Color(0xFF433327)
val DarkOnSurface = Color(0xFFF7F0E6)
val DarkOnSurfaceVariant = Color(0xFFCBBFAE)
val DarkOutline = Color(0xFF8F8172)
val DarkOutlineVariant = Color(0xFF4A3B2E)
val DarkPrimary = Color(0xFFF0A882)
val DarkPrimaryButton = Color(0xFFF0A882)
val DarkOnPrimary = Color(0xFF3D1603)
val DarkPrimaryContainer = Color(0xFF6E2C10)
val DarkOnPrimaryContainer = Color(0xFFFBE3D3)
val DarkPrimaryFixed = Color(0xFFFBE3D3)
val DarkPrimaryFixedDim = Color(0xFFF0A882)
val DarkOnPrimaryFixed = Color(0xFF3D1603)
val DarkOnPrimaryFixedVariant = Color(0xFF7A2E12)
val DarkSecondary = Color(0xFFE4C07A)
val DarkOnSecondary = Color(0xFF3A2600)
val DarkSecondaryContainer = Color(0xFF4A3208)
val DarkOnSecondaryContainer = Color(0xFFFBEFD2)
val DarkTertiary = Color(0xFFF2A8C0)
val DarkOnTertiary = Color(0xFF4A1226)
val DarkTertiaryContainer = Color(0xFF5E1B35)
val DarkOnTertiaryContainer = Color(0xFFFADCE4)
val DarkError = Color(0xFFFFB4A6)
val DarkOnError = Color(0xFF5E0E08)
val DarkErrorContainer = Color(0xFF7A1F16)
val DarkOnErrorContainer = Color(0xFFFDE6E0)
val DarkCardBorder = Color(0xFF4A3B2E)

/** Saturated family tokens — the single palette shared by tool tiles and workspaces.
 * Dark partners are brighter to maintain icon visibility on low-light surfaces. */
val LightToolAccents = listOf(
    Color(0xFFC22733), // PDF · brick red
    Color(0xFFB45309), // Images · dark amber
    Color(0xFF0E7A6B), // Downloads · deep teal
    Color(0xFF6D28D9), // Files · violet
    Color(0xFFBE185D), // Organize · magenta rose
    Color(0xFF4D7C0F)  // Utilities · olive
)
val DarkToolAccents = listOf(
    Color(0xFFFF7A85),
    Color(0xFFFBBF24),
    Color(0xFF5EEAD4),
    Color(0xFFC4B5FD),
    Color(0xFFF9A8D4),
    Color(0xFFA3E635)
)

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
    pdfPrimary = LightToolAccents[0],
    pdfContainer = Color(0xFFFDE5E4),
    pdfOnContainer = Color(0xFF6E1410),
    pdfSubtitle = Color(0xFF9C2226),
    imagesPrimary = LightToolAccents[1],
    imagesContainer = Color(0xFFFDF0D2),
    imagesOnContainer = Color(0xFF4A2E05),
    imagesSubtitle = Color(0xFF8A5A10),
    downloadsPrimary = LightToolAccents[2],
    downloadsContainer = Color(0xFFD8F1EB),
    downloadsOnContainer = Color(0xFF0B4F47),
    downloadsSubtitle = Color(0xFF0E7A6B),
    filesPrimary = LightToolAccents[3],
    filesContainer = Color(0xFFEDE9FE),
    filesOnContainer = Color(0xFF2E1065),
    filesSubtitle = Color(0xFF5B21B6),
    vaultPrimary = Color(0xFFBE185D),
    vaultContainer = Color(0xFFFCE7F3),
    successPrimary = Color(0xFF15803D),
    successContainer = Color(0xFFDCFCE7),
    warningPrimary = Color(0xFF92400E),
    warningContainer = Color(0xFFFEF3C7),
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE3D7C3),
    primaryActionBackground = Color(0xFF241B12),
    primaryActionText = Color(0xFFFFF8F0),
    dockSurface = Color(0xF7FFFDF8),
    dockBorder = Color(0x1A241B12),
    activePillContainer = Color(0xFFE3D7C3),
    activePillContent = Color(0xFF241B12)
)

val DarkPillarColors = PillarColors(
    pdfPrimary = DarkToolAccents[0],
    pdfContainer = Color(0xFF450B08),
    pdfOnContainer = Color(0xFFFDE5E4),
    pdfSubtitle = Color(0xFFFECACA),
    imagesPrimary = DarkToolAccents[1],
    imagesContainer = Color(0xFF451A03),
    imagesOnContainer = Color(0xFFFDF0D2),
    imagesSubtitle = Color(0xFFFDE68A),
    downloadsPrimary = DarkToolAccents[2],
    downloadsContainer = Color(0xFF042F2B),
    downloadsOnContainer = Color(0xFFD8F1EB),
    downloadsSubtitle = Color(0xFF99F6E4),
    filesPrimary = DarkToolAccents[3],
    filesContainer = Color(0xFF2E1065),
    filesOnContainer = Color(0xFFEDE9FE),
    filesSubtitle = Color(0xFFDDD6FE),
    vaultPrimary = Color(0xFFF9A8D4),
    vaultContainer = Color(0xFF500724),
    successPrimary = Color(0xFF4ADE80),
    successContainer = Color(0xFF052E16),
    warningPrimary = Color(0xFFFCD34D),
    warningContainer = Color(0xFF451A03),
    cardSurface = Color(0xFF1D1510),
    cardBorder = Color(0xFF4A3B2E),
    primaryActionBackground = Color(0xFFEDE0D3),
    primaryActionText = Color(0xFF241B12),
    dockSurface = Color(0xFF2A1E15),
    dockBorder = Color(0x26F7F0E6),
    activePillContainer = Color(0xFF433327),
    activePillContent = Color(0xFFF7F0E6)
)

val LocalPillarColors = staticCompositionLocalOf { LightPillarColors }

/**
 * Settings and account rows never follow the user-chosen accent: each row has
 * its own fixed icon color, so changing the app accent never recolors them.
 * Light/dark partners are chosen for >= 3.0 contrast of the icon on its tile.
 */
@Immutable
data class SettingsIconStyle(val tint: Color, val container: Color)

val LightSettingsIconStyles = mapOf(
    "theme" to SettingsIconStyle(Color(0xFF6D28D9), Color(0xFFEDE9FE)),
    "app-color" to SettingsIconStyle(Color(0xFF9C3D1E), Color(0xFFFBE3D3)),
    "language" to SettingsIconStyle(Color(0xFF0E7A6B), Color(0xFFD8F1EB)),
    "haptics" to SettingsIconStyle(Color(0xFFBE185D), Color(0xFFFCE7F3)),
    "text-size" to SettingsIconStyle(Color(0xFFB45309), Color(0xFFFDF0D2)),
    "notifications" to SettingsIconStyle(Color(0xFFC22733), Color(0xFFFDE5E4)),
    "video-quality" to SettingsIconStyle(Color(0xFF2563EB), Color(0xFFDBEAFE)),
    "audio-quality" to SettingsIconStyle(Color(0xFF0E7490), Color(0xFFCFFAFE)),
    "storage" to SettingsIconStyle(Color(0xFF4D7C0F), Color(0xFFEAF0DC)),
    "cache" to SettingsIconStyle(Color(0xFF78716C), Color(0xFFF5F5F4)),
    "about" to SettingsIconStyle(Color(0xFF0369A1), Color(0xFFE0F2FE)),
    "my-files" to SettingsIconStyle(Color(0xFF6D28D9), Color(0xFFEDE9FE)),
    "settings" to SettingsIconStyle(Color(0xFF9C3D1E), Color(0xFFFBE3D3)),
    "privacy" to SettingsIconStyle(Color(0xFF0E7A6B), Color(0xFFD8F1EB)),
    "default" to SettingsIconStyle(Color(0xFF6E6155), Color(0xFFF5EEE2))
)

val DarkSettingsIconStyles = mapOf(
    "theme" to SettingsIconStyle(Color(0xFFC4B5FD), Color(0xFF2E1065)),
    "app-color" to SettingsIconStyle(Color(0xFFF0A882), Color(0xFF6E2C10)),
    "language" to SettingsIconStyle(Color(0xFF5EEAD4), Color(0xFF042F2B)),
    "haptics" to SettingsIconStyle(Color(0xFFF9A8D4), Color(0xFF500724)),
    "text-size" to SettingsIconStyle(Color(0xFFFBBF24), Color(0xFF451A03)),
    "notifications" to SettingsIconStyle(Color(0xFFFF7A85), Color(0xFF450B08)),
    "video-quality" to SettingsIconStyle(Color(0xFF93C5FD), Color(0xFF172554)),
    "audio-quality" to SettingsIconStyle(Color(0xFF67E8F9), Color(0xFF164E63)),
    "storage" to SettingsIconStyle(Color(0xFFA3E635), Color(0xFF1E2A08)),
    "cache" to SettingsIconStyle(Color(0xFFA8A29E), Color(0xFF292524)),
    "about" to SettingsIconStyle(Color(0xFF7DD3FC), Color(0xFF0C4A6E)),
    "my-files" to SettingsIconStyle(Color(0xFFC4B5FD), Color(0xFF2E1065)),
    "settings" to SettingsIconStyle(Color(0xFFF0A882), Color(0xFF6E2C10)),
    "privacy" to SettingsIconStyle(Color(0xFF5EEAD4), Color(0xFF042F2B)),
    "default" to SettingsIconStyle(Color(0xFFCBBFAE), Color(0xFF2A1E15))
)

/** Fixed icon style for a settings/account row; unknown rows get the neutral default. */
fun settingsIconStyleFor(row: String, dark: Boolean): SettingsIconStyle =
    (if (dark) DarkSettingsIconStyles else LightSettingsIconStyles)[row]
        ?: (if (dark) DarkSettingsIconStyles else LightSettingsIconStyles).getValue("default")
