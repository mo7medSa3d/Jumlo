#!/usr/bin/env python3
"""Source contracts + behavioral port for the Phase 4 Appearance color picker.

Static: M3ColorFactory hex/HSV helpers, 16 bilingual presets, the picker
component, its SettingsScreen wiring, and repository hex-accent acceptance.
Behavioral: a Python mirror validates hex parsing/formatting and accent validation.
"""
import re
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


# --- Behavioral mirror of M3ColorFactory hex helpers ---
def hex_color(accent):
    s = accent.strip().removeprefix("#").removeprefix("0x")
    if len(s) not in (6, 8):
        return None
    if not all(c in '0123456789abcdefABCDEF' for c in s):
        return None
    argb = ("FF" + s) if len(s) == 6 else s
    return int(argb, 16)


def hex_string(argb):
    r = (argb >> 16) & 0xFF
    g = (argb >> 8) & 0xFF
    b = argb & 0xFF
    return "#%02X%02X%02X" % (r, g, b)


def is_valid_accent(value):
    named = {"VIOLET", "BLUE", "TEAL", "CORAL", "DYNAMIC"}
    if value in named:
        return True
    s = value.removeprefix("#")
    return len(s) == 6 and all(c in '0123456789abcdefABCDEF' for c in s)


class ColorPickerStatic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.factory = read('ui/theme/M3ColorFactory.kt')
        cls.presets = read('ui/theme/ColorPresets.kt')
        cls.picker = read('ui/settings/AppearanceColorPicker.kt')
        cls.settings = read('ui/settings/SettingsScreen.kt')
        cls.repo = read('data/preferences/UserPreferencesRepository.kt')

    def test_factory_exposes_hex_and_hsv_helpers(self):
        for sig in ('fun hexColor(accent: String): Color?',
                    'fun hexString(c: Color): String',
                    'fun hsvOf(c: Color): Triple<Float, Float, Float>',
                    'fun fromHsv(h: Float, s: Float, v: Float): Color',
                    'fun isValidAccent(accent: String): Boolean'):
            self.assertIn(sig, self.factory, f'missing: {sig}')

    def test_sixteen_bilingual_presets(self):
        # 16 ColorPreset entries, each with EN/AR names and a hex.
        entries = re.findall(r'ColorPreset\("([^"]+)",\s*"([^"]*)",\s*"([^"]*)",\s*"(#[0-9A-Fa-f]{6})"\)', self.presets)
        self.assertEqual(len(entries), 16, f'expected 16 presets, found {len(entries)}')
        for key, en, ar, hexv in entries:
            self.assertTrue(en, f'preset {key} missing EN name')
            self.assertTrue(ar, f'preset {key} missing AR name')
            self.assertTrue(hexv.startswith('#'))

    def test_presets_hex_unique(self):
        hexes = re.findall(r'ColorPreset\("[^"]+",\s*"[^"]*",\s*"[^"]*",\s*"(#[0-9A-Fa-f]{6})"\)', self.presets)
        self.assertEqual(len(hexes), len(set(hexes)), 'preset hex values must be unique')

    def test_picker_component_exists(self):
        self.assertIn('fun AppearanceColorPicker(', self.picker)
        self.assertIn('ModalBottomSheet', self.picker)
        self.assertIn('M3ColorFactory.scheme(seed, dark = false)', self.picker)
        self.assertIn('ColorPresets.ALL', self.picker)

    def test_wired_into_settings(self):
        self.assertIn('item("app-color")', self.settings)
        self.assertIn('pref_app_color', self.settings)
        self.assertIn('"app-color" -> AppearanceColorPicker(', self.settings)
        self.assertIn('onSelect = { viewModel.setAccentColor(it); panel = null }', self.settings)

    def test_repository_accepts_hex_accents(self):
        self.assertIn('takeIf { isValidAccent(it) }', self.repo)
        self.assertIn('private fun isValidAccent(value: String): Boolean', self.repo)

    def test_app_color_strings_both_locales(self):
        en = (ROOT / 'app/src/main/res/values/account_settings.xml').read_text(encoding='utf-8')
        ar = (ROOT / 'app/src/main/res/values-ar/account_settings.xml').read_text(encoding='utf-8')
        for key in ('pref_app_color', 'pref_app_color_preview', 'pref_app_color_presets',
                    'pref_app_color_hsv', 'pref_app_color_hue', 'pref_app_color_sat',
                    'pref_app_color_val', 'pref_app_color_hex', 'pref_app_color_reset'):
            self.assertIn(key, en, f'EN missing {key}')
            self.assertIn(key, ar, f'AR missing {key}')


class ColorPickerBehavior(unittest.TestCase):
    def test_hex_color_parses_valid_forms(self):
        self.assertEqual(hex_color("#1E88E5"), 0xFF1E88E5)
        self.assertEqual(hex_color("1E88E5"), 0xFF1E88E5)
        self.assertEqual(hex_color("0xFF7B4DFF"), 0xFF7B4DFF)
        self.assertEqual(hex_color("#7B4DFF"), 0xFF7B4DFF)

    def test_hex_color_rejects_invalid(self):
        self.assertIsNone(hex_color("#12"))          # too short
        self.assertIsNone(hex_color("#12345G6"))     # bad char
        self.assertIsNone(hex_color("blue"))         # named, not hex
        self.assertIsNone(hex_color("#123456789"))   # too long

    def test_hex_string_round_trip(self):
        self.assertEqual(hex_string(0xFF1E88E5), "#1E88E5")
        self.assertEqual(hex_string(0xFF000000), "#000000")
        self.assertEqual(hex_string(0xFFFFFFFF), "#FFFFFF")

    def test_is_valid_accent(self):
        self.assertTrue(is_valid_accent("TEAL"))
        self.assertTrue(is_valid_accent("DYNAMIC"))
        self.assertTrue(is_valid_accent("#1E88E5"))
        self.assertTrue(is_valid_accent("1E88E5"))
        self.assertFalse(is_valid_accent("blue"))     # lowercase named not in set
        self.assertFalse(is_valid_accent("#12"))

    def test_hex_round_trip_through_parse_and_format(self):
        for h in ("#1E88E5", "#00897B", "#E64A3A", "#7B4DFF", "#3949AB"):
            parsed = hex_color(h)
            self.assertIsNotNone(parsed)
            self.assertEqual(hex_string(parsed), h.upper())


if __name__ == '__main__':
    unittest.main()
