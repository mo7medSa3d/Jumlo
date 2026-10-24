package com.jumlo.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Generates a Material 3 color scheme from a single seed color.
 *
 * The seed is converted to HSV and mapped to M3 tonal roles (primary, secondary,
 * tertiary and their containers), neutral surfaces/outlines and an error role.
 * Light, Dark and AMOLED (pure-black surfaces) variants share one seed so every
 * accent stays harmonious — no hard-coded brand hues. Foreground on-colors pass a
 * WCAG AA contrast guard; if a seed would fail, tone is adjusted silently toward
 * the readable extreme.
 *
 * This is a dependency-free approximation of Material Color Utilities' HCT mapping.
 * It is the single source of accent color for the app; neutral tonal surfaces stay
 * stable across seeds.
 */
object M3ColorFactory {

    private const val AA_NORMAL = 4.5f
    private const val NEAR_BLACK = 0xFF1A1C1E
    private const val NEAR_WHITE = 0xFFE6E1E5

    /** WCAG relative-luminance contrast ratio between two colors. */
    fun contrast(a: Color, b: Color): Float {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    fun meetsAA(bg: Color, fg: Color): Boolean = contrast(bg, fg) >= AA_NORMAL

    private fun luminance(c: Color): Float {
        val r = c.red.toLinear()
        val g = c.green.toLinear()
        val b = c.blue.toLinear()
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun Float.toLinear(): Float =
        if (this <= 0.03928f) this / 12.92f else ((this + 0.055f) / 1.055f).let { it * it * it }

    /** Pushes `fg` toward the extreme that maximizes contrast with `bg` until AA passes. */
    fun ensureContrast(bg: Color, fg: Color, min: Float = AA_NORMAL): Color {
        if (contrast(bg, fg) >= min) return fg
        val towardWhite = contrast(bg, Color.White) >= contrast(bg, Color.Black)
        val target = if (towardWhite) Color.White else Color.Black
        var current = fg
        var step = 0
        while (contrast(bg, current) < min && step < 24) {
            current = lerp(current, target, 0.15f)
            step++
        }
        return current
    }

    private fun lerp(a: Color, b: Color, t: Float): Color =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)

    /** Seed color for a named accent preset or a hex accent ("#RRGGBB" /
     *  "RRGGBB" / "#AARRGGBB"). Unknown names fall back to BLUE. */
    fun seedFor(accent: String): Color {
        val hex = hexColor(accent)
        if (hex != null) return hex
        return when (accent.uppercase()) {
            "TEAL" -> Color(0xFF00897B)
            "CORAL" -> Color(0xFFE64A3A)
            "VIOLET" -> Color(0xFF7B4DFF)
            else -> Color(0xFF1E88E5)
        }
    }

    /** True when [accent] is a recognized preset name or a valid hex color. */
    fun isValidAccent(accent: String): Boolean =
        hexColor(accent) != null || accent.uppercase() in setOf("TEAL", "CORAL", "VIOLET", "DYNAMIC", "BLUE")

    /** Parses "#RRGGBB", "RRGGBB", or "#AARRGGBB" into a fully-opaque Color. */
    fun hexColor(accent: String): Color? {
        var s = accent.trim().removePrefix("#").removePrefix("0x")
        if (s.length != 6 && s.length != 8) return null
        if (!s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val argb = if (s.length == 6) "FF" + s else s
        return Color(argb.toLong(16))
    }

    /** "#RRGGBB" (uppercase) for a color. */
    fun hexString(c: Color): String {
        val r = (c.red * 255).roundToInt().coerceIn(0, 255)
        val g = (c.green * 255).roundToInt().coerceIn(0, 255)
        val b = (c.blue * 255).roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    /** HSV components, hue in 0..360 and s/v in 0..1. */
    fun hsvOf(c: Color): Triple<Float, Float, Float> = c.toHSV()

    /** A fully-opaque color built from HSV components. */
    fun fromHsv(h: Float, s: Float, v: Float): Color =
        hsv(normalize(h), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

    fun scheme(seed: Color, dark: Boolean, amoled: Boolean = false): ColorScheme {
        val (h, s, _) = seed.toHSV()
        val sat = s.coerceIn(0.25f, 1f)
        fun tone(deg: Float, v: Float) = hsv(normalize(h + deg), sat, v)

        if (!dark) {
            val primary = tone(0f, 0.70f)
            val primaryContainer = tone(0f, 0.90f)
            val secondary = tone(30f, 0.70f)
            val secondaryContainer = tone(30f, 0.90f)
            val tertiary = tone(-30f, 0.70f)
            val tertiaryContainer = tone(-30f, 0.90f)
            val error = hsv(0f, 1f, 0.70f)
            val errorContainer = hsv(0f, 1f, 0.90f)
            return lightColorScheme(
                primary = primary,
                onPrimary = ensureContrast(primary, Color(NEAR_BLACK)),
                primaryContainer = primaryContainer,
                onPrimaryContainer = ensureContrast(primaryContainer, Color(NEAR_BLACK)),
                secondary = secondary,
                onSecondary = ensureContrast(secondary, Color(NEAR_BLACK)),
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = ensureContrast(secondaryContainer, Color(NEAR_BLACK)),
                tertiary = tertiary,
                onTertiary = ensureContrast(tertiary, Color(NEAR_BLACK)),
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = ensureContrast(tertiaryContainer, Color(NEAR_BLACK)),
                error = error,
                onError = ensureContrast(error, Color(NEAR_BLACK)),
                errorContainer = errorContainer,
                onErrorContainer = ensureContrast(errorContainer, Color(NEAR_BLACK)),
                background = Color.White,
                onBackground = Color(NEAR_BLACK),
                surface = Color(0xFFFCFCFD),
                onSurface = Color(NEAR_BLACK),
                surfaceVariant = Color(0xFFE0E2EC),
                onSurfaceVariant = Color(0xFF44474F),
                outline = Color(0xFF74777F),
                outlineVariant = Color(0xFFC4C6D0),
                surfaceTint = Color.Transparent
            )
        }
        val surface = if (amoled) Color.Black else Color(0xFF121316)
        val primary = tone(0f, 0.45f)
        val primaryContainer = tone(0f, 0.62f)
        val secondary = tone(30f, 0.45f)
        val secondaryContainer = tone(30f, 0.62f)
        val tertiary = tone(-30f, 0.45f)
        val tertiaryContainer = tone(-30f, 0.62f)
        val error = hsv(0f, 1f, 0.55f)
        val errorContainer = hsv(0f, 1f, 0.35f)
        return darkColorScheme(
            primary = primary,
            onPrimary = ensureContrast(primary, Color.White),
            primaryContainer = primaryContainer,
            onPrimaryContainer = ensureContrast(primaryContainer, Color(NEAR_BLACK)),
            secondary = secondary,
            onSecondary = ensureContrast(secondary, Color.White),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = ensureContrast(secondaryContainer, Color(NEAR_BLACK)),
            tertiary = tertiary,
            onTertiary = ensureContrast(tertiary, Color.White),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = ensureContrast(tertiaryContainer, Color(NEAR_BLACK)),
            error = error,
            onError = ensureContrast(error, Color.White),
            errorContainer = errorContainer,
            onErrorContainer = ensureContrast(errorContainer, Color(0xFFFFDAD6)),
            background = surface,
            onBackground = Color(NEAR_WHITE),
            surface = surface,
            onSurface = Color(NEAR_WHITE),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF63606A),
            surfaceTint = Color.Transparent
        )
    }

    private fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    private fun Color.toHSV(): Triple<Float, Float, Float> {
        val r = red
        val g = green
        val b = blue
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        var h = 0f
        if (d != 0f) {
            h = when (mx) {
                r -> ((g - b) / d) % 6f
                g -> ((b - r) / d) + 2f
                else -> ((r - g) / d) + 4f
            } * 60f
            if (h < 0f) h += 360f
        }
        val s = if (mx == 0f) 0f else d / mx
        return Triple(h, s, mx)
    }

    private fun hsv(h: Float, s: Float, v: Float): Color {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }
}
