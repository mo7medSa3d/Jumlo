#!/usr/bin/env python3
"""Failure fixes: specific codes, specific messages, no silent collapses.

Source-level contracts for the 2026-11-02 failure-fixes push:
- Media init retries once after wiping a stale extracted tree, while
  repair/ABI diagnostic codes propagate with their own strings instead
  of collapsing into the generic runtime banner.
- ArchiveCore.extract maps JDK zip/IO failures to stable codes, and
  every archive code has a message branch plus EN and AR strings.
- Drive token failures are split (missing vs expired vs cancelled
  sign-in) instead of one 401 toast, and failed cards name their input.
- No values file may contain a duplicate string name (the duplicate
  watermark string that once broke the build was caught by presence
  checks only; uniqueness was never asserted).
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'app/src/main/java/com/jumlo/app'
CORE = ROOT / 'core-common/src/main/kotlin/com/jumlo/app/core'
RES = ROOT / 'app/src/main/res'


def read(path):
    return path.read_text(encoding='utf-8')


def string_names(xml_path):
    tree = ET.parse(xml_path)
    return [el.get('name') for el in tree.getroot().iter('string')]


def resource_for(code, messages):
    """The R.string.* a UserMessages branch maps a code to, or None."""
    for line in messages.splitlines():
        if 'code=="%s"' % code in line:
            match = re.search(r'R\.string\.(\w+)', line)
            if match:
                return match.group(1)
    return None


class FailureFixContracts(unittest.TestCase):
    def test_archive_codes_have_messages_and_strings(self):
        messages = read(APP / 'ui/components/UserMessages.kt')
        en = read(RES / 'values/error_messages.xml')
        ar = read(RES / 'values-ar/error_messages.xml')
        codes = set()
        for rel in ('FileProcessing.kt',):
            codes |= set(re.findall(r'"(ARCHIVE_[A-Z_]+)"', read(CORE / rel)))
        codes |= set(re.findall(r'"(ARCHIVE_[A-Z_]+|DUPLICATE_ARCHIVE_ENTRY|OUTPUT_CREATE_FAILED)"',
                                read(APP / 'data/processing/ArchiveOperationHandler.kt')))
        self.assertIn('ARCHIVE_NOT_A_ZIP', codes)
        self.assertIn('ARCHIVE_CORRUPT', codes)
        self.assertIn('ARCHIVE_ENCRYPTED', codes)
        self.assertIn('ARCHIVE_READ_FAILED', codes)
        for code in sorted(codes):
            resource = resource_for(code, messages)
            self.assertIsNotNone(resource, '%s has no UserMessages branch' % code)
            self.assertIn('<string name="%s"' % resource, en, '%s missing EN string' % code)
            self.assertIn('<string name="%s"' % resource, ar, '%s missing AR string' % code)

    def test_drive_codes_have_messages_and_strings(self):
        messages = read(APP / 'ui/components/UserMessages.kt')
        en = read(RES / 'values/error_messages.xml')
        ar = read(RES / 'values-ar/error_messages.xml')
        for code in ('DRIVE_TOKEN_MISSING', 'DRIVE_TOKEN_EXPIRED', 'DRIVE_SIGNIN_CANCELLED'):
            resource = resource_for(code, messages)
            self.assertIsNotNone(resource, '%s has no UserMessages branch' % code)
            self.assertIn('<string name="%s"' % resource, en, '%s missing EN string' % code)
            self.assertIn('<string name="%s"' % resource, ar, '%s missing AR string' % code)

    def test_media_repair_codes_have_messages_and_strings(self):
        messages = read(APP / 'ui/components/UserMessages.kt')
        en = read(RES / 'values/media_strings.xml')
        ar = read(RES / 'values-ar/media_strings.xml')
        for code in ('MEDIA_NATIVE_REPAIR_INVALID', 'MEDIA_NATIVE_REPAIR_UNAVAILABLE'):
            resource = resource_for(code, messages)
            self.assertIsNotNone(resource, '%s has no UserMessages branch' % code)
            self.assertIn('<string name="%s"' % resource, en, '%s missing EN string' % code)
            self.assertIn('<string name="%s"' % resource, ar, '%s missing AR string' % code)

    def test_media_init_retries_once_and_propagates_diagnostics(self):
        factory = read(ROOT / 'app/src/media/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt')
        self.assertGreaterEqual(factory.count('initializeOnce()'), 3,
                                'initializeOnce must be defined once and called twice (try + retry)')
        self.assertGreaterEqual(factory.count('resetExtractedTree()'), 2,
                                'retry must wipe the extracted trees first')
        for code in ('MEDIA_NATIVE_REPAIR_INVALID', 'MEDIA_NATIVE_REPAIR_UNAVAILABLE',
                     'MEDIA_NATIVE_ABI_UNAVAILABLE'):
            self.assertIn(code, factory, '%s must skip the retry' % code)
        install = read(ROOT / 'app/src/media/java/com/jumlo/app/data/media/BundledFfmpegLibraries.kt')
        self.assertIn('OperationFailure("MEDIA_NATIVE_REPAIR_INVALID")', install)
        self.assertIn('OperationFailure("MEDIA_NATIVE_REPAIR_UNAVAILABLE")', install)
        self.assertNotIn('require(', install,
                         'repair validation must throw OperationFailure, not require(), '
                         'or initialize() collapses it into the generic runtime banner')

    def test_transfer_row_shows_source_name(self):
        rows = read(APP / 'ui/components/ProductionRows.kt')
        self.assertIn('sourceName:String?=null', rows)
        self.assertIn('JobStage.FAILED, JobStage.PARTIAL', rows)
        downloads = read(APP / 'ui/downloads/DownloadsScreen.kt')
        self.assertIn('vm.imported', downloads)
        self.assertGreaterEqual(downloads.count('sourceName='), 3,
                                'Downloads lists plus ActivityJobRow must pass sourceName')
        home = read(APP / 'ui/home/HomeScreen.kt')
        self.assertIn('vm.imported', home)
        self.assertIn('sourceName', home)

    def test_drive_split_sites(self):
        client = read(APP / 'data/files/GoogleDriveClient.kt')
        self.assertIn('401 -> "DRIVE_TOKEN_EXPIRED"', client)
        handler = read(APP / 'data/processing/FileExtrasHandler.kt')
        self.assertGreaterEqual(handler.count('DRIVE_TOKEN_MISSING'), 2,
                                'lost handle and empty token are both missing-token')
        view_model = read(APP / 'ui/AppViewModel.kt')
        self.assertIn('DRIVE_TOKEN_MISSING', view_model)
        store = read(APP / 'data/jobs/JobStore.kt')
        self.assertIn('DRIVE_TOKEN_MISSING', store)
        self.assertIn('DRIVE_TOKEN_EXPIRED', store)
        nav = read(APP / 'ui/navigation/JumloNavHost.kt')
        self.assertIn('DRIVE_SIGNIN_CANCELLED', nav)

    def test_no_duplicate_string_names(self):
        # Within one file, and across files of the same qualifier: a
        # repeated name is a build-breaking duplicate resource, even when
        # every key is "present" in both locales.
        by_qualifier = {}
        for xml_path in sorted((RES).glob('values*/**/*.xml')):
            names = string_names(xml_path)
            self.assertEqual(len(names), len(set(names)),
                             '%s has duplicate string names: %s' % (xml_path, sorted(
                                 {n for n in names if names.count(n) > 1})))
            by_qualifier.setdefault(xml_path.parent.name, []).extend(names)
        for qualifier, names in sorted(by_qualifier.items()):
            self.assertEqual(len(names), len(set(names)),
                             '%s has duplicate string names across files: %s' % (qualifier, sorted(
                                 {n for n in names if names.count(n) > 1})))


if __name__ == '__main__':
    unittest.main()
