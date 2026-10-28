# AUDIT_2 — Jumlo Phase 0 production-readiness inventory

**Audit date:** 2026-10-28. **Audited base:** `main` @ `d599b8a3159f38623e3d0b83c3d58a23073e70cf`. **Method:** GitHub API read-only inspection of the ten mandatory root documents, recursive tree, registry, policies, dispatcher, concrete handlers, navigation, settings, theme, Gradle build and Actions workflow. This first audit is committed **before** any product implementation changes.

**Evidence semantics:** `works` requires an observed, relevant passing runtime test; `partial` includes an existing implementation not yet qualified across the requested end-to-end behavior; `broken` requires contradictory or failing source/runtime evidence; `stub` requires a route/claim with no substantive handler. A `partial` row does **not** mean its algorithm is demonstrably defective. Existing test classes and the green historical CI are not current end-to-end device evidence. Statuses are not upgraded from source alone.

**Verified prior CI context:** `main` GitHub Actions run [#96](https://github.com/mo7medSa3d/Jumlo/actions/runs/40106249318) completed `success` on 2026-10-24. Its workflow only runs `lintDebug`, not the requested `lintRelease`, and it does not enforce a 60 MB full-feature APK ceiling or run connected tests on ordinary PRs. It is **not** a green production gate.

## Release-blocking source findings

| Severity | Status | Evidence | Necessary fix / acceptance |
|---|---|---|---|
| P0 | **BROKEN** release size gate | `app/build.gradle.kts:309-338` logs size but performs no bound check; `README.md:31` still claims a strict 60,000,000-byte gate | Reintroduce decimal 60,000,000-byte checks in compact/release; measure full media APK in CI; do **not** remove features to pass |
| P0 | **BROKEN** CI coverage contract | `.github/workflows/ci.yml:68-75` uses `lintDebug` rather than `lintRelease`; `:app:verifyReleaseApkSize` not run in build job; `ci.yml:193-196` connected tests only on manual dispatch | PR gate must run debug assemble, unit, release lint, strict size verification; tier device tests with honest blocked status |
| P1 | **PARTIAL** downloader automatic behavior | `MediaOperationHandler.kt:35` requires `settings.media`; `DownloadOperationHandler.kt:106-109` treats direct/batch separately; `ROUTES.md` describes metadata selection | Make best quality/video/audio auto-resolve safely; default zero pickers; Advanced optional; validate all routed authorized providers |
| P1 | **BROKEN** persisted motion-setting consistency | `UserPreferencesRepository.kt:56,143,222-224` writes `respect_reduced_motion` but maps settings to hard-coded `false` | Decide and implement user-facing reduce-motion as requested, with system setting precedence; add persistence and animation tests |
| P1 | **PARTIAL** haptics control | `UserPreferencesRepository.kt:57-58,144-146,226-236` persists haptic intensity; `SettingsScreen.kt:56-99` exposes no haptics choice | Add actual Settings control or remove dead contract; verify haptics respects OFF |
| P1 | **PARTIAL** scanner Arabic OCR | `README.md:66` and `MlKitOcrEngine.kt` document Latin-only OCR | Localized explicit unsupported-language UX; do not promise scanned Arabic OCR without engine/model and device proof |
| P1 | **STUB / missing capability** Office → PDF | No Office→PDF entry in `CapabilityRegistry.kt:7-59` or `OperationDispatcher.kt:35-44`; PDF→DOCX/PPTX is the reverse direction | Decide feasibility, implement and test on supported inputs or accurately scope unsupported formats before menu exposure |
| P1 | **PARTIAL** navigation design | `QuietNavigation.kt:43` declares 5 tabs incl. Downloads, contrary to four requested; `JumloNavHost.kt:67-170` owns routing | Move Downloads into tools/Home; maintain deep links and saved-stack/back behavior, verify RTL |
| P1 | **BROKEN** stale capability status docs | `TOOL_UI_SPEC.md:5` claims 44 enabled/3 disabled; `CapabilityPolicy.kt:14-46` enables every registered capability | Align documents to live registry and distinguish SOURCE vs QUALIFIED |
| P2 | **DEAD / unreachable handler** | `OperationDispatcher.kt:37` accepts `pdf_redact` but policy `CapabilityPolicy.kt:34-40` does not; `file_trash` dispatch arm exists but policy treats it as navigation | Remove or reconcile unreachable dispatch branches with explicit tests and intended behavior |
| P2 | **DEAD retained preferences** | `UserPreferencesRepository.kt:62,152,274-275` persists Wi-Fi-only but always emits `false` and Settings has no toggle | Remove/migrate unused persistence and callers, keeping deliberate no Wi-Fi-only UI policy |
| P2 | **PARTIAL** theme visual acceptance | `Color.kt:8-89,130-200` has semantic token definitions and category hues; `Theme.kt:25-159` uses M3 token schemes | Audit every direct hard-coded component color, WCAG AA snapshots in EN/AR/light/dark; implement bold coherent palette |

## All registered tools (source vs end-to-end qualification)

There are **45** registered tool IDs in `CapabilityRegistry.tools` including `sha256`. Source-dispatched tools are real implementations unless otherwise noted; *none* has a current, complete pick→configure→run→result Android evidence matrix attached to this audit. For that reason the status below is `partial`, not fabricated `works`.

| Tool ID / name | Status | Evidence (source location) | Fix and qualification plan |
|---|---|---|---|
| `pdf_to_docx` — PDF to Word | partial | `CapabilityRegistry.kt:9`; `PdfOfficeOperationHandler.kt:34-113` | End-to-end fixture + independent OOXML parser + Office app compatibility |
| `pdf_to_pptx` — PDF to PowerPoint | partial | `CapabilityRegistry.kt:10`; `PdfOfficeOperationHandler.kt:34-113` | End-to-end fixture + independent OOXML parser + Office app compatibility |
| `pdf_02` — Images to PDF | partial | `CapabilityRegistry.kt:11`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_03` — Merge PDF | partial | `CapabilityRegistry.kt:12`; `PdfOperationHandler.kt:31-48` | Verify pick/drag-page-grid/configure/run/result, history and multiple-page edge fixtures |
| `pdf_05` — Split PDF | partial | `CapabilityRegistry.kt:13`; `PdfOperationHandler.kt:31-48` | Verify pick/drag-page-grid/configure/run/result, history and multiple-page edge fixtures |
| `pdf_04` — Organize Pages | partial | `CapabilityRegistry.kt:14`; `PdfOperationHandler.kt:31-48` | Verify pick/drag-page-grid/configure/run/result, history and multiple-page edge fixtures |
| `pdf_06` — Extract Pages | partial | `CapabilityRegistry.kt:15`; `PdfOperationHandler.kt:31-48` | Verify pick/drag-page-grid/configure/run/result, history and multiple-page edge fixtures |
| `pdf_07` — PDF to Images | partial | `CapabilityRegistry.kt:16`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_01` — View PDF | partial | `CapabilityRegistry.kt:17`; Navigation/UI (`JumloNavHost.kt` / destination) | Verify 200-page smoothness, search, zoom, thumbnails, remember-position and RTL |
| `pdf_compress` — Compress PDF | partial | `CapabilityRegistry.kt:18`; `PdfOperationHandler.kt:31-48` | Run 5 scan fixtures; verify true 3-level measured reduction, publish and 200-page memory behavior |
| `pdf_crop` — Crop PDF Pages | partial | `CapabilityRegistry.kt:19`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_annotate` — Annotate PDF | partial | `CapabilityRegistry.kt:20`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_stamp` — Watermark & Page Numbers | partial | `CapabilityRegistry.kt:21`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_signature` — Sign PDF | partial | `CapabilityRegistry.kt:22`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_image` — Add Image to PDF | partial | `CapabilityRegistry.kt:23`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_protect` — Password Protect PDF | partial | `CapabilityRegistry.kt:24`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_metadata` — PDF Metadata | partial | `CapabilityRegistry.kt:25`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_clean` — Clean Metadata | partial | `CapabilityRegistry.kt:26`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_forms` — Fill PDF Form | partial | `CapabilityRegistry.kt:27`; `PdfOperationHandler.kt:31-48` | Execute real PDF fixtures; verify preview/settings/export/permissions and publish lifecycle |
| `pdf_ocr` — Extract Text | partial | `CapabilityRegistry.kt:28`; `OcrWebHandler.kt:25-99` | Latin OCR real-device test; explain unsupported scanned Arabic; test text/PDF source |
| `scan_doc` — Smart Scan | partial | `CapabilityRegistry.kt:29`; Navigation/UI (`JumloNavHost.kt` / destination) | Verify real CameraX/ML Kit multipage, edge/perspective, JPEG/PDF and Latin/Arabic messaging on devices |
| `img_01` — Convert Images | partial | `CapabilityRegistry.kt:31`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_02` — Resize Images | partial | `CapabilityRegistry.kt:32`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_03` — Crop, Rotate & Flip | partial | `CapabilityRegistry.kt:33`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_04` — Compress Images | partial | `CapabilityRegistry.kt:34`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_exif` — EXIF Inspector | partial | `CapabilityRegistry.kt:35`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_watermark` — Watermark Images | partial | `CapabilityRegistry.kt:36`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_join` — Join / Collage | partial | `CapabilityRegistry.kt:37`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_adjust` — Adjust & Pad | partial | `CapabilityRegistry.kt:38`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_target` — Target Size | partial | `CapabilityRegistry.kt:39`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_compare` — Compare Images | partial | `CapabilityRegistry.kt:40`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `img_stitch` — Stitch Screenshots | partial | `CapabilityRegistry.kt:41`; `ImageOperationHandler.kt:33-41,100-336` | Codec/pixel device fixture, visual preview vs final output, drag/editor and memory limits |
| `dl_media` — Video, Audio & File Downloads | partial | `CapabilityRegistry.kt:44`; `MediaOperationHandler.kt:35-117` | Auto-best + advanced audio/video, all provider fixtures, pause/resume/retry, FGS API26–35 |
| `dl_02` — New Direct Download | partial | `CapabilityRegistry.kt:45`; `DownloadOperationHandler.kt:106-355` | Verify direct/batch intake, link errors, resume and unify into main Download workflow |
| `dl_03` — Batch URL Intake | partial | `CapabilityRegistry.kt:46`; `DownloadOperationHandler.kt:106-355` | Verify direct/batch intake, link errors, resume and unify into main Download workflow |
| `dl_settings` — Download Settings | partial | `CapabilityRegistry.kt:47`; Navigation/UI (`JumloNavHost.kt` / destination) | Verify direct/batch intake, link errors, resume and unify into main Download workflow |
| `file_02` — Browse Storage | partial | `CapabilityRegistry.kt:50`; Navigation/UI (`JumloNavHost.kt` / destination) | Navigation alias; evaluate pruning with route/documentation/test migration, not silent removal |
| `file_07` — Create ZIP Archive | partial | `CapabilityRegistry.kt:51`; `ArchiveOperationHandler.kt:22-26` | Test real SAF grants, storage and full pick/run/output lifecycle incl. low-space/permissions |
| `file_08` — Extract ZIP Archive | partial | `CapabilityRegistry.kt:52`; `ArchiveOperationHandler.kt:22-26` | Test real SAF grants, storage and full pick/run/output lifecycle incl. low-space/permissions |
| `file_encrypted_zip` — Encrypted ZIP | partial | `CapabilityRegistry.kt:53`; `FileExtrasHandler.kt:37-42` | Test real SAF grants, storage and full pick/run/output lifecycle incl. low-space/permissions |
| `file_batch_rename` — Batch Rename (copies) | partial | `CapabilityRegistry.kt:54`; `FileExtrasHandler.kt:37-42` | Test real SAF grants, storage and full pick/run/output lifecycle incl. low-space/permissions |
| `file_duplicates` — Duplicate Finder | partial | `CapabilityRegistry.kt:55`; `FileExtrasHandler.kt:37-42` | Test real SAF grants, storage and full pick/run/output lifecycle incl. low-space/permissions |
| `file_trash` — Managed Trash Info | partial | `CapabilityRegistry.kt:56`; Navigation/UI (`JumloNavHost.kt` / destination) | Navigation alias; evaluate pruning with route/documentation/test migration, not silent removal |
| `file_drive` — Google Drive Import / Export | partial | `CapabilityRegistry.kt:57`; `FileExtrasHandler.kt:37-42` | OAuth/token-free? validate token-gated Drive v3 on authorized account and error paths |
| `sha256` — SHA-256 verification | partial | `CapabilityRegistry.kt:59`; `ArchiveOperationHandler.kt:22-26` | Verify actual byte digests and expected hash mismatch; consider intentional prune |

**Catalogue discrepancy:** `ToolCatalogue.kt:125` intentionally hides `img_compare`, `file_trash` and `file_02`. These are still registered IDs, so the registered inventory and the *visible* menu differ. Check visible/hidden routing against `ToolCatalogueTest` and `ToolCatalogueInteractionTest` after proposed pruning. The three legacy navigation aliases `file_02`, `file_trash`, `dl_settings` should not be treated as processing jobs; `pdf_01` and `scan_doc` intentionally use special viewer/scanner routes.

## Screen and navigation inventory

All fourteen serializable destinations are declared in `app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt:57-72`. The table is route coverage, **not** passing emulator/device evidence.

| Route / screen | Status | Evidence | Fix plan |
|---|---|---|---|
| `home` / `HomeRoute` | partial | `JumloNavHost.kt:57-72`; `HomeScreen.kt` | Four-tab Home action and resume-draft tests |
| `tools` / `ToolsRoute` | partial | `JumloNavHost.kt:57-72`; `ToolsScreen.kt` | Sections and 200% font EN/AR semantics; visual screenshots |
| `files` / `FilesRoute` | partial | `JumloNavHost.kt:57-72`; `FilesScreen.kt` | SAF search/filter/sort/select/delete/recovery/storage usage |
| `downloads` / `DownloadsRoute` | partial | `JumloNavHost.kt:57-72`; `DownloadsScreen.kt` | Move from fifth dock tab without losing job history |
| `settings` / `SettingsRoute` | partial | `JumloNavHost.kt:57-72`; `SettingsScreen.kt` | Wire real toggles and save folder, persistence tests |
| `tools/run` / `ToolRoute` | partial | `JumloNavHost.kt:57-72`; `WorkspaceScreen.kt / MediaWorkspace.kt` | Shared pick/configure/run/result incl. reset & cancellation |
| `tools/scan` / `ScanRoute` | partial | `JumloNavHost.kt:57-72`; `ScanScreen.kt` | Real scanner with gallery/CameraX fallback and OCR |
| `tools/print` / `PrintRoute` | partial | `JumloNavHost.kt:57-72`; `PrintScreen.kt` | Android print preview/range/copies/orientation |
| `files/viewer` / `ViewerRoute` | partial | `JumloNavHost.kt:57-72`; `PdfViewerScreen.kt` | 200-page viewer, search, thumbnails, night/remember position |
| `downloads/job` / `JobRoute` | partial | `JumloNavHost.kt:57-72`; `JumloNavHost.kt` | Notification deep link; pause/retry/progress result |
| `downloads/activity` / `ActivityRoute` | partial | `JumloNavHost.kt:57-72`; `JumloNavHost.kt` | Job history durability, selection and empty states |
| `settings/details` / `SettingsDetailRoute` | partial | `JumloNavHost.kt:57-72`; `SettingsScreen.kt` | Return-to-caller/Back behavior |
| `settings/storage` / `StorageRoute` | partial | `JumloNavHost.kt:57-72`; `StorageScreen.kt` | Refresh, permissions, storage accounting |
| `workspace` / `WorkspaceRoute` | partial | `JumloNavHost.kt:57-72`; `JumloNavHost.kt` | Cold/warm deep link and cancellation-safe intake |

## Every visible Settings control (from `SettingsScreen.kt:56-140`)

| Setting / action | Status | Evidence | Fix / verification |
|---|---|---|---|
| Theme (system/light/dark/AMOLED) | partial | `SettingsScreen.kt:57,102-103; Theme.kt` | Persist, relaunch, light/dark and preview contrast |
| Dynamic color (Android 12+) | partial | `SettingsScreen.kt:59-65` | Verify wallpaper-based palette and fallback |
| App accent/preset | partial | `SettingsScreen.kt:68-70,104-108` | Verify saved palette, accessible contrast |
| Language EN/AR | partial | `SettingsScreen.kt:71-72,109-110` | All screens and error messages + RTL/test |
| Default save folder/select/open/reset | partial | `SettingsScreen.kt:73-80` | SAF persist/open/revocation, media destination, API26-35 |
| Default video quality | partial | `SettingsScreen.kt:83-85,112-116` | Apply defaults to auto best/adaptive metadata; per-provider test |
| Default audio bitrate | partial | `SettingsScreen.kt:86-88,118-122` | Prove audio-only format/bitrate and fallback |
| Storage usage | partial | `SettingsScreen.kt:91-95` | Real bytes and refresh on file change |
| Clear cache | partial | `SettingsScreen.kt:96-97,136-140` | Confirm temp only, no lost active jobs/source |
| About | partial | `SettingsScreen.kt:98-99,126-134` | Version, guide, diagnostics, privacy all live |
| Guide replay | partial | `SettingsScreen.kt:130` | Onboarding opens from about |
| Diagnostics export | partial | `SettingsScreen.kt:131` | Redacted export and URI permission |
| Privacy view | partial | `SettingsScreen.kt:132` | Match policies to actual flows |
| Haptic feedback/intensity — required but hidden | broken | `UserPreferencesRepository.kt:144-146,226-236; SettingsScreen.kt:56-99` | Expose real setting and respect OFF |
| Reduce motion — required but hidden | broken | `UserPreferencesRepository.kt:143,222-224` | Fix persisted field and Compose animation semantics |
| Wi-Fi-only legacy — deliberately not visible | partial | `UserPreferencesRepository.kt:62,152,274-275` | Remove unused setter/storage or migrate explicitly; no dead UI toggle |

## Source-level literal, no-op and obsolete-path register

- **Verified dead contract:** `UserPreferencesRepository.setRespectReducedMotion` writes a key, but the exposed state is always `false`; `setWifiOnlyDefault` writes another key, while the exposed state is always `false`. This is confirmed without a device.
- **Unreachable paths:** `pdf_redact` dispatch and the `file_trash` processing arm need reconciliation against the capability policy and nav aliases; no attempt to call them is documented.
- **Hard-coded user-facing copy:** bilingual English/Arabic tool titles/descriptions are literal constructor arguments in `CapabilityRegistry.kt:9-59` rather than `strings.xml`. This is *real*, but an intentional existing catalogue mechanism; migrate in a coordinated resource-backed change rather than deleting labels.
- **Hard-coded colors in designated token files:** `app/src/main/java/com/jumlo/app/ui/theme/Color.kt:8-89,130-200` declares `Color(0x...)` semantic constants and category colors. Those are token definitions, not violations by themselves. Component-level raw color literals still require source-wide discovery and contrast testing.
- **Intentional source constants, not UI strings:** route IDs, error codes, MIME types, storage keys and test tags must not be falsely reported as user-facing hardcoded text.
- **TODO / FIXME / no-op control inventory:** a GitHub code search returned no indexed hits (search also returned zero for known common `Color(` constructs, so its zero results are **not reliable negative proof**). A source-wide lexical scan of every production source and XML resource has **NOT RUN**. Do not claim zero TODOs, stubs, dead buttons, literal UI strings or raw colors; complete the deterministic scan and add every file:line before closing Phase 0. Target patterns: `TODO`, `FIXME`, `NotImplementedError`, `UnsupportedOperationException`, empty `onClick`/`onValueChange`, `Color(0x...)` outside theme, composable literal `Text("...")` and resource hardcodes. This audit deliberately records the coverage gap rather than inventing completeness.

## Mandatory verification and prioritization

1. **P0:** Correct release size checks and CI gates without hiding functionality; verify actual APK size and required test/lint commands on PR branch.
2. **P0–P1:** Prove basic pick→run→result with 5 scan fixtures, 200-page PDF, real save/restore/permission errors and local files; fix reproduced failures first.
3. **P1:** Downloader best-default/audio-only, provider/error matrix, background FGS and cancellation/resume (authorized public URLs only).
4. **P1:** Reconcile Settings motion/haptics and four-tab UX; save folder and Arabic/RTL checks.
5. **P2:** Shared interaction system, high-contrast vivid M3 tokens, preview parity, icon provenance, performance/accessibility/Play policies.

**Blocked / not verified:** No Android build, test, lint, actual APK artifact, running emulator, physical camera, Play upload key, live provider URLs or attached screenshots were executed/produced by *this* audit. GitHub's earlier green CI only covers the commands present in its then-current workflow. Sign-off requires fresh CI links, verified artifacts and explicit device/emulator rows in `TEST_REPORT.md`. Never merge a failing gate merely to make the branch green.
