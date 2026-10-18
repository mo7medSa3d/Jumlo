# Jumlo 2026 redesign — phases 0–5 delivery report

**Date:** 2026-10-17. **Input:** user-provided `Jumlo-main (1).zip`, SHA-256 `3ac77fca98cfddb11e84499b769610d63d3663b14ce10122e76b64369a79f6c6`.

**Scope and release status:** Updated **Android source candidate**. The supplied archive has no iOS project or shared iOS target. **NOT VERIFIED ON ANDROID HARDWARE / NOT RELEASE QUALIFIED.** Do not claim these source edits produce a working APK, playable media from any third-party site, sustained 60 fps, or cross-platform parity without building/installing and qualifying them. Historical PASS notes in the source ZIP refer to different revisions.

## Phase 0 — Read, root-cause diagnosis, plan

- **What to do:** trace navigation/session state, direct and extracted media, Files persistence, settings, theming, localization and lifecycle before editing.
- **Done:** inspected the Android source; documented original file-and-line causes in [PHASE0_DIAGNOSIS.md](PHASE0_DIAGNOSIS.md). The old activity-scoped ViewModel retained file IDs, `dl_media` was disabled in standard builds, ordinary HTML page URLs were streamed as files, results lived in private storage, and the folder setting opened Files rather than a picker. Chose the existing embedded yt-dlp/FFmpeg Android implementation rather than add an unprovisioned backend; direct URLs continue to use bounded, resumable HTTP streaming.
- **Verification:** baseline host Kotlin suite 117/117 with UTF-8; read available source, resource and test definitions. Android SDK and cached Gradle 8.14 absent.
- **Next:** Phase 1.

## Phase 1 — Core behavior

- **What to do:** make every tool entry a new empty session; make explicit handoff opt-in; extract media instead of saving HTML; remove filename/format prompts; expose saved files; wire settings.
- **Done:** `AppViewModel` resets source IDs/links on `chooseTool` and clears them on exit; a session epoch explicitly clears the media ViewModel on reentry; explicit `chooseToolWithSources` retains user-requested handoff. A unified downloader probes direct file URLs and extracts recognized media pages using the bundled yt-dlp/FFmpeg engine, chooses an audio-bearing best/balanced preference, and displays metadata when available. HTML payloads are rejected; names use content/disposition/MIME or first-source-derived names with collision-safe publication. Extractor partial fragments now survive pause/retry within the same request. Direct HTTP transfers keep existing resumable WorkManager queue and retry policy. Public mirrors use Android 10+ MediaStore `Download/Jumlo/{Video,Audio,PDF,Images,Documents}`, or an explicitly picked SAF tree (`<chosen>/Jumlo/<category>`), with verified hashes and private durable originals; pre-29 uses app-specific external storage (limitation). Settings stores writable tree grants, download preferences and concurrency, used by the worker admission gate. If public publication fails, Files signals that the result is only in private storage and offers export.
- **Verification:** Kotlin host checks for link classification, MIME naming, HTML rejection, storage paths/collisions, source-derived naming, media argument policy, queue resumability and existing processing regression. Static wiring/resource checks. **No actual social-site or Android transfer execution**; resume after process death may require re-entry because signed download URLs remain in memory-only secret storage.
- **Next:** Phase 2.

## Phase 2 — Foundations and navigation

- **What to do:** strong color/typography, adaptable spacing and a five-tab, RTL/safe-area-aware floating dock.
- **Done:** dynamic curated accent palette and readable dark/light foregrounds, density text scale, existing locally bundled IBM Plex English variable/Arabic families, five DockTab routes including Settings, floating pill dock, removed unused expanded navigation rail, reduced-motion-aware animated dock feedback, all click targets at least 48dp in newly authored Files controls.
- **Verification:** 10 static cross-screen redesign assertions; bilingual resource and XML parity checks. **Contrast measurement, Arabic weight fidelity, screenshot comparisons and runtime window-inset behavior not device-verified.**
- **Next:** Phase 3.

## Phase 3 — Pages and settings

- **What to do:** redesign Home/Tools/Download/Files/Settings without templated stacked cards, use actual data, responsive layouts.
- **Done:** Home hero, real recent outputs/jobs and editable quick actions; Tools grouped colored bento tiles plus recently/most-used lists based on persisted tool-open counters; unified URL → automatic analysis → preview → one Download action with Advanced behind disclosure; Files save-path, open-folder, search, filters, sort, list/grid/gallery view, share/export, multi-select and confirmed delete/Undo/trash; Settings with functional theme, bilingual language, accent, text size, motion, haptics, quality/audio defaults, concurrency, destination picker, file view and storage-by-media-type totals/cleanup. Existing SAF provider folder rename/copy/move controls remain.
- **Verification:** source contracts, persisted-preference call-path inspection, localization tests, Kotlin naming/usage tests. **OutputRecord in-app rename/move not added** (existing provider entries support rename/move where the provider allows it); the full device file manager is not indexed in Files and the iOS Files/Photos integration is absent.
- **Next:** Phase 4.

## Phase 4 — Motion and touch

- **What to do:** predictable spring transitions, restrained tactile feedback, all animations respecting app/system Reduce Motion.
- **Done:** Compose navigation springs, animated dock indicator, system animator-scale ContentObserver, composition-scoped motion/haptic toggles, snap checkbox animation when reduced motion is enabled, static shimmer under reduced motion, avoidance of duplicate tab haptics.
- **Verification:** static call-path checks. **60fps performance and complete animation inventory not measured on hardware.**
- **Next:** Phase 5.

## Phase 5 — Verification and source handoff

- **What to do:** protect source invariants, localization/RTL, original behavior, storage/mime and downloader policies; attempt the native toolchain, package a verifiable ZIP.
- **Done:** tests added for tool recency, direct-link classification, auto naming, HTML detection and path collisions; regression test for actual yt-dlp resume arguments; source wiring suite for ten cross-screen contracts; regeneratable deterministic ZIP manifest with input hash; bilingual additions in `values/` and `values-ar/`. `app/schemas/legacy-v1-structure.sql` and `app/schemas/schema-provenance.md` restore missing structural test fixtures found absent in uploaded archive; they **do not** establish the historical v1 schema fingerprint. All newly added visible strings are localized, aside from the technical `PDF` abbreviation.
- **Verification actually run:** `bash scripts/verify-host.sh`: **123/123 Kotlin/JVM host tests**, **8/8 network/guard tests**, **10/10 SQLite fixture checks**, **14/14 source/locale/font checks**, **10/10 redesign source-wiring checks**, and two shell validation suites passed. `scripts/verify-native.sh gate` **BLOCKED / exit 1**: no cached Gradle 8.14 and `UnknownHostException: services.gradle.org` during wrapper distribution download; Android SDK unavailable. `scripts/verify-host.sh --with-media` **INCOMPLETE / execution timeout after first three FFmpeg integration cases passed**; do not count the rest. No device/emulator, API 26/36 install, signing/R8, screenshots, live network-provider matrix, storage permission tests, or performance profile was executed.
- **Next:** a connected Android 26/36 CI runner must compile debug/release, unit-test, lint, inspect release classpath/ABI and installed APK, run instrumented MediaStore/SAF/back/RTL/theme tests, then test live sources and binary media tracks on real devices. An iOS source project is necessary for an iOS implementation.

## Important remaining gaps and risk register

1. **Android compilation/device tests unverified**; code changes are extensive, so platform compile/runtime errors may remain. No APK is included in this source handoff.
2. **Provider playback unverified:** no proof that YouTube, Shorts, TikTok, Instagram, Facebook, LinkedIn, Twitter/X or Google Drive are extractable and playable on the installed app. Availability depends on the bundled extractor version, site changes, native runtime/ABI and network conditions. Public posts/stories requiring session credentials are not handled generally.
3. **Playlists/channels remain unsupported by this single-file flow** (`--no-playlist` and `MediaCatalog` rejects playlist resources). No fake preview/download success is substituted.
4. **Native iOS implementation missing**; no SwiftUI/Xcode/KMP project supplied. No iOS background URLSession, photo library or Files integration performed.
5. **Pause/retry caveat:** fragments survive same-job in-process interruptions, but confidential media URLs are intentionally only in volatile memory. Full reboot-survivable media resume requires a separate secure restart workflow, plus partial-download garbage collection.
6. **Android API 26–28 visibility** uses an app-specific fallback; public MediaStore saves are implemented for API 29+. Gallery/photos indexing of each category and SAF folder behavior require OEM/device tests. Private+public mirrored bytes may double storage usage.
7. **Not yet implemented:** user-selectable output naming template, automated age-based cleanup of user files, biometric/Face ID app lock, bottom-bar style preference, optional delete-confirmation override, in-app rename/move of durable OutputRecords, and per-site playlist batching. These were not presented as working settings.
8. **Performance and typography** require real-device profiling: bundled Python/FFmpeg increases APK/installed footprint, Arabic uses the supplied IBM Plex Sans Arabic regular asset (other weights can be synthetic), color/contrast and 60fps are not quantified.
9. **Historical schema migration:** newly written structural fixture is not authentic historical schema verification. Real historical v1 schema artifact, migration execution and CI are still required.

## Optional product idea (proposed, not silently implemented)

Add a **Download Recovery Center** that detects duplicate content hashes, explains interrupted transfers in plain language and offers one-tap resume/retry, backed by encrypted durable token/session recovery rather than displaying low-level HTTP errors. This could reduce clutter and accidental duplicate files after device restarts.

## Reproduction

```bash
LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8' bash scripts/verify-host.sh
python3 scripts/test-release-media-isolation.py
bash scripts/test-workspace.sh
# On a networked machine with JDK 17, Android SDK 36 and Gradle 8.14:
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
./gradlew :app:assembleRelease --no-daemon
```

**Decision:** Source-level improvements and host checks delivered; **release remains NO-GO** pending native build, iOS project and all essential live/device qualifications. None of these gaps is represented as a passing test.
