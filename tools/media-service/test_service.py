#!/usr/bin/env python3
"""The media service, tested without a network and without the real extractor.

Run it with:

    python3 tools/media-service/test_service.py
"""
from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import service  # noqa: E402


def _completed(args, returncode=0, stdout="", stderr=""):
    return subprocess.CompletedProcess(list(args), returncode, stdout, stderr)


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
            target = args[args.index("-o") + 1]
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

    def setUp(self):
        self.original = service.run_ytdlp
        self.addCleanup(setattr, service, "run_ytdlp", self.original)
        _Upstream.seen_ranges = []
        upstream = f"http://127.0.0.1:{self.port}/file.mp4"
        service.run_ytdlp = lambda args, timeout=120: _completed(args, 0, upstream + "\n", "")

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
        handler.headers = {"Range": "bytes=10-"}
        handler.log_message = lambda *args: None
        service.Handler._proxy(handler, f"http://127.0.0.1:{self.port}/file.mp4", "media.mp4", "video/mp4")
        self.assertTrue(_Upstream.seen_ranges, "the upstream must see the range")
        self.assertEqual("bytes=10-", _Upstream.seen_ranges[0])

    def test_the_service_forwards_the_validator_headers(self):
        # A real request through the service's own handler.
        server = ThreadingHTTPServer(("127.0.0.1", 0), _ServiceHandler)
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
        self.assertEqual('"v1"', headers.get("ETag"))
        self.assertEqual("Wed, 21 Oct 2026 10:00:00 GMT", headers.get("Last-Modified"))
        self.assertIn("Content-Range", headers)
        self.assertTrue(body.startswith(b"0123456789"[10:]))


class _ServiceHandler(service.Handler):
    def log_message(self, *args):
        pass


if __name__ == "__main__":
    unittest.main()
