# AUDIT_2 — Jumlo Phase 0 production audit (2026-10-28)

**Source:** `mo7medSa3d/Jumlo`, `main` at `d599b8a3159f38623e3d0b83c3d58a23073e70cf`. **Verdict: NOT PRODUCTION-QUALIFIED.** This audit was written before any application source changes in the new work-package sequence. A wired handler does not prove a working on-device Pick → Configure → Run → Result flow.

**Required documentation consulted:** README.md, TEST_REPORT.md, AUDIT.md, ROUTES.md, TOOL_UI_SPEC.md, ICON_MAP.md, DECISIONS.md, CHANGELOG.md, IMPLEMENTATION_PLAN.md, RELEASE_CHECKLIST.md. Source-of-truth for tool IDs is `CapabilityRegistry.kt` (44 `allTools` + the SHA-256 entry = **45**). `TOOL_UI_SPEC.md` and `ICON_MAP.md` still describe **47**, predating two registry removals. Evidence statuses below refer to current files; older TEST_REPORT.md reports host suites from 2026-10-24, not executed today.

**Verification baseline:** GitHub Actions [run #96](https://github.com/mo7medSa3d/Jumlo/actions/runs/40106249318) at main SHA above: conclusion SUCCESS on 2026-10-24. It calls `:app:assembleDebug :app:verifyCompactApkSize :app:testDebugUnitTest :app:lintDebug`, **not** `:app:lintRelease`. The size task currently prints bytes but has **no limit**. Current full Android/device qualification, all-tool UI flows, signing and real-service checks remain **NOT RUN / BLOCKED**; do not infer passes from documented test names or old artifacts.

## 1. Complete current tool inventory (45)

**Status key:** `partial` means the registry exposes a source implementation or navigation route but its full real-device behavior has not been accepted under current criteria. It does **not** assert the implementation is absent or broken. `broken` is reserved for a demonstrated source contradiction/failing behavior. No tool can be marked `works` until test execution, output inspection and resulting CI evidence have been recorded.

| Name | Status | Evidence (file + line or test) | Fix / acceptance plan |
|---|---|---|---|
| `pdf_to_docx` — PDF to Word | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:9`; PdfOfficeOperationHandler (OperationDispatcher.kt:36) | Verify editable paragraphs, embedded Arabic RTL, blank/OCR fallback, and DOCX reopening in Word. |
| `pdf_to_pptx` — PDF to PowerPoint | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:10`; PdfOfficeOperationHandler (OperationDispatcher.kt:36) | Verify rendered-page image plus text layer on each slide; open with PowerPoint. |
| `pdf_02` — Images to PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:11`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify ordered images, page sizes, alpha and multi-page PDF output. |
| `pdf_03` — Merge PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:12`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify input order, page count and unchanged source hashes; instrument drag reorder. |
| `pdf_05` — Split PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:13`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify custom/interval splits, boundaries and all reopened outputs. |
| `pdf_04` — Organize Pages | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:14`; PdfOperationHandler (OperationDispatcher.kt:37) | Test reorder/rotate/delete/undo on visual grid and real output. |
| `pdf_06` — Extract Pages | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:15`; PdfOperationHandler (OperationDispatcher.kt:37) | Test page-range extraction and malformed/out-of-range selections. |
| `pdf_07` — PDF to Images | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:16`; PdfOperationHandler (OperationDispatcher.kt:37) | Test 200-page conversion with bounded memory; validate PNG/JPG output. |
| `pdf_01` — View PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:17`; navigation (ROUTES.md:32–40) | Test thumbnails, smooth zoom, search, night mode and position restore on large PDF. |
| `pdf_compress` — Compress PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:18`; PdfOperationHandler (OperationDispatcher.kt:37) | Run all five scan fixtures on a device; compare before/after bytes and 3 quality levels. |
| `pdf_crop` — Crop PDF Pages | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:19`; PdfOperationHandler (OperationDispatcher.kt:37) | Pixel-verify per-page crop rectangles across rotated pages. |
| `pdf_annotate` — Annotate PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:20`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify saved annotation geometry, edit gestures, reopen in independent reader. |
| `pdf_stamp` — Watermark & Page Numbers | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:21`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify text/image watermark positions, opacity and page numbers. |
| `pdf_signature` — Sign PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:22`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify PNG alpha, scaled/rotated placement and multi-page persistence. |
| `pdf_image` — Add Image to PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:23`; PdfOperationHandler (OperationDispatcher.kt:37) | Test imported logo/image position and size, including RTL gestures. |
| `pdf_protect` — Password Protect PDF | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:24`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify AES round trip, wrong password, unlock, permission flags and corrupt files. |
| `pdf_metadata` — PDF Metadata | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:25`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify edits with independent metadata parser and retained source. |
| `pdf_clean` — Clean Metadata | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:26`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify removal of info dictionary and XMP; reopen output. |
| `pdf_forms` — Fill PDF Form | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:27`; PdfOperationHandler (OperationDispatcher.kt:37) | Verify real AcroForm values/appearance, flattening and invalid fields. |
| `pdf_ocr` — Extract Text | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:28`; OcrWebHandler (OperationDispatcher.kt:38) | Limit claims to supported language; test Latin OCR and clear scanned-Arabic unsupported messaging. |
| `scan_doc` — Smart Scan | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:29`; navigation (ROUTES.md:32–40) | Test ML Kit and CameraX fallback, capture/edge detection, multipage export, denial and relaunch. |
| `img_01` — Convert Images | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:31`; ImageOperationHandler (OperationDispatcher.kt:40) | Verify output format/quality, alpha/EXIF and actual encoded bytes. |
| `img_02` — Resize Images | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:32`; ImageOperationHandler (OperationDispatcher.kt:40) | Verify resize geometry, upscaling policy and pixels. |
| `img_03` — Crop, Rotate & Flip | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:33`; ImageOperationHandler (OperationDispatcher.kt:40) | Test rotate/flip/crop combos, EXIF normalization and visual handles. |
| `img_04` — Compress Images | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:34`; ImageOperationHandler (OperationDispatcher.kt:40) | Verify compression ladder and before/after output bytes. |
| `img_exif` — EXIF Inspector | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:35`; ImageOperationHandler (OperationDispatcher.kt:40) | Confirm full advertised EXIF coverage and round-trip metadata removal. |
| `img_watermark` — Watermark Images | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:36`; ImageOperationHandler (OperationDispatcher.kt:40) | Pixel-test opacity/drag/scale and resulting saved images. |
| `img_join` — Join / Collage | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:37`; ImageOperationHandler (OperationDispatcher.kt:40) | Verify grid/strip order, slot drag, dimensions and memory budgets. |
| `img_adjust` — Adjust & Pad | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:38`; ImageOperationHandler (OperationDispatcher.kt:40) | Pixel-test brightness/contrast/saturation/padding on output. |
| `img_target` — Target Size | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:39`; ImageOperationHandler (OperationDispatcher.kt:40) | Prove size-target result and decide whether it belongs inside Compress Images. |
| `img_compare` — Compare Images | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:40`; ImageOperationHandler (OperationDispatcher.kt:40) | Verify actual before/after comparison and correct RTL clipping; review prune candidate. |
| `img_stitch` — Stitch Screenshots | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:41`; ImageOperationHandler (OperationDispatcher.kt:40) | Verify overlap/collage output and document retention/prune decision. |
| `dl_media` — Video, Audio & File Downloads | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:44`; MediaOperationHandler (OperationDispatcher.kt:43) | Test authorized live platform URLs, automatic best media/audio mode, pause/resume and Android 8–15 notification. |
| `dl_02` — New Direct Download | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:45`; DownloadOperationHandler (OperationDispatcher.kt:42) | Test direct/Drive transfer including Range, 403, expired link and folder recovery; consider merging intake. |
| `dl_03` — Batch URL Intake | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:46`; DownloadOperationHandler (OperationDispatcher.kt:42) | Test dedupe/invalid URLs, batch scheduling, cancellation; consider merging intake. |
| `dl_settings` — Download Settings | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:47`; navigation (ROUTES.md:32–40) | Verify navigation opens real downloader defaults; remove duplicate alias only with approved prune plan. |
| `file_02` — Browse Storage | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:50`; navigation (ROUTES.md:32–40) | Verify Files navigation/SAF grants, sorting/filtering/multi-select; prune duplicate alias only after review. |
| `file_07` — Create ZIP Archive | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:51`; ArchiveOperationHandler (OperationDispatcher.kt:41) | Validate ZIP content, traversal-safe paths, collisions and large-file streaming. |
| `file_08` — Extract ZIP Archive | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:52`; ArchiveOperationHandler (OperationDispatcher.kt:41) | Validate extraction against ZIP slip/zip bombs and remove partials on cancellation. |
| `file_encrypted_zip` — Encrypted ZIP | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:53`; FileExtrasHandler (OperationDispatcher.kt:39) | Verify AES create/extract, tamper rejection, wrong password and safe cleanup. |
| `file_batch_rename` — Batch Rename (copies) | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:54`; FileExtrasHandler (OperationDispatcher.kt:39) | Verify preview names match published copies; collisions and Unicode. |
| `file_duplicates` — Duplicate Finder | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:55`; FileExtrasHandler (OperationDispatcher.kt:39) | Test streamed byte hash groups/cancellation and non-destructive action labels. |
| `file_trash` — Managed Trash Info | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:56`; navigation (ROUTES.md:32–40) | Verify Files-managed trash restoration/purge; remove redundant alias only after review. |
| `file_drive` — Google Drive Import / Export | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:57`; FileExtrasHandler (OperationDispatcher.kt:39) | Requires a real authorized Drive OAuth account; test token expiry, upload, download and privacy. |
| `sha256` — SHA-256 verification | partial (source present; end-to-end not reverified) | `CapabilityRegistry.kt:59`; ArchiveOperationHandler (OperationDispatcher.kt:41) | Verify SHA-256 against known fixtures and invalid expected hashes; review prune decision. |

All tool handler routing: `app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt:35–44`, navigation-only IDs: `core-common/src/main/kotlin/com/jumlo/app/core/CapabilityPolicy.kt:21–46`, and the tool-specific source UI contract: `TOOL_UI_SPEC.md:45–495`. **Every row must be updated with a concrete on-device/JVM test name and result before promotion to works.**

## 2. Screen and route inventory (14 destinations + UI subflows)

| Screen / route | Status | Evidence | Fix / acceptance plan |
|---|---|---|---|
| `HomeRoute` — HomeScreen | partial (device flow not reverified) | `ROUTES.md:27`; `ui/navigation/JumloNavHost.kt` | Confirm fresh launch, onboarding, active jobs, tab restoration |
| `ToolsRoute` — ToolsScreen | partial (device flow not reverified) | `ROUTES.md:28`; `ui/navigation/JumloNavHost.kt` | Confirm 45 tools discoverable, category grouping, usage order and EN/AR |
| `FilesRoute` — FilesScreen | partial (device flow not reverified) | `ROUTES.md:29`; `ui/navigation/JumloNavHost.kt` | Confirm file actions, permissions, trash, and storage usage changes |
| `DownloadsRoute` — DownloadsScreen | partial (device flow not reverified) | `ROUTES.md:30`; `ui/navigation/JumloNavHost.kt` | Verify historical/current jobs and real progress/control buttons |
| `SettingsRoute` — SettingsScreen | partial (device flow not reverified) | `ROUTES.md:31`; `ui/navigation/JumloNavHost.kt` | Verify all visible preferences; fix custom hex and expose requested motion/haptics |
| `ToolRoute` — WorkspaceScreen / ViewerWorkspace / MediaWorkspace | partial (device flow not reverified) | `ROUTES.md:32`; `ui/navigation/JumloNavHost.kt` | Test fresh pick→configure→run→result for every processing tool and saved draft |
| `ScanRoute` — ScanScreen / ML Kit | partial (device flow not reverified) | `ROUTES.md:33`; `ui/navigation/JumloNavHost.kt` | Test real camera/permissions/output and fallback |
| `PrintRoute` — PrintScreen | partial (device flow not reverified) | `ROUTES.md:34`; `ui/navigation/JumloNavHost.kt` | Verify Android print adapter preview/range/copies/orientation |
| `ViewerRoute` — PdfViewerScreen | partial (device flow not reverified) | `ROUTES.md:35`; `ui/navigation/JumloNavHost.kt` | Verify 200-page paging/zoom/search/night/position |
| `JobRoute` — ActivityScreen | partial (device flow not reverified) | `ROUTES.md:36`; `ui/navigation/JumloNavHost.kt` | Verify job links and completion/error states |
| `ActivityRoute` — ActivityScreen | partial (device flow not reverified) | `ROUTES.md:37`; `ui/navigation/JumloNavHost.kt` | Verify filtering and status restoration |
| `SettingsDetailRoute` — SettingsScreen | partial (device flow not reverified) | `ROUTES.md:38`; `ui/navigation/JumloNavHost.kt` | Verify Back returns to correct caller |
| `StorageRoute` — StorageScreen | partial (device flow not reverified) | `ROUTES.md:39`; `ui/navigation/JumloNavHost.kt` | Verify totals and cleanup reflect actual files |
| `WorkspaceRoute` — Compatibility dispatch | partial (device flow not reverified) | `ROUTES.md:40`; `ui/navigation/JumloNavHost.kt` | Verify cold/warm jumlo://workspace intent and restored origin |
| Onboarding/guide modal | partial | `ui/components/OnboardingGuide.kt`; `ROUTES.md:92–94` | Test complete, dismiss, reopen, rotation, EN/AR |
| CameraX scan fallback | partial | `ui/scan/CameraXScanFallback.kt`; `ROUTES.md:65` | Verify photo capture, edges, gallery import, deny/retry, PDF/JPEG |
| File pick/share/drag intake | partial | `ui/intake/IntakeController.kt`; `ROUTES.md:76–88` | Test multi-pick, cross-app drag grants, malformed/expired URIs |
| Page editor and watermark/signature studios | partial | `TOOL_UI_SPEC.md:28–36`; `ui/workspace/` | Exercise gestures with real output and undo/redo; use screenshot/test evidence |

**Navigation disagreement:** current `ROUTES.md:7–19` defines **five** bottom tabs (Tools · Files · Home · Downloads · Settings), while the requested Phase 3 target calls for **four** (Home · Tools · Files · Settings). Consolidating Downloads requires a design/migration with regression tests, not a blind deletion.

## 3. Every current visible setting / settings requirement

| Setting | Status | Evidence | Fix / acceptance plan |
|---|---|---|---|
| Theme / AMOLED | partial | `SettingsScreen.kt:57–58,102–103` | Persist/restart, real light/dark previews and contrast |
| Dynamic color | partial | `SettingsScreen.kt:59–67` | Verify Android 12+ device changes real scheme |
| Custom accent picker | broken | `AppearanceColorPicker.kt:45–135; UserPreferencesRepository.kt:262–265` | Allow validated #RRGGBB; reject invalid input safely; add persistence test |
| Language EN/AR | partial | `SettingsScreen.kt:71–72,109–110` | Persist, RTL and translated labels on every route |
| Save folder / reset | partial | `SettingsScreen.kt:73–81` | SAF persisted permission, revoked grant, storage flow |
| Video default quality | partial | `SettingsScreen.kt:82–85,111–117` | Verify downloader actually applies default and auto-best |
| Audio default quality | partial | `SettingsScreen.kt:86–88,118–123` | Verify audio-only bitrate default honored |
| Storage usage | partial | `SettingsScreen.kt:91–95` | Measure before/after deletion and refresh |
| Clear cache | partial | `SettingsScreen.kt:96–97,135–140` | Verify only temporary files removed |
| About / guide / privacy / export logs | partial | `SettingsScreen.kt:98–99,126–134` | Exercise all sub-actions and verify privacy claims |
| Haptics control | stub (not exposed) | `UserPreferencesRepository.kt:226–236; SettingsScreen.kt:56–100` | Expose real toggle/intensity and honor throughout app |
| Reduce-motion control | broken (not exposed / forced off) | `UserPreferencesRepository.kt:143,222–224` | Expose toggle, read persisted state, respect OS setting |
| Legacy Wi-Fi-only toggle | not exposed (cleanup) | `UserPreferencesRepository.kt:62,152,274–276` | Keep hidden or remove legacy setter after migration; no user-facing no-op |

## 4. Confirmed defects and cross-artifact contradictions

| Priority | Issue | Reproducible source evidence | Corrective work package |
|---|---|---|---|
| P0 | **60 MB size protection removed**: `verifyCompactApkSize` and `verifyReleaseApkSize` no longer assert a byte bound. | `app/build.gradle.kts:314–344`; `DECISIONS.md:12–13`; README.md:27 and RELEASE_CHECKLIST.md:10 still promise the cap. | WP01: restore strict decimal byte size checks, both full-media gates, test without trimming features. |
| P0 | **CI release lint gap**: main builds and uses `lintDebug` but user's gate requires `lintRelease`. | `.github/workflows/ci.yml:68–75` | WP01: add release lint and publish package tests; verify PR checks. |
| P1 | **Custom color cannot persist:** UI emits a hex seed but repository guard accepts named palette values only, despite `isValidAccent` helper existing. | `ui/settings/AppearanceColorPicker.kt:45–135`; `data/preferences/UserPreferencesRepository.kt:262–265,289–293` | WP02: use validation helper; test accept/reject and DataStore round-trip. |
| P1 | **Reduce motion persistence is disconnected:** the setter writes a DataStore key while the read projection forces `false`; no visible control. | `UserPreferencesRepository.kt:143,222–224`; `SettingsScreen.kt:56–100` | WP02: make preference round-trip, compose toggle, OS reduced motion override and tests. |
| P1 | **Haptics not configurable in UI** although repository holds haptic preference/levels and `AppHaptics` exists. | `SettingsScreen.kt:56–100`; `UserPreferencesRepository.kt:144–146,226–236` | WP02: add haptic preference and tests; don't show dead options. |
| P1 | **Documentation inventory drift 47→45** and contradictory 60 MB statements. | `TOOL_UI_SPEC.md:5`; `ICON_MAP.md:3`; `CapabilityRegistry.kt:7–59` | WP01/docs: synchronize catalog, history and release checklists. |
| P1 | **Unqualified live providers, scanned Arabic and 200-page performance**: current source cannot establish real-platform availability or on-device memory. | `TEST_REPORT.md:76–104`; `README.md:64,70` | WP03–WP07: device/authorized URL tests, clear capability messages, performance measurements. |
| P2 | **Five vs four root tabs**; alternative information architecture requested. | `ROUTES.md:7–19`; `ui/navigation/JumloNavHost.kt:122–164` | WP08: redesign and test back stack/RTL instead of removing Downloads without migration. |
| P2 | **Literal color use outside semantic theme** (scanner/signature, appearance fallback, viewer UI) needs review. | `ui/scan/ScanScreen.kt:95,99,152`; `ui/settings/AppearanceColorPicker.kt:68`; `ui/theme/Color.kt:14–85` | WP08: replace UI-only magic colors with token roles; preserve content-ink/paper pixels. |

## 5. TODO, stubs, no-op controls, raw text and direct color literals — source review

Known source-level defects are registered above. `core-common/CapabilityPolicy.kt:41–46` rejects unregistered processing capabilities, while navigation aliases are deliberately not processing jobs. The legacy `UserPreferencesRepository.kt:270–276` setters persist concurrency and Wi-Fi-only fields that `settingsFlow` deliberately ignores (`:149,152`); they are **not current visible settings** but should be removed after persistence migration/code-reference review. `AppearanceColorPicker.kt:68` and `ScanScreen.kt:95,99,152` contain direct colors. Token declarations in `Color.kt:14–85,125–189` and `ToolPalette.kt:13–26` are intentional centralized constants, not errors in themselves. Current UI hardcoded strings also include `PdfViewerScreen.kt:394,436` (numeric UI), `MediaWorkspace.kt:400` (`kbps` suffix), and `ToolsScreen.kt:53–68` bilingual section titles rather than resources.

**Important search limit:** this is a hand-verified source review, not a certified exhaustive static scan of all 796 tracked blobs. The exact `TODO`, `FIXME`, empty onClick, literal UI strings and color inventory must be expanded by an automated full-tree scan (and dead-control behavior by device tests). No “zero TODOs” or “no dead buttons” conclusion is justified before those checks.

## 6. Existing test coverage vs missing acceptance

The repository has unit and instrumentation test source (examples `app/src/test/java/com/jumlo/app/pdf/`, `app/src/androidTest/java/com/jumlo/app/ui/`, `app/src/androidTest/java/com/jumlo/app/data/PdfHandlerReliabilityTest.kt`, `app/src/androidTest/java/com/jumlo/app/pdf/PdfCompressionDeviceTest.kt`). Presence is not a passing execution. Five required scan PDFs: `scan_01pages.pdf`, `scan_03pages.pdf`, `scan_06pages.pdf`, `scan_10pages.pdf`, `scan_30pages.pdf` (README.md:18). Require exact fixture hashes, all three quality levels, output reopen, byte reduction and no OOM before acceptance. Documented run #96 verifies a previous CI gate, not the new acceptance criteria. Physical device, authorized Google Drive/OAuth, live social links, and owner release signing are still blocked or not-run.

## 7. Next work packages (separate branches and PRs)

1. `wp/01-restore-release-gates`: fix size assertions, CI lintRelease, release-report/documentation discrepancies; no main merge until green.
2. `wp/02-real-settings`: repair custom accent, expose/verify haptics and reduced motion, persisted preferences, UI tests.
3. `wp/03-downloader-reliability`: links, automatic default, audio, progress/cancel/retry and authorized provider/device matrix.
4. `wp/04-document-engine`: every PDF/Office flow, five scanned compression fixtures, isolated drafts, output verification.
5. `wp/05-scanner-viewer-print`: edge/perspective/export/OCR, immersive reader and Android print flows.
6. `wp/06-files-and-output`: SAF/MediaStore, storage totals, selection/rename/delete/share and publication failure recovery.
7. `wp/07-shared-tool-ux`: universal pick/configure/progress/result, accurate previews, gestures, understandable errors.
8. `wp/08-design-accessibility`: bold token palette, four-tab navigation, typography/RTL/contrast/motion, icon consistency.
9. `wp/09-release-qualification`: full host+CI+device matrix, screenshots, 200-page benchmarks, Play/privacy/signing risk report.

**Do not mark any tool complete merely to reduce partial rows.** Close each only with test IDs, run links and real output inspection; record remaining blockers in TEST_REPORT.md.
