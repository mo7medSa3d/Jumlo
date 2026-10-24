#!/usr/bin/env python3
"""Jumlo UI design-system contract tests (source and contrast checks, not device tests)."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'app/src/main/java/com/jumlo/app/ui'

def source(relative):
    return (UI / relative).read_text(encoding='utf-8')

def luminance(rgb):
    channels = [int(rgb[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in channels]
    return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))

def ratio(a, b):
    light, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (light + .05) / (dark + .05)

class PremiumUxContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tokens = dict(re.findall(r'^val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', source('theme/Color.kt'), flags=re.M))

    def color(self, name):
        self.assertIn(name, self.tokens)
        return self.tokens[name]

    def test_brand_palette_is_consistent_across_light_and_dark(self):
        # Neutral surfaces preserve pure OLED black in every accent mode.
        expected = {
            'LightBackground': 'F6F6F8', 'LightSurface': 'FFFFFF',
            'LightOnSurface': '202024', 'LightOnSurfaceVariant': '55555E',
            'LightPrimary': '2F46C6', 'LightTertiary': '7050AB',
            'DarkBackground': '000000',
            'DarkSurface': '101010', 'DarkOnSurface': 'F5F5F5',
            'DarkPrimary': 'A9B7FF', 'DarkTertiary': 'D3B7FC',
        }
        for key, value in expected.items():
            with self.subTest(key=key):
                self.assertEqual(self.color(key).upper(), value)

    def test_semantic_text_pairs_have_wcag_aa_contrast(self):
        pairs = (
            ('LightOnSurface', 'LightBackground'),
            ('LightOnSurface', 'LightSurface'),
            ('LightOnSurfaceVariant', 'LightSurface'),
            ('LightOnPrimary', 'LightPrimary'),
            ('LightOnPrimaryContainer', 'LightPrimaryContainer'),
            ('LightOnSecondary', 'LightSecondary'),
            ('LightOnTertiary', 'LightTertiary'),
            ('LightOnError', 'LightError'),
            ('DarkOnSurface', 'DarkBackground'),
            ('DarkOnSurface', 'DarkSurface'),
            ('DarkOnSurfaceVariant', 'DarkSurface'),
            ('DarkOnPrimary', 'DarkPrimary'),
            ('DarkOnPrimaryContainer', 'DarkPrimaryContainer'),
            ('DarkOnSecondary', 'DarkSecondary'),
            ('DarkOnTertiary', 'DarkTertiary'),
            ('DarkOnError', 'DarkError'),
        )
        for foreground, background in pairs:
            with self.subTest(foreground=foreground, background=background):
                contrast = ratio(self.color(foreground), self.color(background))
                self.assertGreaterEqual(contrast, 4.5, f'{foreground} / {background}: {contrast:.2f}')

    def test_splash_uses_cohesive_palette_family(self):
        splash = source('components/JumloSplash.kt')
        self.assertIn('Color(0xFF0C1231)', splash)
        self.assertIn('Color(0xFF4434A9)', splash)
        self.assertIn('Color(0xFFE0E5FF)', splash)
        self.assertNotIn('Color(0xFF2221A1)', splash)

    def test_tool_catalog_adapts_to_tablet_and_fold_safe_width(self):
        code = source('tools/ToolsScreen.kt')
        self.assertIn('BoxWithConstraints(', code)
        # The catalogue is one keyed lazy grid whose sections and full-width
        # headers are emitted inside it; search still appears exactly once.
        self.assertEqual(1, code.count('LazyVerticalGrid('))
        self.assertIn('columns = GridCells.Fixed(columns)', code)
        self.assertIn('GridItemSpan(maxLineSpan)', code)
        self.assertIn('ToolSection(', code)
        self.assertIn('SectionHeader(', code)
        self.assertIn('items(filtered, key = { it.id }, contentType = { "tool" })', code)
        self.assertIn('LocalDensity.current.fontScale >= 1.6f', code)
        self.assertRegex(code, r'largeText\s*->\s*1')
        self.assertNotIn('primary.chunked(', code)
        self.assertIn('widthIn(max = 1000.dp)', code)
        self.assertIn('maxWidth >= 600.dp', code)

    def test_home_and_tools_centered_width_caps_are_applied_before_filling(self):
        # An outer fillMaxSize prevents a later widthIn from constraining the child.
        self.assertIn('Modifier.widthIn(max = 980.dp).fillMaxSize()', source('home/HomeScreen.kt'))
        self.assertIn('Modifier.widthIn(max = 1000.dp).fillMaxSize()', source('tools/ToolsScreen.kt'))

    def test_home_quick_actions_have_four_distinct_filled_icons(self):
        code = source('home/HomeScreen.kt')
        for label, icon in [('scan','scan'),('convert','document'),('compress','compress'),('download','download')]:
            self.assertIn(f'Triple(R.string.modern_{label}, R.drawable.ic_prism_{icon}',code)
        self.assertIn('HomeQuickAction(',code)
        self.assertIn('heightIn(min = 92.dp)',code)
        self.assertIn('maxLines = 2',code)
        self.assertNotIn('QuietSearchBar(',code)

    def test_dynamic_color_persists_and_preserves_neutral_surfaces(self):
        settings = source('settings/SettingsScreen.kt')
        theme = source('theme/Theme.kt')
        self.assertIn('settings.accentColor == "DYNAMIC"',settings)
        self.assertIn('viewModel.setAccentColor(if (it) "DYNAMIC" else "BLUE")',settings)
        self.assertIn('Build.VERSION.SDK_INT >= 31',settings)
        self.assertIn('background = neutral.background',theme)
        self.assertIn('surface = neutral.surface',theme)
        self.assertIn('surfaceTint = Color.Transparent',theme)

    def test_direct_mlkit_scan_returns_a_validated_pdf(self):
        scanner = source('scan/SmartDocumentScanButton.kt')
        self.assertIn('GmsDocumentScanning.getClient(options)',scanner)
        self.assertIn('.setGalleryImportAllowed(true)',scanner)
        formats = re.search(r'\.setResultFormats\(([^)]*)\)', scanner)
        self.assertIsNotNone(formats)
        selected = set(re.findall(r'RESULT_FORMAT_\w+', formats.group(1)))
        # PDF remains mandatory; JPEG is now required alongside it by Phase 4.
        self.assertTrue({'RESULT_FORMAT_PDF', 'RESULT_FORMAT_JPEG'} <= selected)
        self.assertIn('SmartScanResultStore.copyPdf(context, pdf.uri, pdf.pageCount)',scanner)
        self.assertIn('latestResult(requireNotNull(owned))',scanner)
        self.assertNotIn('PreviewView',scanner)
        self.assertIn('CameraXScanFallback(onScannedPdf=onScannedPdf',scanner)
        fallback = source('scan/CameraXScanFallback.kt')
        self.assertIn('cameraProvider.bindToLifecycle(lifecycleOwner',fallback)
        self.assertIn('SmartScanResultStore.createPdfFromImages(context,pages)',fallback)

    def test_tab_indicator_is_cell_local_and_honors_system_motion(self):
        palette = source('theme/ToolPalette.kt')
        dock = source('components/QuietNavigation.kt')
        self.assertIn('private val darkAccents',palette)
        self.assertIn('colors.background.luminance()', source('tools/ToolsScreen.kt'))
        self.assertIn('LocalReducedMotion.current',dock)
        self.assertIn('if (reduced) snap()',dock)
        self.assertIn('animateColorAsState(',dock)
        self.assertIn('animateFloatAsState(',dock)
        self.assertIn('Modifier.fillMaxWidth(.88f).widthIn(max = 64.dp).height(34.dp)',dock)
        self.assertNotIn('BorderStroke(',dock)
        self.assertNotIn('.offset(',dock)
        self.assertIn('role = Role.Tab',dock)

    def test_downloads_offer_contextual_one_tap_pause_and_resume(self):
        code = source('components/ProductionRows.kt')
        self.assertIn('onPause()', code)
        self.assertIn('onRetry()', code)
        self.assertIn('R.string.action_pause', code)
        self.assertIn('R.string.action_resume', code)
        self.assertIn('TextButton(', code[code.index('@Composable fun TransferRow'):])

    def test_search_exposes_label_and_search_ime_action(self):
        code = source('components/QuietButtons.kt')
        self.assertIn('contentDescription = placeholder', code)
        self.assertIn('ImeAction.Search', code)
        self.assertIn('KeyboardOptions(', code)

    def test_settings_are_focused_and_keep_real_quality_defaults(self):
        code = source('settings/SettingsScreen.kt')
        for tag in ('settings.theme','settings.language','settings.videoQuality',
                    'settings.audioQuality','settings.storage','settings.cache','settings.about'):
            self.assertIn(tag,code)
        for obsolete in ('setHapticIntensity','setRespectReducedMotion','setWifiOnlyDefault',
                         'setMaxConcurrentDownloads','mediaServerUrl','allowHttp'):
            self.assertNotIn(obsolete,code)
        self.assertIn('viewModel.setDefaultVideoQuality(choices[it])',code)
        self.assertIn('viewModel.setDefaultAudioQuality(choices[it])',code)
        self.assertIn('viewModel.cleanTemporary()',code)

    def test_file_import_is_distinct_primary_action(self):
        code = source('files/FilesScreen.kt')
        heading = code[code.index('item("heading")'):code.index('item("search")')]
        self.assertIn('QuietPrimaryButton(', heading)
        self.assertIn('onClick=onImport', heading)
        self.assertIn('SaveFolderCard(settings.downloadDestinationFolder, onChangeSaveFolder, onSavedFolder)', heading)
        self.assertIn('onStorage()',heading)

    def test_media_workspace_is_readable_on_large_displays_and_uses_url_keyboard(self):
        code = source('downloads/MediaWorkspace.kt')
        self.assertIn('widthIn(max=840.dp)', code)
        self.assertIn('KeyboardType.Uri', code)
        self.assertIn('ImeAction.Go', code)
        self.assertIn('KeyboardActions(onGo', code)

    def test_output_rows_use_real_thumbnails_size_and_localized_date(self):
        code = source('components/ProductionRows.kt')
        self.assertIn('fileDateLabel(output.createdAtEpochMs)', code)
        self.assertIn('formatOutputSizeSummary(output)',code)
        self.assertIn('NeonOutputPreview(output',code)
        self.assertIn('icon=outputIcon', code)
        self.assertNotIn('· ${output.mimeType}', code)

    def test_empty_states_have_clear_semantic_heading_and_icon(self):
        code = source('components/QuietStates.kt')
        self.assertIn('Modifier.semantics { heading() }', code)
        self.assertIn('QuietIconTile(', code)

    def test_dock_keeps_screen_reader_labels_without_clipping_large_fonts(self):
        code = source('components/QuietNavigation.kt')
        self.assertIn('fontScale < 1.6f', code)
        self.assertIn('if (showLabel)', code)
        self.assertIn('contentDescription = label', code)
        self.assertIn('heightIn(min = 64.dp)', code)

    def test_new_accessibility_strings_have_arabic_and_english(self):
        en = ET.parse(ROOT / 'app/src/main/res/values/premium_ui.xml').getroot()
        ar = ET.parse(ROOT / 'app/src/main/res/values-ar/premium_ui.xml').getroot()
        keys = lambda elements: {el.get('name') for el in elements if el.tag == 'string'}
        self.assertEqual(keys(en), keys(ar))
        self.assertIn('premium_download_tip', keys(en))

if __name__ == '__main__':
    unittest.main(verbosity=2)
