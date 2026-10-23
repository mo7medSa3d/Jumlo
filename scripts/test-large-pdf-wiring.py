#!/usr/bin/env python3
"""Static contracts for Android-only large-document integrations; not a device test."""
from pathlib import Path
import re
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
# Compression moved into one runner shared with the measured preview. Follow
# that delegation and keep guarding the actual bounded/validated fallback path.
compression = handler.split('private suspend fun compressPdfGuaranteedSmaller(', 1)[1].split('private suspend fun seekAll(', 1)[0]
for fragment, title in (
    ('PdfCompressionRunner.compress(source, out', 'shared compression implementation'),
    ('AndroidImageCodec(context.budget.maxBitmapBytes)', 'bounded Android image codec'),
    ('maxWorkingBytes = context.budget.maxBitmapBytes', 'propagated bitmap working budget'),
    ('minFreeBytes = context.budget.minFreeBytes', 'propagated disk reserve'),
    ('checkCancel = context.checkCancellation', 'propagated cancellation'),
):
    assert_in(compression, fragment, title)
runner = src('app/src/main/java/com/jumlo/app/data/pdf/PdfCompressionRunner.kt')
measurement = runner.split('fun measure(', 1)[1].split('fun compress(', 1)[0]
assert_in(measurement, 'return compress(source, File(directory, "measured.pdf")', 'preview uses the publication compressor')
for fragment, title in (
    ('source.canonicalFile != destination.canonicalFile', 'immutable source/output separation'),
    ('val storageLimit = scratch.usableSpace - minFreeBytes', 'bounded PDF scratch storage'),
    ('MemoryUsageSetting.setupTempFileOnly(storageLimit).setTempDir(scratch)', 'temp-backed bounded PDF parsing'),
    ('checkCancel()', 'runner cancellation callback'),
    ('require(parent.usableSpace >= minFreeBytes)', 'free-space checks during compression'),
    ('maxWorkingBytes = maxWorkingBytes, checkCancel = ::check', 'compressor budget and per-image cancellation'),
    ('val candidatePages = open(candidate, directory, minFreeBytes).use { it.numberOfPages }', 'candidate reopen before selection'),
    ('PdfCompressionPolicy.betterCandidate(originalBytes, bestBytes, candidate.length(), pageCount, candidatePages)', 'smaller candidate with preserved page count'),
    ('val ready = if (best.isFile) best else File(directory, "unchanged.pdf").also { copy(source, it, ::check) }', 'valid original fallback when no candidate improves the source'),
    ('require(ready.length() in 1..originalBytes)', 'nonempty output never larger than source'),
    ('Files.move(ready.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)', 'complete candidate promoted at the end'),
):
    assert_in(runner, fragment, title)
publication = runner.split('fun compress(', 1)[1].split('private fun open(', 1)[0]
for owned_run in (measurement, publication):
    assert re.search(r'finally\s*\{\s*directory\.deleteRecursively\(\)', owned_run), 'Compression/preview must release owned scratch on every exit'
copy = runner.split('private fun copy(', 1)[1]
assert_in(copy, 'ByteArray(64 * 1024)', 'bounded original-copy buffer')
assert re.search(r'while\s*\(true\)\s*\{\s*check\(\)\s*val count = input\.read\(buffer\)', copy), 'Original fallback must check cancellation/disk reserve between bounded reads'
assert 'readBytes(' not in runner, 'Compression must not retain whole PDF byte arrays'
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
