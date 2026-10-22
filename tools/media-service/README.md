# Jumlo media extraction service

A small reference service that wraps a maintained extractor (yt-dlp) and speaks the
protocol the Jumlo app expects.

The point of the split is simple: **the extractor is the part that breaks when a site
changes, and the app should not have to be released when it does.** The service owns
the extractor; the app owns the download, the progress, the pause, the resume, the
retry and the save location.

## Running it

```bash
pip install -U yt-dlp          # a maintained extractor
python3 tools/media-service/service.py --host 127.0.0.1 --port 8731
```

With a shared secret:

```bash
python3 tools/media-service/service.py --port 8731 --token "$JUMLO_SERVICE_TOKEN"
```

The service needs `yt-dlp` and `ffmpeg` on the `PATH`. It uses only the Python
standard library otherwise.

## The protocol

| Endpoint | Purpose |
|---|---|
| `POST /extract` | `{"url": "..."}` → the formats the extractor can offer, or `{"error": "MEDIA_..."}` |
| `GET /stream?url=…&mode=video\|audio&format=…&bitrate=…` | Streams the chosen stream; honours `Range` so a paused download resumes |
| `GET /health` | `{"ok": true, "ytdlp": "2026.08.19", "ffmpeg": true}` |

The error codes are the app's own vocabulary (`MEDIA_SESSION_REQUIRED`,
`MEDIA_PRIVATE_CONTENT`, `MEDIA_GEO_BLOCKED`, `MEDIA_RATE_LIMITED`,
`MEDIA_ENCRYPTED_SOURCE`, `MEDIA_REMOVED`, `MEDIA_NETWORK_ERROR`,
`MEDIA_SOURCE_REJECTED`, `MEDIA_EXTRACTION_FAILED`, …), so the app can show one plain
sentence per failure instead of a stack trace.

## Updating the extractor

```bash
pip install -U yt-dlp
```

That is the whole update. The app reads the service's version from `/health` and shows
it, so the user can see that the extractor was refreshed without an app release.

## What it will not do

* It does not decrypt DRM. A protected stream is reported as `MEDIA_ENCRYPTED_SOURCE`.
* It does not bypass a paywall, a login or a private account. A site that needs a
  session is reported as `MEDIA_SESSION_REQUIRED`; the app lets the user supply their
  own session through its existing cookie store.
* It does not work around a rate limit. `MEDIA_RATE_LIMITED` is a failure.
* It only ever handles a URL the user pasted or shared.

## Before putting this in front of users

This is a **reference** implementation, good enough to develop and test against. A
production deployment needs TLS, authentication, per-user rate limiting, a request log
that does not record URLs, and a decision about where the documents may transit. The
downloader is also the part of the app most likely to conflict with a store policy:
the standard build ships **no** media runtime, so the downloader can be offered as a
direct-APK channel without changing the rest of the app.
