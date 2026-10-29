# Jumlo — AUDIT_2 (source evidence and release gap register)

## 2026-10-28 origin-scoping follow-up

User-provided downloader custom headers were at risk of being reused on redirected hosts. The pending WP11 branch adds exact scheme/host/effective-port matching, disabled cross-origin custom headers, and JVM redirect tests. Do not close the P0 risk or call it production verified until latest CI and actual redirect testing pass.

Date: 2026-10-28. Target repository: `mo7medSa3d/Jumlo`; baseline `main` tree/commit `d599b8a3159f38623e3d0b83c3d58a23073e70cf`.
**Verdict: NOT RELEASE QUALIFIED.** This is an audit, not a claim that implementations were executed on a device. Source inventory and historic test evidence are different from a freshly passing complete pick→result flow.

## Evidence rules and boundary

- The audit read README.md, TEST_REPORT.md, AUDIT.md, ROUTES.md, TOOL_UI_SPEC.md, ICON_MAP.md, DECISIONS.md, CHANGELOG.md, IMPLEMENTATION_PLAN.md and RELEASE_CHECKLIST.md before any source change.
- Main CI: [Android CI & APK run #40106249318](https://github.com/mo7medSa3d/Jumlo/actions/runs/40106249318), 2026-10-24, successful **debug** build + unit tests + lintDebug; instrumented jobs **skipped**. Build log printed **55,368,299 bytes** for the compact APK. There was no build or device run in this audit session.
- `partial`: implementation or route exists, but the required complete real interaction and output are not certified. `broken`: a directly provable contract violation. `stub`: a stored control/policy with no real effect. **No row is upgraded to works merely by source presence.**
- Source line references below refer to `main` as examined; CI/dependency changes can shift them. The registry is `app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt`, and the dispatch entry is `app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt:25-47`. Test sources exist in `app/src/test` and `app/src/androidTest`; they are not new executed evidence.
- Current registry: **45** entries (44 in `allTools` plus `sha256` appended through `tools`). Some other docs still mention **47**; this must be reconciled. `ToolCatalogue` hides/merges specialist IDs but `ToolsScreen.kt:38-43` enumerates all enabled registry entries without consulting `ToolCatalogue.isListed`. Verify visibility before claiming any deliberate prune.
- The GitHub code-search index returned `incomplete_results: true` and zero matches for the attempted TODO/literal searches. Focused source files were read, but **exhaustive TODO/no-op/literal inventory is BLOCKED until a complete tracked-source grep and Kotlin AST/resource sweep can be executed**. No absence claims should be made from this incomplete search.

## 1. Tool-by-tool inventory

Every registered entry has a row. Backend identifiers, source files and plans are grounded in the current registry/dispatch. Device qualification is pending for every complete workflow.

| Name / ID | Status | Evidence (source location) | Required fix / acceptance plan |
|---|---|---|---|
| `pdf_to_docx` — PDF to Word | partial | `CapabilityRegistry.kt:9`; `PdfOfficeOperationHandler.kt` (source; not a device PASS) | Prove Office Word open/Arabic layout, scan input and limits with real fixture and device result. |
| `pdf_to_pptx` — PDF to PowerPoint | partial | `CapabilityRegistry.kt:10`; `PdfOfficeOperationHandler.kt` (source; not a device PASS) | Prove slide pagination/text layer in independent PPTX parser and Office app. |
| `pdf_02` — Images to PDF | partial | `CapabilityRegistry.kt:11`; `PdfOperationHandler.kt` (source; not a device PASS) | Exercise multipage image ordering, paper geometry, export/reopen and cancellation. |
| `pdf_03` — Merge PDF | partial | `CapabilityRegistry.kt:12`; `PdfOperationHandler.kt` (source; not a device PASS) | Verify drag order, combined page count, multiple ranges and corrupt-source rejection. |
| `pdf_05` — Split PDF | partial | `CapabilityRegistry.kt:13`; `PdfOperationHandler.kt` (source; not a device PASS) | Test boundaries, ranges, every-N visual plan, output count and reopening. |
| `pdf_04` — Organize Pages | partial | `CapabilityRegistry.kt:14`; `PdfOperationHandler.kt` (source; not a device PASS) | Instrument grid drag, rotate/delete/undo and compare reopened output page order. |
| `pdf_06` — Extract Pages | partial | `CapabilityRegistry.kt:15`; `PdfOperationHandler.kt` (source; not a device PASS) | Validate selected-page extraction against source identity and actual output pages. |
| `pdf_07` — PDF to Images | partial | `CapabilityRegistry.kt:16`; `PdfOperationHandler.kt` (source; not a device PASS) | Test pixel dimensions, image formats, page range and >200-page memory budget. |
| `pdf_01` — View PDF | partial | `CapabilityRegistry.kt:17`; `PdfViewerScreen.kt` (source; not a device PASS) | Verify search, zoom, thumbnails, jump, dark mode, resume, performance and API 26. |
| `pdf_compress` — Compress PDF | partial | `CapabilityRegistry.kt:18`; `PdfOperationHandler.kt` (source; not a device PASS) | Stage five documented scan fixtures; measure real reductions, quality, 3 presets and calibrated estimate. |
| `pdf_crop` — Crop PDF Pages | partial | `CapabilityRegistry.kt:19`; `PdfOperationHandler.kt` (source; not a device PASS) | Check per-page crop rectangles, page range and output page media boxes. |
| `pdf_annotate` — Annotate PDF | partial | `CapabilityRegistry.kt:20`; `PdfOperationHandler.kt` (source; not a device PASS) | Instrument ink/shape/highlight gestures, color/size, undo and PDF appearance streams. |
| `pdf_stamp` — Watermark & Page Numbers | partial | `CapabilityRegistry.kt:21`; `PdfOperationHandler.kt` (source; not a device PASS) | Test text/image watermark move/resize/rotate/tile and reopened rendering. |
| `pdf_signature` — Sign PDF | partial | `CapabilityRegistry.kt:22`; `PdfOperationHandler.kt` (source; not a device PASS) | Validate transparent PNG and multi-object transform/rotate placement on exported PDF. |
| `pdf_image` — Add Image to PDF | partial | `CapabilityRegistry.kt:23`; `PdfOperationHandler.kt` (source; not a device PASS) | Test placement handles and emitted image coordinates/pixels in output PDF. |
| `pdf_protect` — Password Protect PDF | partial | `CapabilityRegistry.kt:24`; `PdfOperationHandler.kt` (source; not a device PASS) | Test protect/unprotect, incorrect passwords, permissions and third-party reopening. |
| `pdf_metadata` — PDF Metadata | partial | `CapabilityRegistry.kt:25`; `PdfOperationHandler.kt` (source; not a device PASS) | Inspect output title/author/subject/keywords independently after save. |
| `pdf_clean` — Clean Metadata | partial | `CapabilityRegistry.kt:26`; `PdfOperationHandler.kt` (source; not a device PASS) | Assert sensitive metadata absent from reopened bytes and preserve content. |
| `pdf_forms` — Fill PDF Form | partial | `CapabilityRegistry.kt:27`; `PdfOperationHandler.kt` (source; not a device PASS) | Use widget fixtures, overlay input, checkbox/radio and save/reopen values. |
| `pdf_ocr` — Extract Text | partial | `CapabilityRegistry.kt:28`; `OcrWebHandler.kt` (source; not a device PASS) | Device OCR and copy/export tests; explicitly label Arabic scanned text unsupported. |
| `scan_doc` — Smart Scan | partial | `CapabilityRegistry.kt:29`; `ScanScreen.kt` (source; not a device PASS) | Run ML Kit plus CameraX fallback, edge/perspective, multipage and OCR device matrix. |
| `img_01` — Convert Images | partial | `CapabilityRegistry.kt:31`; `ImageOperationHandler.kt` (source; not a device PASS) | Verify actual encoded output and sample preview parity per format. |
| `img_02` — Resize Images | partial | `CapabilityRegistry.kt:32`; `ImageOperationHandler.kt` (source; not a device PASS) | Verify exported dimensions/ratio and sampled pixel transforms. |
| `img_03` — Crop, Rotate & Flip | partial | `CapabilityRegistry.kt:33`; `ImageOperationHandler.kt` (source; not a device PASS) | Verify gesture crop/rotate/flip preview-to-file pixel correspondence. |
| `img_04` — Compress Images | partial | `CapabilityRegistry.kt:34`; `ImageOperationHandler.kt` (source; not a device PASS) | Measure actual output bytes and image quality ladder, including larger-than-input cases. |
| `img_exif` — EXIF Inspector | partial | `CapabilityRegistry.kt:35`; `ImageOperationHandler.kt` (source; not a device PASS) | Verify metadata inspection and location removal on reopened image. |
| `img_watermark` — Watermark Images | partial | `CapabilityRegistry.kt:36`; `ImageOperationHandler.kt` (source; not a device PASS) | Test object gestures and final watermark bitmap placement. |
| `img_join` — Join / Collage | partial | `CapabilityRegistry.kt:37`; `ImageOperationHandler.kt` (source; not a device PASS) | Exercise visual order, collage constraints and pixel output. |
| `img_adjust` — Adjust & Pad | partial | `CapabilityRegistry.kt:38`; `ImageOperationHandler.kt` (source; not a device PASS) | Measure adjustment/filter outputs and before/after sample parity. |
| `img_target` — Target Size | partial | `CapabilityRegistry.kt:39`; `ImageOperationHandler.kt` (source; not a device PASS) | Measured target-byte threshold; consider consolidating under image compression without losing flow. |
| `img_compare` — Compare Images | partial | `CapabilityRegistry.kt:40`; `ImageOperationHandler.kt` (source; not a device PASS) | Resolve hidden-catalog discrepancy; verify before/after interaction and output. |
| `img_stitch` — Stitch Screenshots | partial | `CapabilityRegistry.kt:41`; `ImageOperationHandler.kt` (source; not a device PASS) | Test overlap/ordering/dimension results; justify any prune in DECISIONS. |
| `dl_media` — Video, Audio & File Downloads | partial | `CapabilityRegistry.kt:44`; `MediaOperationHandler.kt` (source; not a device PASS) | Live authorized fixture matrix: all seven providers, audio-only, quality, 429/private/geo/changed extractor, resume. |
| `dl_02` — New Direct Download | partial | `CapabilityRegistry.kt:45`; `DownloadOperationHandler.kt` (source; not a device PASS) | Direct 200/range/no-range downloads, filenames, pause/retry, networking and persistence. |
| `dl_03` — Batch URL Intake | partial | `CapabilityRegistry.kt:46`; `DownloadOperationHandler.kt` (source; not a device PASS) | Batch duplicate/invalid/valid URLs, progress, failure isolation and cancellation. |
| `dl_settings` — Download Settings | partial | `CapabilityRegistry.kt:47`; `DownloadOperationHandler.kt` (source; not a device PASS) | Navigation alias: consolidate without losing downloadable settings access or route compatibility. |
| `file_02` — Browse Storage | partial | `CapabilityRegistry.kt:50`; `FileExtrasHandler.kt` (source; not a device PASS) | Navigation alias: expose Files directly; preserve legacy route behavior. |
| `file_07` — Create ZIP Archive | partial | `CapabilityRegistry.kt:51`; `ArchiveOperationHandler.kt` (source; not a device PASS) | Archive creation with collisions, compression, source hash and cancellation. |
| `file_08` — Extract ZIP Archive | partial | `CapabilityRegistry.kt:52`; `ArchiveOperationHandler.kt` (source; not a device PASS) | Extraction traversal protection, corrupt/archive cases and output hash. |
| `file_encrypted_zip` — Encrypted ZIP | partial | `CapabilityRegistry.kt:53`; `FileExtrasHandler.kt` (source; not a device PASS) | Round trip password/AES, wrong password, tampered archive and error recovery. |
| `file_batch_rename` — Batch Rename (copies) | partial | `CapabilityRegistry.kt:54`; `FileExtrasHandler.kt` (source; not a device PASS) | Preview collisions/patterns, copied names, restart and dedup. |
| `file_duplicates` — Duplicate Finder | partial | `CapabilityRegistry.kt:55`; `FileExtrasHandler.kt` (source; not a device PASS) | Verify hash groups and display metadata; test large duplicate groups. |
| `file_trash` — Managed Trash Info | partial | `CapabilityRegistry.kt:56`; `FileExtrasHandler.kt` (source; not a device PASS) | Navigation alias: test restore, permanent deletion and source isolation; consider pruning alias. |
| `file_drive` — Google Drive Import / Export | partial | `CapabilityRegistry.kt:57`; `FileExtrasHandler.kt` (source; not a device PASS) | Explicit approved-account authorized tests: import, export, revoked token, offline failure, no credential leaks. |
| `sha256` — undefined | partial | `CapabilityRegistry.kt:59`; `ArchiveOperationHandler.kt` (source; not a device PASS) | Verify expected digest entry, valid/mismatch results and clipboard/published report. |

## 2. Routes, screens and overlays

| Screen / route | Status | Evidence | Required fix / acceptance plan |
|---|---|---|---|
| Home | partial | `HomeScreen.kt / NavHost:72` | Restore draft, launch actions, active jobs, RTL and 200% font UI. |
| Tools | partial | `ToolsScreen.kt:36-43 / NavHost:73` | Verify 45 IDs, visibility policy, ranking/search and unique tile tap paths. |
| Downloads | partial | `DownloadsScreen.kt / NavHost:74` | Verify queue, notifications, history and all failure actions. |
| Print | partial | `PrintScreen.kt / NavHost:75` | System preview, page ranges, copies, orientation and device printing. |
| Files | partial | `FilesScreen.kt / NavHost:76` | Dedicated save folder, sort/filter/search, select, rename, delete all, share and usage. |
| Settings | partial | `SettingsScreen.kt:34-142 / NavHost:77` | Verify persisted options take effect and remove/restore dead controls. |
| Settings detail | partial | `JumloNavHost.kt:78` | Validate compatibility shortcut and Back return. |
| Storage | partial | `StorageScreen.kt / NavHost:79` | Measure refresh, cleanup, safeguards, permission revocation. |
| Scanner | partial | `ScanScreen.kt / NavHost:80` | ML Kit/CameraX fallback, crop filters, multipage and OCR. |
| Workspace | partial | `WorkspaceScreen.kt / NavHost:81` | Pick/configure/preview/run/result/cancel for every registered tool. |
| PDF viewer | partial | `PdfViewerScreen.kt / NavHost:82` | 200-page memory, zoom, scroll, search, saved position, RTL. |
| Job detail | partial | `JumloNavHost.kt:83` | State restore and verified output handoff. |
| Compatibility deep link | partial | `JumloNavHost.kt:84` | Cold-link and repeated warm-link origin/Back semantics. |
| Activity | partial | `JumloNavHost.kt:85` | Job history/detail, cancellation, retry and accessibility. |
| Account hub modal | partial | `AccountHubModal.kt` | Verify visible controls, Drive auth and localized errors. |
| Onboarding guide | partial | `OnboardingGuide.kt` | Verify replay, close, outside tap and RTL. |

## 3. Visible preferences and retained legacy options

| Setting | Status | Evidence | Required fix / acceptance plan |
|---|---|---|---|
| Theme: system/light/dark/AMOLED | partial | `SettingsScreen.kt:57,102-103; Theme.kt:99-112` | Persist and device-verify mode changes across restart |
| Android dynamic color | partial | `SettingsScreen.kt:59-66; Theme.kt:117-118` | Verify Android 12+ system scheme and AA contrast |
| Custom app color | partial | `SettingsScreen.kt:68-70,104-107; M3ColorFactory.kt` | Verify light/dark contrast incl. arbitrary hex |
| Language EN/AR | partial | `SettingsScreen.kt:71-72,109-110` | Device LTR/RTL, real strings, text clipping, rotation |
| Save folder choose/open/reset | partial | `SettingsScreen.kt:74-80; UserPreferencesRepository.kt:242-248` | Test persistent SAF grant and failure from revoked tree |
| Default video quality | partial | `SettingsScreen.kt:83-85; UserPreferencesRepository.kt:251-253` | Prove downloader honors actual metadata/capability |
| Default audio bitrate | partial | `SettingsScreen.kt:86-88; UserPreferencesRepository.kt:256-258` | Prove real audio-only output takes configured bitrate |
| Storage usage | partial | `SettingsScreen.kt:91-95` | Device-verify size refresh after save/delete |
| Clear cache | partial | `SettingsScreen.kt:96-97,135-140` | Verify removal, progress/errors and retained outputs |
| About/version/privacy/guide/logs | partial | `SettingsScreen.kt:98,126-133` | Verify every action is navigable and accurate |
| Haptics intensity/enable (not visible) | partial | `UserPreferencesRepository.kt:144-146,226-235` | Expose and test actual effect; honor OFF and settings |
| Reduce motion (latent no-op) | broken | `UserPreferencesRepository.kt:143,222-224` | Restore user preference and wire to all transitions |
| Wi-Fi only (legacy hidden) | stub | `UserPreferencesRepository.kt:152,274-275` | Remove dead persisted setter/key or implement a real user option |
| Concurrency count (legacy hidden) | stub | `UserPreferencesRepository.kt:149,270-272` | Remove redundant persisted setter/key, retain fixed scheduler policy |
| Media service URL/token (not visible) | partial | `UserPreferencesRepository.kt:278-283` | Verify explicit privacy/HTTPS policy and secure token handling |

## 4. Source-confirmed defects, dead paths and literal-style risks

| Priority | Classification | Evidence | Plan |
|---|---|---|---|
| P0 | Broken release-size gate | `app/build.gradle.kts:314-350` explicitly removes the 60,000,000-byte maximum from `verifyCompactApkSize` and `verifyReleaseApkSize`. | Reintroduce real APK byte checks without dropping full media; test with both under/over-limit fixtures. |
| P0 | Incomplete default CI command | `.github/workflows/ci.yml:63-68` runs `:app:lintDebug` rather than the required `:app:lintRelease`. | Add release lint and explicit size gate; preserve unit/assembly/tests. |
| P0 | Incomplete release acceptance | `TEST_REPORT.md:76-100` and CI run #40106249318 | Run emulator API 26/36 matrix, five scan fixtures, external authorized media sources and 200-page memory; record NOT RUN when unavailable. |
| P1 | Latent no-op reduce-motion setting | `UserPreferencesRepository.kt:143` always populates false while setter at 222-224 writes a key. | Make it user-visible and read preference; test motion gating. |
| P1 | Latent no-op Wi-Fi setting | `UserPreferencesRepository.kt:152` always false while setter at 274-275 writes. | Remove dead setter/key or implement actual network policy; do not expose misleading switch. |
| P1 | Latent no-op concurrency setting | `UserPreferencesRepository.kt:149` always 2 while setter at 270 persists older value. | Remove unused API/key or make scheduler behavior testable. |
| P1 | Catalogue/visibility mismatch | `ToolCatalogue.kt:124-132` hides `img_compare`, `file_trash`, `file_02`; `ToolsScreen.kt:38-43` does not apply `isListed`. | Align visibility with declared product decisions; test 45 tools and keyboard/RTL. |
| P1 | Stale dispatch IDs | `OperationDispatcher.kt:37` still routes `pdf_redact` and `pdf_compare`; neither appears in current `CapabilityRegistry.tools`. `CapabilityPolicy.kt:37` includes `pdf_compare`. | Audit internal callers; either implement and register or remove unreachable wiring in a tested change. |
| P1 | Legacy documentation drift | `TOOL_UI_SPEC.md:5` says 47 entries; registry actually enumerates 45. `README.md:27` describes a hard 60MB gate while Gradle currently lifts it. | Synchronize docs with verified implementation and restored gate. |
| P2 | UI literal color surfaces | `ScanScreen.kt:95,99,152,156,194` includes inline greys, ink palette and white surface; `VisualWatermarkEditor.kt:146` has black/white icon tint. | Move **chrome** colors to semantic tokens, keep preview-paper/ink fidelity intentional, test contrast. |
| P2 | UI literal color gestures | `VisualDocumentEditor.kt:381,390,393,445` directly uses black/white for handles and masked crop overlay. | Define semantic overlay/handle roles without changing exported pixels. |
| P2 | Inline display strings/units | `VisualWatermarkEditor.kt:158` degree label, `MediaWorkspace.kt:400` bitrate label, `PdfViewerScreen.kt:394,436` zoom/page indicators. | Audit locale-safe formatting and resource handling where appropriate. |
| P2 | Unproven dead-button absence | Focused scans of Workspace, Visual Document/Watermark editors, Viewer, Scanner, Files, MediaWorkspace and Home found no empty `onClick = {}` in those files; this does **not** prove repository-wide absence. | Complete tracked-source static scan plus automated UI interaction traversal; log every unreachable/dead action here. |

**Design-token boundary:** hard-coded hex values inside `ui/theme/Color.kt` or `ToolPalette.kt` are centralized theme tokens, not by themselves an anti-pattern. White document pages and ink samples are content/preview behavior, not ordinary chrome; assess those separately before rewriting them.

## 5. Work packages and release blockers

1. `wp/01-release-quality-gates` — fail closed on APK size, require `lintRelease`, wire CI evidence, keep full media runtime and update docs in same PR.
2. `wp/02-downloader-reliability` — link detection, auto best, advanced, metadata errors, resumable audio/video/direct transfer, Android 8–15 foreground behavior; authorized live matrix.
3. `wp/03-pdf-engine-qualification` — round-trip all PDF conversions/operations; five scan fixtures, measured compression, password, OCR limitation, 200-page safety.
4. `wp/04-tools-ux-consistency` — verified single pick/configure/run/result shell, input isolation, gestures, error recovery, file result handoff and print.
5. `wp/05-settings-and-files` — save folder/SAF, file operations and usage, haptics/reduce motion, eliminate dead settings and alias drift.
6. `wp/06-prism-theme-and-rtl` — bold semantic tokens, tool tiles, bottom navigation policy, WCAG AA, EN/AR/RTL previews and screenshots.
7. `wp/07-final-qualification` — device and physical-device matrix, benchmark, privacy and release documents, all green CI, final risk list.

No package is marked complete until its branch/PR has cited commands, results and screenshots where applicable. The signing key, Play Console, real third-party account/network access, emulator and physical device may remain explicitly **blocked** when unavailable. Never reduce the test gate, fake a screenshot or mark a source-only implementation as a runtime PASS.

### Current readiness

- Registered tools: **45**; complete end-to-end acceptance: **not demonstrated**.
- Rows still partial/broken/stub: **non-zero**. The zero-row completion criterion is **not met**.
- Main CI run observed: **success**, but did not enforce release lint or size cap and skipped instrumentation.
- APK under-limit measurement: **55,368,299 bytes** for compact build, but the current task checks the number only by printing.
- **No source fix and no emulator/physical-device execution is claimed by this Phase 0 document.**

## 2026-10-28 follow-up: complete production Kotlin/Java lexical scan

The follow-up [AUDIT_2_SOURCE_SCAN.md](AUDIT_2_SOURCE_SCAN.md) records **244/244** production Kotlin/Java files read from the audited base GitHub commit with **zero fetch failures**. The reproducible lexical candidate rules located **204 direct raw-color references**, **7 direct UI text literals**, **0 TODO/FIXME/stub markers** and **0 syntactically empty callbacks**. These are **candidates**, not claims that every color is wrong: most are token definitions, document-paper/ink colors or intentional numeric labels. No finding certifies runtime behavior, catches semantically inert *nonempty* callbacks or covers XML/scripts/binary assets. The full file:line inventory is retained in the linked source scan. Original capability/status table above remains authoritative for missing end-to-end evidence.

## WP07 stale page-order source defect

PrintPageSelection.move and PrintViewModel originally reused malformed saved pageOrder even when the renderer showed the original order; UI manipulation could silently change the wrong pages. Source patch normalizes stale order and preserves selected source pages. Regression tests added; latest-head CI and device print-provider acceptance remain pending.
