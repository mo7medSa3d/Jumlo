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
        self.assertIn('VisualDocumentEditor(vm,editorSource.id,form,vm::updateForm,overlayImage?.id)',workspace)
        self.assertIn('pdf_image',workspace)
        self.assertIn('adjustment=if(tool=="img_adjust") form else null',workspace)
        self.assertIn('VisualImageSettings(form,vm::updateForm)',workspace)
    def test_engine_preserves_editing_contract(self):
        model=code('model/WorkspaceDraft.kt')
        for tool in ('img_02','img_03','pdf_crop','pdf_signature','pdf_redact','img_adjust'):
            self.assertIn('"'+tool+'"',model)
        editor=code('ui/workspace/VisualDocumentEditor.kt')
        self.assertIn('cropLeft=safe.left.serialized()',editor)
        self.assertIn('cropRight=safe.right.serialized()',editor)
        self.assertIn('vm.preview(overlayId,1)',editor)
        self.assertIn('VisualRect.fitAspect(',editor)
        self.assertIn('pdf_annotate',editor)
        self.assertIn('detectDragGestures(',editor)
        self.assertIn('hitHandle(',editor)
    def test_compression_reduction_and_safety_contracts(self):
        compressor=code('data/processing/PdfOperationHandler.kt')
        backend=code('data/pdf/PdfBoxBackend.kt')
        policy=code('core/PdfPageGeometry.kt')
        catalog=code('data/jobs/CapabilityRegistry.kt')
        self.assertIn('PdfCompressionPolicy.betterCandidate(',compressor)
        self.assertIn('bestBytes = candidate.length()',compressor)
        self.assertIn('best.delete()',compressor)
        self.assertIn('getImage(null, subsampling)',backend)
        self.assertIn('PDFormXObject',backend)
        self.assertIn('decodeSubsampling(',policy)
        self.assertIn('pdf_image',catalog)
        self.assertIn('"overlayImage" ->',compressor)

    def test_visual_settings_do_not_ask_for_file_size_or_dpi(self):
        controls=code('ui/workspace/VisualImageSettings.kt')
        self.assertIn('R.string.visual_compress_balanced',controls)
        self.assertIn('R.string.visual_compress_small',controls)
        self.assertIn('R.string.visual_compress_best',controls)
        self.assertIn('R.string.visual_compress_strong',controls)
        self.assertNotIn('Field(',controls)

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
