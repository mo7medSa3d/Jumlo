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
        self.assertIn('composable<ScanRoute>', nav)
        self.assertIn('onScannedPdf={file -> scanPublishing=true; viewModel.publishScannedPdf(file,onFinished={scanPublishing=false})',nav)
        self.assertIn('externalBusy=scanPublishing',nav)
        self.assertIn('navController.currentBackStackEntry?.id==entryId',nav)
        # A finished scan must keep its entry origin when the viewer replaces it.
        compact = re.sub(r'\s+', '', nav)
        self.assertIn('navController.navigate(ViewerRoute(output.uri,output.displayName,returnTab=transitionOrigin))',compact)
        self.assertIn('popUpTo<ScanRoute>{inclusive=true}',compact)
        self.assertIn('entry.toRoute<ScanRoute>().transitionOrigin',compact)
        self.assertIn('{navigateTo("scan")}',nav)
        self.assertIn('"scan" -> com.jumlo.app.ui.scan.ScanScreen', nav)
        self.assertIn('onScan: () -> Unit', dock)
        # The center slot is Home; Scan and Print are explicit actions in Tools.
        self.assertIn('MainDockTabs = listOf(DockTab.TOOLS, DockTab.FILES, DockTab.HOME, DockTab.DOWNLOADS, DockTab.SETTINGS)', dock)
        self.assertIn('MainDockTabs.forEach', dock)
        self.assertIn('destination == selectedTab', dock)
        self.assertIn('.testTag("nav.indicator.${tab.name}")', dock)
        self.assertIn('.selectable(selected = active, role = Role.Tab', dock)
        self.assertIn('DownloadsRoute', nav)
        self.assertIn('com.jumlo.app.ui.downloads.DownloadsScreen', nav)

    def test_home_uses_real_tool_catalog_and_outputs(self):
        home = read('ui/home/HomeScreen.kt')
        self.assertIn('CapabilityRegistry.enabled(id)', home)
        for label in ('modern_scan', 'modern_convert', 'modern_compress', 'modern_download'):
            self.assertIn('R.string.' + label, home)
        self.assertIn('HomeQuickAction(', home)
        self.assertIn('open("pdf_compress")', home)
        self.assertIn('open("dl_media")', home)
        self.assertIn('NeonOutputPreview(output', home)
        self.assertIn('items(outputs.take(8)', home)
        self.assertNotIn('HomeActionEditor(', home)
        self.assertNotIn('QuietSearchBar(', home)
        self.assertIn('fileDateLabel(output.createdAtEpochMs)', home)
        self.assertIn('onFiles: () -> Unit', home)
        self.assertIn('onDownloads: () -> Unit', home)
        self.assertNotIn('Mohamed Saad', home)
        self.assertNotIn('Pro subscription', home)

    def test_pdf_and_image_previews_are_not_fake(self):
        components = read('ui/components/NeonUi.kt')
        # PDFium now goes through the API 26/27 parser/cancellation safety factory.
        self.assertIn('PdfRendererFactory.open(fd, context.cacheDir) { coroutine.ensureActive() }', components)
        self.assertIn('pdf.openPage(0).use', components)
        self.assertIn('page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)', components)
        factory = read('data/pdf/PdfRendererFactory.kt')
        self.assertIn('preflight(descriptor, scratchDirectory, checkCancellation)', factory)
        self.assertIn('return PdfRenderer(descriptor)', factory)
        self.assertIn('if (document.isEncrypted) throw OperationFailure("PDF_ENCRYPTED")', factory)
        self.assertIn('parser.setLenient(false)', factory)
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
                     'vm.setFileViewMode(', 'onImport()',
                     'SaveFolderCard(settings.downloadDestinationFolder, onChangeSaveFolder, onSavedFolder)'):
            with self.subTest(item=text):
                self.assertIn(text, files)
        self.assertIn('selectedOutputs.isNotEmpty()', files)
        self.assertIn('if(!selecting)', files)

    def test_flat_tool_keys_are_stable_and_selected_archive_is_named_correctly(self):
        tools = read('ui/tools/ToolsScreen.kt')
        files = read('ui/files/FilesScreen.kt')
        # Every available tool gets a stable ID key; the brief removes category groups.
        self.assertIn('CapabilityRegistry.visible(it.id) && CapabilityRegistry.enabled(it.id)', tools)
        self.assertIn('settings.rankedToolIds', tools)
        self.assertIn('items(visible, key = { it.id }, contentType = { "tool" })', tools)
        self.assertIn('ToolTile(tool, language, { onTool(tool) }', tools)
        self.assertEqual(1, tools.count('QuietSearchBar('))
        self.assertNotIn('item("workspace-"', tools)
        self.assertNotIn('groupBy(', tools)
        self.assertIn('R.string.files_bundle_selected,selectedOutputs.size', files)

    def test_settings_and_downloads_keep_real_data(self):
        settings = read('ui/settings/SettingsScreen.kt')
        downloads = read('ui/downloads/DownloadsScreen.kt')
        self.assertIn('storage.outputs', settings)
        self.assertIn('viewModel.setDefaultVideoQuality', settings)
        self.assertIn('viewModel.setDefaultAudioQuality', settings)
        self.assertIn('viewModel.cleanTemporary()', settings)
        self.assertIn('onChooseDownloadFolder', settings)
        self.assertIn('SaveFolderCard(', settings)
        for obsolete in ('wifiOnlyDefault', 'maxConcurrentDownloads', 'allowHttp', 'setHapticIntensity', 'respectReducedMotion'):
            self.assertNotIn(obsolete, settings)
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
