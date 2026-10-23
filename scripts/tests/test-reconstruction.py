#!/usr/bin/env python3
"""Offline source contract tests for the reconstructed UI and safe editing flows."""
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

class ReconstructionContractTests(unittest.TestCase):
    def test_onboarding_persist_errors_are_caught(self):
        vm = text('app/src/main/java/com/jumlo/app/ui/AppViewModel.kt')
        self.assertRegex(vm, r'fun finishOnboarding\(\)\s*=\s*savePreference\s*\{')

    def test_first_run_and_settings_replay_are_wired(self):
        nav = text('app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt')
        settings = text('app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt')
        self.assertIn('OnboardingGuide(', nav)
        self.assertIn('hasCompletedOnboarding', nav)
        self.assertIn('onShowGuide', nav)
        self.assertIn('onShowGuide', settings)
        self.assertIn('"about" -> AlertDialog(', settings)
        self.assertIn('onShowGuide() }) { Text(stringResource(R.string.quick_start_replay)) }', settings)

    def test_interactive_guide_is_accessible(self):
        guide = text('app/src/main/java/com/jumlo/app/ui/components/OnboardingGuide.kt')
        self.assertIn('verticalScroll', guide)
        self.assertIn('heightIn(min = 48.dp)', guide)
        self.assertIn('onImport', guide)
        self.assertIn('onScan', guide)
        self.assertIn('onExplore', guide)
        self.assertIn('testTag("onboarding.guide")', guide)

    def test_new_localized_resources_present(self):
        names = ['jumlo.xml']
        english = ElementTree.parse(ROOT/'app/src/main/res/values/reconstruction.xml').getroot()
        arabic = ElementTree.parse(ROOT/'app/src/main/res/values-ar/reconstruction.xml').getroot()
        en_ids = {e.attrib['name'] for e in english.findall('string')}
        ar_ids = {e.attrib['name'] for e in arabic.findall('string')}
        self.assertGreaterEqual(len(en_ids), 14)
        self.assertEqual(en_ids, ar_ids)
        for node in arabic.findall('string'):
            self.assertTrue(any('\u0600' <= c <= '\u06ff' for c in ''.join(node.itertext())), node.attrib['name'])

    def test_print_validation_blocks_stale_inputs(self):
        vm = text('app/src/main/java/com/jumlo/app/printing/PrintViewModel.kt')
        ui = text('app/src/main/java/com/jumlo/app/printing/PrintScreen.kt')
        self.assertIn('inputErrors', vm)
        self.assertIn('setNumericDraft', vm)
        self.assertIn('state.inputErrors.isEmpty()', ui)
        self.assertIn('onValidityChanged', ui)

    def test_touch_and_large_font_targets_remain_reachable(self):
        home = text('app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt')
        state = text('app/src/main/java/com/jumlo/app/ui/components/NeonUi.kt')
        settings = text('app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt')
        targets = [float(value) for value in re.findall(r'heightIn\(min\s*=\s*(\d+(?:\.\d+)?)\.dp\)', home)]
        self.assertTrue(targets, 'Home actions must declare a minimum touch height')
        self.assertTrue(all(value >= 48 for value in targets), targets)
        self.assertIn('maxLines = 3', state)
        self.assertIn('LazyColumn(', settings)
        self.assertIn('heightIn(min = 56.dp)', settings)
        self.assertIn('Text(label, Modifier.padding(start = 12.dp))', settings)

if __name__=='__main__':
    unittest.main()
