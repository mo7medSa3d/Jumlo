# Media downloader capability matrix — NOT TESTED

No live platform was tested (no emulator/device in the authoring
environment; CI instrumented jobs are the first place this can run).
**No platform is claimed supported, in docs or in the UI.**

Rule for upgrading a row: a passing live test on a public, rights-clear
test item (e.g. a Creative-Commons / public-domain upload), recorded with
run ID, test URL type, and date below.

| Platform | Extractor path | Test URL type | Live test date | Result |
|---|---|---|---|---|
| YouTube | bundled yt-dlp (`-PextendedMedia=true`) | public CC video — not run | — | UNVERIFIED |
| TikTok | same | public video — not run | — | UNVERIFIED |
| Instagram | same | public reel — not run | — | UNVERIFIED |
| Facebook | same | public video — not run | — | UNVERIFIED |
| LinkedIn | same | public post video — not run | — | UNVERIFIED |
| X | same | public post video — not run | — | UNVERIFIED |
| Reddit | same | public video post — not run | — | UNVERIFIED |
| Vimeo | same | public video — not run | — | UNVERIFIED |
| Dailymotion | same | public video — not run | — | UNVERIFIED |
| SoundCloud | same | public track — not run | — | UNVERIFIED |

Constraints enforced in source: public content only by default; no DRM or
encryption circumvention; no paywall/private bypass; account-gated content
only through a user-signed-in in-app WebView whose session is never
exported, logged, or persisted; rights/terms confirmation before first
use; playlists out of scope (single items); resume only when the server
supports byte ranges and the validator matches, otherwise the job restarts
and says so. Error categories surfaced: private, removed, geo-blocked,
unsupported, rate-limited, network.

Licensing (see `docs/DEPENDENCY_NOTICES.md`): yt-dlp is Unlicense/
public domain, but the Android wrapper + bundled FFmpeg in the optional
`-PextendedMedia=true` build must be treated as **GPL** until its build
flags are audited. Media stays gated **off** in release builds pending
owner sign-off.
