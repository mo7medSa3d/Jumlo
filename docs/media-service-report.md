# Media service diagnosis

Service command: `python3 tools/media-service/service.py --host 127.0.0.1 --port 8099`
Health check: `curl http://127.0.0.1:8099/health` -> `{"ok": true, "ytdlp": "2026.08.19", "ffmpeg": true}`

yt-dlp version before: 2026.08.19
yt-dlp version after update attempt (`yt-dlp -U`): 2026.08.19 (already latest)

| URL | HTTP status | Result / error | Classification |
|---|---|---|---|
| YouTube public video (dQw4w9WgXcQ) | 200 | formats returned | works |
| TikTok public video | 200 | formats returned | works |
| Facebook public video | 200 | formats returned (sd/hd) | works |
| SoundCloud track (Flickermood) | 200 | formats returned | works |
| Direct MP4 (Google Cloud Storage sample) | 422 | MEDIA_SOURCE_REJECTED | network/source rejected (upstream 403) |
| Direct MP4 (test-videos.co.uk) | 200 | formats returned | works |
| Direct MP3 (SoundHelix) | 200 | formats returned | works |
| Instagram public post | 422 | MEDIA_SESSION_REQUIRED | login/cookies required |

No code changes were required for the media service; failures are upstream/network or login-wall conditions. DRM/paywalls were not bypassed.
