#!/usr/bin/env python3
"""Offline behavioral checks for the live qualification handoff runner."""
import importlib.util
import json
import os
from pathlib import Path
import shlex
import signal
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'qualify-live-media.py'


class QualificationRunnerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('live_media_qualification', SCRIPT)
        cls.runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.runner)

    def test_plan_keeps_all_35_required_cases_per_target_unqualified(self):
        manifest = self.runner.template()
        rows = self.runner.matrix_rows(manifest)
        required = [r for r in rows if r['case'] in self.runner.PLATFORM_CASES]
        self.assertEqual(7 * 5 * 3, len(required))
        self.assertEqual({'NOT_RUN'}, {r['status'] for r in rows})
        self.assertTrue(all(r['input_ready'] == 'false' for r in required))

    def test_plan_has_no_raw_input_urls_and_uses_audio_fallback(self):
        manifest = self.runner.template()
        url = 'https://www.youtube.com/watch?v=sample&signature=DO_NOT_PUBLISH'
        manifest['platforms']['youtube']['video_url'] = url
        rows = self.runner.matrix_rows(manifest)
        audio = next(r for r in rows if r['platform'] == 'youtube' and r['case'] == 'audio')
        self.assertEqual('true', audio['input_ready'])
        self.assertEqual(self.runner.digest(url), audio['input_sha256'])
        serialized = json.dumps(rows)
        self.assertNotIn('DO_NOT_PUBLISH', serialized)
        self.assertNotIn(url, serialized)

    def test_input_validation_rejects_credentials_and_private_destinations(self):
        for url in ('file:///tmp/a', 'https://me:secret@example.org/a',
                    'http://127.0.0.1/a', 'https://host.local/a', 'http://10.0.0.1/a',
                    'http://127.1/a', 'https://[::1]/a', 'https://example.org/a\n--exec=bad'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                self.runner.validate_url(url)
        self.assertEqual('https://example.org/a', self.runner.validate_url('https://example.org/a'))

    def test_host_report_is_allowlisted_metadata_without_signed_stream_urls(self):
        payload = {'id': 'sample', 'title': 'PRIVATE TITLE', 'extractor_key': 'Youtube',
                   'duration': 2, 'webpage_url': 'https://example.org/private',
                   'formats': [
                       {'format_id': '137', 'vcodec': 'h264', 'acodec': 'none', 'height': 1080,
                        'width': 1920, 'ext': 'mp4', 'url': 'https://cdn.example/signed?token=SECRET'},
                       {'format_id': '140', 'vcodec': 'none', 'acodec': 'aac', 'ext': 'm4a', 'abr': 128},
                       {'format_id': 'direct', 'vcodec': 'unknown', 'acodec': 'unknown', 'ext': 'mp4'}]}
        summary = self.runner.metadata_summary('UU_PID:42\n' + json.dumps(payload))
        self.assertEqual(1, summary['known_video_formats'])
        self.assertEqual(1, summary['known_audio_formats'])
        self.assertEqual([1080], summary['reported_short_edges'])
        self.assertNotIn('PRIVATE TITLE', json.dumps(summary))
        self.assertNotIn('SECRET', json.dumps(summary))
        self.assertNotIn('https://', json.dumps(summary))

    def test_probe_command_only_requests_metadata_and_ignores_user_configuration(self):
        command = self.runner.probe_command(Path('/sample/bootstrap.py'), Path('/sample/yt-dlp'),
                                            'https://example.org/media.mp4')
        for flag in ('--simulate', '--skip-download', '--dump-single-json', '--ignore-config',
                     '--no-plugin-dirs', '--no-playlist'):
            self.assertIn(flag, command)
        self.assertEqual(['--', 'https://example.org/media.mp4'], command[-2:])
        for flag in ('--cookies', '--cookies-from-browser', '--exec', '--write-info-json', '--write-thumbnail'):
            self.assertNotIn(flag, command)

    def test_malformed_codec_values_do_not_become_known_tracks(self):
        payload = {'formats': [{'format_id': 'bad', 'vcodec': 12, 'acodec': None},
                               {'format_id': 'unknown', 'vcodec': 'unknown', 'acodec': 'none'}]}
        summary = self.runner.metadata_summary(json.dumps(payload))
        self.assertEqual(0, summary['known_video_formats'])
        self.assertEqual(0, summary['known_audio_formats'])

    def test_intent_command_preserves_metacharacters_through_host_and_android_shells(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work) / 'input.json'
            url = 'https://example.org/video?q=$(uname)&name=a%20b'
            path.write_text(json.dumps({'schema': 1, 'platforms': {'direct': {'video_url': url}}}))
            result = subprocess.run([sys.executable, str(SCRIPT), 'intent', '--manifest', str(path),
                                     '--platform', 'direct', '--serial', 'emulator-5554'],
                                    text=True, capture_output=True, timeout=5)
            self.assertEqual(0, result.returncode, result.stderr)
            host_command = shlex.split(result.stdout)
            self.assertEqual(['adb', '-s', 'emulator-5554', 'shell'], host_command[:4])
            remote_command = shlex.split(host_command[4])
            self.assertEqual(url, remote_command[-1])

    def test_probe_timeout_and_output_limits_are_distinct_failures(self):
        with tempfile.TemporaryDirectory() as work:
            timeout = self.runner.run_bounded([sys.executable, '-c', 'import time;time.sleep(5)'],
                                              Path(work), {}, timeout=0.1)
            self.assertEqual('HOST_TIMEOUT', timeout['failure'])
            overflow = self.runner.run_bounded([sys.executable, '-c', 'print("x"*20000)'],
                                               Path(work), {}, timeout=3, stdout_limit=100)
            self.assertEqual('HOST_OUTPUT_LIMIT', overflow['failure'])
            self.assertLessEqual(len(overflow['stdout']), 100)

    def test_cleanup_stops_descendants_when_leader_exits_or_is_terminated(self):
        child_code = """
import os, signal, time
from pathlib import Path
signal.signal(signal.SIGTERM, signal.SIG_IGN)
Path('child.ready').write_text(str(os.getpid()))
while True:
    Path('heartbeat').write_text(str(time.monotonic_ns()))
    time.sleep(0.02)
"""
        parent_code = """
import os, subprocess, sys, time
from pathlib import Path
os.setsid()
Path('parent.pid').write_text(str(os.getpid()))
print('UU_PID:' + str(os.getpid()), flush=True)
subprocess.Popen([sys.executable, '-c', sys.argv[2]])
while not Path('heartbeat').exists():
    time.sleep(0.005)
if sys.argv[1] == 'timeout':
    time.sleep(30)
"""
        for mode in ('exit', 'timeout'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as work:
                directory = Path(work)
                try:
                    result = self.runner.run_bounded([sys.executable, '-u', '-c', parent_code, mode, child_code],
                                                     directory, {}, timeout=0.5 if mode == 'timeout' else 3)
                    self.assertEqual('HOST_TIMEOUT' if mode == 'timeout' else None, result['failure'])
                    self.assertTrue((directory / 'child.ready').is_file(), 'Fixture child did not start')
                    before = (directory / 'heartbeat').read_text()
                    time.sleep(0.15)
                    self.assertEqual(before, (directory / 'heartbeat').read_text(),
                                     'A descendant is still executing after run_bounded returned')
                finally:
                    # Clean the deliberately leaked red-test process group as well.
                    if (directory / 'parent.pid').is_file():
                        try:
                            os.killpg(int((directory / 'parent.pid').read_text()), signal.SIGKILL)
                        except ProcessLookupError:
                            pass

    def test_diagnostics_remove_complete_input_and_generated_urls(self):
        url = 'https://example.org/watch?token=PRIVATE'
        error = f'ERROR: {url}: HTTP Error 403; redirect https://cdn.example.org/a?sig=SECRET'
        cleaned = self.runner.redact(error, url)
        self.assertIn('HTTP Error 403', cleaned)
        self.assertNotIn('PRIVATE', cleaned)
        self.assertNotIn('SECRET', cleaned)

    def test_manifest_typo_is_rejected_and_partial_manifest_is_expanded(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work) / 'input.json'
            path.write_text(json.dumps({'schema': 1, 'platforms': {'youtube': {'video_url': 'https://example.org/video'}}}))
            parsed = self.runner.load_manifest(path)
            self.assertEqual(set(self.runner.PLATFORMS), set(parsed['platforms']))
            path.write_text(json.dumps({'schema': 1, 'platforms': {'youtub': {}}}))
            with self.assertRaises(ValueError):
                self.runner.load_manifest(path)


if __name__ == '__main__':
    unittest.main(verbosity=2)
