#!/usr/bin/env python3
"""Jumlo UI design-system contract tests (source and contrast checks, not device tests)."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'app/src/main/java/com/jumlo/app/ui'

def source(relative):
    return (UI / relative).read_text(encoding='utf-8')

def luminance(rgb):
    channels = [int(rgb[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in channels]
    return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))

def ratio(a, b):
    light, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (light + .05) / (dark + .05)

class PremiumUxContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tokens = dict(re.findall(r'^val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', source('theme/Color.kt'), flags=re.M))

    def color(self, name):
        self.assertIn(name, self.tokens)
        return self.tokens[name]

    def test_brand_palette_is_consistent_across_light_and_dark(self):
        expected = {
            'LightBackground': 'F7F7FC', 'LightSurface': 'FFFFFF',
            'LightOnSurface': '17152B', 'LightOnSurfaceVariant': '666178',
            'LightPrimary': '5638D8', 'DarkBackground': '11111D',
            'DarkSurface': '201F30', 'DarkOnSurface': 'F6F3FF',
            'DarkPrimary': 'B6A5FF',
        }
        for key, value in expected.items():
            with self.subTest(key=key):
                self.assertEqual(self.color(key).upper(), value)

    def test_semantic_text_pairs_have_wcag_aa_contrast(self):
        pairs = (
            ('LightOnSurface', 'LightBackground'),
            ('LightOnSurface', 'LightSurface'),
            ('LightOnSurfaceVariant', 'LightSurface'),
            ('LightOnPrimary', 'LightPrimary'),
            ('LightOnPrimaryContainer', 'LightPrimaryContainer'),
            ('LightOnSecondary', 'LightSecondary'),
            ('LightOnTertiary', 'LightTertiary'),
            ('LightOnError', 'LightError'),
            ('DarkOnSurface', 'DarkBackground'),
            ('DarkOnSurface', 'DarkSurface'),
            ('DarkOnSurfaceVariant', 'DarkSurface'),
            ('DarkOnPrimary', 'DarkPrimary'),
            ('DarkOnPrimaryContainer', 'DarkPrimaryContainer'),
            ('DarkOnSecondary', 'DarkSecondary'),
            ('DarkOnTertiary', 'DarkTertiary'),
            ('DarkOnError', 'DarkError'),
        )
        for foreground, background in pairs:
            with self.subTest(foreground=foreground, background=background):
                contrast = ratio(self.color(foreground), self.color(background))
                self.assertGreaterEqual(contrast, 4.5, f'{foreground} / {background}: {contrast:.2f}')

    def test_splash_uses_cohesive_palette_family(self):
        splash = source('components/JumloSplash.kt')
        self.assertIn('Color(0xFF31206F)', splash)
        self.assertIn('Color(0xFF5638D8)', splash)
        self.assertIn('Color(0xFFF6F3FF)', splash)
        self.assertNotIn('Color(0xFF2221A1)', splash)

    def test_tool_catalog_adapts_to_tablet_and_fold_safe_width(self):
        code = source('tools/ToolsScreen.kt')
        self.assertIn('BoxWithConstraints(', code)
        self.assertIn('group.chunked(columns)', code)
        self.assertIn('widthIn(max=1000.dp)', code)
        self.assertIn('maxWidth >= 600.dp', code)

    def test_home_and_tools_centered_width_caps_are_applied_before_filling(self):
        # An outer fillMaxSize prevents a later widthIn from constraining the child.
        self.assertIn('Modifier.widthIn(max=960.dp).fillMaxSize()', source('home/HomeScreen.kt'))
        self.assertIn('Modifier.widthIn(max=1000.dp).fillMaxSize()', source('tools/ToolsScreen.kt'))

    def test_home_quick_actions_have_category_specific_icons(self):
        code = source('home/HomeScreen.kt')
        self.assertIn('QuietIconTile(', code)
        self.assertIn('when(tool.pillar)', code)
        self.assertIn('Icons.AutoMirrored.Rounded.ArrowForward', code)

    def test_downloads_offer_contextual_one_tap_pause_and_resume(self):
        code = source('components/ProductionRows.kt')
        self.assertIn('onPause()', code)
        self.assertIn('onRetry()', code)
        self.assertIn('R.string.action_pause', code)
        self.assertIn('R.string.action_resume', code)
        self.assertIn('TextButton(', code[code.index('@Composable fun TransferRow'):])

    def test_search_exposes_label_and_search_ime_action(self):
        code = source('components/QuietButtons.kt')
        self.assertIn('contentDescription = placeholder', code)
        self.assertIn('ImeAction.Search', code)
        self.assertIn('KeyboardOptions(', code)

    def test_settings_shortcuts_do_not_duplicate_haptics_control(self):
        code = source('settings/SettingsScreen.kt')
        hero = code[code.index('item("settings-hero")'):code.index('item("appearance")')]
        self.assertIn('onOpenFiles()', hero)
        self.assertNotIn('panel="haptic-intensity"', hero)
        self.assertIn('panel="haptic-intensity"', code)

    def test_file_import_is_distinct_primary_action(self):
        code = source('files/FilesScreen.kt')
        heading = code[code.index('item("heading")'):code.index('item("search")')]
        self.assertIn('QuietPrimaryButton(', heading)
        self.assertIn('onImport()', heading)
        self.assertIn('onSavedFolder()', heading)

    def test_media_workspace_is_readable_on_large_displays_and_uses_url_keyboard(self):
        code = source('downloads/MediaWorkspace.kt')
        self.assertIn('widthIn(max=840.dp)', code)
        self.assertIn('KeyboardType.Uri', code)
        self.assertIn('ImeAction.Go', code)
        self.assertIn('KeyboardActions(onGo', code)

    def test_output_rows_use_localized_file_types_and_visual_file_icons(self):
        code = source('components/ProductionRows.kt')
        self.assertIn('outputTypeLabel(output.mimeType)', code)
        self.assertIn('icon=outputIcon', code)
        self.assertNotIn('· ${output.mimeType}', code)

    def test_empty_states_have_clear_semantic_heading_and_icon(self):
        code = source('components/QuietStates.kt')
        self.assertIn('Modifier.semantics { heading() }', code)
        self.assertIn('QuietIconTile(', code)

    def test_dock_keeps_screen_reader_labels_without_clipping_large_fonts(self):
        code = source('components/QuietNavigation.kt')
        self.assertIn('fontScale < 1.5f', code)
        self.assertIn('if (showLabel)', code)
        self.assertIn('contentDescription=label', code)

    def test_new_accessibility_strings_have_arabic_and_english(self):
        en = ET.parse(ROOT / 'app/src/main/res/values/premium_ui.xml').getroot()
        ar = ET.parse(ROOT / 'app/src/main/res/values-ar/premium_ui.xml').getroot()
        keys = lambda elements: {el.get('name') for el in elements if el.tag == 'string'}
        self.assertEqual(keys(en), keys(ar))
        self.assertIn('premium_download_tip', keys(en))

if __name__ == '__main__':
    unittest.main(verbosity=2)
