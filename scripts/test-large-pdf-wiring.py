#!/usr/bin/env python3
"""Static contracts for Android-only large-document integrations; not a device test."""
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent

def src(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

def assert_in(text, fragment, title):
    assert fragment in text, f"Missing {title}: {fragment}"

source = src('app/src/main/java/com/jumlo/app/data/source/SourceStore.kt')
assert_in(source, 'SourceSnapshotPolicy.cachedFile', 'verified reuse of immutable source')
assert_in(source, 'source.cachedPath', 'source from Room')
pdf = src('app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt')
assert_in(pdf, 'MemoryUsageSetting.setupTempFileOnly()', 'spillable PDF file scratch')
assert_in(pdf, 'PDDocument.load(file, password?.let { String(it) } ?: "", MemoryUsageSetting.setupTempFileOnly())', 'spillable PDF loads')
handler = src('app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt')
assert_in(handler, 'PDDocument(MemoryUsageSetting.setupTempFileOnly())', 'spillable PDF generation')
assert_in(handler, 'source.copyTo(out', 'valid original fallback when compression cannot improve file')
assert_in(handler, 'LargeResultStrategy', 'safe page fanout selection')
assert_in(handler, 'PdfBoxBackend.open(temp,null).use', 'split archive verifies each generated PDF before packing')
assert_in(handler, 'BitmapFactory.decodeFile(temp.path,options)', 'image archive verifies each rendered image before packing')
dl_handler = src('app/src/main/java/com/jumlo/app/data/processing/DownloadOperationHandler.kt')
assert_in(dl_handler, 'LargeResultStrategy.forPageOutputs(totalPages, context.budget.maxOutputFiles)', 'downloaded PDF-to-images archive fanout guard')
assert_in(dl_handler, 'context.outputs.find(request.id, pageKey)', 'downloaded PDF-to-images per-page retry idempotency')
assert_in(pdf, 'PageTextComparison.anyPageContains', 'lazy page text comparison')
# The per-page reader and the bounded search are what kept the old verification
# from copying the whole document; they are still the only way text is read.
assert_in(pdf, 'fun pageText(doc: PDDocument, zeroBasedIndex: Int)', 'per-page text reader')
assert_in(pdf, 'PageTextComparison.anyPageContains', 'bounded page text search')
assert_in(pdf, 'fun containsText(doc: PDDocument, needle: String)', 'bounded text lookup')
assert 'val aTexts = pageTexts(a)' not in pdf, 'PDF text comparison still creates full-document copies'
assert 'val pages = doc.pages.toList()' not in pdf, 'PDF info still creates redundant full-page list'
print('PASS large document Android wiring checks')
