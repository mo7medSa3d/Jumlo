#!/usr/bin/env python3
"""Source contracts + behavioral port for the tap-drag-done workspace.

Static: the no-typing kit exists and WorkspaceFields uses it where the visual
editor is the primary path, so no tool flow requires keystrokes.
Behavioral: a Python mirror validates the page-count summary and the password
generator's class guarantees.
"""
import random
import re
import string
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


def xml_strings(locale):
    text = (ROOT / f'app/src/main/res/{locale}/production_ui.xml').read_text(encoding='utf-8')
    text += (ROOT / f'app/src/main/res/{locale}/watermark_2026.xml').read_text(encoding='utf-8')
    text += (ROOT / f'app/src/main/res/{locale}/studio.xml').read_text(encoding='utf-8')
    return text


# --- Behavioral mirrors ---
def count_selected_pages(spec):
    s = spec.strip()
    if not s or s.lower() == 'all':
        return None
    count = 0
    for part in s.split(','):
        p = part.strip()
        if not p:
            continue
        rng = p.split('-')
        if len(rng) == 1:
            if not rng[0].isdigit():
                return -1
            count += 1
        elif len(rng) == 2:
            if not rng[0].strip().isdigit() or not rng[1].strip().isdigit():
                return -1
            a, b = int(rng[0]), int(rng[1])
            if b < a:
                return -1
            count += b - a + 1
        else:
            return -1
    return count


def generate_strong_password():
    upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ'
    lower = 'abcdefghjkmnpqrstuvwxyz'
    digits = '23456789'
    symbols = '!@#$%^&*'
    all_chars = upper + lower + digits + symbols
    chars = [random.choice(all_chars) for _ in range(16)]
    chars[0] = random.choice(upper)
    chars[1] = random.choice(lower)
    chars[2] = random.choice(digits)
    chars[3] = random.choice(symbols)
    random.shuffle(chars)
    return ''.join(chars)


class NoTypingStatic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kit = read('ui/workspace/NoTypingControls.kt')
        cls.screen = read('ui/workspace/WorkspaceScreen.kt')

    def test_kit_components_exist(self):
        for name in ('PresetChipsRow', 'GeneratePasswordButton', 'PasteButton',
                     'PageSelectionSummary', 'generateStrongPassword', 'countSelectedPages'):
            self.assertIn(name, self.kit, f'missing kit component: {name}')

    def test_no_typed_page_specs_in_workspace(self):
        for gone in ('field_pages_spec', 'field_organize_order', 'field_rotate_pages'):
            self.assertNotIn(gone, self.screen, f'typed page field remains: {gone}')

    def test_visual_tools_use_page_summary(self):
        for tool in ('"pdf_05"', '"pdf_06"', '"pdf_07"', '"pdf_crop"', '"pdf_stamp"',
                     '"pdf_to_docx","pdf_to_pptx","pdf_ocr"'):
            self.assertIn(tool, self.screen, f'tool branch missing: {tool}')
        # 6 call sites cover 8 tools: to_docx/to_pptx/ocr share one branch.
        self.assertGreaterEqual(self.screen.count('PageSelectionSummary('), 6)
        self.assertIn('ui_pages_edit_visual', self.screen)

    def test_preset_chips_cover_text_settings(self):
        self.assertIn('PresetChipsRow(listOf("converted_","IMG_","Photo_")', self.screen)
        self.assertIn('watermark_preset_draft', self.screen)
        self.assertIn('watermark_preset_sample', self.screen)
        self.assertIn('PresetChipsRow(listOf("{name}_{n}","{date}_{name}","{name}")', self.screen)

    def test_password_generate_only_for_creation(self):
        self.assertEqual(self.screen.count('GeneratePasswordButton'), 2)
        self.assertIn('if(!f.flag1) GeneratePasswordButton', self.screen)

    def test_sha256_gets_paste(self):
        self.assertIn('PasteButton {v->edit {it.copy(expectedHash=v)}}', self.screen)

    def test_new_strings_both_locales(self):
        for locale in ('values', 'values-ar'):
            text = xml_strings(locale)
            for key in ('ui_output_name_auto', 'ui_pages_selection_title', 'ui_pages_all',
                        'ui_pages_selected_count', 'ui_pages_edit_visual',
                        'watermark_preset_draft', 'watermark_preset_sample',
                        'studio_password_generate'):
                self.assertIn(key, text, f'missing string {key} in {locale}')


class NoTypingBehavior(unittest.TestCase):
    def test_page_count_summary(self):
        self.assertIsNone(count_selected_pages('all'))
        self.assertIsNone(count_selected_pages(''))
        self.assertEqual(count_selected_pages('1-3,5'), 4)
        self.assertEqual(count_selected_pages('1'), 1)
        self.assertEqual(count_selected_pages('1-3, 7, 10-12'), 7)
        self.assertEqual(count_selected_pages('3-1'), -1)
        self.assertEqual(count_selected_pages('abc'), -1)

    def test_generated_passwords_cover_all_classes(self):
        for _ in range(200):
            pw = generate_strong_password()
            self.assertEqual(len(pw), 16)
            self.assertTrue(any(c in string.ascii_uppercase for c in pw))
            self.assertTrue(any(c in string.ascii_lowercase for c in pw))
            self.assertTrue(any(c in string.digits for c in pw))
            self.assertTrue(any(c in '!@#$%^&*' for c in pw))


if __name__ == '__main__':
    unittest.main()
