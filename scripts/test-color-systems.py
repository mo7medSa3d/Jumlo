#!/usr/bin/env python3
"""Color systems contract: exactly four systems drive the whole app.

Parses ui/theme/ColorSystems.kt (the source of truth) and verifies every
text pair at WCAG AA, every tool icon on its tile at 3.0+, pillar pairs,
splash readability, the picker, the Settings wiring, the repository, and
that the old accent system is unreferenced from app code. Not a device test.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'app/src/main/java/com/jumlo/app/ui'
APP = ROOT / 'app/src/main/java/com/jumlo/app'


def read_ui(relative):
    return (UI / relative).read_text(encoding='utf-8')


def read_app(relative):
    return (APP / relative).read_text(encoding='utf-8')


def luminance(hex_rgb):
    channels = [int(hex_rgb[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    lo, hi = sorted((luminance(a), luminance(b)))
    return (hi + .05) / (lo + .05)


def mix(a, b, amount):
    return ''.join('%02X' % round(int(a[i:i + 2], 16) * (1 - amount) + int(b[i:i + 2], 16) * amount)
                   for i in (0, 2, 4))


def extract_call(text, marker):
    """Balanced contents of the call that starts at marker (exclusive)."""
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


def parse_scheme(block, constants):
    out = {}
    for name, val in re.findall(r'(\w+) = (Color\(0xFF[0-9A-Fa-f]{6}\)|[A-Za-z]+)', block):
        if val.startswith('Color'):
            out[name] = val[10:16].upper()
        else:
            out[name] = constants[val]
    return out


def parse_system(text, key):
    start = text.index(f'val {key} = ColorSystem(')
    nxt = re.search(r'\n    val [A-Z]+ = ColorSystem\(', text[start + 10:])
    block = text[start:start + 10 + nxt.start()] if nxt else text[start:]
    constants = dict(re.findall(r'private val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', text))
    constants = {k: v.upper() for k, v in constants.items()}
    ramp = re.findall(r'Color\(0xFF([0-9A-Fa-f]{6})\)', extract_call(block, 'ramp = listOf'))
    light = parse_scheme(extract_call(block, 'light = SystemScheme'), constants)
    dark = parse_scheme(extract_call(block, 'dark = SystemScheme'), constants)
    light_tools = re.findall(r'Color\(0xFF([0-9A-Fa-f]{6})\)', extract_call(block, 'lightTools = listOf'))
    dark_tools = re.findall(r'Color\(0xFF([0-9A-Fa-f]{6})\)', extract_call(block, 'darkTools = listOf'))
    splash = dict(re.findall(r'(splash\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', block))
    splash = {k: v.upper() for k, v in splash.items()}
    names = dict(re.findall(r'(nameEn|nameAr|taglineEn|taglineAr) = "([^"]*)"', block))
    return {'ramp': [h.upper() for h in ramp], 'light': light, 'dark': dark,
            'lightTools': [h.upper() for h in light_tools],
            'darkTools': [h.upper() for h in dark_tools],
            'splash': splash, 'names': names}


def parse_pillars(text, systems):
    """Family (primary, container, onContainer, subtitle) x5 per system/mode."""
    body = text.split('fun pillarColors', 1)[1]
    light_part, dark_part = body.split('} else when', 1)
    out = {}
    for mode, part in (('light', light_part), ('dark', dark_part)):
        for key in ('LUIGI', 'AURORA', 'VIOLA', 'SUNSET'):
            marker = f'"{key}" -> listOf' if key != 'SUNSET' else 'else -> listOf'
            entries = re.findall(r'tools\[(\d)\]|Color\(0xFF([0-9A-Fa-f]{6})\)',
                                 extract_call(part, marker))
            tools = systems[key]['lightTools' if mode == 'light' else 'darkTools']
            resolved = [tools[int(i)] if i else h.upper() for i, h in entries]
            assert len(resolved) == 20, (key, mode, len(resolved))
            out[(key, mode)] = [tuple(resolved[i:i + 4]) for i in range(0, 20, 4)]
    return out


EXPECTED_RAMPS = {
    'LUIGI': ['FFE3B3', 'C2DD6C', '79CE67', '30B155'],
    'AURORA': ['FBCFE8', 'A78BFA', '60A5FA', '3B82F6'],
    # Viola's printed labels repeat Luigi's; the ramp is measured from the artwork.
    'VIOLA': ['FFE1C0', 'FFC57B', 'CD4FBA', '780E94'],
    'SUNSET': ['FFE3B3', 'FBB931', 'F88F22', 'EA6113'],
}

TEXT_PAIRS = [
    ('onSurface', 'background'), ('onSurface', 'surface'), ('onSurface', 'surfaceLow'),
    ('onSurfaceVariant', 'surface'), ('onSurfaceVariant', 'surfaceHighest'),
    ('onPrimary', 'primary'), ('onPrimaryContainer', 'primaryContainer'),
    ('onSecondary', 'secondary'), ('onSecondaryContainer', 'secondaryContainer'),
    ('onTertiary', 'tertiary'), ('onTertiaryContainer', 'tertiaryContainer'),
    ('onError', 'error'), ('onErrorContainer', 'errorContainer'), ('error', 'background'),
]


class ColorSystemsContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = read_ui('theme/ColorSystems.kt')
        cls.systems = {key: parse_system(cls.text, key) for key in EXPECTED_RAMPS}
        cls.pillars = parse_pillars(cls.text, cls.systems)

    def test_exactly_four_systems_in_artwork_order_with_sunset_default(self):
        order = re.findall(r'val (LUIGI|AURORA|VIOLA|SUNSET) = ColorSystem\(', self.text)
        self.assertEqual(order, ['LUIGI', 'AURORA', 'VIOLA', 'SUNSET'])
        self.assertIn('val ALL: List<ColorSystem> = listOf(LUIGI, AURORA, VIOLA, SUNSET)', self.text)
        self.assertIn('val DEFAULT: ColorSystem = SUNSET', self.text)
        self.assertIn('ALL.firstOrNull { it.key == key } ?: DEFAULT', self.text)

    def test_ramps_match_the_reference_artworks(self):
        for key, ramp in EXPECTED_RAMPS.items():
            with self.subTest(system=key):
                self.assertEqual(self.systems[key]['ramp'], ramp)

    def test_bilingual_names_and_taglines(self):
        for key, system in self.systems.items():
            with self.subTest(system=key):
                for field in ('nameEn', 'nameAr', 'taglineEn', 'taglineAr'):
                    self.assertTrue(system['names'].get(field), f'{key} missing {field}')

    def test_schemes_carry_every_role(self):
        roles = ['background', 'surface', 'surfaceDim', 'surfaceBright', 'surfaceLowest',
                 'surfaceLow', 'surfaceContainer', 'surfaceHigh', 'surfaceHighest',
                 'onSurface', 'onSurfaceVariant', 'outline', 'outlineVariant',
                 'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer',
                 'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
                 'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
                 'error', 'onError', 'errorContainer', 'onErrorContainer']
        for key, system in self.systems.items():
            for mode in ('light', 'dark'):
                with self.subTest(system=key, mode=mode):
                    for role in roles:
                        self.assertIn(role, system[mode], f'{key} {mode} missing {role}')
        for key, system in self.systems.items():
            self.assertEqual(len(system['lightTools']), 6, key)
            self.assertEqual(len(system['darkTools']), 6, key)

    def test_text_pairs_meet_wcag_aa(self):
        for key, system in self.systems.items():
            for mode in ('light', 'dark'):
                scheme = system[mode]
                for fg, bg in TEXT_PAIRS:
                    with self.subTest(system=key, mode=mode, fg=fg, bg=bg):
                        self.assertGreaterEqual(contrast(scheme[fg], scheme[bg]), 4.5,
                                                f'{key} {mode} {fg}/{bg}')

    def test_tool_icons_meet_non_text_contrast_on_their_tiles(self):
        for key, system in self.systems.items():
            paper = system['light']['background']
            dsurf = system['dark']['surface']
            for accent in system['lightTools']:
                for amount in (.12, .20):
                    container = mix(paper, accent, amount)
                    self.assertGreaterEqual(contrast(accent, container), 3.0,
                                            f'{key} light {accent} on {container}')
            for accent in system['darkTools']:
                for amount in (.17, .20):
                    container = mix(dsurf, accent, amount)
                    self.assertGreaterEqual(contrast(accent, container), 3.0,
                                            f'{key} dark {accent} on {container}')

    def test_pillar_pairs_meet_wcag_aa(self):
        for (key, mode), families in self.pillars.items():
            system = self.systems[key]
            surface = system[mode]['surface']
            for primary, container, on_container, subtitle in families:
                with self.subTest(system=key, mode=mode, primary=primary):
                    self.assertGreaterEqual(contrast(on_container, container), 4.5)
                    self.assertGreaterEqual(contrast(subtitle, surface), 4.5)

    def test_splash_text_stays_readable(self):
        for key, system in self.systems.items():
            splash = system['splash']
            with self.subTest(system=key):
                self.assertGreaterEqual(contrast('FFFFFF', splash['splashMid']), 4.5)
                self.assertGreaterEqual(contrast(splash['splashTag'], splash['splashMid']), 4.5)

    def test_picker_offers_only_the_four_systems(self):
        picker = read_ui('settings/AppearanceColorPicker.kt')
        self.assertIn('fun AppearanceColorPicker(', picker)
        self.assertIn('ModalBottomSheet', picker)
        self.assertIn('ColorSystems.ALL', picker)
        self.assertIn('Brush.horizontalGradient(system.ramp)', picker)
        self.assertIn('onSelect(system.key)', picker)
        for gone in ('OutlinedTextField', 'Slider', 'ColorPresets', 'M3ColorFactory', 'hexDraft'):
            self.assertNotIn(gone, picker, f'old picker element remains: {gone}')

    def test_settings_wiring_theme_and_repo(self):
        settings = read_ui('settings/SettingsScreen.kt')
        self.assertIn('colorSystemLabel(settings.colorSystem, settings.language)', settings)
        self.assertIn('onSelect = { viewModel.setColorSystem(it); panel = null }', settings)
        self.assertIn('current = settings.colorSystem', settings)
        for gone in ('DYNAMIC', 'setAccentColor', 'accentColor', 'ColorPresets', 'M3ColorFactory'):
            self.assertNotIn(gone, settings, f'old wiring remains: {gone}')
        theme = read_ui('theme/Theme.kt')
        self.assertIn('colorSystem: String = "SUNSET"', theme)
        self.assertIn('LocalColorSystem provides system', theme)
        self.assertIn('ColorSystems.systemFor(colorSystem)', theme)
        for gone in ('useDynamicColor', 'seeded', 'QuietLightColorScheme', 'QuietDarkColorScheme'):
            self.assertNotIn(gone, theme, f'old theme wiring remains: {gone}')
        repo = read_app('data/preferences/UserPreferencesRepository.kt')
        self.assertIn('COLOR_SYSTEM', repo)
        self.assertIn('isValidColorSystem', repo)
        self.assertIn('"SUNSET"', repo)
        for gone in ('ACCENT_COLOR', 'isValidAccent', 'setAccentColor'):
            self.assertNotIn(gone, repo, f'old repo wiring remains: {gone}')
        main = read_app('MainActivity.kt')
        self.assertIn('settings.colorSystem', main)

    def test_old_system_unreferenced_from_app_code(self):
        import subprocess
        out = subprocess.run(['grep', '-rn', 'ColorPresets', 'app/src/main/java', 'app/src/test/java',
                              'app/src/androidTest/java'],
                             cwd=str(ROOT), capture_output=True, text=True).stdout
        lines = [ln for ln in out.splitlines() if 'ColorPresets.kt' not in ln and 'build/' not in ln]
        self.assertEqual(lines, [], f'ColorPresets still referenced: {lines}')
        themes = (ROOT / 'app/src/main/res/values/themes.xml').read_text(encoding='utf-8')
        self.assertIn('#FFF6E8', themes)
        self.assertNotIn('#FBF7F0', themes)


if __name__ == '__main__':
    unittest.main(verbosity=2)
