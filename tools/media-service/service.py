#!/usr/bin/env python3
"""Jumlo media extraction service.

A thin, maintained-extractor front end: the phone sends a public URL, this service
answers with the formats that yt-dlp can offer, and the phone downloads the chosen
stream. The extractor lives here, so it can be updated (pip install -U yt-dlp) without
shipping a new app.

The service only ever handles public URLs the user pasted or shared. It does not
decrypt DRM, does not bypass paywalls, does not log in to private accounts and does
not work around rate limits: when the upstream site requires a session, the service
reports that and the app shows a human message.

Only the standard library is used, so the service runs on a bare Python image with
yt-dlp and ffmpeg installed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# The upstream site decides what a request means; these are the shapes the app
# understands. They mirror the app's MediaError codes exactly.
ERROR_CODES = {
    # Match the access restriction before the generic "sign in" category.
    "age-restricted": "MEDIA_AGE_RESTRICTED",
    "age restricted": "MEDIA_AGE_RESTRICTED",
    "confirm your age": "MEDIA_AGE_RESTRICTED",
    "age verification": "MEDIA_AGE_RESTRICTED",
    "unsupported url": "MEDIA_SITE_UNSUPPORTED",
    "unsupported site": "MEDIA_SITE_UNSUPPORTED",
    "no video formats found": "MEDIA_NO_USABLE_FORMAT",
    "requested format is not available": "MEDIA_REANALYSIS_REQUIRED",
    "private video": "MEDIA_PRIVATE_CONTENT",
    "members-only": "MEDIA_PRIVATE_CONTENT",
    "this video is private": "MEDIA_PRIVATE_CONTENT",
    "account authentication is required": "MEDIA_SESSION_REQUIRED",
    "sign in to confirm": "MEDIA_SESSION_REQUIRED",
    "cookies": "MEDIA_SESSION_REQUIRED",
    "not a bot": "MEDIA_SESSION_REQUIRED",
    "only works when logged-in": "MEDIA_SESSION_REQUIRED",
    "not available in your country": "MEDIA_GEO_BLOCKED",
    "geo restricted": "MEDIA_GEO_BLOCKED",
    "video unavailable": "MEDIA_REMOVED",
    "has been removed": "MEDIA_REMOVED",
    "drm": "MEDIA_ENCRYPTED_SOURCE",
    "encrypted": "MEDIA_ENCRYPTED_SOURCE",
    "429": "MEDIA_RATE_LIMITED",
    "rate limit": "MEDIA_RATE_LIMITED",
    "timed out": "MEDIA_NETWORK_ERROR",
    "connection reset": "MEDIA_NETWORK_ERROR",
    "403": "MEDIA_SOURCE_REJECTED",
    "401": "MEDIA_SOURCE_REJECTED",
    "404": "MEDIA_REMOVED",
    "not found": "MEDIA_REMOVED",
    "unable to download webpage": "MEDIA_NETWORK_ERROR",
    "no space left": "MEDIA_STORAGE_FULL",
    "disk full": "MEDIA_STORAGE_FULL",
}


def classify(message: str) -> str:
    """Map an upstream failure onto the app's error vocabulary."""
    text = (message or "").lower()
    for needle, code in ERROR_CODES.items():
        if needle in text:
            return code
    return "MEDIA_EXTRACTION_FAILED"


def run_ytdlp(args: list[str], timeout: int = 120) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["yt-dlp", "--ignore-config", "--no-plugin-dirs", "--no-playlist",
         "--socket-timeout", "20", "--retries", "2", "--fragment-retries", "2", *args],
        capture_output=True, text=True, timeout=timeout, check=False,
    )


def ytdlp_version() -> str:
    try:
        result = subprocess.run(["yt-dlp", "--version"], capture_output=True, text=True, timeout=30, check=False)
        return (result.stdout or "").strip() or "unknown"
    except Exception:
        return "unavailable"


def to_format(entry: dict) -> dict:
    """Convert one yt-dlp format entry into the app's format shape."""
    height = entry.get("height")
    fps = entry.get("fps")
    vbr = entry.get("vbr")
    abr = entry.get("abr")
    tbr = entry.get("tbr")
    if vbr is None and tbr is not None:
        vbr = tbr
    extension = (entry.get("ext") or "mp4").lower()
    video_codec = (entry.get("vcodec") or "").split(".")[0]
    audio_codec = (entry.get("acodec") or "").split(".")[0]
    # Some sites (Facebook, X) publish a progressive file and report no codec at
    # all. A progressive container carries both tracks, so say so - otherwise the
    # app would look for a separate audio stream and try to mux one in.
    progressive = extension in ("mp4", "m4v", "mov", "webm", "mkv") and entry.get('protocol', 'https') in ('http', 'https')
    audio_only = extension in ("m4a", "mp3", "ogg", "opus", "flac", "wav", "aac")
    if not video_codec and not audio_codec:
        if progressive:
            video_codec = audio_codec = "unknown"
        elif audio_only:
            audio_codec = "unknown"
    if not audio_codec and progressive and video_codec not in ('', 'none'):
        audio_codec = 'unknown'
    if abr is None and tbr is not None and (video_codec == 'none' or audio_only):
        abr = tbr
    return {
        "id": str(entry.get("format_id") or ""),
        "ext": extension,
        "vcodec": video_codec or "none",
        "acodec": audio_codec or "none",
        "height": height,
        "width": entry.get('width'),
        "fps": fps,
        "vbr": vbr,
        "abr": abr,
        "filesize": entry.get("filesize"),
        "filesize_approx": entry.get("filesize_approx"),
        "protocol": entry.get("protocol") or "https",
        "language": entry.get("language"),
        "drm": bool(entry.get("has_drm")),
    }


def extract(url: str) -> dict:
    """Return the formats for a public URL, or an error code."""
    if not re.match(r"^https?://", url, re.IGNORECASE):
        return {"error": "MEDIA_SITE_UNSUPPORTED"}
    try:
        result = run_ytdlp(["-J", url], timeout=120)
    except subprocess.TimeoutExpired:
        return {"error": "MEDIA_NETWORK_ERROR"}
    if result.returncode != 0:
        return {"error": classify(result.stderr or result.stdout)}
    try:
        info = json.loads(result.stdout)
    except json.JSONDecodeError:
        return {"error": "MEDIA_EXTRACTION_FAILED"}
    formats = [to_format(f) for f in (info.get("formats") or []) if f.get("url") or f.get("format_id")]
    if not formats:
        return {"error": "MEDIA_NO_USABLE_FORMAT"}
    return {
        "id": str(info.get("id") or url),
        "extractor": str(info.get("extractor_key") or info.get("extractor") or "generic"),
        "title": str(info.get("title") or "media"),
        "duration": info.get("duration"),
        "thumbnail": info.get("thumbnail"),
        "uploader": info.get("uploader"),
        "isLive": bool(info.get("is_live")),
        "isPlaylist": bool(info.get("is_live") is False and info.get("_type") == "playlist"),
        "formats": formats,
    }


MEDIA_MIME = {
    'mp4': 'video/mp4', 'm4v': 'video/mp4', 'mov': 'video/quicktime',
    'webm': 'video/webm', 'mkv': 'video/x-matroska', 'ts': 'video/mp2t',
    'mp3': 'audio/mpeg', 'm4a': 'audio/mp4', 'opus': 'audio/ogg',
    'ogg': 'audio/ogg', 'flac': 'audio/flac', 'wav': 'audio/wav', 'aac': 'audio/aac',
}


def exact_selector(ids: str) -> str:
    parts = ids.split('+')
    if not 1 <= len(parts) <= 2 or len(set(parts)) != len(parts) or any(
            not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,95}', value) for value in parts):
        raise RuntimeError('INVALID_MEDIA_FORMAT')
    return '+'.join(f"best*[format_id='{value}']" for value in parts)


def resolve_stream(url: str, mode: str, fmt: str, bitrate: int,
                   selector: str = '', container: str = '') -> tuple[str, str, str]:
    """Materialize the selected tracks with yt-dlp before sending any response bytes.

    Printing URLs cannot merge adaptive tracks and also drops extractor-required
    headers. Let yt-dlp download, merge or extract audio using its own metadata.
    """
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password:
        raise RuntimeError('MEDIA_SITE_UNSUPPORTED')
    if mode not in ('audio', 'video') or bitrate not in (0, 64, 96, 128, 160, 192, 256, 320):
        raise RuntimeError('INVALID_MEDIA_FORMAT')
    args = ['--no-check-formats', '--abort-on-error', '--abort-on-unavailable-fragments',
            '--hls-prefer-native', '--downloader', 'native']
    if mode == 'audio':
        audio_format = (fmt or 'mp3').lower()
        if audio_format not in ('mp3', 'm4a', 'opus', 'best', 'original'):
            raise RuntimeError('INVALID_MEDIA_FORMAT')
        audio_format = 'best' if audio_format == 'original' else audio_format
        args += ['-f', exact_selector(selector) if selector else 'bestaudio/best',
                 '-x', '--audio-format', audio_format]
        if audio_format != 'best' and bitrate > 0:
            args += ['--audio-quality', f'{bitrate}K']
    else:
        args += ['-f', exact_selector(selector or fmt)]
        if container:
            if container not in ('mp4', 'webm', 'mkv'):
                raise RuntimeError('INVALID_MEDIA_CONTAINER')
            args += ['--merge-output-format', container]
    work = tempfile.mkdtemp(prefix='jumlo-media-')
    try:
        args += ['-o', os.path.join(work, 'media.%(ext)s'), '--', url]
        result = run_ytdlp(args, timeout=3600)
        if result.returncode != 0:
            raise RuntimeError(classify(result.stderr or result.stdout))
        files = [path for path in Path(work).glob('media.*')
                 if path.suffix[1:] in MEDIA_MIME and path.is_file() and not path.is_symlink()]
        if len(files) != 1 or files[0].stat().st_size <= 0:
            raise RuntimeError('MEDIA_OUTPUT_MISSING')
        target = files[0]
        extension = target.suffix[1:]
        mime = 'audio/webm' if mode == 'audio' and extension == 'webm' else MEDIA_MIME[extension]
        return str(target), target.name, mime
    except BaseException:
        shutil.rmtree(work, ignore_errors=True)
        raise


class MediaHTTPServer(ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        # socketserver's fallback prints the client address and raw traceback.
        # A request can fail before Handler's guarded logging path is reached.
        error_type = sys.exc_info()[0]
        kind = error_type.__name__ if error_type is not None else "UnknownError"
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]{0,63}", kind):
            kind = "UnknownError"
        sys.stderr.write(json.dumps({"event": "http_request_failed", "type": kind}) + "\n")


class Handler(BaseHTTPRequestHandler):
    server_version = "JumloMediaService/1.0"
    protocol_version = "HTTP/1.1"

    def log_request(self, code="-", size="-"):
        # BaseHTTPRequestHandler's default formatter includes the full request line
        # and client address. /stream carries a signed source URL in its query.
        # Only a fixed route vocabulary and numeric status may cross this boundary.
        path = getattr(self, "path", "").split("?", 1)[0]
        route = path if path in ("/extract", "/stream", "/health") else "unknown"
        status = int(code) if str(code).isdigit() and 100 <= int(code) <= 599 else None
        sys.stderr.write(json.dumps({"event": "http_request", "route": route, "status": status}) + "\n")

    def log_message(self, fmt, *args):
        # Never format arbitrary request/error text: exceptions can contain URLs,
        # authorization data and user content. Request status is recorded above.
        sys.stderr.write('{"event":"http_server_event"}\n')

    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _error(self, code: str, status: int = 422) -> None:
        self._json(status, {"error": code})

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        if parsed.path == "/health":
            self._json(200, {"ok": True, "ytdlp": ytdlp_version(), "ffmpeg": bool(shutil.which("ffmpeg"))})
            return
        if parsed.path == "/stream":
            self._stream(query)
            return
        self._error("MEDIA_SITE_UNSUPPORTED", 404)

    def _stream(self, query: dict) -> None:
        url = (query.get("url") or [""])[0]
        mode = (query.get("mode") or ["video"])[0]
        fmt = (query.get("format") or [""])[0]
        selector = (query.get("selector") or [""])[0]
        container = (query.get("container") or [""])[0]
        try:
            bitrate = int((query.get("bitrate") or ["0"])[0])
        except ValueError:
            bitrate = 0
        if not url:
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        try:
            direct, name, content_type = resolve_stream(url, mode, fmt, bitrate, selector, container)
        except RuntimeError as error:
            self._error(str(error))
            return
        except subprocess.TimeoutExpired:
            self._error("MEDIA_NETWORK_ERROR")
            return
        self._proxy(direct, name, content_type)

    def _proxy(self, direct: str, name: str, content_type: str) -> None:
        """Stream the resolved media, honouring a Range request so the app can resume."""
        if os.path.isfile(direct):
            self._proxy_file(direct, name, content_type)
            return
        start = 0
        end = None
        ranged = False
        range_header = self.headers.get("Range")
        if range_header:
            match = re.match(r"bytes=(\d+)-(\d*)", range_header.strip())
            if match:
                ranged = True
                start = int(match.group(1))
                if match.group(2):
                    end = int(match.group(2))
        request = urllib.request.Request(direct, headers={"User-Agent": "Jumlo/1.0", "Accept": "*/*"})
        if ranged:
            # Forward the range verbatim, including a zero offset, so a resumed
            # download continues instead of starting over.
            request.add_header("Range", f"bytes={start}-" + (str(end) if end is not None else ""))
            if self.headers.get('If-Range'):
                request.add_header('If-Range', self.headers['If-Range'])
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                total = response.headers.get("Content-Length")
                status = 206 if response.status == 206 else 200
                self.send_response(status)
                self.send_header("Content-Type", response.headers.get("Content-Type") or content_type)
                self.send_header("Content-Disposition", f'attachment; filename="{name}"')
                if total:
                    self.send_header("Content-Length", total)
                self.send_header("Accept-Ranges", "bytes")
                # The validator is what lets the app resume without splicing the
                # new bytes onto a file that has changed underneath it.
                for header in ("ETag", "Last-Modified", "Content-Range"):
                    value = response.headers.get(header)
                    if value:
                        self.send_header(header, value)
                self.end_headers()
                while True:
                    chunk = response.read(256 * 1024)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
        except Exception as error:  # the connection is already committed here
            self.log_message("stream failed: %s", error)

    def _proxy_file(self, path: str, name: str, content_type: str) -> None:
        """Stream a file the service produced itself, such as a converted track."""
        size = os.path.getsize(path)
        digest = hashlib.sha256()
        with open(path, 'rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(chunk)
        etag = '"' + digest.hexdigest() + '"'
        start = 0
        end = size - 1
        status = 200
        range_header = self.headers.get("Range")
        try:
            if range_header and self.headers.get('If-Range', etag) == etag:
                match = re.fullmatch(r'bytes=(\d+)-(\d*)', range_header.strip())
                if match:
                    start = int(match.group(1))
                    end = min(int(match.group(2)), size - 1) if match.group(2) else size - 1
                if not match or start >= size or end < start:
                    self.send_response(416)
                    self.send_header('Content-Range', f'bytes */{size}')
                    self.send_header('Content-Length', '0')
                    self.send_header('ETag', etag)
                    self.end_headers()
                    return
                status = 206
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Disposition", f'attachment; filename="{name}"')
            self.send_header("Content-Length", str(end - start + 1))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("ETag", etag)
            if status == 206:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.end_headers()
            with open(path, "rb") as handle:
                handle.seek(start)
                remaining = end - start + 1
                while remaining > 0:
                    chunk = handle.read(min(256 * 1024, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    remaining -= len(chunk)
        finally:
            # The converted file only exists for this one download.
            shutil.rmtree(os.path.dirname(path), ignore_errors=True)

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/extract":
            self._error("MEDIA_SITE_UNSUPPORTED", 404)
            return
        length = int(self.headers.get("Content-Length") or 0)
        try:
            payload = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        url = str(payload.get("url") or "")
        result = extract(url)
        if "error" in result:
            self._error(result["error"])
            return
        self._json(200, result)


def main() -> None:
    parser = argparse.ArgumentParser(description="Jumlo media extraction service")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8731)
    parser.add_argument("--token", default=os.environ.get("JUMLO_SERVICE_TOKEN", ""),
                        help="shared secret required in the Authorization header")
    args = parser.parse_args()

    if args.token:
        expected = f"Bearer {args.token}"
        original = Handler.do_GET
        original_post = Handler.do_POST

        def guard(func):
            def wrapper(self):
                if self.headers.get("Authorization") != expected:
                    self._error("MEDIA_SOURCE_REJECTED", 401)
                    return
                func(self)
            return wrapper

        Handler.do_GET = guard(original)
        Handler.do_POST = guard(original_post)

    server = MediaHTTPServer((args.host, args.port), Handler)
    version = ytdlp_version()
    safe_version = version if re.fullmatch(r"[0-9]{4}\.[0-9]{2}\.[0-9]{2}(?:[.-][A-Za-z0-9.]{1,32})?", version) else "unknown"
    print(json.dumps({"event": "server_started", "extractor_version": safe_version}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
