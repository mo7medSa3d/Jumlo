#!/usr/bin/env python3
"""Source-level guardrails for a compact build that retains the full downloader."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
GRADLE = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')

class CompactConfigurationTests(unittest.TestCase):
    def test_default_full_media_runtime_remains_enabled(self):
        self.assertIn('orElse(true).get()', GRADLE)
        self.assertIn('implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")', GRADLE)
        self.assertIn('implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")', GRADLE)
        self.assertIn('check(extendedMedia)', GRADLE)

    def test_compact_is_releaselike_and_arm64_only(self):
        self.assertIn('create("compact")', GRADLE)
        self.assertIn('initWith(getByName("release"))', GRADLE)
        self.assertIn('ndk { abiFilters.add("arm64-v8a") }', GRADLE)
        self.assertIn('isMinifyEnabled = true', GRADLE)
        self.assertIn('isShrinkResources = true', GRADLE)
        self.assertIn('sourceSets["compact"].java.srcDir(if (extendedMedia)', GRADLE)
        self.assertIn('for (variant in listOf("debug", "release", "benchmark", "compact"))', GRADLE)
        self.assertIn('for (variant in listOf("debug", "release", "compact"))', GRADLE)

    def test_both_full_media_apks_enforce_the_decimal_60mb_gate(self):
        self.assertIn('tasks.register("verifyCompactApkSize")', GRADLE)
        self.assertIn('dependsOn("assembleCompact")', GRADLE)
        self.assertIn('tasks.register("verifyReleaseApkSize")', GRADLE)
        self.assertIn('dependsOn("assembleRelease")', GRADLE)
        self.assertIn('maxFullMediaApkBytes = 60_000_000L', GRADLE)
        self.assertEqual(2, GRADLE.count('check(apk.length() <= maxFullMediaApkBytes)'))
        # Correctness gates retained in the release size task.
        self.assertIn('arm64-v8a', GRADLE)
        self.assertIn('assets/media/yt-dlp', GRADLE)

if __name__ == '__main__':
    unittest.main(verbosity=2)
