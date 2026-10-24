#!/usr/bin/env python3
"""Source contracts for the PDF compression quality ladder.

Static: these assertions do not execute PDFBox. They bind the ladder pieces the
product brief requires -- image downsampling, JPEG re-encode through the codec
seam, Flate raster downsampling, metadata stripping, bounded memory and the
guaranteed-smaller policy -- to the code that implements them, so a regression
that drops a ladder step fails here before it reaches a device.
"""
import re
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[2]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


class CompressionLadderContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compressor = read('data/pdf/PdfImageCompressor.kt')
        cls.runner = read('data/pdf/PdfCompressionRunner.kt')
        cls.backend = read('data/pdf/PdfBoxBackend.kt')

    def test_image_ladder_downsamples_and_recodes(self):
        # JPEG re-encode through the byte-level codec seam.
        self.assertIn('codec.recode(bytes, targetWidth, targetHeight, quality)', self.compressor)
        # Pure-Kotlin box downscale for 8-bit Flate scans.
        self.assertIn('boxDownscale(raw, image.width, image.height, targetW, targetH', self.compressor)
        self.assertIn('for ((dpi, quality) in ladder)', self.compressor)

    def test_ladder_respects_memory_budget_and_cancellation(self):
        self.assertIn('IMAGE_MEMORY_BUDGET_EXCEEDED', self.compressor)
        self.assertIn('checkCancel()', self.compressor)
        self.assertIn('OUT_OF_MEMORY', self.compressor)

    def test_compress_strips_document_metadata_before_save(self):
        # The brief's ladder includes stripping metadata; the runner must do it
        # through the shared pure-PDFBox helper before writing the candidate.
        body = self.runner.split('open(source, directory, minFreeBytes).use { document ->', 1)[1]
        save_at = body.index('document.save(candidate)')
        scrub_at = body.index('PdfBoxBackend.scrubMetadata(document)')
        self.assertLess(scrub_at, save_at, 'metadata must be scrubbed before the candidate is saved')

    def test_metadata_scrub_removes_info_keys_and_xmp(self):
        scrub = self.backend.split('fun scrubMetadata(', 1)[1].split('\n    fun ', 1)[0]
        self.assertIn('doc.documentInformation.cosObject', scrub)
        self.assertIn('cos.removeItem(key)', scrub)
        self.assertIn('COSName.METADATA', scrub)
        self.assertIn('removed += "xmp"', scrub)

    def test_compression_never_publishes_a_larger_output(self):
        runner = self.runner
        self.assertIn('betterCandidate(originalBytes, bestBytes, candidate.length(), pageCount, candidatePages)', runner)
        self.assertIn('require(ready.length() in 1..originalBytes)', runner)

    def test_previews_use_the_publication_compressor(self):
        measurement = self.runner.split('fun measure(', 1)[1].split('fun compress(', 1)[0]
        self.assertIn('return compress(source, File(directory, "measured.pdf")', measurement)


if __name__ == '__main__':
    unittest.main(verbosity=2)
