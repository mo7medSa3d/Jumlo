#!/usr/bin/env python3
"""Guard emulator strict-mode shell and real Android UI test selection."""
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]

class EmulatorSmokeRunnerContract(unittest.TestCase):
    def test_single_bash_command_runs_a_syntax_checked_script(self):
        workflow = (ROOT / ".github/workflows/android-smoke.yml").read_text()
        fragment = workflow.split("      - name: Execute real Android UI and persistence tests", 1)[1].split("      - name: Keep reports and logcat even on failure", 1)[0]
        self.assertIn("script: bash scripts/run-emulator-smoke.sh", fragment)
        self.assertNotIn("bash <<", fragment)
        script = ROOT / "scripts/run-emulator-smoke.sh"
        body = script.read_text()
        self.assertTrue(body.startswith("#!/usr/bin/env bash"))
        self.assertIn("set -euo pipefail", body)
        self.assertIn(":app:connectedDebugAndroidTest", body)
        self.assertIn("tee android-smoke.log", body)
        self.assertIn("adb logcat -d -v time", body)
        subprocess.run(["bash", "-n", str(script)], check=True)

    def test_matrix_and_failure_artifacts_still_required(self):
        workflow = (ROOT / ".github/workflows/android-smoke.yml").read_text()
        self.assertIn("api: [26, 35, 36]", workflow)
        self.assertIn("if: always()", workflow)
        self.assertIn("app/build/outputs/androidTest-results/", workflow)
        self.assertIn("app/build/reports/androidTests/", workflow)
        self.assertIn("python3 scripts/test-emulator-smoke-runner.py", workflow)

if __name__ == "__main__":
    unittest.main(verbosity=2)
