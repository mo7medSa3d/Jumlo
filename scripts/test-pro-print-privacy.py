#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class ProPrintPrivacyContractTest(unittest.TestCase):
    def test_nup_and_redaction_engines(self):
        nup = (ROOT / "app/src/main/java/com/jumlo/app/data/pdf/PdfNupComposer.kt").read_text(encoding="utf-8")
        self.assertIn("setOf(2, 4)", nup)
        self.assertIn("LayerUtility", nup)
        self.assertIn("toGrayscale", nup)
        red = (ROOT / "app/src/main/java/com/jumlo/app/data/pdf/PdfRedactionEngine.kt").read_text(encoding="utf-8")
        self.assertIn("PDDocumentInformation()", red)
        self.assertIn("PdfPageRasterizer", red)
        self.assertIn("canvas.drawRect", red)

    def test_tools_wired_across_registry_dispatcher_draft_and_handler(self):
        for rel in (
            "app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt",
            "app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt",
            "app/src/main/java/com/jumlo/app/model/WorkspaceDraft.kt",
            "app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt",
            "app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt",
        ):
            src = (ROOT / rel).read_text(encoding="utf-8")
            self.assertIn("pdf_nup", src, rel)
            self.assertIn("pdf_privacy_redact", src, rel)

if __name__ == "__main__":
    unittest.main()
