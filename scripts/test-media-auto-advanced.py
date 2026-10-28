#!/usr/bin/env python3
"""Source contract for the opt-in downloader overrides. Not an Android UI test."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/jumlo/app/ui/downloads/MediaWorkspace.kt"
VIEW_MODEL = ROOT / "app/src/main/java/com/jumlo/app/ui/downloads/MediaViewModel.kt"

class MediaAutomaticDefaults(unittest.TestCase):
    def test_default_is_auto_selected_and_submit_without_opening_advanced(self):
        ui = UI.read_text()
        vm = VIEW_MODEL.read_text()
        self.assertIn("var showAdvanced by rememberSaveable(sessionEpoch) { mutableStateOf(false) }", ui)
        self.assertIn("selectedKey=MediaCatalog.preferred(choices,effectiveMode,preferredVideoHeight)?.key", vm)
        self.assertIn("(state.direct!=null || state.selectedKey!=null)", ui)
        self.assertNotIn("state.asset != null -> stringResource(R.string.media_download_selected)", ui)

    def test_optional_overrides_remain_reachable(self):
        ui = UI.read_text()
        for token in (
            'if (showAdvanced) item("media-type-selector")',
            'if (showAdvanced) item("media-quality-header")',
            'if (showAdvanced) items(state.choices.chunked(2)',
            'if (showAdvanced && state.mode == MediaMode.AUDIO)',
            'if (showAdvanced && preview.kind == DirectFileKind.PDF)',
            'showAdvanced && (state.asset != null || state.direct != null)',
        ):
            self.assertIn(token,ui)
        self.assertIn('testTag("media.advanced")', ui)

    def test_translations_match(self):
        keys = []
        for language in ("values", "values-ar"):
            root = ET.parse(ROOT / f"app/src/main/res/{language}/media_strings.xml").getroot()
            by_name = {item.attrib["name"]: item.text for item in root.findall("string")}
            for key in ("media_advanced_show", "media_advanced_hide"):
                self.assertTrue(by_name.get(key), f"{language} missing {key}")
            keys.append(set(by_name))
        self.assertEqual(keys[0], keys[1])

if __name__ == "__main__":
    unittest.main(verbosity=2)
