#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class ProScanContractTest(unittest.TestCase):
    def test_document_filters_and_id_card_a4_composer(self):
        flt = (ROOT / "app/src/main/java/com/jumlo/app/data/scan/DocumentFilterEngine.kt").read_text(encoding="utf-8")
        for mode in ("ORIGINAL", "CLEAN_DOCUMENT", "CRISP_BW", "MAGIC_COLOR"):
            self.assertIn(mode, flt)
        comp = (ROOT / "app/src/main/java/com/jumlo/app/data/scan/IdCardA4Composer.kt").read_text(encoding="utf-8")
        self.assertIn("595f", comp)
        self.assertIn("842f", comp)
        self.assertIn("ensureLandscape", comp)
        self.assertIn("PDF_VERIFY_FAILED", comp)

    def test_scan_screen_exposes_id_card_mode(self):
        sc = (ROOT / "app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt").read_text(encoding="utf-8")
        self.assertIn("IdCardScanFlow", sc)
        self.assertIn("scan.format.idcard", sc)

if __name__ == "__main__":
    unittest.main()
