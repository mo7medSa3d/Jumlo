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
import urllib.error
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
        with patch.object(
                sys, "argv", ["service.py", "--host", "192.0.2.42", "--token", "", "--allow-unauthenticated"]), \
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
    @classmethod
    def setUpClass(cls):
        cls._work = tempfile.TemporaryDirectory(prefix='jumlo-service-tracks-')
        cls.addClassCleanup(cls._work.cleanup)
        root = Path(cls._work.name)
        video, audio = root / 'video.mp4', root / 'audio.m4a'
        subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-y', '-f', 'lavfi',
                        '-i', 'color=c=blue:s=320x240:r=10', '-t', '0.5', '-an',
                        '-c:v', 'mpeg4', str(video)], check=True, timeout=20)
        subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-y', '-f', 'lavfi',
                        '-i', 'sine=frequency=660', '-t', '0.5', '-vn',
                        '-c:a', 'aac', str(audio)], check=True, timeout=20)
        cls.metadata = root / 'fixture.info.json'
        cls.metadata.write_text(json.dumps({
            'id': 'fixture', 'title': 'Fixture', 'extractor': 'fixture',
            'webpage_url': 'https://example.org/fixture',
            'formats': [
                {'format_id': '137', 'url': video.as_uri(), 'ext': 'mp4',
                 'vcodec': 'mp4v', 'acodec': 'none', 'width': 320, 'height': 240},
                {'format_id': '140', 'url': audio.as_uri(), 'ext': 'm4a',
                 'vcodec': 'none', 'acodec': 'mp4a'},
            ],
        }))

    def setUp(self):
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
        threading.Thread(target=cls.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()

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
        threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()
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

    def test_dash_container_extensions_are_normalized(self):
        self.assertEqual("mp4", service.to_format({"format_id": "137", "ext": "mp4_dash", "vcodec": "avc1", "acodec": "none"})["ext"])
        self.assertEqual("m4a", service.to_format({"format_id": "140", "ext": "m4a_dash", "vcodec": "none", "acodec": "mp4a"})["ext"])

    def test_proxy_file_cleans_up_temp_dir_on_error_and_sanitizes_filename(self):
        work = tempfile.mkdtemp(prefix='jumlo-cleanup-')
        missing = os.path.join(work, 'missing.mp4')
        handler = service.Handler.__new__(service.Handler)
        handler.headers = {}
        with self.assertRaises(FileNotFoundError):
            service.Handler._proxy_file(handler, missing, 'bad"\r\nname.mp4', 'video/mp4')
        self.assertFalse(os.path.exists(work), "temp dir must be removed even when stat fails")
        self.assertEqual('bad___name.mp4', service._safe_filename('dir/bad"\r\nname.mp4'))


class RequestValidationTest(unittest.TestCase):
    def test_extract_rejects_credentials_and_separates_url_with_double_dash(self):
        self.assertEqual({"error": "MEDIA_SITE_UNSUPPORTED"}, service.extract("https://user:pass@example.org/watch"))
        seen = []
        def fake_run(args, timeout=120):
            seen.append(args)
            return _completed(args, 0, json.dumps({"id": "1", "formats": [{"format_id": "18", "ext": "mp4", "url": "https://cdn.example.org/v.mp4"}]}))
        with patch.object(service, "run_ytdlp", side_effect=fake_run):
            result = service.extract("https://example.org/watch")
        self.assertIn("formats", result)
        self.assertEqual(["-J", "--", "https://example.org/watch"], seen[0])

    def test_extract_and_resolve_stream_block_ssrf_and_invalid_urls(self):
        blocked = (
            "file:///etc/passwd",
            "ftp://example.org/file",
            "http://127.0.0.1/admin",
            "http://127.1/admin",
            "http://0x7f000001/admin",
            "http://[::1]/admin",
            "http://[::ffff:127.0.0.1]/admin",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.1/internal",
            "http://192.168.1.1/router",
            "https://localhost/secret",
            "https://service.internal/secret",
            "https://printer.local/secret",
            "https://example.org:99999/badport",
            "https://example.org/bad\r\nheader",
        )
        for bad_url in blocked:
            with self.subTest(url=bad_url):
                self.assertEqual({"error": "MEDIA_SITE_UNSUPPORTED"}, service.extract(bad_url))
                with self.assertRaisesRegex(RuntimeError, "MEDIA_SITE_UNSUPPORTED"):
                    service.resolve_stream(bad_url, "video", "18", 0)

    def test_proxy_blocks_file_scheme_urls(self):
        handler = service.Handler.__new__(service.Handler)
        captured = {}
        handler.headers = {}
        handler.wfile = io.BytesIO()
        handler.send_response = lambda code: captured.update(status=code)
        handler.send_header = lambda name, value: None
        handler.end_headers = lambda: None
        service.Handler._proxy(handler, "file:///etc/passwd", "passwd", "text/plain")
        self.assertEqual(422, captured["status"])
        self.assertEqual({"error": "MEDIA_SITE_UNSUPPORTED"}, json.loads(handler.wfile.getvalue()))

    def test_bearer_token_auth_is_per_server_and_enforced(self):
        server = service.MediaHTTPServer(("127.0.0.1", 0), _ServiceHandler, token="top-secret")
        port = server.server_address[1]
        threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()
        try:
            with self.assertRaises(urllib.error.HTTPError) as missing:
                urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10)
            self.assertEqual(401, missing.exception.code)
            missing.exception.close()

            bad = urllib.request.Request(f"http://127.0.0.1:{port}/health",
                                         headers={"Authorization": "Bearer wrong"})
            with self.assertRaises(urllib.error.HTTPError) as rejected:
                urllib.request.urlopen(bad, timeout=10)
            self.assertEqual(401, rejected.exception.code)
            rejected.exception.close()

            ok = urllib.request.Request(f"http://127.0.0.1:{port}/health",
                                        headers={"Authorization": "Bearer top-secret"})
            with urllib.request.urlopen(ok, timeout=10) as response:
                self.assertEqual(200, response.status)
                self.assertTrue(json.loads(response.read())["ok"])
        finally:
            server.shutdown()
            server.server_close()

    def test_post_extract_rejects_invalid_or_oversized_content_length(self):
        for body in (b"[]", b"123", b"null"):
            handler = service.Handler.__new__(service.Handler)
            handler.path = "/extract"
            handler.headers = {"Content-Length": str(len(body))}
            handler.rfile = io.BytesIO(body)
            handler.wfile = io.BytesIO()
            captured = {}
            handler.send_response = lambda code: captured.update(status=code)
            handler.send_header = lambda name, value: None
            handler.end_headers = lambda: None
            handler.do_POST()
            self.assertEqual(400, captured["status"])
        for raw_len, expected_status in (("abc", 400), ("-5", 400), ("65537", 413)):
            handler = service.Handler.__new__(service.Handler)
            handler.path = "/extract"
            handler.headers = {"Content-Length": raw_len}
            handler.rfile = io.BytesIO(b"{}")
            handler.wfile = io.BytesIO()
            captured = {}
            handler.send_response = lambda code: captured.update(status=code)
            handler.send_header = lambda name, value: None
            handler.end_headers = lambda: None
            handler.do_POST()
            self.assertEqual(expected_status, captured["status"])

    def test_run_ytdlp_blocks_netrc_and_disables_config_loading(self):
        captured = {}

        class FakeProc:
            returncode = 0
            pid = 12345

            def communicate(self, timeout=None):
                return ("{}", "")

        def fake_popen(cmd, **kwargs):
            captured["cmd"] = list(cmd)
            return FakeProc()

        with patch.object(service.subprocess, "Popen", side_effect=fake_popen):
            service.run_ytdlp(["-J", "--", "https://example.org/watch"])
        cmd = captured["cmd"]
        sep = cmd.index("--")
        self.assertIn("--ignore-config", cmd[:sep])
        self.assertIn("--no-config-locations", cmd[:sep])
        self.assertIn("--no-plugin-dirs", cmd[:sep])
        self.assertNotIn("--no-netrc", cmd[:sep])
        for forbidden in ("-n", "--netrc", "--netrc-cmd=id", "--netrc-location=/tmp/x", "--config-locations=/tmp/c"):
            with self.subTest(forbidden=forbidden):
                with self.assertRaisesRegex(RuntimeError, "MEDIA_FORBIDDEN_NETRC_OPTION"):
                    service.run_ytdlp([forbidden, "--", "https://example.org/watch"])

    def test_handler_setup_sets_socket_timeout(self):
        timeouts = []

        class FakeSocket:
            def settimeout(self, value):
                timeouts.append(value)

            def makefile(self, mode, bufsize=-1):
                return io.BytesIO()

        handler = object.__new__(service.Handler)
        handler.request = FakeSocket()
        handler.setup()
        self.assertEqual([30], timeouts)


class ProductionHardeningTest(unittest.TestCase):
    def _start_server(self, **kwargs):
        server = service.MediaHTTPServer(("127.0.0.1", 0), _ServiceHandler, **kwargs)
        port = server.server_address[1]
        threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server, port

    def test_security_headers_are_emitted_on_success_and_error_responses(self):
        _, port = self._start_server()
        expected = {
            "X-Content-Type-Options": "nosniff",
            "X-Frame-Options": "DENY",
            "Cache-Control": "no-store",
            "Referrer-Policy": "no-referrer",
        }
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as response:
            for name, value in expected.items():
                self.assertEqual(value, response.headers.get(name))

        with self.assertRaises(urllib.error.HTTPError) as not_found:
            urllib.request.urlopen(f"http://127.0.0.1:{port}/missing", timeout=10)
        try:
            self.assertEqual(404, not_found.exception.code)
            for name, value in expected.items():
                self.assertEqual(value, not_found.exception.headers.get(name))
        finally:
            not_found.exception.close()

    def test_ready_endpoint_reflects_ytdlp_availability_and_shutdown_state(self):
        server, port = self._start_server()
        with patch.object(service, "ytdlp_version", return_value="2026.01.01"):
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=10) as response:
                self.assertEqual(200, response.status)
                payload = json.loads(response.read())
                self.assertTrue(payload["ready"])
                self.assertFalse(payload["shutting_down"])

        with patch.object(service, "ytdlp_version", return_value="unavailable"):
            with self.assertRaises(urllib.error.HTTPError) as missing_ytdlp:
                urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=10)
            try:
                self.assertEqual(503, missing_ytdlp.exception.code)
                self.assertFalse(json.loads(missing_ytdlp.exception.read())["ready"])
            finally:
                missing_ytdlp.exception.close()

        server.shutting_down = True
        with patch.object(service, "ytdlp_version", return_value="2026.01.01"):
            with self.assertRaises(urllib.error.HTTPError) as draining:
                urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=10)
            try:
                self.assertEqual(503, draining.exception.code)
                payload = json.loads(draining.exception.read())
                self.assertFalse(payload["ready"])
                self.assertTrue(payload["shutting_down"])
            finally:
                draining.exception.close()

    def test_per_ip_rate_limiting_returns_429_with_retry_after_and_exempts_probes(self):
        _, port = self._start_server(rate_limit=2)
        for _ in range(2):
            with self.assertRaises(urllib.error.HTTPError) as err:
                urllib.request.urlopen(f"http://127.0.0.1:{port}/unknown", timeout=10)
            self.assertEqual(404, err.exception.code)
            err.exception.close()

        with self.assertRaises(urllib.error.HTTPError) as limited:
            urllib.request.urlopen(f"http://127.0.0.1:{port}/unknown", timeout=10)
        try:
            self.assertEqual(429, limited.exception.code)
            retry_after = limited.exception.headers.get("Retry-After")
            self.assertIsNotNone(retry_after)
            self.assertGreaterEqual(int(retry_after), 1)
            self.assertEqual({"error": "MEDIA_RATE_LIMITED"}, json.loads(limited.exception.read()))
        finally:
            limited.exception.close()

        with patch.object(service, "ytdlp_version", return_value="2026.01.01"):
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as health:
                self.assertEqual(200, health.status)
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=10) as ready:
                self.assertEqual(200, ready.status)

    def test_concurrency_cap_returns_503_when_saturated(self):
        _, port = self._start_server(max_concurrent=1)
        entered = threading.Event()
        release = threading.Event()

        def slow_extract(_url):
            entered.set()
            release.wait(timeout=5)
            return {"id": "1", "formats": []}

        body = json.dumps({"url": "https://example.org/watch"}).encode("utf-8")
        first_status = []

        def run_first():
            req = urllib.request.Request(
                f"http://127.0.0.1:{port}/extract",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                first_status.append(resp.status)

        with patch.object(service, "extract", side_effect=slow_extract):
            worker = threading.Thread(target=run_first, daemon=True)
            worker.start()
            self.assertTrue(entered.wait(timeout=5))
            req2 = urllib.request.Request(
                f"http://127.0.0.1:{port}/extract",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with self.assertRaises(urllib.error.HTTPError) as busy:
                urllib.request.urlopen(req2, timeout=10)
            try:
                self.assertEqual(503, busy.exception.code)
                self.assertEqual({"error": "MEDIA_SERVICE_BUSY"}, json.loads(busy.exception.read()))
            finally:
                busy.exception.close()
                release.set()
            worker.join(timeout=5)
        self.assertEqual([200], first_status)

    def test_startup_validation_rejects_invalid_port_and_unauthenticated_non_loopback(self):
        for bad_port in ("0", "65536", "-1", "notaport"):
            with self.subTest(port=bad_port):
                with self.assertRaises(ValueError):
                    service.parse_config(["--port", bad_port], environ={})
                captured = io.StringIO()
                with redirect_stderr(captured), self.assertRaises(SystemExit) as exited:
                    service.main(["--port", bad_port])
                self.assertEqual(2, exited.exception.code)

        for non_loopback in ("0.0.0.0", "192.0.2.10", "::"):
            with self.subTest(host=non_loopback):
                with self.assertRaises(ValueError):
                    service.parse_config(["--host", non_loopback], environ={})
                captured = io.StringIO()
                with redirect_stderr(captured), self.assertRaises(SystemExit) as exited:
                    service.main(["--host", non_loopback, "--token", ""])
                self.assertEqual(2, exited.exception.code)

        # Allowed combinations: loopback without token, non-loopback with token or explicit opt-in.
        loopback_cfg = service.parse_config([], environ={})
        self.assertEqual("127.0.0.1", loopback_cfg.host)
        self.assertEqual(8787, loopback_cfg.port)
        self.assertEqual(60, loopback_cfg.rate_limit)
        self.assertEqual(8, loopback_cfg.max_concurrent)
        self.assertEqual("INFO", loopback_cfg.log_level)

        env_cfg = service.parse_config(
            [],
            environ={
                "JUMLO_MEDIA_HOST": "0.0.0.0",
                "JUMLO_MEDIA_PORT": "9000",
                "JUMLO_MEDIA_TOKEN": "prod-secret",
                "JUMLO_MEDIA_RATE_LIMIT": "120",
                "JUMLO_MEDIA_MAX_CONCURRENT": "16",
                "JUMLO_MEDIA_LOG_LEVEL": "warning",
            },
        )
        self.assertEqual("0.0.0.0", env_cfg.host)
        self.assertEqual(9000, env_cfg.port)
        self.assertEqual("prod-secret", env_cfg.token)
        self.assertEqual(120, env_cfg.rate_limit)
        self.assertEqual(16, env_cfg.max_concurrent)
        self.assertEqual("WARNING", env_cfg.log_level)

        opt_in_cfg = service.parse_config(["--host", "0.0.0.0", "--allow-unauthenticated"], environ={})
        self.assertEqual("0.0.0.0", opt_in_cfg.host)

        for bad_env in (
            {"JUMLO_MEDIA_RATE_LIMIT": "0"},
            {"JUMLO_MEDIA_MAX_CONCURRENT": "0"},
            {"JUMLO_MEDIA_LOG_LEVEL": "TRACE"},
        ):
            with self.subTest(bad_env=bad_env):
                with self.assertRaises(ValueError):
                    service.parse_config([], environ=bad_env)


if __name__ == "__main__":
    unittest.main()
