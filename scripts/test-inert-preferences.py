#!/usr/bin/env python3
"""Regression contract: removed global preference writes must not masquerade as active controls."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PREF = ROOT / "app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt"
VM = ROOT / "app/src/main/java/com/jumlo/app/ui/AppViewModel.kt"
MODEL = ROOT / "app/src/main/java/com/jumlo/app/model/Models.kt"
ANDROID_TEST = ROOT / "app/src/androidTest/java/com/jumlo/app/data/PreferencesPersistenceTest.kt"


class InertPreferencesTest(unittest.TestCase):
    def test_global_dead_setters_and_keys_removed(self):
        pref = PREF.read_text(encoding="utf-8")
        vm = VM.read_text(encoding="utf-8")
        for name in ("setWifiOnlyDefault", "setMaxConcurrentDownloads"):
            self.assertNotIn(name, pref)
            self.assertNotIn(name, vm)
        for name in ("val WIFI_ONLY_DEFAULT", "val MAX_CONCURRENT_DOWNLOADS"):
            self.assertNotIn(name, pref)

    def test_fixed_policy_still_visible_without_fake_persistence(self):
        pref = PREF.read_text(encoding="utf-8")
        self.assertIn("wifiOnlyDefault = false", pref)
        self.assertIn("maxConcurrentDownloads = 2", pref)
        tests = ANDROID_TEST.read_text(encoding="utf-8")
        self.assertIn("assertFalse(restored.wifiOnlyDefault)", tests)
        self.assertIn("assertEquals(2, restored.maxConcurrentDownloads)", tests)

    def test_real_per_job_wifi_policy_not_removed(self):
        model = MODEL.read_text(encoding="utf-8")
        vm = VM.read_text(encoding="utf-8")
        self.assertIn("wifiOnly: Boolean", model)
        self.assertIn("wifiOnly=current.wifiOnly", vm)


if __name__ == "__main__":
    unittest.main()
