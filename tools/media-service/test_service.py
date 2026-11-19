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
                        shutil.rmtree(os.path.dirname(direct), ignore_errors=True))
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
            "http://224.0.0.1/multicast",
            "http://[ff02::1]/multicast",
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

    def test_redirect_handler_blocks_ssrf_downgrade_and_strips_cross_origin_credentials(self):
        redirector = service._HttpOnlyRedirectHandler()
        base_req = urllib.request.Request(
            "https://cdn.example.org/video.mp4",
            headers={"Authorization": "Bearer secret", "Cookie": "sid=abc"},
        )
        for bad_target in (
            "http://127.0.0.1/admin",
            "http://169.254.169.254/latest/meta-data/",
            "http://224.0.0.1/multicast",
            "http://cdn.example.org/video.mp4",
            "file:///etc/passwd",
        ):
            with self.subTest(target=bad_target):
                with self.assertRaisesRegex(ValueError, "MEDIA_SITE_UNSUPPORTED"):
                    redirector.redirect_request(base_req, None, 302, "Found", {}, bad_target)

        redirected = redirector.redirect_request(
            base_req, None, 302, "Found", {}, "https://other.example.net/video.mp4"
        )
        self.assertIsNotNone(redirected)
        self.assertFalse(any(k.lower() in ("authorization", "cookie") for k in redirected.headers))
        self.assertFalse(any(k.lower() in ("authorization", "cookie") for k in redirected.unredirected_hdrs))

    def test_proxy_reports_http_error_when_open_fails_before_headers_committed(self):
        handler = service.Handler.__new__(service.Handler)
        captured = {}
        handler.headers = {}
        handler.wfile = io.BytesIO()
        handler.send_response = lambda code: captured.update(status=code)
        handler.send_header = lambda name, value: None
        handler.end_headers = lambda: None
        with patch.object(service._HTTP_OPENER, "open", side_effect=ValueError("MEDIA_SITE_UNSUPPORTED")):
            service.Handler._proxy(handler, "https://example.org/video.mp4", "media.mp4", "video/mp4")
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
            "Content-Security-Policy": "default-src 'none'",
            "Permissions-Policy": "interest-cohort=()",
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

        for body, expected_code in ((b"not-json", 400), (b'{"url": "http://127.0.0.1/private"}', 422)):
            req = urllib.request.Request(
                f"http://127.0.0.1:{port}/extract",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with self.assertRaises(urllib.error.HTTPError) as err:
                urllib.request.urlopen(req, timeout=10)
            try:
                self.assertEqual(expected_code, err.exception.code)
                for name, value in expected.items():
                    self.assertEqual(value, err.exception.headers.get(name))
            finally:
                err.exception.close()

    def test_security_headers_are_emitted_on_unauthorized_error_response(self):
        _, port = self._start_server(token="secret-token")
        with self.assertRaises(urllib.error.HTTPError) as unauth:
            urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10)
        try:
            self.assertEqual(401, unauth.exception.code)
            self.assertEqual("nosniff", unauth.exception.headers.get("X-Content-Type-Options"))
            self.assertEqual("no-store", unauth.exception.headers.get("Cache-Control"))
            self.assertEqual("default-src 'none'", unauth.exception.headers.get("Content-Security-Policy"))
            self.assertEqual("interest-cohort=()", unauth.exception.headers.get("Permissions-Policy"))
        finally:
            unauth.exception.close()

    def test_health_endpoint_caches_probe_within_ttl_and_refreshes_after_expiry(self):
        clock = [100.0]
        versions = ["2026.10.26", "2026.11.01"]
        ytdlp_calls = []
        which_calls = []

        def fake_ytdlp():
            ytdlp_calls.append(1)
            return versions[min(len(ytdlp_calls) - 1, len(versions) - 1)]

        def fake_which(cmd):
            which_calls.append(cmd)
            return "/usr/bin/ffmpeg"

        with patch.object(service.time, "monotonic", side_effect=lambda: clock[0]),                 patch.object(service, "ytdlp_version", side_effect=fake_ytdlp),                 patch.object(service.shutil, "which", side_effect=fake_which):
            _, port = self._start_server(health_cache_ttl=30.0)
            for _ in range(3):
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as resp:
                    self.assertEqual(200, resp.status)
                    payload = json.loads(resp.read())
                    self.assertTrue(payload["ok"])
                    self.assertEqual("2026.10.26", payload["ytdlp"])
                    self.assertTrue(payload["ffmpeg"])
                    self.assertEqual(0, payload["uptime_seconds"])
                    self.assertIn("metrics", payload)
            self.assertEqual(1, len(ytdlp_calls), "ytdlp_version must be cached across /health probes within TTL")
            self.assertEqual(1, len(which_calls), "shutil.which must be cached across /health probes within TTL")

            clock[0] += 31.0
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as resp:
                self.assertEqual(200, resp.status)
                payload = json.loads(resp.read())
                self.assertTrue(payload["ok"])
                self.assertEqual("2026.11.01", payload["ytdlp"])
                self.assertTrue(payload["ffmpeg"])
                self.assertEqual(31, payload["uptime_seconds"])
            self.assertEqual(2, len(ytdlp_calls), "expired TTL must refresh /health probe")
            self.assertEqual(2, len(which_calls))

    def test_ready_endpoint_reflects_ytdlp_availability_and_shutdown_state(self):
        server, port = self._start_server(health_cache_ttl=0)
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

    def test_ready_endpoint_reuses_cached_ytdlp_status_and_checks_shutdown_live(self):
        clock = [200.0]
        versions = ["2026.10.26", "2026.11.01"]
        ytdlp_calls = []
        which_calls = []

        def fake_ytdlp():
            ytdlp_calls.append(1)
            return versions[min(len(ytdlp_calls) - 1, len(versions) - 1)]

        def fake_which(cmd):
            which_calls.append(cmd)
            return "/usr/bin/ffmpeg"

        with patch.object(service.time, "monotonic", side_effect=lambda: clock[0]), \
                patch.object(service, "ytdlp_version", side_effect=fake_ytdlp), \
                patch.object(service.shutil, "which", side_effect=fake_which):
            server, port = self._start_server(health_cache_ttl=30.0)
            for _ in range(2):
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=10) as resp:
                    self.assertEqual(200, resp.status)
                    self.assertEqual(
                        {"ready": True, "shutting_down": False, "ytdlp": "2026.10.26", "ffmpeg": True},
                        json.loads(resp.read()),
                    )
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as resp:
                self.assertEqual(200, resp.status)
                payload = json.loads(resp.read())
                self.assertTrue(payload["ok"])
                self.assertEqual("2026.10.26", payload["ytdlp"])
                self.assertTrue(payload["ffmpeg"])
            self.assertEqual(1, len(ytdlp_calls), "/ready and /health must share cached ytdlp_version within TTL")
            self.assertEqual(1, len(which_calls))

            server.shutting_down = True
            with self.assertRaises(urllib.error.HTTPError) as draining:
                urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=10)
            try:
                self.assertEqual(503, draining.exception.code)
                payload = json.loads(draining.exception.read())
                self.assertFalse(payload["ready"])
                self.assertTrue(payload["shutting_down"])
            finally:
                draining.exception.close()
            self.assertEqual(1, len(ytdlp_calls), "live shutting_down check must not bypass ytdlp cache")

            server.shutting_down = False
            clock[0] += 31.0
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/ready", timeout=10) as resp:
                self.assertEqual(200, resp.status)
                self.assertEqual("2026.11.01", json.loads(resp.read())["ytdlp"])
            self.assertEqual(2, len(ytdlp_calls))

    def test_head_probes_return_empty_body_matching_headers_and_enforce_auth(self):
        with patch.object(service, "ytdlp_version", return_value="2026.01.01"):
            server, port = self._start_server(token="probe-secret", health_cache_ttl=30.0)
            for path in ("/health", "/ready"):
                req = urllib.request.Request(f"http://127.0.0.1:{port}{path}", method="HEAD")
                with self.assertRaises(urllib.error.HTTPError) as unauth:
                    urllib.request.urlopen(req, timeout=10)
                try:
                    self.assertEqual(401, unauth.exception.code)
                    self.assertEqual(b"", unauth.exception.read())
                    self.assertEqual("default-src 'none'", unauth.exception.headers.get("Content-Security-Policy"))
                    self.assertEqual("interest-cohort=()", unauth.exception.headers.get("Permissions-Policy"))
                finally:
                    unauth.exception.close()

            auth_headers = {"Authorization": "Bearer probe-secret"}
            for path in ("/health", "/ready"):
                req = urllib.request.Request(f"http://127.0.0.1:{port}{path}", headers=auth_headers, method="HEAD")
                with urllib.request.urlopen(req, timeout=10) as resp:
                    self.assertEqual(200, resp.status)
                    self.assertEqual(b"", resp.read())
                    self.assertEqual("application/json", resp.headers.get("Content-Type"))
                    self.assertGreater(int(resp.headers.get("Content-Length") or "0"), 0)
                    self.assertEqual("default-src 'none'", resp.headers.get("Content-Security-Policy"))
                    self.assertEqual("interest-cohort=()", resp.headers.get("Permissions-Policy"))

            server.shutting_down = True
            req = urllib.request.Request(f"http://127.0.0.1:{port}/ready", headers=auth_headers, method="HEAD")
            with self.assertRaises(urllib.error.HTTPError) as draining:
                urllib.request.urlopen(req, timeout=10)
            try:
                self.assertEqual(503, draining.exception.code)
                self.assertEqual(b"", draining.exception.read())
                self.assertGreater(int(draining.exception.headers.get("Content-Length") or "0"), 0)
            finally:
                draining.exception.close()

            req = urllib.request.Request(f"http://127.0.0.1:{port}/unknown", headers=auth_headers, method="HEAD")
            with self.assertRaises(urllib.error.HTTPError) as missing:
                urllib.request.urlopen(req, timeout=10)
            try:
                self.assertEqual(404, missing.exception.code)
                self.assertEqual(b"", missing.exception.read())
            finally:
                missing.exception.close()

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

    def test_cors_config_validation_accepts_valid_origins_and_rejects_invalid(self):
        default_cfg = service.parse_config([], environ={})
        self.assertEqual("", default_cfg.cors_origin)

        for valid in ("*", "https://app.example.com", "http://localhost:3000", "https://[::1]:8443"):
            with self.subTest(valid=valid):
                self.assertEqual(valid, service.parse_config(["--cors-origin", valid], environ={}).cors_origin)
                self.assertEqual(valid, service.parse_config([], environ={"JUMLO_MEDIA_CORS_ORIGIN": valid}).cors_origin)

        for invalid in (
            "https://app.example.com/",
            "https://app.example.com/path",
            "https://app.example.com?q=1",
            "https://app.example.com#frag",
            "https://user@app.example.com",
            "ftp://app.example.com",
            "null",
            "app.example.com",
            "https://app.example.com:99999",
            "https://app.example.com\r\nX-Evil: 1",
        ):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    service.parse_config(["--cors-origin", invalid], environ={})
                captured = io.StringIO()
                with redirect_stderr(captured), self.assertRaises(SystemExit) as exited:
                    service.main(["--cors-origin", invalid])
                self.assertEqual(2, exited.exception.code)

    def test_cors_disabled_by_default_omits_headers_and_rejects_options(self):
        _, port = self._start_server()
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as resp:
            self.assertEqual(200, resp.status)
            self.assertIsNone(resp.headers.get("Access-Control-Allow-Origin"))
            self.assertIsNone(resp.headers.get("Vary"))

        req = urllib.request.Request(f"http://127.0.0.1:{port}/extract", method="OPTIONS")
        with self.assertRaises(urllib.error.HTTPError) as err:
            urllib.request.urlopen(req, timeout=10)
        try:
            self.assertEqual(405, err.exception.code)
            self.assertEqual("GET, POST, HEAD", err.exception.headers.get("Allow"))
            self.assertIsNone(err.exception.headers.get("Access-Control-Allow-Origin"))
        finally:
            err.exception.close()

    def test_cors_headers_and_options_preflight_when_configured(self):
        _, port = self._start_server(token="cors-secret", cors_origin="https://app.example.com")

        with self.assertRaises(urllib.error.HTTPError) as unauth:
            urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10)
        try:
            self.assertEqual(401, unauth.exception.code)
            self.assertEqual("https://app.example.com", unauth.exception.headers.get("Access-Control-Allow-Origin"))
            self.assertEqual("Origin", unauth.exception.headers.get("Vary"))
        finally:
            unauth.exception.close()

        auth_req = urllib.request.Request(
            f"http://127.0.0.1:{port}/health",
            headers={"Authorization": "Bearer cors-secret"},
        )
        with urllib.request.urlopen(auth_req, timeout=10) as resp:
            self.assertEqual(200, resp.status)
            self.assertEqual("https://app.example.com", resp.headers.get("Access-Control-Allow-Origin"))
            self.assertEqual("Origin", resp.headers.get("Vary"))

        preflight = urllib.request.Request(f"http://127.0.0.1:{port}/extract", method="OPTIONS")
        with urllib.request.urlopen(preflight, timeout=10) as resp:
            self.assertEqual(204, resp.status)
            self.assertEqual(b"", resp.read())
            self.assertEqual("https://app.example.com", resp.headers.get("Access-Control-Allow-Origin"))
            self.assertEqual("Origin", resp.headers.get("Vary"))
            self.assertEqual("GET, POST, HEAD, OPTIONS", resp.headers.get("Access-Control-Allow-Methods"))
            self.assertEqual("Authorization, Content-Type", resp.headers.get("Access-Control-Allow-Headers"))
            self.assertEqual("nosniff", resp.headers.get("X-Content-Type-Options"))

    def test_health_metrics_track_uptime_requests_errors_rate_limits_and_active_extractions(self):
        clock = [500.0]
        entered = threading.Event()
        release = threading.Event()

        def slow_extract(_url):
            entered.set()
            release.wait(timeout=5)
            return {"id": "1", "formats": []}

        with patch.object(service.time, "monotonic", side_effect=lambda: clock[0]), \
                patch.object(service, "ytdlp_version", return_value="2026.10.26"), \
                patch.object(service, "extract", side_effect=slow_extract):
            _, port = self._start_server(rate_limit=2, max_concurrent=1, log_level="ERROR")
            clock[0] += 12.7

            # Trigger 1 in-flight extraction, then 1 404, then 1 429 rate limit.
            body = json.dumps({"url": "https://example.org/watch"}).encode("utf-8")
            worker_status = []

            def run_extract():
                req = urllib.request.Request(
                    f"http://127.0.0.1:{port}/extract",
                    data=body,
                    headers={"Content-Type": "application/json"},
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=10) as resp:
                    worker_status.append(resp.status)

            worker = threading.Thread(target=run_extract, daemon=True)
            worker.start()
            self.assertTrue(entered.wait(timeout=5))
            try:
                with self.assertRaises(urllib.error.HTTPError) as missing:
                    urllib.request.urlopen(f"http://127.0.0.1:{port}/missing", timeout=10)
                self.assertEqual(404, missing.exception.code)
                missing.exception.close()

                with self.assertRaises(urllib.error.HTTPError) as limited:
                    urllib.request.urlopen(f"http://127.0.0.1:{port}/missing", timeout=10)
                self.assertEqual(429, limited.exception.code)
                limited.exception.close()

                with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as resp:
                    raw = resp.read()
                    payload = json.loads(raw)
                self.assertNotIn(b"127.0.0.1", raw)
                self.assertNotIn(b"example.org", raw)
                self.assertEqual(12, payload["uptime_seconds"])
                self.assertEqual(
                    {
                        "requests_total": 2,
                        "errors_total": 2,
                        "rate_limited_total": 1,
                        "active_extractions": 1,
                    },
                    payload["metrics"],
                )
            finally:
                release.set()
            worker.join(timeout=5)
            self.assertEqual([200], worker_status)

            with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=10) as resp:
                after = json.loads(resp.read())
            self.assertEqual(
                {
                    "requests_total": 4,
                    "errors_total": 2,
                    "rate_limited_total": 1,
                    "active_extractions": 0,
                },
                after["metrics"],
            )




class ExtractionEdgeCasesTest(unittest.TestCase):
    def test_single_format_entry_without_formats_array_is_extracted(self):
        payload = {
            "id": "track-1",
            "extractor_key": "Soundcloud",
            "title": "Demo Track",
            "format_id": "http_mp3_128",
            "url": "https://cdn.example.org/track.mp3",
            "ext": "mp3",
            "vcodec": "none",
            "acodec": "mp3",
            "abr": 128,
        }
        with patch.object(service, "run_ytdlp", return_value=_completed(["yt-dlp"], 0, json.dumps(payload))):
            result = service.extract("https://example.org/track")
        self.assertNotIn("error", result)
        self.assertEqual(1, len(result["formats"]))
        self.assertEqual("http_mp3_128", result["formats"][0]["id"])
        self.assertEqual("mp3", result["formats"][0]["ext"])

    def test_multi_entry_playlist_or_carousel_reports_playlist_selection_required(self):
        payload = {
            "_type": "playlist",
            "id": "carousel-1",
            "title": "Public Carousel",
            "entries": [
                {"id": "1", "url": "https://cdn.example.org/1.mp4"},
                {"id": "2", "url": "https://cdn.example.org/2.mp4"},
            ],
        }
        with patch.object(service, "run_ytdlp", return_value=_completed(["yt-dlp"], 0, json.dumps(payload))):
            result = service.extract("https://example.org/carousel")
        self.assertEqual({"error": "PLAYLIST_REQUIRES_ITEM_SELECTION"}, result)

    def test_single_entry_wrapped_playlist_is_unwrapped(self):
        payload = {
            "_type": "playlist",
            "id": "post-1",
            "extractor_key": "Instagram",
            "title": "Single Wrapped Post",
            "uploader": "creator",
            "entries": [
                {
                    "id": "item-1",
                    "formats": [
                        {"format_id": "v1", "url": "https://cdn.example.org/v1.mp4", "ext": "mp4", "width": 720, "height": 1280}
                    ],
                }
            ],
        }
        with patch.object(service, "run_ytdlp", return_value=_completed(["yt-dlp"], 0, json.dumps(payload))):
            result = service.extract("https://example.org/p/1")
        self.assertNotIn("error", result)
        self.assertEqual("Single Wrapped Post", result["title"])
        self.assertEqual("creator", result["uploader"])
        self.assertEqual(1, len(result["formats"]))
        self.assertEqual("v1", result["formats"][0]["id"])

    def test_live_and_upcoming_streams_report_live_not_finite(self):
        for extra in ({"is_live": True}, {"live_status": "is_upcoming"}):
            payload = {"id": "live-1", "title": "Live Stream", **extra}
            with self.subTest(extra=extra):
                with patch.object(service, "run_ytdlp", return_value=_completed(["yt-dlp"], 0, json.dumps(payload))):
                    self.assertEqual({"error": "LIVE_MEDIA_NOT_FINITE"}, service.extract("https://example.org/live"))

    def test_dimensioned_video_with_omitted_vcodec_keeps_video_track(self):
        # HLS or progressive video where acodec is declared (or protocol is m3u8_native) and vcodec is omitted.
        progressive = service.to_format({"format_id": "v1", "ext": "mp4", "width": 1920, "height": 1080, "acodec": "aac"})
        self.assertEqual("unknown", progressive["vcodec"])
        hls = service.to_format({"format_id": "h1", "ext": "mp4", "width": 1280, "height": 720, "protocol": "m3u8_native"})
        self.assertEqual("unknown", hls["vcodec"])

    def test_resolve_stream_handles_uppercase_output_extension(self):
        def fake_run(args, timeout=120):
            target = args[args.index("-o") + 1].replace("%(ext)s", "MP4")
            Path(target).write_bytes(b"\x00\x00\x00\x18ftypmp42" * 8)
            return _completed(args, 0, "", "")

        with patch.object(service, "run_ytdlp", side_effect=fake_run):
            path, name, mime = service.resolve_stream("https://example.org/clip", "video", "18", 0)
        self.addCleanup(shutil.rmtree, os.path.dirname(path), ignore_errors=True)
        self.assertEqual("media.MP4", name)
        self.assertEqual("video/mp4", mime)

    def test_classify_covers_geo_hyphen_rate_limit_no_video_and_expired_link(self):
        cases = (
            ("ERROR: This video is geo-restricted", "MEDIA_GEO_BLOCKED"),
            ("HTTP Error 429: Too Many Requests", "MEDIA_RATE_LIMITED"),
            ("ERROR: There's no video in this tweet", "MEDIA_NO_USABLE_FORMAT"),
            ("ERROR: No formats found for this post", "MEDIA_NO_USABLE_FORMAT"),
            ("ERROR: This link has expired", "MEDIA_LINK_EXPIRED"),
            ("ERROR: Temporary failure in name or service not known", "MEDIA_NETWORK_ERROR"),
        )
        for msg, expected in cases:
            with self.subTest(msg=msg):
                self.assertEqual(expected, service.classify(msg))


    def test_run_ytdlp_scopes_youtube_player_client_to_youtube_hosts_only(self):
        captured_cmds = []

        class _FakeProc:
            pid = 12345
            returncode = 0

            def communicate(self, timeout=None):
                return ("{}", "")

        def fake_popen(cmd, **kwargs):
            captured_cmds.append(list(cmd))
            return _FakeProc()

        with patch.object(service.subprocess, "Popen", side_effect=fake_popen):
            service.run_ytdlp(["-J", "--", "https://www.youtube.com/watch?v=abc"])
            service.run_ytdlp(["-J", "--", "https://youtu.be/abc"])
            service.run_ytdlp(["-J", "--", "https://www.tiktok.com/@user/video/12345"])
            service.run_ytdlp(["-J", "--", "https://www.instagram.com/reel/C123/"])

        for yt_cmd in captured_cmds[:2]:
            self.assertIn("--extractor-args", yt_cmd)
            self.assertEqual("youtube:player_client=default,web_embedded", yt_cmd[yt_cmd.index("--extractor-args") + 1])
        for other_cmd in captured_cmds[2:]:
            self.assertNotIn("--extractor-args", other_cmd)

    def test_resolve_stream_video_uses_android_compatible_format_sort(self):
        captured = []

        def fake_run(args, timeout=120):
            captured.append(list(args))
            target = args[args.index("-o") + 1].replace("%(ext)s", "mp4")
            Path(target).write_bytes(b"\x00\x00\x00\x18ftypmp42" * 8)
            return _completed(args, 0, "", "")

        with patch.object(service, "run_ytdlp", side_effect=fake_run):
            path, _, _ = service.resolve_stream("https://example.org/clip", "video", "137+140", 0)
        self.addCleanup(shutil.rmtree, os.path.dirname(path), ignore_errors=True)
        self.assertIn("-S", captured[0])
        self.assertEqual("res,vcodec:h264,acodec:m4a", captured[0][captured[0].index("-S") + 1])


class SecurityHardeningRegressionTest(unittest.TestCase):
    def test_dns_ssrf_and_empty_userinfo_bypass_are_blocked(self):
        for bad_url in (
            "https://@example.org/watch",
            "https://:@example.org/watch",
            "https://example.org/" + ("a" * 9000),
        ):
            with self.subTest(url=bad_url[:64]):
                self.assertFalse(service._is_public_web_url(bad_url))

        for resolved_ip in ("127.0.0.1", "169.254.169.254", "100.64.0.1", "::ffff:10.0.0.1"):
            fake_addr = [(service.socket.AF_INET, service.socket.SOCK_STREAM, 6, "", (resolved_ip, 443))]
            with self.subTest(resolved_ip=resolved_ip), patch.object(
                service.socket, "getaddrinfo", return_value=fake_addr
            ):
                self.assertFalse(service._is_public_web_url("https://rebind.example.org/video"))

        public_addr = [(service.socket.AF_INET, service.socket.SOCK_STREAM, 6, "", ("93.184.216.34", 443))]
        with patch.object(service.socket, "getaddrinfo", return_value=public_addr):
            self.assertTrue(service._is_public_web_url("https://example.org/video"))

    def test_get_and_head_enforce_uri_length_and_query_field_limits(self):
        for method in ("do_GET", "do_HEAD"):
            handler = service.Handler.__new__(service.Handler)
            handler.path = "/stream?" + ("a" * 9000)
            handler.headers = {}
            handler.wfile = io.BytesIO()
            captured = {}
            handler.send_response = lambda code: captured.update(status=code)
            handler.send_header = lambda name, value: None
            handler.end_headers = lambda: None
            getattr(handler, method)()
            self.assertEqual(414, captured["status"])
            self.assertTrue(handler.close_connection)

        handler = service.Handler.__new__(service.Handler)
        handler.path = "/stream?" + "&".join(f"k{i}=1" for i in range(40))
        handler.headers = {}
        handler.wfile = io.BytesIO()
        captured = {}
        handler.send_response = lambda code: captured.update(status=code)
        handler.send_header = lambda name, value: None
        handler.end_headers = lambda: None
        handler.do_GET()
        self.assertEqual(400, captured["status"])

    def test_post_extract_rejects_transfer_encoding_and_truncated_body_and_closes_connection(self):
        cases = (
            ({"Transfer-Encoding": "chunked"}, b"", 400),
            ({"Content-Length": "20"}, b'{"url":', 400),
            ({"Content-Length": "65537"}, b"{}", 413),
        )
        for headers, body, expected_status in cases:
            with self.subTest(headers=headers):
                handler = service.Handler.__new__(service.Handler)
                handler.path = "/extract"
                handler.headers = headers
                handler.rfile = io.BytesIO(body)
                handler.wfile = io.BytesIO()
                captured = {}
                handler.send_response = lambda code: captured.update(status=code)
                handler.send_header = lambda name, value: None
                handler.end_headers = lambda: None
                handler.do_POST()
                self.assertEqual(expected_status, captured["status"])
                self.assertTrue(handler.close_connection)

    def test_error_sanitizes_untrusted_codes_and_health_sanitizes_version(self):
        handler = service.Handler.__new__(service.Handler)
        handler.wfile = io.BytesIO()
        handler.send_response = lambda code: None
        handler.send_header = lambda name, value: None
        handler.end_headers = lambda: None
        handler._error("https://secret.example/leak?token=abc")
        self.assertEqual({"error": "MEDIA_EXTRACTION_FAILED"}, json.loads(handler.wfile.getvalue()))

        server = service.MediaHTTPServer(("127.0.0.1", 0), _ServiceHandler, bind_and_activate=False)
        self.addCleanup(server.server_close)
        with patch.object(service, "ytdlp_version", return_value="ERROR: https://secret.example/?tok=1"):
            version, _, body = server._cached_probe()
        self.assertEqual("unknown", version)
        self.assertNotIn(b"secret.example", body)

    def test_rate_limiter_bounds_memory_under_distinct_ip_flood(self):
        limiter = service._RateLimiter(limit=1, window=60.0, max_keys=4)
        for i in range(10):
            allowed, retry = limiter.allow(f"198.51.100.{i}")
            self.assertTrue(allowed)
            self.assertEqual(0, retry)
        self.assertLessEqual(len(limiter._hits), 4)
        allowed, retry = limiter.allow("198.51.100.9")
        self.assertFalse(allowed)
        self.assertGreaterEqual(retry, 1)

    def test_proxy_file_does_not_delete_non_jumlo_directories(self):
        work = tempfile.mkdtemp(prefix="other-app-")
        self.addCleanup(shutil.rmtree, work, ignore_errors=True)
        path = Path(work) / "media.mp4"
        path.write_bytes(b"0123456789")
        handler = service.Handler.__new__(service.Handler)
        handler.headers = {}
        handler.wfile = io.BytesIO()
        handler.send_response = lambda code: None
        handler.send_header = lambda name, value: None
        handler.end_headers = lambda: None
        service.Handler._proxy_file(handler, str(path), "media.mp4", "video/mp4")
        self.assertTrue(os.path.isdir(work), "non-jumlo directories must never be deleted")

    def test_run_ytdlp_blocks_exec_cookies_and_batch_file_options(self):
        for forbidden in (
            "--exec",
            "--exec=id",
            "--exec-before-download=id",
            "--cookies=/etc/passwd",
            "--cookies-from-browser",
            "--plugin-dirs=/tmp/p",
            "--batch-file=/tmp/b",
        ):
            with self.subTest(forbidden=forbidden):
                with self.assertRaisesRegex(RuntimeError, "MEDIA_FORBIDDEN_NETRC_OPTION"):
                    service.run_ytdlp([forbidden, "--", "https://example.org/watch"])


if __name__ == "__main__":
    unittest.main()
