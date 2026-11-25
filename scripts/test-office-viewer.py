#!/usr/bin/env python3
"""Contract checks for the multi-format in-app office document viewer."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
CODE = MAIN / "java/com/jumlo/app"

EXPECTED_OFFICE_STRING_KEYS = {
    "viewer_office_badge_word",
    "viewer_office_badge_presentation",
    "viewer_office_badge_spreadsheet",
    "viewer_office_badge_text",
    "viewer_office_loading",
    "viewer_office_error_open",
    "viewer_office_open_external",
    "viewer_office_convert_pdf",
    "viewer_office_present",
    "viewer_office_exit_present",
    "viewer_office_slide_counter",
    "viewer_office_empty_slides",
    "viewer_office_empty_doc",
    "viewer_office_outline",
    "viewer_office_appearance",
    "viewer_office_font_size",
    "viewer_office_theme_auto",
    "viewer_office_theme_paper",
    "viewer_office_theme_sepia",
    "viewer_office_theme_dark",
    "viewer_office_search_hint",
    "viewer_office_search_matches",
    "viewer_office_search_none",
    "viewer_office_word_count",
    "viewer_office_partial_notice",
    "viewer_office_page_break",
    "viewer_office_prev_slide",
    "viewer_office_next_slide",
}

EXPECTED_MANIFEST_MIME_TYPES = {
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "text/markdown",
}


class OfficeViewerContractTests(unittest.TestCase):
    def test_document_viewer_loader_contract(self):
        loader_path = CODE / "data/office/DocumentViewerLoader.kt"
        self.assertTrue(loader_path.is_file(), f"Missing {loader_path.relative_to(ROOT)}")
        text = loader_path.read_text(encoding="utf-8")
        self.assertIn("OfficeDocumentReader.read", text)
        self.assertIn("MAX_VIEWER_FILE_BYTES", text)
        self.assertRegex(text, r'content(?::/|")', "Loader must enforce content:// URI scheme")
        for kind in ("WORD", "PRESENTATION", "SPREADSHEET", "TEXT"):
            self.assertRegex(text, rf"\b{kind}\b", f"Loader missing support for {kind}")

    def test_office_document_viewer_screen_contract(self):
        screen_path = CODE / "ui/viewer/OfficeDocumentViewerScreen.kt"
        self.assertTrue(screen_path.is_file(), f"Missing {screen_path.relative_to(ROOT)}")
        text = screen_path.read_text(encoding="utf-8")
        # Presentation stage, filmstrip, and fullscreen present mode
        self.assertIn("HorizontalPager", text)
        self.assertIn("viewer_office_present", text)
        self.assertIn("viewer_office_exit_present", text)
        self.assertIn("viewer_office_slide_counter", text)
        self.assertRegex(text, r"(?i)filmstrip|LazyRow", "Presentation viewer should provide a slide filmstrip")
        # Word reader with Aa appearance, outline, and search
        self.assertIn("AppearanceBottomSheet", text)
        self.assertIn("viewer_office_appearance", text)
        self.assertIn("viewer_office_outline", text)
        self.assertIn("viewer_office_search_hint", text)
        # Spreadsheet sheet tabs
        self.assertRegex(text, r"TabRow|ScrollableTabRow|selectedSheet", "Spreadsheet viewer should provide sheet tabs")

    def test_nav_host_routes_office_and_preserves_pdf(self):
        nav_text = (CODE / "ui/navigation/JumloNavHost.kt").read_text(encoding="utf-8")
        self.assertIn(
            'data class ViewerRoute(val uri: String, val title: String, val returnTab: String? = null, val mimeType: String? = null)',
            nav_text,
        )
        self.assertIn("DocumentViewerFormats.supportsInAppViewer", nav_text)
        self.assertIn("DocumentViewerFormats.detect", nav_text)
        self.assertIn("DocumentViewerKind.PDF", nav_text)
        self.assertIn("OfficeDocumentViewerScreen(", nav_text)
        self.assertIn("PdfViewerScreen(", nav_text)

    def test_manifest_declares_office_share_mime_types(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(MAIN / "AndroidManifest.xml").getroot()
        send_mimes = set()
        for intent_filter in root.findall("application/activity/intent-filter"):
            actions = {a.get(android + "name") for a in intent_filter.findall("action")}
            if "android.intent.action.SEND" in actions:
                for data in intent_filter.findall("data"):
                    mime = data.get(android + "mimeType")
                    if mime:
                        send_mimes.add(mime)
        self.assertTrue(
            EXPECTED_MANIFEST_MIME_TYPES.issubset(send_mimes),
            f"Missing ACTION_SEND mimeTypes: {sorted(EXPECTED_MANIFEST_MIME_TYPES - send_mimes)}",
        )

    def test_bilingual_office_viewer_strings_exist_with_parity(self):
        spec_re = re.compile(r"%\d+\$[sd]")
        en_map = {
            node.get("name"): (node.text or "")
            for node in ET.parse(MAIN / "res/values/strings.xml").getroot()
            if node.tag == "string"
        }
        ar_map = {
            node.get("name"): (node.text or "")
            for node in ET.parse(MAIN / "res/values-ar/strings.xml").getroot()
            if node.tag == "string"
        }
        self.assertEqual(28, len(EXPECTED_OFFICE_STRING_KEYS))
        missing_en = EXPECTED_OFFICE_STRING_KEYS - set(en_map)
        missing_ar = EXPECTED_OFFICE_STRING_KEYS - set(ar_map)
        self.assertEqual(set(), missing_en, f"Missing EN keys: {sorted(missing_en)}")
        self.assertEqual(set(), missing_ar, f"Missing AR keys: {sorted(missing_ar)}")
        for key in sorted(EXPECTED_OFFICE_STRING_KEYS):
            self.assertTrue(en_map[key].strip(), f"Empty EN string: {key}")
            self.assertTrue(ar_map[key].strip(), f"Empty AR string: {key}")
            self.assertEqual(
                set(spec_re.findall(en_map[key])),
                set(spec_re.findall(ar_map[key])),
                f"Format specifier mismatch for {key}",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
