#!/usr/bin/env python3
"""Local profile navigation and truthfulness source contracts (not Android UI tests)."""
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
S=(ROOT/"app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt").read_text()
class LocalProfile(unittest.TestCase):
    def test_avatar_and_label_open_live_profile(self):
        self.assertIn('onClick = { panel="profile" }',S)
        self.assertIn('TextButton(onClick={panel="profile"}',S)
        self.assertIn('"profile" -> AlertDialog(',S)
        self.assertIn('savedOutputs.size',S)
        self.assertIn('storage.outputs',S)
        self.assertIn('onOpenFiles()',S)
    def test_no_fabricated_plan_or_identity(self):
        self.assertNotIn('Mohamed Saad',S)
        self.assertNotIn('1002 left',S)
        self.assertNotIn('Premium expires',S)
    def test_arabic_english_resource_parity(self):
        en=ET.parse(ROOT/"app/src/main/res/values/local_profile.xml").getroot()
        ar=ET.parse(ROOT/"app/src/main/res/values-ar/local_profile.xml").getroot()
        keys=lambda x: {e.attrib["name"] for e in x if e.tag=="string"}
        self.assertEqual(keys(en),keys(ar))
        self.assertEqual(len(keys(en)),6)
if __name__=="__main__":unittest.main(verbosity=2)
