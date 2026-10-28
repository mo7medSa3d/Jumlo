# AUDIT_2 — Jumlo source-to-release qualification audit

**Audit date:** 2026-10-28. **Baseline:** `main` @ `d599b8a3159f38623e3d0b83c3d58a23073e70cf` (2026-10-24). **Disposition: NOT PRODUCTION QUALIFIED.**

> This is a source/CI evidence inventory, **not** a declaration that every on-device interaction has been exercised. `partial` means an implementation/route was found but its required end-to-end evidence is missing. `works` requires a current passing result, not a code path or test source. `broken` requires evidence of a violated requirement. `stub` requires an explicitly inert implementation. Browser/API inspection cannot substitute for emulator/device execution.

## Scope and ground truth

- Required documents read before changes: `README.md`, `TEST_REPORT.md`, `AUDIT.md`, `ROUTES.md`, `TOOL_UI_SPEC.md`, `ICON_MAP.md`, `DECISIONS.md`, `CHANGELOG.md`, `IMPLEMENTATION_PLAN.md`, `RELEASE_CHECKLIST.md`.
- GitHub tree `main` contains **796 blobs** (recursive tree not truncated). Examined the actual `CapabilityRegistry` (45 registered tools), `CapabilityPolicy`, `OperationDispatcher`, `ToolCatalogue`, Tools/Settings/navigation source and representative worker, document, scanner, reader and UI files. This is **not** yet a full 796-file text scan; GitHub code search returned `incomplete_results: true` for keyword queries. Therefore a global `TODO`/dead-button count must remain **NOT VERIFIED**.
- Current main GitHub Actions workflow [40106249318](https://github.com/mo7medSa3d/Jumlo/actions/runs/40106249318): **success**, 2026-10-24. It ran `assembleDebug`, `verifyCompactApkSize`, `testDebugUnitTest`, `lintDebug` and the media inspection step; its emulator jobs were **skipped**. This does **not** establish `lintRelease`, signed `release`, final-release size, instrumentation or device qualification.
- Existing `TEST_REPORT.md` is dated 2026-10-24 and claims Gradle was not executed **in its own earlier sandbox**. It must not override the separate latest GitHub Actions run. Current Phase-0 inspection itself executed **no local tests**.
- `CapabilityPolicy.kt:L21` declares five navigation-only entries. Processing entries use the dispatch in `OperationDispatcher.kt:L20-L32`. A navigation shortcut cannot be truthfully credited with pick→run→result; qualify its navigation behavior or relocate it outside the processing menu.
- Source paths abbreviated below: `CapabilityRegistry.kt` = `app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt`; `JumloNavHost.kt` = `app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt`; other shortened names refer to the corresponding files inside `app/src/main/java/com/jumlo/app/`.

## P0/P1/P2 finding ledger

| ID | Priority | Status | Evidence | Required correction and evidence |
|---|---|---|---|---|
| A2-001 | P0 | **broken release contract** | `app/build.gradle.kts:L314-L350` explicitly removes 60,000,000-byte size ceiling; README.md and IMPLEMENTATION_PLAN.md still require it | Reinstate hard decimal cap for full-media compact **and** release variants, add regression guard; run full-media CI with measured actual bytes. Do **not** silently substitute `extendedMedia=false`. |
| A2-002 | P0 | **partial CI contract** | `.github/workflows/ci.yml` runs `:app:lintDebug`, not mandated `:app:lintRelease`; run 40106249318 succeeded | Add `:app:lintRelease` and full mandatory `assembleDebug/testDebugUnitTest/lintRelease` gate; verify new PR run before merge. |
| A2-003 | P0 | **blocked runtime qualification** | CI run 40106249318 skips emulator jobs; `TEST_REPORT.md` has no matching physical-device output | Execute API 26/API 35 or 36 instrumentation, screenshots, large PDF and Android 8–15 media notifications; record PASS/FAIL/BLOCKED per case. |
| A2-004 | P0 | **blocked signing / distribution** | `RELEASE_CHECKLIST.md:L5-L12`; user release keystore absent from tracked source | Signed publishable APK/AAB + certificate/permissions/native/Play policy gates; do not fabricate signing credentials. |
| A2-005 | P1 | **broken navigation requirement** | `QuietNavigation.kt:L42-L43` defines five tabs; user requires four | Move Downloads into Home/Tools without deleting it; preserve route state, back behavior, discoverability and RTL; update navigation tests and ROUTES. |
| A2-006 | P1 | **broken/missing settings** | `SettingsScreen.kt:L45-L93` omits haptics/reduce-motion; repository reads `respectReducedMotion = false` | Expose both meaningful settings, persist, apply to runtime and test restart/system accessibility interaction. |
| A2-007 | P1 | **partial default auto-download** | `UserPreferencesRepository.kt` defaults video quality to `1080`; `SettingsScreen.kt:L94-L103` offers BEST | Set true BEST-by-default with audio-only preference as needed; verify actual selection, not just setting label. |
| A2-008 | P1 | **blocked functional claims** | `TOOL_UI_SPEC.md` lists 47/44+3 whereas actual `CapabilityRegistry.kt` has 45 all enabled; `DECISIONS.md` lifts size limit | Reconcile every published tool count and feature status to source, tested output and policy; never turn unchecked rows green. |
| A2-009 | P1 | **OCR limitation** | `README.md` and `TEST_REPORT.md` say scanned Arabic unsupported | Preserve embedded Unicode Arabic, explicitly flag scanned-Arabic OCR limitations in UI and device-test supported languages. |
| A2-010 | P2 | **hardcoded UI colors** | `ui/scan/ScanScreen.kt:L95,L99,L152` uses literal `Color(0x...)` outside token system | Move scanner checkerboard/ink swatches to explicit semantic tokens; AA and visual RTL/dark checks. |
| A2-011 | P2 | **hardcoded English UI fallback** | `ui/settings/SettingsScreen.kt`, `previewAccentLabel` ends with `return "Blue"` | Return a localized resource/preset name and add Arabic regression. |
| A2-012 | P2 | **hardcoded section strings** | `ui/tools/ToolsScreen.kt:L53-L69` inlines English/Arabic section labels | Move to `values/values-ar` strings with resource-key parity checks. |
| A2-013 | P2 | **unused callback surface** | `SettingsScreen.kt` suppresses unused parameters; `QuietFloatingDock` has unused `onScan`/`onPrint` slots | Remove retired callbacks or wire meaningful actions; confirm user-facing control behavior with Compose test. |
| A2-014 | P2 | **stale preferences** | `UserPreferencesRepository.kt` retains `WIFI_ONLY_DEFAULT`/`MAX_CONCURRENT_DOWNLOADS` while exposing hardcoded values | Mark migration-only intentionally or remove dead state without data loss; confirm no visible fake setting. |
| A2-015 | P1 | **incomplete exhaustive audit** | Code-search query response `incomplete_results: true`; no emulator connected in this GitHub-API-only phase | Complete a real full-source and resource scan, investigate each hit, collect device evidence; this audit is an open baseline, not completion. |

## All registered tools (45)

**Status vocabulary:** `partial` = found in registry/dispatch, but the mandatory current device/real-output acceptance matrix is not all PASS. No tool is assigned `works` by inspection alone. Device-specific `blocked:` reasons are recorded in TEST_REPORT when attempted.

| Tool | Status | Evidence | Fix/test plan |
|---|---|---|---|
| `pdf_to_docx` — PDF to Word | partial | `CapabilityRegistry.kt:L9`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Round-trip DOCX package; assert paragraphs/RTL and blank/scan behavior; Office app/device checks. |
| `pdf_to_pptx` — PDF to PowerPoint | partial | `CapabilityRegistry.kt:L10`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Assert 1 slide/page, rendered image and text layer, reopen output in independent parser. |
| `pdf_02` — Images to PDF | partial | `CapabilityRegistry.kt:L11`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Test multiple images, ordering, page sizes, EXIF rotation and actual saved PDF. |
| `pdf_03` — Merge PDF | partial | `CapabilityRegistry.kt:L12`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Test two-plus PDF merge order, page count, corrupt/encrypted source, cancel and output hash. |
| `pdf_05` — Split PDF | partial | `CapabilityRegistry.kt:L13`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Test split ranges and every emitted file (page count/order); large-file device case. |
| `pdf_04` — Organize Pages | partial | `CapabilityRegistry.kt:L14`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Test reorder/rotate/delete/undo against independent saved-page inspection. |
| `pdf_06` — Extract Pages | partial | `CapabilityRegistry.kt:L15`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Test selected ranges and precise exported page order. |
| `pdf_07` — PDF to Images | partial | `CapabilityRegistry.kt:L16`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Test PNG/JPEG DPI, output dimensions/bytes and source-page alignment. |
| `pdf_01` — View PDF | partial | `CapabilityRegistry.kt:L17`; CapabilityPolicy.kt:L21 (navigation); no current end-to-end device result | Route-only: qualify zoom, thumbnails, jump/search, last page, night mode and 200-page memory. |
| `pdf_compress` — Compress PDF | partial | `CapabilityRegistry.kt:L18`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Run all three presets on five named scan fixtures; record real before/after bytes and fallback. |
| `pdf_crop` — Crop PDF Pages | partial | `CapabilityRegistry.kt:L19`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Assert normalized crop boxes on rotated, odd-sized and multipage PDFs. |
| `pdf_annotate` — Annotate PDF | partial | `CapabilityRegistry.kt:L20`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Compare visual strokes and persisted annotations after reopening exported PDF. |
| `pdf_stamp` — Watermark & Page Numbers | partial | `CapabilityRegistry.kt:L21`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Compare watermark and numbering overlay pixels/geometry in exported document. |
| `pdf_signature` — Sign PDF | partial | `CapabilityRegistry.kt:L22`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Verify transparent alpha, multiple placements/rotation, persisted signed-page pixels. |
| `pdf_image` — Add Image to PDF | partial | `CapabilityRegistry.kt:L23`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Verify inserted image clipping, sizing, output geometry and saved PDF. |
| `pdf_protect` — Password Protect PDF | partial | `CapabilityRegistry.kt:L24`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Correct/wrong password and permission round trips on saved PDF/device readers. |
| `pdf_metadata` — PDF Metadata | partial | `CapabilityRegistry.kt:L25`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Assert title/author/subject/keywords round trip from saved PDF, including Unicode. |
| `pdf_clean` — Clean Metadata | partial | `CapabilityRegistry.kt:L26`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Verify document info + XMP removal after export and no false claim about image metadata. |
| `pdf_forms` — Fill PDF Form | partial | `CapabilityRegistry.kt:L27`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Edit/flatten AcroForm widgets and verify values/positions in independent reader. |
| `pdf_ocr` — Extract Text | partial | `CapabilityRegistry.kt:L28`; OperationDispatcher.kt:L22-L31 (pdf handler); no current end-to-end device result | Validate text extraction/OCR language detection and explicit scanned-Arabic unsupported message. |
| `scan_doc` — Smart Scan | partial | `CapabilityRegistry.kt:L29`; CapabilityPolicy.kt:L21 (navigation); no current end-to-end device result | Route-only: exercise ML Kit + CameraX fallback, edge/perspective, gallery, OCR and PDF/JPEG export. |
| `img_01` — Convert Images | partial | `CapabilityRegistry.kt:L31`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Real JPEG/PNG/WebP encode/decode and batch output round trip. |
| `img_02` — Resize Images | partial | `CapabilityRegistry.kt:L32`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify pixel dimensions, aspect ratio, EXIF/orientation, and output byte preservation. |
| `img_03` — Crop, Rotate & Flip | partial | `CapabilityRegistry.kt:L33`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify crop/rotate/flip pixels, gesture coordinates and RTL action semantics. |
| `img_04` — Compress Images | partial | `CapabilityRegistry.kt:L34`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify requested quality maps to measured bytes and decoded image quality. |
| `img_exif` — EXIF Inspector | partial | `CapabilityRegistry.kt:L35`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify read/strip EXIF without altering source; output metadata independently inspected. |
| `img_watermark` — Watermark Images | partial | `CapabilityRegistry.kt:L36`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify logo/text placement, drag/resize, opacity and output pixel parity. |
| `img_join` — Join / Collage | partial | `CapabilityRegistry.kt:L37`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify collage order, boundaries, scaling and saved image dimensions. |
| `img_adjust` — Adjust & Pad | partial | `CapabilityRegistry.kt:L38`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify brightness/contrast/saturation/padding pixels and source immutability. |
| `img_target` — Target Size | partial | `CapabilityRegistry.kt:L39`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Run target byte-size ladder, report when impossible, independently measure output. |
| `img_compare` — Compare Images | partial | `CapabilityRegistry.kt:L40`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Assert actual diff report on identical/different samples and result navigation. |
| `img_stitch` — Stitch Screenshots | partial | `CapabilityRegistry.kt:L41`; OperationDispatcher.kt:L22-L31 (images handler); no current end-to-end device result | Verify screenshot stitching order, seams and output dimension limits. |
| `dl_media` — Video, Audio & File Downloads | partial | `CapabilityRegistry.kt:L44`; OperationDispatcher.kt:L22-L31 (downloads handler); no current end-to-end device result | Qualify authorized live providers, auto best video/audio, retry/pause/resume, real notifications. |
| `dl_02` — New Direct Download | partial | `CapabilityRegistry.kt:L45`; OperationDispatcher.kt:L22-L31 (downloads handler); no current end-to-end device result | Verify direct + Drive URLs, redirects, resumes, filename cleaning, private/expired errors. |
| `dl_03` — Batch URL Intake | partial | `CapabilityRegistry.kt:L46`; OperationDispatcher.kt:L22-L31 (downloads handler); no current end-to-end device result | Verify batch parsing/dedup, per-item error isolation and resumable downloads. |
| `dl_settings` — Download Settings | partial | `CapabilityRegistry.kt:L47`; CapabilityPolicy.kt:L21 (navigation); no current end-to-end device result | Route-only: verify Settings shortcut and persistence effects on later download jobs. |
| `file_02` — Browse Storage | partial | `CapabilityRegistry.kt:L50`; CapabilityPolicy.kt:L21 (navigation); no current end-to-end device result | Route-only: inspect granted/revoked SAF, browsing, search, rename/share and saved-folder access. |
| `file_07` — Create ZIP Archive | partial | `CapabilityRegistry.kt:L51`; OperationDispatcher.kt:L22-L31 (files handler); no current end-to-end device result | Verify ZIP members, path safety, filenames, cancellation and independent extraction. |
| `file_08` — Extract ZIP Archive | partial | `CapabilityRegistry.kt:L52`; OperationDispatcher.kt:L22-L31 (files handler); no current end-to-end device result | Test archive traversal/zip-bomb prevention, extraction hashes and collision handling. |
| `file_encrypted_zip` — Encrypted ZIP | partial | `CapabilityRegistry.kt:L53`; OperationDispatcher.kt:L22-L31 (files handler); no current end-to-end device result | Test encrypted ZIP round trip, incorrect password, tampering and device reopen. |
| `file_batch_rename` — Batch Rename (copies) | partial | `CapabilityRegistry.kt:L54`; OperationDispatcher.kt:L22-L31 (files handler); no current end-to-end device result | Verify preview→persisted copies, collision names, date formats and undo. |
| `file_duplicates` — Duplicate Finder | partial | `CapabilityRegistry.kt:L55`; OperationDispatcher.kt:L22-L31 (files handler); no current end-to-end device result | Verify SHA-256 grouping of real files, false matches, outputs and delete behavior. |
| `file_trash` — Managed Trash Info | partial | `CapabilityRegistry.kt:L56`; CapabilityPolicy.kt:L21 (navigation); no current end-to-end device result | Route-only: verify managed trash restore/purge with byte-perfect round trips. |
| `file_drive` — Google Drive Import / Export | partial | `CapabilityRegistry.kt:L57`; OperationDispatcher.kt:L22-L31 (files handler); no current end-to-end device result | Qualify OAuth token supplied by user, import/export, grant failure, quota and privacy; BLOCKED without token. |
| `sha256` — SHA-256 verification | partial | `CapabilityRegistry.kt:L59`; OperationDispatcher.kt:L22-L31 (files handler); no current end-to-end device result | Verify digest output against known SHA-256 vectors, wrong hash, large file and cancel. |

## Screens and navigation (14 typed destinations + dock)

| Screen / route | Status | Evidence | Fix/test plan |
|---|---|---|---|
| Home / home | partial | `JumloNavHost.kt:L49, ROUTES.md:L27` | Launch/recreation, Home shortcuts and tab return on device. |
| Tools / tools | partial | `ToolsScreen.kt:L35-L43, ROUTES.md:L28` | Verify search, category grouping, all 45 icons and accessibility. |
| Files / files | partial | `FilesScreen.kt, ROUTES.md:L29` | Verify sorting/search/multiselect, saved folder, SAF and trash actions. |
| Downloads / downloads | partial | `DownloadsScreen.kt, ROUTES.md:L30` | Verify queued/failed/complete links, pause/retry, notification deep links. |
| Settings / settings | partial | `SettingsScreen.kt:L45-L117, ROUTES.md:L31` | Verify persistence, restore/relaunch and locale/theme. |
| Shared tool workspace / tools/run | partial | `WorkspaceScreen.kt, ROUTES.md:L32` | Test pick→configure→run→result and reset/no leaked previous file for every processing tool. |
| Scan / tools/scan | partial | `ScanScreen.kt:L50-L54, ROUTES.md:L33` | Test real capture/galleries/denied permission/scanning fallback. |
| Print / tools/print | partial | `ROUTES.md:L34` | Test Android PrintManager page range/copies/orientation and spool confirmation. |
| PDF viewer / files/viewer | partial | `PdfViewerScreen.kt, ROUTES.md:L35` | 200-page zoom/scroll/search, last position, RTL, no OOM. |
| Job detail / downloads/job | partial | `ROUTES.md:L36` | Verify durable state, output actions, failure recovery, intents. |
| Activity / downloads/activity | partial | `ROUTES.md:L37` | Verify all-jobs list, filters and navigation. |
| Settings detail / settings/details | partial | `ROUTES.md:L38` | Back returns to caller; persistence after route reopening. |
| Storage / settings/storage | partial | `ROUTES.md:L39` | Validate measured bytes, refresh and cache cleanup. |
| Compatibility / workspace | partial | `ROUTES.md:L40` | Warm/cold deep-link and share-sheet intake, malformed payload. |
| Bottom navigation (all roots) | broken | `QuietNavigation.kt:L42-L43` | Product requirement: four tabs Home/Tools/Files/Settings; retain downloads features as destination/shortcut; update tests. |

## Settings inventory

| Visible option / required behavior | Status | Evidence | Fix/test plan |
|---|---|---|---|
| Theme: system/light/dark/AMOLED | partial | `SettingsScreen.kt:L49,L94; Theme.kt` | Persist, restart, verify actual surface contrast. |
| Dynamic color (API 31+) | partial | `SettingsScreen.kt:L51-L59` | Assert OS dynamic color and fallback if disabled. |
| App color picker | partial | `SettingsScreen.kt:L61-L63; M3ColorFactory.kt` | Verify presets/custom contrast and Arabic fallback label. |
| Language EN/AR + RTL | partial | `SettingsScreen.kt:L64-L65; Theme.kt` | Test all routes and errors in RTL, dynamic locale switching. |
| Save folder/SAF reset/open | partial | `SettingsScreen.kt:L67-L74` | Verify persisted grant, actual published destination and revoked grants. |
| Default video quality | partial | `SettingsScreen.kt:L76-L78; UserPreferencesRepository.kt` | Change default to BEST, test chosen real stream without forced 1080p. |
| Default audio quality | partial | `SettingsScreen.kt:L79-L82` | Verify actual audio-only extraction bitrate and output, not just DataStore. |
| Storage usage | partial | `SettingsScreen.kt:L85-L88` | Verify live counts and refresh as files are added/deleted. |
| Clear cache | partial | `SettingsScreen.kt:L89-L90,L118-L123` | Ensure only eligible temporary data cleared; no active job/source loss. |
| About/onboarding/diagnostics/privacy | partial | `SettingsScreen.kt:L91-L92,L104-L115` | Verify callbacks and actual release privacy links/log export. |
| Haptics: enabled/intensity | broken | `UserPreferencesRepository.kt (keys + HapticIntensity); SettingsScreen.kt:L45-L93` | Restore visible working control, test system-disabled behavior and persistence. |
| Reduce motion | broken | `UserPreferencesRepository.kt (respectReducedMotion=false); SettingsScreen.kt:L45-L93` | Add true in-app preference while honoring system setting; test animation snapping. |
| Deprecated Wi-Fi/concurrency preferences | partial | `UserPreferencesRepository.kt (WIFI_ONLY_DEFAULT/MAX_CONCURRENT_DOWNLOADS)` | Remove obsolete migration-only keys or prove no exposed control depends on them. |

## TODO, stubs, inert actions, literals — source findings and coverage

- **Explicit TODO/FIXME:** none confirmed **in the targeted files inspected**, but the GitHub code-search index reported incomplete results; repository-wide count is **NOT VERIFIED** and must not be presented as zero.
- **Confirmed stubs:** no processing handler conclusively identified as a stub from `OperationDispatcher.kt` and sampled implementations. This is not certification that all paths work. The five navigation-only tools are not processing stubs; they must be labeled/located as navigation.
- **Inert/unexposed configuration:** see A2-006, A2-013, A2-014. `SettingsScreen.onRequestNotifications` and older dock callbacks are unconsumed API parameters, not proof of a visible dead button.
- **Hardcoded styling and copy actually observed:** see A2-010 (scanner literal colors), A2-011 (`"Blue"` fallback), A2-012 (Tools section labels), plus static Compose direct colors requiring full scan.
- **Known product limitations, not hidden:** scanned Arabic OCR unavailable; PDF→DOCX not layout-perfect; PDF→PPTX uses image-plus-text; live social extraction needs authorization/availability checks; five compression scan fixtures are required. Keep explicit user-facing messaging.
- **Full scan remaining:** all main Kotlin files, modules (`core-common`, `downloader-core`, `document-core`, `storage-core`), XML resources, tests, GitHub workflows and scripts must be enumerated by a working code index or checked-out repository. This phase was constrained to GitHub API, and GitHub search did not yield reliable completeness.

## Work packages, in verification order

| Branch | Target | Acceptance/gate |
|---|---|---|
| `wp/01-release-quality-gates` | Restore full-media 60MB cap, `lintRelease`, fix CI coverage drift | New CI run, exact measured compressed APK bytes, release lint exit 0; no weakened checks. |
| `wp/02-downloader-core` | Auto BEST/audio, platform errors, persistence, pause/resume/retry/foreground Android behavior | JVM tests, Android 8–15 checks, owner-authorized platform matrix, privacy evidence. |
| `wp/03-pdf-engine` | Page ops, security, conversion/OCR, measured compression, 5 fixtures | Output-based tests across source, large, encrypted and damaged PDFs. |
| `wp/04-scanner-viewer-print` | Camera, edge geometry, PDF reader performance, print spool | Instrumented pixel/render tests, hardware/manual blockers explicit. |
| `wp/05-files-settings` | SAF saves, file operations, visible haptics/reduce-motion and default behavior | Real storage/file round trip, settings persistence+effect tests. |
| `wp/06-tool-experience` | Shared pick/configure/run/result, gesture handling, four tabs | Five complete Compose tool flows plus RTL/reduce-motion. |
| `wp/07-prism-design` | Material tokens, bold light/dark, icons, translations and accessibility | WCAG AA automated ratios + screenshot review, Arabic/RTL, 200% font. |
| `wp/08-release-verification` | Emulator/physical-device, 200 pages/ANR, security/privacy, store assets | Verified artifact hashes, lint/build/size/device results and final risk register. |

**Workflow:** one PR per package; small commits; docs synchronized; no merge while required CI checks fail. All incomplete or unavailable results must be `NOT_RUN` or `blocked: <reason>`, never PASS.

## Current Phase-0 report

- **Completed (source-only):** required-doc read, registry enumeration of 45, capability and dispatch mapping, 14 route inventory, Settings inventory, first-pass confirmed contract mismatches and representative hardcoded-value scan.
- **Verified how:** GitHub contents/tree API at baseline SHA, current `main` Actions run 40106249318 and its job steps. GitHub connector is **not** an Android emulator/terminal.
- **Not verified:** exhaustive `TODO`/literal/control scan, every pick→result flow, all device/emulator tests, release lint/signing/size, live providers and physical performance.
- **Next:** complete the exhaustive audit scanning and PR QA, then implement release-quality gates in `wp/01-release-quality-gates` before feature refactors.

