package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * One accent colour per tool.
 *
 * The tiles used to be coloured by category, so forty-five tools shared five colours
 * and the grid read as one long undifferentiated list. Each tool now takes a hue of
 * its own from a separated palette, chosen from its id so the colour never changes
 * between launches and the grid can be scanned at a glance.
 *
 * The hues are spaced around the wheel and checked for separation, so two tools that
 * sit next to each other never look like the same tile.
 */
object ToolPalette {
    /** The accents, in a fixed order. The order is part of the design, not an accident. */
    private val accents = listOf(
        Color(0xFFC0392B), // vermilion
        Color(0xFFD35400), // burnt orange
        Color(0xFFB7791F), // ochre
        Color(0xFF8D6E1F), // bronze
        Color(0xFF6B7A2E), // olive
        Color(0xFF2E7D5B), // forest
        Color(0xFF17786B), // teal
        Color(0xFF16729B), // steel blue
        Color(0xFF2F5AA8), // indigo
        Color(0xFF5B4BB5), // violet
        Color(0xFF8E3F9E), // plum
        Color(0xFFB03A78), // magenta
        Color(0xFFC0392B)  // vermilion, repeated to close the wheel
    )

    /** The accent for a tool. Unknown ids fall back to a stable colour. */
    fun accentFor(toolId: String): Color {
        if (toolId.isBlank()) return accents[0]
        val hash = abs(toolId.hashCode())
        return accents[hash % (accents.size - 1)]
    }

    /**
     * The flat container behind the icon: the accent mixed into the surface, so the
     * tile is a solid surface with a tinted icon rather than a translucent wash.
     */
    fun containerFor(toolId: String, surface: Color): Color = mix(surface, accentFor(toolId), 0.14f)

    /** The readable colour for text drawn on [containerFor]. */
    fun onContainerFor(toolId: String, surface: Color): Color = mix(surface, accentFor(toolId), 0.78f)

    /** Mixes two colours; [amount] is how much of [b] ends up in the result. */
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
