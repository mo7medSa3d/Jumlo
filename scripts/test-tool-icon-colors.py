#!/usr/bin/env python3
"""Tool icon colors are fixed per tool, never the theme accent.

Source-level contracts, not device tests. ToolIconColors assigns every
catalogue tool a vivid color from its family hue plus a coprime step
(37 degrees inside a 90-degree window, so all 47 hues are distinct), and
picks a black/white glyph by WCAG contrast. The tool tiles in ToolsScreen
and NeonUi must use those colors — in a warm system the old pillar
accents made every icon read as the same orange.
"""
from pathlib import Path
import colorsys
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/jumlo/app'


def read(rel):
    return (SRC / rel).read_text(encoding='utf-8')


def luminance(hex_rgb):
    channels = [int(hex_rgb[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    lo, hi = sorted((luminance(a), luminance(b)))
    return (hi + .05) / (lo + .05)


def pillar_center(tool_id):
    if tool_id == 'scan_doc':
        return 185
    if tool_id.startswith('pdf_') or tool_id == 'office_to_pdf':
        return 12
    if tool_id.startswith('img_'):
        return 150
    if tool_id.startswith('dl_'):
        return 220
    if tool_id.startswith('file_') or tool_id == 'sha256':
        return 275
    if tool_id.startswith('xf_'):
        return 45
    return 320


def icon_hex(tool_id, index):
    hue = (pillar_center(tool_id) + ((index * 37) % 90) - 45) % 360
    r, g, b = colorsys.hls_to_rgb(hue / 360.0, 0.55, 0.85)
    return '#%02X%02X%02X' % (round(r * 255), round(g * 255), round(b * 255))


class ToolIconColorContracts(unittest.TestCase):
    def test_every_catalogue_tool_has_a_distinct_vivid_color(self):
        code = read('ui/theme/ToolIconColors.kt')
        # The list closes on its own line; a plain non-greedy ')' would
        # stop at a parenthesis inside a comment above the items.
        ordered = re.search(r'private val ORDERED = listOf\((.*?)\n    \)',
                            code, re.S).group(1)
        tools = re.findall(r'"([^"]+)"', ordered)
        self.assertEqual(len(tools), 47, tools)
        self.assertEqual(len(set(tools)), 47, 'duplicate tool id in ORDERED')
        colors = {}
        for index, tool_id in enumerate(tools):
            hexv = icon_hex(tool_id, index)
            self.assertNotIn(hexv, colors, f'{tool_id} collides with {colors.get(hexv)}')
            colors[hexv] = tool_id
            glyph = max(('FFFFFF', '000000'),
                        key=lambda fg: contrast(fg, hexv[1:]))
            self.assertGreaterEqual(
                contrast(glyph, hexv[1:]), 4.5,
                f'{tool_id} {hexv}: best glyph {glyph} below AA')

    def test_icon_colors_never_follow_the_theme(self):
        code = read('ui/theme/ToolIconColors.kt')
        for marker in ('fun iconColorFor(', 'fun glyphOn(', '0.85f', '0.55f',
                       '(index * 37) % 90', '185f', '12f', '150f', '220f',
                       '275f', '45f', '320f'):
            self.assertIn(marker, code, f'missing palette marker: {marker}')
        for banned in ('ToolPalette', 'ColorSystems', 'LocalColorSystem'):
            self.assertNotIn(banned, code, f'theme leak in ToolIconColors: {banned}')

    def test_tiles_use_the_fixed_icon_colors(self):
        tiles = read('ui/tools/ToolsScreen.kt')
        self.assertIn('ToolIconColors.iconColorFor(tool.id)', tiles)
        self.assertIn('ToolIconColors.glyphOn(', tiles)
        self.assertNotIn('ToolPalette', tiles)
        neon = read('ui/components/NeonUi.kt')
        self.assertIn('ToolIconColors.iconColorFor(tool.id)', neon)
        self.assertIn('ToolIconColors.glyphOn(', neon)
        self.assertNotIn('ToolPalette.accentFor', neon)
        self.assertNotIn('ToolPalette.containerFor', neon)


if __name__ == '__main__':
    unittest.main(verbosity=2)
