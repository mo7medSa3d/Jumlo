#!/usr/bin/env python3
"""Fast source contract checks. These do not replace Gradle or emulator tests."""
import pathlib
import unittest
from source_paths import source_path
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/jumlo/app'
def code(p):return source_path(p).read_text()
class VisualEditorContract(unittest.TestCase):
    def test_real_workspace_wiring(self):
        workspace=code('ui/workspace/WorkspaceScreen.kt')
        self.assertIn('VisualDocumentEditor(vm,editorSource.id,form,vm::updateForm,overlayImage?.id)',workspace)
        self.assertIn('pdf_image',workspace)
        # Adjustment now has its own real before/after preview, rather than a flag
        # on the generic input pager (which is excluded for the adjustment tool).
        self.assertIn('ImageResultPreview(vm,ids,form)',workspace)
        result_preview=code('ui/workspace/ImageResultPreview.kt')
        self.assertIn('ImageComparisonPreview(original,shown.bitmap)',result_preview)
        self.assertIn('renderSinglePreview(source,logo,draft)',result_preview)
        self.assertIn('VisualImageSettings(form,vm::updateForm)',workspace)
    def test_engine_preserves_editing_contract(self):
        model=code('model/WorkspaceDraft.kt')
        for tool in ('img_02','img_03','pdf_crop','pdf_signature','pdf_annotate','img_adjust'):
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
        runner=code('data/pdf/PdfCompressionRunner.kt')
        backend=code('data/pdf/PdfBoxBackend.kt')
        policy=code('core/PdfPageGeometry.kt')
        catalog=code('data/jobs/CapabilityRegistry.kt')
        image_codec=code('data/pdf/PdfImageCodec.kt')
        image_compressor=code('data/pdf/PdfImageCompressor.kt')
        android_codec=code('data/pdf/AndroidImageCodec.kt')
        # Preview and publication share the extracted runner. Check the actual
        # validation/cleanup there and preserve the handler-to-runner connection.
        self.assertIn('PdfCompressionRunner.compress(',compressor)
        self.assertIn('PdfCompressionPolicy.betterCandidate(originalBytes, bestBytes, candidate.length(), pageCount, candidatePages)',runner)
        self.assertIn('val candidatePages = open(candidate, directory, minFreeBytes).use { it.numberOfPages }',runner)
        self.assertIn('bestBytes = best.length()',runner)
        self.assertIn('candidateBytes < originalBytes',policy)
        self.assertIn('candidatePages == originalPages',policy)
        self.assertIn('candidateBytes < bestBytes',policy)
        self.assertIn('require(source.canonicalFile != destination.canonicalFile)',runner)
        self.assertIn('require(ready.length() in 1..originalBytes)',runner)
        self.assertIn('Files.move(ready.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)',runner)
        compress_body=runner[runner.index('fun compress('):runner.index('private fun open(')]
        self.assertIn('finally {\n            directory.deleteRecursively()',compress_body)
        self.assertIn('PDFormXObject',backend)
        self.assertIn('decodeSubsampling(',policy)
        self.assertIn('pdf_image',catalog)
        self.assertIn('"overlayImage" ->',compressor)
        # The image work goes through a byte-level codec seam, so the same code
        # runs on the host and the scan case has CI coverage.
        self.assertIn('fun interface PdfImageCodec',image_codec)
        self.assertIn('fun recode(',image_codec)
        self.assertNotIn('import android',image_codec)
        self.assertIn('PdfImageCompressor',backend)
        self.assertIn('codec.recode(',image_compressor)
        # The decode is budgeted and an out-of-memory is caught per image.
        self.assertIn('ImageWorkPolicy.requireFits(',android_codec)
        self.assertIn('OutOfMemoryError',image_compressor)
        # The ladder reaches below the chosen resolution, and keeps the quality.
        self.assertIn('fun ladderFloor(',policy)
        self.assertIn('rungs += step to selectedQuality',policy)

    def test_visual_settings_do_not_ask_for_file_size_or_dpi(self):
        controls=code('ui/workspace/VisualImageSettings.kt')
        self.assertIn('R.string.visual_compress_balanced',controls)
        self.assertIn('R.string.visual_compress_small',controls)
        self.assertIn('R.string.visual_compress_best',controls)
        self.assertIn('R.string.visual_compress_strong',controls)
        self.assertNotIn('Field(',controls)

    def test_undo_and_redo_edit_history(self):
        editor=code('ui/workspace/VisualDocumentEditor.kt')
        for name in ('undoHistory','redoHistory','recordEdit(', 'beforeDrag', 'R.string.visual_edit_undo', 'R.string.visual_edit_redo'):
            self.assertIn(name,editor)
        # A drag commits as recordEdit(beforeDrag, active) then saves the active box;
        # move/size buttons record recordEdit(rect, next) before saveRect(next).
        self.assertIn('recordEdit(it,active)',editor)
        self.assertIn('saveRect(active)',editor)
        self.assertIn('saveRect(next)',editor)

    def test_compression_progress_and_signature_order_are_resilient(self):
        engine=code('data/processing/PdfOperationHandler.kt')
        runner=code('data/pdf/PdfCompressionRunner.kt')
        self.assertIn('profiles.withIndex()',runner)
        self.assertIn('onProgress(pass.toLong() * total + done, profiles.size.toLong() * total)',runner)
        self.assertIn('context.report(JobProgress(JobStage.RUNNING, completed, total))',engine)
        self.assertIn('checkCancel = context.checkCancellation',engine)
        self.assertIn('PdfOverlaySourcePolicy.ordered(',engine)
        self.assertIn('seekAll(context,request,ordered)',engine)

    def test_pdf_image_overlay_preview_matches_real_pdf_geometry(self):
        geometry=code('core/PdfOverlaySourcePolicy.kt')
        renderer=code('data/pdf/PdfBoxBackend.kt')
        editor=code('ui/workspace/VisualDocumentEditor.kt')
        backend=code('data/processing/PdfOperationHandler.kt')
        self.assertIn('object PdfOverlaySourcePolicy',geometry)
        self.assertIn('object PdfImagePlacementPolicy',geometry)
        self.assertIn('PdfImagePlacementPolicy.fit(',renderer)
        self.assertIn('placement.width,placement.height',renderer)
        self.assertIn('contentScale=ContentScale.Fit)',editor)
        # Image watermarks add a third consumer; a global count cannot establish
        # that each image-bearing operation orders PDF/image inputs correctly.
        for operation,next_operation in (('stamp','annotate'),('overlayImage','signature'),('signature','clean')):
            branch=backend.split('"'+operation+'" ->',1)[1].split('"'+next_operation+'" ->',1)[0]
            with self.subTest(operation=operation):
                self.assertIn('PdfOverlaySourcePolicy.ordered(',branch)
                self.assertIn('seekAll(context,request,ordered)',branch)

    def test_file_savings_display_uses_real_metadata(self):
        fileScreen=code('ui/files/FilesScreen.kt')
        self.assertIn('output.metadata.originalSizeBytes',fileScreen)
        self.assertIn('R.string.visual_edit_saved_percent',fileScreen)

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
