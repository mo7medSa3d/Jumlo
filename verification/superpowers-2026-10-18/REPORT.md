# Jumlo | Superpowers audit and repair — 2026-10-18

## Scope and provenance

- **Inspected artifact:** `Jumlo-main-verified.zip`, SHA-256 `336ab9e6338b027710fe69b2cd19bff60bf27c2cbc790bfa7c04a054da9bef4a`.
- **Actual stack:** Kotlin, Android Jetpack Compose/Material 3, Hilt, Room, DataStore, WorkManager, OkHttp, PDFBox-Android, Android PDF renderer and file/document providers; Android minimum API 26, target API 36.
- **Navigation:** `MainActivity` -> Compose `JumloNavHost`; Home, Tools/Workspace, Downloads/Media, Files, Settings, Activity/Results, account modal.
- **State/data:** ViewModel state flows and Compose state, Room job/output/source tables, DataStore preferences, WorkManager leased worker, app-private source snapshots and outputs, verified public MediaStore/SAF copies.
- **Downloads:** HTTP streaming with bounded memory and verified resume, opt-in direct HTTP, native direct/HTML5 media runtime; a separate extended-media build is described by Gradle, not exercised here.
- **Platforms actually provided:** Android only. iOS icon assets are not an iOS app implementation.
- **Git:** All changes were made in an isolated local Git history built from this ZIP. No remote GitHub branch was accessed or changed.

## Bug inventory and disposition

| ID | Severity | Area and exact evidence | Reproduction: observed vs expected | Root cause | Disposition |
|---|---|---|---|---|---|
| JM-01 | Major | `scripts/test-storage-sql.py:95`, `app/schemas/` | Run `python3 scripts/test-storage-sql.py`: 11 tests passed, 1 error (`FileNotFoundError` for `app/schemas/legacy-v1-structure.sql`); expected a complete schema-regression suite | Required structural fixture was absent from the supplied archive | **FIXED for host test:** created a carefully labeled structural fixture and provenance note; this is **not** proof of an actual historical Room v1 schema or on-device upgrade |
| JM-02 | Major | `scripts/verify-host.sh:21` | Run the supplied verification script: references nonexistent `scripts/test-phase1-to-4-regressions.py`; expected every command to exist and run | Host test suite missing | **FIXED:** added eight defensive source checks, with direct red/green evidence for preference identity, output locations and dead fixture |
| JM-03 | Major | `ui/downloads/MediaViewModel.kt:255-257` | Queue media, change Wi-Fi-only/unmetered/HTTP permission, submit again; source could return previous job ID (device path unverified); expected a different intent to produce a distinct job | Previous idempotency signature omitted network restrictions | **FIXED in source:** `MediaSubmissionKey` length-prefixes all identity fields, including flags; host test distinguishes all three changes. Device integration unverified |
| JM-04 | Major | `core/DownloadLinkPolicy.kt:101`, `core/StoragePaths.kt:24`, `core/AutoOutputName.kt:13`, `core/Validation.kt:41` | Host red tests reproduced three failures: long emoji titles, surrogate splits, RFC5987 plus sign decoding and case-only filename collisions; expected legal Unicode files with <=200 UTF-8 bytes and exact extensions | Previous logic cut UTF-16 units, lost literal `+`, used case-sensitive collision checks | **FIXED:** codepoint/UTF-8-aware bounding, preserved suffixes and plus signs, case-insensitive collision detection; three tests red -> green |
| JM-05 | Major | `data/media/NativeDirectMediaRuntime.kt:24-25` | Attempt dynamic social-page extraction (YouTube, TikTok, Instagram, Facebook, LinkedIn, X) with base build; many pages cannot be processed; expected verified A/V extraction without silent/misleading success | Default runtime intentionally supports direct files and static HTML5 only; full extractor/codec muxer is not present in the base build | **DEFERRED — blocking requested feature completeness:** requires chosen extractor architecture, legal/size review and real platform/network tests; no fabricated implementation or support claims were added |
| JM-06 | Minor | `ui/downloads/DownloadsScreen.kt:176-183`, `ui/files/FilesScreen.kt:275-282` | Open saved-output details: previous UI gave a generic storage label, not the destination; expected to distinguish public copy vs app-private-only | Destination UI did not use `publicUri` and category metadata | **FIXED in source:** both detail views show branded relative folder for public copies or actionable app-only text; added Arabic and English translations. UI not device-rendered |
| JM-07 | Minor | `scripts/test-core.sh:3-8` | Run Unicode publication test under POSIX locale: `InvalidPathException` in `Publication.finish` despite otherwise correct Unicode file name; expected reliable host verification | JVM filesystem path charset inherited ASCII/POSIX locale | **FIXED:** runner chooses available `C.UTF-8` locale; recovered full host 180/0 then 181/0 |
| JM-08 | Polish | `ui/components/LegacyPreviewFixtures.kt` (removed) | Source reference search shows no consumers; component always displayed generic image icons as fake before/after comparison | Unused legacy visual fixture in production source | **REMOVED:** 115-line unused component, no references outside itself; source-level regression asserts absence |
| JM-09 | Minor | `scripts/package-source.py:20` | Package manifest previously attributed source input to a different SHA-256; expected hash of uploaded ZIP | Stale hardcoded archive identity from previous handoff | **FIXED:** updated current handoff's original-user-zip digest; generated source package verifies each member's SHA-256 |
| JM-10 | Blocker | Entire ZIP: no Xcode/Swift/UIKit/SwiftUI/Flutter iOS target | Try to build/run on iOS: no source or project; expected an iOS app if cross-platform completion is required | Source supplied is Android-only | **BLOCKED:** cannot implement or verify a missing iOS target without new work, platform tooling and signing |
| JM-11 | Blocker | `scripts/verify-local.sh`, `gradle/wrapper` | Run `bash scripts/verify-local.sh :app:compileDebugKotlin`: exit 2 (`Trusted cached Gradle 8.14 missing`); expected Android compilation | Offline environment lacks trusted Gradle distribution/Android build prerequisites | **UNVERIFIED:** all modified Compose classes need a real Android Gradle build plus Android instrumentation |
| JM-12 | Major verification gap | PDF transforms, WorkManager, SAF providers, Compose UI, background tasks | Rotation, process kill, low-storage, denied permissions, large/encrypted PDFs, real video+audio output and provider conflicts require devices/fixtures; host-only review cannot prove them | Platform-backed runtime and representative fixtures unavailable | **UNVERIFIED with reason:** treat as release gate; no claim of crash-free end-to-end workflows or performance measurements |

**No newly demonstrated critical file-loss bug was proven in the inspected host code.** The existing private snapshots, staged publication journaling, HTTP range/validator checks, and job-recovery ownership mechanisms are retained. Those observations **do not** prove end-to-end safety on Android.

## Phase-by-phase work

| Phase | Findings | Changes | Verification | Next release gate |
|---|---|---|---|---|
| 0: Baseline / inventory | Confirmed native Android-only stack; 177/0 baseline host tests; two broken Python fixture checks | Recorded source locations and precise test failures before fixing | `scripts/test-core.sh`, storage/source Python tests | Device-compatible build |
| 1: Critical stability and data loss | Durable job/source/output safeguards already present; no confirmed host-only critical defect | Preserved working implementations, avoided speculative rewrites | Host algorithms + source contracts | Kill/restart, full-disk, permission denial, process isolation device tests |
| 2: Major correctness | Filename Unicode/collisions, media submission preferences, missing schema/verification fixtures, unsatisfied social extraction | Isolated test-driven changes; deferred extractor honestly | 181 Kotlin host tests; Python regression suites | Real downloader site/codec compatibility tests |
| 3: Performance and stability | Core code includes storage/resource budgets; actual frame time, memory peak and startup numbers absent | No unmeasured rewrites or new heavy dependencies | CLI resource-policy tests only | Mid-range device trace, PDF stress benchmark |
| 4: UX, localization and accessibility | Missing output location explanation in both detail views, unused fake preview | Localized destination details; removed unused fixture; EN/AR resource parity enforced | Eight source regressions; full 720-key EN/AR parity | TalkBack, RTL/screen font scale and interaction tests |
| 5: Regression/handoff | Android build and iOS integration blocked by environment/source | Deliver modified source and honest verification log | Full host verification and deterministic ZIP integrity checks | Android+ iOS hands-on E2E, security, memory and release checks |

## Verification performed

1. **Before fixing:** `scripts/test-core.sh` **177 passed, 0 failed**. `scripts/test-storage-sql.py` **11 passed, 1 error** (missing fixture). `scripts/test-source-static.py` **13 passed, 1 error** (same fixture). Phase contract suite was missing entirely.
2. **TDD red:** three Unicode/filename Kotlin host cases failed under the original behavior. Reproduced source-contract failures for missing network preference identity, output destinations and dummy fixture. Every subsequent code fix was individually checked and committed in local history.
3. **After fixes:** `scripts/test-core.sh` **181 passed, 0 failed**. `scripts/test-phase1-to-4-regressions.py` **8 passed, 0 failed**. Locale resource scan found **720 EN keys and 720 AR keys**, no missing keys or duplicate resource identifiers.
4. **Full suite:** `bash scripts/verify-host.sh` passed on the last completed run; rerun after final file-library and resource test changes is recorded in `verify-host.log` in this folder.
5. **Android:** `bash scripts/verify-local.sh :app:compileDebugKotlin` **blocked, exit 2**, no trusted Gradle 8.14 cached and no Android SDK/emulator/physical device. No APK build, UI screenshots, startup/frame/memory numbers or instrumentation results were claimed.
6. **iOS:** no iOS target exists in this archive; cannot compile or execute iOS flows.

## Production-readiness decision

**NO-GO for the user's cross-platform definition of done.** Host regressions and source packaging are green, but dynamic-platform video downloading is incomplete in the base Android build, the Android app has not compiled or run on-device in this environment, and iOS code was not included. This is not a production-certification report.

### Prioritized validation and remaining work

1. Run the Android Gradle build on a provisioned SDK/Gradle 8.14 runner and fix compiler errors if any; then run UI/instrumentation tests on a mid-range physical Android device.
2. Exercise process death/restart and loss of Wi-Fi mid-download, storage exhaustion, denied SAF URI grants, concurrent copy/delete, encrypted/corrupt PDFs, and >1 GiB streamed file operations. Check bytes and hashes before release.
3. Specify and implement a tested media extractor/muxer solution if YouTube/social coverage is a firm requirement; include codec handling and upstream integration fixtures, avoiding false support promises.
4. Obtain/build an iOS app project and validate the complete feature parity matrix independently.
5. Run TalkBack and RTL/Arabic font scaling tests, instrument startup/scroll jank, heap usage and cancel behavior. Establish measured mid-range device baselines rather than guessed targets.

### Deliberate removals and constraints

- Removed **only** `ui/components/LegacyPreviewFixtures.kt`, an unreferenced 115-line pretend before/after preview; no functional tool or active workflow was intentionally removed.
- No new third-party runtime dependency, no mock result, no simulated downloader, no automatic weakening of protection for large files.
- The legacy Room structural fixture is **not** an authentic historical exported Room schema; see `app/schemas/schema-provenance.md` before using it as a migration compatibility claim.
