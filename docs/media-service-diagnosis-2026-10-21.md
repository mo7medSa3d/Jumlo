# Media service diagnosis — 2026-10-21

The service was started and every platform was probed on this host. The full
request and response are recorded, not only the status code.

## The service

```
python3 tools/media-service/service.py --host 127.0.0.1 --port 8731
```

```
$ curl -sS -v http://127.0.0.1:8731/health
> GET /health HTTP/1.1
> Host: 127.0.0.1:8731
> User-Agent: curl/8.18.0
> Accept: */*
>
< HTTP/1.1 200 OK
< Server: JumloMediaService/1.0 Python/3.14.8
< Content-Type: application/json
< Content-Length: 62

{"ok": true, "ytdlp": "2026.08.19", "ffmpeg": true}
```

## yt-dlp: before and after

| | version | release date | source |
|---|---|---|---|
| installed before | `2026.08.19` | 2026-08-19 | `yt-dlp --version` |
| latest release | `2026.08.19` | published `2026-08-19T23:48:43Z` | `GET https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest` |
| installed after the update attempt | `2026.08.19` | — | `python3 -m pip install -U yt-dlp` → "Requirement already satisfied: yt-dlp in /usr/lib/python3.14/site-packages (2026.8.19)" |

**The extractor was already at the latest release.** The update was therefore a
re-verification, not a repair: none of the failures below is caused by an
outdated extractor.

## The probes

Every probe is `POST /extract` with `{"url":"…"}`.

### The direct MP4, with the full exchange

```
$ curl -sS -v -X POST -H "Content-Type: application/json" \
    -d '{"url":"https://download.samplelib.com/mp4/sample-5s.mp4"}' \
    http://127.0.0.1:8731/extract
> POST /extract HTTP/1.1
> Host: 127.0.0.1:8731
> User-Agent: curl/8.18.0
> Accept: */*
> Content-Type: application/json
> Content-Length: 58
>
} [58 bytes data]
* upload completely sent off: 58 bytes
< HTTP/1.1 200 OK
< Server: JumloMediaService/1.0 Python/3.14.8
< Date: Wed, 21 Oct 2026 19:15:41 GMT
< Content-Type: application/json
< Content-Length: 368
<
{ [368 bytes data]

{"id": "sample-5s", "extractor": "Generic", "title": "sample-5s", "duration": null,
 "thumbnail": null, "uploader": null, "isLive": false, "isPlaylist": false,
 "formats": [{"id": "mp4", "ext": "mp4", "vcodec": "unknown", "acodec": "unknown",
 "height": null, "fps": null, "vbr": null, "abr": null, "filesize": null,
 "protocol": "https", "language": null, "drm": false}]}
```

### Every platform

| platform | URL | HTTP | the result | classification |
|---|---|---|---|---|
| YouTube | `youtube.com/watch?v=aqz-KE-bpKQ` | 200 | `{"extractor":"Youtube","title":"Big Buck Bunny 60fps 4K - Official Blender Foundation Short Film","duration":635,…}` | works |
| TikTok | `tiktok.com/@scout2015/video/6718335390845095173` | 200 | `{"extractor":"TikTok","title":"Scramble up ur name & I'll try to guess it…"}` | works |
| Facebook | `facebook.com/Engineering/videos/10153231379946729/` | 200 | `{"extractor":"Facebook","title":"How to Share With Just Friends",…}` | works |
| SoundCloud | `soundcloud.com/uiceheidd/lets-go` | 422 | `{"error": "MEDIA_REMOVED"}` | **the content is gone** — the track no longer exists. Not a tool fault: a different public SoundCloud track is needed to test the extractor. |
| direct MP4 | `download.samplelib.com/mp4/sample-5s.mp4` | 200 | `{"extractor":"Generic","formats":[{"id":"mp4","ext":"mp4",…}]}` | works |
| direct MP3 | `download.samplelib.com/mp3/sample-3s.mp3` | 200 | `{"extractor":"Generic","formats":[{"id":"mpeg","ext":"mp3",…}]}` | works |
| Instagram | `instagram.com/p/CUbHfhpswxt/` | 422 | `{"error": "MEDIA_NO_USABLE_FORMAT"}` | **login wall** — the post needs the account's own session. The expected result, and the only honest one. |

### Classification

| class | count | which |
|---|---|---|
| works | 5 | YouTube, TikTok, Facebook, direct MP4, direct MP3 |
| service not running | 0 | — |
| network blocked | 0 | — |
| extractor outdated | 0 | the extractor is the latest release |
| login or cookies required | 1 | Instagram (`MEDIA_NO_USABLE_FORMAT`) |
| geo-restricted | 0 | — |
| unsupported URL | 0 | — |
| other | 1 | SoundCloud: the track was removed (`MEDIA_REMOVED`) — a content problem, not a tool problem |

## The defects the code review found, and what was done about them

These are **not** extractor problems. They are in the service and in the client.

| # | defect | where | status |
|---|---|---|---|
| D1 | The audio path combines `-x --audio-format mp3` with `--get-url`; `--get-url` only prints a URL, so no conversion happens while the file is called `media.mp3` → `INVALID_MEDIA_CONTAINER` downstream. | `tools/media-service/service.py:166-169` | **not fixed** — the service is a reference implementation and the change needs the client's expectations realigned with it. |
| D2 | The proxy forwards no `ETag`/`Last-Modified`/`Content-Range`, while `StreamTransfer` requires a resume validator → `MISSING_RESUME_VALIDATOR`. | `service.py:235-270`; `core/StreamTransfer.kt:66-71` | **not fixed** — same reason. |
| D3 | The app sends only the video format id, but `validateTracks` requires audio too → `MEDIA_AUDIO_MISSING` for a muxed source. | `data/media/RemoteExtractorRuntime.kt:149`; `data/media/MediaOperationHandler.kt:131` | **not fixed**. |
| D4 | `setMediaServiceToken` has no UI caller, so a token-protected service always answers 401. | `data/preferences/UserPreferencesRepository.kt:270-278` | **not fixed**. |
| D5 | `refreshExtractor()` is never called from the UI: the "update the extractor" button in the design note does not exist, and the version is never shown. | `data/media/RemoteExtractorRuntime.kt:187-200` | **not fixed**. |

None of these were changed in this session: the work in this session was the
compression qualification, and touching the download path without a device to
test it would be the kind of unverifiable change this report is against.

## What is needed to fix the rest

- **Device access**: the client changes (D3, D4, D5) can only be proven with a
  device and a real service URL.
- **A cookie from the user** for Instagram, and for any platform that starts
  asking for one. The service has no cookie path at all today.
- **A deployment**: the service is plain HTTP on localhost with an optional
  bearer token. It must not be exposed to the internet as it is.
