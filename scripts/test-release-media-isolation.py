#!/usr/bin/env python3
"""Source-only checks for the new default production media runtime and opt-out stub."""
from pathlib import Path
import re
import unittest
root=Path(__file__).resolve().parent.parent
build=(root/'app/build.gradle.kts').read_text()
policy=(root/'app/src/main/java/com/jumlo/app/core/CapabilityPolicy.kt').read_text()

class MediaBuildConfiguration(unittest.TestCase):
    def test_default_includes_real_media_in_all_variants(self):
        self.assertIn('orElse(false).get()',build)
        for variant in ('debug','release','benchmark'):
            self.assertIn(f'sourceSets["{variant}"].java.srcDir(if (extendedMedia) "src/media/java" else "src/base/java")',build)
        for coordinate in ('library:0.18.1','ffmpeg:0.18.1'):
            self.assertIn(f'implementation("io.github.junkfood02.youtubedl-android:{coordinate}")',build)
        self.assertIn('if (extendedMedia)',build)
    def test_lite_build_remains_possible(self):
        self.assertTrue((root/'app/src/base/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').is_file())
        self.assertIn('NativeDirectMediaRuntime(client, sessions)',(root/'app/src/base/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').read_text())
    def test_release_policy_exposes_media(self):
        self.assertIn('"dl_media"',policy)
        self.assertNotIn('"dl_media" to "EXTENDED_MEDIA_QUALIFICATION_PENDING"',policy)
    def test_extractor_uses_pinned_bundled_payload(self):
        runtime=(root/'app/src/media/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').read_text()
        self.assertNotIn('updateYoutubeDL(',runtime)
        self.assertIn('refreshBundledPayload()',runtime)

if __name__=='__main__': unittest.main(verbosity=2)
