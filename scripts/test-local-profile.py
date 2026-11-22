#!/usr/bin/env python3
"""About/storage navigation and truthfulness source contracts (not Android UI tests)."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
S=(ROOT/"app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt").read_text(encoding="utf-8")
class LocalProfile(unittest.TestCase):
    def test_settings_routes_to_real_storage_and_about_instead_of_fake_profile(self):
        self.assertIn('onOpenStorage?.invoke()',S)
        self.assertIn('storage.outputs + storage.partials + storage.temporary + storage.trash',S)
        self.assertIn('"about" -> AlertDialog(',S)
        self.assertIn('com.jumlo.app.BuildConfig.VERSION_NAME',S)
        self.assertIn('onShowGuide()',S)
        self.assertNotIn('panel="profile"',S)
        nav=(ROOT/"app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt").read_text(encoding="utf-8")
        self.assertIn('composable<StorageRoute>',nav)
        self.assertNotIn('AccountHubModal(',nav)
    def test_no_fabricated_plan_or_identity(self):
        self.assertNotIn('Mohamed Saad',S)
        self.assertNotIn('1002 left',S)
        self.assertNotIn('Premium expires',S)
    def test_retired_local_profile_resources_removed(self):
        self.assertFalse((ROOT/"app/src/main/res/values/local_profile.xml").exists())
        self.assertFalse((ROOT/"app/src/main/res/values-ar/local_profile.xml").exists())
if __name__=="__main__":unittest.main(verbosity=2)
