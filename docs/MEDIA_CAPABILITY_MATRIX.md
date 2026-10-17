# Media downloader capability matrix — NOT TESTED

No live platform was tested in this environment (no emulator, no Android
runtime, restricted network). **No platform is claimed supported.**

| Platform | Extractor path | Live test | Result |
|---|---|---|---|
| YouTube | bundled yt-dlp (`-PextendedMedia=true`) | not run | UNVERIFIED |
| TikTok | same | not run | UNVERIFIED |
| Instagram | same | not run | UNVERIFIED |
| Facebook | same | not run | UNVERIFIED |
| LinkedIn | same | not run | UNVERIFIED |
| X | same | not run | UNVERIFIED |
| Reddit | same | not run | UNVERIFIED |
| Vimeo | same | not run | UNVERIFIED |
| Dailymotion | same | not run | UNVERIFIED |
| SoundCloud | same | not run | UNVERIFIED |

Constraints enforced in source: no DRM/paywall/private bypass; public content
by default; account content only via user signed-in WebView (cookies never
exported); first-use rights/terms confirmation; extractor refreshes from the
installed APK (`YoutubeDLRequest`/update path in media runtime), no remote
code updates. GPL-3.0 payload decision in DEPENDENCY_NOTICES.md is still
unsigned, so `dl_media` stays disabled in release and non-extended builds.
