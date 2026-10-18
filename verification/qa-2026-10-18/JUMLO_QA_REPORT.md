# Jumlo reliability audit — October 18, 2026

> **Release verdict: NO-GO / source fixes under review.** All fixes are isolated on
> `qa/reliability-audit-20261018`, PR #4. No production/main changes in this audit.
> Device reproduction, full Android installed tests, live-provider transfer,
> and iOS validation were **not** performed. Do not interpret source diagnosis
> or committed code as a verified on-device fix.

## Phase 0 — mapping (prior to the first change)

Baseline: `main` at `77fca5d749fe7b8d0e0baa683303ff5641fcdbc8`.
Native Android Kotlin/Jetpack Compose (Navigation Compose),
ViewModels/StateFlow + Room/DataStore, WorkManager/CoroutineWorker,
OkHttp StreamTransfer with durable partial journals, PDFBox-Android and
PdfRenderer, scoped SAF/MediaStore and SHA256-verified private outputs.
EN/AR resources live in `app/src/main/res/values[-ar]`.
The repository has iOS icon art but **no** iOS project or app binary.

`MainActivity` -> `JumloNavHost` -> `AppViewModel`/`MediaViewModel`
-> `JobStore`/Room and `WorkManager` -> `OperationDispatcher`
-> download/PDF/image handlers -> `OutputStore` private publication
-> `PublicOutputStore` SAF/MediaStore -> Files/Activity UI.

## Bug inventory (exact baseline file/line; execution status explicit)

| ID | Severity | Area / baseline file:line | Reproduction steps; observed vs expected; root cause | Status on PR #4 |
|---|---|---|---|---|
| JML-001 | Major | Recovery `data/jobs/JobStore.kt:28-29,50-56` | Start direct download, pause, terminate/reopen. **Source predicts:** paused job gets enqueued on recovery because user pause and unexpected interruption both used `INTERRUPTED`. **Expected:** intentional pause stays paused until Resume. | **Patched:** persisted `PAUSED`, recovery exclusion, resume UI, SQL lease fences, JVM and host tests authored. Android process-death behavior unverified. |
| JML-002 | Major | Process recovery `data/source/EphemeralSecretStore.kt:9-16`; `data/processing/DownloadOperationHandler.kt:59` | Start, kill app process, reopen and retry. **Source predicts:** URL handle is missing because stored in RAM only; `SECRET_EXPIRED` requires URL reentry. **Expected:** recover or clearly request reentry. | **Deferred:** no automatic recovery without consented at-rest secret storage. Re-entry prompt exists in Activity screen; true process-death/UX validation needed. |
| JML-003 | Major | Public output `data/output/OutputStore.kt:57-61` | Revoke chosen SAF destination, then finish an operation. **Observed source path:** verified private result persists with `publicUri=null`; global Files warning *already existed* at `FilesScreen.kt:84-89`, but affected individual result was not labelled. **Expected:** clear per-output status and Save as recovery. | **Patched:** individualized EN/AR private-only note in shared OutputRow. Grid has global warning only. Runtime unverified. |
| JML-004 | Major | Media `data/media/MediaOperationHandler.kt` / `data/media/NativeDirectMediaRuntime.kt` | Input JavaScript-only social-video page or split A/V stream. **Observed source:** standard APK has no full provider extractor/muxer; no assertion of live success. **Expected:** supported direct media, honest unsupported state otherwise. | **Deferred:** experimental extended media variant exists but is not qualified for real providers, size, licenses or app-store distribution. |
| JML-005 | Minor | Settings `ui/settings/SettingsScreen.kt:167`, `data/preferences/UserPreferencesRepository.kt:164-165` | Persist max concurrency=6 then open settings: UI limits picker to 1-4. **Expected:** selectable 1-6. Root: presentation inconsistent with bounded persisted range. | **Patched:** 1-6; Android preference recreation test authored. |
| JML-006 | Critical scope gap | iOS `brand/ios/` | Look for runnable iOS application/Xcode workspace: icon set exists; no source target. **Expected:** runnable iOS app per requested cross-platform DoD. | **Deferred — separate iOS implementation required**, no claim of iOS completeness. |
| JML-007 | Major risk | PDF `data/pdf/PdfBoxBackend.kt:320+` | Compress 500MB+ PDFs, encrypted/malformed/mixed page size and test outputs under low memory. **Source:** bounded image decode guards and output check exist; no real device characterization. **Expected:** no data loss/OOM, fidelity and reasonable progress. | **Unverified:** device and representative fixture suite required; no speculative fix. |
| JML-008 | Major | Transfers `core/StreamTransfer.kt:118` | HTTP 200 with zero bytes, unknown/content-length 0. **Source before change:** returned success, allowing empty file publication. **Expected:** actionable failure. | **Patched:** `EMPTY_HTTP_BODY` before publication; host + JVM tests authored; EN/AR error. |
| JML-009 | Minor | File-grid actions `ui/files/FilesScreen.kt:159-166` | Grid mode, attempt to tap Share/Info at 36dp. **Source:** less than 44dp touch target. **Expected:** accessible touch areas with fewer controls. | **Patched:** 48dp favorite + 48dp overflow, with Share, Details, Save as, Reuse and Trash preserved. Device/TalkBack verification outstanding. |
| JML-010 | Major | Image-download compression `data/processing/DownloadOperationHandler.kt:203-217` | Download PNG/WebP using image-compressed action. **Source:** encoder generated JPEG but ticket kept original name/MIME; compressor could produce more bytes than source. **Expected:** matching bytes/extension/MIME and preserve smaller original. | **Patched:** `DownloadCompressionPolicy`, JPEG ticket on shrink, original fallback on larger output, atomic stage, host + JVM tests authored. Runtime unverified. |
| JML-011 | Polish | Media error copy `res/values[-ar]/error_messages.xml:34,54` | Unsupported social site. **Before:** blanket claim all such downloading necessarily violates terms/app-store policies; could mislead. **Expected:** accurate actionable limitation. | **Patched:** concise EN/AR link guidance, no unsupported legal generalization. |
| JML-012 | Major | Job transition races `data/jobs/JobStore.kt:51-65`; `data/local/ProcessingEntities.kt:32-39` | Tap Pause or Cancel while worker transitions to FINALIZING or terminal state. **Before:** generic setStage could overwrite a newer FINALIZING, FAILED or PARTIAL stage after the UI read. **Expected:** never resurrect/erase a terminal result, never pause mid-publication. | **Patched:** atomic, guarded SQL pause/cancel transitions; SQL regression fixtures authored, device race test unverified. |

### Verification matrix

| Check | Recorded evidence | Status |
|---|---|---|
| Baseline Android CI at `77fca5d` | GitHub Actions #39091079271 | **Passed on baseline only**, not a qualification of these fixes |
| PR debug APK / unit tests / lint | GitHub Actions CI on latest PR SHA | **Pending at report authoring**; check PR checks for truth |
| Pure Kotlin host tests (`scripts/verify-host.sh`) | New source test cases committed | **NOT EXECUTED on this workspace**; no repository checkout available locally |
| New JVM tests | `JobLifecyclePolicyTest`, `StreamTransferEmptyBodyTest`, `DownloadCompressionPolicyTest` | **Authored; CI result pending** |
| Android emulator API 26/36 and real phone | Not run | **NOT EXECUTED** |
| Low storage, SAF revoked, process death / foreground service | Not run | **NOT EXECUTED** |
| Real direct and social provider playback/download | Not run | **NOT EXECUTED** |
| PDF fidelity, annotations, encryption, mixed rotations, massive files | Not run | **NOT EXECUTED** |
| Arabic, RTL, light/dark, TalkBack, 200% font, touch targets | Code/resource inspection only | **NOT DEVICE VERIFIED** |
| iOS compile/test | No iOS app source | **BLOCKED BY MISSING PLATFORM** |

### Changes and decisions

1. **Phase 1 / durability:** classify intentional pauses separately from crashes and prevent recovery from auto-rescheduling PAUSED work; use guarded atomic stage transitions and preserve fences. No persistent cleartext URL secrets added; process-death re-entry remains a documented limitation.
2. **Phase 2 / correctness:** fail empty transfers, preserve image source if JPEG compression grows, always publish truthfully labelled JPEG when compressed, expose all supported concurrency values.
3. **Phase 3 / performance:** review bounded stream buffer and PDF decode budgets; no performance measurements or speculative rewrites. Representative device profiling remains required.
4. **Phase 4 / UX:** localized PAUSED/Resume/error copy, per-output internal-only notice, grid 48dp controls and overflow.
5. **Phase 5 / regression:** authored focused host/JVM/instrumentation preference cases. Build and device release gate not signed off before CI result.

### Remaining risks and release gates

A green Android compilation and JVM test suite alone cannot establish that paused
background operations resume correctly on actual Android devices, that a converted
PDF looks right, that split video/audio playback works, or that a full-size release
is shippable. Required before merging/releasing: verified green CI on exact final
SHA; device API 26/36 process-death/recovery/SAF revocation; representative
large/encrypted PDF fixtures and visual/text comparisons; mixed quality video
playback with audio; 16KB compatibility; file-name Arabic/emoji and collision
tests; TalkBack/RTL/font-scale and rotation screenshots; platform licensing;
real iOS application if the iOS DoD remains in scope.

No intentionally removed features. File-grid Share and Details became
overflow actions, with Save as, Reuse, Trash also accessible there.
