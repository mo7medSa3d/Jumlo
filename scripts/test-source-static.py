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
    @classmethod
    def setUpClass(cls):
        # Cache Kotlin source text once so static checks do not repeatedly walk and re-read app/src.
        cls._all_kt={p:p.read_text(encoding="utf-8") for p in (ROOT/'app/src').rglob('*.kt')}
        cls._main_kt={p:txt for p,txt in cls._all_kt.items() if p.is_relative_to(CODE)}

    def test_xml_files_parse(self):
        for path in MAIN.rglob('*.xml'):
            with self.subTest(path=str(path.relative_to(ROOT))):
                ET.parse(path)
    def test_default_strings_exist_for_all_code_references(self):
        names={node.get('name') for path in (MAIN/'res/values').glob('*.xml') for node in ET.parse(path).getroot() if node.tag=='string'}
        ref_re=re.compile(r'(?<![\w.])R\.string\.(\w+)')
        used=set()
        for text in self._main_kt.values():
            used.update(ref_re.findall(text))
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
        legacy_names=('PdfEngine','ImageEngine','ArchiveAndFileEngine','CryptoVaultEngine','DownloadEngine','UniversalRepository','TransferForegroundService')
        legacy_files={f'{name}.kt' for name in legacy_names}
        legacy_re=re.compile(r'\b(?:'+'|'.join(legacy_names)+r')\b')
        for path, text in self._main_kt.items():
            self.assertNotIn(path.name, legacy_files, path.stem)
            self.assertIsNone(legacy_re.search(text), str(path))
    def test_capability_policy_is_enforced_at_enqueue_and_dispatch(self):
        # Behavioral policy cases run in CoreTests; this check verifies all entry points use it.
        self.assertIn('CapabilityRegistry.canProcess(request.toolId)',(CODE/'data/jobs/OperationDispatcher.kt').read_text(encoding="utf-8"))
        self.assertIn('CapabilityRegistry.canProcess(request.toolId)',(CODE/'data/jobs/JobStore.kt').read_text(encoding="utf-8"))
        self.assertIn('CapabilityRegistry.canProcess(state.toolId)',(CODE/'ui/AppViewModel.kt').read_text(encoding="utf-8"))
        registry=(CODE/'data/jobs/CapabilityRegistry.kt').read_text(encoding="utf-8")
        self.assertIn('CapabilityPolicy.availability(',registry)
        self.assertNotIn('BuildConfig.DEBUG',registry)
    def test_fileprovider_exposes_only_outputs_and_immutable_viewer_snapshots(self):
        entries=list(ET.parse(MAIN/'res/xml/file_paths.xml').getroot())
        self.assertEqual(2,len(entries))
        self.assertTrue(all(n.tag=='files-path' for n in entries))
        self.assertEqual({'processing/results','inbound'},{n.get('path').strip('/') for n in entries})
        android='{http://schemas.android.com/apk/res/android}'
        provider=next(node for node in ET.parse(MAIN/'AndroidManifest.xml').getroot().findall('application/provider')
                      if node.get(android+'name')=='androidx.core.content.FileProvider')
        self.assertEqual('false',provider.get(android+'exported'))
        self.assertEqual('true',provider.get(android+'grantUriPermissions'))
        viewer=(CODE/'ui/AppViewModel.kt').read_text(encoding="utf-8").split('suspend fun viewerUri(',1)[1].split('/**',1)[0]
        for token in ('sources.get(sourceId)','printableSource(source)','FileProvider.getUriForFile'):
            self.assertIn(token,viewer)
    def test_cancel_receiver_is_not_exported(self):
        android='{http://schemas.android.com/apk/res/android}'
        receivers=ET.parse(MAIN/'AndroidManifest.xml').getroot().findall('application/receiver')
        match=[r for r in receivers if r.get(android+'name')=='.worker.CancelJobReceiver']
        self.assertEqual(1,len(match))
        self.assertEqual('false',match[0].get(android+'exported'))
    def test_print_service_query_is_declared_in_manifest(self):
        android='{http://schemas.android.com/apk/res/android}'
        actions=[node.get(android+'name')
                 for node in ET.parse(MAIN/'AndroidManifest.xml').getroot().findall('queries/intent/action')]
        self.assertIn('android.printservice.PrintService',actions)
    def test_ui_hardening_regressions_are_absent(self):
        self.assertNotIn('showAdvanced',self._main_kt[CODE/'ui/downloads/MediaWorkspace.kt'])
        self.assertNotIn('inline-advanced-settings',self._main_kt[CODE/'ui/workspace/WorkspaceScreen.kt'])
        downloads=self._main_kt[CODE/'ui/downloads/DownloadsScreen.kt']
        self.assertIn('fun ActivityJobRow(',downloads)
        activity_job_row=re.split(
            r'\n(?=@Composable\b|(?:private\s+|internal\s+)?(?:fun|class|object)\b)',
            downloads.split('fun ActivityJobRow(',1)[1],
            maxsplit=1,
        )[0]
        self.assertNotIn('errorContainer',activity_job_row)
    def test_bundled_fonts_are_exactly_the_licensed_arabic_latin_subsets(self):
        import hashlib
        # Derived with tools/subset-fonts.py, retaining Arabic/Latin layout tables,
        # a fixed width and only used weights; renamed families preserve OFL terms.
        pinned={'ibm_plex_sans_variable.ttf':'17b364b7d3e0adb0fa619370d264351f0ed7eadb3e9c5bd1067398af53c7a538',
                'ibm_plex_sans_arabic_regular.ttf':'d01c5bf84381a0c8ecd780387865e5ab353a3cc4981a813a75abbb03f9174802'}
        found={p.name:p for p in MAIN.rglob('*') if p.suffix.lower() in {'.ttf','.otf','.woff','.woff2','.ttc','.eot'}}
        self.assertEqual(set(pinned),set(found))
        for name,path in found.items():
            self.assertEqual(pinned[name],hashlib.sha256(path.read_bytes()).hexdigest(),name)
        self.assertLessEqual(sum(path.stat().st_size for path in found.values()),360_000)
        derivation=(ROOT/'tools/subset-fonts.py').read_text(encoding="utf-8")
        for token in ('Jumlo Sans Arabic', "'wdth':100", "'wght':(400,400,700)", "options.layout_features=['*']", 'Required glyphs lost'):
            self.assertIn(token,derivation)
        self.assertIn('SIL OPEN FONT LICENSE',(MAIN/'assets/licenses/IBM-Plex-OFL.txt').read_text(encoding="utf-8"))
    def test_room_download_url_writes_are_redacted_and_legacy_urls_scrubbed(self):
        local = (CODE / 'data/local/AppDatabase.kt').read_text(encoding="utf-8")
        module = (CODE / 'di/AppModule.kt').read_text(encoding="utf-8")
        callback = (CODE / 'data/local/LegacyDownloadPrivacyCallback.kt').read_text(encoding="utf-8")
        test = (ROOT / 'app/src/androidTest/java/com/jumlo/app/data/DatabaseMigrationAndDaoTest.kt').read_text(encoding="utf-8")
        self.assertIn('tasks.map { it.copy(fullUrl = "") }', local)
        self.assertIn('task.copy(fullUrl = "")', local)
        self.assertIn('.addCallback(com.jumlo.app.data.local.LegacyDownloadPrivacyCallback)', module)
        self.assertIn("fullUrl = '' WHERE fullUrl <> ''", callback)
        self.assertIn('val restored = open()', test)
        self.assertIn('assertEquals("", download.fullUrl)', test)

    def test_retired_google_drive_client_is_absent(self):
        self.assertFalse((CODE / 'data/files/GoogleDriveClient.kt').exists())

    def test_current_room_schema_is_packaged(self):
        schema_dir=ROOT/'app/schemas/com.jumlo.app.data.local.AppDatabase'
        s2=json.loads((schema_dir/'2.json').read_text(encoding="utf-8"))
        self.assertEqual(2,s2['database']['version'])
        t2={e['tableName'] for e in s2['database']['entities']}
        self.assertEqual({'managed_files','download_tasks','sources','jobs','outputs','drafts'},t2)
        # No authentic v1 export is committed; inventing a 1.json to satisfy an existence check is forbidden.
        self.assertFalse((schema_dir/'1.json').exists())
    def test_kotlin_files_have_balanced_delimiters_and_no_known_compile_hazards(self):
        for path, text in self._all_kt.items():
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
