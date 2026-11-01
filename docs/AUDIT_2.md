# Jumlo — AUDIT_2 (source evidence and release gap register)

## 2026-11-01 — Phase 0 audit (new; supersedes nothing, adds code-traced findings)

**Pinned live main:** `82c22c6fcf76232d7f07a28934dc6513836c1703` (2026-11-01 07:07 UTC, verified
via the GitHub API; the uploaded zip snapshot matches this head for the docs read here).
**Method:** read all 10 prerequisite root documents, the 45-tool registry, catalogue,
dispatcher, handlers, settings/theme, navigation and Gradle/CI files; ran
`python3 scripts/audit_source.py` locally; personally re-verified every headline gap below
with grep/sed before writing it. **No Gradle build was run yet for this section and no
device was used, so this is a SOURCE AUDIT, NOT A RELEASE PASS.** Status key:
`partial` = screen renders and dispatches to a real handler in code, but pick → run → result
has **not** been verified on a device/emulator in this audit; `nav` = navigation/viewer-only,
not a processing job; `missing` / `broken` = the code path itself is absent or a no-op.
Nothing in this section is labelled `works` on code reading alone.

**Reproduced locally, 2026-11-01:** `scripts/audit_source.py` → 246 production files,
27 lexical candidates (20 hardcoded colors, 7 dynamic text expressions), **0 TODO / FIXME /
XXX / NotImplementedError and 0 empty `onClick` in production Kotlin**. That zero is not
proof that every click has an effect — the semantic findings below are exactly the cases
a lexical scan cannot see.

### Corrections to the brief's premises (verified, not assumed)

- **Bottom tabs already exist.** `ui/components/QuietNavigation.kt:43`
  `MainDockTabs = [HOME, TOOLS, FILES, SETTINGS]`; `ui/navigation/JumloNavHost.kt:422`
  renders `QuietFloatingDock` as the Scaffold bottom bar. Phase 3's requested tab structure
  is already the structure; Downloads and Print are detail routes, not dock tabs.
- **Tool sections already exist.** `ui/tools/ToolsScreen.kt:52-72` groups into Scan & Capture,
  Organize PDF, Convert, Edit & Sign, Optimize, Protect, Download, Files. The brief's six
  sections are close to, but not identical to, the shipped eight.
- **Every row visible in Settings works** (table below). The dead weight is the reverse:
  real preferences with no UI, and enforcement code with no reachable toggle.
- **The 60 MB cap was NOT lifted.** `app/build.gradle.kts:318-365` still enforces
  `verifyCompactApkSize` and `verifyReleaseApkSize` at 60,000,000 bytes. `DECISIONS.md`
  item 8 (24 Oct, "lift the cap") is stale and is contradicted by current code.
- **Tool count is 45, not 47.** `CapabilityRegistry.kt:8-65`: 44 entries in `allTools` +
  `sha256` appended via `tools` = 45 (40 processing + 5 navigation in
  `core-common/.../CapabilityPolicy.kt:13-47`). Older "47" references are superseded.
- **Wi-Fi-only is not a "pointless toggle to remove" — it is not a toggle at all.**
  Enforcement exists (`data/processing/DownloadOperationHandler.kt:400`,
  `data/media/MediaOperationHandler.kt:65`, `WIFI_REQUIRED`) but the per-download setter
  (`ui/downloads/MediaViewModel.kt:193` `wifi()`) has zero callers and no UI switch, and
  the global default is hardcoded false (`data/preferences/UserPreferencesRepository.kt:150`).
  The same shape holds for HTTP/HTTPS cleartext (`MediaViewModel.kt:192` `allowHttp()`,
  zero callers; `MediaWorkspace.kt:87` normalises with `allowHttp=true` hardcoded) and
  `unmeteredOnly` (`MediaViewModel.kt:194`, zero callers). Deleting the enforcement would
  remove real behaviour; the fix is to expose it in the Advanced panel or document it as
  intentionally fixed — a decision, recorded in `DECISIONS.md`, not a silent deletion.

### Every tool — status, evidence, fix plan

Shared processing screen is `ui/workspace/WorkspaceScreen.kt:81`; shared execute is
`WorkspaceScreen.kt:394` → `AppViewModel.kt:750 submit`. Handlers: P = `PdfOperationHandler`,
O = `PdfOfficeOperationHandler`, I = `ImageOperationHandler`, A = `ArchiveOperationHandler`,
D = `DownloadOperationHandler`, F = `FileExtrasHandler`, M = `MediaOperationHandler`.

| Tool (id) | Status | Evidence (file:line) | Fix plan |
| --- | --- | --- | --- |
| Smart Scan (`scan_doc`) | nav / partial | `ui/scan/ScanScreen.kt:52` → Play-services `GmsDocumentScannerOptions` FULL, page limit 100 (`ui/scan/SmartDocumentScanButton.kt:83-87`); fallback `CameraXScanFallback.kt:60,101` | Device-verify capture; edge/perspective/filters are inside closed Play services and cannot be judged from this repo — do not claim them as app code |
| Smart Scan → JPG export | **broken** | JPEG is requested (`SmartDocumentScanButton.kt:86` `RESULT_FORMAT_JPEG`) but the result handler reads only `pdf` (`:59-66`) and ignores page image URIs; fallback returns PDF only via `onScannedPdf` | P0: consume `pages[].imageUri` and offer JPG export, or stop requesting JPEG; add a test for the chosen path |
| View PDF (`pdf_01`) | nav | `ui/workspace/DocumentWorkspace.kt:38` `ViewerWorkspace` → `ui/viewer/PdfViewerScreen.kt:85` | See broken back button below |
| View PDF back button | **broken** | `PdfViewerScreen.kt:203` `testTag="pdf.back"` calls `onBack`, but `DocumentWorkspace.kt:38` defaults `onBack = {}` and the sole call site `WorkspaceScreen.kt:112` does not pass it (the Shell top bar above still goes back; the viewer's own back does nothing in this flow; `ViewerRoute` flow at `JumloNavHost.kt:494` passes `onBack=::goBack` and works) | P0: pass a real back action at `WorkspaceScreen.kt:112`; add a UI test asserting it |
| Merge PDF (`pdf_03`) | partial | P `structural(merge)`; min-2 rule `WorkspaceScreen.kt:124,163` | P1: dedicated merge pick→result test (none traced) |
| Organize Pages (`pdf_04`) | partial | P `structural(organize)`; visual page grid `WorkspaceScreen.kt:119`; rotate/reorder/delete live here, not as separate ids | P1: dedicated organize test; rotate is 90° steps only — document that limit |
| Split PDF (`pdf_05`) | partial | P `structural(split)`; controls `WorkspaceScreen.kt:819` | P1: dedicated split test |
| Extract Pages (`pdf_06`) | partial | P `structural(extract)` | P1: dedicated extract test |
| Crop PDF Pages (`pdf_crop`) | partial | P `structural(crop)`; visual selection `WorkspaceScreen.kt:119` | P1: dedicated crop test |
| PDF to Word (`pdf_to_docx`) | partial | O; paragraphs + headings + page breaks only, no complex tables/layout (README states this) | Keep the documented fidelity limit; device-verify one real document |
| PDF to PowerPoint (`pdf_to_pptx`) | partial | O; one slide per page, page image + editable text layer, not reconstructed shapes | Keep the documented limit; device-verify |
| Images to PDF (`pdf_02`) | partial | P `imagesToPdf` (A4/Letter/Custom/Auto, margins, per-image budget) | P1: dedicated test |
| PDF to Images (`pdf_07`) | partial | P `pagesToImages` (DPI 36–600, JPEG/PNG, one bitmap at a time, budget-checked) | P1: dedicated test |
| Extract Text / OCR (`pdf_ocr`) | partial | `OcrWebHandler.kt:25` → ML Kit Latin-only recogniser; unsupported-language notice exists in EN and AR (`values/ocr_language_disclosure.xml:3`, `values-ar/...:3`: "Latin-script scans only… Arabic text in a scanned image cannot be recognized here") | Scanned Arabic OCR is an explicit, documented limitation of the chosen recogniser, not a silent failure — do not mark it "fixed" without a new recogniser |
| Convert Images (`img_01`) | partial | I `img_01` conversion branch | P1: dedicated test |
| Annotate PDF (`pdf_annotate`) | partial | P `structural(annotate)` | P1: dedicated test |
| Sign PDF (`pdf_signature`) | partial | P `structural(signature)`; placement editor gated on non-empty placements `WorkspaceScreen.kt:191`; signature pad in `ScanScreen.kt:70+` | P1: dedicated test incl. drag/resize handles |
| Watermark & Page Numbers (`pdf_stamp`) | partial | P `structural(stamp)`; default text preset `WorkspaceScreen.kt:100` | P1: dedicated test |
| Add Image to PDF (`pdf_image`) | partial | P `structural(overlayImage)`; requires exactly 1 PDF + 1 image `WorkspaceScreen.kt:129,164` | P1: dedicated test |
| Fill PDF Form (`pdf_forms`) | partial | P `structural(forms)`; form-ready gate `WorkspaceScreen.kt:118` | P1: dedicated test |
| PDF Metadata (`pdf_metadata`) | partial | P `structural(metadata)`; fields `WorkspaceScreen.kt:881+` | P1: dedicated test |
| Clean Metadata (`pdf_clean`) | partial | P `structural(clean)` | P1: dedicated test |
| Compress PDF (`pdf_compress`) | partial | **4 presets, not the brief's 3:** BEST_QUALITY 240 dpi/90, BALANCED 180/75, SMALL_FILE 135/58, STRONG 108/45 (`document-core/.../PdfPageGeometry.kt:137-145`); ladder keeps strictly-smaller-or-original only (`PdfCompressionRunner.kt:52-120`); before/after bytes exist via `AppViewModel.kt:1082` `measuredCompression()` | P1: confirm the compress UI actually displays both sizes (measure API exists; the UI label was not traced); run the 5-scan-fixture device benchmark — still blocked on fixtures, see below |
| Resize Images (`img_02`) | partial | I `img_02` | P1: dedicated test |
| Crop, Rotate & Flip (`img_03`) | partial | I `img_03` | P1: dedicated test |
| Compress Images (`img_04`) | partial | I `img_04`; `IMAGE_NOT_SMALLER` unchanged outcome `ImageOperationHandler.kt:89` | P1: dedicated test |
| Adjust & Pad (`img_adjust`) | partial | I extras `img_adjust` (`ImageOperationHandler.kt:40,260`) | P1: dedicated test |
| Target Size (`img_target`) | partial | I extras `img_target` (`:280`) | P1: dedicated test |
| Join / Collage (`img_join`) | partial | I extras `img_join` (`:209`); min-2 `WorkspaceScreen.kt:124` | P1: dedicated test |
| Stitch Screenshots (`img_stitch`) | partial | I extras `img_stitch` (`:209,245`) | P1: dedicated test |
| Compare Images (`img_compare`) | partial, search-only | I extras `img_compare` (`:306`); report-only; excluded from the default grid by `ToolCatalogue.isListed`/`searchOnly` | Document as search-only; do not silently delete |
| EXIF Inspector (`img_exif`) | partial | I extras `img_exif` (`:114`); no full tag table per `TOOL_UI_SPEC.md` | P2: decide full-table scope or document the limit |
| Watermark Images (`img_watermark`) | partial | I extras `img_watermark` (`:174`) | P1: dedicated test |
| Password Protect PDF (`pdf_protect`) | partial | P `structural(protect)`; protect **and** unprotect via `field_remove_protection` toggle `WorkspaceScreen.kt:881-887`; AES-128 | P1: dedicated test |
| Video/Audio/Media Downloader (`dl_media`) | partial | M; auto best-selection exists (`downloader-core/.../MediaPolicies.kt:65-69`, `MediaBestSelectionTest`) **but the quality grid is NOT behind Advanced**: `MediaWorkspace.kt:521-545` renders it directly (comment: "Visible directly!") and the Advanced expander (`:610-620`) contains only a filename field | P0: gate the quality picker behind Advanced (auto-best by default) or amend the brief; add a UI test for the default state. Audio-only exists (`MediaPolicies.kt:35-50`, `MediaWorkspace.kt:442-502`) but MP3/M4A/OPUS conversion is build-gated: non-`EXTENDED_MEDIA` builds offer ORIGINAL only (`MediaWorkspace.kt:482-490`) |
| New Direct Download (`dl_02`) | partial | D; direct/Drive intake; link detection covers YouTube/TikTok/Instagram/Facebook/LinkedIn/Drive/direct (`downloader-core/.../DownloadLinkPolicy.kt:40-97,106-143` auto filename) | P1: device-verify one authorised link per platform; live providers remain unqualified |
| Batch URL Intake (`dl_03`) | partial | D; ≤100 URLs; single-URL rule for non-batch `AppViewModel.kt:775+` | P1: dedicated test |
| Download Settings (`dl_settings`) | nav, misleading | Routes to the main Settings screen (`JumloNavHost.kt:405`); hidden from the Tools grid and from search (`ToolCatalogue.kt:124-132`) | P2: rename or remove the entry; it is not a download-settings page |
| Browse Storage (`file_02`) | nav | `openTool` → Files tab (`JumloNavHost.kt:403-407`); hidden from the default grid by `isListed` despite being in the Files section set (`ToolsScreen.kt:70-72`) | P2: fix the section/filter contradiction or document it |
| Create ZIP (`file_07`) / Extract ZIP (`file_08`) | partial | A `create` / `extract` | P1: dedicated tests |
| Encrypted ZIP (`file_encrypted_zip`) | partial | F `encryptedZip`; appears in only 1 test file (catalogue-level) | P1: dedicated test |
| Batch Rename (`file_batch_rename`) | partial | F `rename`; live preview `WorkspaceScreen.kt:105,171`; appears in only 1 test file | P1: dedicated test |
| Duplicate Finder (`file_duplicates`) | partial | F `duplicatesAndTrash` (duplicates branch) | P1: dedicated test |
| Managed Trash Info (`file_trash`) | nav, dead branch | Files tab route; `FileExtrasHandler.kt:39,94,98,104` contains a `file_trash` report branch that is **unreachable** because `OperationDispatcher.kt:27` throws `CAPABILITY_UNAVAILABLE` for non-processing ids first | P1: delete the unreachable branch or make the tool processing — do not leave both |
| Google Drive Import/Export (`file_drive`) | partial | F `googleDrive` (`FileExtrasHandler.kt:216-221`): real Drive v3 client, but a user-supplied OAuth token is required (`LOGIN_REQUIRED` if blank, `AppViewModel.kt:768-772`) | P1: document the token flow honestly; no Drive flow works without it |
| SHA-256 verification (`sha256`) | partial | A `hashes`; expected-digest field `WorkspaceScreen.kt:818` | P1: dedicated test |
| Word / Office → PDF | **missing** | No tool id, no registry entry, no dispatch branch: `OperationDispatcher.kt:35-48` routes only `pdf_to_docx`/`pdf_to_pptx` (PDF→Office), never the reverse | P0 decision: implement it (new tool + handler + tests) or remove it from the Phase 1 list and record why in `DECISIONS.md`. It must not be reported as present in the meantime |
| Downloader pause/resume/retry | partial | Job-level pause (`data/jobs/JobStore.kt:80-85`) and retry (`:106-125`); stream resume via Range/ETag with backoff `300ms·2^(attempt-1)`, max 3 (`downloader-core/.../StreamTransfer.kt:9,49`, `ResumePolicy.kt:7-18`); foreground service + `dataSync` (`worker/EngineOperationWorker.kt:24`, `ProcessingNotification.kt:49`, manifest) | No traced user-visible pause→resume path for an in-progress *media* download in `MediaViewModel.kt` (only analysis cancel `:147,190`) — P1: trace or add it; Android 8–15 behaviour is not device-verified |
| Downloader error messages | partial | Fixed codes `MEDIA_PRIVATE_CONTENT`, `MEDIA_GEO_BLOCKED`, `MEDIA_REMOVED` (404/410), `MEDIA_RATE_LIMITED`, `MEDIA_SESSION_REQUIRED` (`downloader-core/.../MediaFailurePolicy.kt:7-45`) | No distinct expired-link code (falls back to generic `MEDIA_REMOVED`/`MEDIA_EXTRACTION_FAILED`) and no extractor-changed code — P1: add both codes + messages + tests |
| PDF viewer | partial | Zoom 1–5×, double-tap 2.5×, continuous scroll (`PdfViewerScreen.kt:240-242,377-390`); thumbnails (`:280-289`); jump-to-page (`:296-309`); search (`:254-269,494-502`, no dedicated search test traced); night mode is **session-only** (`:115` `rememberSaveable`, not persisted); last position remembered via hashed per-URI DataStore key (`ReaderPositionStore.kt:25,38-42`) | P1: persist night mode or document session-only; add a search test |
| Files page | partial | Save folder `Download/Jumlo/<Video|Audio|PDF|Images|Documents>` (`storage-core/.../StoragePaths.kt:15,23`); sort/filter/search (`FilesScreen.kt:74-103,136,149`); multi-select (`:79-82,160-167`); delete/delete-all moves to trash (`AppViewModel.kt:683-694`), no traced permanent empty-trash path; storage usage refreshed on launch and after trash/rename/cleanup (`AppViewModel.kt:971-984`, `FilesScreen.kt:62,127`); rename with undo (`AppViewModel.kt:567-576`) | P1: trace share/open end-to-end (row callbacks traced, concrete intent path not traced in this pass); add multi-select, delete-all and storage-usage tests; clarify trash vs permanent delete in UI copy |
| Printing | partial | System flow via `PrintManager.print` (`PrintViewModel.kt:323-335`); adapter range handling (`JumloPrintAdapter.kt:37-78`); preview reusing the real render pipeline (`PrintScreen.kt:591-664`); page range (`PrintScreen.kt:235-236`); orientation chips (`:246`) | **No app-level copies control exists** (zero `copies` hits in `printing/` + `Models.kt`) — copies, if any, come only from the Android system print dialog after `print()` is called. Do not add a fake copies field; verify on device and document that the system dialog owns copies, or implement a real one through the adapter |
| Dead code, not buttons | — | `WorkspaceScreen.kt:124,164,222,233` reference `pdf_compare`/`pdf_redact`, which are in no registry, policy or dispatcher and can never be reached via `chooseTool` (`AppViewModel.kt:362` requires `enabled`) | P2: remove the dead branches |
| Dead dock parameter | — | `QuietNavigation.kt:47-57` `QuietFloatingDock(..., onScan = {})`; the dock renders only `MainDockTabs` and never invokes `onScan`; NavHost does not pass it | P2: remove the parameter |

### Settings — every option, traced to its effect

Settings UI = `ui/settings/SettingsScreen.kt` (210 lines); store =
`data/preferences/UserPreferencesRepository.kt` (DataStore `universal_utilities_prefs`).

| Setting | Effect | Verdict |
| --- | --- | --- |
| Theme System/Light/Dark/AMOLED | `MainActivity.kt:61` → `ui/theme/Theme.kt:106-112` | REAL |
| Dynamic color (SDK≥31) | `Theme.kt:115-122` dynamic schemes, Quiet neutrals pinned | REAL, gated |
| App color: 16 presets + custom hex | `Theme.kt:140` `M3ColorFactory.scheme(seedFor(accent))` | REAL |
| Language EN/AR | `MainActivity.kt:50-56` locales + `Theme.kt:173,175` RTL/font switch | REAL |
| Haptics OFF/LIGHT/MEDIUM/STRONG | `MainActivity.kt:63-64` → `ui/util/AppHaptics.kt:91-98` level-gated | REAL |
| Reduce motion | `MainActivity.kt:67` → `LocalReducedMotion`, consumed in `JumloMotion.kt:44,68`, `ProductionRows.kt:171,214`, `QuietButtons.kt:252`, `JumloNavHost.kt:110`, `ZoomPan.kt:179,199`, `PrintScreen.kt:594` | REAL |
| UI sounds | `MainActivity.kt:65` → `AppHaptics.kt:65` | REAL |
| Save folder + Reset | `AppViewModel.kt:880-890` persistable URI → `data/output/PublicOutputStore.kt:45,79-90` (SAF `Jumlo/<category>/`, else MediaStore/legacy); default `Download/Jumlo` | REAL |
| Default video quality BEST/1080/720/480; default audio quality 320/192/128 | `JumloNavHost.kt:515` → `MediaWorkspace.kt:91` → `MediaViewModel.kt:160-165` | REAL |
| Storage usage row; Clear cache; About actions | `StorageScreen.kt`, `AppViewModel.kt:958,971`, `JumloNavHost.kt:490-492` | REAL |
| Font scale SMALL/NORMAL/LARGE | Applied (`Theme.kt:177-178`, 0.91/1.0/1.15) via `AppViewModel.kt:877`, but **no UI row anywhere** | REAL behaviour, missing UI — P1: add a Settings picker or document it as developer-only |
| File view LIST/GRID; quick actions; favourites/recents | Applied in Files/Tools/Home, not in Settings | REAL, correctly placed outside Settings |
| Media-service URL/token (Keystore-ciphered) | `di/AppModule.kt:32` backend selection; empty = on-device only; setters `AppViewModel.kt:878-879` | REAL behaviour, **no UI screen exposes them** — P1: add a guarded advanced screen or remove the setters |
| `notificationsGranted` flag | Written (`AppViewModel.kt:892`, `JumloNavHost.kt:321` launcher) but **never read** anywhere; the OS permission is the real gate. `SettingsScreen.kt:37,39-40` also takes `onRequestNotifications`/`onOpenFiles`/`onChooseFolder` that its body never invokes (file carries `@Suppress("UNUSED_PARAMETER")` at `:34`) | DEAD — P1: remove the flag and the dead parameters, or wire them |
| `maxConcurrentDownloads` (= 2), `wifiOnlyDefault` (= false) | Hardcoded in `settingsFlow` with "fixed scheduler policy" comments | Dead as settings by design — document, do not pretend they are user options |

### Design system, icons, RTL, hygiene

- **Tokens:** one system with a two-layer caveat — `ui/theme/Color.kt` + `Theme.kt` +
  `M3ColorFactory.kt` + `ColorPresets.kt` + `ToolPalette.kt` + `QuietTheme` locals; the final
  scheme is Quiet neutrals + seed-generated primary/secondary/tertiary/error
  (`Theme.kt:140-159`), not a single static palette table. Light primary indigo `#2F46C6`,
  dark primary `#A9B7FF`, AMOLED background pure black. The brief's "hot accent"
  (coral/magenta) exists only as a user-selectable preset, not the default.
- **Per-category colors** (`Color.kt` Light/DarkToolAccents, `ToolPalette.accentFor`): PDF
  rose `#AD1F58`, Images orange `#AA4A00`, Downloads teal `#006F73`, Files indigo `#3B36CD`,
  Organize violet `#692AA3`, Utilities cobalt `#155DA2` (brighter dark partners). Quirk:
  `scan_doc` maps to Files indigo; unknown ids fall back to a hash.
- **Hardcoded colors outside `ui/theme/`:** 240 `Color(`/`0xFF` matches in `app/src/main`,
  171 inside `ui/theme/` (token definitions by design). Remaining pockets, all previously
  triaged in the 2026-10-31 section and still open: splash gradients
  (`JumloSplash.kt:98-166`, ignore light/dark + accent), scan placeholder checkerboard
  (`ScanScreen.kt:95,99`, ignores dark theme), legacy `AccentPickerDialog.kt:40-43`
  swatches that **diverge from `ColorPresets`** (TEAL `#086F6B` vs `#00897B`, CORAL
  `#B63E54` vs `#E64A3A` — a second, inconsistent accent source; trace callers, then
  delete or source it from `ColorPresets`), `AppearanceColorPicker.kt:69` fallback
  `#1E88E5`. Most other matches are document pixels (PDF stamp/render colors, image
  processing), not app chrome.
- **WCAG AA:** tested for generated schemes and tool accents only —
  `M3ColorFactory.kt:29,36-81` (`AA_NORMAL = 4.5f`), `M3ColorFactoryTest.kt` (all seeded
  roles ≥ 4.5 light/dark/AMOLED) and `ToolPaletteContrastTest.kt` (icon contrast ≥ 3:1).
  **Not covered:** splash, scan placeholder, non-seeded colors. **No tokens-preview screen
  exists** in `ui/theme/` — Phase 3 must add one (or Compose previews) for light/dark/RTL.
- **Typography:** "Jumlo Sans" is a rebrand of bundled IBM Plex Sans (variable font) +
  IBM Plex Sans Arabic Regular (`res/font/ibm_plex_sans*.ttf`, `Type.kt:15,19-23,114-117`).
  Scale: display 40, screenTitle 26, sectionTitle 20, body 16, metadata 13, navigation 12,
  badge 11; Arabic gets 0 letter-spacing, 1.42× line height. **Gap:** Arabic has a single
  Regular weight — no bold hierarchy in Arabic by design.
- **Icons — the messiest area:** `ICON_MAP.md` documents only the `ic_tool_*` Material
  Symbols vectors (`ToolIcons.kt:13-59` maps exactly 45 ids; 146 drawables in `res/drawable/`).
  In practice the app primarily uses hand-embedded curated vectors (`ui/icons/Icons.kt`,
  `ui/icons/rounded/Rounded.kt` 89 refs, `automirrored` 9 refs; 273× `Icons.Rounded`,
  33× `Icons.AutoMirrored` across the app, no provenance in `ICON_MAP.md`) **and** a second
  `ic_prism_*` set (`ModernToolIcons.kt:13-56`, used by `NeonUi.kt` tiles while
  `ToolsScreen.kt:263` tiles use `ToolIcon`). The same tool tiles can render from two
  different icon sets depending on screen. Phase 3 must pick one family per surface and
  record provenance for all three, or consolidate.
- **RTL:** first-class, not perfect. Manifest `supportsRtl="true"` + locale config;
  `Theme.kt:173` forces layout direction from the in-app language; `values-ar/` mirrors
  `values/` 1:1 on string counts for all 29 string files (e.g. `strings.xml` 359/359).
  Legitimate LTR exceptions: print preview (`PrintScreen.kt:600`), URL fields
  (`MediaWorkspace.kt:215`, `WorkspaceScreen.kt:320,691`), file-size text. **One suspect:**
  `VisualDocumentEditor.kt:281` uses `AbsoluteAlignment.TopLeft` (non-mirrored) in a
  drag/resize editor — needs a visual RTL check. No `padding(left/right)` found.
- **Hygiene:** zero TODO/FIXME in production; hardcoded UI strings are numbers/symbols
  only (7 literals: degrees, kbps, %, page numbers, ✓, index + file name, angle).
  One i18n smell: `ui/components/QuietFileRows.kt:64-65` hardcodes `"$it صفحة"` /
  `"$it pages"` plurals in code instead of quantity strings.

### Build, CI, tests — what the gates actually cover

- Modules: `:app`, `:benchmark`, `:core-common`, `:downloader-core`, `:document-core`,
  `:storage-core` (`settings.gradle.kts:22-24`); Gradle 8.14, AGP 8.10.1, Kotlin 2.0.21;
  `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36` (`app/build.gradle.kts:44-48`).
  Core modules are JVM-only with no Android imports (enforced by `audit_source.py`).
- `ci.yml` ("Android CI & APK") runs the full required set on main pushes:
  `:app:assembleDebug :app:verifyCompactApkSize :app:verifyReleaseApkSize`
  `:core-common:test :downloader-core:test :document-core:test :storage-core:test`
  `:app:testDebugUnitTest :app:lintDebug :app:lintRelease`, plus Python source-contract
  scripts. Owner-signed `verifyPublishableRelease` runs only when the 4 signing secrets
  exist, otherwise skipped. `android-smoke.yml` runs `:app:assembleDebugAndroidTest` +
  `scripts/run-emulator-smoke.sh` filtered to **14 classes only** — not the full
  instrumentation suite, and it runs no unit tests or lint.
- Test files: `:app` 99 unit + 46 androidTest; each core module 1–2 unit files;
  total 104 unit + 46 androidTest files. Handler-level gap: `ImageOperationHandler`,
  `MediaOperationHandler`, `DownloadOperationHandler` appear in **zero** test files
  (covered only via policy/catalogue/matrix/contract tests); `img_exif`,
  `file_encrypted_zip`, `file_batch_rename` appear in only 1 test file each.
- **No dedicated pick → run → result test for any single tool** is evidenced:
  `docs/testing/tool-matrix.template.csv` marks all 160 processing cases `NOT_RUN`
  and 20 navigation cases `N/A`. That, not code absence, is why most rows above are
  `partial` rather than `works`.
- Prior exact-head evidence (for context, not this audit's run): CI #40982142929 —
  compact 55,378,831 B, release 55,366,447 B, both < 60,000,000; emulator smoke
  #41144898786 — API 26: 72/72, API 35: 73/73, API 36: 73/73, selected suite only.

### Priority fix order (Phase 1 → 4)

- **P0 (broken / missing / brief-non-compliant):** pdf_01 viewer back button; scanner JPG
  export (or stop requesting JPEG); Office → PDF decision (implement or formally remove
  from scope in `DECISIONS.md`); downloader quality picker behind Advanced; expired-link
  and extractor-changed error codes.
- **P1 (verify or wire):** one dedicated pick → run → result test per tool, starting with
  merge/split/organize/compress/downloader/print/files; media pause→resume path; night-mode
  persistence decision; share/open end-to-end trace; empty-trash path; font-scale picker;
  media-service advanced screen or setter removal; `notificationsGranted` + dead
  `SettingsScreen` parameters; unreachable `file_trash` branch; 5-scan-fixture compression
  benchmark; live authorised downloader matrix.
- **P2 (design/hygiene):** splash tokens; scan placeholder dark-theme colors; legacy
  `AccentPickerDialog` consolidation; icon-family consolidation + `ICON_MAP.md`
  provenance for curated and Prism sets; tokens-preview screen (light/dark/RTL);
  `VisualDocumentEditor` RTL visual check; `QuietFileRows` quantity strings; dead
  `pdf_compare`/`pdf_redact` branches; dead `onScan` dock parameter; Arabic bold weight
  decision; `DECISIONS.md` stale items (60 MB cap, 5-tab navigation) corrected.
- **Still blocked / not verified (do not claim):** physical-device testing of any kind;
  camera/scanner hardware; real print output; TalkBack / 200% font / RTL screenshot grid;
  cold-start and frame benchmarks; 200-page scanned-PDF memory; owner production signing
  (4 Actions secrets absent — compact APK remains test-signed); Play Console review,
  privacy-policy URL publication, Data Safety form; full `connectedDebugAndroidTest`
  (unfiltered); scanned Arabic OCR (limitation, see above).

**Completion-criterion status after Phase 0:** criterion 1 (zero broken/stub/partial rows)
is **NOT met** — this audit adds 3 broken items (viewer back, scan JPG, and, as a menu
promise, Office → PDF missing), several brief-non-compliant items, and 40+ `partial`
rows whose only missing piece is device pick → result evidence. Criteria 2–7 are
therefore also not met. The next commit must be a fix, not another audit.

### 2026-10-31 — review of latest source-scan candidates

Exact-code CI log #41144898783 ran `scripts/audit_source.py`, which reports **246 production sources and 27 lexical candidates**; the prior green code-head job #40982142929 printed the individual file/line matches. The 27 are **20 hardcoded colors and seven dynamic text expressions**, **zero basic TODO/empty-callback lexical matches**; this last zero is not proof that every click path has a real effect.

| Locations | Candidate count | Contextual triage | Release action |
| --- | ---: | --- | --- |
| `ui/components/JumloSplash.kt:98-166` | 10 colors | Fixed brand splash gradients/text, not user-selected semantic UI. User's one-token-system requirement still argues for named `SplashTokens` rather than inline constants | Centralize brand-only tokens and test light/dark/high-contrast splash; retain identical visual output until screenshots |
| `ui/scan/ScanScreen.kt:95,99,152` | 5 colors | Signature canvas checkerboard plus deliberate ink swatches; these represent **document pixels**, not app chrome | Preserve exact exported ink/paper semantics; verify contrast and TalkBack selection on both themes |
| `ui/settings/AccentPickerDialog.kt:40-43` | 4 colors | Legacy four-color preview literals differ from `ui/theme/ColorPresets.kt` seeds; current `SettingsScreen.kt:135+` opens `AppearanceColorPicker` | Trace all remaining callers; remove unused dialog only with regression proof, or source preview values from `ColorPresets` |
| `ui/settings/AppearanceColorPicker.kt:69` | 1 color | Hardcoded blue fallback matches `ColorPresets.BLUE` but can drift | Use canonical token seed and preserve invalid input handling |
| `PrintScreen`, `MediaWorkspace`, `PdfViewerScreen`, `AnnotationToolbar`, `SourceOrderStrip`, `VisualWatermarkEditor` | 7 text patterns | Dynamic numbers, angles, file names, keyboard-visible units and a selected-symbol checkmark; not necessarily untranslated sentences | Review Arabic/RTL and screen-reader semantic formatting; keep numeric values dynamic and use localized accessible descriptions |

**No line is called broken solely from this regular-expression scan.** The remaining semantic no-op/dead-button/contrast audit is still pending physical interaction and screenshot qualification.


## 2026-10-31 — current-main revalidation (supersedes historical defect statuses below)

**Pinned code commit:** `89cdb51db7199741925f6bf908a9704a740b6eff`. **Method:** read the 10 prerequisite root documents, current registry/catalogue/dispatcher/settings/theme/Gradle and the exact-head GitHub Actions job logs through GitHub API. No code was modified or local Gradle command run. This remains a **SOURCE AUDIT, NOT A RELEASE PASS**; the 2026-10-28 baseline findings below are preserved as historical records and must not be interpreted as defects confirmed at this newer commit.

### Current evidence and limits

| Gate | Exact-head evidence | Disposition |
| --- | --- | --- |
| Compilation, JVM tests, lint | [Android CI & APK #40982142929](https://github.com/mo7medSa3d/Jumlo/actions/runs/40982142929): `:app:assembleDebug`, `:app:testDebugUnitTest` (Gradle reported `FROM-CACHE`), `:app:lintDebug`, `:app:lintRelease`, pure core-module test gates; overall `BUILD SUCCESSFUL` | **PASS on pinned SHA**, not an independent local run |
| Full-media size gates | Same run: `:app:verifyCompactApkSize` **55,378,831 / 60,000,000 bytes** and `:app:verifyReleaseApkSize` **55,366,447 / 60,000,000 bytes**; verified the expected bundled media payload | **PASS on pinned SHA**; compact is test-signed, not an owner-signed release |
| Android emulator smoke | [Android UI and persistence smoke #40982142945](https://github.com/mo7medSa3d/Jumlo/actions/runs/40982142945): three successful `:app:connectedDebugAndroidTest` jobs (API 26, 35, 36) | **PASS for the selected smoke suite only**, not the complete device matrix |
| Source lexical scan | Same main CI log: **246 files, 27 candidates**; see `scripts/audit_source.py` for the exact lexical rules | **REVIEW REQUIRED**. Not a Kotlin AST sweep, interaction audit or proof of zero dead callbacks |
| Full tool qualification | `CapabilityRegistry.kt:8-65` registers **45 IDs**: `CapabilityPolicy.kt:13-47` classifies 40 processing and five navigation; `docs/testing/tool-matrix.template.csv` has 160 processing cases `NOT_RUN` and 20 navigation cases `N/A` as processing, each requiring separate navigation evidence | **NOT VERIFIED** for 45 complete user-facing flows |
| Production release | Owner signing secrets, actual production certificate lineage, physical device, five scanned-PDF corpus, live authorized downloader providers, 200-page RAM/frame benchmarks and Play policy qualification | **BLOCKED / NOT VERIFIED** as applicable; see `RELEASE_CHECKLIST.md` |

### Reclassification of stale 2026-10-28 source findings

| Historical concern | Pinned-source evidence | Current judgement / remaining proof |
| --- | --- | --- |
| Broken APK size cap | `app/build.gradle.kts:318-365` enforces the decimal 60MB limit for both full-media APKs; corresponding CI tasks passed | **Source-resolved; CI verified** |
| CI skipped release lint | `.github/workflows/ci.yml:88-90` invokes `:app:lintRelease`; passed in latest job | **Source-resolved; CI verified** |
| Reduce motion hardcoded false | `UserPreferencesRepository.kt:142,220-222` reads/writes real DataStore preference; `SettingsScreen.kt:80-97` exposes the toggle | **Source-resolved; device animation and system-policy checks pending** |
| Wi-Fi-only and concurrency dead setters | `UserPreferencesRepository.kt:148-150` retains non-editable compatibility defaults; old persisted setter methods are absent; visible `SettingsScreen` has no such switches | **Removed as visible options**; inspect migration/old-data handling on device |
| Catalogue ignored hidden policy | `ToolsScreen.kt:98-120` uses `ToolCatalogue.isListed` for ordinary results and `isSearchable` for search, with pinned/recent filters | **Source-resolved**; touch/search/UI RTL still need full-flow testing |
| Ghost `pdf_redact`/`pdf_compare` dispatch routes | `OperationDispatcher.kt:34-50` no longer routes either ID | **Source-resolved**; test registry/dispatch exhaustiveness on device |
| Incomplete hardcoded colors/strings review | `scripts/audit_source.py` identifies lexical candidates (latest 27), most centralized tokens. Inline ink/paper pixels may be intentional representations rather than UI chrome | **Not exhaustively validated**; context review and light/dark WCAG AA + RTL needed |

### Source-backed scope of Phase 0

The existing **45 tool rows** in section 1, **16 route/overlay rows** in section 2, and visible + hidden settings rows in section 3 remain the itemized inventory. All 45 tools are **partial pending full pick → configure → run → result device/output evidence**, including the five navigation aliases requiring route-based proof instead of jobs. These conservative labels reflect missing acceptance evidence, not a conclusion that all 45 implementations are broken. Existing local/host tests prove only their asserted contracts.

**Priority P0 acceptance blockers:** complete the 160 processing matrix on controlled normal/large/encrypted/corrupt fixtures; stage/verify all five scan PDFs; test real live downloader sources with rights and audio-only outcomes; test pause/retry/background across API 26–36; test large PDFs and printing on a physical device; verify owner-signed install/update and privacy/Play Console review. Do not turn `NOT_RUN` into `PASS` from compilation or source inspection.

**Local execution blocker (this audit):** this conversation runtime has GitHub API access but no downloadable git checkout (GitHub DNS cannot resolve), no configured Android SDK and no Gradle cache. The latest remote Actions are the only fresh build evidence here. Consequently **no Android product code was changed or pushed**; future code changes require their own test-before-push verification gate.

---

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
| Reduce motion (persistent; device proof pending) | partial | `SettingsScreen.kt:80-97; UserPreferencesRepository.kt:142,220-222` | Device-verify persisted motion, system policy and transition coverage |
| Wi-Fi only (hidden compatibility state) | partial | `UserPreferencesRepository.kt:150` | No visible control or setter; verify legacy migration/compatibility behavior |
| Concurrency count (fixed scheduler compatibility state) | partial | `UserPreferencesRepository.kt:148` | No visible control or setter; confirm fixed scheduler behavior under stress |
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

## WP10 source-confirmed catalogue mismatch — 2026-10-28

Before this fix, `ToolsScreen` passed every available capability to the default catalogue despite `ToolCatalogue` intentionally hiding navigation aliases and search-only specialists. Updated browse/pinned/recent/explicit search with `isListed` and `isSearchable` contract without deleting operations. JVM and Compose tests added; merged-base CI/devices still need verification.

## 2026-10-28 — automated source inventory and review

- `scripts/audit_source.py` inventories app Kotlin/Java plus the four Android-free modules, recording exact file:line source candidates; it also checks pure module imports. It does not execute UI controls or tool workflows.
- [GitHub Actions #40641786606](https://github.com/mo7medSa3d/Jumlo/actions/runs/40641786606) ran 5 original scanner tests successfully and scanned **244 source files / 27 candidates**: **20 literal-color occurrences** (including branded splash, selectable pen inks, accent swatches and scan preview canvas), **7 inline/dynamic label patterns** (page numbers, rotation, bitrate, zoom and checkmarks). There were no matches in the scanner's limited TODO/empty-callback patterns. These are **review leads**, not 27 proven bugs or 27 resolved findings.
- The new sixth scanner test requires zero Android imports in pure JVM modules; regular CI now runs all scanner tests before Gradle. The reconciled branch's CI result is pending. Report artifacts are available in [source inventory Actions #40641825217](https://github.com/mo7medSa3d/Jumlo/actions/runs/40641825217).
- **P0 ABSENT:** Word/Office → PDF is missing entirely from the registry. Tracked in [issue #47](https://github.com/mo7medSa3d/Jumlo/issues/47); PDF → Word/PowerPoint does not satisfy the reverse capability. No acceptance claim is made.
- Still **NOT VERIFIED:** device execution across all registered tools, true gesture behavior, large/corrupt inputs, OCR language support, real provider downloads, end-to-end signed release.

## WP07 stale page-order source defect

PrintPageSelection.move and PrintViewModel originally reused malformed saved pageOrder even when the renderer showed the original order; UI manipulation could silently change the wrong pages. Source patch normalizes stale order and preserves selected source pages. Regression tests added; latest-head CI and device print-provider acceptance remain pending.

## WP12 file/archive Unicode spoof control — 2026-10-28

Found: `SafeNames.basename` and `fitUtf8` validated path separators and invalid surrogates but admitted invisible U+202E and related bidi formatting controls, which could disguise a filename extension in Files and archive views. Code fix proposed in `wp/12-safe-bidi-document-names` with four JVM cases. **NOT VERIFIED** until matching GitHub CI; all 45 tool flows remain subject to separate end-to-end evidence.

## WP13 split-range allocation vulnerability — 2026-10-28

`PageSelection.groups` eagerly expanded each requested range with `.toList()` without any aggregate budget, so repeated `1-10000` expressions could exhaust memory. New source guard rejects expanded page lists over 100,000 with `PAGE_SELECTION_LIMIT`; 200-page and Arabic-Indic test fixtures added, alongside bilingual error copy. Runtime tests pending; do not elevate functional status without CI/device evidence.
