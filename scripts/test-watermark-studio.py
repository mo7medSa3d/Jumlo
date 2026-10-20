#!/usr/bin/env python3
"""Source-only wiring contracts; native compilation and device interaction are separate gates."""
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/jumlo/app'

class WatermarkStudioContracts(unittest.TestCase):
    def test_real_tool_routes_to_visual_editor(self):
        workspace=(SRC/'ui/workspace/WorkspaceScreen.kt').read_text()
        self.assertIn('VisualWatermarkEditor(vm,editorSource.id,form,vm::updateForm)',workspace)
        self.assertIn('pdf_stamp',workspace)
    def test_styles_are_serialized_and_consumed(self):
        draft=(SRC/'model/WorkspaceDraft.kt').read_text()
        handler=(SRC/'data/processing/PdfOperationHandler.kt').read_text()
        backend=(SRC/'data/pdf/PdfBoxBackend.kt').read_text()
        for key in ('style.centerX','style.centerY','style.opacityPercent','style.rotationDegrees','style.colorHex'):
            self.assertIn(key,draft)
        self.assertIn('PdfWatermarkStyle(',handler)
        self.assertIn('stampWatermarkAndNumbers(doc,selected,text,addNumbers,style)',handler)
        self.assertIn('LosslessFactory.createFromImage(doc,bitmap)',backend)
        self.assertIn('Matrix.getRotateInstance(',backend)
        self.assertIn('bitmap.recycle()',backend)
        self.assertIn('zeroBasedPages.forEach',backend)
    def test_no_raw_numeric_fields_or_fake_document_preview(self):
        ui=(SRC/'ui/workspace/VisualWatermarkEditor.kt').read_text()
        self.assertIn('vm.preview(sourceId,page)',ui)
        self.assertIn('detectDragGestures(',ui)
        self.assertIn('WatermarkSlider(',ui)
        self.assertNotIn('Field(',ui)
        self.assertNotIn('mockPdf',ui)
        self.assertIn('draft.text2 == "TILE"',ui)
    def test_bilingual_resources(self):
        en=ET.parse(ROOT/'app/src/main/res/values/watermark_2026.xml').getroot()
        ar=ET.parse(ROOT/'app/src/main/res/values-ar/watermark_2026.xml').getroot()
        keys=lambda root: {x.attrib.get('name') for x in root if x.tag=='string'}
        self.assertEqual(keys(en),keys(ar))
        self.assertEqual(19,len(keys(en)))

if __name__=='__main__':
    unittest.main(verbosity=2)
