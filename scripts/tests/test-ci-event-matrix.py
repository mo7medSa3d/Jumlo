#!/usr/bin/env python3
"""Protect one required Android CI run per work-package pull request."""
from pathlib import Path
import unittest

WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/ci.yml"

class CiEventMatrixTests(unittest.TestCase):
    def setUp(self):
        content = WORKFLOW.read_text(encoding="utf-8")
        self.header = content.split("\npermissions:", 1)[0]
        self.workflow = content

    def test_main_and_release_tags_still_build(self):
        push = self.header.split("  push:", 1)[1].split("  pull_request:", 1)[0]
        self.assertIn("branches: [main, 'arena/**']", push)
        self.assertIn("tags: ['v*']", push)

    def test_work_packages_build_on_pr_not_duplicate_push(self):
        push = self.header.split("  push:", 1)[1].split("  pull_request:", 1)[0]
        pull_request = self.header.split("  pull_request:", 1)[1].split("  workflow_dispatch:", 1)[0]
        self.assertNotIn("wp/**", push, "Do not run the entire Android build twice per WP commit")
        self.assertIn("branches: [main]", pull_request)
        self.assertIn("on:", self.header)

    def test_manual_device_and_lite_gates_remain(self):
        self.assertIn("workflow_dispatch:", self.header)
        self.assertIn("device_tests:", self.header)
        self.assertIn("lite_build:", self.header)
        self.assertIn(":app:verifyCompactApkSize", self.workflow)
        self.assertIn(":app:verifyReleaseApkSize", self.workflow)
        self.assertIn(":app:lintRelease", self.workflow)
        self.assertIn("scripts/tests/test-ci-event-matrix.py", self.workflow)

if __name__ == "__main__":
    unittest.main(verbosity=2)
