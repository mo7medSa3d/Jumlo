#!/usr/bin/env python3
"""Static integration checks for direct Play Services PDF scanning; not device proof."""
import pathlib
import unittest
from xml.etree import ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/jumlo/app'
def code(path):return (SRC/path).read_text(encoding="utf-8")

class SmartScannerWiring(unittest.TestCase):
    def test_direct_pdf_scanner_and_real_sdk(self):
        scan=code("ui/scan/ScanScreen.kt")
        smart=code("ui/scan/SmartDocumentScanButton.kt")
        self.assertIn("SmartDocumentScanLauncher(",scan)
        self.assertNotIn("ProcessCameraProvider",scan)
        self.assertNotIn("Manifest.permission.CAMERA",scan)
        self.assertIn("GmsDocumentScanning.getClient(options)",smart)
        self.assertIn(".fromActivityResultIntent(activityResult.data)",smart)
        self.assertIn("SCANNER_MODE_FULL",smart)
        self.assertIn("RESULT_FORMAT_PDF",smart)
        self.assertIn(".setGalleryImportAllowed(true)",smart)
        self.assertIn("LaunchedEffect(Unit)",smart)
        self.assertIn("addOnFailureListener",smart)
    def test_batch_intake_is_ordered_and_rollback_safe(self):
        vm=code("ui/AppViewModel.kt")
        nav=code("ui/navigation/JumloNavHost.kt")
        self.assertIn("onScannedPdf",nav)
        self.assertIn("selected.forEachIndexed",vm)
        self.assertIn("sources.discardNewImport(id)",vm)
        self.assertIn("selected.forEach { it.delete() }",vm)
    def test_english_arabic_resources(self):
        en=ET.parse(ROOT/"app/src/main/res/values/smart_scan.xml")
        ar=ET.parse(ROOT/"app/src/main/res/values-ar/smart_scan.xml")
        keys=lambda root: {e.get("name") for e in root.getroot()}
        self.assertEqual(keys(en),keys(ar))
        self.assertEqual(3,len(keys(en)))
    def test_library_is_real_dependency(self):
        gradle=(ROOT/"app/build.gradle.kts").read_text()
        self.assertIn("play-services-mlkit-document-scanner:16.0.0",gradle)

if __name__=="__main__":unittest.main(verbosity=2)
