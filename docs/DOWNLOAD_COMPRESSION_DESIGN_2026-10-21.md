# Jumlo — downloader, compression, tool removal and visual redesign

Date: 2026-10-21 · Android (Kotlin/Compose) · The iOS target does not exist in this
repository: there is no Swift source, no Xcode project and no `Package.swift`, so
everything below was verified on the Android build. The iOS brand assets (a full-bleed
opaque app-icon set) are present and correct; the app itself would have to be written
for iOS from scratch.

---

## 1. Root causes

### 1.1 The video downloader failed for every platform link

**Root cause: the shipping APK contains no extractor at all, but the tool is still
offered.**

The build splits the media runtime into two mutually exclusive source sets:

```kotlin
sourceSets["debug"].java.srcDir(if (extendedMedia) "src/media/java" else "src/base/java")
```

* `src/base/…/MediaRuntimeFactory.kt` builds **only** `NativeDirectMediaRuntime` — a
  hand-written fetcher that reads a page, finds static HTML5 `<video>`/`<audio>`
  sources and downloads them.
* `src/media/…/MediaRuntimeFactory.kt` additionally builds `PackagedMediaRuntime`
  (yt-dlp + FFmpeg + a Python runtime) — but only in the `-PextendedMedia=true` build,
  which is not the store build.

A YouTube, TikTok, Facebook or Instagram watch page exposes no static media URL: the
player is script-driven. So `NativeDirectMediaRuntime.inspect` falls through
`inspectDirect` → `readPage` → `Html5MediaDiscovery` and ends at
`MEDIA_SITE_UNSUPPORTED` — *"No downloadable video or audio resource was found."* — for
**every** platform link, while the tool stayed visible in the catalogue.

**Fix.** The extractor now lives in a service, and the app keeps the download:

* `tools/media-service/service.py` — a reference service that wraps a maintained
  extractor (yt-dlp), exposes `POST /extract`, `GET /stream` (byte-range aware) and
  `GET /health`. It can update its own extractor (`pip install -U yt-dlp`) **without an
  app release**.
* `RemoteExtractorRuntime` (app side) — posts the URL, reads the formats the extractor
  offers, and then pulls the chosen stream through the ordinary transfer machinery, so
  progress, pause, resume, cancel, retry and the on-device save folder all keep working.
* `RoutingMediaRuntime` — tries the service first, then the bundled/on-device engines.
  A failure that means "this site is not supported here" falls through; a private
  video, a rate limit or a network error stays failed, because retrying it elsewhere
  would be trying to get around an access control.
* A **Downloader service** setting (Settings → Downloads) with an optional bearer token.
  With no service configured the app offers direct media links only and says so.
* `GET /health` gives the app the service's extractor version, so "update the
  extractor" is a button rather than an app release.

The service never decrypts anything, never logs in to a private account and never
works around a rate limit: when the site needs a session the service reports
`MEDIA_SESSION_REQUIRED` and the app shows a plain message.

### 1.2 Compression "did not work"

**Root cause: the engine was honest, the interface was silent.**

Measured on a real 721 710-byte scanned PDF (`PdfBoxBackend.compressImages`):

| Profile | Output | Saved | Pages |
|---|---|---|---|
| 96 DPI / q55 | 588 805 B | **18.4 %** | 2 (unchanged) |
| 150 DPI / q70 | 634 967 B | 12.0 % | 2 (unchanged) |
| 300 DPI / q88 | 721 710 B | 0 % | 2 (unchanged) |
| text-only PDF | unchanged | 0 % | 1 (unchanged) |

The engine works, but: the default profile (180 DPI) yields nothing on a document whose
images are already below 180 DPI; there were no named presets; and when nothing could be
reduced the tool reported `PDF_NOT_COMPRESSIBLE` with no before/after in the flow.

**Fix.**
* Three named presets — **High compression / Medium compression / Low compression** —
  mapped to concrete (DPI, quality) pairs, on both PDF and image compression.
* The **size/quality slider** with a live estimate and the estimated saving percentage,
  computed from the real embedded-image bytes of the source (`embeddedImageSummary`).
* The before/after sizes and the saving percentage were already reported on the output
  row; the "nothing could be reduced" case keeps its honest message.

### 1.3 The design

* **Glassmorphism:** the tool tile drew a vertical gradient over a translucent
  container and a translucent border, and the hero card a three-stop gradient.
* **One colour for everything:** tiles were coloured by *category*, so 45 tools shared
  five hues and the grid read as one undifferentiated list.
* **The dock:** a floating pill with a heavy shadow, a translucent active pill, five
  slots that included both *Home* and *Tools* (the same screen to the user) and no
  Downloads at all.

---

## 2. What changed

### 2.1 Downloader

| File | Change |
|---|---|
| `tools/media-service/service.py` | **New.** The reference extraction service: `/extract`, `/stream` (Range-aware), `/health`. Maps upstream failures onto the app's error vocabulary. |
| `app/src/main/java/com/jumlo/app/data/media/RemoteExtractorRuntime.kt` | **New.** The app half: post the URL, parse the formats, download the chosen stream through `StreamTransfer`. |
| `…/data/media/RemoteExtractorRuntime.kt` (`RoutingMediaRuntime`) | **New/moved.** Engine order, safe fall-through, and "which engine answered" for the download. |
| `app/src/base/…/MediaRuntimeFactory.kt` | Rewritten: the service first, then the on-device direct fetcher. |
| `app/src/media/…/MediaRuntimeFactory.kt` | Rewritten: service → bundled yt-dlp → on-device direct. |
| `data/preferences/UserPreferencesRepository.kt` | The service address and token, with a live snapshot so a change applies without a restart. |
| `ui/settings/SettingsScreen.kt`, `ui/AppViewModel.kt` | The **Downloader service** setting. |
| `ui/components/UserMessages.kt` | `MEDIA_SERVICE_NOT_CONFIGURED` → a plain message. |
| `ui/downloads/MediaViewModel.kt`, `ui/downloads/MediaWorkspace.kt` | The **terms of use are now actually shown.** The app had the strings `media_rights_confirmation` and `media_rights_required` but never displayed them, so the promise it made to a store reviewer was not kept. A media download is now held until the user confirms they have the rights, and the message states that Jumlo does not bypass DRM, paywalls or access controls. |
| `app/src/test/…/media/RemoteExtractorRuntimeTest.kt` | **New.** Six tests against an in-process service: formats parsed, errors mapped, unconfigured service reported, the stream downloaded to the device, the extractor version read, an absent service reported. |
| `app/src/test/…/media/RemoteExtractorServiceLiveTest.kt` | **New.** The same loop against a **real** service; skipped unless `JUMLO_MEDIA_SERVICE` is set. |

### 2.2 Compression

`core/PdfPageGeometry.kt` — the presets became
`HIGH_COMPRESSION / MEDIUM_COMPRESSION / LOW_COMPRESSION` (the old names still parse),
plus `sliderSteps()`, `sliderStep()`, `sliderPosition()` and `estimateBytes()`.
`data/pdf/PdfBoxBackend.kt` — `embeddedImageSummary()` reports the bytes and pixels of
the embedded images. `ui/workspace/WorkspaceScreen.kt` — the presets and the slider with
the live estimate and the saving percentage.

### 2.3 Removed tools

**"Compare PDF"** (`pdf_compare`) — removed completely: the registry entry, the policy
entry, the dispatcher mapping, `PdfCompareSettings`, the handler branch, the workspace
branch, `PdfBoxBackend.compareText()` / `compare()` / `CompareResult` / `CompareVerdict`,
the strings and two tests.

**"تنقيح PDF"** (`pdf_redact`, *Redact PDF*) — identified as the **permanent content
removal** tool: it removes text-showing operands, fully covered images and annotations
from a region and re-opens its own output to prove the text is gone. Removed
completely: the registry and policy entries, the dispatcher mapping, `PdfRedactSettings`,
the handler branch, the workspace and visual-editor branches, `PdfBoxBackend.redact()`,
`RedactRegion`, `RedactResult`, the whole `ContentRedactor` engine and its `Geo`
helpers, the strings and six tests.

> **A note on this removal, because it is worth a decision.** The redaction tool was
> built and *qualified* earlier this week: `PdfSecurityQualificationTest` proved the
> redacted word is physically absent from the saved bytes, that a text page needs no
> raster fallback, and that untouched pages keep their text. The brief that introduced
> it (2026-10-20) lists redaction as a required professional tool, and Acrobat, Foxit
> and every other serious PDF app ship it as a compliance feature. It has been removed
> as instructed; restoring it is a self-contained change (the registry/policy/dispatcher
> entries, the handler branch, the backend, and the six qualification tests).

**What remains: 45 registry entries** (44 tools + the SHA-256 utility) — merge, split,
compress, rotate/reorder/delete/extract, images→PDF, PDF→images, crop, annotate,
watermark, sign, image overlay, metadata edit, **metadata clean**, password
protect/unlock, forms, OCR (gated), scan, webpage→PDF, nine image tools, media
download, direct download, batch download, download settings, browse storage, ZIP
create/extract/encrypted, batch rename, duplicate finder, trash, Drive import, the
private vault (gated).

### 2.4 Visual style and navigation

| Change | Detail |
|---|---|
| `ui/theme/ToolPalette.kt` | **New.** A twelve-hue palette; every tool takes its own hue from its id, so the colour is stable and the grid can be scanned. |
| `ui/components/NeonUi.kt` | The tool tile is now a **flat** surface with a solid tinted container and a one-hue icon — no gradient, no translucent border. The hero card is flat too. |
| `ui/components/QuietNavigation.kt` | The dock is a flat bar with a top divider instead of a floating glass pill; the destinations are **Tools, Downloads, Files, Settings** plus the scan/print action in the middle (Home and Tools were the same screen; Downloads was missing). The active item is a filled icon in the primary colour with a stronger label, the inactive ones are outlined and muted. |
| Tap targets | The tool tile grew from 156 dp to 164 dp (128 dp compact) and the dock destinations keep a 52 dp minimum with a 48 dp+ icon. |

---

## 3. Test results

### 3.1 Downloader — platform matrix (real service, this machine)

Every row was produced by running the reference service against a **public** URL with the
installed extractor (yt-dlp 2026.08.19, ffmpeg present). No login, no cookie, no DRM
and no rate-limit workaround was used.

| Platform | URL | Result | Detail |
|---|---|---|---|
| YouTube | `youtube.com/watch?v=aqz-KE-bpKQ` | **OK** | extractor `Youtube`, 53 formats, best 2160p vp9 |
| TikTok | `tiktok.com/@scout2015/video/6718335390845095173` | **OK** | extractor `TikTok`, 11 formats, 720p h265, 2 004 627 B |
| Facebook | `facebook.com/facebook/videos/10153231379946729/` | **OK** | extractor `Facebook`, 2 progressive mp4 formats |
| SoundCloud | `soundcloud.com/uiceheidd/lucid-dreams-forget-me` | **OK** | extractor `Soundcloud`, 2 audio formats |
| VK | `vk.com/video-1_456239018` | **OK** | extractor `VK`, 19 formats, best 720p avc1 |
| Instagram | `instagram.com/reel/C4wPTKAp4oS/` | FAIL | `MEDIA_SESSION_REQUIRED` — Instagram requires an account session from a server IP |
| Reddit | `reddit.com/r/aww/comments/ckp1q2/` | FAIL | `MEDIA_SESSION_REQUIRED` — Reddit's own extractor now requires an account |
| Vimeo | `vimeo.com/76979871` | FAIL | `MEDIA_SESSION_REQUIRED` — the Vimeo web client needs a login or a client token |
| X | `x.com/Interior/status/1302475321909909376` | FAIL | `MEDIA_EXTRACTION_FAILED` — that post carries no video; a second video URL was rejected with `MEDIA_SOURCE_REJECTED` (403 from a data-centre IP) |
| Direct MP4 | `download.samplelib.com/mp4/sample-5s.mp4` | **OK** | 1 progressive format; downloaded 2 848 208 B, `206 Partial Content` on resume |
| Direct MP3 | `download.samplelib.com/mp3/sample-3s.mp3` | **OK** | 1 audio format |

**Five platforms and both direct-link types work.** The four failures are the upstream
sites' own access controls, reported as a human message — not crashes and not silent
failures. They are the sites where the app's existing cookie store lets a user supply
their **own** session, which is the legitimate route.

**End-to-end through the app client** (the live test): the app posted a direct MP4 URL
to the running service, read the format list, and wrote the file to the "device"
directory — 2 of 2 live tests passed.

**Not verified:** the same platforms on iOS — there is no iOS client. On Android the
tests are JVM-level against a real service; the on-device download path is the same
`StreamTransfer` the rest of the app uses, but it has not been exercised on hardware
because no device or emulator is available here.

### 3.2 Compression

| Input | Profile | Before | After | Saved | Pages | Text intact |
|---|---|---|---|---|---|---|
| scanned PDF (image + text page) | 96 DPI / q55 | 721 710 B | 588 805 B | 18.4 % | 2 → 2 | yes |
| scanned PDF | 150 DPI / q70 | 721 710 B | 634 967 B | 12.0 % | 2 → 2 | yes |
| scanned PDF | 300 DPI / q88 | 721 710 B | 721 710 B | 0 % | 2 → 2 | yes |
| text-only PDF | 96 DPI / q55 | 841 B | 841 B | 0 % (reported, not faked) | 1 → 1 | yes |

The output is re-opened and the page count and the extracted text are asserted, so a
"compressed" file that lost a page or the text can never be reported as a success.

### 3.3 Whole-project verification

| Suite | Result |
|---|---|
| JVM unit tests | **311 passed, 0 failures** (the six new downloader-client tests included) |
| Host Kotlin suite | **181 passed, 0 failures** (one case went with the comparison tool) |
| Static architecture checks | 19 suites green. Four of them encoded the *old* design and were updated to the new one: the media-engine fallback now names the new router, the tool-tile check now expects a per-tool hue instead of a per-category one, the dock check now expects the four new destinations, and the page-text check now names the bounded search that survived the comparison tool. |
| Terms of use | The confirmation is now wired into the download flow, not just present as a string. |
| Lint | 0 errors |
| Builds | debug, release, benchmark and the instrumented-test source set all build |
| `-PextendedMedia=true` build | compiles (the bundled yt-dlp variant) |
| Artifact gate | PASS — no forbidden media markers, ELF64 alignment ≥ 16 KB |

---

## 4. Removed files and components

| Removed | Where |
|---|---|
| `PdfCompareSettings` | `data/processing/Settings.kt` |
| `PdfBoxBackend.compareText()`, `compare()`, `CompareResult`, `CompareVerdict` | `data/pdf/PdfBoxBackend.kt` |
| The `pdf_compare` handler branch | `data/processing/PdfOperationHandler.kt` |
| The `pdf_redact` handler branch | `data/processing/PdfOperationHandler.kt` |
| `PdfRedactSettings`, `PdfRedactResult`, `RedactRegion` | `data/processing/Settings.kt`, `data/pdf/PdfBoxBackend.kt` |
| `PdfBoxBackend.redact()`, `stripAnnotations()`, `leftoverAfterRedact()`, `ContentRedactor`, the `Geo` helpers, `replaceContent()` | `data/pdf/PdfBoxBackend.kt` |
| The `pdf_redact` branches | `ui/workspace/VisualDocumentEditor.kt`, `ui/workspace/WorkspaceScreen.kt`, `ui/workspace/VisualEditGeometry.kt` |
| The registry, policy and dispatcher entries for both tools | `data/jobs/CapabilityRegistry.kt`, `core/CapabilityPolicy.kt`, `data/jobs/OperationDispatcher.kt` |
| The redaction and comparison strings | `res/values/strings.xml`, `values-ar/`, `visual_editor.xml`, `error_messages.xml` |
| The comparison and redaction tests | `app/src/test/…/WorkspaceSettingsTest.kt`, `PdfStructureCoreTest.kt`, `PdfSecurityQualificationTest.kt` |

No file was deleted; the removals are inside the files above.

---

## 5. Screenshots

**Not produced, and not faked.** There is no emulator, no device and no iOS simulator
in this environment, so no screenshot of the new navigation or tool grid could be
captured. The visual changes are described above and are implemented in
`ui/theme/ToolPalette.kt`, `ui/components/NeonUi.kt` and `ui/components/QuietNavigation.kt`;
they need one run on a device to be seen.

---

## 6. Remaining risks

| Risk | Detail | How it is addressed |
|---|---|---|
| **Store policy on the downloader** | A downloader that can pull media from video platforms is restricted by the Play and App Store policies. | The extractor is **not in the app**: it is a service, and the app only downloads from an address the user configures. The standard build ships no media runtime at all. A terms-of-use screen is still needed before the service is offered in a store build; the direct-APK route is the fallback. |
| **No iOS client** | The brief asks for Android and iOS; the repository has no Swift source and the machine has no Swift toolchain. | The iOS app-icon assets are correct and opaque; the architecture above is platform-neutral, but the iOS client has to be written. Nothing here was verified on iOS. |
| **No device** | No emulator, no device, no simulator. | Every claim is JVM-level or service-level. Install, camera capture, the real print spooler and the on-device download have not been observed. The instrumented tests compile but have not run. |
| **Platform extractors need sessions** | Instagram, Reddit, Vimeo and X refuse a server without a session. | Reported as `MEDIA_SESSION_REQUIRED` with a human message. The app already has a cookie store; a user can supply their own session, which is the legitimate route. No bypass is implemented. |
| **The service is a single point of failure** | If it is down, platform links fail. | The router falls through to the on-device and bundled engines, and a plain message explains the state. |
| **A live extraction service needs hardening** | The reference service is a development-grade service. | It is shipped as a *reference*, with a bearer token and an explicit allow-list of what it will do. A production deployment needs TLS, rate limiting, authentication and logging that does not record URLs. |
| **Compression is conservative by design** | A document whose images are already small, or that is pure text, cannot be made smaller without losing quality. | The tool reports the real sizes and says why nothing changed; it never claims a saving it did not make. |
