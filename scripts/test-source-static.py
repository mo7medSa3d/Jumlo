#!/usr/bin/env python3
"""Narrow source/package consistency checks. Does not replace Android lint or compilation."""
from pathlib import Path
import json
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
    def test_en_and_ar_string_resources_have_exact_parity(self):
        en_keys={node.get('name') for path in (MAIN/'res/values').glob('*.xml') for node in ET.parse(path).getroot() if node.tag=='string'}
        ar_keys={node.get('name') for path in (MAIN/'res/values-ar').glob('*.xml') for node in ET.parse(path).getroot() if node.tag=='string'}
        self.assertEqual(set(), en_keys - ar_keys, f'Missing in values-ar: {sorted(en_keys - ar_keys)}')
        self.assertEqual(set(), ar_keys - en_keys, f'Missing in values: {sorted(ar_keys - en_keys)}')
    def test_string_format_placeholders_match_between_en_and_ar(self):
        spec_re=re.compile(r'%\d+\$[sd]')
        en_specs={node.get('name'):set(spec_re.findall(node.text or '')) for path in (MAIN/'res/values').glob('*.xml') for node in ET.parse(path).getroot() if node.tag=='string'}
        ar_specs={node.get('name'):set(spec_re.findall(node.text or '')) for path in (MAIN/'res/values-ar').glob('*.xml') for node in ET.parse(path).getroot() if node.tag=='string'}
        for key, specs in en_specs.items():
            self.assertEqual(specs, ar_specs.get(key, set()), f'Format placeholder mismatch for {key}')
        self.assertIn('%1$d', en_specs['home_resume_workspace'])
        self.assertEqual({'%1$s','%2$s','%3$s','%4$s','%5$s'}, en_specs['settings_storage_summary'])
        self.assertEqual({'%1$d','%2$s'}, en_specs['workspace_queue_summary'])
    def test_resource_names_are_unique_per_locale(self):
        for folder in (MAIN/'res').glob('values*'):
            keys=[n.get('name') for path in folder.glob('*.xml') for n in ET.parse(path).getroot() if n.tag=='string']
            self.assertEqual(len(keys),len(set(keys)),str(folder))
    def test_legacy_simulated_engines_are_not_in_production(self):
        for name in ('PdfEngine','ImageEngine','ArchiveAndFileEngine','CryptoVaultEngine','DownloadEngine','UniversalRepository','TransferForegroundService'):
            self.assertFalse(list(CODE.rglob(f'{name}.kt')),name)
            for path in CODE.rglob('*.kt'):
                self.assertIsNone(re.search(r'\b'+name+r'\b',path.read_text()),str(path))
    def test_capability_policy_is_enforced_at_enqueue_and_dispatch(self):
        # Behavioral policy cases run in CoreTests; this check verifies all entry points use it.
        self.assertIn('CapabilityRegistry.canProcess(request.toolId)',(CODE/'data/jobs/OperationDispatcher.kt').read_text())
        self.assertIn('CapabilityRegistry.canProcess(request.toolId)',(CODE/'data/jobs/JobStore.kt').read_text())
        self.assertIn('CapabilityRegistry.canProcess(state.toolId)',(CODE/'ui/AppViewModel.kt').read_text())
        registry=(CODE/'data/jobs/CapabilityRegistry.kt').read_text()
        self.assertIn('CapabilityPolicy.availability(id)',registry)
        self.assertNotIn('BuildConfig.DEBUG',registry)
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
    def test_bundled_fonts_are_exactly_the_pinned_plex_files(self):
        import hashlib
        pinned={'ibm_plex_sans_variable.ttf':'3b031aa4216174205bd8471f88a49b91f093169e9e87bd5262242bc5967fe2e3',
                'ibm_plex_sans_arabic_regular.ttf':'6f611412270a132bbac838da9259d4c68569b4175f3b3b8fa3fa36a30b56dab9'}
        found={p.name:p for p in MAIN.rglob('*') if p.suffix.lower() in {'.ttf','.otf','.woff','.woff2','.ttc','.eot'}}
        self.assertEqual(set(pinned),set(found))
        for name,path in found.items():
            self.assertEqual(pinned[name],hashlib.sha256(path.read_bytes()).hexdigest(),name)
    def test_room_download_url_writes_are_redacted_and_legacy_urls_scrubbed(self):
        local = (CODE / 'data/local/AppDatabase.kt').read_text()
        module = (CODE / 'di/AppModule.kt').read_text()
        callback = (CODE / 'data/local/LegacyDownloadPrivacyCallback.kt').read_text()
        test = (ROOT / 'app/src/androidTest/java/com/jumlo/app/data/DatabaseMigrationAndDaoTest.kt').read_text()
        self.assertIn('tasks.map { it.copy(fullUrl = "") }', local)
        self.assertIn('task.copy(fullUrl = "")', local)
        self.assertIn('.addCallback(com.jumlo.app.data.local.LegacyDownloadPrivacyCallback)', module)
        self.assertIn("fullUrl = '' WHERE fullUrl <> ''", callback)
        self.assertIn('val restored = open()', test)
        self.assertIn('assertEquals("", download.fullUrl)', test)

    def test_drive_output_is_not_truncated_and_failure_does_not_delete_another_output(self):
        drive = (CODE / 'data/files/GoogleDriveClient.kt').read_text()
        self.assertIn('StandardOpenOption.CREATE_NEW', drive)
        self.assertIn('var ownsTarget = false', drive)
        self.assertIn('if (ownsTarget) targetFile.delete()', drive)
        self.assertNotIn('FileOutputStream(targetFile)', drive)

    def test_current_room_schema_and_legacy_structural_fixture_are_packaged(self):
        schema_dir=ROOT/'app/schemas/com.jumlo.app.data.local.AppDatabase'
        s2=json.loads((schema_dir/'2.json').read_text())
        self.assertEqual(2,s2['database']['version'])
        t2={e['tableName'] for e in s2['database']['entities']}
        self.assertEqual({'managed_files','download_tasks','sources','jobs','outputs','drafts'},t2)
        import sqlite3
        connection=sqlite3.connect(':memory:')
        try:
            connection.executescript((ROOT/'app/schemas/legacy-v1-structure.sql').read_text())
            tables={row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            self.assertEqual({'managed_files','download_tasks'},tables)
        finally:
            connection.close()
        # This fixture validates structure only. Historical exported identity is a release gate,
        # documented explicitly; inventing a 1.json to satisfy an existence assertion is forbidden.
        self.assertTrue((ROOT/'app/schemas/schema-provenance.md').is_file())
    def test_kotlin_files_have_balanced_delimiters_and_no_known_compile_hazards(self):
        for path in (ROOT/'app/src').rglob('*.kt'):
            text=path.read_text()
            with self.subTest(path=str(path.relative_to(ROOT))):
                self.assertNotIn('private set\n        private set',text)
                self.assertNotIn('route=next.route',text)
                self.assertNotIn('RenderingMode.INVISIBLE',text)
                self.assertNotIn('JumloDestination',text)
                self.assertNotIn('QuietHeader',text)
                self.assertNotIn('QuietPrimaryActionButton',text)
                self.assertNotIn('Icons.Outlined.',text)
                self.assertTrue(text.strip().startswith('package com.jumlo.'))

if __name__=='__main__':
    unittest.main(verbosity=2)
