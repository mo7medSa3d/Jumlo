package com.jumlo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo color systems, 2026: the four systems in ColorSystems.kt own every
// semantic role. This file keeps only what is system-independent: the pillar
// color shape, and the settings/account row icons, whose colors are fixed
// per row by an explicit request and never follow the chosen system.
// Never tint document previews or real paper: the chroma belongs to the UI,
// not the file content.
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

val LocalPillarColors = staticCompositionLocalOf { pillarColors(ColorSystems.DEFAULT, false) }

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
