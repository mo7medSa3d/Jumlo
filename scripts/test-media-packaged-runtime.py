#!/usr/bin/env python3
"""Exercise the actual APK bootstrap and pinned upstream extractor without internet.

JUMLO_EXTRACTOR_ARCHIVE may point at an older payload to reproduce a stale runtime.
These checks do not qualify Android's native executables or live platform access.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/media/assets/media'
ARCHIVE = Path(os.environ.get('JUMLO_EXTRACTOR_ARCHIVE', ASSETS / 'yt-dlp'))
PINNED_VERSION = '2026.10.21'


class PackagedExtractorTests(unittest.TestCase):
    def launch(self, *args):
        with tempfile.TemporaryDirectory(prefix='jumlo-bootstrap-') as work:
            result = subprocess.run(
                [sys.executable, str(ASSETS / 'bootstrap.py'), str(ARCHIVE),
                 '--ignore-config', '--no-plugin-dirs', '--no-check-formats', *args],
                cwd=work, capture_output=True, text=True, timeout=30,
                env={**os.environ, 'UU_ALLOW_HTTP': '0', 'PYTHONNOUSERSITE': '1'},
            )
        self.assertEqual(0, result.returncode, result.stderr)
        first, _, body = result.stdout.partition('\n')
        self.assertRegex(first, r'^UU_PID:[1-9][0-9]*$')
        return body.strip()

    def test_bootstrap_runs_the_current_pinned_release(self):
        self.assertEqual(PINNED_VERSION, self.launch('--version'))

    def test_bundled_bytes_match_the_published_pin(self):
        properties = dict(line.split('=', 1) for line in
                          (ASSETS / 'extractor.properties').read_text().splitlines()
                          if line and not line.startswith('#'))
        self.assertEqual(PINNED_VERSION, properties['version'])
        self.assertEqual(properties['sha256'], hashlib.sha256(ARCHIVE.read_bytes()).hexdigest())
        self.assertEqual(
            f'https://github.com/yt-dlp/yt-dlp/releases/download/{PINNED_VERSION}/yt-dlp',
            properties['source'])

    def test_ejs_scripts_are_available_inside_the_same_archive(self):
        with zipfile.ZipFile(ARCHIVE) as archive:
            files = archive.namelist()
            self.assertIn('yt_dlp_ejs/__init__.py', files)
            self.assertTrue(any(name.startswith('yt_dlp_ejs/') and name.endswith('.js') for name in files))

    def test_exact_selection_keeps_both_real_tracks(self):
        fixture = {
            'id': 'fixture', 'title': 'Fixture', 'extractor': 'fixture',
            'webpage_url': 'https://example.org/watch/fixture',
            'formats': [
                {'format_id': '137', 'url': 'https://example.org/video.mp4',
                 'ext': 'mp4', 'vcodec': 'avc1', 'acodec': 'none', 'height': 1080},
                {'format_id': '140', 'url': 'https://example.org/audio.m4a',
                 'ext': 'm4a', 'vcodec': 'none', 'acodec': 'mp4a'},
            ],
        }
        with tempfile.TemporaryDirectory(prefix='jumlo-metadata-') as work:
            metadata = Path(work) / 'fixture.info.json'
            metadata.write_text(json.dumps(fixture))
            result = self.launch('--load-info-json', str(metadata), '--simulate',
                                 '--format', "best*[format_id='137']+best*[format_id='140']",
                                 '--print', '%(requested_formats.:.format_id)j')
        self.assertEqual(['137', '140'], json.loads(result.splitlines()[-1]))


if __name__ == '__main__':
    unittest.main(verbosity=2)
