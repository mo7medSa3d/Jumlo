# Jumlo Phases 1–4: Evidence-Driven Release Qualification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans task-by-task. Checklist items are **not completed** by code presence or a successful smoke run. This supersedes the 2026-10-23 historical plan, which names relocated files and contradicts the current four-tab, haptics and reduce-motion decisions.

**Goal:** Qualify all 45 registered Jumlo tools and every user setting for an owner-signed, fully tested release without deleting working features as a shortcut.

**Architecture:** Keep the current six modules and their Android-free `core-common`, `downloader-core`, `document-core`, and `storage-core` boundaries. Retain `OperationDispatcher.execute(JobRequest, ExecutionContext): List<ItemOutcome>` and the Compose shared workspace instead of creating parallel engines/screens. Device/runner acceptance uses the existing `docs/testing/tool-matrix.template.csv`, with output hashes and independently reopened files.

**Tech Stack:** Current repository Kotlin/Compose/Material 3, Navigation, Hilt, Room, DataStore, WorkManager, PDFBox, ML Kit, Gradle 8.14 / AGP 8.10.1 / SDK 36 / minSdk 26.

**Spec:** `PRODUCTION_BRIEF.md`, `docs/AUDIT_2.md`, `TOOL_UI_SPEC.md`, `DECISIONS.md`, `RELEASE_CHECKLIST.md`, and the user’s 2026-10-31 Phase 0–4 requirements.

## Global constraints

- Commit **directly to `main`**; never create branches, PRs, issues or drafts, force-push, rebase pushed commits or weaken CI checks.
- **Before each product-code push**, execute and inspect `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease`, plus affected modules and `:app:verifyCompactApkSize :app:verifyReleaseApkSize` when applicable. A remote successful run on a *different* commit is not pre-push verification.
- If an authorized build environment is unavailable, **do not push untested product code**; log the specific blocker and continue source analysis/qualification planning. Do not substitute a GitHub Actions result *after* a push for the explicitly required pre-push gate.
- After **every** push, confirm both GitHub Actions workflows on that exact SHA conclude `success` before any new modification.
- Preserve **four** bottom tabs (Home, Tools, Files, Settings), with Downloads manager as navigable detail; retain bilingual RTL, real haptic intensity and reduce-motion setting. The old plan's five-tab and remove-haptics instructions are obsolete.
- All 45 currently registered IDs remain covered: 40 processing × 4 normal/large/encrypted/corrupt cases = **160** required output cases, five navigation aliases × 4 = **20 N/A as jobs** but require their own route acceptance. Prune only after user-level rationale and `DECISIONS.md` entry.
- Full ARM64 APK size cap: **60,000,000 bytes** with complete local media runtime. The compact build uses a **test** key; only owner-verified signing lineage qualifies release distribution.
- Every committed code change is TDD: named failing regression → observed failure → minimal fix → passing relevant test and complete gate; include docs/progress and the test result in the same logical commit.

## Review focus (tests must explicitly include these boundary inputs)

1. A workspace is reopened after Back/recreation with an invalid draft or revoked URI: show a localized actionable error and never operate on a previous tool’s input (Task 5).
2. Downloader redirects cross-origin after customized headers/cookies: never expose credentials or retry auth/rate-denied sources via unsafe transport (Task 1).
3. A public-output destination loses its SAF grant or runs out of space halfway: never claim a successful public save; retain/retry private verified output (Task 4).
4. 200-page scanned PDF with a near-limit scratch budget: no OOM or incomplete published output and measurable cleanup (Task 2).
5. Audio-only input or video with separate audio tracks: saved result is playable, has the expected tracks, clean filename and resumable job semantics (Task 1).

---

### Task 0 — Pre-push verification environment (P0 blocker)

**Files:** `README.md`, `.github/workflows/ci.yml`, `scripts/run-emulator-smoke.sh`, `TEST_REPORT.md`, `docs/PROGRESS.md`.

**Interfaces:** Existing Gradle build and test tasks, **not** a new app interface.

- [ ] **Step 1:** Obtain an actual checkout with JDK17, Python3, Android SDK36/Build Tools 36, Gradle dependencies and emulator/device access.
- [ ] **Step 2:** Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease`; preserve full logs, exit codes, source SHA and Android SDK identity.
- [ ] **Step 3:** Run `./gradlew :app:verifyCompactApkSize :app:verifyReleaseApkSize` and verify both signed/unsigned variant and full-media claims.
- [ ] **Step 4:** Inspect 14 selected smoke classes and run the **unfiltered** connected suite after the five-scan fixture preflight. Record failures/skips and output hashes, not an inferred pass.
- [ ] **Step 5:** Record environment-specific blockers in `TEST_REPORT.md` if the gate is impossible; do not push new product code.

### Task 1 — Downloader auto-detection, best choice, foreground and recovery

**Files:** `downloader-core/src/main/kotlin/com/jumlo/app/core/DownloadLinkPolicy.kt`, `downloader-core/src/main/kotlin/com/jumlo/app/core/media/MediaPolicies.kt`, `downloader-core/src/main/kotlin/com/jumlo/app/core/media/MediaFailurePolicy.kt`, `app/src/main/java/com/jumlo/app/ui/downloads/MediaViewModel.kt`, `app/src/main/java/com/jumlo/app/data/media/MediaOperationHandler.kt`, `app/src/main/java/com/jumlo/app/data/processing/DownloadOperationHandler.kt`, `app/src/main/java/com/jumlo/app/worker/ProcessingNotification.kt`.

**Tests:** `app/src/test/java/com/jumlo/app/core/MediaLinkRoutingTest.kt`, `MediaFailurePolicyTest.kt`, `MediaBestSelectionTest.kt`, `StreamTransferMidstreamRetryTest.kt`; device `app/src/androidTest/java/com/jumlo/app/media/PackagedMediaRuntimeDeviceTest.kt` and `app/src/androidTest/java/com/jumlo/app/worker/ProcessingNotificationTest.kt`.

**Interfaces:** Consume existing `DownloadLinkPolicy.prefersMediaAnalysis(url)`, `MediaCatalog.preferred(choices,mode,maxHeight)`; preserve `MediaViewModel.analyze(...)` and `submit(...)`.

- [ ] **Step 1:** Write a failing test for each confirmed provider-routing/automatic BEST/audio-only/filename failure and error taxonomy case; verify the failure is meaningful.
- [ ] **Step 2:** Repair only demonstrated defects; retain Advanced customization optional, not default.
- [ ] **Step 3:** Re-run JVM tests plus full Gradle pre-push gate; execute authorized provider video/audio/private/geo/expired/source-changed matrix for YouTube, TikTok, Instagram, Facebook, LinkedIn, Drive, direct and generic pages.
- [ ] **Step 4:** Execute pause/resume/retry/backoff/process death/dataSync foreground notification tests across supported API versions; inspect actual output tracks/hashes.
- [ ] **Step 5:** Commit fix + `CHANGELOG.md`, `TEST_REPORT.md`, `TOOL_UI_SPEC.md`, `docs/PROGRESS.md` after gates pass; push directly to `main`, wait for both Actions runs.

### Task 2 — PDF operations and measured compression

**Files:** `app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt`, `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt`, `PdfImageCompressor.kt`, `PdfCompressionRunner.kt`, `app/src/main/java/com/jumlo/app/ui/workspace/PdfPageEditor.kt`.

**Tests:** `app/src/test/java/com/jumlo/app/pdf/PdfStructureCoreTest.kt`, `PdfSecurityQualificationTest.kt`, `PdfMetadataCleanerTest.kt`, `PdfOverlayCoordinatesTest.kt`; device `app/src/androidTest/java/com/jumlo/app/data/PdfHandlerReliabilityTest.kt` and `app/src/androidTest/java/com/jumlo/app/pdf/PdfCompressionDeviceTest.kt`.

**Interfaces:** Preserve `OperationHandler.execute(request,context)`; output `ItemOutcome` with verified publication status.

- [ ] **Step 1:** Build per-ID fixture assertions for merge, split, extract, rotate, reorder, annotate, protect, watermark, sign, compress and page/image conversions.
- [ ] **Step 2:** Run the five exact scan PDFs from `README.md`; record original/output bytes, quality preset, page count, reopened validity, percent reduction, and no false savings for incompressible inputs.
- [ ] **Step 3:** Reproduce 200-page memory/low-space/corrupt/encrypted failures; write a failing regression for each proven defect and fix minimally.
- [ ] **Step 4:** Re-run pre-push Gradle/size gates, commit evidence and changes, push to `main`; wait for Actions green.

### Task 3 — Office conversion, scanner and OCR

**Files:** `app/src/main/java/com/jumlo/app/data/processing/PdfOfficeOperationHandler.kt`, `OcrWebHandler.kt`, `app/src/main/java/com/jumlo/app/data/ocr/MlKitOcrEngine.kt`, `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt`, `SmartDocumentScanButton.kt`, `CameraXScanFallback.kt`, `document-core/src/main/kotlin/com/jumlo/app/core/office/OfficePackageWriter.kt`.

**Tests:** `app/src/officeTest/kotlin/OfficePackageHostTests.kt`; `app/src/androidTest/java/com/jumlo/app/data/PdfOfficeHandlerTest.kt`, `NativeOcrCancellationTest.kt`, `PdfReaderAndScanStoreTest.kt`, `app/src/androidTest/java/com/jumlo/app/ui/scan/ScanPreviewBitmapOwnershipTest.kt`.

**Interfaces:** Existing JobRequest/ItemOutcome and OCR failure vocabulary; no new Android imports in document-core.

- [ ] **Step 1:** Verify real DOCX/PPTX output reopens in independent parsers and compatible Office viewers; preserve Arabic Unicode and disclose layout limits.
- [ ] **Step 2:** Verify CameraX fallback, edge/perspective, multipage, gallery, filters and PDF/JPG exports with actual camera and denied permission cases.
- [ ] **Step 3:** Verify supported Latin OCR and explicit unsupported scanned-Arabic notice; do not silently claim Arabic recognition.
- [ ] **Step 4:** Write/red-run missing regressions, fix confirmed defects, run full pre-push gate, commit/CI per policy.

### Task 4 — Viewer, Files, Printing and storage integrity

**Files:** `app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt`, `app/src/main/java/com/jumlo/app/data/pdf/ReaderPositionStore.kt`, `app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt`, `app/src/main/java/com/jumlo/app/data/files/DocumentTreeStore.kt`, `app/src/main/java/com/jumlo/app/data/output/PublicOutputStore.kt`, `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt`, `PrintViewModel.kt`, `JumloPrintAdapter.kt`.

**Tests:** `app/src/androidTest/java/com/jumlo/app/ui/PdfViewerInteractionTest.kt`, `ProviderFilesInteractionTest.kt`, `app/src/androidTest/java/com/jumlo/app/data/PublicSaveRecoveryTest.kt`, `ReaderPositionStoreTest.kt`, `app/src/androidTest/java/com/jumlo/app/printing/JumloPrintAdapterTest.kt` and `PrintPageManagerInteractionTest.kt`.

**Interfaces:** Existing SAF URI permission model, private/public publication distinction, Android `PrintDocumentAdapter`.

- [ ] **Step 1:** Reproduce remembered PDF position, zoom, thumbnails, search, night mode and 200-page memory paths with populated test documents.
- [ ] **Step 2:** Validate visible save-folder, sort/search/filter, multi-select, rename/delete all, share/open, real storage totals and revoke-grant retry.
- [ ] **Step 3:** Inspect actual Android print preview, selected page ranges, orientation and copies on a real print provider.
- [ ] **Step 4:** Add failing regressions before repair; run Gradle verification, commit and wait for exact-head Actions green.

### Task 5 — Shared Compose pick/configure/run/result interaction

**Files:** `app/src/main/java/com/jumlo/app/ui/components/ToolScaffold.kt`, `ui/components/DropFilesTarget.kt`, `ui/workspace/WorkspaceScreen.kt`, `ui/workspace/SourceOrderStrip.kt`, `ui/workspace/SignaturePlacementEditor.kt`; `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt`.

**Test create:** `app/src/androidTest/java/com/jumlo/app/ui/ToolPickRunResultTest.kt`.

**Interfaces:** Reuse current `AppViewModel.chooseTool(id)`, job state and `ToolResultAction` rather than separate local job engines.

- [ ] **Step 1:** Add five real Compose end-to-end tests for `pdf_03`, `pdf_05`, `pdf_compress`, `img_02`, and `file_07`, each selecting fixture → configuring → running → checking published output → Open/Share/Save/Use-in-another-tool.
- [ ] **Step 2:** Assert fresh tool isolation, drag reorder and shared intents/recent-file intake, cancellable honest progress and actionable localized errors.
- [ ] **Step 3:** Fix only failing interactions using reusable components, not copies; run complete gates, update routes/spec/CI evidence, commit and wait green.

### Task 6 — Material 3 design system, settings, language and accessibility

**Files:** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt`, `Theme.kt`, `JumloMotion.kt`, `ui/theme/ColorPresets.kt`, `ui/settings/SettingsScreen.kt`, `AppearanceColorPicker.kt`, `ui/components/JumloSplash.kt`, `app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt`, `ui/navigation/JumloNavHost.kt`.

**Tests:** `app/src/test/java/com/jumlo/app/ui/theme/M3ColorFactoryTest.kt`, `ToolPaletteContrastTest.kt`, `app/src/androidTest/java/com/jumlo/app/ui/ScreenConfigSemanticsTest.kt`, `FourTabNavigationTest.kt`, `StudioAccessibilityTest.kt` and `ui/screenshots/ToolScreenshotMatrixDeviceTest.kt`.

**Interfaces:** Existing `JumloTheme`, `UserPreferencesRepository` and four primary routes. Preserve current Jumlo Sans / Arabic assets.

- [ ] **Step 1:** Contrast-check all semantic light/dark/dynamic accent combinations at 4.5:1 normal text / 3:1 non-text; visually inspect RTL and 200% font screenshots.
- [ ] **Step 2:** Triage the 20 literal color source-scan candidates: centralize only UI chrome/splash tokens; preserve intentionally fixed signature ink and checkboard document pixels.
- [ ] **Step 3:** Verify settings persistence/effect for theme, language, default save folder, haptics, reduce-motion, sounds, quality and cache; no dead Wi-Fi-only HTTP/concurrency switches.
- [ ] **Step 4:** Run TDD for each defect, screenshot/contrast/device gates, verify main build and size, then commit + Actions green.

### Task 7 — Full release evidence, privacy and sign-off

**Files:** `docs/testing/tool-matrix.template.csv` (copy per device, never overwrite template as if executed), `PRIVACY_AND_PERMISSIONS.md`, `PRIVACY_POLICY.md`, `PLAY_STORE_LISTING.md`, `RELEASE_CHECKLIST.md`, `TEST_REPORT.md`, `docs/PROGRESS.md`, `docs/FINAL_SUMMARY.md`.

**Interfaces:** Recorded test evidence, authorized owner key, Play Console inputs; no secrets committed.

- [ ] **Step 1:** Execute and evidence all 160 processing rows; separately prove five navigation aliases. Include normal, large, encrypted, corrupt inputs, no false `PASS`.
- [ ] **Step 2:** Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease :app:verifyCompactApkSize :app:verifyReleaseApkSize :app:connectedDebugAndroidTest` with fixture preflight; run benchmarks, screenshots, Android 8/15/16 and physical-device checks.
- [ ] **Step 3:** Complete privacy and Play disclosures only from released SDKs/data flows and authorized provider/content-rights review; verify owner release certificate/upgrade on device and signing gates.
- [ ] **Step 4:** Ensure `AUDIT_2.md` has no unresolved broken/stub/partial rows **only where proven**; commit `docs/FINAL_SUMMARY.md` with all changes, exact evidence, and any remaining risks without claiming release readiness while blockers remain.

## Execution ledger and current blocker

At the current 2026-10-31 start, `main` `91253f59b0967b4989c05d91bf46c5b472ea87ae` is green with [Android CI #41144898783](https://github.com/mo7medSa3d/Jumlo/actions/runs/41144898783) and [emulator smoke #41144898786](https://github.com/mo7medSa3d/Jumlo/actions/runs/41144898786): **218 selected emulator test executions, 0 failure/error/skip** across API26/35/36. That is not full application acceptance. **No Kotlin/Java code can be pushed from the current API-only/no-Android-SDK environment without violating the owner's mandatory pre-push Gradle gate.** Continue read-only root-cause research and document evidence; when a verified Android build environment is available, execute Task 0 then tasks in priority order without restarting completed work.
