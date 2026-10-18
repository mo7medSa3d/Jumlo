## 2026-10-18 flagship reliability and direct-manipulation follow-up — SOURCE ONLY / NO-GO

**Starting artifact:** the most recent delivered `Jumlo-Commercial-Workflows-2026-10-18.zip`, not the older GitHub `main`. Application ID `com.jumlo.app`, brand identity, Room database schema, user outputs, and existing functionality have not been intentionally changed or reset. This is a narrowly verified improvement cycle, **not** a claim that every feature is commercially qualified.

| Priority | Verified source defect / cause | Implemented remediation | Verification |
| --- | --- | --- | --- |
| P0 | HTTP resumed 206 without stored total used range end as entire object, possibly publishing truncated content as success | Require full representation size in Content-Range, validate range, derive complete total and reject unknown total | New RED/GREEN actual transfer tests; host Kotlin suite |
| P0 | Generated ZIP accepted central-directory listing even with corrupt member payload | Stream every member and validate CRC, decompressed size, entry identity and budget before publication; delete invalid output | New adversarial ZIP corruption tests; host Kotlin suite |
| P1 | ZIP extraction could expand thousands of entries before rejecting too many output files | Cap extracted entries before writing temp files; enforce conservative per-job output, archive and disk budgets | Archive limit host tests and source wiring |
| P1 | Invalid PDF order entries were dropped silently, creating PDFs with missing pages | Strict parser for localized digits, invalid tokens, bounds, duplicates; preserve order or fail without publication | New RED/GREEN order regression tests |
| P1 | PDF split required range entry to define coherent multi-page parts | Visual split-after-page markers with derived contiguous segments, output count limit and preview | New grouping tests and EN/AR Compose source wiring |
| P1 | The new Split every page chip selected the single-range path, not EVERY mode | Wire chip to `flag1=true`, `every=1` and disable irrelevant tile selection | RED/GREEN source contract check |
| P1 | Browser share containing captions or several URLs was not handled reliably | Bounded deduplicating URL discovery, block obvious local/private literal targets from shared prose, reroute unknown webpage links to media inspection, and reject unsupported mixed social batches clearly | New Kotlin and source wiring tests |
| P2 | Artificial splash delay added 450ms even when app state was ready | Transition directly when settings and workspace are ready; keep readiness gate | RED/GREEN source check |

**Evidence this iteration:** `scripts/test-core.sh` returned `RESULT passed=165 failed=0`, including actual local HTTP responses/partial files, ZIP corruption, and PDF split policies. Python/UI/source/Room structural suites passed in individually observed runs. `./gradlew --offline :app:assembleDebug` **failed before compilation**, attempting to fetch uncached Gradle 8.14 and raising `UnknownHostException: services.gradle.org`. No SDK/device was available; Compose compilation, Android UI automation, real social-provider downloads, app performance measurements, APK/AAB sizes, Google Play billing, end-to-end output rendering and 16 KB native compatibility remain **NOT VERIFIED**. The source is **NO-GO** for commercial release. No GitHub push or commit was performed.

## 2026-10-18 commercial workflows — latest source follow-up / **NO-GO**

Continued from the most recent edited source ZIP (`Jumlo-Universal-Media-Source-2026-10-18.zip`), not a clean checkout or previous beta. Source fixes: correct result/source “Use as input” routing, batch ZIP/PDF/image handoff from saved outputs and imported snapshots, bounded local Favorites, safe rollback of newly imported copies, and 32-byte magic MIME inference for SAF-provided generic documents. Branding `Jumlo`, package `com.jumlo.app` and Room schema v2 unchanged. Not a complete flagship or SaaS/payments implementation.

**Verified host:** `scripts/verify-host.sh` **exit 0**: 149/149 Kotlin host tests, 11/11 media-bootstrap, 4/4 network guard, 7/7 universal architecture, 4/4 downloader density, 10/10 SQLite checks, 14/14 source/resource, 12/12 redesign wiring, 14/14 new file workflows, 14/14 brand/motion plus shell prerequisites. Tests on Android for new source snapshots were **authored, not executed**.

**Build blocker:** `./gradlew --offline :app:assembleDebug --no-daemon` exited 1 **before Kotlin/Android compilation** with `UnknownHostException: services.gradle.org`; Gradle 8.14 distribution not cached, Android SDK absent. No new APK, CI run, payment proof or real device output verification. Needs an Android build/device environment, compliant Play Billing and independent content-fidelity tests before charging customers. Detailed scope and business gates: `docs/COMMERCIAL_READINESS.md`.

---

## 2026-10-18 downloader UX and automatic source-compatibility follow-up — SOURCE ONLY / NO-GO

**Actual source changes in this follow-up:** Removed `PlatformSessionSheet` and its in-app WebView, cookies.txt importer, and custom User-Agent/header settings controls from the downloader. Removed obsolete translated UI resources and corrected login/rate-limit errors so they no longer direct people to deleted controls. Existing app-private session data is preserved for migration/compatibility; cookies are not invented and no user credentials are collected. The backend now selects Android/desktop HTTP User-Agent profiles per source, tries at most one alternate on HTTP 406/415 or successful HTML without verifiable media, and stops on explicit 401/403/429 denials. Profile selection is also applied when transferring the file rather than only discovering it; a previous custom agent cannot disable fallback. See `core/BrowserProfilePolicy.kt` and `data/media/MediaRuntimeFactory.kt`.

**Real source discovery improvements:** `Html5MediaDiscovery` accepts declared HTTPS OpenGraph/JSON-LD `contentUrl` sources, including extensionless signed URLs, but **only offers formats after live MIME/URL server verification**. Thumbnail discovery is bounded; local-address and unencrypted metadata candidates are rejected. No JavaScript runtime, ffmpeg, yt-dlp, DRM bypass, login circumvention, HLS/DASH or guaranteed social-platform extractor has been added. Source/page discovery is **not proof that a playable download succeeded**.

**UI/UX touched in this iteration:** `MediaWorkspace` is link -> analyzing -> actual preview / format -> fixed Download button with honest restricted/unsupported states, no technical login sheet; `DownloadsScreen` now prioritizes active transfers before history, uses a compact summary and direct primary action; Home reduces oversized decorative hero; Files reduces redundant header copy/spacing. Other app-wide UI improvements inherited from the previous candidate remain, but **no all-screen device-verified redesign** is claimed.

**Verification:** `LANG=C.UTF-8 LC_ALL=C.UTF-8 bash scripts/verify-host.sh` **exit 0**: **141 Kotlin host** regression tests (pure algorithms/HTTP loopback), **11 media source static contracts**, **4 downloader density/static UI contracts**, **10 SQLite structural**, **14 source/resource**, **12 previous UI wiring**, **14 brand/motion**, plus shell launch/prerequisite safety tests. This is **not** an Android compile/device/media-site test. `./gradlew --offline :app:assembleDebug` **exit 1** due absent cached Gradle 8.14 and `UnknownHostException: services.gradle.org`; Android SDK also unavailable. APK size, 16KB compatibility, emulator screenshots, actual provider transfers, YouTube/Meta/TikTok/LinkedIn support, local playback and release signing remain **NOT EXECUTED**. CI/GitHub untouched.

**Source-of-truth evidence** is appended to `verification/current-session/2026-10-18/REPORT.md`. All older test totals in this audit refer to prior revisions and must not be added to the current 141 figure.

---

## 2026-10-18 flagship implementation pass — **source candidate, not released**

Source snapshot: SHA-256 input `5d60b822ad328c00995c96d333e4b870efb55586dc370887cc251af890d16663`. Remote `main` checked read-only at `3044f345b1f9f2e529c51fff8845cd52022b3089`; no remote write occurred. Current evidence is in [`verification/current-session/2026-10-18/REPORT.md`](verification/current-session/2026-10-18/REPORT.md).

**Verification:** `LANG=C.UTF-8 LC_ALL=C.UTF-8 bash scripts/verify-host.sh` exited **0** with **135 Kotlin host** tests, **6 media source**, **10 SQLite structural**, **14 consistency**, **12 redesign wiring**, **14 brand/motion** and **4 shell safety** checks. The four scopes are not Android builds, instrumented gestures, device runtime or external-source downloads. An Android APK/release measurement does **not** exist for this modified source; **NO-GO** until Android compilation, 16KB, API26/36, live licensed media downloads, document output checks and UI tests pass.

**P0/P1 improvements in source:** replaced direct-only media webpage rejection with a bounded passive HTML5 source scanner and real source HEAD/range verification (no YouTube/HLS/DASH claim); introduced a PDF thumbnail-based visual page editor with reorder/selection/deletion/undo and visual split/extract; added automatic per-image page geometry; removed two PDF compression full-rasterization paths; bounded expensive image decoding; added accurate compression failure, bounded PDF preview failure, media audio-unknown labeling and grouped Activity/results. Persistent Room schema and `com.jumlo.app` identity remain unchanged.

**Major unfinished work:** full UI redesign and drag integration on a device, independent PDF content fidelity, FFmpeg-free authorized HLS/DASH, live provider qualification, authenticated downloads, long-running UIDT scheduling decision, actual APK size and CI. These are not shown green.

---

## Additional hardened-ZIP iteration, 2026-10-17 — current status

Current execution continues **the updated hardened archive**, never the original
prototype or git remote. Fully reproducible current logs reside under
`verification/current-session/followup-2026-10-17/`. The Android SDK and Gradle
wrapper distribution were unavailable; no Android binaries or installed-app test
were produced, and historic native PASS artifacts from older revisions are **not**
accepted as evidence for this work. Current verdict remains **NO-GO**.

| Risk | Corrective source change | Evidence / outstanding test |
|---|---|---|
| PDF crop vertical inversion | Map top-origin controls into PDF media-box bottom-origin | `PdfCropCoordinates` host cases passed; PdfBox output reader **not run** |
| PDF creation excessive bitmap accounting | Decode, paint and recycle one image at a time; cap source count | Source inspection; Android PDF round trip **not run** |
| Password-protected ZIP weaker method / traversal | Require AES-256 headers; validate filename before extraction; retain stage and no-overwrite publication | Safe path properties pass host; real zip4j JUnit **not run** |
| Drive URL impostor / overlarge metadata | Canonical Drive/Docs HTTPS ID-only parser; bounded response reads | Host DriveLink tests passed; official OAuth/API **not run** |
| Drive destination overwrite | `FileChannel` with `CREATE_NEW`; cleanup only files created by this operation | JUnit regression authored; Android JVM **not run** |
| Persisted signed download URL | Sanitize new Room DAO writes; scrub legacy `download_tasks.fullUrl` upon Room open, preserve progress | Actual SQL check passed; persistent Room/device migration test authored, **not run** |
| Media rights auto-accepted | Default false; explicit EN/AR checkbox; execution-boundary persisted-consent gate | Static/source assertions passed; actual UI **not run** |
| Unpinned online yt-dlp update | Restore the packaged copy instead of fetching executable code | Source gate passed; packaged Android binary hash/license **not qualified** |
| Media licensing exposure | Keep optional binaries debug-only, release gated; CI source checks and host FFmpeg fixture step | Static checks and host FFmpeg passed; Gradle graph, APK contents and owner license sign-off **not run** |

Previous 113/113, 12/12 and 9/9 counts below refer to an earlier hardened
source revision and are superseded by this follow-up's 117/117, 14/14, 10/10.
Open P0/P1: full Android compile/lint/R8, zip4j/PdfBox/CameraX/ML Kit integration,
real input-output/reference readers, Room actual migration, physical performance,
RTL/TalkBack/screenshot matrix, licensed FFmpeg and live supported-platform matrix.

## Current source-archive follow-up, independently executed 2026-10-17

The prior audit below was included in the user-supplied ZIP. It is historic provenance only; the current updated source has **not** passed Android Gradle or installed-app verification. The archive was modified in an isolated copy. The current session ran Kotlin host tests and static/SQLite/media fixture tests; see `verification/current-session/` and `docs/RELEASE.md` for exact scope and failure evidence.

**New fixes in this session:** `EncryptedZip.extract` no longer deletes an existing destination; validates all central-directory member paths, normalized/case-colliding names, symlinks, directories/size/entry limits, and file-as-parent conflicts before extracting; uses a temporary sibling directory and promotes it only after streaming checks. `EncryptedZip.create` refuses existing targets, streams source snapshots within a shared byte budget and stages before publication. `FileExtrasHandler` bounds temporary input/output copying, checks storage and uses unique extraction subfolders. `ErrorLog.read` now includes the prior rotation and `clear` erases both rotations; host regressions passed. Extended media is separated into debug-only dependencies and source sets; release/benchmark use the no-media factory and a CI dependency graph check was added. New real zip4j JVM regression tests cover malicious names, existing destination preservation, wrong passwords and roundtrips, but **those Android JVM tests were authored and not executed**.

**Open:** Everything that requires Android SDK/Gradle, installed Android devices, live platform resources, reference PDF/image output readers, API migration test on an authentic old database, UI screenshots/RTL/200% text, shipped AAR license/FFmpeg flags, source/output hashes from a current APK, owner signing, and an exact-archive CI run. Do not infer functional readiness from the older native build logs.

---

# Jumlo production execution audit — 2026-10-17

## Outcome and provenance

The supplied Kotlin/Compose application was repaired and refactored in an isolated extracted copy. The source ZIP SHA-256 was `37c3d4445c6455a1c0da2cd036fd6b597781a4eea4944bba4b0ee6eca794952e`. A local baseline commit exists solely for review. No assertion is made that this ZIP is identical to the plan's inspected GitHub SHA. No remote commit, push, CI run or production publication was performed.

The user's current instruction was to apply the attached plan to the attached source and return a ZIP. This supersedes the older plan's direct-main/push procedure for this delivery. Source/test work is implemented; hardware, authentic historic installation and signed exact-SHA release evidence remain mandatory gates. **Release decision: NO-GO until the open criteria below are met.**

Current evidence is under `verification/production-2026-10-17/`; individual final native outcomes are in [docs/RELEASE.md](docs/RELEASE.md). Historical supplied claims are preserved in `baseline-documents/` for comparison and are not current verification evidence.

## P0 / P1 findings and closure criteria

| ID | Priority / root cause | Source repair | Evidence / remaining closure criterion |
|---|---|---|---|
| B01 | P0 — inconsistent Android test plugin ownership; benchmark configuration failed | Single version-catalog AGP ownership, active benchmark module; corrected invalid benchmark build-type lookup | Actual Gradle configuration passed. Complete native gate outcomes are in RELEASE; exact-SHA remote CI remains unexecuted. |
| B02 | P0 — DEBUG-only availability disabled release processing | Explicit shared policy; UI, submission, enqueue and dispatch agree; navigation is not processing | Executable registry matrix and host policy tests. Native/device behavior still must be qualified; no blanket release approval. |
| B03 | P0 — documentation asserted an authentic v1 schema that was absent | Historical provenance search; explicit structural SQL fixture; genuine KSP-generated v2 comparison; data-preserving migration/Room tests; removed blanket schema ignore so CI source can include exports/fixtures | Structural SQLite passed and v2 identity matched. **OPEN:** authentic historical v1 export/database/APK unavailable; real old-install update not proven. No fake identity file was added. |
| B04 | P0 — unconfirmed broad trash purge | Explicit count/irreversibility dialogs capture exact IDs; no nullable global purge entry | Host exact-ID/path/digest tests. UI cancellation and Android boundary tests still need installed execution. |
| B05 | P1 — benchmark omitted and unmeasured profile claims | Benchmark restored; optimized profileable variant; real generator/workflow; handwritten rules removed from active app | Module tasks configure. **OPEN:** physical-device measurements and valid generated profile. |
| B06 | P1 — missing repository debug keystore | Standard debug signing; excluded optional release keystore; relative signing path corrected | Actual signing/build outcomes in RELEASE. **OPEN:** owner-signed release install/update. |
| U01 | P1 — dead media hero | Up to four working, locally configurable common actions; real tasks/drafts/recents | Source + pure selection/order tests; real activity smoke tests authored. Installed interaction/goldens pending. |
| U02/U03 | P1 — false count and inert unavailable controls | Actual policy filters/count; 33 Tools entries (32 processors + viewer); blocked entries hidden | Generator executes Kotlin policy; unknown IDs and navigation dispatch fail closed. |
| U04 | P1 — fake user/tier and uncertain account behavior | Local Account & Data with Files, measured storage, Settings, Privacy and Help | Exact callback instrumentation authored. No assertion of reproduced original physical tap failure; API/theme/locale proof pending. |
| U05 | P1 — ungrouped Settings and ineffective preferences | Grouped rows/modals, persisted choices, actual OS notification state, confirmed data controls; common haptic preference provider | Preference and Account instrumentation authored. Device locale/recreation/permissions pending. |
| U06 | P1 — unsaved back and recovery ambiguity | Separate explicit checkpoint/auto-recovery; Save/Discard/Keep; startup-ready gate; dirty-only predictive-back handling | Room persistence tests; independent review corrections below. Android process/back evidence pending. |
| U07/A01 | P1 — large navigation/components and visual card clutter | Feature extraction, per-screen collections, flat file/transfer rows, overflow, targeted primary CTA and purpose-based components | Source refactor and callback tests. Measured recomposition and full visual review pending. |
| A02 | P1 — inferred preview/results and uncertain output integrity | Real bounded previews; honest unknown progress; publication byte/hash revalidation; retry uses existing validated outputs | Host publication/transfer/archive tests; native pixel/structure results recorded separately. |
| S01 | P1 — high-risk unqualified paths | Protection/redaction/encrypted ZIP/Drive/media plus OCR/webpage gates; malicious archive/provider/trash hardening | Threat review in SECURITY_MODEL; native provider/cryptographic/device criteria remain open. |
| D01 | P2 — stale audit/feature/release claims | Executable feature matrix; action inventory; evidence-specific audit/release/performance/privacy docs | Current claims backed by paths/results. No fabricated green CI links, screenshots or timings. |
| V01 | P2 — inconsistent visuals/localization | Quiet semantic colors, rounded Material icons, bundled Plex Arabic, paired resources, width-based layout inside a WindowManager-derived safe pane | Static parity, pure fold geometry and opaque token contrast passed; resolved-screen AA/TalkBack/font/hinge review pending. |

## Additional integrity repairs

Managed trash used filenames that could collide with its own metadata. The new fixed payload path, transition journal and digest validation protect new transitions; legacy migration only proceeds with matching bytes. Data already overwritten by old code is not recoverable through invented metadata.

Publication normalizes Unicode names before journaling and validates existing output bytes on retry. Archive extraction handles Unicode-equivalent collisions without overwriting prior entries. Resumed transfer retry no longer reuses a stale starting offset after appending bytes and encountering an I/O failure. Host regressions reproduced these defects before the fixes.

Multi-image operations account for aggregate resident buffers; cancellation is propagated rather than treated as a per-file success. Image comparison includes alpha, watermark opacity is honored, impossible byte targets fail, and EXIF write failures are visible. SAF replacement is rejected when atomic safety cannot be guaranteed; explicit provider capabilities are rechecked.

Temporary cleanup no longer sweeps active/staged/resumable data or silently purges trash. Six actual filesystem tests cover allowed roots, age boundaries, preserved originals/results/transfers/staging, symlinks and cancellation.

The final plan-completeness review also closed concrete P2 source gaps: locally persisted/customizable Home actions; separating/occluding fold avoidance; provider rename/delete visibility based on both held write permission and provider support; live normalized rename conflicts; and pre-submission batch-rename names from the actual handler algorithm. Watermark, stamp and PDF annotation content is now visible in essential settings and validated before enqueue. Logo mode requires a second distinct selected image. The workspace regression run first reproduced five missing checks, then passed all 20 focused JVM tests (18 settings and two submission-secret cases); `workspace-essentials-red.log` and `workspace-essentials-green.log` preserve both outcomes. Pure favorites/fold checks passed 11 cases and Files policy checks passed 13 cases. These are separate scopes and must not be added to overlapping native-test totals as unique tests.

The supplied Gradle launcher had mode 0644. Its executable bit is now preserved in the source archive, so the documented `./gradlew` invocation works after extraction on systems that honor ZIP file modes.

Native verification found processor compatibility failures, restricted window-layout constructor use, and 4KB ELF load alignment in CameraX 1.3.4's image-processing library. The repaired processor pins are documented in RELEASE; hinge observation now maps public tracker data into application-owned bounds. CameraX 1.4.0 fixes the observed native load alignment, and the artifact gate now rejects insufficient ELF64 alignment and checks APK ZIP alignment. The original actual APK fails this new gate; the corrected debug APK passes. These checks remain distinct from runtime qualification on a 16KB device.

## Independent review

A fresh reviewer identified five P1 integration defects: restored download handles/waiting-stage recovery, same-folder Copy, ignored workspace controls, a startup draft race, and missing waiting-job Retry. Corrections were implemented and are separately re-reviewed in `verification/production-2026-10-17/review-report.md`. Regression sources include SubmissionSecretsTest, WorkspaceDraftPersistenceTest, destination-policy real-file tests and WorkspaceRecoveryInteractionTest. The report explicitly distinguishes inspection from executed device tests.

The independent warning review in `verification/production-2026-10-17/lint-warning-review.md` checks potentially material findings in the preserved native lint report against actual callers and WorkManager bytecode. It found no additional actionable security/data-loss/P1 defect in that scope. Style, unused-resource and dependency-update warnings remain documented; a successful lint command does not mean zero warnings.

## Evidence distinctions

| Evidence | What it proves | What it does not prove |
|---|---|---|
| Wrapper/hash/static resource checks | Checked distribution integrity, source/resource consistency and paired keys | Native compilation or UI behavior |
| Host core/files/HTTP | Actual algorithm/file/loopback invariants and simulated interrupted disk states | Android process kill, SAF/OEM codecs, foreground services |
| SQLite fixture migration | Known legacy structure/data preserved and current structural constraints | Authentic old Room identity or update from a real prior APK |
| KSP/native/JVM/lint | The individual command's observed scope in RELEASE | Full installed product qualification |
| Built APK/AAB inspection | Recorded artifact hash, known media markers and ELF64 load alignment; separate APK ZIP/signature checks | Complete licensing review, owner signing or 16KB runtime behavior |
| Opaque token contrast | The documented numeric color-pair calculations | Resolved screen backgrounds, every state or TalkBack acceptance |
| Authored instrumentation | Executable test source / compile result if recorded | A device pass until actually executed |

The initial source static/SQLite tests failed because v1 was missing; the current tests use an honest structural fixture and provenance requirement rather than manufacturing a schema to satisfy assertions. Earlier environment claims of no SDK/JDK are superseded by the toolchain installed for this execution. Native failures/retries remain in raw logs with their reasons.

## Open release criteria

Obtain authentic historic upgrade evidence; run API 26/36 instrumentation, 16KB-page runtime checks, output roundtrips and adversarial workspaces; verify real process death/low disk/SAF revocation; capture complete EN/AR light/dark/200%-font/TalkBack/keyboard/device goldens; measure representative physical performance; generate and validate a baseline profile; review built manifest/DEX/assets/dependencies and licensing; sign/install/update the release with the owner's key; and get all required remote CI jobs green on exactly the intended final SHA. No claim of zero open P0/P1 or 100% device coverage is made before those results exist.

---

## Jumlo brand/motion follow-up (2026-10-17)

See [`docs/JUMLO_2026_MOTION_BRAND_QA.md`](docs/JUMLO_2026_MOTION_BRAND_QA.md) for exact source pathways, phase actions, tests, visual/tactile limitations and mandatory device gates. This work was executed against the prior **updated source ZIP**, not current GitHub. Native Android build and iOS application development remain unverified/unavailable. **NO-GO** for production approval.


## Jumlo identity and asset integrity — 2026-10-17

The app launcher, translated screens, splash, resource identifiers, sounds, SVG assets, public output directory, test scripts and documentation all use Jumlo. The redesigned neutral document/download emblem and motion system are unchanged. Custom SAF destinations, persisted output URIs, package identity and user data are preserved. The download preference accepts only custom SAF tree URIs; any stored generated-path default resolves to `Download/Jumlo`. Native Android CI/device checks are tracked separately.

## 2026-10-18: Universal media-engine source candidate (not released)

**Baseline:** prior default main intentionally removed native Python/yt-dlp/FFmpeg due to 60 MB APK gate. Static HTML5/HTTP cannot analyze many social pages. **Change:** preserved native direct engine, separated its implementation into shared code, added an opt-in APK-bundled yt-dlp/FFmpeg adapter (`-PextendedMedia=true`) with exact-format selection/worker integration, bounded process runner, no external code updater or cookie UI, and explicit error classification. Added offline routing/URL security tests. Default APK excludes the extended assets/dependencies and retains the size check.

**Verification:** `scripts/test-core.sh` 144 host tests passed after routing work. Python security tests found then corrected invalid port/alternative-private-IP handling. Android Gradle build, APK/ABI alignment, real provider downloads, and device playback remain **NOT VERIFIED**. Do not claim YouTube, Instagram, TikTok, Facebook, LinkedIn or 'all sites' work. GPL-3.0 obligations and Google Play distribution policy require review. See `docs/media/UNIVERSAL_ENGINE.md` for acceptance gates.

## 2026-10-18: Extended media source follow-up and CI evidence

The new `extended_media` manual CI job builds/tests/lints an experimental GPL yt-dlp/FFmpeg Android APK and uploads an independent artifact and SHA-256 only on successful execution. The standard build's 60 MB size and native-exclusion gates are unchanged. The downloader now shows build-specific, localized media availability copy instead of advertising a lightweight limitation on the extended build. `verify-host.sh` runs network-guard and architecture regressions as part of its default checks.

Observed: `bash scripts/verify-host.sh` exited 0, with 144 pure Kotlin host test passes and all configured Python/static/shell checks. A separate local media fixture log reported `RESULT passed=149 failed=0` (including real FFmpeg mux/transcode), but the execution tool timed out before returning the outer script exit code; **full shell status unobserved**. An extended Gradle build was attempted and blocked before compile by unavailable Gradle 8.14 download (`UnknownHostException: services.gradle.org`). No native package or real-platform download was executed. **NO-GO** for release. See `docs/media/UNIVERSAL_ENGINE.md`.


## 2026-10-18 — Large-PDF/resource hardening on latest Flagship ZIP (source candidate)

**Input**: `Jumlo-Flagship-Professional-Source-2026-10-18.zip` (not remote GitHub `main`). The customer symptom was arbitrary rejection of large PDFs / vague memory errors. Do **not** infer the reporter's precise failure mode without device logs.

### Confirmed source findings and implementation

| Finding | Severity | Root cause in source | Change | Verification scope |
|---|---|---|---|---|
| Large import rejected even with free disk | P1 | `maxImportBytes = 4 GiB` | Disk/SAF streaming admission without artificial 4 GiB byte ceiling; keep minimum free-space check and safe copy | Pure Kotlin policy tests, static wiring |
| File batches rejected above 100 or 4 GiB aggregate | P1 | `OutputWorkflowPolicy` hard byte total and 100-item cap | Batch 1000 consistent with JobStore; no aggregate byte ceiling | Kotlin workflow tests |
| Operations duplicated huge source on storage | P1 | `withSeekable` copied already-imported cached `.bin` on every action | Verified immutable app-private snapshot reused; legacy uncached imports still spooled; fail closed for forged paths | Kotlin security tests, wiring check |
| PDFBox kept unrestricted PDF scratch in heap | P1 | Default `PDDocument.load(file)` / `PDDocument()` used in PDF paths | Temp-file scratch for PDFBox loads/creation, merge already disk-spillable | Source signature/API review, wiring check; Android compile NOT RUN |
| One-page split/export rejected above 512 published files | P1 | Hard `requireOutputCount` irrespective of output type | Stream each member to one verified ZIP for larger fanout; preserve individual outputs for <=512 | Kotlin 600-member ZIP CRC roundtrip; Android handler wiring check |
| PDF text comparison / redaction allocated full-page text lists | P1 | `pageTexts` used for both PDFs before comparing, also joined into one verification string | Lazy first-mismatch and early-match readers with bounded per-page text; redaction verifies only affected pages; metadata removes redundant page-object list | 2 Kotlin TDD tests, Android integration wiring |
| Already-optimized PDF marked as failed | P2 | Throws after two unsuccessful size-reduction passes | Copy valid original to new output rather than fail or rasterize; original unchanged | Source inspection / static wiring; Android compression test NOT RUN |
| Subarchive members not individually validated | P2 | ZIP byte integrity alone did not validate generated PDF/images | Validate PDF page count and image dimensions prior to packing; CRC-verified archive afterward | Static integration check only |

### Safety limits retained on purpose

- **Device**: real storage capacity, reserved free disk, RAM/bitmap budget, cancellation, Android/PDF codec limits.
- **Adversarial ZIP extraction**: 4 GiB expanded archive byte budget, 2048 entries, 512 separately published outputs (zip-bomb / record fanout resistance). This is NOT the import/PDF size limit.
- **Submission**: 1000 source IDs per job, to limit database/jobs state. >512 generated pages go to one archive. Huge jobs may still need practical batching.
- **No cloud processing**, brand rename, Room migrations, or remote GitHub write.

### Verification and blocker

- Fresh `scripts/test-core.sh`: **172/172** passed.
- Fresh `scripts/verify-host.sh`: see `verification/current-session/2026-10-18/large-files-host.log` and companion report. Includes Host Kotlin, Python/static/SQLite, resources, shell checks; NOT an Android test.
- Android `./gradlew --offline :app:assembleDebug`: **blocked before compilation** by `java.net.UnknownHostException: services.gradle.org`, required Gradle 8.14 not cached.
- Large encrypted/corrupted PDFs, 5+ GiB realistic PDF roundtrips, Android device tests, low-memory instrumented tests, UI performance, release APK size: **NOT EXECUTED**. Claiming zero possible errors would be false; release stays **NO-GO**.
