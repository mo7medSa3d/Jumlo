#!/usr/bin/env python3
"""Source-level Jumlo Prism color/touch contract checks; not a substitute for device QA."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/jumlo/app'

def read(file):
    return (SRC / file).read_text(encoding='utf-8')

def luminance(hex_rgb):
    channels = [int(hex_rgb[i:i+2],16)/255.0 for i in (0,2,4)]
    linear = [v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in channels]
    return sum(a*b for a,b in zip(linear, (.2126,.7152,.0722)))

def contrast(a, b):
    values = sorted((luminance(a),luminance(b)))
    return (values[1]+.05)/(values[0]+.05)

def mix(a,b,amount):
    components = [round(int(a[i:i+2],16)*(1-amount)+int(b[i:i+2],16)*amount) for i in (0,2,4)]
    return ''.join(f'{v:02X}' for v in components)

def extract_call(text, marker):
    i = text.index(marker)
    j = text.index('(', i)
    depth = 0
    k = j
    while True:
        ch = text[k]
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                break
        k += 1
    return text[j + 1:k]


def parse_systems(text):
    constants = {k: v.upper() for k, v in
                 re.findall(r'private val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', text)}
    systems = {}
    for key in ('LUIGI', 'AURORA', 'VIOLA', 'SUNSET'):
        start = text.index(f'val {key} = ColorSystem(')
        nxt = re.search(r'\n    val [A-Z]+ = ColorSystem\(', text[start + 10:])
        block = text[start:start + 10 + nxt.start()] if nxt else text[start:]
        modes = {}
        for mode in ('light', 'dark'):
            scheme = extract_call(block, f'{mode} = SystemScheme')
            roles = {}
            for name, val in re.findall(r'(\w+) = (Color\(0xFF[0-9A-Fa-f]{6}\)|[A-Za-z]+)', scheme):
                roles[name] = val[10:16].upper() if val.startswith('Color') else constants[val]
            modes[mode] = roles
        for tools_mode in ('lightTools', 'darkTools'):
            modes[tools_mode] = [h.upper() for h in
                                 re.findall(r'Color\(0xFF([0-9A-Fa-f]{6})\)',
                                            extract_call(block, f'{tools_mode} = listOf'))]
        systems[key] = modes
    return systems


class PrismSourceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.systems = parse_systems(read('ui/theme/ColorSystems.kt'))

    def test_wcag_aa_material_semantic_text_pairs(self):
        for key, modes in self.systems.items():
            for mode in ('light', 'dark'):
                roles = modes[mode]
                for fg,bg in [('onSurface','background'),('onSurface','surface'),
                              ('onSurfaceVariant','surface'),('onPrimary','primary'),
                              ('onPrimaryContainer','primaryContainer'),
                              ('onSecondary','secondary'),('onSecondaryContainer','secondaryContainer'),
                              ('onTertiary','tertiary'),('onTertiaryContainer','tertiaryContainer'),
                              ('onError','error'),('onErrorContainer','errorContainer')]:
                    with self.subTest(system=key,mode=mode,fg=fg,bg=bg):
                        self.assertGreaterEqual(contrast(roles[fg], roles[bg]),
                            4.5, f'{key} {mode} {fg} on {bg}')

    def test_all_four_systems_have_readable_action_text(self):
        for key, modes in self.systems.items():
            with self.subTest(system=key):
                self.assertGreaterEqual(contrast(modes['light']['primary'],'FFFFFF'), 4.5, key)
                self.assertGreaterEqual(
                    contrast(modes['dark']['primary'], modes['dark']['onPrimary']), 4.5, key)
        theme = read('ui/theme/Theme.kt')
        self.assertIn('colorSystem: String = "SUNSET"',theme)
        self.assertNotIn('useDynamicColor',theme)
        self.assertNotIn('seeded',theme)

    def test_dark_and_light_tool_icon_on_tile_contrast(self):
        # ToolPalette delegates to the per-system tool lists in ColorSystems.kt.
        for key, modes in self.systems.items():
            self.assertEqual(len(modes['lightTools']),6)
            self.assertEqual(len(modes['darkTools']),6)
            for name,surface,mix_amount in [('lightTools','FFFFFF',.12),('darkTools','151515',.17)]:
                for accent in modes[name]:
                    container = mix(surface,accent,mix_amount)
                    self.assertGreaterEqual(contrast(accent,container), 3.0,(key,name,accent,container))

    def test_single_48dp_plus_hit_targets_and_longer_labels(self):
        neon = read('ui/components/NeonUi.kt')
        tools = read('ui/tools/ToolsScreen.kt')
        picker = read('ui/settings/AppearanceColorPicker.kt')
        self.assertIn('heightIn(min = if (compact) 116.dp else 126.dp)',neon)
        self.assertIn('heightIn(min = 122.dp)',tools)
        self.assertIn('maxLines = 2',neon)
        self.assertIn('maxLines = 2',tools)
        self.assertIn('onSelect(system.key)',picker)

    def test_navigation_and_direct_mlkit_scan_leave_actions_wired(self):
        nav = read('ui/components/QuietNavigation.kt')
        scan = read('ui/scan/SmartDocumentScanButton.kt')
        self.assertIn('if (reduced) snap()',nav)
        self.assertIn('onScan: () -> Unit',nav)
        self.assertIn('GmsDocumentScanning.getClient(options)',scan)
        self.assertIn('.setGalleryImportAllowed(true)',scan)
        self.assertIn('.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)',scan)
        self.assertIn('LaunchedEffect(Unit) { if (!launched && !externalBusy) startScan() }',scan)
        self.assertIn('if (busy || externalBusy || launched) return',scan)
        self.assertIn('if (!active || latestExternalBusy)',scan)
        self.assertIn('latestBack()',scan)
        self.assertIn('SmartScanResultStore.copyPdf(',scan)

if __name__ == '__main__':
    unittest.main(verbosity=2)
