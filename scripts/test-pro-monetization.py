#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

class ProMonetizationContractTest(unittest.TestCase):
    def test_flavors_and_play_billing_integrity_deps(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("libs.play.billing", gradle)
        self.assertIn("libs.play.integrity", gradle)
        self.assertIn('create("play")', gradle)
        self.assertIn('create("direct")', gradle)
        self.assertIn("ENABLE_MEDIA_DOWNLOADER", gradle)

    def test_billing_manager_supports_lifetime_and_annual(self):
        bm = (ROOT / "app/src/main/java/com/jumlo/app/data/billing/JumloBillingManager.kt").read_text(encoding="utf-8")
        self.assertIn("jumlo_pro_lifetime", bm)
        self.assertIn("jumlo_pro_annual", bm)
        self.assertIn("acknowledgePurchase", bm)
        self.assertIn("restorePurchases", bm)

    def test_pro_sheet_uses_dynamic_prices_and_play_integrity_present(self):
        sheet = (ROOT / "app/src/main/java/com/jumlo/app/ui/pro/JumloProSheet.kt").read_text(encoding="utf-8")
        self.assertIn("entitlement.lifetimePrice", sheet)
        self.assertIn("entitlement.annualPrice", sheet)
        pi = (ROOT / "app/src/main/java/com/jumlo/app/data/security/PlayIntegrityVerifier.kt").read_text(encoding="utf-8")
        self.assertIn("StandardIntegrityManager", pi)

if __name__ == "__main__":
    unittest.main()
