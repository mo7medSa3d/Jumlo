package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated, limited tool accents. Colors are not used as status indicators.
 * Each real workspace uses a stable family color, while labels/icons identify the action.
 * Unclassified tools use a deterministic fallback, never a random color at launch.
 * Every family has a brighter dark-mode partner to keep the icon discernible.
 */
object ToolPalette {
    // One source of truth: values are the semantic tokens declared in Color.kt.
    private val lightAccents = listOf(
        LightPillarColors.pdfPrimary,
        LightPillarColors.imagesPrimary,
        LightPillarColors.downloadsPrimary,
        LightPillarColors.filesPrimary,
        LightPillarColors.vaultPrimary,
        LightSecondary
    )
    private val darkAccents = listOf(
        DarkPillarColors.pdfPrimary,
        DarkPillarColors.imagesPrimary,
        DarkPillarColors.downloadsPrimary,
        DarkPillarColors.filesPrimary,
        DarkPillarColors.vaultPrimary,
        DarkSecondary
    )

    fun accentFor(toolId: String, dark: Boolean = false): Color {
        val index = when {
            toolId == "scan_doc" -> 3
            toolId.startsWith("pdf_") -> 0
            toolId.startsWith("img_") -> 1
            toolId.startsWith("dl_") -> 2
            toolId.startsWith("file_") || toolId == "sha256" -> 3
            toolId.startsWith("xf_") -> 4
            else -> (toolId.hashCode() and Int.MAX_VALUE) % lightAccents.size
        }
        return if (dark) darkAccents[index] else lightAccents[index]
    }

    /** Foreground is WCAG AA-readable against the saturated tile background. */
    fun onAccentFor(toolId: String, dark: Boolean = false): Color =
        M3ColorFactory.ensureContrast(accentFor(toolId, dark), if (dark) Color.Black else Color.White)

    fun containerFor(toolId: String, surface: Color, dark: Boolean = false): Color =
        mix(surface, accentFor(toolId, dark), if (dark) 0.17f else 0.12f)

    fun onContainerFor(toolId: String, surface: Color, dark: Boolean = false): Color =
        accentFor(toolId, dark)

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
