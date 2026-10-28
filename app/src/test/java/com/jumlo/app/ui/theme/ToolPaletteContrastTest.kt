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
        for (seed in listOf("BLUE", "CORAL", "VIOLET")) {
            for (mode in listOf(false to false, true to false, true to true)) {
                val scheme = M3ColorFactory.scheme(M3ColorFactory.seedFor(seed), mode.first, mode.second)
                for ((background, onColor) in listOf(
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondary to scheme.onSecondary,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiary to scheme.onTertiary,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer
                )) assertTrue("AA contrast failed for $seed dark=${mode.first}", M3ColorFactory.meetsAA(background, onColor))
            }
        }
    }
}
