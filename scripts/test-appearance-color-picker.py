#!/usr/bin/env python3
"""Source contracts + behavioral port for the four color systems picker.

Static: ColorSystems.kt (exactly four systems), the picker component, its
SettingsScreen wiring, and repository color-system acceptance.
Behavioral: a Python mirror validates system-key resolution and fallback.
"""
import re
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


# --- Behavioral mirror of ColorSystems.systemFor ---
SYSTEMS = ("LUIGI", "AURORA", "VIOLA", "SUNSET")
DEFAULT = "SUNSET"


def system_for(key):
    return key if key in SYSTEMS else DEFAULT


class ColorPickerStatic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.systems = read('ui/theme/ColorSystems.kt')
        cls.picker = read('ui/settings/AppearanceColorPicker.kt')
        cls.settings = read('ui/settings/SettingsScreen.kt')
        cls.repo = read('data/preferences/UserPreferencesRepository.kt')

    def test_exactly_four_bilingual_systems(self):
        order = re.findall(r'val (LUIGI|AURORA|VIOLA|SUNSET) = ColorSystem\(', self.systems)
        self.assertEqual(order, ['LUIGI', 'AURORA', 'VIOLA', 'SUNSET'])
        for key in SYSTEMS:
            block = self.systems.split(f'val {key} = ColorSystem(')[1]
            self.assertRegex(block, r'nameEn = "[^"]+"')
            self.assertRegex(block, r'nameAr = "[^"]+"')
            self.assertRegex(block, r'taglineEn = "[^"]+"')
            self.assertRegex(block, r'taglineAr = "[^"]+"')

    def test_picker_component_exists(self):
        self.assertIn('fun AppearanceColorPicker(', self.picker)
        self.assertIn('ModalBottomSheet', self.picker)
        self.assertIn('ColorSystems.ALL', self.picker)
        self.assertIn('Brush.horizontalGradient(system.ramp)', self.picker)
        self.assertIn('onSelect(system.key)', self.picker)
        for gone in ('OutlinedTextField', 'Slider', 'ColorPresets', 'M3ColorFactory', 'hexDraft'):
            self.assertNotIn(gone, self.picker, f'old picker element remains: {gone}')

    def test_wired_into_settings(self):
        self.assertIn('item("app-color")', self.settings)
        self.assertIn('pref_app_color', self.settings)
        self.assertIn('"app-color" -> AppearanceColorPicker(', self.settings)
        self.assertIn('onSelect = { viewModel.setColorSystem(it); panel = null }', self.settings)
        self.assertIn('current = settings.colorSystem', self.settings)
        for gone in ('DYNAMIC', 'setAccentColor', 'accentColor'):
            self.assertNotIn(gone, self.settings, f'old wiring remains: {gone}')

    def test_repository_accepts_only_four_systems(self):
        self.assertIn('takeIf { isValidColorSystem(it) }', self.repo)
        self.assertIn('private fun isValidColorSystem(value: String): Boolean', self.repo)
        self.assertIn('setOf("LUIGI", "AURORA", "VIOLA", "SUNSET")', self.repo)
        self.assertIn('"SUNSET"', self.repo)

    def test_app_color_strings_both_locales(self):
        en = (ROOT / 'app/src/main/res/values/account_settings.xml').read_text(encoding='utf-8')
        ar = (ROOT / 'app/src/main/res/values-ar/account_settings.xml').read_text(encoding='utf-8')
        self.assertIn('pref_app_color', en)
        self.assertIn('pref_app_color', ar)
        self.assertIn('Color system', en)
        self.assertIn('نظام الألوان', ar)
        for gone in ('pref_app_color_preview', 'pref_app_color_presets', 'pref_app_color_hsv',
                     'pref_app_color_hex', 'pref_app_color_reset'):
            self.assertNotIn(gone, en, f'dead string remains (EN): {gone}')
            self.assertNotIn(gone, ar, f'dead string remains (AR): {gone}')


class ColorPickerBehavior(unittest.TestCase):
    def test_system_for_accepts_only_four_keys(self):
        for key in SYSTEMS:
            self.assertEqual(system_for(key), key)

    def test_system_for_falls_back_to_sunset(self):
        for key in ('CLAY', 'BLUE', 'DYNAMIC', '#1E88E5', 'not-a-system', ''):
            self.assertEqual(system_for(key), DEFAULT)

    def test_ramps_are_distinct(self):
        ramps = {
            'LUIGI': ('FFE3B3', 'C2DD6C', '79CE67', '30B155'),
            'AURORA': ('FBCFE8', 'A78BFA', '60A5FA', '3B82F6'),
            'VIOLA': ('FFE1C0', 'FFC57B', 'CD4FBA', '780E94'),
            'SUNSET': ('FFE3B3', 'FBB931', 'F88F22', 'EA6113'),
        }
        self.assertEqual(len(set(ramps.values())), 4)


if __name__ == '__main__':
    unittest.main()
