#!/usr/bin/env python3
"""Settings icons stay off the user accent; tool top bars show subtitle + pillar.

Source-level contracts, not device tests. Settings row icons use fixed
per-row colors from Color.kt, the workspace top bar receives the tool's
benefit subtitle and its own pillar family, and the PDF image codec honors
a maximum-quality choice instead of capping it at 95.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/jumlo/app'


def read(rel):
    return (SRC / rel).read_text(encoding='utf-8')


def luminance(hex_rgb):
    channels = [int(hex_rgb[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    lo, hi = sorted((luminance(a), luminance(b)))
    return (hi + .05) / (lo + .05)


# 'haptics' was removed from the Settings page 2026-11-02 (user request);
# the Color.kt map may still carry its entry, but no row renders it.
SETTINGS_ROWS = ['theme', 'app-color', 'language', 'text-size',
                 'notifications', 'video-quality', 'audio-quality',
                 'storage', 'cache', 'about']
HUB_ROWS = ['my-files', 'storage', 'settings', 'privacy', 'about']


class SettingsIconContracts(unittest.TestCase):
    def test_preference_row_never_uses_the_accent_for_icons(self):
        row = read('ui/settings/PreferenceComponents.kt')
        self.assertIn('iconStyle', row)
        self.assertIn('tint = if (destructive) colors.error else iconStyle.tint', row)
        self.assertIn('container = if (destructive) colors.errorContainer else iconStyle.container', row)
        tile = row.split('QuietIconTile(', 1)[1].split(')', 1)[0]
        self.assertNotIn('colors.primary', tile)

    def test_every_settings_row_passes_its_own_fixed_style(self):
        screen = read('ui/settings/SettingsScreen.kt')
        for key in SETTINGS_ROWS:
            with self.subTest(row=key):
                self.assertIn(f'settingsIconStyleFor("{key}"', screen)

    def test_theme_row_has_its_own_modern_icon_not_the_palette_duplicate(self):
        screen = read('ui/settings/SettingsScreen.kt')
        theme_block = screen.split('item("theme")', 1)[1].split('item("app-color")', 1)[0]
        self.assertIn('Icons.Rounded.DarkMode', theme_block)
        self.assertNotIn('Icons.Rounded.Palette', theme_block)
        app_color_block = screen.split('item("app-color")', 1)[1].split('item("language")', 1)[0]
        self.assertIn('Icons.Rounded.Palette', app_color_block)

    def test_account_hub_rows_pass_fixed_styles_too(self):
        hub = read('ui/components/AccountHubModal.kt')
        for key in HUB_ROWS:
            with self.subTest(row=key):
                self.assertIn(f'settingsIconStyleFor("{key}"', hub)

    def test_settings_icon_styles_cover_every_row_and_stay_readable(self):
        colors = read('ui/theme/Color.kt')
        for mode in ('Light', 'Dark'):
            block = colors.split(f'val {mode}SettingsIconStyles = mapOf(', 1)[1].split('\n)', 1)[0]
            entries = {key: (tint, container) for key, tint, container in re.findall(
                r'"([^"]+)" to SettingsIconStyle\(Color\(0xFF([0-9A-Fa-f]{6})\), Color\(0xFF([0-9A-Fa-f]{6})\)\)',
                block)}
            for key in SETTINGS_ROWS + HUB_ROWS:
                with self.subTest(mode=mode, row=key):
                    self.assertIn(key, entries)
                    tint, container = entries[key][0], entries[key][1]
                    self.assertGreaterEqual(contrast(tint, container), 3.0,
                                            f'{mode} {key}: {tint} on {container}')

    def test_workspace_top_bar_gets_subtitle_and_pillar(self):
        nav = read('ui/navigation/JumloNavHost.kt')
        self.assertIn('private fun subtitle(tool:UtilityTool,language:AppLanguage)', nav)
        call = nav.split('QuietWorkspaceTopBar(', 1)[1]
        self.assertIn('subtitle=if(shellRoute=="workspace")', call)
        self.assertIn('pillar=if(shellRoute=="workspace")', call)

    def test_pdf_image_codec_honors_maximum_quality(self):
        codec = read('data/pdf/AndroidImageCodec.kt')
        self.assertNotIn('coerceIn(10, 95)', codec)
        self.assertIn('coerceIn(10, 100)', codec)


if __name__ == '__main__':
    unittest.main(verbosity=2)
