# Jumlo Universal Media Engine (source candidate)

Date: 2026-10-18. **Release status: NOT VERIFIED.** Do not advertise support for any provider without device-level, permission-cleared transfer and playback evidence.

## Research and design choice

- Seal: Kotlin UI over `youtubedl-android`/yt-dlp. https://github.com/JunkFood02/Seal
- YTDLnis: yt-dlp plus a managed Python/FFmpeg/JS environment. https://github.com/deniscerri/ytdlnis
- NewPipe: specialized, relatively lightweight extractors, but much narrower coverage. https://github.com/TeamNewPipe/NewPipe
- Cobalt: powerful self-hosted API with AGPL-3.0 distribution implications; rejected as a new cloud dependency. https://github.com/imputnet/cobalt
- yt-dlp: wide extractor coverage; listed sites are not guaranteed functional. https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md
- YouTube currently requires a JS runtime plus compatible EJS scripts for many cases. Packaged QuickJS is not equivalent to verified functional EJS installation. https://github.com/yt-dlp/yt-dlp/wiki/EJS

**Selected:** modular local runtime: verified direct HTTP/HTML5 as the small default, optional APK-packaged yt-dlp, Python, QuickJS and FFmpeg for wider site extraction and adaptive streams. Do not require a remote server, use cloud document processing, download arbitrary native executables, or embed a third-party site's credentials. Selection and publishing use the existing persisted JobStore/WorkManager, exact format IDs, output verification and scoped file store.

## Build variants

| Command | Bundled engine | Purpose |
| --- | --- | --- |
| `./gradlew :app:assembleDebug` | OkHttp direct/HTML5 | Default small APK, existing 60 MB CI budget |
| `./gradlew -PextendedMedia=true :app:assembleDebug` | Native + bundled yt-dlp/FFmpeg/Python/QuickJS | **Source candidate only**; site coverage unverified |
| `./gradlew -PextendedMedia=true :app:testDebugUnitTest :app:lintDebug` | Extended test graph | Android compilation/lint gates, not a live provider test |

GitHub Actions: **Actions > Android CI & APK > Run workflow > extended_media: true** starts a *separate* experimental job. The ordinary APK's 60 MB guard remains unchanged. Only after Android compilation, unit tests, and lint succeed does the new job upload a `Jumlo-Extended-Media-Debug-APK-*` artifact and its SHA-256. The workflow has not been triggered on GitHub and the experimental artifact does not yet exist. Its size may exceed 60 MB; license, security, native ABI, and live-provider validation remain mandatory.

`extendedMedia` is opt-in and is compiled into the APK rather than relying on remote code updates. `src/base` factory uses only direct/HTML5 and has no extra runtime assets. `src/media` factory chooses a known platform extractor first and otherwise attempts the lightweight path, falling back only on truly unsupported site types; 401/403/429/private content never triggers a masquerading alternative. A native choice keeps its original transport. `src/media/assets` is included only in the extended build.

The opt-in engine uses `io.github.junkfood02.youtubedl-android:library:0.18.1` and `:ffmpeg:0.18.1`, and the upstream wrapper contains Python and a yt-dlp payload. **GPL-3.0 applies** to the wrapper (https://github.com/yausername/youtubedl-android). A legal/license review of distributing a combined APK and corresponding source is mandatory before publishing. No code, trademark or proprietary UI was copied from third-party apps. For a Play build, content restrictions and platform policy must be independently qualified. YouTube API policies forbid storing audiovisual YouTube content absent prior approval (https://developers.google.com/youtube/terms/developer-policies).

## Runtime flow

```
Shared URL / typed link
 -> MediaViewModel (validation + genuine analysis)
 -> MediaEngineRouter
      -> NativeDirectMediaRuntime [public direct/HTML5]
      -> PackagedMediaRuntime [yt-dlp extractor]
 -> MediaCatalog choices (from genuine inspected formats)
 -> persisted request / WorkManager
 -> reinspection and exact format fingerprint verification
 -> owned temporary folder / tracked transfer + FFmpeg if needed
 -> container & Android MediaExtractor track validation
 -> atomic output store publish / Files + Activity
```

Extended runtime uses the APK resource only (no updater). A guarded stdlib urllib handler rejects unsafe schemes, invalid ports, local/private literal addresses and HTTPS downgrades, and drops sensitive request headers on cross-origin redirects. It is **NOT a full DNS rebinding sandbox** and does not cover every yt-dlp transport; API/network security review is required before untrusted shared links can be offered in a public build. No WebView cookies or custom header editor is exposed or exported to the extractor. Authenticated downloads requiring authorization are unsupported until a credential-safe, consent-based system is added. The handler will show an error instead of bypassing access rules.

## Format and source matrix

| Source type | Implemented in this source | Independently verified |
| --- | --- | --- |
| Direct HTTPS media and files | Existing OkHttp engine preserved | Existing host local HTTP loopback tests only |
| Static HTML5 / OpenGraph / JSON-LD media | Existing passive resolver preserved | Parser / source-routing host tests only |
| yt-dlp listed websites (YouTube, Instagram, TikTok, Facebook, LinkedIn, Vimeo, Reddit, etc.) | Opt-in Python/yt-dlp adapter and exact format path wired | **NOT VERIFIED** for any named provider |
| HLS/DASH and separate video/audio muxing | Opt-in yt-dlp/FFmpeg command generation wired | Host FFmpeg fixtures only; Android execution **NOT VERIFIED** |
| YouTube EJS challenge runtime | QuickJS adapter path declared | EJS assets / version compatibility **NOT VERIFIED** |
| DRM, private content, forbidden downloads | Not bypassed | Unsupported by design |
| Authenticated provider flows | No browser/cookie import; no session export | Not implemented / **NOT VERIFIED** |

## Mandatory acceptance gates before release

1. Build and install both APKs on Android 26 and Android 36 (arm64, 16 KB page mode), run lint and instrumentation.
2. Inspect all ELF segments for **16 KB** page alignment and native installation compatibility. Older wrapper packaging may fail this requirement; don't ship it by assumption.
3. Measure real per-ABI APK and Play AAB sizes; extended APK may exceed 60 MB. Preserve the base APK's size policy without pretending the universal APK meets it.
4. Run authorized live examples per provider and record URL category, source permission, app version, extracted formats, actual transferred bytes, media track quality, playback, and failures. An extractor listing is not a test.
5. Validate no unbounded metadata output, no credential leakage, no unknown executable code update, no malicious redirect to LAN or privilege-bearing origin, and no partial result publication.
6. Verify WorkManager cancellation, background quota behavior, process death, Wi-Fi changes, and storage exhaustion on physical devices.
7. Legal review for GPL-3.0 obligations and provider/Google Play terms; public release may need a different distribution channel or alternative engine.

**NO-GO:** native Gradle build, APK size, EJS viability and live providers have not been verified in the current environment. Never describe this as 'downloads everything'.

### Local verification evidence (2026-10-18)

- `LANG=C.UTF-8 LC_ALL=C.UTF-8 bash scripts/verify-host.sh` exited **0**: 144 Kotlin host checks and all configured static/Python/shell tests, including seven extended-media contract checks and four Python URL network-guard checks.
- The local FFmpeg test script's saved output reached **`RESULT passed=149 failed=0`**, including actual local mux, MP3, M4A and Opus fixtures. The outer execution tool timed out before it observed the whole command's return code; shell exit status for that separate long run is therefore **unobserved**, not claimed as exit 0.
- `./gradlew -PextendedMedia=true :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline` failed **before compilation**: required Gradle 8.14 was not locally cached and downloading it returned `UnknownHostException: services.gradle.org`. No Android APK, device test, native alignment measurement, or live provider evidence was produced.
