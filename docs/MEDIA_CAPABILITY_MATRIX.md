> **2026-10-18 current lightweight-source update:** `dl_media` is present/enabled in `CapabilityPolicy` and now probes discoverable static HTML5 media URLs in addition to direct HTTP(S) media files. Source-level Kotlin tests verified parsing and controlled local transfer; **no real provider, Android player, video resolution or audio format has been qualified**. No native bundled extractor, HLS/DASH muxer or DRM support was added. The 10-platform table below remains **UNVERIFIED** and the old notes about debug-only yt-dlp are historic revision details; do not infer that those binaries ship in this source.

# Jumlo media capability matrix — live qualification


> **2026-10-17 hardened ZIP follow-up:** All 10 named media platforms and
> Google Drive remain **UNVERIFIED**. No platform video, audio, authenticated
> content or official Drive API was fetched in this execution. The host's 5/5
> FFmpeg tests involve synthetic local fixtures and the host-installed binary,
> not Android FFmpeg. The executable extraction update was changed to restore
> only the packaged yt-dlp payload, never auto-install an unpinned download.
> Explicit content-rights consent now defaults unchecked, with a handler gate;
> neither change qualifies a platform or unlocks `dl_media` in release.

Updated 2026-10-17. **No external platform download was executed in this session**, and the complete media capability remains hidden/denied by `CapabilityPolicy` in debug and release. Network from the build container was unavailable; no Android device/SDK or bundled extractor binary was available for a live end-to-end check. FFmpeg host fixtures are not substitutes for a platform result. The public UI must **not** label any platform VERIFIED.

All media testing must use public, rights-clear *single items*, the resolution/codec reported by the real extractor, real on-device output opening and an inspected log with no signed URLs, passwords, cookies or tokens. Record CI/run SHA, test date, URL **type** rather than credentials, actual resolution, audio format, reader and error classification; do not embed signed test URLs in the report.

| Platform | URL type to qualify | Video + reported resolution/codec | Audio + actual codec | Live date | Session status |
|---|---|---|---|---|---|
| YouTube | Single public watch/short video | Not run | Not run | — | **UNVERIFIED** |
| TikTok | Single public post/video | Not run | Not run | — | **UNVERIFIED** |
| Instagram | Single public Reel/post | Not run | Not run | — | **UNVERIFIED** |
| Facebook | Single public video/post | Not run | Not run | — | **UNVERIFIED** |
| LinkedIn | Single public public post | Not run | Not run | — | **UNVERIFIED** |
| X | Single public status | Not run | Not run | — | **UNVERIFIED** |
| Reddit | Single public media post | Not run | Not run | — | **UNVERIFIED** |
| Vimeo | Single public video | Not run | Not run | — | **UNVERIFIED** |
| Dailymotion | Single public video | Not run | Not run | — | **UNVERIFIED** |
| SoundCloud | Single public audio track | N/A — audio-first service | Not run | — | **UNVERIFIED** |
| Google Drive | User-owned or explicitly shared Drive file ID | N/A (file API) | N/A (file API) | — | **UNVERIFIED** (OAuth + official Drive v3 real transfer not run) |
| Direct HTTPS download | Public test file URL without signed query | Host HTTP streamed bytes, redirects/resume: PASS; Android output opening not run | N/A | 2026-10-17 (host only) | **PARTIAL — host transport only** |

### What the current session actually proved

- `python3 scripts/test-media-ffmpeg.py`: **5/5** synthetic local video/audio mux/transcode/invalid-input checks (host `/usr/bin/ffmpeg`, NOT the optional mobile FFmpeg binary).
- `scripts/verify-host.sh`: media subprocess/metadata/format policies and network guard tests passed against local fakes/loopback, not these named live platforms.
- The media extractor runtime remains **unqualified**: no pinned yt-dlp binary execution, no platform video/audio download, no exact bundled FFmpeg `-buildconf` / hash, no permissions or device test, no verified codec/resolution per service.
- The Gradle source configuration was hardened so `-PextendedMedia=true` supplies optional media runtime and its binaries to **debug only**. Release and benchmark source sets compile the base no-media factory and have no direct optional media dependencies. The native Gradle dependency verification still needs to run.

### Owner release/licensing decision

Do **not** turn on `dl_media` in public builds. Obtain actual native FFmpeg binary build flags and component source, GPL/LGPL obligations, runtime/license/copyright attributions, ffmpeg/yt-dlp/wrapper license texts, platform usage policy review, signed release contents and 10-platform live acceptance before the owner approves a redistribution route. See [DEPENDENCY_NOTICES.md](DEPENDENCY_NOTICES.md) and [RELEASE.md](RELEASE.md).
