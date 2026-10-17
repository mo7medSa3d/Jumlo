#!/usr/bin/env python3
"""Source gate for media build graph isolation; Gradle graph verifies it in CI."""
from pathlib import Path
import re
import unittest

root = Path(__file__).resolve().parent.parent
build = (root / 'app/build.gradle.kts').read_text()
policy = (root / 'app/src/main/java/com/jumlo/app/core/CapabilityPolicy.kt').read_text()
native = (root / 'scripts/verify-native.sh').read_text()

class ReleaseMediaIsolation(unittest.TestCase):
    def test_all_optional_media_dependencies_are_debug_scoped(self):
        for coordinate in ('library:0.18.1', 'ffmpeg:0.18.1'):
            self.assertIn(f'debugImplementation("io.github.junkfood02.youtubedl-android:{coordinate}")', build)
        self.assertNotRegex(build, r'(?m)^\s*implementation\("io\.github\.junkfood02')

    def test_release_and_benchmark_compile_stub_even_with_optin(self):
        self.assertIn('sourceSets["release"].java.srcDir("src/base/java")', build)
        self.assertIn('sourceSets["benchmark"].java.srcDir("src/base/java")', build)
        self.assertIn('sourceSets["debug"].java.srcDir(if (extendedMedia) "src/media/java" else "src/base/java")', build)
        self.assertIn('buildConfigField("boolean", "EXTENDED_MEDIA", "false")', build)
        self.assertEqual(1, len(list((root / 'app/src/base/java').rglob('MediaRuntimeFactory.kt'))))
        self.assertIn('MEDIA_BUILD_NOT_ENABLED', (root / 'app/src/base/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').read_text())

    def test_user_has_to_opt_in_before_media_analysis_and_download(self):
        vm = (root / 'app/src/main/java/com/jumlo/app/ui/downloads/MediaViewModel.kt').read_text()
        work = (root / 'app/src/main/java/com/jumlo/app/ui/downloads/MediaWorkspace.kt').read_text()
        op = (root / 'app/src/main/java/com/jumlo/app/data/media/MediaOperationHandler.kt').read_text()
        prefs = (root / 'app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt').read_text()
        self.assertIn('termsAccepted: Boolean=false', vm)
        self.assertIn('if(!_state.value.termsAccepted)', vm)
        self.assertIn('if(s.submitting || s.analyzing || !s.termsAccepted) return', vm)
        self.assertIn('Checkbox(', work)
        self.assertIn('checked = s.termsAccepted', work)
        self.assertIn('if (!preferences.settingsFlow.first().mediaTermsAccepted)', op)
        self.assertIn('prefs[Keys.MEDIA_TERMS_ACCEPTED] ?: false', prefs)
        self.assertNotIn('preferences.setMediaTermsAccepted(true)', vm)

    def test_online_extractor_update_is_not_allowed_without_a_pinned_artifact(self):
        runtime = (root / 'app/src/media/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').read_text()
        self.assertNotIn('updateYoutubeDL(', runtime)
        self.assertIn('refreshBundledPayload()', runtime)
        self.assertIn('EXTRACTOR_RESTORED_FROM_APK', runtime)

    def test_release_never_opens_media_capability(self):
        self.assertIn('"dl_media" to "EXTENDED_MEDIA_QUALIFICATION_PENDING"', policy)
        self.assertNotIn('BuildConfig.DEBUG', policy)
        self.assertIn('extended-media-release-dependencies', native)
        self.assertIn('releaseRuntimeClasspath', native)

if __name__ == '__main__':
    unittest.main(verbosity=2)
