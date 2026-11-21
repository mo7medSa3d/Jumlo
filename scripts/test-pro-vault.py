#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class ProVaultContractTest(unittest.TestCase):
    def test_biometric_dependency_and_guard(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("libs.androidx.biometric", gradle)
        guard = (ROOT / "app/src/main/java/com/jumlo/app/data/files/BiometricVaultGuard.kt").read_text(encoding="utf-8")
        self.assertIn("BIOMETRIC_STRONG or DEVICE_CREDENTIAL", guard)
        self.assertIn("BIOMETRIC_ERROR_NONE_ENROLLED", guard)
        self.assertIn("BIOMETRIC_ERROR_NO_HARDWARE", guard)

    def test_files_and_home_hide_locked_folders_and_autolock(self):
        fs = (ROOT / "app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt").read_text(encoding="utf-8")
        self.assertIn("Lifecycle.Event.ON_STOP", fs)
        self.assertIn("hiddenInVault", fs)
        hs = (ROOT / "app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt").read_text(encoding="utf-8")
        self.assertIn("lockedFolderIds", hs)

if __name__ == "__main__":
    unittest.main()
