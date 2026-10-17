# JUMLO — Main Branch Production Excellence Execution Plan

**Audit snapshot:** 2026-10-17 | **Repository:** https://github.com/mo7medSa3d/Jumlo | **Branch:** `main`  
**Inspected SHA:** `a3c9ff0bca06f0c5959e33b3c7b7cf920be26c49`  
**Status:** EXECUTION PLAN, NOT IMPLEMENTATION / NOT RELEASE APPROVAL.

## Executive decision

Keep the existing **native Kotlin + Jetpack Compose** product, engines, user data and architecture. Do not restart or substitute Flutter/React Native/WebView. Transform Jumlo into a **fast, deliberately minimal, local-first, premium Android utility app** for PDF, images, downloads and files. The user's biggest complaints are actionable: unresponsive account/control flows, visually repetitive card stacks, a weak Settings information architecture, needless text/buttons, incomplete feature wiring, and uncertain release quality.

The governing release condition is **zero known open P0/P1 findings; all visible release controls operational; no fabricated file output or progress; full native CI green on the exact release SHA; and verified real-device performance/accessibility evidence.** This is a defensible exit gate, not a promise that all software defects can be mathematically eliminated.

### Evidence boundary

Inspected GitHub's `main` tree (377 file/directory paths), key application/Gradle/UI files, feature-status and audit documents, and the GitHub Actions run histories/logs. This was a selective source and CI audit, **not** a line-by-line qualification of every source file, Android compilation, APK install, emulator run or physical-device test. This plan begins with those missing checks.

- Latest inspected CI run: https://github.com/mo7medSa3d/Jumlo/actions/runs/39004487403 — `in_progress` at inspection; no pass claimed.
- Prior CI run: https://github.com/mo7medSa3d/Jumlo/actions/runs/38997444221 — failed. Its `build-test-lint` and both API-26/API-36 instrumentation jobs failed at Gradle configuration with: `Error resolving plugin [id: 'com.android.test', version: '8.10.1']`, already present on classpath with unknown version.
- `settings.gradle.kts` currently has `// include(":benchmark")`; the configured performance module therefore is not part of the active build. Treat the recent removal as a temporary bypass, not proof the configuration defect was properly solved.
- `app/build.gradle.kts` specifies `compileSdk=36`, `targetSdk=36`, `minSdk=26` (appropriate Android 16 target baseline), but an explicit debug signing configuration points to missing repo-root `debug.keystore`. Verify whether tasks supply it; do not assume success.
- `AUDIT.md` states Room `app/schemas/com.jumlo.app.data.local.AppDatabase/1.json` was created, but the inspected `main` tree contains `2.json` only. Investigate the state, migration-test preconditions and documentation drift.

### Confirmed findings vs risks

| ID | Priority | Finding grounded in `main` | Implementation and proof |
|---|---|---|---|
| B01 | P0 | Last completed CI runs failed before Android tests due to AGP `com.android.test` plugin resolution. | Fix plugin ownership/version resolution without dropping the module; run release, debug, lint, test and instrumentation all green on one SHA. |
| B02 | P0 | `CapabilityRegistry.enabled(id)` requires `BuildConfig.DEBUG`, making actual processing unavailable in release builds. | Introduce explicit per-feature qualification and release-gating independent of debuggable mode. Only enable tested engines; route enforcement must match UI. |
| B03 | P0 | `main` lacks Room v1 exported schema while `AUDIT.md` claims it is present. | Restore/verify *authentic* v1 schema from historical source; test real v1->v2 migration and schema identity. Never manufacture a schema merely to satisfy tests. |
| B04 | P0 | Settings/Files UI invokes `viewModel.purgeTrashItem(null)` directly from `Purge Trash` action, with no explicit confirmation at that callsite. | Add a destructive confirmation with item count and irreversible result; test cancellation and exact deletion boundaries. |
| B05 | P1 | `settings.gradle.kts` comments out `:benchmark`; Macrobenchmark claims are not executable. | Re-enable benchmark after fixing Gradle plugin resolution; generate valid baselines and physical-device measurements. |
| B06 | P1 | Release signing/debug keystore reference lacks the named tracked file. | Replace with reliable standard debug signing or provision ephemeral key in CI; smoke test installation; never commit private release keys. |
| U01 | P1 | The Home hero advertises `Instant Video Download`, yet `dl_media` needs an opt-in `extendedMedia` build and the base build disables it. | Replace featured dead capability with a real useful action; exclude optional media payload from normal release and honor licensing. |
| U02 | P1 | Tools filter says `All (16)` while registry declares 44 tool items plus a SHA-256 tool; not all are release-capable. | Compute live counts from *available* tools; either safely finish eligible features or exclude unavailable entries from public catalog. |
| U03 | P1 | `ToolGridCard` renders unavailable entries with an empty `onClick` and little clear unavailable-state semantics. | No inert click targets: either hide, disable with explicit reason, or provide a meaningful safe alternative. Add end-to-end tap tests. |
| U04 | P1 | `AccountHubModal` exists and top-bar click is wired in code, but it labels a local user `Jumlo Engine User` and shows a `Tier` without a real Jumlo account system. | Verify on-device tap; redesign as truthful Account & Data / Local Space hub with optional Google Drive connection and working destinations, no fake subscription/user identity. |
| U05 | P1 | Settings screen is a succession of radio buttons, controls and storage totals, rather than grouped navigation. | Rebuild as clean grouped Settings rows with modal choices, visibility of current values and clear data controls. |
| U06 | P1 | `BackHandler` navigates away from workspaces directly; no dirty-draft confirmation is evident at that callsite. | Verify persistence behavior; add proper Keep editing / Save draft / Discard choice and predictive-back integration. |
| U07 | P1 | Home/Files/Workspace rely heavily on bordered cards, large hero/stats areas, and multiple direct row buttons; confirms the reported visual clutter. | Switch to purpose-specific list rows/grid, contextual menus, one primary action, restrained surfaces and useful previews. |
| A01 | P1 | `JumloNavHost.kt` ~1,586 lines, `QuietPrecisionComponents.kt` ~1,145 lines, with globally collected flows in the host. | Extract feature screens, navigation shell, workspace scaffolds, account/settings, and per-screen state. Profile recomp before/after; preserve functionality. |
| A02 | P1 | Some preview and progress presentation is inferred from form controls rather than measured final output; output remains unverified on Android. | Always distinguish estimated preview from measured output; ensure exact real file-processing tests and cancel/recovery. |
| S01 | P1 | High-risk PDF redaction, protected PDFs, archives, OAuth/Drive, SAF provider writes and background transfers have no proven device qualification in this inspection. | Threat model, test known malicious/corrupt inputs and data-integrity round trips. Gate destructive/high-risk features until verified. |
| D01 | P2 | Docs report partially/untested feature status and verification differing from actual repo state (benchmark and schema). | Generate source-of-truth feature matrix and refresh AUDIT/FEATURE_STATUS/RELEASE with proof URLs, no unsupported green claims. |
| V01 | P2 | UI uses many per-tool literal colors and ad hoc icon choices instead of the approved restrained violet identity. | Replace scattered styling with semantic theme tokens, one Material Symbols Rounded family, controlled motion and Arabic/English typography. |

**Not yet proven defects:** whether Account Hub physically fails on installed APK, the exact reason the latest CI is still running, real-world memory/jank metrics, and individual engine behavior on Android. Test before asserting.

---

## Phase 0 — Reproducible baseline & complete interaction inventory (required before code changes)

- [ ] Confirm HEAD SHA and clean local worktree. Save build hashes and raw CI log links. Do not conflate older ZIP snapshots with the current GitHub `main`.
- [ ] Create `docs/SCREEN_AND_ACTION_MATRIX.md`: every root route, modal, sheet, toggle, menu, keyboard action, external picker, tool, state (loading/empty/error/success) and back path. For each: user intent, navigation target, command handler, persistence, expected error, test, verified platform/status.
- [ ] Create `docs/FEATURE_QUALIFICATION_MATRIX.md` from `CapabilityRegistry`, `Capabilities`, `OperationDispatcher`, settings mappings and real handlers, not marketing counts. Columns: tool ID, available UI, eligible build, inputs, handler, supported limits, golden tests, device proof, license/security gate.
- [ ] Capture baseline screenshots/videos of Home, Tools, Downloads, Files, Account, Settings, every workspace in light/dark and EN/AR; record visual deviations from the approved design.
- [ ] Freeze baseline performance: physical mid-range Android device plus API 26/36 emulator smoke; release-like package, startup TTID/TTFD, frame percentiles, heap high-water mark, peak processing RSS, APK/AAB sizes. Do not substitute emulator performance for real-device performance.
- [ ] Triage all actionable P0/P1 issues into `AUDIT.md` with evidence, root cause, owner/test and closure criteria.

**Exit:** traceable matrix for 100% of *visible* screens/actions and current build/perf baselines, clearly distinguishing untested from broken.

## Phase 1 — Make native build & CI trustworthy (P0)

- [ ] Resolve `com.android.test` AGP version/classpath conflict with one consistent plugin source, then include `:benchmark` again and verify Gradle configuration succeeds.
- [ ] Check Gradle wrapper integrity and actual wrapper invocation; verify compatible AGP/Kotlin/KSP/Compose/Hilt/Room versions rather than updating everything blindly. Review Compose BOM `2024.11.00` for modernization after stabilization.
- [ ] Verify tracked Room v1 schema against authentic prior app schema and run migration tests on SQLite and Android Room. Correct documentation.
- [ ] Fix debug signing: use reproducible default debug keystore or CI-generated temporary key. Keep release signing secrets out of repository.
- [ ] Run separate failing commands: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleRelease`, and `:app:connectedDebugAndroidTest` on API 26 + 36. Diagnose Kotlin, KSP, Android manifests and resource errors in order; never disable tests to force green.
- [ ] Make CI fast fail: only run Android-emulator matrix after Gradle compilation/tests/lint gate is green; preserve logs, JUnit XML, lint HTML, debug APK, release unsigned/signed distinction and screenshots as artifacts.
- [ ] Enforce `main` quality at every push: green CI on exactly the pushed SHA, no silent retries that disguise reproducible source errors.

**Exit:** all required native CI jobs pass on the exact commit; benchmark module configured and executed; no workaround by commenting out coverage.

## Phase 2 — Functional completeness, reliability and data integrity

- [ ] Wire *every release-visible capability* from `ToolGridCard`/Home/deeplink to a typed workspace, validation, real job handler, progress, cancel, result, reopen, share/export, and recovery path.
- [ ] Implement a single source of truth for capability availability across Home/Tools/Downloads/Files/dispatch. Remove empty click handlers; do not show inaccessible tools as tappable.
- [ ] Use real-file fixture suites: scanned and digital PDFs, rotated/multipage/large/transparent images, HEIC where supported, protected/corrupt PDFs, archives with collisions/symlinks/zip bombs, direct transfer with HTTP partial/resume behavior and slow/error connections.
- [ ] Compare input/output bytes or structural invariants after all operations; original inputs must remain untouched. Never report success before a verified output is durably published.
- [ ] Test process death at write, finalization and publication phases; unique jobs, idempotent retries, cancellation fencing, disk full, SAF permission revocation, missing/moved files, app force stop and provider errors.
- [ ] Gate destructive operations: managed trash purge requires explicit confirmation, delete-original requires consent and provider capability verification, rename/move collisions must be previewed, export retry must not reprocess.
- [ ] Maintain the bounded local-first contract: no unsolicited uploads, external accounts optional, no fake network status, progress or document transformation. Respect licensed/authorized download scope and GPL media exclusion in the base release.
- [ ] Keep advanced PDF redaction/protection, encrypted ZIP and Google Drive transfer unavailable to release users until independent integrity/privacy/security tests pass.

**Exit:** 100% of advertised release actions have test-backed real output + error/recovery behavior. No unimplemented command can be reached by tap, share intent or direct route.

## Phase 3 — New information architecture: minimal premium utility app

**Design direction:** Quiet Precision x restrained Material 3 Expressive; *premium SaaS simplicity*, not a fake dashboard or web UI. Preserve native Kotlin/Compose, violet visual identity, local-first experience and four destinations. The user sees at most one main commitment action per screen.

### Home

- [ ] Compact branded app bar: Jumlo + search + genuine Account & Data destination; one Settings entry in the hub (avoid duplicative settings and account actions if the design would clutter).
- [ ] Search field and a calm 2x2 (or adaptive) set of **working** frequent actions: Images to PDF, Merge PDF, Resize/Compress Image, New Direct Download. Smart reorder/favorites local only.
- [ ] Replace bulky welcome/hero/status/stats cards with contextual active task and recent files/drafts (only when real data exists), concise empty states and nonintrusive help.
- [ ] Show task name + minimal context + single intent. No 3-line promotional subtitles, colorful card rainbow, giant banners or tier/status decorations.

### Tools

- [ ] Search, compact category filters and single trusted count based on eligible tools.
- [ ] Category sections with a clear icon, a short name, optional one-line purpose; prefer rows in large catalog and optional grid for four favorites.
- [ ] Remove dead icons, concept entries, arbitrary colored boxes and duplicate download/file operations that already deep-link to their respective places.

### Downloads / Files

- [ ] Use clean file/transfer rows with icon, filename, meaningful metadata, progress/state and a trailing overflow menu instead of five inline action buttons.
- [ ] Short selection toolbar for multi-select; bulk actions only when valid. Show sort/search/filter on demand. Distinguish outputs, app-imported files, SAF-granted folders, and managed trash.
- [ ] Show only operations granted by storage provider; non-destructive defaults and explicit irreversible-action confirmations.

### Shared Hybrid Workspace

- [ ] File(s) selected first -> dominant actual preview -> 2-4 essential settings -> Advanced one-sheet -> sticky safe-area primary CTA (`Create PDF`, `Merge 3 files`, etc.).
- [ ] Show source/target names, destination, expected count; metadata and output size only after measurement.
- [ ] All editors share import/reorder/remove/retry/draft/resume conventions. Do not stack multiple bottom bars or many nested cards.
- [ ] Handle all states: first-use, loading, invalid input, busy, partial success, failed, retry, completed and unsaved-back.

**Exit:** compare every route with screenshots at 360dp, 412dp, tablet and landscape; UX review confirms one clear intent per screen with no redundant CTA.

## Phase 4 — Account, Settings & every control

- [ ] Confirm avatar click opens a functional `Account & Data` screen on API 26/36, Arabic/English, theme variants, after process recreation; verify keyboard/back/gesture navigation. **Do not call the code 'broken' without reproducing the app behavior.**
- [ ] Stop presenting fake `Jumlo Engine User`, membership `Tier`, cloud sync/account status, or account-login expectations. Show **Local workspace** with files/results/temporary storage and optional Google Drive connection only if officially functioning.
- [ ] Account & Data destinations: My files, Storage usage, Privacy, Optional connected services, Help/About, Settings. Every item routes somewhere and has a tested result; an absent feature is not a dead button.
- [ ] Rebuild Settings with grouped native preference rows instead of stacked cards/radios: **Appearance** (Theme/Language), **Downloads** (connection & location), **Files & storage** (cache, trash, permissions), **Privacy & About**. Use one-row summaries + appropriate bottom sheets/dialogs; destructive items separated and confirmed.
- [ ] Persist settings through DataStore and restoration, update locale/RTL correctly without duplicate Activity work, prevent preference flicker, and make choices immediately observable.
- [ ] Audit **every onClick/onCheckedChange/onValueChange**, accessibility action, long press, overflow and deeplink; test view state, result and back behavior. Remove no-op lambdas and placeholder controls.

**Exit:** 100% action inventory checked with instrumentation and device interaction evidence; no cosmetic controls without real action.

## Phase 5 — Design system, localization, motion & responsiveness

- [ ] Adopt semantic tokens rather than one-off per-tool literal gradients/colors. Suggested approved baseline: warm neutral light `#FAFAF9`, deep near-black dark `#0B0B0D`, quiet violet primary `#4F3DFF` (dark equivalent), category colors used sparingly in icons/metadata. Verify contrast on *resolved* surfaces.
- [ ] Material 3 Expressive **selectively**: modern menu/sheet/navigation behavior, improved state transitions, real button hierarchy and tactile feedback. No motion for motion's sake.
- [ ] One licensed icon family (Material Symbols Rounded), consistent 24dp optical size and >=48dp effective tap regions; IBM Plex Sans + IBM Plex Sans Arabic with local assets and notices.
- [ ] Adaptive compact/medium/expanded width; avoid hard-coded landscape-as-tablet shortcut. Account for hinge/folds, IME, safe areas, gesture and 3-button nav.
- [ ] EN/AR localization through string resources; do not mix hand-written branch strings with resources. RTL mirroring of navigation, **not** document pages, bytes, filenames or technical values.
- [ ] Test dark/light, dynamic content, text at 200% scaling, TalkBack, reduced motion, color contrast target WCAG AA (4.5:1 normal text, 3:1 essential non-text), keyboard focus and captions/tooltips.

**Exit:** screenshot golden set approved for all screens/states and interaction states; accessibility violations fixed/triaged before release.

## Phase 6 — Architecture and measurable performance

- [ ] Split `ui/navigation/JumloNavHost.kt` into small route composables and navigation shell (`home/`, `tools/`, `files/`, `downloads/`, `settings/`, `account/`, `workspace/`) while keeping behavior/saved state. Split reusable components by meaning; don't create abstractions without a use case.
- [ ] Prefer screen-owned, immutable `UiState`, targeted Flow collection, `collectAsStateWithLifecycle`, stable item keys/content types, `remember` for expensive derivations, and `derivedStateOf` only for rapidly changing state. Profile before optimizing.
- [ ] Keep image/pdf decode and file operations off main thread; sample thumbnails, cap decode buffers, cancel obsolete preview work, close `PdfRenderer`/streams, avoid repeated giant bitmap copies and background-memory leaks.
- [ ] Keep WorkManager and Room state durable across activity recreation/process death; validate controlled concurrency and disk/memory budgets during bulk operations.
- [ ] Fix `:benchmark` module and use Macrobenchmark + Baseline Profiles on actual representative hardware, at **release-like** optimization; compare cold start TTID/TTFD, warm start, frame timing, scroll/navigation jank, memory and APK size against baseline.
- [ ] Suggested initial SLOs (calibrate after physical measurements): cold TTID median <=1.2s on designated mid-range device, p90 <=2.0s; no frozen frames (>700ms); frame jank <5% on routine navigation/scroll; zero ANRs in acceptance scenarios; memory bounded during worst supported inputs and no OOM. Document device, versions and test method. Targets are **goals, not observed measurements**.

**Exit:** reproducible performance report (`docs/PERFORMANCE_REPORT.md`), charts/traces, performance regression tests, improved or justified metrics on ≥1 real mid-range device; no blocking work on UI thread.

## Phase 7 — Security, privacy, compliance and release readiness

- [ ] Threat-model file import/export, URI grants, archive extraction, PDF parsing/redaction, WebView, remote URLs/redirects, cloud token lifecycle and temp-file retention.
- [ ] Verify native code/FFmpeg/GPL licensing and feature flags. **Do not ship extended-media binaries** in base release. Verify manifest/DEX/assets/dependency graph in built AAB and publish accurate privacy policy.
- [ ] Confirm API 36 Play target and Android 16 edge-to-edge behavior; test API 26 compatibility. Audit target-SDK permission/foreground-service changes.
- [ ] Generate native release with R8/minification, resource shrinking, valid signing, reproducible versioning and release notes. Run release smoke and app update/migration on data seeded with an older version.
- [ ] Add security and accessibility regression tests to CI; archive evidence and actionable failure classification. No fake 'verified' badges.

**Exit:** Play release candidate with complete reproducible build log, actual AAB/APK, verified manifest, dependency/license reports, no open P0/P1 and a signed release checklist.

## Phase 8 — Final adversarial QA and closure

For every released tool run: correct inputs, wrong MIME, malformed data, empty selection, huge data, cancelled execution, force-stop mid-write, lost SAF grant, low disk, concurrent taps, rotation/font change, Arabic UI, dark theme, export failure, app restart, missing network (where applicable), result file round-trip. Record actual result/hash/metadata, not an assumed pass.

Conduct a *fresh-eyes* UI interaction review for confusing navigation, copy, density, repeated cards, labels, empty/error states and behavior consistency. Check the 48dp interaction target and 200%-font criteria. Re-run all checks on the final SHA, not an earlier ancestor, and don't report a pass until GitHub Actions actually says success.

**Release/no-go rule:** Block release while any P0/P1 remains, any visible button is inert, any file transformation can silently corrupt originals, native CI fails/is skipped, a high-risk handler remains unqualified, or the only performance proof is an emulator/repository claim.

---

## Direct-`main` execution procedure (when implementation is requested)

The user previously directed work on `main`, not a new PR/branch. Plan execution should preserve that branch policy, but **this plan-writing task does not commit or push anything**. When asked to execute: obtain clean baseline and current HEAD; apply coherent, reviewable small commits on `main`; make a checkpoint of existing changes; for each commit produce focused red/green tests and exact GitHub Actions URL; verify the remote SHA after pushing. Never force-push, silently discard local work, bypass branch protection or claim a write succeeded if GitHub rejects it.

### Minimum commands after build configuration is repaired

```bash
# Preflight on the user's configured Android/JDK environment
bash scripts/restore-wrapper.sh --check
python3 scripts/test-source-static.py
python3 scripts/test-storage-sql.py
python3 scripts/test-media-bootstrap.py
python3 scripts/test-media-ffmpeg.py

# Native gates
./gradlew :app:assembleDebug --no-daemon --console=plain
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
./gradlew :app:lintDebug --no-daemon --console=plain
./gradlew :app:assembleRelease --no-daemon --console=plain
# :app:connectedDebugAndroidTest via GitHub CI device matrix API 26, 36.
# :benchmark tasks: inspect available tasks after re-enabling module,
# then run generation/benchmark per actual AGP/AndroidX plugin configuration.
```

### Required deliverables

1. `AUDIT.md` with actual findings, severity, root cause, patch and evidence.
2. `docs/SCREEN_AND_ACTION_MATRIX.md` and `docs/FEATURE_QUALIFICATION_MATRIX.md`, mapping all visible actions to real implementation.
3. `docs/DESIGN.md` with tokens/components/adaptive behaviors and screenshot references.
4. `docs/PERFORMANCE_REPORT.md` with before/after physical measurements and traces.
5. `docs/RELEASE.md` with complete green CI links for one SHA, signed/unsigned build distinctions, launch/upgrade test results and blockers (if any).
6. Corrected source, preserved user data/migrations, validated APK/AAB only when actually built. Do not fabricate build files/evidence.

### Source links

- https://github.com/mo7medSa3d/Jumlo/tree/main
- https://github.com/mo7medSa3d/Jumlo/blob/main/.github/workflows/ci.yml
- https://github.com/mo7medSa3d/Jumlo/blob/main/app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt
- https://github.com/mo7medSa3d/Jumlo/blob/main/app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt
- https://github.com/mo7medSa3d/Jumlo/blob/main/app/src/main/java/com/jumlo/app/ui/components/AccountHubModal.kt
- https://github.com/mo7medSa3d/Jumlo/blob/main/docs/FEATURE_STATUS.md
- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/develop/ui/compose/performance/bestpractices
- https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile
- https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation
- https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- https://support.google.com/googleplay/android-developer/answer/11926878
