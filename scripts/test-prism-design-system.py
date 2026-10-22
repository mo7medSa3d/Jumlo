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

class PrismSourceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.palette = dict(re.findall(r'^val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)',
            read('ui/theme/Color.kt'), re.M))

    def test_wcag_aa_material_semantic_text_pairs(self):
        for mode in ('Light','Dark'):
            for fg,bg in [('OnSurface','Background'),('OnSurface','Surface'),
                          ('OnSurfaceVariant','Surface'),('OnPrimary','Primary'),
                          ('OnPrimaryContainer','PrimaryContainer'),
                          ('OnSecondary','Secondary'),('OnSecondaryContainer','SecondaryContainer'),
                          ('OnTertiary','Tertiary'),('OnTertiaryContainer','TertiaryContainer'),
                          ('OnError','Error'),('OnErrorContainer','ErrorContainer')]:
                with self.subTest(mode=mode,fg=fg,bg=bg):
                    self.assertGreaterEqual(contrast(self.palette[mode+fg], self.palette[mode+bg]),
                        4.5, f'{mode}{fg} on {mode}{bg}')

    def test_all_four_selected_accents_have_readable_action_text(self):
        light = ['2F46C6','6942B0','086F6B','B63E54']
        dark = [('A9B7FF','13205A'),('D3B7FC','361D65'),
                ('92DCD8','083C41'),('FFB5C1','690D26')]
        for hex_rgb in light:
            self.assertGreaterEqual(contrast(hex_rgb,'FFFFFF'), 4.5, hex_rgb)
        for accent,on in dark:
            self.assertGreaterEqual(contrast(accent,on), 4.5, accent)
        theme = read('ui/theme/Theme.kt')
        self.assertIn('useDynamicColor = accentColor == "DYNAMIC"',theme)
        self.assertIn('onPrimaryContainer = selectedOnContainer',theme)

    def test_dark_and_light_tool_icon_on_tile_contrast(self):
        src = read('ui/theme/ToolPalette.kt')
        arrays = {}
        for mode in ('lightAccents','darkAccents'):
            block = src.split(f'private val {mode} = listOf(',1)[1].split('\n    )',1)[0]
            arrays[mode] = re.findall(r'Color\(0xFF([0-9A-Fa-f]{6})\)',block)
        self.assertEqual(len(arrays['lightAccents']),6)
        self.assertEqual(len(arrays['darkAccents']),6)
        for name,surface,mix_amount in [('lightAccents','FFFFFF',.12),('darkAccents','171F39',.17)]:
            for accent in arrays[name]:
                container = mix(surface,accent,mix_amount)
                self.assertGreaterEqual(contrast(accent,container), 3.0,(name,accent,container))

    def test_single_48dp_plus_hit_targets_and_longer_labels(self):
        neon = read('ui/components/NeonUi.kt')
        tools = read('ui/tools/ToolsScreen.kt')
        picker = read('ui/settings/AccentPickerDialog.kt')
        self.assertIn('heightIn(min = if (compact) 116.dp else 126.dp)',neon)
        self.assertIn('heightIn(min = 122.dp)',tools)
        self.assertIn('maxLines = 2',neon)
        self.assertIn('maxLines = 2',tools)
        self.assertIn('onClick = { onSelect(id) }',picker)

    def test_navigation_and_camera_leave_actions_wired(self):
        nav = read('ui/components/QuietNavigation.kt')
        scan = read('ui/scan/ScanScreen.kt')
        self.assertIn('if (reduced) snap()',nav)
        self.assertIn('onScan: () -> Unit',nav)
        self.assertIn('PreviewView(ctx)',scan)
        self.assertIn('capture.takePicture(',scan)
        self.assertIn('ComposeCanvas(',scan)

if __name__ == '__main__':
    unittest.main(verbosity=2)
