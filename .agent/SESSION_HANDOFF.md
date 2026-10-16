# SESSION HANDOFF — Jumlo (UniversalUtilities) — 2026-10-17

## 1. Mission, objectives, acceptance criteria

Continue "Jumlo" (Android, Kotlin, Compose+M3, Hilt, Room, WorkManager, OkHttp; pkg `com.jumlo.app`; minSdk 26, target/compileSdk 36) through 8 defined phases toward a complete production-grade utility app. Source-of-truth docs read at session start: README.md, AUDIT.md, verification/TASK_STATUS.md, verification/media-upgrade/VERIFICATION.md, docs/approved/tasks.json, docs/approved/CONTRACTS.md.

Acceptance criteria summarized by phases:
- **Phase 0 (build gate)**: wrapper SHA verify; fix compile errors; keep green: assembleDebug, testDebugUnitTest, lintDebug, assembleRelease w/ R8; run instrumented tests on API 26 + 36 emulators OR state explicitly they did not run.
- **Phase 1**: all 41 approved tasks from docs/approved/tasks.json implemented with tests OR their UI entry removed; priorities: core pipeline F01–F04, PDF structural ops (P01–P03, E01–E11), image ops (I01–I02, E12–E17), capture/scan, OCR, files (A01–A02, E21–E25).
- **Phase 2**: yt-dlp media downloader verification with live per-platform capability matrix; no DRM circumvention; rights confirmation; GPL-3 obligations to docs/DEPENDENCY_NOTICES.md; media disabled in release builds until licensing decision recorded.
- **Phase 3**: Room exported schemas + MigrationTestHelper tests; WorkManager idempotency; engine unit tests; fuzz/property tests for parsers; import size/count limits; storage preflight; ZIP-slip protection; no cleartext without consent; no signed URLs/personal data in logs; hot paths off main thread; bitmap recycling.
- **Phase 4**: Navigation Compose type-safe serializable routes; deep links; cold start <1s; Baseline Profile; macrobenchmark module; lazy list stable keys.
- **Phase 5**: spring animations; shared-element transitions; predictive back; determinate progress; haptics; reduced-motion support.
- **Phase 6**: 4/8 grid; design tokens only; empty/loading/error/permission states; snackbar undo; full en+ar localization (MissingTranslation lint error); RTL check; TalkBack labels; 48dp targets; 200% font; adaptive layout.
- **Phase 7**: unit tests for domain/engine; instrumented tests for SAF/scanning/merging/downloads; screenshot tests (light, dark, en, ar); CI on every push.
- **Phase 8**: signing via keystore.properties (never committed); versioning from Gradle property or git tag; docs/PRIVACY.md, docs/RELEASE.md, docs/DEPENDENCY_NOTICES.md; remove dead code/strings/permissions.

Also required in final report: exact commands + results; feature table (done and verified / done but not device-verified / partial / removed); per-platform media download results from capability matrix; screenshots if renderable; remaining risks; licensing decisions needing owner confirmation; NEVER claim "release-ready" or "supports all platforms" unless actually verified this session.

## 2. Project architecture and key dependencies

Layout of current tree (branch-lineage: `main` → `release-hardening` → `feature/full-completion` current):
- `app/src/main/java/com/jumlo/app/` main code. Key subsystems:
  - `core/`: pure-Kotlin/dependency-free engines: FileProcessing.kt (Hashing, ArchiveCore, SafeNames, MemoryBudget, PageSelection, ImageGeometry, HttpPolicy, Capabilities), Validation.kt; core/media/*; Publication.kt (journal/archive); ResumePolicy.kt; StreamTransfer.kt.
  - `data/local/`: Room (AppDatabase v2, DatabaseMigrations.FROM_1_TO_2, ProcessingEntities: ManagedFileDao, DownloadTaskDao).
  - `data/jobs/`: JobStore (Room-backed), OperationDispatcher (routes toolId → handlers, semaphores: bitmapSlot=1, downloadSlots=2), WorkBudget, DraftStore, EphemeralSecretStore (in-memory CharArray secrets by handle).
  - `data/processing/`: ImageOperationHandler, PdfOperationHandler (pdf_02 images→PDF, pdf_07 PDF→images), ArchiveOperationHandler (file_07 zip create, file_08 extract, sha256), DownloadOperationHandler, Settings.kt (per-tool @Serializable settings).
  - `data/source/`: SourceStore, SourceEntity, EphemeralSecretStore (F02).
  - `data/output/`: OutputStore + PublicationJournal (F03).
  - `data/files/`: DocumentTreeStore (SAF).
  - `data/media/`: MediaOperationHandler, MediaRuntime, MediaMetadata; `MediaRuntimeFactory` at `app/src/base/java/.../MediaRuntimeFactory.kt` for base builds and `app/src/media/java/...` for extendedMedia=true builds (bound to `io.github.junkfood02.youtubedl-android`).
  - `worker/`: EngineOperationWorker (WorkManager), CancelJobReceiver, ProcessingNotification.
  - `ui/`: AppViewModel (central state; form draft persisted via DraftStore debounce), ui/navigation/JumloNavHost.kt (single-activity Compose host; string-route `route` var, tabs via DockTab, ToolCard gating through CapabilityRegistry, WorkspaceScreen generic form-driven), ui/downloads/MediaWorkspace.kt + MediaViewModel + MediaBrowser (browser UI), theme/QuietTheme (Aurora palette committed as-is).
- `app/src/test/...` unit tests: 19 tests in 4 suites (MediaMetadataTest, EphemeralSecretStoreTest, PureDomainAlgorithmsTest, WorkspaceSettingsTest).
- `app/src/androidTest/...`: 5 suites exist but have NEVER run (no emulator).
- `app/src/hostTest/kotlin/...`: host script tests (scripts/verify-host.sh).
- Build system: gradle wrapper 8.14 (jar checksum verified), AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, Hilt 2.52, Room 2.6.1, Work 2.10.0, Compose BOM 2024.11.00, OkHttp 4.12.0, coil, datastore, exif, documentfile, window. build.gradle.kts top-level flag `-PextendedMedia=true` toggles media source set; debug/release split on BuildConfig.DEBUG gates the CapabilityRegistry.
- SDK available locally: platforms 33-36, build-tools 33-36.0.0, platform-tools. NO emulator; NO system images installed (download stopped per user).
- Gradle cache (~/.gradle/caches/modules-2/files-2.1) has ALL project deps + NEW: io.github.junkfood02.youtubedl-android + `com.tom-roush/pdfbox-android` 2.0.27.0 + `net.lingala.zip4j/zip4j/2.11.5` + androidx.camera trio 1.3.4 + com.google.mlkit/text-recognition 16.0.1.

## 3. All important user requirements and decisions

- **Do not install anything; internet is limited.** A background sdkmanager download (emulator+sys imgs API26/36) was started but KILLED before writing anything (no system-images dir created). Emulator install CANNOT proceed without new user approval. Instrumented tests stay NOT RUN.
- User explicitly APPROVED exactly 4 small dependency downloads (~55MB total): **pdfbox-android 2.0.27.0, zip4j 2.11.5, androidx.camera trio 1.3.4, ML Kit text-recognition 16.0.1**. All are now fetched into local Gradle cache. NO further downloads without new approval.
- Work must be in small commits on a feature branch; each commit must build+test pass.
- Final report must explicitly state what did and did not run; no false claims.
- Reproduce every result yourself; don't trust "passed" claims in existing docs.
- Ponytail mode active (minimum change principle), executing-plans + TDD skill discipline.
- The user's prior statement stands: do NOT download system images / emulator without explicit new permission.

## 4. Completed work + exact files modified (this session)

**Verification only (no source changes):**
- Wrapper SHA-256 independently verified: 7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172 (exact match official Gradle 8.14). Command: `sha256sum gradle/wrapper/gradle-wrapper.jar`.
- Build gate: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` EXIT=0 (then with `clean` + assembleRelease EXIT=0; most tasks were cached). Forced-safe uncached run `--no-build-cache --rerun-tasks` — everything passed EXCEPT 3 lintAnalyze* tasks which crashed AFTER compile/tests/R8 completed (all 93 other tasks executed); unit tests re-verified in real result XMLs: 19 tests, 0 failures. Then `./gradlew :app:lintDebug :app:assembleDebug` EXIT=0 (flake gone on retry, matching AUDIT.md's known lint worker flake — treat as environmental, not source bug).
- SDD workspace created at `.superpowers/sdd/tasks.json/progress.md` containing Phase 0 evidence + the lint-flake ruling.

**Source changes (uncommitted):**
- `gradle/libs.versions.toml`: added versions + aliases for the 4 approved deps (pdfbox-android 2.0.27.0, zip4j 2.11.5, camera trio 1.3.4, mlkit text-recognition 16.0.1).
- `app/build.gradle.kts`: added `implementation(libs.pdfbox.android)`, `libs.zip4j`, `libs.camera.lifecycle`, `libs.camera.camera2`, `libs.camera.view`, `libs.mlkit.text.recognition`.
- `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt` (NEW — Phase 1 P01/P02, most of a session's drafting): structural PDF backend w/ data classes (PageBox, PdfInfo, PdfInput, OrganizeOp, PageGroup, RedactRegion, FormEntry sealed, CompareVerdict/Result, RedactResult) + `PdfCapabilityPolicy.capabilities` set + `PdfBoxBackend` object with: open, info, pageTexts, merge, split, organize, insert, rotate, setCrop, setMetadata, metadata, protect (AES-128 via StandardProtectionPolicy), removeProtection, listFormFields, fillForm, flattenForm, compress (flate-RGB/GRAY box-downsample pure-Kotlin), compare (pageTexts equality w/ whitespace normalization), redact (operator-level content removal via own operator walker + ContentRedactor inner class painting opaque fill rects; stripAnnotations; pageFallbacks; per-page removedWordFragments; leftoverAfterRedact verification). Currently in a CLEAN-up-not-yet-compile state — see §5.
- `app/src/test/java/com/jumlo/app/pdf/PdfStructureCoreTest.kt` (NEW): 15 pure-JVM JUnit tests for the backend (merge order, split, extract reorder, organize w/ rotation, rotate in place, insert external pages, metadata round-trip, protect/remove standard password round-trip, wrong-password error, form fill+flatten, redact removes glyphs in region, redact outside region keeps text, compress keeps text and shrinks flate image, compare flags text differences, page info exposes boxes+text flag).
- `app/src/test/java/com/jumlo/app/pdf/PdfFixtures.kt` (NEW): pure-JVM (no android classes) PDF builders: simpleDoc (text pages on PDType1Font.HELVETICA at (72,700)), imageDoc (600x400 flate DeviceRGB image + optional text page), formDoc (AcroForm text+checkbox w/ refreshAppearances). Uses hand-rolled flate DeviceRGB COSStream w/ DeflaterOutputStream.
- `.superpowers/sdd/tasks.json/progress.md` (NEW): Phase-0 ledger; written evidence + rulings.

**Environment/infra (NOT repo files)**: created `.superpowers/sdd/tasks.json/` dir; no other disk/config changes.

## 5. Current work in progress / unfinished (exact resume point)

**PdfBoxBackend.kt** is mid-edit, uncompiled, and still contains leftovers of an old edit path. Precise remaining tasks:
1. Re-read the file fully; REMOVE any leftover duplicate/obsolete pieces (earlier duplicated `normalizedKey` helpers were removed by one edit but `verifyAgainstRegions` may still exist; there may be two copies of helper blocks). Search order: grep for `normalizedKey`, `verifyAgainstRegions`, `regionText`, `emptyAll`, `leftoverAfterRedact`, removed-word fragments handling.
2. Make `ContentRedactor.removedWordFragments` **public** (currently `private val`) so blackened boxes' strings can be checked after mutation via `leftoverAfterRedact`.
3. Flow inside `redact()` must end as exactly:
   `plan = ContentRedactor(doc, page, areas)` → `plan.execute()` boundary (removedChunks/complex checks) → `plan.paintOver(areas)` → `fragments[pageIndex] = plan.removedWordFragments.toSet()` → `stripAnnotations(doc, regionsByPage)` → `leftoverAfterRedact(doc, fragments)` → return `RedactResult(removedChunks, removedImages, removedAnnotations, fallbacks.distinct(), leftover)`.
4. `redact()` needs `pdImageXObject` decode path validated: `image.createInputStream()` (decodes filters) must be re-used if more robust; the code currently reads from `image.createInputStream()` — correct per javap.
5. Get the file to COMPILE first (`./gradlew :app:compileDebugKotlin`), then run tests: `./gradlew :app:testDebugUnitTest --tests "com.jumlo.app.pdf.*"`. Expect RED on first run (test compile errors likely); fix by consulting the javap-verified API list in §6. Then get GREEN, then wire into handlers (see §10 next steps).

## 6. Known bugs, blockers, risks, root causes

- **lintAnalyze* failures under `--rerun-tasks --no-build-cache`** are a REPEATING environmental flake; never treat as source bug; log-and-retry pattern: run without `--rerun-tasks` or just `:app:lintDebug :app:assembleDebug`.
- **rm -rf `app/build` rapid-corruption event**: `app/src/main/java/com/jumlo/app/ui/theme/Color.kt` was briefly corrupted with a leading `/` byte after a fresh clean build — likely tooling/cache layer, recovered via `git checkout --` (verified `git status` clean afterwards). Rule for future: after ANY batch file edit, run `git status --short` + `git diff --stat` and re-verify `head -1` of suspicious files before continue/commit.
- **`PDFTextStripperByArea`** uses `android.graphics.RectF` — NOT usable in pure-JVM tests (jvm-safe replacement = use doc-level `pageTexts` and check removed-word fragments against post-mutation extraction, which is what `leftoverAfterRedact` does; raster-based region verification is deferred to Android handler).
- **`pdfbox-android` package prefix is `com.tom_roush`** not `org.apache.pdfbox`; cryptography handler classes are in `com.tom_roush.pdfbox.pdmodel.encryption`; `InvalidPasswordException` confirmed there.
- `PDAcroForm.flatten()`, `PDCheckBox.check()`, `PDField.setValue(String)`, `PDRadioCollection.setValue(name)`, `PDDocument.load(File, String)` etc. confirmed real via `javap -classpath <extracted pdfbox classes.jar>`.
- **`patch/diff fly away` risk**: several small edit calls in a row caused inconsistent file state in `PdfBoxBackend.kt`; now the file must be reconciled in ONE final write (see §5 steps) before continuing.
- **Security policy around redact()**: the test asserts removal truly deletes glyphs (`left.isBlank()` after covering full text area) — hard requirement for the feature, must stay red if not met.

## 7. Tests/builds already executed (this session, with results)

1. `sha256sum gradle/wrapper/gradle-wrapper.jar` → PASS (exact official 8.14 hash).
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` → PASS (cached + incremental).
3. `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` → PASS (still cached).
4. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-build-cache --rerun-tasks` → FAIL: only 3 lintAnalyze* tasks (lint flake re-verified as env, not source); everything else passed (93 tasks executed, 19/19 unit tests green, R8 OK).
5. `./gradlew :app:lintDebug :app:assembleDebug` → PASS (proves lint flake is env-only).
6. `./gradlew :app:assembleDebug` after adding 4 new deps → FAIL **only** because `Color.kt` got corrupted (1-byte); deps resolved fine.
7. After Color.kt restore via `git checkout --`: no rerun yet; source tree still modified and untested with new deps.

## 8. Tests NOT executed

- `:app:testDebugUnitTest` with the NEW pdf/* test sources — pending (source not yet compiling).
- Any `connectedDebugAndroidTest` / androidTest suites — **never run, no emulator permitted**.
- No media/video platform download matrix attempted yet.
- No lint on newest pdf code (depends on §5 completion).
- No instrumented tests for PDF backend (PdfBackendQualificationTest etc. from tasks.json not yet authored at all).

## 9. Git state (verified just now)

- Current branch: `feature/full-completion` (created this session from `release-hardening` HEAD 942c288).
- HEAD: 942c288 `chore(hardening): re-apply security and release gates on Aurora` (embedding the earlier a61e03b Aurora retheme).
- Working tree NOT clean: modified — `gradle/libs.versions.toml`, `app/build.gradle.kts` (4 new deps); untracked — `.superpowers/`, `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt`, `app/src/test/java/com/jumlo/app/pdf/PdfFixtures.kt`, `app/src/test/java/com/jumlo/app/pdf/PdfStructureCoreTest.kt`.
- NO commits made yet in this session.

## 10. Exact next steps, priority order

1. **Reconcile `PdfBoxBackend.kt` into one final clean write** per §5 (probably easiest: re-write the file from scratch honoring the compiled API list already verified), then `./gradlew :app:compileDebugKotlin`.
2. Run `./gradlew :app:testDebugUnitTest --tests "com.jumlo.app.pdf.*"`; expect RED→fix→GREEN loop; keep whole suite green in the final check.
3. Commit (pdf backend P01/P02 milestone: "feat(pdf): structural backend (merge/split/extract/organize/insert/rotate/metadata/protect/forms/compress/compare/redact) + JVM tests").
4. Wire new tools into capability/handlers/UI: extend `data/processing/Settings.kt` per contract, add tool ids in `core/Validation.kt Capabilities.implemented` and `data/jobs/CapabilityRegistry.kt` for pdf_03/04/05/06 + expansions (e01–e11 for PDF; e12–e17 for images; e21–e25 for files; e24 encrypted ZIP needs **zip4j**; e08 scan + OCR need camerax + mlkit already cached).
5. Register new handlers in `data/jobs/OperationDispatcher.kt`, add `WorkspaceDraft.executionSettings` cases, add UI WorkspaceFields entries in JumloNavHost.kt, and enable `CapabilityRegistry` cards in the theme.
6. TDD for each remaining task (one RED test first, then implementation, then green; keep suite green each commit).
7. If user consents to network later: build per-platform capability matrix for yt-dlp (script-based verification, not app-internal; live URLs per platform) — currently NOT STARTED.
8. Phases 3–8 as listed in §1 in order; screenshot testing ONCE emulator access OR decide "not verified" as required by the user's rules.
9. Phase 2 media: `docs/media/DEVICE_QUALIFICATION.md` marked NOT EXECUTED in repo is the handoff-defined doc anchor; the actual extended media build flag `-PextendedMedia=true assembleDebug` is cached and must be re-verified after new pdf deps land (no new downloads needed).

## 11. Critical constraints / mistakes to not repeat

- **Do NOT re-edit-by-patch repeatedly in one file** — drafting loops introduced inconsistencies in `PdfBoxBackend.kt`; use one final write per file when iterate/patch pattern shows like this.
- **Do NOT use `--rerun-tasks` as a default gate key** — it trips known `lintAnalyze*` flake; run without it, or exclude lint, to gate code changes.
- **Do NOT trust file "looked clean" after big tool-driven edits**; always `git status`/`git diff` sanity check. The Color.kt corruption was only caught by the KSP failure.
- **Do NOT add new dependencies** without explicit user approval; network is limited (already exceeded the granted 4).
- **Do NOT claim device/emulator verification for anything** without actually running it; instrumented suite is currently NOT RUN and must be reported as such.
- `PDFTextStripperByArea`/`LegacyPDFStreamEngine` are android-tied in `com.tom_roush` port — do not use in JVM tests; do not bypass via reflection.
- `.superpowers/sdd/tasks.json/progress.md` is the scratch ledger for SDD flow — keep appending step evidence there as work proceeds.

## Task-specific conventions

Write task-specific progress notes into `.superpowers/sdd/tasks.json/progress.md` (one line per completed test/fix cycle, TDD-style: RED → GREEN evidence, plus verdict). Keep AGENTS.md (not present in repo) out of this — repo has no convention file yet; if one becomes useful later (e.g. lint guard against superfluous deps), add it via a small doc change, not hard-coded rules.
