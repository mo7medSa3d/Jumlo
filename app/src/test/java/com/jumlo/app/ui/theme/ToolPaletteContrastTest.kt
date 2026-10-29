package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/** Color tokens must pass WCAG contrast before an icon or control is shipped. */
class ToolPaletteContrastTest {
    @Test fun everyToolCategoryIconExceedsNonTextContrastInBothThemes() {
        for ((dark, surface) in listOf(false to Color.White, true to Color(0xFF151515))) {
            for (tool in listOf("pdf_03", "img_02", "dl_media", "file_07", "xf_test", "unknown")) {
                val foreground = ToolPalette.accentFor(tool, dark)
                val container = ToolPalette.containerFor(tool, surface, dark)
                assertTrue("$tool dark=$dark insufficient icon contrast: ${M3ColorFactory.contrast(container, foreground)}",
                    M3ColorFactory.contrast(container, foreground) >= 3f)
            }
        }
    }

    @Test fun accentForegroundsMeetAaForLightDarkAndAmoled() {
        for (seed in listOf("BLUE", "CORAL", "VIOLET", "TEAL", "#808080", "#808020", "#00B000", "#CCCCCC")) {
            for (mode in listOf(false to false, true to false, true to true)) {
                val scheme = M3ColorFactory.scheme(M3ColorFactory.seedFor(seed), mode.first, mode.second)
                val roles = listOf(
                    "primary" to (scheme.primary to scheme.onPrimary),
                    "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
                    "secondary" to (scheme.secondary to scheme.onSecondary),
                    "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
                    "tertiary" to (scheme.tertiary to scheme.onTertiary),
                    "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer)
                )
                for ((role, colors) in roles) {
                    val ratio = M3ColorFactory.contrast(colors.first, colors.second)
                    assertTrue("$role for $seed dark=${mode.first} amoled=${mode.second} ratio=$ratio", ratio >= 4.5f)
                }
            }
        }
    }
    @Test fun borderlineBackgroundsAlwaysReachAaWithWorstInitialForegrounds() {
        for (shade in 0..255 step 3) {
            val value = shade / 255f
            val bg = Color(value, value, value, 1f)
            for (initial in listOf(Color.White, Color.Black, Color(0xFF777777))) {
                val adapted = M3ColorFactory.ensureContrast(bg, initial)
                val ratio = M3ColorFactory.contrast(bg, adapted)
                assertTrue("gray=$shade initial=$initial ratio=$ratio", ratio >= 4.5f)
            }
        }
    }

}
