#!/usr/bin/env python3
"""Independent source-contract checks for the opt-in multi-site APK. Not an Android build."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT/'app/src/main/java/com/jumlo/app'

class UniversalMediaArchitecture(unittest.TestCase):
    def test_ci_retains_standard_gate_and_has_explicit_extended_artifact(self):
        workflow=(ROOT/'.github/workflows/ci.yml').read_text()
        self.assertIn('inputs.extended_media',workflow)
        self.assertIn('-PextendedMedia=true',workflow)
        self.assertIn('Jumlo-Extended-Media-Debug-APK-',workflow)
        self.assertIn('APK exceeds the hard 60 MB limit',workflow)
        self.assertIn('Forbidden heavy media runtime found in APK',workflow)
    def test_downloader_copy_matches_optional_engine_without_false_claims(self):
        import xml.etree.ElementTree as ET
        ui=(MAIN/'ui/downloads/MediaWorkspace.kt').read_text()
        self.assertIn('BuildConfig.EXTENDED_MEDIA', ui)
        self.assertIn('media_supported_platforms_extended', ui)
        for folder in ('values', 'values-ar'):
            values={v.attrib['name']: v.text or '' for v in ET.parse(ROOT/'app/src/main/res'/folder/'media_strings.xml').getroot()}
            self.assertTrue(values['media_supported_platforms_extended'])
            self.assertNotIn('lightweight build', values['media_error_conversion'].lower())
            self.assertNotIn('النسخة الخفيفة', values['media_error_conversion'])
    def test_runtime_is_explicitly_opt_in_and_direct_build_is_preserved(self):
        build=(ROOT/'app/build.gradle.kts').read_text()
        self.assertIn('extendedMedia',build)
        self.assertIn('orElse(false).get()',build)
        self.assertIn('src/media/java',build)
        self.assertIn('src/base/java',build)
        self.assertIn('library:0.18.1',build)
        self.assertIn('ffmpeg:0.18.1',build)
        self.assertIn('if (extendedMedia)',build)
    def test_provider_runtime_falls_back_to_original_direct_engine(self):
        media=(ROOT/'app/src/media/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').read_text()
        shared=(MAIN/'data/media/NativeDirectMediaRuntime.kt').read_text()
        self.assertIn('NativeDirectMediaRuntime(client, sessions)',media)
        self.assertIn('MediaEngineRouter(',media)
        self.assertIn('MediaProcessRunner {',media)
        self.assertIn('MediaMetadata.parse(',media)
        self.assertIn('StreamTransfer(',shared)
        self.assertNotIn('updateYoutubeDL(',media)
    def test_extractor_enforces_private_workspace_and_integrity(self):
        media=(ROOT/'app/src/media/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').read_text()
        self.assertIn('MediaLinks.normalize(',media)
        self.assertIn('MediaArguments.probe(',media)
        self.assertIn('MediaArguments.download(',media)
        self.assertNotIn('buildExtractorEnv', media) # browser credentials are never exported
        self.assertNotIn('updateYoutubeDL(',media)
        self.assertIn('refreshBundledPayload()',media)
        self.assertIn('AtomicFile(',media)
        self.assertIn('MediaProcessSpec(',media)
    def test_bundle_disables_cookie_browser_and_debug_bypasses(self):
        bootstrap=(ROOT/'app/src/media/assets/media/bootstrap.py').read_text()
        self.assertNotIn('UU_COOKIES_FILE', bootstrap)
        self.assertNotIn('UU_EXTRA_HEADERS', bootstrap)
        self.assertNotIn('--no-check-certificates',bootstrap)
        self.assertNotIn('updateYoutubeDL(',bootstrap)
    def test_build_documentation_discloses_packaging_and_provider_limits(self):
        doc=(ROOT/'docs/media/UNIVERSAL_ENGINE.md').read_text()
        for word in ('GPL-3.0','16 KB','NOT VERIFIED','Play','YouTube','yt-dlp','APK'):
            self.assertIn(word, doc)

if __name__=='__main__': unittest.main(verbosity=2)
