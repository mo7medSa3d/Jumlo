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
from collections import OrderedDict, deque
import hashlib
import hmac
import ipaddress
import json
import math
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path
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
    "no formats found": "MEDIA_NO_USABLE_FORMAT",
    "there's no video": "MEDIA_NO_USABLE_FORMAT",
    "does not contain video": "MEDIA_NO_USABLE_FORMAT",
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
    "geo-restricted": "MEDIA_GEO_BLOCKED",
    "geo restricted": "MEDIA_GEO_BLOCKED",
    "video unavailable": "MEDIA_REMOVED",
    "has been removed": "MEDIA_REMOVED",
    "does not exist": "MEDIA_REMOVED",
    "link has expired": "MEDIA_LINK_EXPIRED",
    "link expired": "MEDIA_LINK_EXPIRED",
    "url has expired": "MEDIA_LINK_EXPIRED",
    "signature has expired": "MEDIA_LINK_EXPIRED",
    "drm": "MEDIA_ENCRYPTED_SOURCE",
    "encrypted": "MEDIA_ENCRYPTED_SOURCE",
    "429": "MEDIA_RATE_LIMITED",
    "rate limit": "MEDIA_RATE_LIMITED",
    "too many requests": "MEDIA_RATE_LIMITED",
    "timed out": "MEDIA_NETWORK_ERROR",
    "connection reset": "MEDIA_NETWORK_ERROR",
    "name or service not known": "MEDIA_NETWORK_ERROR",
    "network is unreachable": "MEDIA_NETWORK_ERROR",
    "403": "MEDIA_SOURCE_REJECTED",
    "401": "MEDIA_SOURCE_REJECTED",
    "404": "MEDIA_REMOVED",
    "410": "MEDIA_REMOVED",
    "not found": "MEDIA_REMOVED",
    "unable to download webpage": "MEDIA_NETWORK_ERROR",
    "no space left": "MEDIA_STORAGE_FULL",
    "disk full": "MEDIA_STORAGE_FULL",
}

_SECURITY_HEADERS = (
    ("X-Content-Type-Options", "nosniff"),
    ("X-Frame-Options", "DENY"),
    ("Cache-Control", "no-store"),
    ("Referrer-Policy", "no-referrer"),
    ("Content-Security-Policy", "default-src 'none'"),
    ("Permissions-Policy", "interest-cohort=()"),
)
_VALID_LOG_LEVELS = ("DEBUG", "INFO", "WARNING", "ERROR")
_MAX_URL_LENGTH = 8192
_MAX_QUERY_FIELDS = 32
_ERROR_CODE_RE = re.compile(r"[A-Z][A-Z0-9_]{1,63}")
_VERSION_RE = re.compile(r"[0-9]{4}\.[0-9]{2}\.[0-9]{2}(?:[.-][A-Za-z0-9.]{1,32})?")


def _safe_ytdlp_version(version: str) -> str:
    if version in ("unavailable", "unknown") or _VERSION_RE.fullmatch(version or ""):
        return version
    return "unknown"


def _is_safe_header_value(value: str, max_len: int = 2048) -> bool:
    return bool(value) and len(value) <= max_len and all(32 <= ord(c) < 127 for c in value)


def _is_loopback_host(host: str) -> bool:
    normalized = (host or "").strip().lower().strip("[]")
    if normalized == "localhost":
        return True
    try:
        return ipaddress.ip_address(normalized).is_loopback
    except ValueError:
        return False


def _validate_cors_origin(raw: str) -> str:
    origin = str(raw or "").strip()
    if not origin or origin == "*":
        return origin
    if not _is_safe_header_value(origin, max_len=256) or any(c in origin for c in (" ", "\t", "?", "#")):
        raise ValueError("JUMLO_MEDIA_CORS_ORIGIN must be '*', empty, or a valid http(s)://origin")
    parsed = urllib.parse.urlsplit(origin)
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("JUMLO_MEDIA_CORS_ORIGIN must be '*', empty, or a valid http(s)://origin") from exc
    if (
        parsed.scheme.lower() not in ("http", "https")
        or not parsed.hostname
        or any(c in parsed.hostname for c in ("\\", "%", "/"))
        or parsed.username is not None
        or parsed.password is not None
        or "@" in parsed.netloc
        or parsed.path
        or parsed.query
        or parsed.fragment
        or (port is not None and not 1 <= port <= 65535)
    ):
        raise ValueError("JUMLO_MEDIA_CORS_ORIGIN must be '*', empty, or a valid http(s)://origin")
    return origin


def _parse_int_range(raw: str, name: str, minimum: int, maximum: int | None = None) -> int:
    try:
        value = int(raw)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{name} must be an integer") from exc
    if value < minimum or (maximum is not None and value > maximum):
        bounds = f"between {minimum} and {maximum}" if maximum is not None else f">= {minimum}"
        raise ValueError(f"{name} must be {bounds}")
    return value


def parse_config(argv: list[str] | None = None, environ: dict[str, str] | None = None) -> argparse.Namespace:
    env = os.environ if environ is None else environ
    parser = argparse.ArgumentParser(description="Jumlo media extraction service")
    parser.add_argument("--host", default=env.get("JUMLO_MEDIA_HOST", "127.0.0.1"))
    parser.add_argument("--port", default=env.get("JUMLO_MEDIA_PORT", "8787"))
    parser.add_argument(
        "--token",
        default=env.get("JUMLO_MEDIA_TOKEN") or env.get("JUMLO_SERVICE_TOKEN", ""),
        help="shared secret required in the Authorization header",
    )
    parser.add_argument("--rate-limit", default=env.get("JUMLO_MEDIA_RATE_LIMIT", "60"))
    parser.add_argument("--max-concurrent", default=env.get("JUMLO_MEDIA_MAX_CONCURRENT", "8"))
    parser.add_argument("--log-level", default=env.get("JUMLO_MEDIA_LOG_LEVEL", "INFO"))
    parser.add_argument("--cors-origin", default=env.get("JUMLO_MEDIA_CORS_ORIGIN", ""))
    parser.add_argument(
        "--allow-unauthenticated",
        action="store_true",
        help="allow binding to a non-loopback address without JUMLO_MEDIA_TOKEN",
    )
    args = parser.parse_args(argv)

    args.port = _parse_int_range(str(args.port), "JUMLO_MEDIA_PORT", 1, 65535)
    args.rate_limit = _parse_int_range(str(args.rate_limit), "JUMLO_MEDIA_RATE_LIMIT", 1)
    args.max_concurrent = _parse_int_range(str(args.max_concurrent), "JUMLO_MEDIA_MAX_CONCURRENT", 1)
    args.log_level = str(args.log_level or "INFO").strip().upper()
    if args.log_level not in _VALID_LOG_LEVELS:
        raise ValueError(f"JUMLO_MEDIA_LOG_LEVEL must be one of {', '.join(_VALID_LOG_LEVELS)}")
    args.cors_origin = _validate_cors_origin(args.cors_origin)
    if not _is_loopback_host(args.host) and not args.token and not args.allow_unauthenticated:
        raise ValueError(
            "JUMLO_MEDIA_TOKEN is required when binding to a non-loopback host "
            "(or pass --allow-unauthenticated)"
        )
    return args


class _RateLimiter:
    """Thread-safe, bounded-memory per-IP sliding-window rate limiter."""

    def __init__(self, limit: int, window: float = 60.0, max_keys: int = 1024) -> None:
        self.limit = limit
        self.window = window
        self.max_keys = max(1, max_keys)
        self._lock = threading.Lock()
        self._hits: OrderedDict[str, deque[float]] = OrderedDict()

    def allow(self, key: str) -> tuple[bool, int]:
        with self._lock:
            now = time.monotonic()
            cutoff = now - self.window
            bucket = self._hits.get(key)
            if bucket is not None:
                self._hits.move_to_end(key)
            else:
                if len(self._hits) >= self.max_keys:
                    for stale_key in [k for k, q in self._hits.items() if not q or q[-1] <= cutoff]:
                        del self._hits[stale_key]
                    while len(self._hits) >= self.max_keys:
                        self._hits.popitem(last=False)
                bucket = deque()
                self._hits[key] = bucket
            while bucket and bucket[0] <= cutoff:
                bucket.popleft()
            if len(bucket) >= self.limit:
                retry_after = max(1, math.ceil(bucket[0] + self.window - now))
                return False, retry_after
            bucket.append(now)
            return True, 0


def _url_origin(parsed: urllib.parse.SplitResult) -> tuple[str, str, int]:
    scheme = parsed.scheme.lower()
    port = parsed.port or (443 if scheme == "https" else 80)
    return (scheme, (parsed.hostname or "").lower().rstrip("."), port)


class _HttpOnlyRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not _is_public_web_url(newurl):
            raise ValueError("MEDIA_SITE_UNSUPPORTED")
        previous = urllib.parse.urlsplit(req.full_url)
        destination = urllib.parse.urlsplit(newurl)
        if previous.scheme.lower() == "https" and destination.scheme.lower() != "https":
            raise ValueError("MEDIA_SITE_UNSUPPORTED")
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is not None and _url_origin(previous) != _url_origin(destination):
            for store in (redirected.headers, redirected.unredirected_hdrs):
                for key in [k for k in store if k.lower() in ("authorization", "proxy-authorization", "cookie")]:
                    store.pop(key, None)
        return redirected


_HTTP_OPENER = urllib.request.build_opener(
    urllib.request.HTTPHandler(),
    urllib.request.HTTPSHandler(),
    _HttpOnlyRedirectHandler(),
)


def _is_public_ip(address: ipaddress.IPv4Address | ipaddress.IPv6Address) -> bool:
    if not address.is_global or address.is_multicast:
        return False
    if isinstance(address, ipaddress.IPv6Address):
        for embedded in (address.ipv4_mapped, address.sixtofour):
            if embedded is not None and (not embedded.is_global or embedded.is_multicast):
                return False
    return True


def _is_public_web_url(url: str) -> bool:
    """Validate that url is a public http/https URL without credentials or private/loopback targets."""
    if not isinstance(url, str) or not url or len(url) > _MAX_URL_LENGTH:
        return False
    if any(ord(c) < 33 or ord(c) == 127 for c in url):
        return False
    parsed = urllib.parse.urlsplit(url)
    if (
        parsed.scheme.lower() not in ("http", "https")
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or "@" in parsed.netloc
    ):
        return False
    try:
        port = parsed.port
    except ValueError:
        return False
    if port is not None and not 1 <= port <= 65535:
        return False
    hostname = parsed.hostname.lower().rstrip(".")
    if not hostname or any(c in hostname for c in ("\\", "%", "/", "\x00")):
        return False
    if hostname == "localhost" or hostname.endswith((".localhost", ".local", ".internal")):
        return False
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError:
        try:
            address = ipaddress.IPv4Address(socket.inet_aton(hostname))
        except (OSError, ValueError):
            address = None
    if address is not None:
        return _is_public_ip(address)
    # shortcut: DNS check runs before yt-dlp/urllib connect (TOCTOU window remains); upgrade to socket-level IP pinning if exposed to hostile DNS
    try:
        resolved = socket.getaddrinfo(
            hostname,
            port or (443 if parsed.scheme.lower() == "https" else 80),
            type=socket.SOCK_STREAM,
        )
    except OSError:
        return True
    for _, _, _, _, sockaddr in resolved:
        try:
            resolved_ip = ipaddress.ip_address(sockaddr[0].split("%", 1)[0])
        except ValueError:
            return False
        if not _is_public_ip(resolved_ip):
            return False
    return True


def _safe_filename(name: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]", "_", os.path.basename(name or ""))
    return cleaned or "media"


def classify(message: str) -> str:
    """Map an upstream failure onto the app's error vocabulary."""
    text = (message or "").lower()
    for needle, code in ERROR_CODES.items():
        if needle in text:
            return code
    return "MEDIA_EXTRACTION_FAILED"


_FORBIDDEN_YTDLP_OPTS = (
    "-n", "--netrc", "--netrc-location", "--netrc-cmd",
    "--config-location", "--config-locations", "--plugin-dirs",
    "--exec", "--exec-before-download", "--cookies", "--cookies-from-browser", "--batch-file",
)
_FORBIDDEN_YTDLP_PREFIXES = (
    "--netrc-location=", "--netrc-cmd=", "--config-location=", "--config-locations=",
    "--plugin-dirs=", "--exec=", "--exec-before-download=", "--cookies=",
    "--cookies-from-browser=", "--batch-file=",
)


def _is_youtube_url(url: str) -> bool:
    host = (urllib.parse.urlsplit(url or "").hostname or "").lower().rstrip(".")
    return host in ("youtu.be", "youtube.com") or host.endswith((".youtube.com", ".youtube-nocookie.com"))


def run_ytdlp(args: list[str], timeout: int = 120) -> subprocess.CompletedProcess:
    for arg in args:
        if arg == "--":
            break
        if arg in _FORBIDDEN_YTDLP_OPTS or arg.startswith(_FORBIDDEN_YTDLP_PREFIXES):
            raise RuntimeError("MEDIA_FORBIDDEN_NETRC_OPTION")
    target_url = args[-1] if len(args) >= 2 and args[-2] == "--" else ""
    yt_args = ["--extractor-args", "youtube:player_client=android_vr,tv_embedded,web_safari,default"] if _is_youtube_url(target_url) else []
    cmd = [
        "yt-dlp", "--ignore-config", "--no-config-locations", "--no-plugin-dirs", "--no-playlist", "--geo-bypass",
        *yt_args,
        "--socket-timeout", "25", "--retries", "5", "--extractor-retries", "5", "--fragment-retries", "10", *args,
    ]
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        start_new_session=True,
    )
    try:
        stdout, stderr = proc.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except OSError:
            proc.kill()
        proc.communicate()
        raise
    return subprocess.CompletedProcess(cmd, proc.returncode, stdout, stderr)


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
    extension = {"mp4_dash": "mp4", "m4a_dash": "m4a"}.get((entry.get("ext") or "mp4").lower(), (entry.get("ext") or "mp4").lower())
    video_codec = (entry.get("vcodec") or "").split(".")[0]
    audio_codec = (entry.get("acodec") or "").split(".")[0]
    # Some sites (Facebook, X) publish a progressive file and report no codec at
    # all. A progressive container carries both tracks, so say so - otherwise the
    # app would look for a separate audio stream and try to mux one in.
    progressive = extension in ("mp4", "m4v", "mov", "webm", "mkv") and entry.get('protocol', 'https') in ('http', 'https')
    audio_only = extension in ("m4a", "mp3", "ogg", "opus", "flac", "wav", "aac")
    if not video_codec and (height is not None or entry.get("width") is not None) and extension in ("mp4", "m4v", "mov", "webm", "mkv"):
        video_codec = "unknown"
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
    if not _is_public_web_url(url):
        return {"error": "MEDIA_SITE_UNSUPPORTED"}
    try:
        result = run_ytdlp(["-J", "--", url], timeout=120)
    except subprocess.TimeoutExpired:
        return {"error": "MEDIA_NETWORK_ERROR"}
    except FileNotFoundError:
        return {"error": "MEDIA_EXTRACTION_FAILED"}
    if result.returncode != 0:
        return {"error": classify(result.stderr or result.stdout)}
    try:
        info = json.loads(result.stdout)
    except json.JSONDecodeError:
        return {"error": "MEDIA_EXTRACTION_FAILED"}
    if not isinstance(info, dict):
        return {"error": "MEDIA_EXTRACTION_FAILED"}
    raw_entries = info.get("entries")
    entries = [e for e in raw_entries if isinstance(e, dict)] if isinstance(raw_entries, list) else []
    is_collection = info.get("_type") in ("playlist", "multi_video") or bool(entries)
    if is_collection:
        if len(entries) == 1 and (entries[0].get("formats") or entries[0].get("url")):
            item = dict(entries[0])
            for fallback_key in ("extractor_key", "extractor", "title", "uploader", "channel", "thumbnail"):
                if not item.get(fallback_key) and info.get(fallback_key):
                    item[fallback_key] = info[fallback_key]
            info = item
        else:
            return {"error": "PLAYLIST_REQUIRES_ITEM_SELECTION"}
    is_live = bool(info.get("is_live")) or info.get("live_status") in ("is_live", "is_upcoming")
    if is_live:
        return {"error": "LIVE_MEDIA_NOT_FINITE"}
    raw_formats = info.get("formats") or ([info] if info.get("url") else [])
    formats = [to_format(f) for f in raw_formats if isinstance(f, dict) and (f.get("url") or f.get("format_id"))]
    if not formats:
        return {"error": "MEDIA_NO_USABLE_FORMAT"}
    return {
        "id": str(info.get("id") or url),
        "extractor": str(info.get("extractor_key") or info.get("extractor") or "generic"),
        "title": str(info.get("title") or "media"),
        "duration": info.get("duration"),
        "thumbnail": info.get("thumbnail"),
        "uploader": info.get("uploader") or info.get("channel"),
        "isLive": False,
        "isPlaylist": False,
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
    if not _is_public_web_url(url):
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
        args += ['-S', 'res,vcodec:h264,acodec:m4a', '-f', exact_selector(selector or fmt)]
        if container:
            if container not in ('mp4', 'webm', 'mkv'):
                raise RuntimeError('INVALID_MEDIA_CONTAINER')
            args += ['--merge-output-format', container]
    work = tempfile.mkdtemp(prefix='jumlo-media-')
    try:
        args += ['-o', os.path.join(work, 'media.%(ext)s'), '--', url]
        try:
            result = run_ytdlp(args, timeout=3600)
        except FileNotFoundError as error:
            raise RuntimeError('MEDIA_EXTRACTION_FAILED') from error
        if result.returncode != 0:
            raise RuntimeError(classify(result.stderr or result.stdout))
        files = [path for path in Path(work).glob('media.*')
                 if path.suffix[1:].lower() in MEDIA_MIME and path.is_file() and not path.is_symlink()]
        if len(files) != 1 or files[0].stat().st_size <= 0:
            raise RuntimeError('MEDIA_OUTPUT_MISSING')
        target = files[0]
        extension = target.suffix[1:].lower()
        mime = 'audio/webm' if mode == 'audio' and extension == 'webm' else MEDIA_MIME[extension]
        return str(target), target.name, mime
    except BaseException:
        shutil.rmtree(work, ignore_errors=True)
        raise


class MediaHTTPServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(
        self,
        server_address,
        RequestHandlerClass,
        bind_and_activate=True,
        token: str = "",
        rate_limit: int = 60,
        max_concurrent: int = 8,
        log_level: str = "INFO",
        health_cache_ttl: float = 30.0,
        cors_origin: str = "",
    ):
        self.expected_authorization = f"Bearer {token}" if token else None
        self.rate_limiter = _RateLimiter(rate_limit)
        self.concurrency_semaphore = threading.BoundedSemaphore(max_concurrent)
        self.shutting_down = False
        self.log_level = log_level
        self.cors_origin = _validate_cors_origin(cors_origin)
        self._started_at = time.monotonic()
        self._metrics_lock = threading.Lock()
        self._requests_total = 0
        self._errors_total = 0
        self._rate_limited_total = 0
        self._active_extractions = 0
        self._health_cache_lock = threading.Lock()
        self._health_cache_ttl = health_cache_ttl
        self._health_cache: tuple[float, str, bool, bytes] | None = None
        super().__init__(server_address, RequestHandlerClass, bind_and_activate=bind_and_activate)

    def _cached_probe(self) -> tuple[str, bool, bytes]:
        # Cache probe metadata + serialized /health bytes per TTL so /health and /ready avoid spawning yt-dlp per probe.
        now = time.monotonic()
        cached = self._health_cache
        if cached is not None and now < cached[0]:
            return cached[1], cached[2], cached[3]
        with self._health_cache_lock:
            now = time.monotonic()
            cached = self._health_cache
            if cached is not None and now < cached[0]:
                return cached[1], cached[2], cached[3]
            version = _safe_ytdlp_version(ytdlp_version())
            ffmpeg = bool(shutil.which("ffmpeg"))
            body = json.dumps({
                "ok": True,
                "ytdlp": version,
                "ffmpeg": ffmpeg,
            }).encode("utf-8")
            self._health_cache = (now + self._health_cache_ttl, version, ffmpeg, body)
            return version, ffmpeg, body

    def record_response(self, status: int | None) -> None:
        if status is None:
            return
        with self._metrics_lock:
            self._requests_total += 1
            if status >= 400:
                self._errors_total += 1
            if status == 429:
                self._rate_limited_total += 1

    def change_active_extractions(self, delta: int) -> None:
        with self._metrics_lock:
            self._active_extractions = max(0, self._active_extractions + delta)

    def metrics_snapshot(self) -> dict[str, int]:
        with self._metrics_lock:
            return {
                "requests_total": self._requests_total,
                "errors_total": self._errors_total,
                "rate_limited_total": self._rate_limited_total,
                "active_extractions": self._active_extractions,
            }

    def health_payload_bytes(self) -> bytes:
        version, ffmpeg = self.probe_dependencies()
        return json.dumps({
            "ok": True,
            "ytdlp": version,
            "ffmpeg": ffmpeg,
            "uptime_seconds": max(0, int(time.monotonic() - self._started_at)),
            "metrics": self.metrics_snapshot(),
        }).encode("utf-8")

    def probe_dependencies(self) -> tuple[str, bool]:
        version, ffmpeg, _ = self._cached_probe()
        return version, ffmpeg

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

    def setup(self) -> None:
        self.request.settimeout(30)
        super().setup()

    def end_headers(self) -> None:
        for name, value in _SECURITY_HEADERS:
            self.send_header(name, value)
        cors_origin = getattr(getattr(self, "server", None), "cors_origin", "")
        if cors_origin:
            self.send_header("Access-Control-Allow-Origin", cors_origin)
            self.send_header("Vary", "Origin")
        super().end_headers()

    def _authorized(self) -> bool:
        expected = getattr(getattr(self, "server", None), "expected_authorization", None)
        if not expected:
            return True
        actual = self.headers.get("Authorization") or ""
        if not hmac.compare_digest(actual.encode("utf-8"), expected.encode("utf-8")):
            self._error("MEDIA_SOURCE_REJECTED", 401)
            return False
        return True

    def _check_rate_limit(self) -> bool:
        limiter = getattr(getattr(self, "server", None), "rate_limiter", None)
        if limiter is None:
            return True
        client_address = getattr(self, "client_address", None)
        client_ip = client_address[0] if client_address else "unknown"
        allowed, retry_after = limiter.allow(client_ip)
        if not allowed:
            self._error("MEDIA_RATE_LIMITED", 429, extra_headers={"Retry-After": str(retry_after)})
            return False
        return True

    def _acquire_concurrency(self) -> bool:
        server = getattr(self, "server", None)
        if server is None:
            return True
        if getattr(server, "shutting_down", False):
            self._error("MEDIA_SERVICE_UNAVAILABLE", 503)
            return False
        semaphore = getattr(server, "concurrency_semaphore", None)
        if semaphore is None:
            return True
        if not semaphore.acquire(blocking=False):
            self._error("MEDIA_SERVICE_BUSY", 503, extra_headers={"Retry-After": "1"})
            return False
        if hasattr(server, "change_active_extractions"):
            server.change_active_extractions(1)
        return True

    def _release_concurrency(self) -> None:
        server = getattr(self, "server", None)
        semaphore = getattr(server, "concurrency_semaphore", None)
        if semaphore is not None:
            if hasattr(server, "change_active_extractions"):
                server.change_active_extractions(-1)
            semaphore.release()

    def log_request(self, code="-", size="-"):
        # BaseHTTPRequestHandler's default formatter includes the full request line
        # and client address. /stream carries a signed source URL in its query.
        # Only a fixed route vocabulary and numeric status may cross this boundary.
        path = getattr(self, "path", "").split("?", 1)[0]
        route = path if path in ("/extract", "/stream", "/health", "/ready") else "unknown"
        status = int(code) if str(code).isdigit() and 100 <= int(code) <= 599 else None
        server = getattr(self, "server", None)
        if server is not None and hasattr(server, "record_response"):
            server.record_response(status)
        level = getattr(server, "log_level", "INFO")
        if level == "ERROR" and (status is None or status < 500):
            return
        if level == "WARNING" and (status is None or status < 400):
            return
        sys.stderr.write(json.dumps({"event": "http_request", "route": route, "status": status}) + "\n")

    def log_message(self, fmt, *args):
        # Never format arbitrary request/error text: exceptions can contain URLs,
        # authorization data and user content. Request status is recorded above.
        sys.stderr.write('{"event":"http_server_event"}\n')

    def _json_bytes(self, status: int, body: bytes, extra_headers: dict[str, str] | None = None) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if extra_headers:
            for name, value in extra_headers.items():
                self.send_header(name, value)
        self.end_headers()
        if getattr(self, "command", None) != "HEAD":
            self.wfile.write(body)

    def _json(self, status: int, payload: dict, extra_headers: dict[str, str] | None = None) -> None:
        self._json_bytes(status, json.dumps(payload).encode("utf-8"), extra_headers=extra_headers)

    def _error(self, code: str, status: int = 422, extra_headers: dict[str, str] | None = None) -> None:
        safe_code = code if _ERROR_CODE_RE.fullmatch(code or "") else "MEDIA_EXTRACTION_FAILED"
        self._json(status, {"error": safe_code}, extra_headers=extra_headers)

    def _handle_probe(self, path: str) -> bool:
        server = getattr(self, "server", None)
        if path == "/health":
            if server is not None and hasattr(server, "health_payload_bytes"):
                self._json_bytes(200, server.health_payload_bytes())
            else:
                self._json(200, {"ok": True, "ytdlp": ytdlp_version(), "ffmpeg": bool(shutil.which("ffmpeg"))})
            return True
        if path == "/ready":
            shutting_down = bool(getattr(server, "shutting_down", False))
            if server is not None and hasattr(server, "probe_dependencies"):
                version, ffmpeg = server.probe_dependencies()
            else:
                version, ffmpeg = ytdlp_version(), bool(shutil.which("ffmpeg"))
            ytdlp_ready = version not in ("unavailable", "unknown", "")
            ready = (not shutting_down) and ytdlp_ready
            self._json(
                200 if ready else 503,
                {"ready": ready, "shutting_down": shutting_down, "ytdlp": version, "ffmpeg": ffmpeg},
            )
            return True
        return False

    def do_OPTIONS(self) -> None:
        if len(self.path) > _MAX_URL_LENGTH:
            self.close_connection = True
            self._error("MEDIA_OUTPUT_LIMIT", 414)
            return
        cors_origin = getattr(getattr(self, "server", None), "cors_origin", "")
        if not cors_origin:
            self._error("MEDIA_SITE_UNSUPPORTED", 405, extra_headers={"Allow": "GET, POST, HEAD"})
            return
        if not self._check_rate_limit():
            return
        self.send_response(204)
        self.send_header("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_HEAD(self) -> None:
        if len(self.path) > _MAX_URL_LENGTH:
            self.close_connection = True
            self._error("MEDIA_OUTPUT_LIMIT", 414)
            return
        if not self._authorized():
            return
        path = self.path.split("#", 1)[0].partition("?")[0]
        if not self._handle_probe(path):
            self._error("MEDIA_SITE_UNSUPPORTED", 404)

    def do_GET(self) -> None:
        if len(self.path) > _MAX_URL_LENGTH:
            self.close_connection = True
            self._error("MEDIA_OUTPUT_LIMIT", 414)
            return
        if not self._authorized():
            return
        target = self.path.split("#", 1)[0]
        path, _, raw_query = target.partition("?")
        if self._handle_probe(path):
            return
        if not self._check_rate_limit():
            return
        if path == "/stream":
            try:
                query = urllib.parse.parse_qs(raw_query, max_num_fields=_MAX_QUERY_FIELDS)
            except ValueError:
                self._error("MEDIA_SITE_UNSUPPORTED", 400)
                return
            if not self._acquire_concurrency():
                return
            try:
                self._stream(query)
            finally:
                self._release_concurrency()
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
        if urllib.parse.urlsplit(direct).scheme.lower() not in ("http", "https"):
            self._error("MEDIA_SITE_UNSUPPORTED", 422)
            return
        start = 0
        end = None
        ranged = False
        range_header = self.headers.get("Range")
        if range_header:
            match = re.fullmatch(r"bytes=(\d+)-(\d*)", range_header.strip())
            if match:
                parsed_start = int(match.group(1))
                parsed_end = int(match.group(2)) if match.group(2) else None
                if parsed_end is None or parsed_end >= parsed_start:
                    ranged = True
                    start = parsed_start
                    end = parsed_end
        request = urllib.request.Request(direct, headers={"User-Agent": "Jumlo/1.0", "Accept": "*/*"})
        if ranged:
            # Forward the range verbatim, including a zero offset, so a resumed
            # download continues instead of starting over.
            request.add_header("Range", f"bytes={start}-" + (str(end) if end is not None else ""))
            if_range = self.headers.get('If-Range')
            if if_range and _is_safe_header_value(if_range, max_len=256):
                request.add_header('If-Range', if_range)
        safe_name = _safe_filename(name)
        committed = False
        try:
            with _HTTP_OPENER.open(request, timeout=60) as response:
                total = response.headers.get("Content-Length")
                status = 206 if response.status == 206 else 200
                committed = True
                self.send_response(status)
                upstream_type = response.headers.get("Content-Type") or ""
                self.send_header("Content-Type", upstream_type if _is_safe_header_value(upstream_type, 256) else content_type)
                self.send_header("Content-Disposition", f'attachment; filename="{safe_name}"')
                if total and total.isdigit():
                    self.send_header("Content-Length", total)
                self.send_header("Accept-Ranges", "bytes")
                # The validator is what lets the app resume without splicing the
                # new bytes onto a file that has changed underneath it.
                for header in ("ETag", "Last-Modified", "Content-Range"):
                    value = response.headers.get(header)
                    if value and _is_safe_header_value(value, 512):
                        self.send_header(header, value)
                self.end_headers()
                while True:
                    chunk = response.read(256 * 1024)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
        except ValueError as error:
            if not committed:
                self._error(str(error) or "MEDIA_SITE_UNSUPPORTED", 422)
            else:
                self.close_connection = True
                self.log_message("stream failed: %s", error)
        except Exception as error:
            if not committed:
                self._error("MEDIA_NETWORK_ERROR", 502)
            else:
                self.close_connection = True
                self.log_message("stream failed: %s", error)

    def _proxy_file(self, path: str, name: str, content_type: str) -> None:
        """Stream a file the service produced itself, such as a converted track."""
        try:
            with open(path, "rb") as handle:
                size = os.fstat(handle.fileno()).st_size
                if size <= 0:
                    self._error("MEDIA_OUTPUT_MISSING", 422)
                    return
                in_memory = handle.read() if size <= 1024 * 1024 else None
                if in_memory is not None:
                    etag = '"' + hashlib.sha256(in_memory).hexdigest() + '"'
                else:
                    etag = '"' + hashlib.file_digest(handle, "sha256").hexdigest() + '"'
                start = 0
                end = size - 1
                status = 200
                range_header = self.headers.get("Range")
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
                self.send_header("Content-Disposition", f'attachment; filename="{_safe_filename(name)}"')
                self.send_header("Content-Length", str(end - start + 1))
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("ETag", etag)
                if status == 206:
                    self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
                self.end_headers()
                if in_memory is not None:
                    self.wfile.write(in_memory[start:end + 1])
                else:
                    handle.seek(start)
                    remaining = end - start + 1
                    while remaining > 0:
                        chunk = handle.read(min(1024 * 1024, remaining))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        remaining -= len(chunk)
        finally:
            # The converted file only exists for this one download; only remove jumlo-* temp dirs.
            parent = os.path.realpath(os.path.dirname(path))
            temp_root = os.path.realpath(tempfile.gettempdir())
            if os.path.dirname(parent) == temp_root and os.path.basename(parent).startswith("jumlo-"):
                shutil.rmtree(parent, ignore_errors=True)

    def do_POST(self) -> None:
        if not self._authorized():
            return
        if not self._check_rate_limit():
            return
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/extract":
            self._error("MEDIA_SITE_UNSUPPORTED", 404)
            return
        if self.headers.get("Transfer-Encoding"):
            self.close_connection = True
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            self.close_connection = True
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        if length < 0:
            self.close_connection = True
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        if length > 65536:
            self.close_connection = True
            self._error("MEDIA_OUTPUT_LIMIT", 413)
            return
        raw_body = self.rfile.read(length) if length > 0 else b""
        if len(raw_body) != length:
            self.close_connection = True
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        try:
            payload = json.loads(raw_body or b"{}")
        except (json.JSONDecodeError, UnicodeDecodeError):
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        if not isinstance(payload, dict):
            self._error("MEDIA_SITE_UNSUPPORTED", 400)
            return
        if not self._acquire_concurrency():
            return
        try:
            url = str(payload.get("url") or "")
            result = extract(url)
            if "error" in result:
                self._error(result["error"])
                return
            self._json(200, result)
        finally:
            self._release_concurrency()


def main(argv: list[str] | None = None) -> None:
    try:
        args = parse_config(argv)
    except ValueError as exc:
        sys.stderr.write(json.dumps({"event": "startup_config_invalid", "error": str(exc)}) + "\n")
        sys.exit(2)

    server = MediaHTTPServer(
        (args.host, args.port),
        Handler,
        token=args.token,
        rate_limit=args.rate_limit,
        max_concurrent=args.max_concurrent,
        log_level=args.log_level,
        cors_origin=args.cors_origin,
    )

    def _shutdown_handler(_signum, _frame):
        if not server.shutting_down:
            server.shutting_down = True
            threading.Thread(target=server.shutdown, daemon=True).start()

    if threading.current_thread() is threading.main_thread():
        signal.signal(signal.SIGTERM, _shutdown_handler)
        signal.signal(signal.SIGINT, _shutdown_handler)

    safe_version = _safe_ytdlp_version(ytdlp_version())
    print(json.dumps({"event": "server_started", "extractor_version": safe_version}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.shutting_down = True
        server.server_close()


if __name__ == "__main__":
    main()
