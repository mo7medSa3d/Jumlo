# Media downloader capability matrix — NOT LIVE-TESTED IN THIS SESSION

No live platform was tested in this session because the sandbox VM has no
outbound internet access and no Android SDK/emulator runtime.
**No platform is claimed supported in docs or in the UI (`MediaPlatformMatrix.entries` sets every external platform status to `UNVERIFIED`, and the Media Workspace renders an explicit `UNVERIFIED` status badge on each platform chip).**

Rule for upgrading a row: a passing live test on a public, rights-clear
test item (e.g. a Creative-Commons / public-domain upload) for both Video (at a reported resolution) and Audio, recorded with CI run ID, test URL, URL type, resolution/codec, and date below.

| Platform | Extractor / API path | URL type | Video test (resolution / codec) | Audio test (MP3 / M4A / Opus / source) | Live test date | Status in UI & docs |
|---|---|---|---|---|---|---|
| YouTube | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public watch/short URL (`https://www.youtube.com/watch?v=aqz-KE-bpKQ`) | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| TikTok | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public video URL | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| Instagram | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public reel/post URL | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| Facebook | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public watch video URL | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| LinkedIn | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public post video URL | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| X | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public status video URL | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| Reddit | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public post / `v.redd.it` URL | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| Vimeo | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public video URL (`https://vimeo.com/1084537`) | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| Dailymotion | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public video URL | Not run (no network/SDK in VM) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| SoundCloud | Pinned yt-dlp (`-PextendedMedia=true`) + FFmpeg | Single public track URL | N/A (audio platform) | Not run (no network/SDK in VM) | Not run | **UNVERIFIED** |
| Google Drive | Official Drive v3 API (`GoogleDriveClient`) | `drive.google.com/file/d/<id>` or file ID (signed-in user's accessible files only) | Unit-tested in source (`GoogleDriveClientTest`); live API not run in VM | Unit-tested in source (`GoogleDriveClientTest`); live API not run in VM | Not run | **UNVERIFIED** |
| Direct File (HTTPS) | `OkHttpDownloadEngine` + `StreamTransfer` | Direct HTTPS media/file URL | Host guard & FFmpeg mux verified (`test-media-bootstrap.py`, `test-media-ffmpeg.py`) | Host FFmpeg MP3/M4A/Opus verified (`test-media-ffmpeg.py`) | 2026-10-17 | **HOST_VERIFIED (8/8 URL/redirect tests & 5/5 FFmpeg tests pass; device test not run)** |

## What WAS Verified in This Session

1. **FFmpeg Media Remux & Audio Transcoding (`python3 scripts/test-media-ffmpeg.py` — 5/5 PASS)**:
   - Merging separate video (`1280x720` H.264) and audio (AAC) into MP4 (`-c:v copy -c:a aac`) while preserving exact dimensions and streams.
   - Audio-only extraction to `.mp3` (`libmp3lame`), `.m4a` (source-codec `-c:a copy`), and `.opus` (`libopus` in Ogg container).
   - Malformed input rejection with zero leftover output files.
2. **URL, Redirect & Credential Guards (`python3 scripts/test-media-bootstrap.py` — 8/8 PASS)**:
   - HTTPS enforcement, explicit per-job HTTP consent, HTTPS→HTTP downgrade blocking, cross-origin `Authorization` stripping, and unconditional `Cookie` header blocking.
3. **Hard Constraints Enforced in Source**:
   - Public single-item content by default (`--no-playlist`); no DRM/encryption circumvention (`drm` / `widevine` / `fairplay` / `playready` / `hlse` / `cenc` formats rejected at metadata parsing and classified as `MEDIA_ENCRYPTED_SOURCE`).
   - Account-gated content only through the user-initiated in-app WebView (`MediaBrowserSheet`), whose cookies are never read, exported, logged, or passed to `YoutubeDLRequest`.
   - Google Drive downloads and exports use the official Google Drive v3 REST API (`GoogleDriveClient`) with a session-only OAuth2 token (`EphemeralSecretStore`), restricted to files the signed-in user can access.
   - First-use rights & terms confirmation required and persisted (`mediaTermsAccepted` in DataStore) before URL analysis or queueing.
   - Error categories surfaced clearly in UI: `private`, `removed`, `geo-blocked`, `login-required`, `unsupported`, `rate-limited`, `network`.
   - Disabled in release builds (`BuildConfig.DEBUG && BuildConfig.EXTENDED_MEDIA` gate in `Capabilities.kt`) pending owner sign-off on GPL-3.0 obligations documented in `docs/DEPENDENCY_NOTICES.md`.
