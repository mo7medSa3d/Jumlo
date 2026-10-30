#!/usr/bin/env python3
"""Protect installable optimized APK publishing; source checks, not device qualification."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")


def step(name: str) -> str:
    anchor = "      - name: " + name + "\n"
    if anchor not in WORKFLOW:
        raise AssertionError(f"Missing workflow step: {name}")
    tail = WORKFLOW.split(anchor, 1)[1]
    return tail.split("\n      - name: ", 1)[0]


class InstallableArtifactContractTest(unittest.TestCase):
    def test_optimized_installer_is_release_minified_but_honestly_test_signed(self):
        self.assertIn('create("compact")', GRADLE)
        self.assertIn('initWith(getByName("release"))', GRADLE)
        self.assertIn('signingConfig = signingConfigs.getByName("debug")', GRADLE)
        self.assertIn('isShrinkResources = true', GRADLE)
        self.assertIn('isMinifyEnabled = true', GRADLE)
        self.assertIn('ndk { abiFilters.add("arm64-v8a") }', GRADLE)

    def test_optimized_artifact_is_separate_and_installable(self):
        prepare = step("Verify and prepare optimized ARM64 installer")
        self.assertIn('apksigner', prepare)
        self.assertIn('verify --verbose --print-certs "$apk"', prepare)
        self.assertIn('build/optimized-installer/Jumlo-Optimized-ARM64.apk.sha256', WORKFLOW)
        self.assertIn('TEST build, NOT', prepare)
        upload = step("Upload optimized installable APK (ARM64)")
        self.assertIn('name: Jumlo-Optimized-Installable-ARM64-', upload)
        self.assertIn('Jumlo-Optimized-ARM64.apk', upload)
        self.assertNotIn('app-debug.apk', upload)
        self.assertNotIn('app-release.apk', upload)
        debug = step("Upload media debug APK separately")
        self.assertIn('app-debug.apk', debug)
        self.assertNotIn('app-compact.apk', debug)

    def test_download_link_is_present_in_workflow_summary(self):
        upload = step("Upload optimized installable APK (ARM64)")
        self.assertIn('id: optimized_installer', upload)
        summary = step("Show optimized APK download link")
        self.assertIn('steps.optimized_installer.outputs.artifact-url', summary)
        self.assertIn('GITHUB_STEP_SUMMARY', summary)

    def test_owner_signed_release_is_conditional_and_never_debug_signed(self):
        prep = step("Prepare owner release signing when configured")
        self.assertIn("github.event_name != 'pull_request'", prep)
        for secret in ('JUMLO_KEYSTORE_BASE64', 'JUMLO_STORE_PASSWORD', 'JUMLO_KEY_ALIAS', 'JUMLO_KEY_PASSWORD'):
            self.assertIn('secrets.' + secret, prep)
        build = step("Build and verify owner-signed Release APK")
        self.assertIn("steps.owner_signing.outputs.enabled == 'true'", build)
        self.assertIn(':app:verifyPublishableRelease', build)
        self.assertIn('apksigner', build)
        upload = step("Upload owner-signed Release APK")
        self.assertIn("steps.owner_signing.outputs.enabled == 'true'", upload)
        self.assertNotIn('app-debug.apk', upload)
        self.assertNotIn('app-compact.apk', upload)
        self.assertIn('Jumlo-Owner-Signed-Release-ARM64.apk', upload)
        cleanup = step("Remove temporary owner signing keystore")
        self.assertIn('always()', cleanup)
        self.assertIn('rm -f --', cleanup)

    def test_existing_build_and_size_gates_remain(self):
        for task in (
            ':app:assembleDebug', ':app:verifyCompactApkSize',
            ':app:verifyReleaseApkSize', ':app:testDebugUnitTest', ':app:lintRelease'
        ):
            self.assertIn(task, WORKFLOW)
        self.assertIn('maxFullMediaApkBytes = 60_000_000L', GRADLE)
        self.assertIn('python3 scripts/tests/test-release-apk-artifacts.py', WORKFLOW)


if __name__ == "__main__":
    unittest.main(verbosity=2)
