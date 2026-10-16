#!/usr/bin/env python3
"""Narrow source/package consistency checks. Does not replace Android lint or compilation."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
MAIN=ROOT/'app/src/main'
CODE=MAIN/'java/com/jumlo/app'

class SourceConsistencyTests(unittest.TestCase):
    def test_xml_files_parse(self):
        for path in MAIN.rglob('*.xml'):
            with self.subTest(path=str(path.relative_to(ROOT))):
                ET.parse(path)
    def test_default_strings_exist_for_all_code_references(self):
        names={node.get('name') for path in (MAIN/'res/values').glob('*.xml') for node in ET.parse(path).getroot() if node.tag=='string'}
        used=set()
        for path in CODE.rglob('*.kt'):
            used.update(re.findall(r'(?<![\w.])R\.string\.(\w+)',path.read_text()))
        self.assertEqual(set(),used-names)
    def test_resource_names_are_unique_per_locale(self):
        for folder in (MAIN/'res').glob('values*'):
            keys=[n.get('name') for path in folder.glob('*.xml') for n in ET.parse(path).getroot() if n.tag=='string']
            self.assertEqual(len(keys),len(set(keys)),str(folder))
    def test_legacy_simulated_engines_are_not_in_production(self):
        for name in ('PdfEngine','ImageEngine','ArchiveAndFileEngine','CryptoVaultEngine','DownloadEngine','UniversalRepository','TransferForegroundService'):
            self.assertFalse(list(CODE.rglob(f'{name}.kt')),name)
            for path in CODE.rglob('*.kt'):
                self.assertIsNone(re.search(r'\b'+name+r'\b',path.read_text()),str(path))
    def test_candidate_gate_is_enforced_at_dispatch(self):
        self.assertIn('CapabilityRegistry.enabled(request.toolId)',(CODE/'data/jobs/OperationDispatcher.kt').read_text())
        self.assertIn('BuildConfig.DEBUG && Capabilities.isEnabled(id)',(CODE/'data/jobs/CapabilityRegistry.kt').read_text())
    def test_only_published_files_have_fileprovider_paths(self):
        entries=list(ET.parse(MAIN/'res/xml/file_paths.xml').getroot())
        self.assertTrue(entries)
        self.assertTrue(all(n.tag=='files-path' and n.get('path').strip('/')=='processing/results' for n in entries))
    def test_cancel_receiver_is_not_exported(self):
        android='{http://schemas.android.com/apk/res/android}'
        receivers=ET.parse(MAIN/'AndroidManifest.xml').getroot().findall('application/receiver')
        match=[r for r in receivers if r.get(android+'name')=='.worker.CancelJobReceiver']
        self.assertEqual(1,len(match))
        self.assertEqual('false',match[0].get(android+'exported'))
    def test_no_font_binaries_in_project_sources(self):
        prohibited={'.ttf','.otf','.woff','.woff2','.ttc','.eot'}
        self.assertEqual([], [str(p) for p in MAIN.rglob('*') if p.suffix.lower() in prohibited])

if __name__=='__main__':
    unittest.main(verbosity=2)
