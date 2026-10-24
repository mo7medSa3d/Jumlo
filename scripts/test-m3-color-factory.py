#!/usr/bin/env python3
"""Source contracts for the seed-generated Material 3 color system.

Static: asserts the M3ColorFactory derives a full light/dark/AMOLED scheme from
one seed, applies a WCAG AA contrast guard, exposes no hard-coded brand hues, and
is wired into JumloTheme (non-dead). Does not render.
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
        self.assertIn('fun ensureContrast(bg: Color, fg: Color, min: Float = AA_NORMAL): Color', self.factory)
        # WCAG sRGB relative-luminance coefficients.
        for coef in ('0.2126f', '0.7152f', '0.0722f'):
            self.assertIn(coef, self.factory, f'missing luminance coefficient {coef}')
        self.assertIn('AA_NORMAL = 4.5f', self.factory)

    def test_seed_mapping_has_no_hardcoded_brand_hues_in_scheme(self):
        # The scheme derives all roles from the seed; preset names only map to seeds.
        self.assertIn('fun seedFor(accent: String): Color', self.factory)
        self.assertIn('seed.toHSV()', self.factory)
        # Tone roles are derived, not hard-coded per accent.
        self.assertIn('tone(0f', self.factory)
        self.assertIn('tone(30f', self.factory)
        self.assertIn('tone(-30f', self.factory)

    def test_wired_into_theme_non_dead(self):
        self.assertIn('M3ColorFactory.scheme(M3ColorFactory.seedFor(accentColor), dark = darkTheme, amoled = isAmoled)', self.theme)
        # The old hard-coded per-accent when-blocks are gone.
        self.assertNotIn('val selectedAccent = when (accentColor)', self.theme)
        self.assertNotIn('val selectedContainer = when (accentColor)', self.theme)
        # Seed-generated accents are copied onto the stable neutral surface ladder.
        self.assertIn('baseColors.copy(', self.theme)
        self.assertIn('primary = seeded.primary', self.theme)
        self.assertIn('secondary = seeded.secondary', self.theme)
        self.assertIn('tertiary = seeded.tertiary', self.theme)
        self.assertIn('error = seeded.error', self.theme)

    def test_amoled_theme_mode_wired(self):
        models = read('model/Models.kt')
        settings = read('ui/settings/SettingsScreen.kt')
        self.assertIn('AMOLED(', models)
        self.assertIn('ThemeMode.AMOLED -> true', self.theme)
        self.assertIn('val isAmoled = themeMode == ThemeMode.AMOLED', self.theme)
        self.assertIn('amoled = isAmoled', self.theme)
        self.assertIn('ThemeMode.AMOLED -> R.string.pref_theme_amoled', settings)

    def test_amoled_string_defined_both_locales(self):
        en = (ROOT / 'app/src/main/res/values/account_settings.xml').read_text(encoding='utf-8')
        ar = (ROOT / 'app/src/main/res/values-ar/account_settings.xml').read_text(encoding='utf-8')
        self.assertIn('pref_theme_amoled', en)
        self.assertIn('pref_theme_amoled', ar)


if __name__ == '__main__':
    unittest.main()
