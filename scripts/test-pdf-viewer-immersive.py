#!/usr/bin/env python3
"""Source contracts for the immersive, content-first PDF viewer.

Static: asserts the single slim top bar (back, title, page indicator, overflow),
the overflow menu's seven entries, immersive auto-hide chrome + system-bar control,
on-demand thumbnail/search/info sheets, and that the old pages/dark-pages tab row
and the standalone dark-pages string are gone. Does not render.
"""
import re
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


def string_names(text):
    return set(re.findall(r'name="([^"]+)"', text))


class PdfViewerImmersiveContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.viewer = read('ui/viewer/PdfViewerScreen.kt')
        cls.nav = read('ui/navigation/JumloNavHost.kt')
        cls.en = (ROOT / 'app/src/main/res/values/pdf_reader.xml').read_text(encoding='utf-8')
        cls.ar = (ROOT / 'app/src/main/res/values-ar/pdf_reader.xml').read_text(encoding='utf-8')

    def test_screen_signature_takes_back_openfile_print(self):
        self.assertRegex(self.viewer, r'fun PdfViewerScreen\(\s*uri:\s*String,\s*title:\s*String,\s*onBack:\s*\(\)\s*->\s*Unit,\s*onOpenFile:\s*\(\)\s*->\s*Unit,\s*onPrint:\s*\(String,\s*String\)\s*->\s*Unit')

    def test_single_slim_top_bar_with_required_controls(self):
        self.assertIn('pdf.back', self.viewer)
        self.assertIn('pdf.page.indicator', self.viewer)
        self.assertIn('pdf.menu', self.viewer)
        # Top bar content height is capped at 56dp.
        self.assertRegex(self.viewer, r'height\(56\.dp\)')

    def test_overflow_menu_exposes_all_seven_entries(self):
        for key in ('reader_menu_open', 'reader_menu_night', 'reader_menu_thumbnails',
                    'reader_menu_search', 'reader_menu_share', 'reader_menu_print',
                    'reader_menu_info'):
            self.assertIn(f'R.string.{key}', self.viewer, f'missing overflow item {key}')

    def test_page_indicator_uses_page_of_count_string(self):
        self.assertIn('R.string.reader_page_indicator', self.viewer)

    def test_old_pages_dark_pages_tab_row_is_removed(self):
        self.assertNotIn('reader_dark_pages', self.viewer)
        self.assertNotIn('pdf.dark.toggle', self.viewer)
        self.assertNotIn('pdf.thumbnails.toggle', self.viewer)
        self.assertNotIn('FilterChip(thumbnails', self.viewer)
        self.assertNotIn('FilterChip(darkPages', self.viewer)

    def test_immersive_chrome_and_system_bar_control(self):
        self.assertIn('AnimatedVisibility', self.viewer)
        self.assertIn('WindowInsetsControllerCompat', self.viewer)
        self.assertIn('BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE', self.viewer)
        self.assertIn('WindowInsetsCompat.Type.systemBars()', self.viewer)
        self.assertIn('FLAG_KEEP_SCREEN_ON', self.viewer)
        # Auto-hide after two seconds of stillness.
        self.assertRegex(self.viewer, r'delay\(2000\)')

    def test_on_demand_surfaces_are_sheets(self):
        self.assertIn('ThumbnailsSheet', self.viewer)
        self.assertIn('SearchSheet', self.viewer)
        self.assertIn('InfoSheet', self.viewer)
        self.assertIn('JumpDialog', self.viewer)
        self.assertIn('ModalBottomSheet', self.viewer)

    def test_reading_quality_features_present(self):
        # fit-to-width default, pinch zoom, double-tap zoom, night invert.
        self.assertIn('rememberTransformableState', self.viewer)
        self.assertIn('onDoubleTap', self.viewer)
        self.assertIn('ColorMatrix', self.viewer)
        # Thumbnails lazy-load through the session for 500+ pages.
        self.assertIn('document.pageCount', self.viewer)

    def test_dark_pages_string_is_gone_everywhere(self):
        self.assertNotIn('reader_dark_pages', self.en)
        self.assertNotIn('reader_dark_pages', self.ar)

    def test_en_ar_string_keys_match(self):
        self.assertEqual(string_names(self.en), string_names(self.ar))

    def test_nav_shell_hides_dock_and_topbar_for_viewer(self):
        # Bottom dock only on roots and the workspace/activity routes.
        self.assertRegex(self.nav, r'showDock\s*=\s*root\s*\|\|\s*shellRoute in setOf\("workspace",\s*"activity"\)')
        # The workspace top bar is skipped for the viewer route.
        self.assertIn('else if(shellRoute!="viewer") QuietWorkspaceTopBar', self.nav)

    def test_nav_wires_viewer_callbacks(self):
        branch = self.nav.split('"viewer" -> viewer?.let', 1)[1].split('\n', 1)[0]
        self.assertIn('onBack=::goBack', branch)
        self.assertIn('onOpenFile=', branch)
        self.assertIn('onPrint=', branch)
        self.assertIn('importForPrint', branch)


if __name__ == '__main__':
    unittest.main()
