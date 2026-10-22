#!/usr/bin/env python3
"""Jumlo 2026 reference-inspired UI source contracts; native rendering requires Android CI."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/com/jumlo/app'

def read(path):
    return (SOURCE / path).read_text(encoding='utf-8')

class NeonReferenceRedesign(unittest.TestCase):
    def test_identity_and_native_scan_are_preserved(self):
        nav = read('ui/navigation/JumloNavHost.kt')
        dock = read('ui/components/QuietNavigation.kt')
        self.assertIn('onScan={navigateTo("scan")}', nav)
        self.assertIn('"scan" -> com.jumlo.app.ui.scan.ScanScreen', nav)
        self.assertIn('onScan: () -> Unit', dock)
        # Four destinations and the native scan action in the middle. Settings is
        # reached from the home screen, not from a "me" tab.
        self.assertIn('DockDestination(DockTab.HOME', dock)
        self.assertIn('DockDestination(DockTab.TOOLS', dock)
        self.assertIn('DockDestination(DockTab.DOWNLOADS', dock)
        self.assertIn('DockDestination(DockTab.FILES', dock)
        self.assertIn('DockTab.DOWNLOADS -> 4', dock)
        self.assertIn('DownloadsRoute', nav)
        self.assertIn('com.jumlo.app.ui.downloads.DownloadsScreen', nav)

    def test_home_uses_real_tool_catalog_and_outputs(self):
        home = read('ui/home/HomeScreen.kt')
        self.assertIn('CapabilityRegistry.canProcess(it.id)', home)
        self.assertIn('HomeActionPolicy.resolve(', home)
        self.assertIn('NeonHero(', home)
        self.assertIn('NeonToolTile(', home)
        self.assertIn('NeonOutputPreview(output', home)
        self.assertIn('items(outputs.take(8)', home)
        self.assertIn('HomeActionEditor(', home)
        self.assertIn('onFiles: () -> Unit', home)
        self.assertIn('onDownloads: () -> Unit', home)
        self.assertNotIn('Mohamed Saad', home)
        self.assertNotIn('Pro subscription', home)

    def test_pdf_and_image_previews_are_not_fake(self):
        components = read('ui/components/NeonUi.kt')
        self.assertIn('PdfRenderer(fd)', components)
        self.assertIn('Dispatchers.IO', components)
        self.assertIn('Bitmap.createBitmap', components)
        self.assertIn('output.uri', components)
        self.assertIn('AsyncImage(', components)
        self.assertIn('ModernToolIcon(tool.iconName', components)

    def test_files_preserve_real_mutations_and_selection(self):
        files = read('ui/files/FilesScreen.kt')
        for text in ('NeonOutputPreview(output', 'NeonBatchAction(',
                     'Intent.ACTION_SEND_MULTIPLE', 'onOutputWorkflow(selectedOutputs,"file_07")',
                     'onOutputWorkflow(selectedOutputs,"pdf_03")',
                     'onOutputWorkflow(selectedOutputs,"pdf_02")',
                     'vm.trashOutputs(', 'vm.restoreOutput(', 'vm.purgeTrashIds(',
                     'vm.setFileViewMode(', 'onImport()', 'onSavedFolder()'):
            with self.subTest(item=text):
                self.assertIn(text, files)
        self.assertIn('selectedOutputs.isNotEmpty()', files)
        self.assertIn('if(!selecting)', files)

    def test_tool_group_keys_are_unique_and_selected_archive_is_named_correctly(self):
        tools = read('ui/tools/ToolsScreen.kt')
        files = read('ui/files/FilesScreen.kt')
        # The catalogue is grouped by workspace, and every group key is unique.
        self.assertIn('item("workspace-" + entry.workspace.id)', tools)
        self.assertNotIn('\\${pillar.name}', tools)
        self.assertIn('R.string.files_bundle_selected,selectedOutputs.size', files)

    def test_settings_and_downloads_keep_real_data(self):
        settings = read('ui/settings/SettingsScreen.kt')
        downloads = read('ui/downloads/DownloadsScreen.kt')
        self.assertIn('storage.outputs', settings)
        self.assertIn('savedOutputs.size', settings)
        self.assertIn('viewModel::setSoundsEnabled', settings)
        self.assertIn('onChooseDownloadFolder', settings)
        self.assertIn('NeonPanel(', settings)
        self.assertIn('NeonPanel(', downloads)
        self.assertIn('onTool(mediaTool)', downloads)
        self.assertIn('items(activeDownloads', downloads)
        self.assertIn('items(previousDownloads', downloads)

    def test_en_ar_resources_match_and_no_static_profile_claims(self):
        en = ET.parse(ROOT/'app/src/main/res/values/neon_2026.xml').getroot()
        ar = ET.parse(ROOT/'app/src/main/res/values-ar/neon_2026.xml').getroot()
        keys = lambda xml: {it.get('name') for it in xml if it.tag=='string'}
        self.assertEqual(keys(en), keys(ar))
        self.assertIn('neon_scan_action', keys(en))
        self.assertGreater(len(keys(en)), 30)
        for name in ('app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt',
                     'app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt'):
            content=(ROOT/name).read_text(encoding='utf-8')
            self.assertNotIn('1002 left', content)
            self.assertNotIn('Expires on:', content)
            self.assertNotIn('Mohamed Saad', content)

if __name__ == '__main__':
    unittest.main(verbosity=2)
