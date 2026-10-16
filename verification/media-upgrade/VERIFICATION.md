# Universal Utilities - media downloader source and verification

Date: October 16, 2026. Status: **modified Android source candidate; NOT Android-build-verified, NOT device-verified, NOT a release.**

## What was delivered

This extension adds real processing source, not a planning-only handoff. It preserves the previous source/job/output architecture and the PDF/image/file candidates. The original 41-task product is still unfinished: **17 partial/unqualified and 24 not implemented/gated; zero fully qualified tasks**. The changed E20 status reflects browser-source implementation, not completion of webpage-to-PDF capture.

The new `dl_media` path has typed source metadata; actual quality, FPS, codec, language and size choices; exact video/audio format selection; source-codec audio, MP3, M4A and Opus options; a bounded process supervisor; a pinned on-device runtime adapter; a native Compose workspace and optional WebView; existing Room/WorkManager integration; and actual output signature/track checks before publication. The APK runtime is **source-integrated but has not been compiled, initialized or exercised here**.

All 46 new media resource keys have English and Arabic values. This is not complete localization or visual qualification of the whole app.

## Executed checks

Command: `bash scripts/verify-host.sh --with-media`

The complete command exited **0**. The raw full log is `json-guard-green-full.log`; its exit marker is `json-guard-green-full.exit`.

| Check | Observed result | Exact scope |
|---|---|---|
| Kotlin core/domain/HTTP/media suite | **97 passed, 0 failed** | Actual production core source, compiled with host kotlinc; includes 11 local HTTP cases, 10 process-control cases and 5 actual FFmpeg/ffprobe media cases |
| Python URL/redirect guard | **8 passed** | Actual production pure guard functions and stdlib request objects; NOT actual bundled yt-dlp transport integration |
| Production DAO/migration SQL | **8 passed** | Exact SQL in host SQLite; NOT Room/KSP/Android qualification |
| Static source checks | **8 passed** | Resource/XML consistency, removal of legacy simulations, gates, FileProvider scope and no fonts; NOT compilation/lint/security certification |
| Offline preflight regressions | **2 passed** | Missing prerequisite behavior, no network bootstrap |
| Launcher safety regressions | **2 passed** | Missing or invalid official JAR does not invoke JVM/download |

The five real-media cases generate local synthetic video/audio **files**, run actual installed FFmpeg through the production process supervisor, then inspect output with ffprobe and the production file-signature code. They check separate-track merge preserving dimensions; MP3 audio-only; AAC in M4A without video; Opus in Ogg; and invalid media leaving no finished output. These are real codec operations, not mocked engines. They do **not** execute the Android runtime adapter or prove any live website works.

Historical RED/GREEN runs are retained. Their counts overlap: do not add reruns together. Final host total is 125 checks across the categories above, not 125 Android tests. Four new metadata-parser Gradle tests were written but NOT executed.

## Native build attempts and environment

Exact command arrays, UTC times, logs and exits are in `final/native-results.json`. All these requests returned **BLOCKED, exit 2**, before Gradle execution:

```sh
bash scripts/verify-local.sh :app:assembleDebug
bash scripts/verify-local.sh -PextendedMedia=true :app:assembleDebug
bash scripts/verify-local.sh -PextendedMedia=true :app:testDebugUnitTest
bash scripts/verify-local.sh -PextendedMedia=true :app:lintDebug
bash scripts/verify-local.sh -PextendedMedia=true :app:connectedDebugAndroidTest
bash gradlew --offline --version
```

Observed runner: OpenJDK 21.0.12.1, kotlinc 1.9.0, Python 3.13.5, FFmpeg/ffprobe 7.1.5. No Gradle, SDK manager or adb on PATH; no configured Android SDK; no official wrapper JAR. The project requires JDK17/Kotlin2.0.21/Gradle8.14/SDK36/BuildTools36.0.0. Host JVM17 bytecode is not proof of the Android compiler/plugin graph. See `final/environment.json`.

No APK was produced. No native dependencies were resolved, transitive binary hashes inspected, Android UI rendered, emulator/device used, or live-platform/authenticated session tested. Native integration defects may remain. This report does not attribute missing feature code to missing tooling.

## Important behavior and limits

Only formats actually reported by a resource are displayed. An exact selection is bound to source identity, codecs, dimensions, FPS and language. The worker re-analyzes before downloading. Changed or missing choices produce an error; there is no automatic downgrade to a different format. Reserved IDs such as `best` are treated as literal format IDs, not selector aliases. MP3/M4A/Opus bitrate is an encoding option, not recovered source quality.

Direct files retain the previous streamed HTTP/resume implementation. Media streams use a separate finite-resource path and restart an owned unpublished workspace on retry rather than appending potentially changed HLS/DASH segments. The process supervisor bounds captured output, checks cancellation without waiting for progress, enforces deadlines, and terminates the process group on failure/cancellation. Native initialization and platform filesystem/foreground semantics remain unqualified.

The browser can analyze a page and capture candidate network media/manifest URLs or file-download requests. No download occurs before user action. It is not universal interception: arbitrary blobs, session-required pages, playlist management, bounded live recording, impersonation-only sites and protected representations are not implemented as universal downloads. Browser cookies are not exported to the extractor. A platform has not been tested just because a WebView can open it.

Signed URLs and capture candidates stay in memory; jobs store opaque handles and hashes, not stream secrets. Process death requires fresh input/analysis. The worker's storage limit is 8 GiB polled every 500 ms with a minimum-free-space check, not a filesystem quota; transient overshoot is possible. Process-stop recovery, complete cleanup/retention, OS quotas and all native codecs/ABIs still require qualification.

## Dependencies and build switch

Full media build: **`-PextendedMedia=true`**. The default base build does not resolve the native media runtime and explicitly disables its card. Debug-only candidate/release gates remain in place.

Declared wrapper/native dependencies are `io.github.junkfood02.youtubedl-android:library:0.18.1` and `ffmpeg:0.18.1`. Upstream labels this release prerelease, and its wrapper is GPL-3.0. Exact packaged Python/yt-dlp/QuickJS/FFmpeg payload versions and licenses have not been independently qualified. See `docs/dependencies.md`; no blanket source relicensing or dependency binaries are supplied here.

Source refreshes the extractor from the installed APK using AtomicFile. That update path was reviewed but NOT executed; it is not counted as a passing native regression. Automatic remote executable/plugin updates are not enabled. The urllib transport adapter uses upstream internals and must be verified against the actual AAR payload.

## Review, decisions and outstanding work

Final review was an **author self-review**, not an independent review. Observed host regressions fixed: selector aliasing, duplicate job identity after uncertain enqueue, and deeply nested metadata resource exhaustion. Native-only review changes are separately unqualified, not hidden inside host test counts.

| Ruling | Reason / consequence |
|---|---|
| New media request supersedes the older media-extractor exclusion | Adds a native subsystem and its own qualification obligations; does not complete unrelated utilities |
| Work in an isolated copy, no remote push or user-machine changes | Deliverable requires integration/build; originals preserved |
| Optional `-PextendedMedia=true` runtime boundary | Avoids silently imposing new dependencies/licensing on the base build; full media build needs the flag and distribution review |
| Restrict media transport to guarded urllib | One enforcement point for schemes, TLS downgrade and redirected credentials; impersonation/alternate-transport sites may fail |
| No silent export of browser login cookies | Keeps browsing credentials out of jobs/artifacts; authenticated extraction remains missing |
| Finite individual resources only | Playlist selection and bounded live recording are not delivered |

Deferred minors: compact control rows need small-screen/large-font visual review; technical codec/byte labels use locale-neutral units. These are in addition to, not a replacement for, the major open implementation/qualification gaps.

Structural PDF edits and advanced PDF features, most image expansions, camera and webpage-to-PDF capture, scheduling/multipart, batch rename, duplicates, managed trash, encrypted archives and vault are still unfinished. The complete task inventory is `../TASK_STATUS.md` and its JSON/CSV counterparts. Nothing in this extension establishes that the full app is production-ready or works on every platform.

## Reproduction and archive verification

From the extracted root, run `bash scripts/verify-host.sh --with-media` with installed Kotlin/JDK, Python, FFmpeg and ffprobe. The script downloads nothing and uses temporary fixtures; it does not mutate packaged source files. Default `verify-host.sh` omits the five FFmpeg cases.

For actual Android qualification, see `docs/media/DEVICE_QUALIFICATION.md`, explicitly marked NOT EXECUTED. Original input hashes, detailed logs and an execution ledger are retained. `SOURCE_SHA256SUMS` covers packaged files except itself. The external checksum and package report identify the delivered ZIP and its clean-extraction test replay. There are no signing keys, font binaries, SDKs, Gradle caches or APKs in this source archive.
