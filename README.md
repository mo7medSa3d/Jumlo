> **2026-10-18 Color Designer UI/UX source refresh:** Applied a coherent light/dark Compose palette, consistent surfaces and shapes, responsive Home/Tools discovery grids, clearer Files and Downloads calls to action, contextual download Pause/Resume, accessible dock labels and bilingual actionable messages. Android/iOS device verification remains **NOT EXECUTED**; this is a source candidate, **NOT** a Play/App Store release. See [UX refresh notes](docs/UX_COLOR_DESIGNER_REFRESH_2026-10-18.md).

> **18 October 2026 large-document source update:** Removed artificial 4 GiB PDF/import and aggregate workflow ceilings, reused verified private source snapshots, enabled disk-spillable PDFBox scratch, streamed >512 split/raster results into a verified ZIP, and made PDF text comparison/redaction memory-efficient. `scripts/verify-host.sh` includes new host tests, but Android compile/device/real multi-GB PDF verification remain **NOT EXECUTED**. Archive-extraction anti-bomb limits and physical disk/RAM safeguards remain by design. This is not a release approval; see `AUDIT.md`.

> **2026-10-18 latest flagship reliability source (NO-GO):** Continued directly from the delivered commercial ZIP. Fixed unsafe resumed HTTP transfer completion, added streaming ZIP CRC verification, finite extraction limits, strict PDF page-order parsing, visual multi-part split markers, corrected every-page split, share-text multi-link parsing and safer routing of unknown video webpages; removed an artificial launch delay. **165/165 Kotlin host tests passed; Android build blocked before compile by an uncached Gradle 8.14 distribution and DNS**. No live media-platform, Android-device or Play purchase qualification. See `AUDIT.md` for precise evidence and remaining blockers. This note supersedes older test counts below.

> **2026-10-18 commercial workflow follow-up (latest delivered source):** Added real batch handoff for saved outputs and imported files (ZIP / PDF merge / images-to-PDF), validated non-destructive result reimport, local Favorites with Home shortcuts, and 32-byte file-signature MIME recovery for SAF providers. `scripts/verify-host.sh` exit **0** (149 Kotlin host and all configured Python/static/shell checks). Android APK compilation is **BLOCKED** by missing Gradle 8.14 and `services.gradle.org` DNS; on-device tests, Play Billing and paid-product entitlement verification are **NOT IMPLEMENTED / NOT VERIFIED**. **Not sale-ready.** See [docs/COMMERCIAL_READINESS.md](docs/COMMERCIAL_READINESS.md).

> **Optional universal downloader source (2026-10-18):** `-PextendedMedia=true` enables an APK-bundled yt-dlp/FFmpeg adapter for wider public-site extraction. Default builds remain lightweight. In GitHub Actions, use `extended_media: true` on a manual Android CI & APK run to request a separately verified extended APK artifact. This adapter is **NOT Android-build-, provider-, or Google Play-qualified**; the local Gradle attempt was blocked by unavailable Gradle 8.14 distribution. See [UNIVERSAL_ENGINE.md](docs/media/UNIVERSAL_ENGINE.md) for commands, GPL-3.0 license obligations, package size and 16 KB compatibility gates. No provider is guaranteed.

> **2026-10-18 downloader follow-up — source-only:** Removed in-app browser/cookie/HTTP-header editor; automatic bounded mobile/desktop request profiles; OpenGraph and JSON-LD public media candidates verified against actual source responses; compact Home, Files and Downloads layouts. **141 Kotlin host tests + 11 media + 4 UI static checks passed**, Android compilation and live provider downloads NOT EXECUTED. Instagram, TikTok, YouTube, Facebook, LinkedIn webpage extractors are **not** fully supported by this runtime. See `verification/current-session/2026-10-18/REPORT.md`.

> **2026-10-18 updated source candidate (not a release):** Adds bounded static HTML5 media-file discovery (not JavaScript/social provider extraction), PDF visual page editing, automatic image-to-PDF geometry, safer PDF compression, and improved Activity/PDF preview failure states. **Host suite: 135 Kotlin + 6 media + 10 SQLite + 14 consistency + 12 UI wiring + 14 brand checks + 4 shell checks passed. Android Gradle, APK size, device/UI and live-source verification are not executed.** Read `verification/current-session/2026-10-18/REPORT.md` before release. Historical passages below refer to earlier revisions.

# Jumlo

## Current 60 MB APK size policy

Android CI enforces **60,000,000 bytes (60 MB)** for the installable APK,
not the GitHub artifact ZIP. Python, yt-dlp, FFmpeg and inactive bundled OCR
models are excluded. Existing PDF, image, file and direct HTTP(S) media
downloads remain part of the lightweight, on-device app. Social-media page
extraction and transcoding to MP3 are no longer advertised as built in.
See [docs/APP_SIZE_POLICY.md](docs/APP_SIZE_POLICY.md). Earlier sections below
describe older revisions and superseded architectures.


## Jumlo 2026 motion redesign, phases 0–6 (2026-10-17)

The **Jumlo** name is used consistently throughout the user interface and resources. Its new abstract PDF/download icon is a neutral symbol without lettering. This native Android/Kotlin source candidate has a Jumlo launcher/visual brand, adaptive/themed SVG-derived icons, Android 12+ launch styling, exported iOS icon sources (NO iOS application), a five-tab animated dock, spring press/entrance/route motion, Android predictive-back gesture feedback, original small UI audio cues, per-setting haptic intensity/sounds persistence, and a grid-first Files screen. See [Jumlo implementation audit](docs/JUMLO_2026_MOTION_BRAND_QA.md).

**Release qualification remains NO-GO:** host-level tests, source checks, and assets were executed and packaged, but Android Gradle assembly, real/emulated device verification, live downloads and frame-time profiling were not possible in this environment; no SDK/Gradle distribution/device was available. The source keeps the original `com.jumlo.app` application ID, Room database and legacy `jumlo://workspace` link for installed-user compatibility.

## Previous source revision — 2026 redesign, phases 0–5 (2026-10-17)

The updated **Android source candidate** includes isolated tool sessions, a default on-device yt-dlp/FFmpeg media flow, automatic naming, public categorized output via MediaStore/SAF, a five-tab floating dock and redesigned pages/settings. Host verification is passing, but **Android compilation and live device/platform media playback remain UNVERIFIED; this is not a production-ready APK**. The supplied ZIP contains no iOS implementation. See [phase report, actual test evidence and remaining gaps](verification/2026-redesign/PHASES_0_TO_5.md) and [original root-cause diagnosis](verification/2026-redesign/PHASE0_DIAGNOSIS.md). The historical reports and implementation notes below describe earlier ZIP revisions; where they disagree, the new report takes precedence.

Normal builds now **include** the embedded media runtime; opt out with `-PextendedMedia=false` to build the lite stub. A fresh build must be validated on a networked Android toolchain.

---

## Continued execution on the hardened source ZIP (2026-10-17)

This is a **source-only, NO-GO engineering handoff**. In a separate working copy of
`Jumlo_Updated_Source_Hardened_2026-10-17.zip`, the actual current code passed
**117/117 Kotlin host**, **8/8 media-guard**, **10/10 SQLite**, **14/14 source/locale/font**,
**6/6 filesystem-cleanup**, **11/11 Home/fold policy**, **5/5 synthetic host FFmpeg**,
and **5/5 release-media source-isolation** checks. The host Kotlin compiler was 1.9.0,
not the CI-pinned 2.0.21. Tests do **not** establish Android compilation or runtime success.

Android Gradle Debug, Android unit tests, lint, R8, instrumented API 26/36,
screenshots, real file readers, and all eleven live platform/API qualifications are
**UNVERIFIED**: the environment has no Android SDK or cached Gradle 8.14 distribution;
the attempted wrapper launch failed with `UnknownHostException: services.gradle.org`.

Further changes include corrected bottom-left PDF crop coordinates, bounded image-to-PDF
bitmap residency, AES-256 ZIP header enforcement and fail-safe staging, strict official
Drive link parsing and create-new-only transfers, removal of signed URLs from legacy
Room records/new DAO writes, explicit media-rights consent, and disabling runtime
un-pinned yt-dlp updates. Extended media, Drive, OCR, scan, PDF redact/protect,
encrypted ZIP, and webpage capture remain security-gated. No user-owned keystore is
included. See [current follow-up evidence](verification/current-session/followup-2026-10-17/REPORT.md)
and [feature status](docs/FEATURE_STATUS.md).

---

## Current isolated archive execution — 2026-10-17

**This updated archive is not release-ready.** The current session reproduced 110/110 host Kotlin core tests (after the diagnostic log fix), 8/8 media network-guard, 9/9 real SQLite-fixture, 12/12 source/static, 6/6 workspace-cleanup, 11/11 home/fold geometry and 5/5 local FFmpeg fixture tests. `Gradle` could not run because this container has no Android SDK or cached wrapper distribution and outbound DNS is unavailable. These are scoped local results, **not** an app/device verification. Gradle debug, unit, lint, R8 release and device tests are **UNVERIFIED for this archive**.

This follow-up improves encrypted ZIP extraction safety without enabling the security-gated feature; bounds temporary source copying; keeps the optional yt-dlp/FFmpeg payload strictly out of release variants even with `-PextendedMedia=true`; and corrects rotated error-log export/clear semantics. Read [docs/FEATURE_STATUS.md](docs/FEATURE_STATUS.md), [docs/MEDIA_CAPABILITY_MATRIX.md](docs/MEDIA_CAPABILITY_MATRIX.md), [docs/RELEASE.md](docs/RELEASE.md) and [CHANGELOG.md](CHANGELOG.md). The original source archive and historical verification evidence were retained, but prior build PASS statements are not evidence of these changes passing the Android toolchain.

---

[الدليل العربي](README_AR.md)

Native Kotlin / Jetpack Compose utility for local PDFs, images, files and direct downloads. Package `com.jumlo.app`; minimum API 26, compile/target API 36. This source preserves the existing Hilt, Room, WorkManager and processing architecture.

## This delivery

The supplied production plan has been applied through a repaired native build configuration, consistent capability policy, quieter feature screens, local Account & Data, grouped Settings, recoverable workspace state, bounded processing and safer file/trash operations. Home actions are locally customizable, layouts avoid separating/occluding fold bounds, and rename previews use the real naming policy before submission. The public Tools catalog has 33 entries (32 processing tools plus the PDF viewer) with live counts. Security-sensitive and unqualified features are hidden and rejected at execution boundaries.

**This is an implementation handoff, not approval to publish.** Read [docs/RELEASE.md](docs/RELEASE.md) for observed build/test outcomes and outstanding hardware, historical migration, signing and CI gates. Authored tests are distinguished from executed tests.

## Open and build

Use JDK 17, Android SDK platform 36 and Build Tools 36.0.0. Open the root in Android Studio, or configure `ANDROID_HOME` and use the checked-in Gradle wrapper. Normal builds exclude extended-media binaries. No private signing key is included.

```bash
bash scripts/restore-wrapper.sh --check
./gradlew :app:assembleDebug --no-daemon --console=plain
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
./gradlew :app:lintDebug --no-daemon --console=plain
./gradlew :app:assembleRelease --no-daemon --console=plain
```

Debug uses Android's standard debug signing. Release is unsigned unless the developer supplies their excluded `keystore.properties`; see the template and RELEASE. The included `:benchmark` module targets a profileable optimized app variant with debug signing. No unmeasured handwritten ART rules are packaged; the active profile files contain comments only.

## Verify

```bash
# Kotlin/JDK, Python; corresponding media tests also require FFmpeg:
bash scripts/verify-host.sh
bash scripts/test-workspace.sh
python3 scripts/test-media-ffmpeg.py
python3 scripts/generate-feature-matrix.py

# Individual logged native gates:
bash scripts/verify-native.sh gate
# With a connected emulator/device:
bash scripts/verify-native.sh instrumentation
# On designated physical benchmark hardware:
bash scripts/verify-native.sh benchmark
bash scripts/verify-native.sh baseline
```

Host HTTP fixtures use a local loopback server. Android instrumentation must run on API 26 and 36; emulator testing does not supply physical-device performance evidence. CI runs compilation/unit/lint/release before its emulator matrix and preserves per-SHA logs/artifacts.

## Data contract

Inputs are copied into app-owned snapshots and jobs publish verified new outputs. Managed-trash purge confirms an exact set of IDs. Cache cleanup is restricted to expired reproducible seek/extraction cache. Saved drafts exclude passwords, tokens and raw direct URLs; these need re-entry after process death. Downloads connect to endpoints explicitly entered by the user. There is no invented Jumlo identity, subscription tier or analytics backend.

## Handoff map

| Document | Purpose |
|---|---|
| [AUDIT.md](AUDIT.md) | Findings, patches, evidence and release criteria |
| [Feature qualification](docs/FEATURE_QUALIFICATION_MATRIX.md) | Executable policy/handler inventory |
| [Screen and action matrix](docs/SCREEN_AND_ACTION_MATRIX.md) | Controls, persistence, errors and back paths |
| [Design](docs/DESIGN.md) | Components, tokens, typography and visual-QA limits |
| [Performance](docs/PERFORMANCE_REPORT.md) | Reproducible methodology and actual evidence boundaries |
| [Release](docs/RELEASE.md) | Individual native outcomes, signing and release blockers |
| [Privacy](docs/PRIVACY.md) / [Security](docs/SECURITY_MODEL.md) | Stored data, permissions and threat boundaries |
| [Schema provenance](app/schemas/schema-provenance.md) | Current schema and authentic historical-v1 evidence gap |
| [Dependency notices](docs/DEPENDENCY_NOTICES.md) | Licenses, fonts and optional media restrictions |
| `verification/production-2026-10-17/` | Current raw evidence, reports and archive provenance |

The approved plan is in `docs/approved/2026-10-17-production-execution-plan.md`. Earlier supplied documents are preserved as historical material; current claims come from the verification of this delivery.
