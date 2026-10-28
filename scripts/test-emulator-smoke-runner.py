#!/usr/bin/env python3
"""Prevent emulator smoke from failing before tests due to /bin/sh vs Bash."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class EmulatorSmokeRunnerContract(unittest.TestCase):
    def test_bash_shell_hosts_pipefail_in_android_emulator_runner(self):
        workflow = (ROOT / ".github/workflows/android-smoke.yml").read_text()
        step = workflow.split("      - name: Execute real Android UI and persistence tests", 1)[1]
        script = step.split("      - name: Keep reports and logcat even on failure", 1)[0]
        self.assertIn("script: |\n            bash <<'BASH'\n", script)
        self.assertIn("set -euo pipefail", script)
        self.assertIn("            BASH\n", script)
        self.assertIn(":app:connectedDebugAndroidTest", script)
        self.assertIn("tee android-smoke.log", script)
        self.assertIn("adb logcat -d -v time", script)

    def test_all_three_api_levels_and_log_artifacts_remain(self):
        workflow = (ROOT / ".github/workflows/android-smoke.yml").read_text()
        self.assertIn("api: [26, 35, 36]", workflow)
        self.assertIn("if: always()", workflow)
        self.assertIn("app/build/outputs/androidTest-results/", workflow)
        self.assertIn("app/build/reports/androidTests/", workflow)
        self.assertIn("python3 scripts/test-emulator-smoke-runner.py", workflow)

if __name__ == "__main__":
    unittest.main(verbosity=2)
