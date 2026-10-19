#!/usr/bin/env python3
"""Fast source contract checks. These do not replace Gradle or emulator tests."""
import pathlib
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/jumlo/app'
def code(p):return (SRC/p).read_text()
class VisualEditorContract(unittest.TestCase):
    def test_real_workspace_wiring(self):
        workspace=code('ui/workspace/WorkspaceScreen.kt')
        self.assertIn('VisualDocumentEditor(vm,queue.first().id,form,vm::updateForm)',workspace)
        self.assertIn('VisualImageSettings(form,vm::updateForm)',workspace)
    def test_engine_preserves_editing_contract(self):
        model=code('model/WorkspaceDraft.kt')
        for tool in ('img_02','img_03','pdf_crop','pdf_signature','pdf_redact','img_adjust'):
            self.assertIn('"'+tool+'"',model)
        editor=code('ui/workspace/VisualDocumentEditor.kt')
        self.assertIn('cropLeft=rect.left.serialized()',editor)
        self.assertIn('cropRight=rect.right.serialized()',editor)
        self.assertIn('detectDragGestures(',editor)
        self.assertIn('hitHandle(',editor)
    def test_separate_native_geometry_regressions(self):
        test=ROOT/'app/src/test/java/com/jumlo/app/ui/workspace/VisualEditGeometryTest.kt'
        self.assertIn('cornersNeverCrossAndAlwaysStayOnDocument',test.read_text())
        self.assertIn('aspectPresetUsesOriginalSourceDimensions',test.read_text())
    def test_arabic_and_english(self):
        from xml.etree import ElementTree as ET
        en=ET.parse(ROOT/'app/src/main/res/values/visual_editor.xml')
        ar=ET.parse(ROOT/'app/src/main/res/values-ar/visual_editor.xml')
        names=lambda xml: {e.get('name') for e in xml.getroot()}
        self.assertEqual(names(en),names(ar))
if __name__=='__main__':unittest.main()
