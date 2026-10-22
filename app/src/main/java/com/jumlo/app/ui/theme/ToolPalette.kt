package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Curated, limited tool accents. Colors are not used as status indicators.
 * Each real workspace uses a stable family color, while labels/icons identify the action.
 * Unclassified tools use a deterministic fallback, never a random color at launch.
 * Every family has a brighter dark-mode partner to keep the icon discernible.
 */
object ToolPalette {
    private val lightAccents = listOf(
        Color(0xFFB63557), // rose: PDF
        Color(0xFFAC641D), // amber: images
        Color(0xFF087A73), // lagoon: transfers
        Color(0xFF2F46C6), // indigo: documents
        Color(0xFF7150A9), // iris: organizing
        Color(0xFF326E99)  // ocean: utilities
    )
    private val darkAccents = listOf(
        Color(0xFFFF9DB3),
        Color(0xFFFFCE88),
        Color(0xFF82DED2),
        Color(0xFFACB9FF),
        Color(0xFFD3B7FC),
        Color(0xFF9CCDF0)
    )

    fun accentFor(toolId: String, dark: Boolean = false): Color {
        val index = when {
            toolId == "scan_doc" -> 3
            toolId.startsWith("pdf_") || toolId == "webpage_pdf" -> 0
            toolId.startsWith("img_") -> 1
            toolId.startsWith("dl_") -> 2
            toolId.startsWith("file_") || toolId == "sha256" -> 3
            toolId.startsWith("xf_") -> 4
            else -> (toolId.hashCode() and Int.MAX_VALUE) % lightAccents.size
        }
        return if (dark) darkAccents[index] else lightAccents[index]
    }

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
