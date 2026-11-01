package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tool accents follow the user's chosen color system, never a fixed palette.
 * Each system carries six accents (PDF, Images, Downloads, Files, Organize,
 * Utilities); every accent passes 3.0 against its tile in both modes, and
 * onAccentFor keeps the icon readable through the WCAG contrast guard.
 * Colors are not used as status indicators. Unclassified tools use a
 * deterministic fallback, never a random color at launch.
 */
object ToolPalette {
    fun accentFor(toolId: String, dark: Boolean = false, system: ColorSystem = ColorSystems.DEFAULT): Color {
        val accents = system.tools(dark)
        val index = when {
            toolId == "scan_doc" -> 3
            toolId.startsWith("pdf_") -> 0
            toolId.startsWith("img_") -> 1
            toolId.startsWith("dl_") -> 2
            toolId.startsWith("file_") || toolId == "sha256" -> 3
            toolId.startsWith("xf_") -> 4
            else -> (toolId.hashCode() and Int.MAX_VALUE) % accents.size
        }
        return accents[index]
    }

    /** WCAG-readable foreground for bold, category-colored tool tiles. */
    fun onAccentFor(toolId: String, dark: Boolean = false, system: ColorSystem = ColorSystems.DEFAULT): Color =
        M3ColorFactory.ensureContrast(accentFor(toolId, dark, system), if (dark) Color.Black else Color.White)

    fun containerFor(toolId: String, surface: Color, dark: Boolean = false, system: ColorSystem = ColorSystems.DEFAULT): Color =
        mix(surface, accentFor(toolId, dark, system), 0.20f)

    fun onContainerFor(toolId: String, surface: Color, dark: Boolean = false, system: ColorSystem = ColorSystems.DEFAULT): Color =
        accentFor(toolId, dark, system)

    fun mix(a: Color, b: Color, amount: Float): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue = a.blue + (b.blue - a.blue) * t,
            alpha = 1f
        )
    }
}
