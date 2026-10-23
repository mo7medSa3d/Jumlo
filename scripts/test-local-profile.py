#!/usr/bin/env python3
"""About/storage navigation and truthfulness source contracts (not Android UI tests)."""
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
S=(ROOT/"app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt").read_text()
class LocalProfile(unittest.TestCase):
    def test_settings_routes_to_real_storage_and_about_instead_of_fake_profile(self):
        self.assertIn('onOpenStorage?.invoke()',S)
        self.assertIn('storage.sources + storage.outputs + storage.partials + storage.temporary + storage.trash',S)
        self.assertIn('"about" -> AlertDialog(',S)
        self.assertIn('com.jumlo.app.BuildConfig.VERSION_NAME',S)
        self.assertIn('onShowGuide()',S)
        self.assertNotIn('panel="profile"',S)
        nav=(ROOT/"app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt").read_text()
        self.assertIn('composable<StorageRoute>',nav)
        self.assertNotIn('AccountHubModal(',nav)
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
