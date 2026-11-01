#!/usr/bin/env python3
"""Source contracts for the M3ColorFactory contrast utility.

Static: asserts the factory still derives light/dark/AMOLED schemes, applies a
WCAG AA contrast guard, exposes no hard-coded brand hues, and stays live as the
contrast utility used by ToolPalette — while JumloTheme itself now builds its
scheme from the four color systems, not from a seed. Does not render.
"""
import re
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


class M3ColorFactoryContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.factory = read('ui/theme/M3ColorFactory.kt')
        cls.theme = read('ui/theme/Theme.kt')

    def test_factory_is_an_object_with_scheme_factory(self):
        self.assertIn('object M3ColorFactory', self.factory)
        self.assertRegex(self.factory, r'fun scheme\(seed: Color, dark: Boolean, amoled: Boolean = false\): ColorScheme')

    def test_generates_light_dark_and_amoled_variants(self):
        self.assertIn('lightColorScheme(', self.factory)
        self.assertIn('darkColorScheme(', self.factory)
        # AMOLED derives from the dark path with pure-black surfaces.
        self.assertIn('amoled', self.factory)
        self.assertIn('Color.Black', self.factory)

    def test_contrast_guard_uses_wcag_relative_luminance(self):
        self.assertIn('fun contrast(a: Color, b: Color): Float', self.factory)
        self.assertIn('fun ensureContrast(bg: Color, fg: Color, min: Float = AA_NORMAL + AA_RENDER_MARGIN): Color', self.factory)
        # WCAG sRGB relative-luminance coefficients.
        for coef in ('0.2126f', '0.7152f', '0.0722f'):
            self.assertIn(coef, self.factory, f'missing luminance coefficient {coef}')
        self.assertIn('AA_NORMAL = 4.5f', self.factory)

    def test_seed_mapping_has_no_hardcoded_brand_hues_in_scheme(self):
        # The scheme derives all roles from the seed; seedFor is hex-only now
        # (preset names fall back to BLUE) and no UI code seeds a theme from it.
        self.assertIn('fun seedFor(accent: String): Color', self.factory)
        self.assertIn('seed.toHSV()', self.factory)
        # Tone roles are derived, not hard-coded per accent.
        self.assertIn('tone(0f', self.factory)
        self.assertIn('tone(30f', self.factory)
        self.assertIn('tone(-30f', self.factory)

    def test_contrast_utility_is_live_and_theme_uses_color_systems(self):
        # Theme builds its scheme from the chosen color system, never a seed.
        self.assertIn('ColorSystems.systemFor(colorSystem)', self.theme)
        self.assertIn('colorSchemeFor(scheme, darkTheme)', self.theme)
        self.assertNotIn('M3ColorFactory', self.theme)
        self.assertNotIn('accentColor', self.theme)
        # The factory survives as the contrast utility behind tool icons.
        palette = read('ui/theme/ToolPalette.kt')
        self.assertIn('M3ColorFactory.ensureContrast(', palette)

    def test_amoled_theme_mode_wired(self):
        models = read('model/Models.kt')
        settings = read('ui/settings/SettingsScreen.kt')
        self.assertIn('AMOLED(', models)
        self.assertIn('ThemeMode.AMOLED -> true', self.theme)
        self.assertIn('val isAmoled = themeMode == ThemeMode.AMOLED', self.theme)
        self.assertIn('if (isAmoled)', self.theme)
        self.assertIn('background = Color.Black', self.theme)
        self.assertIn('surface = Color.Black', self.theme)
        self.assertIn('ThemeMode.AMOLED -> R.string.pref_theme_amoled', settings)

    def test_amoled_string_defined_both_locales(self):
        en = (ROOT / 'app/src/main/res/values/account_settings.xml').read_text(encoding='utf-8')
        ar = (ROOT / 'app/src/main/res/values-ar/account_settings.xml').read_text(encoding='utf-8')
        self.assertIn('pref_theme_amoled', en)
        self.assertIn('pref_theme_amoled', ar)


if __name__ == '__main__':
    unittest.main()
