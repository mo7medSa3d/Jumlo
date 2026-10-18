## 2026-10-18 — Color Designer UI/UX source refresh (NO-GO)

- Rebuilt light/dark Material 3 and category theme tokens around an indigo/lilac design system; preserved alternate accent preferences and Arabic/English.
- Applied coordinated cards, icon tiles, responsive Home/Tools discovery, improved Files import and type labels, prominent reversible download controls and progress, accessible search/dock interactions, and a matching splash.
- Added `scripts/test-premium-ux.py` and wired it into host verification. No processing engine, database, or download transport rewrite.
- Android compilation, on-device UI testing, real download providers and iOS are **not verified**. See `docs/UX_COLOR_DESIGNER_REFRESH_2026-10-18.md`.

## 2026-10-18 — Large PDF reliability, disk-spillable processing, and verified batch exports

- Removed the blanket 4 GiB cap for imported files and selected-result byte totals; admission is bounded by real disk availability.
- Stopped duplicating app-private imported source snapshots for every PDF/image operation; retained an authenticated path check and legacy SAF spool fallback.
- Routed PDFBox open/build paths through disk-backed scratch memory where compatible with the used PDFBox-Android API.
- Split and PDF-to-image jobs that would exceed 512 individual result records now produce one streamed, CRC-verified archive; generated PDF page counts and image geometry checked before packing.
- Fixed PDF comparison, PDF metadata, redaction inspections to avoid collecting extracted text/page objects for the full document; comparison returns early at the first mismatch.
- Kept valid source bytes as a new verified PDF output if compression cannot reduce size without unacceptable damage.
- Expanded Kotlin host coverage to **172** tests. Android build remains blocked on uncached Gradle 8.14; **not production-qualified**. See AUDIT.md.

## 2026-10-18 — Flagship safety, PDF split and sharing follow-up (source-only)

- Fix falsely completed HTTP resumed downloads by requiring true Content-Range total and validating truncated content.
- Verify generated ZIP member content and CRC, not only directory metadata; enforce pre-extraction caps.
- Add visual PDF split boundaries and strict page reorder parsing; correct the every-page preset.
- Make shared-link discovery work with prose/multiple URLs, avoid silently saving HTML webpages, and explain unsupported social-link batches.
- Remove artificial startup delay; preserve Jumlo product identity and Room data compatibility.
- Host validation: 165 Kotlin tests passed. Android build not executed successfully (Gradle 8.14 download blocked); production release NO-GO.

## 2026-10-18 — Commercial-grade file workflows (source iteration; NO-GO for store sales)

- Source/result reuse now opens a compatible tool rather than always Images-to-PDF.
- Added multi-selection from **published outputs AND imported source snapshots** for ordered ZIP creation, PDF merge, and images-to-PDF, using existing durable processing; published bytes SHA-checked and copied into new immutable sources.
- Added cleanup for newly staged copies if handoff fails, preserving published originals; added local DataStore Favorites and Home shortcuts, adaptive Files grid, paired Arabic/English strings.
- Added signature-derived SAF MIME correction (PDF/JPEG/PNG/GIF/WebP/ZIP/HEIC/AVIF/MP4/M4A/MP3), not filename guessing; host test-first red/green on type confusion.
- `scripts/verify-host.sh` green, 149/149 Kotlin host; Android Gradle build blocked by unavailable distribution, and no billing or production APK shipped. See `docs/COMMERCIAL_READINESS.md`.

---

# Jumlo — continued engineering changelog (2026-10-17)

**Baseline:** `Jumlo_Updated_Source_Hardened_2026-10-17.zip`, not a git repository.
All changes were applied in a separate unpacked copy and packaged as a NEW ZIP.
The original and the prior hardened archive remain unchanged. **NO-GO** for release:
Android Debug/Unit/Lint/R8/devices/live platforms were NOT verified.

**Every file changed, including every added test/evidence file, relative to that ZIP:**

| File | Action | Change / evidence boundary |
|---|---|---|
| `.github/workflows/ci.yml` | MODIFIED | Added a required synthetic FFmpeg/ffprobe host-fixture step before native qualification. |
| `AUDIT.md` | MODIFIED | Added current risk/fix/evidence matrix, distinguished current tests from historic native logs. |
| `CHANGELOG.md` | MODIFIED | Added this exact all-file inventory relative to the previously hardened ZIP. |
| `README.md` | MODIFIED | Summarized latest source-only changes, pass counts and build blockers. |
| `README_AR.md` | MODIFIED | Updated Arabic handoff with the current NO-GO Android verification boundary. |
| `app/src/androidTest/java/com/jumlo/app/data/DatabaseMigrationAndDaoTest.kt` | MODIFIED | Authored persistent Room re-open test to scrub legacy signed URLs while preserving progress; NOT EXECUTED. |
| `app/src/hostTest/kotlin/CoreTests.kt` | MODIFIED | Added host validation of PDF crop origins and strict Drive link identifiers. |
| `app/src/main/java/com/jumlo/app/core/DriveLink.kt` | ADDED | NEW pure HTTPS Drive/Docs ID parser rejecting impostors, raw text and credentials. |
| `app/src/main/java/com/jumlo/app/core/PdfCropCoordinates.kt` | ADDED | NEW pure geometry mapping UI top-origin normalized selection to bottom-origin PDF points. |
| `app/src/main/java/com/jumlo/app/data/files/EncryptedZip.kt` | MODIFIED | Enforced AES-256, preflight unsafe paths before streaming, staged extraction and overwrite refusals. |
| `app/src/main/java/com/jumlo/app/data/files/GoogleDriveClient.kt` | MODIFIED | Strict official-ID extraction, bounded responses, CREATE_NEW output channel and guarded cleanup. |
| `app/src/main/java/com/jumlo/app/data/local/AppDatabase.kt` | MODIFIED | DAO insertion wrappers redact signed/full direct URL legacy column. |
| `app/src/main/java/com/jumlo/app/data/local/LegacyDownloadPrivacyCallback.kt` | ADDED | NEW on-open SQL scrub for previously persisted signed URLs, keeping job status/bytes. |
| `app/src/main/java/com/jumlo/app/data/media/MediaOperationHandler.kt` | MODIFIED | Checked persisted rights acceptance at execution boundary; fail closed on missing consent. |
| `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt` | MODIFIED | Fixed crop box vertical origin via pure coordinate mapper. |
| `app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt` | MODIFIED | Stopped aggregating recycled input bitmap bytes over pages; bounded input page count. |
| `app/src/main/java/com/jumlo/app/di/AppModule.kt` | MODIFIED | Installed Room legacy signed-URL scrub callback in production database builder. |
| `app/src/main/java/com/jumlo/app/ui/downloads/MediaViewModel.kt` | MODIFIED | Removed default/auto acceptance of download terms and default cleartext opt-in. |
| `app/src/main/java/com/jumlo/app/ui/downloads/MediaWorkspace.kt` | MODIFIED | Added explicit rights checkbox and blocked download until accepted. |
| `app/src/main/res/values-ar/strings.xml` | MODIFIED | Added Arabic confirmation/error strings for rights and terms. |
| `app/src/main/res/values/strings.xml` | MODIFIED | Added English confirmation/error strings for rights and terms. |
| `app/src/media/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt` | MODIFIED | Disabled unpinned online yt-dlp updater; restore packaged extractor only. |
| `app/src/test/java/com/jumlo/app/data/files/EncryptedZipSafetyTest.kt` | MODIFIED | Authored zip4j real integration tests for plaintext/encrypted paths, traversal, limits and password; NOT EXECUTED. |
| `app/src/test/java/com/jumlo/app/domain/PureDomainAlgorithmsTest.kt` | MODIFIED | Authored JUnit regression that Drive downloads refuse clobbering existing destination; NOT EXECUTED. |
| `docs/DEPENDENCY_NOTICES.md` | MODIFIED | Updated SIL OFL pinned-font choice and outstanding FFmpeg/yt-dlp binary licensing. |
| `docs/FEATURE_STATUS.md` | MODIFIED | Reviewed 45 capabilities with zero unearned VERIFIED claims, new partial host coverage. |
| `docs/MEDIA_CAPABILITY_MATRIX.md` | MODIFIED | Kept every live platform UNVERIFIED; clarified host FFmpeg and gated downloader. |
| `docs/PRIVACY.md` | MODIFIED | Explained DAO/legacy scrub, WebView persistent cookie risk and need for validation. |
| `docs/RELEASE.md` | MODIFIED | Specified latest counts, missing Android SDK/Gradle/device and owner release gates. |
| `scripts/test-release-media-isolation.py` | MODIFIED | Two added consent/pinned extractor static source tests (5 in suite). |
| `scripts/test-source-static.py` | MODIFIED | Added source assertions for DAO redaction/Drive CREATE_NEW; closed SQLite fixture connection (14 tests). |
| `scripts/test-storage-sql.py` | MODIFIED | Added executed SQLite regression for legacy signed URLs and progress preservation. |
| `verification/TASK_STATUS.md` | MODIFIED | Updated matrix with current commands/exits and explicit NO-GO. |
| `verification/current-session/followup-2026-10-17/REPORT.md` | ADDED | Independent current-session commands/results, negative gates and owner sign-off report. |
| `verification/current-session/followup-2026-10-17/android-build-attempt.log` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |
| `verification/current-session/followup-2026-10-17/environment.txt` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |
| `verification/current-session/followup-2026-10-17/feature-generator.log` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |
| `verification/current-session/followup-2026-10-17/home-layout.log` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |
| `verification/current-session/followup-2026-10-17/media-ffmpeg.log` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |
| `verification/current-session/followup-2026-10-17/release-media.log` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |
| `verification/current-session/followup-2026-10-17/verify-host.log` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |
| `verification/current-session/followup-2026-10-17/workspace.log` | ADDED | Retained current executed test stdout/stderr or environment/build blocker evidence. |

| `CONTENTS_SHA256.json` | REGENERATED IN NEW ZIP | Per-file SHA-256/size/mode manifest produced and verified by packaging tool; no stale manifest. |

Current test evidence: `verification/current-session/followup-2026-10-17/REPORT.md` and log files.

---

# Changelog — updated uploaded-source follow-up (2026-10-17)

This section records **every changed/added file relative to the exact user-uploaded source ZIP**. All changes were performed in a copy. No remote commits, release signing or device build. **Release: NO-GO.** Original archive SHA-256: `855314d2150e32a990ec1f5a03f6c29e04fe8f578a38beefa879b036a5dcecd3`.

| File | Modification | Why / test boundary |
|---|---|---|
| `.github/workflows/ci.yml` | MODIFIED | Added source guard verifying optional media cannot be advertised as release-ready; native CI remains mandatory. |
| `AUDIT.md` | MODIFIED | Prepended modified-revision audit scope and remaining unverified gates. |
| `CHANGELOG.md` | MODIFIED | This complete file-by-file delivery changelog and historical section retained. |
| `CONTENTS_SHA256.json` | REGENERATED | Regenerated in delivered ZIP for every included source file, SHA-256, length and executable mode. |
| `README.md` | MODIFIED | Explained follow-up changes, source-only verification and decision boundary. |
| `app/build.gradle.kts` | MODIFIED | Scoped optional yt-dlp/FFmpeg artifacts to opted-in debug build; kept release and benchmark stubs. |
| `app/src/hostTest/kotlin/CoreTests.kt` | MODIFIED | Added ErrorLog tests and three 256-case seeded page/URL/path property regressions; 113/113 host passed. |
| `app/src/main/java/com/jumlo/app/core/ErrorLog.kt` | MODIFIED | Include rotated diagnostic entry in local read/export and remove rotation on clear. |
| `app/src/main/java/com/jumlo/app/data/files/EncryptedZip.kt` | MODIFIED | Guard encrypted ZIP creation/extraction against ZIP-slip/collisions, size limits and overwriting existing outputs; stage and publish safely. |
| `app/src/main/java/com/jumlo/app/data/processing/FileExtrasHandler.kt` | MODIFIED | Budget source staging, unique extraction paths, guarded output publication and cleanup of own partials. |
| `app/src/test/java/com/jumlo/app/core/PublicationAndErrorLogTest.kt` | MODIFIED | Additional Android JVM diagnostic-retention test (not run: SDK unavailable). |
| `app/src/test/java/com/jumlo/app/data/files/EncryptedZipSafetyTest.kt` | ADDED | New Android JVM zip4j malicious filename/collision/password/limits tests (not run). |
| `docs/DEPENDENCY_NOTICES.md` | MODIFIED | Documented font SHA/OFL decision, yt-dlp/FFmpeg licensing unknowns and release approval requirement. |
| `docs/FEATURE_STATUS.md` | MODIFIED | Per-capability source/evidence classification with zero unearned device-qualified entries. |
| `docs/MEDIA_CAPABILITY_MATRIX.md` | MODIFIED | Ten platforms explicitly UNVERIFIED; separately labeled synthetic host tests. |
| `docs/PERFORMANCE_REPORT.md` | MODIFIED | Corrected performance measurements and Baseline Profile verification gap. |
| `docs/PRIVACY.md` | MODIFIED | Documented local error log retention/clearing and privacy boundaries. |
| `docs/RELEASE.md` | MODIFIED | Current execution gate, commands/results, no-go decision and signing/legal gates. |
| `docs/dependencies.md` | MODIFIED | Updated current native isolation and remaining artifact/license audits. |
| `docs/media/IMPLEMENTATION.md` | MODIFIED | Clarified internal media visibility, platform status and release exclusions. |
| `scripts/package-source.py` | MODIFIED | Pinned uploaded ZIP SHA; regenerate checked content manifest from current source including old-manifest replacement. |
| `scripts/test-release-media-isolation.py` | ADDED | New host source tests for debug-only media packaging assumptions (not a native proof). |
| `scripts/verify-native.sh` | MODIFIED | CI Gradle release dependency graph guard with extendedMedia=true as well as base config. |
| `verification/TASK_STATUS.md` | MODIFIED | Corrected current source execution pass/block status table. |
| `verification/current-session/REPORT.md` | ADDED | Standalone current execution report and owner sign-offs. |
| `verification/current-session/feature-matrix-generator.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/home-layout.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/media-ffmpeg.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/native-environment.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/release-media-isolation.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/results.json` | ADDED | Machine-readable execution ledger and unverified native/device items. |
| `verification/current-session/test-core-after.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/verify-host-final.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/verify-host-utf8.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/verify-host.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |
| `verification/current-session/workspace-safety.log` | ADDED | Reproduced host check or blocked native attempt stdout/stderr evidence; not a mobile/device PASS. |

## Verification summary for this follow-up

Host: **113/113 core**, **8/8 media bootstrap**, **9/9 SQLite**, **12/12 source checks**, **6/6 workspace**, **11/11 layout**, **5/5 synthetic FFmpeg**, **3/3 release media source checks**. These overlap in scope and are not a count of fully qualified user-facing features. New zip4j Android JUnit tests are authored **not run**. Gradle debug/unit/lint/R8/device results for this edited source: **BLOCKED/UNVERIFIED**, not inherited from historical logs. See `verification/current-session/REPORT.md`.

---

## Preserved historical changelog (input archive, not this modification)

# Changelog — production source execution, 2026-10-17

This records the changes made to the supplied ZIP in this delivery. Earlier supplied changelog claims are retained as historical material in `verification/production-2026-10-17/baseline-documents/source_CHANGELOG.md`; they are not evidence that an authentic v1 schema or native verification existed.

## Build and verification

- KSP 2.0.21-1.0.28/KSP2, Room 2.7.2 and Hilt 2.54 after reproduced processor compatibility failures. Kotlin/Compose compiler 2.0.21, AGP 8.10.1 and Compose BOM 2024.11.00 remain pinned; actual final outcomes are in RELEASE.
- CameraX 1.3.4 → 1.4.0 after the built APK exposed 4KB ELF load alignment in its image-processing library. Added 64-bit ELF alignment rejection and APK ZIP alignment checks to the native artifact gate; physical 16KB-device behavior remains a release qualification step.
- Central Android app/test plugin ownership; restored benchmark module; corrected its invalid build-type/signing configuration.
- Standard debug signing and correctly resolved optional release key paths; no private key included.
- Profileable optimized benchmark app; actual baseline-generation workflow. Removed invalid, unmeasured handwritten ART rules; retained comment-only active pointers and each original file as historical evidence.
- Separate native task logs/statuses, serial native gate before API 26/36 CI, SHA-specific artifacts. Reproduced KSP shadow-output collision is documented with its full-processing build workaround.
- Honest reconstructed legacy structural fixture, real v2 KSP identity evidence and data-preserving Room migration tests. Authentic historical v1 remains unavailable and is an explicit release blocker.

## Product and state

- Quiet Home with four working actions, actual tasks/results/drafts; real searchable/filterable Tools count.
- Feature screens and shared components split by purpose; lifecycle-aware targeted collections.
- Local Account & Data; grouped Settings, persisted theme/language/motion/haptics/network defaults and actual notification state.
- Explicit saved checkpoint separate from automatic recovery; Save/Discard/Keep dirty-back choices; startup restoration gate.
- Durable submission identity with correct fresh URL binding and waiting-state retry; preserved export picker state.
- Actual bounded preview, single workspace CTA, meaningful per-tool controls, fixed-JPEG target policy and hidden unused options.
- Flat file/transfer rows, contextual selection, overflow, waiting-job recovery, paired EN/AR copy and bundled Arabic typography.

## Integrity and safety

- Shared capability policy independent of DEBUG at UI/submission/enqueue/dispatch. 32 processing tools and a PDF viewer appear in Tools; unqualified/security-sensitive definitions are blocked.
- Exact-ID permanent-trash confirmation; durable trash/restore intent and fixed payload names; validated legacy migration.
- Safe cache-only cleanup; no silent trash purge or deletion of staging/resumed transfers.
- Unicode-safe output/archive names, existing-output hash checks, resumed transfer stale-offset fix.
- Aggregate bitmap budgets, correct alpha comparison/watermark opacity, cancellation and target-size failure behavior.
- Provider capability checks, safe KEEP_BOTH same-folder copy, blocked destructive replacement and validated move/delete boundaries.
- Five independent-review P1 corrections, focused JVM/UI/regression sources and actual host red/green evidence.

## Delivery status

See `AUDIT.md`, `docs/RELEASE.md` and `verification/production-2026-10-17/` for actual command results. No device screenshots, physical performance numbers, authentic old-install migration, owner-signed release or remote exact-SHA CI pass is invented. The source handoff does not approve production release.

---

## Jumlo 2026 premium brand and motion pass — 2026-10-17

- **Brand**: Jumlo in localized app labels, headers and Android launcher. Kept `com.jumlo.app`/`jumlo://workspace` for upgrade continuity; new default exported files use `Download/Jumlo` and older saved URIs are retained.
- **Assets**: original SVG emblem, Android adaptive/monochrome resources, 1024px iOS light/dark/tinted icon exports, Android 12+ splash backdrop, six original synthesized short UI sounds and reproducible generator.
- **UI**: spring press feedback, 44ms staggered grid entrances, moving RTL dock indicator, predictive back gesture response, download completion pulse, grid-first Files, short Downloads/Settings bento compositions, paired Arabic/English strings.
- **Interaction**: SoundPool with muted/zero-volume gating, Android haptic intent patterns by intensity, persisted Sounds and Haptic Intensity preferences, feedback wired into principal buttons, navigation and major file operations.
- **Verification**: Added 14 Jumlo asset/wiring/contrast tests to host test runner; preserved 123 Kotlin host tests and existing suite. Android SDK/Gradle bootstrap unavailable; no APK or device trace. This is **source candidate, release NO-GO**. See audit and verification logs.


## 2026-10-17 — Jumlo name and asset consistency

- Aligned the Android launcher, splash, Arabic/English interface, export paths, sound resource names, icon assets, docs and source symbols with the Jumlo name.
- Retained the redesigned abstract document/download icon, original sound effects, haptics, animations and UI improvements.
- Use `Download/Jumlo` for generated public folder defaults. Keep user-chosen SAF destinations and existing saved output URIs intact; obsolete autogenerated path values safely resolve to the Jumlo default.
- Preserved `com.jumlo.app`, `jumlo://workspace`, app databases and existing fixes on main.
- Updated host tests and SHA-256 source integrity metadata. GitHub Actions remains the Android build verification gate.
