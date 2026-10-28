#!/usr/bin/env python3
"""CI regression contract. This verifies task wiring, not KSP runtime execution."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]

class KspCiVerification(unittest.TestCase):
    def test_required_android_checks_are_never_dropped(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        segment = ci.split("name: Media build (full downloader), unit tests, lint, compact size gate", 1)[1].split("name: Inspect the packaged media runtime", 1)[0]
        command = re.search(r"./gradlew .*?\| tee gradle\.log", segment, flags=re.DOTALL)
        self.assertIsNotNone(command, "The required Gradle verification command is absent")
        text = command.group(0)
        for task in (":app:assembleDebug", ":app:verifyCompactApkSize", ":app:verifyReleaseApkSize", ":app:testDebugUnitTest", ":app:lintRelease"):
            self.assertIn(task, text)
        self.assertIn("-PextendedMedia=true", text)

    def test_ksp_analysis_workers_are_serialized_in_full_build(self):
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        segment = ci.split("name: Media build (full downloader), unit tests, lint, compact size gate", 1)[1].split("name: Inspect the packaged media runtime", 1)[0]
        command = re.search(r"./gradlew .*?\| tee gradle\.log", segment, flags=re.DOTALL)
        self.assertIsNotNone(command)
        self.assertIn("--no-parallel", command.group(0))
        self.assertIn("--max-workers=1", command.group(0))
        # Full-media release minification needs more than the local default 2560 MB.
        # This does not disable R8/shrinking or raise worker concurrency.
        self.assertIn('-Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"', command.group(0))
        self.assertIn(":app:verifyReleaseApkSize", command.group(0))
        # The KSP2 engine must remain enabled rather than bypassed to get a green build.
        gradle = (ROOT / "gradle.properties").read_text()
        self.assertRegex(gradle, r"(?m)^ksp\.useKSP2=true\s*$")

if __name__ == "__main__":
    unittest.main(verbosity=2)
