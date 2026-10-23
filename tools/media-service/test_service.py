#!/usr/bin/env python3
"""The media service, tested without a network and without the real extractor.

Run it with:

    python3 tools/media-service/test_service.py
"""
from __future__ import annotations

import os
import json
import io
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.parse
import urllib.request
from contextlib import redirect_stderr, redirect_stdout
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import service  # noqa: E402


def _completed(args, returncode=0, stdout="", stderr=""):
    return subprocess.CompletedProcess(list(args), returncode, stdout, stderr)


class ErrorAndPrivacyTest(unittest.TestCase):
    def test_unhandled_request_errors_do_not_expose_addresses_or_exception_text(self):
        server = object.__new__(service.MediaHTTPServer)
        captured = io.StringIO()
        with redirect_stderr(captured):
            try:
                raise RuntimeError("https://example.org/private?token=secret")
            except RuntimeError:
                server.handle_error(None, ("192.0.2.42", 5678))
        value = captured.getvalue()
        self.assertNotIn("192.0.2.42", value)
        self.assertNotIn("https://", value)
        self.assertNotIn("secret", value)
        self.assertEqual({"event": "http_request_failed", "type": "RuntimeError"}, json.loads(value))

    def test_startup_logs_do_not_publish_bind_addresses_or_untrusted_version_output(self):
        captured = io.StringIO()
        with patch.object(sys, "argv", ["service.py", "--host", "192.0.2.42", "--token", ""]), \
                patch.object(service, "MediaHTTPServer"), \
                patch.object(service, "ytdlp_version", return_value="https://example.org/?token=secret"), \
                redirect_stdout(captured):
            service.main()
        value = captured.getvalue()
        self.assertNotIn("192.0.2.42", value)
        self.assertNotIn("https://", value)
        self.assertNotIn("secret", value)
        event = json.loads(value)
        self.assertEqual("server_started", event["event"])
        self.assertEqual("unknown", event["extractor_version"])

    def test_age_restrictions_are_distinct_from_login_or_network_failures(self):
        for message in (
            "This video is age-restricted",
            "Sign in to confirm your age. This video may be inappropriate for some users.",
            "Age verification is required",
        ):
            with self.subTest(message=message):
                self.assertEqual("MEDIA_AGE_RESTRICTED", service.classify(message))

    def test_request_logs_do_not_expose_client_addresses_or_signed_source_urls(self):
        handler = object.__new__(service.Handler)
        handler.path = "/stream?url=https%3A%2F%2Fexample.org%2Fprivate%3Ftoken%3Dsecret"
        handler.requestline = "GET " + handler.path + " HTTP/1.1"
        handler.client_address = ("192.0.2.42", 5678)
        captured = io.StringIO()
        with redirect_stderr(captured):
            handler.log_request(422, 64)
        value = captured.getvalue()
        self.assertNotIn("192.0.2.42", value)
        self.assertNotIn("example.org", value)
        self.assertNotIn("secret", value)
        event = json.loads(value)
        self.assertEqual("http_request", event["event"])
        self.assertEqual("/stream", event["route"])
        self.assertEqual(422, event["status"])

    def test_untrusted_error_text_is_not_interpolated_into_logs(self):
        handler = object.__new__(service.Handler)
        handler.client_address = ("192.0.2.42", 5678)
        captured = io.StringIO()
        with redirect_stderr(captured):
            handler.log_message("stream failed: %s", "https://example.org/file?token=secret")
        value = captured.getvalue()
        self.assertNotIn("192.0.2.42", value)
        self.assertNotIn("example.org", value)
        self.assertNotIn("secret", value)
        self.assertEqual("http_server_event", json.loads(value)["event"])


class FormatMetadataTest(unittest.TestCase):
    def test_progressive_audio_is_not_lost_when_the_codec_is_omitted(self):
        value=service.to_format({'format_id':'0','ext':'mp4','width':1080,'height':1920,'vcodec':'h264'})
        self.assertEqual('unknown',value['acodec'])
        self.assertEqual(1080,value['width'])

    def test_total_video_bitrate_is_not_reported_as_audio_bitrate(self):
        value=service.to_format({'format_id':'0','ext':'mp4','vcodec':'h264','acodec':'aac','tbr':4500})
        self.assertIsNone(value['abr'])

    def test_estimated_bytes_remain_distinct_from_exact_bytes(self):
        value=service.to_format({'format_id':'0','ext':'m4a','vcodec':'none','acodec':'aac','filesize_approx':1200})
        self.assertIsNone(value['filesize'])
        self.assertEqual(1200,value['filesize_approx'])

    def test_explicitly_silent_and_adaptive_video_have_no_guessed_audio(self):
        for extra in ({'acodec':'none'},{'protocol':'http_dash_segments'}):
            value=service.to_format({'format_id':'0','ext':'mp4','vcodec':'h264',**extra})
            self.assertEqual('none',value['acodec'])


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'FFmpeg is required')
class RealSelectedTracksTest(unittest.TestCase):
    """Run the official bundled extractor against local media, never a live site."""
    def setUp(self):
        self.work = tempfile.TemporaryDirectory(prefix='jumlo-service-tracks-')
        self.addCleanup(self.work.cleanup)
        root = Path(self.work.name)
        video, audio = root / 'video.mp4', root / 'audio.m4a'
        subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-y', '-f', 'lavfi',
                        '-i', 'color=c=blue:s=320x240:r=10', '-t', '0.5', '-an',
                        '-c:v', 'mpeg4', str(video)], check=True, timeout=20)
        subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-y', '-f', 'lavfi',
                        '-i', 'sine=frequency=660', '-t', '0.5', '-vn',
                        '-c:a', 'aac', str(audio)], check=True, timeout=20)
        self.metadata = root / 'fixture.info.json'
        self.metadata.write_text(json.dumps({
            'id': 'fixture', 'title': 'Fixture', 'extractor': 'fixture',
            'webpage_url': 'https://example.org/fixture',
            'formats': [
                {'format_id': '137', 'url': video.as_uri(), 'ext': 'mp4',
                 'vcodec': 'mp4v', 'acodec': 'none', 'width': 320, 'height': 240},
                {'format_id': '140', 'url': audio.as_uri(), 'ext': 'm4a',
                 'vcodec': 'none', 'acodec': 'mp4a'},
            ],
        }))
        archive = Path(__file__).resolve().parents[2] / 'app/src/media/assets/media/yt-dlp'
        original = service.run_ytdlp
        self.addCleanup(setattr, service, 'run_ytdlp', original)
        def offline_extractor(args, timeout=120):
            # Substitute only the external website with a local metadata fixture;
            # selection, downloading, remuxing and output creation are real yt-dlp.
            options = list(args)
            if options[-1] == 'https://example.org/fixture':
                options.pop()
            if options and options[-1] == '--':
                options.pop()
            return subprocess.run([sys.executable, str(archive), '--ignore-config',
                                   '--no-plugin-dirs', '--enable-file-urls',
                                   '--load-info-json', str(self.metadata), *options],
                                  capture_output=True, text=True, timeout=timeout)
        service.run_ytdlp = offline_extractor

    def test_selected_video_and_audio_are_materialized_as_one_playable_file(self):
        path, name, mime = service.resolve_stream('https://example.org/fixture', 'video', '137+140', 192)
        self.assertTrue(os.path.isfile(path), 'a merged selection must produce a completed file, not the first source URL')
        self.addCleanup(shutil.rmtree, os.path.dirname(path), ignore_errors=True)
        result = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
                                 'stream=codec_type,width,height', '-of', 'json', path],
                                capture_output=True, text=True, check=True, timeout=10)
        tracks = json.loads(result.stdout)['streams']
        self.assertEqual({'audio', 'video'}, {track['codec_type'] for track in tracks})
        self.assertTrue(any(track.get('width') == 320 and track.get('height') == 240 for track in tracks))
        self.assertEqual('media.mp4', name)
        self.assertEqual('video/mp4', mime)

    def test_original_audio_preserves_the_selected_codec(self):
        path, name, mime = service.resolve_stream('https://example.org/fixture', 'audio', 'best', 192, selector='140')
        self.addCleanup(shutil.rmtree, os.path.dirname(path), ignore_errors=True)
        self.assertEqual('media.m4a', name)
        result = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=codec_type,codec_name',
                                 '-of', 'json', path], capture_output=True, text=True, check=True, timeout=10)
        self.assertEqual([{'codec_name': 'aac', 'codec_type': 'audio'}], json.loads(result.stdout)['streams'])
        self.assertEqual('audio/mp4', mime)

    def test_a_missing_selected_audio_format_does_not_fall_back_to_best(self):
        with self.assertRaisesRegex(RuntimeError, 'MEDIA_REANALYSIS_REQUIRED'):
            service.resolve_stream('https://example.org/fixture', 'audio', 'mp3', 192, selector='999')


class AudioConversionTest(unittest.TestCase):
    """The audio path has to produce the format the app asked for."""

    def setUp(self):
        self.original = service.run_ytdlp
        self.addCleanup(setattr, service, "run_ytdlp", self.original)
        self.calls: list[list[str]] = []

    def _fake_extractor(self, fail=False):
        def run(args, timeout=120):
            self.calls.append(list(args))
            if fail:
                return _completed(args, 1, "", "ERROR: unable to convert the audio")
            target = args[args.index("-o") + 1].replace('%(ext)s', args[args.index('--audio-format') + 1])
            Path(target).write_bytes(b"ID3converted-audio" * 64)
            return _completed(args, 0, "", "")

        return run

    def test_the_audio_path_converts_instead_of_printing_a_url(self):
        service.run_ytdlp = self._fake_extractor()
        direct, name, content_type = service.resolve_stream(
            "https://example.org/track", "audio", "mp3", 128
        )
        self.addCleanup(lambda: os.path.isdir(os.path.dirname(direct)) and
                        __import__("shutil").rmtree(os.path.dirname(direct), ignore_errors=True))
        self.assertTrue(direct.endswith(".mp3"), direct)
        self.assertTrue(os.path.exists(direct), "the converted file must exist")
        self.assertEqual("media.mp3", name)
        self.assertEqual("audio/mpeg", content_type)
        args = self.calls[0]
        self.assertNotIn("--get-url", args, "the audio path must not print a URL")
        self.assertIn("-x", args)
        self.assertIn("--audio-format", args)
        self.assertIn("128K", args)

    def test_a_failed_conversion_is_reported(self):
        service.run_ytdlp = self._fake_extractor(fail=True)
        with self.assertRaises(RuntimeError):
            service.resolve_stream("https://example.org/track", "audio", "mp3", 0)


class _Upstream(BaseHTTPRequestHandler):
    """A tiny upstream that answers a range request with a validator."""

    body = b"0123456789" * 40
    etag = '"v1"'
    last_modified = "Wed, 21 Oct 2026 10:00:00 GMT"
    seen_ranges: list[str] = []

    def do_GET(self):  # noqa: N802
        header = self.headers.get("Range") or ""
        type(self).seen_ranges.append(header)
        start = 0
        status = 200
        if header:
            status = 206
            start = int(header.split("=")[1].split("-")[0])
        chunk = self.body[start:]
        self.send_response(status)
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Content-Length", str(len(chunk)))
        self.send_header("ETag", self.etag)
        self.send_header("Last-Modified", self.last_modified)
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{len(self.body) - 1}/{len(self.body)}")
        self.end_headers()
        self.wfile.write(chunk)

    def log_message(self, *args):
        pass


class ValidatorForwardingTest(unittest.TestCase):
    """A resumed download needs the upstream's validator, or it can splice."""

    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), _Upstream)
        cls.port = cls.server.server_address[1]
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        self.original = service.resolve_stream
        self.addCleanup(setattr, service, "resolve_stream", self.original)
        _Upstream.seen_ranges = []
        upstream = f"http://127.0.0.1:{self.port}/file.mp4"
        service.resolve_stream = lambda *args, **kwargs: (upstream, 'media.mp4', 'video/mp4')

    def test_the_service_passes_the_validator_and_the_range_to_the_app(self):
        request = urllib.request.Request(
            f"http://127.0.0.1:{self.port}/direct", headers={"Range": "bytes=10-"}
        )
        # The upstream is called through the service's own proxy.
        handler = service.Handler.__new__(service.Handler)
        handler.send_response = lambda *args, **kwargs: None
        handler.send_header = lambda *args, **kwargs: None
        handler.end_headers = lambda *args, **kwargs: None
        handler.wfile = open(os.devnull, "wb")
        self.addCleanup(handler.wfile.close)
        handler.headers = {"Range": "bytes=10-"}
        handler.log_message = lambda *args: None
        service.Handler._proxy(handler, f"http://127.0.0.1:{self.port}/file.mp4", "media.mp4", "video/mp4")
        self.assertTrue(_Upstream.seen_ranges, "the upstream must see the range")
        self.assertEqual("bytes=10-", _Upstream.seen_ranges[0])

    def test_the_service_forwards_the_validator_headers(self):
        # A real request through the service's own handler.
        server = service.MediaHTTPServer(("127.0.0.1", 0), _ServiceHandler)
        port = server.server_address[1]
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            url = f"http://127.0.0.1:{port}/stream?url=" + urllib.parse.quote(
                f"http://127.0.0.1:{self.port}/file.mp4"
            ) + "&mode=video&format=18"
            request = urllib.request.Request(url, headers={"Range": "bytes=10-"})
            with urllib.request.urlopen(request, timeout=30) as response:
                headers = {key: value for key, value in response.headers.items()}
                body = response.read()
        finally:
            server.shutdown()
            server.server_close()
        self.assertEqual('"v1"', headers.get("ETag"))
        self.assertEqual("Wed, 21 Oct 2026 10:00:00 GMT", headers.get("Last-Modified"))
        self.assertIn("Content-Range", headers)
        self.assertTrue(body.startswith(b"0123456789"[10:]))


class _ServiceHandler(service.Handler):
    def log_message(self, *args):
        pass


class MaterializedRangeTest(unittest.TestCase):
    body = b'0123456789' * 20

    def response(self, headers=None, modified=1000):
        work = tempfile.mkdtemp(prefix='jumlo-range-')
        self.addCleanup(shutil.rmtree, work, ignore_errors=True)
        path = Path(work) / 'media.mp4'
        path.write_bytes(self.body)
        os.utime(path, (modified, modified))
        handler = service.Handler.__new__(service.Handler)
        result = {'headers': {}}
        handler.send_response = lambda code: result.update(status=code)
        handler.send_header = lambda name, value: result['headers'].update({name: value})
        handler.end_headers = lambda: None
        handler.headers = headers or {}
        handler.wfile = io.BytesIO()
        service.Handler._proxy_file(handler, str(path), 'media.mp4', 'video/mp4')
        result['body'] = handler.wfile.getvalue()
        return result

    def test_identical_bytes_have_a_stable_resume_validator(self):
        first, second = self.response(modified=1000), self.response(modified=2000)
        self.assertEqual(first['headers']['ETag'], second['headers']['ETag'])

    def test_stale_if_range_restarts_the_complete_file(self):
        result = self.response({'Range': 'bytes=10-', 'If-Range': '"old-content"'})
        self.assertEqual(200, result['status'])
        self.assertEqual(self.body, result['body'])

    def test_unsatisfiable_range_is_not_clamped_to_the_last_byte(self):
        result = self.response({'Range': 'bytes=999-'})
        self.assertEqual(416, result['status'])
        self.assertEqual('bytes */200', result['headers']['Content-Range'])
        self.assertEqual(b'', result['body'])

    def test_an_explicit_range_end_is_honored(self):
        result = self.response({'Range': 'bytes=10-19'})
        self.assertEqual(206, result['status'])
        self.assertEqual(self.body[10:20], result['body'])
        self.assertEqual('10', result['headers']['Content-Length'])


if __name__ == "__main__":
    unittest.main()
