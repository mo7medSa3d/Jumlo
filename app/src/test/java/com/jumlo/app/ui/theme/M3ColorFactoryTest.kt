package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

/** Phase 5 unit tests for the seed-generated M3 color system: WCAG contrast,
 *  the AA guard, seed mapping and hex parsing. Pure Compose (no Android runtime). */
class M3ColorFactoryTest {

    @Test fun `contrast of white on black is the WCAG maximum`() {
        assertEquals(21f, M3ColorFactory.contrast(Color.White, Color.Black), 0.01f)
        assertEquals(1f, M3ColorFactory.contrast(Color.White, Color.White), 0.001f)
    }

    @Test fun `contrast matches a published WCAG reference pair`() {
        // White against #777777 is about 4.48:1, slightly below normal-text AA.
        assertEquals(4.48f, M3ColorFactory.contrast(Color.White, Color(0xFF777777)), 0.03f)
        assertFalse(M3ColorFactory.meetsAA(Color.White, Color(0xFF777777)))
        assertTrue(M3ColorFactory.meetsAA(Color.White, Color(0xFF767676)))
    }

    @Test fun `category tile foreground passes AA in both themes`() {
        for (id in listOf("pdf_03", "img_01", "dl_media", "file_07", "xf_01", "scan_doc")) {
            for (dark in listOf(false, true)) {
                val background = ToolPalette.accentFor(id, dark)
                val foreground = ToolPalette.onAccentFor(id, dark)
                assertTrue("$id dark=$dark contrast=" + M3ColorFactory.contrast(background, foreground),
                    M3ColorFactory.contrast(background, foreground) >= 4.5f)
            }
        }
    }

    @Test fun `meetsAA checks the 4_5 threshold`() {
        assertTrue(M3ColorFactory.meetsAA(Color.White, Color.Black))
        assertTrue(M3ColorFactory.meetsAA(Color.White, Color(0xFF555555)))
        assertFalse(M3ColorFactory.meetsAA(Color.White, Color(0xFFDDDDDD)))
    }

    @Test fun `ensureContrast pushes low-contrast text to AA`() {
        val fixed = M3ColorFactory.ensureContrast(Color.White, Color(0xFFDDDDDD))
        assertTrue(M3ColorFactory.contrast(Color.White, fixed) >= 4.5f)
    }

    @Test fun `ensureContrast leaves compliant text untouched`() {
        val mid = Color(0xFF555555)
        val kept = M3ColorFactory.ensureContrast(Color.White, mid)
        assertEquals(mid, kept)
    }

    @Test fun `seedFor maps named presets to their seed`() {
        assertEquals(Color(0xFF00897B), M3ColorFactory.seedFor("TEAL"))
        assertEquals(Color(0xFFE64A3A), M3ColorFactory.seedFor("CORAL"))
        assertEquals(Color(0xFF7B4DFF), M3ColorFactory.seedFor("VIOLET"))
        assertEquals(Color(0xFF1E88E5), M3ColorFactory.seedFor("BLUE"))
    }

    @Test fun `seedFor falls back to blue for unknown names`() {
        assertEquals(Color(0xFF1E88E5), M3ColorFactory.seedFor("unknown"))
    }

    @Test fun `seedFor accepts hex accents directly`() {
        assertEquals(Color(0xFF1E88E5), M3ColorFactory.seedFor("#1E88E5"))
        assertEquals(Color(0xFF1E88E5), M3ColorFactory.seedFor("1E88E5"))
        assertEquals(Color(0xFF7B4DFF), M3ColorFactory.seedFor("#7B4DFF"))
    }

    @Test fun `hexColor parses valid forms and rejects invalid`() {
        assertEquals(Color(0xFF1E88E5), M3ColorFactory.hexColor("#1E88E5"))
        assertEquals(Color(0xFF1E88E5), M3ColorFactory.hexColor("1E88E5"))
        assertNull(M3ColorFactory.hexColor("bad"))
        assertNull(M3ColorFactory.hexColor("#12"))
    }

    @Test fun `isValidAccent accepts presets and hex`() {
        assertTrue(M3ColorFactory.isValidAccent("TEAL"))
        assertTrue(M3ColorFactory.isValidAccent("DYNAMIC"))
        assertTrue(M3ColorFactory.isValidAccent("#1E88E5"))
        assertFalse(M3ColorFactory.isValidAccent("not-a-color"))
    }

    @Test fun `hexString round-trips through hexColor`() {
        assertEquals("#1E88E5", M3ColorFactory.hexString(Color(0xFF1E88E5)))
        assertEquals("#000000", M3ColorFactory.hexString(Color(0xFF000000)))
        val c = M3ColorFactory.hexColor("#7B4DFF")!!
        assertEquals("#7B4DFF", M3ColorFactory.hexString(c))
    }

    @Test fun `scheme generates a light scheme with a seed-derived primary`() {
        val s = M3ColorFactory.scheme(M3ColorFactory.seedFor("TEAL"), dark = false)
        assertNotEquals(Color.Transparent, s.primary)
        assertTrue(s.primary.red > 0f || s.primary.green > 0f || s.primary.blue > 0f)
        assertNotEquals(Color.Transparent, s.secondary)
        assertNotEquals(Color.Transparent, s.tertiary)
        assertNotEquals(Color.Transparent, s.error)
    }

    @Test fun `scheme generates a dark scheme with distinct surfaces`() {
        val light = M3ColorFactory.scheme(M3ColorFactory.seedFor("CORAL"), dark = false)
        val dark = M3ColorFactory.scheme(M3ColorFactory.seedFor("CORAL"), dark = true)
        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.primary, dark.primary)
    }

    @Test fun `amoled scheme uses pure black surfaces`() {
        val amoled = M3ColorFactory.scheme(M3ColorFactory.seedFor("VIOLET"), dark = true, amoled = true)
        assertEquals(Color.Black, amoled.surface)
    }

    @Test fun `hsv round-trip preserves the color`() {
        val seed = Color(0xFF1E88E5)
        val (h, s, v) = M3ColorFactory.hsvOf(seed)
        val rebuilt = M3ColorFactory.fromHsv(h, s, v)
        assertEquals(seed.red, rebuilt.red, 0.01f)
        assertEquals(seed.green, rebuilt.green, 0.01f)
        assertEquals(seed.blue, rebuilt.blue, 0.01f)
    }
}
