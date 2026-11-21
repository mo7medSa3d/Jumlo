#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class ProMarkupContractTest(unittest.TestCase):
    def test_markup_model_and_rotation_mapping(self):
        src = (ROOT / "document-core/src/main/kotlin/com/jumlo/app/core/PdfMarkupModel.kt").read_text(encoding="utf-8")
        for sym in ("InkStroke", "HighlightStroke", "TextBox", "CheckMark", "SavedSignatureStamp", "UndoRedoStack", "toPdfPoint", "toPdfRect"):
            self.assertIn(sym, src)
        for rot in ("90 ->", "180 ->", "270 ->"):
            self.assertIn(rot, src)

    def test_flattener_uses_static_layout_for_arabic_and_verifies_pdf(self):
        src = (ROOT / "app/src/main/java/com/jumlo/app/data/pdf/PdfMarkupFlattener.kt").read_text(encoding="utf-8")
        self.assertIn("StaticLayout.Builder", src)
        self.assertIn("LosslessFactory.createFromImage", src)
        self.assertIn("PDExtendedGraphicsState", src)
        self.assertIn("PDF_VERIFY_FAILED", src)

    def test_saved_signature_store_bounded(self):
        src = (ROOT / "app/src/main/java/com/jumlo/app/data/pdf/SavedSignatureStore.kt").read_text(encoding="utf-8")
        self.assertIn("MAX_SIGNATURES = 12", src)
        self.assertIn("saved_signatures", src)

    def test_viewer_and_viewmodel_wire_markup_studio(self):
        viewer = (ROOT / "app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt").read_text(encoding="utf-8")
        self.assertIn("PdfMarkupFloatingBar", viewer)
        self.assertIn("SignaturePadSheet", viewer)
        self.assertIn("pdf.markup.toggle", viewer)
        vm = (ROOT / "app/src/main/java/com/jumlo/app/ui/AppViewModel.kt").read_text(encoding="utf-8")
        self.assertIn("fun flattenPdfMarkup", vm)
        self.assertIn("PdfMarkupFlattener.flatten", vm)

if __name__ == "__main__":
    unittest.main()
