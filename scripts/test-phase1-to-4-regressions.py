#!/usr/bin/env python3
"""Source-level regression contracts; they do not substitute for an Android build/device run."""
from pathlib import Path
import unittest
from source_paths import source_path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/jumlo/app'


def read(path):
    return source_path(path).read_text(encoding='utf-8')


class SourceRegressionContracts(unittest.TestCase):
    def test_job_recovery_checks_persisted_state_and_lease(self):
        jobs = read('data/jobs/JobStore.kt')
        self.assertIn('JobLifecyclePolicy.shouldRecover(row.stage)', jobs)
        self.assertIn('row.leaseOwner==null || row.leaseUntil<now', jobs)
        self.assertIn('dao.updateOwned(jobId,owner', jobs)
        self.assertIn('retainedUnits=old.progress.completedUnits.coerceAtLeast(0L)', jobs)
        # Scheduling remains bounded, but concurrency is no longer a user preference.
        self.assertIn('admission.withSlot(2,ctx.checkCancellation)', jobs)
        self.assertNotIn('maxConcurrentDownloads', read('ui/settings/SettingsScreen.kt'))

    def test_imports_are_private_immutable_snapshots(self):
        source = read('data/source/SourceStore.kt')
        self.assertIn('SourceSnapshotPolicy.cachedFile(inbound,sourceId,path)', source)
        self.assertIn('StandardCopyOption.ATOMIC_MOVE', source)
        self.assertIn('sink.fd.sync()', source)

    def test_downloads_prove_full_content_before_publication(self):
        transfer = read('core/StreamTransfer.kt')
        self.assertIn('if(total!=null && written!=total)', transfer)
        self.assertIn('ResumePolicy.validate(', transfer)
        self.assertIn('out.fd.sync()', transfer)
        handler = read('data/processing/DownloadOperationHandler.kt')
        self.assertIn('unexpectedHtml(result.mimeType,prefix)', handler)

    def test_preference_changes_cannot_reuse_media_job_identity(self):
        media = read('ui/downloads/MediaViewModel.kt')
        self.assertIn('MediaSubmissionKey.key(', media)
        self.assertIn('s.wifiOnly', media)
        self.assertIn('s.unmetered', media)
        self.assertIn('s.allowHttp', media)

    def test_history_discloses_where_output_is_saved(self):
        history = read('ui/downloads/DownloadsScreen.kt')
        self.assertIn('output_saved_location', history)
        self.assertIn('output_saved_privately', history)

    def test_file_library_explains_verified_public_and_private_locations(self):
        library = read('ui/files/FilesScreen.kt')
        self.assertIn('output_saved_location', library)
        self.assertIn('output_saved_privately', library)

    def test_production_does_not_ship_unused_fake_before_after_preview(self):
        self.assertFalse((SRC / 'ui/components/LegacyPreviewFixtures.kt').exists(),
            'A hardcoded image-icon comparison is not an actual before/after preview')

    def test_locale_resource_names_match(self):
        def keys(directory):
            values = set()
            for resource in (ROOT / 'app/src/main/res' / directory).glob('*.xml'):
                for element in ET.parse(resource).getroot():
                    if element.tag in {'string', 'plurals', 'string-array'}:
                        key = (element.tag, element.attrib['name'])
                        self.assertNotIn(key, values, f'Duplicate {key!r} in {directory}')
                        values.add(key)
            return values
        en, ar = keys('values'), keys('values-ar')
        self.assertGreater(len(en), 500)
        self.assertEqual(en, ar)

    def test_ssrf_and_scan_resource_guards_are_present(self):
        policies = read('core/media/MediaPolicies.kt')
        html = read('core/media/Html5MediaDiscovery.kt')
        scan = read('ui/scan/ScanScreen.kt')
        self.assertIn('host.startsWith("::ffff:")', policies)
        self.assertIn('host.endsWith(".internal")', policies)
        self.assertIn('trimEnd(\'.\')', html)
        self.assertIn('SmartDocumentScanLauncher(', scan)
        self.assertNotIn('ProcessCameraProvider', scan)
        scanner = read('ui/scan/SmartDocumentScanButton.kt')
        for token in ('SCANNER_MODE_FULL', 'RESULT_FORMAT_PDF', '.setGalleryImportAllowed(true)',
                      '.setPageLimit(100)', 'SmartScanResultStore.copyPdf(', 'owned?.delete()'):
            self.assertIn(token, scanner)
        result = read('data/pdf/SmartScanResultStore.kt')
        for token in ('withContext(Dispatchers.IO)', 'coroutine.ensureActive()',
                      'ResourceBudgetPolicy.minFreeBytes', 'output.fd.sync()',
                      'it.pageCount == expectedPages', 'pending?.delete()'):
            self.assertIn(token, result)
        # The extracted renderer transfers a fresh bitmap to the export block.
        # It is released unconditionally in finally, not behind the old nullable
        # bitmap guard; a render failure releases its own allocation as well.
        self.assertIn('val bitmap = renderSignature(savedStrokes, savedSize.width, savedSize.height)', scan)
        export = scan.split('val bitmap = renderSignature(', 1)[1].split('libraryVersion++', 1)[0]
        self.assertIn('Bitmap.CompressFormat.PNG', export)
        self.assertIn('it.fd.sync()', export)
        self.assertRegex(export, r'finally\s*\{\s*bitmap\.recycle\(\)\s*\}')
        renderer = read('ui/scan/SignatureRenderer.kt')
        self.assertIn('signatureExportGeometry(canvasWidth, canvasHeight)', renderer)
        self.assertIn('Bitmap.Config.ARGB_8888', renderer)
        self.assertRegex(renderer, r'catch\s*\((\w+)\s*:\s*Throwable\)\s*\{\s*bitmap\.recycle\(\)\s*throw\s+\1')


if __name__ == '__main__':
    unittest.main(verbosity=2)
