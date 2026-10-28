# Changelog — current production-hardening candidate

## 2026-10-28 — WP07 print-page reorder recovery (tests pending GREEN)

- `PrintPageSelection.normalizedOrder` falls back to the currently rendered source order when a persisted permutation is missing, duplicated or references removed pages, matching `arrange` semantics.
- `PrintViewModel.movePage` now delegates to `applyPageOrder` rather than editing order alone, so visible page selection follows original source pages through move operations.
- Drag-permutation and per-page rotation use the same normalized visible source IDs. All edits remain non-destructive until actual printing.
- Added five JVM regression cases in `PrintPageSelectionTest` and `PrintViewModelTest`.
- **TDD RED confirmed:** [GitHub Actions #40640984856](https://github.com/mo7medSa3d/Jumlo/actions/runs/40640984856) listed all five new tests as FAILED against previous source; **GREEN pending** final code CI. Android PrintManager/spooler remains device NOT_RUN.


### 2026-10-28 — wp/01 release gate correction (verification pending)

- Restore the strict 60,000,000-byte limit in full-media compact and release APK Gradle tasks; fail over-limit artifacts without trimming functionality.
- Normal GitHub CI now runs `:app:lintRelease` alongside `assembleDebug`, `testDebugUnitTest` and the compact APK size gate.
- These are source changes, **not yet a passing CI result**; capture the work-package run before merging.


Date: 2026-10-23. Based on the uploaded Jumlo 1.6-tools source. This describes
changes made in this execution, not a declaration that each feature has passed
Android qualification. See TEST_REPORT.md for current artifact and test status.

## 2026-10-24 session (Android build NOT verified — no JDK/SDK in sandbox)

- Phase 2 — page-operation core logic in `document-core` (Android-free): extend
  `PdfPageEdit` with per-page rotation (`rotate`/`rotateSelected`, mod-360),
  `extractSelected`, `splitEvery`, `duplicate`, `selectAll`/`invertSelection`;
  add `PageDocumentMerger` (concatenate + renumber + carry rotation) and
  `PageEditHistory` (undo/redo over immutable snapshots). All ops immutable
  (non-destructive). `scripts/test-page-operations.py` (12 tests: static +
  behavioral port).
- Phase 1 — shared `ToolScaffold` for every tool page: reuses
  `QuietWorkspaceTopBar`, a scrollable content area, and a sticky bottom action
  bar with navigation-bar insets; plus `ToolProgressSheet` (progress overlay) and
  `ToolResultScreen`/`ToolResultAction` (Open/Share/Save/Print/Use-again + Close).
  `scripts/test-tool-scaffold.py` (6 tests).
- Phase 4 (continued) — Appearance App-color picker: `M3ColorFactory` now accepts
  `#RRGGBB` hex accents directly; 16 bilingual (EN/AR) `ColorPresets`;
  `AppearanceColorPicker` (ModalBottomSheet) with live preview, 4-col preset grid,
  HSV hue/sat/val sliders, hex field, reset/apply; wired into SettingsScreen;
  repository accepts hex seeds. `scripts/test-appearance-color-picker.py`
  (12 tests).
- Phase 5 — verification: Kotlin JUnit4 unit tests `PageOperationsTest` (24 cases:
  reorder/rotate/delete/extract/split/merge/undo-redo/range-parsing) and
  `M3ColorFactoryTest` (15 cases: WCAG contrast, AA guard, seedFor/hex/scheme/
  HSV); Compose UI test `AppearanceColorPickerTest` (Robolectric). Kotlin suites
  run via `./gradlew :app:testDebugUnitTest` in a proper environment (kotlinc/
  Gradle unavailable in this sandbox). `scripts/test-phase5-unit-tests.py`
  (6 static contracts). `scripts/verify-host.sh` extended with the 4 new suites.
- All tools functional + limits lifted (owner request): enable `file_drive`
  (Google Drive Import/Export — real Drive v3 impl, token-gated); remove
  `webpage_pdf` (dead route — dispatched to `OcrWebHandler` which rejects
  non-`pdf_ocr`) and `xf_05` (expansion concept, no handler/crypto backend)
  from registry, dispatch, workspace form, UI, strings (EN/AR) and icons
  (drawables deleted). Registry now 45 tools, all enabled/visible. Lift the
  60,000,000-byte APK size cap in `verifyCompactApkSize`/`verifyReleaseApkSize`
  (ARM64-only + yt-dlp payload checks retained). Lift tool-list/preset display
  caps: `recentToolIds` (8→all), `mostUsedToolIds` (5→all), `top` (5→all),
  processing presets (16→all). Updated `DECISIONS.md` #7/#8.
- Reorganize the Tools catalogue into the brief's sections — Scan & Capture,
  Organize PDF, Convert, Edit & Sign, Optimize, Protect, Download, Files — with
  full-width headers, Pinned and Recent rows fed by persisted
  `quickActionIds`/`recentToolIds`, a single search field, and a trailing
  "All tools" fallback so a newly registered tool is never hidden. EN/AR strings
  added in `res/values(-ar)/tools_sections.xml`.
- Strip document metadata (info dictionary and XMP) in the shared PDF
  compression pipeline (`PdfCompressionRunner`) before each candidate is saved,
  completing the compression ladder's metadata step.
- Replace the hard-coded per-accent primary/container colors with a proper
  seed-generated Material 3 color system (`M3ColorFactory.kt`): one seed color
  derives primary/secondary/tertiary/error + their containers/on-colors for
  light and dark, with a WCAG AA contrast guard (`ensureContrast`) and a neutral
  tonal surface ladder kept stable. Seed mapping (`seedFor`) covers TEAL/CORAL/
  VIOLET plus a default. Add an `AMOLED` theme mode (true-black surfaces) to
  `ThemeMode`, wired through `JumloTheme` (`amoled = isAmoled`) with EN/AR
  strings (`pref_theme_amoled`). Dynamic Color (Android 12+) still routes to
  the system wallpaper palette. Static contract suite
  `scripts/test-m3-color-factory.py` (7 tests) added.
- Redesign the PDF viewer into an immersive, content-first reader: one slim
  (≤56dp) top bar (back, one-line title, "N / total" page indicator, overflow), a
  seven-entry overflow menu (open another file, night mode, thumbnails, search,
  share, print, file info), auto-hiding chrome and system bars after 2s, on-demand
  thumbnail/search/file-info sheets, keep-screen-on, and removal of the old
  pages/dark-pages tab row. Print routes through the existing
  `AppViewModel.importForPrint` pipeline. EN/AR strings added in
  `res/values(-ar)/pdf_reader.xml`; dead `reader_dark_pages` removed.
- Expand the downloader qualification matrix from 7 to all 17 routed platforms
  and add `test-platform-matrix-parity.py`, which fails if a route is added
  without a plan row or a plan row points at an unrouted host.
- Executed 29 Python host contract suites (all pass), generated a 288-case Android
  qualification plan, and ran one bounded bundled-extractor probe (network-blocked
  in this sandbox). Android build, lint and device gates remain BLOCKED.

## Fixed

- Preserve coroutine cancellation and fatal errors at ViewModel boundaries, show recoverable failures to users, and record sanitized operation diagnostics.
- Surface failed public saves while keeping validated private outputs; retry only the visible copy. Guard URI ownership across cancellation and database handoff, including unchanged/larger output outcomes.
- Release job leases after malformed snapshots and allow eligible jobs to recover expired leases without accepting stale-worker writes.
- Make a subsequent explicit New run recover from an earlier persistence failure while continuing to block submission on the latest failed reset.
- Normalize download extensions, preserve Drive resource keys, map age/private/removed/network/rate-limit failures, and keep local finalization independent of current network connectivity.
- Prevent optional-backend token reuse across origin changes; atomically read endpoint/token and migrate plaintext preferences into Keystore-encrypted storage.
- Sanitize the optional Python service's default server-error path so client addresses and raw exception URLs do not leak through inherited socketserver logging.
- Replace formula-based PDF compression estimates with the actual export runner, with cleanup and cancellation.
- Reject blank DOCX conversion, preserve structured headings/paragraphs and Arabic RTL properties, and wire all PDF protection permission toggles.
- Correct signature alpha/rotation/multiple-placement output, watermark geometry/opacity, annotation highlight bounds and form-widget page geometry.
- Apply independent per-page crop overrides and freeze batch-rename dates so previews agree with execution.
- Pin outgoing shared-transition identity to its navigation entry, separate Home/Tools identities, return to the calling tab, and count navigation-only tools in usage ranking.
- Validate nullable output parent paths explicitly instead of relying on platform-nullability assumptions.
- Retain input-stream ownership across the IO-to-caller cancellation handoff, closing an unclaimed stream without replacing the original failure.
- Use count-neutral labels for 20 active quantity keys while preserving all English/Arabic formatting contracts.
- Accept CameraX's documented public `libjnigraphics.so` dependency while retaining the strict native dependency and alignment checks.
- Correct eight defects confirmed in actual English/light captures: the encrypted-ZIP action label, the batch-rename starting-counter label and vector viewport, and five English/Arabic range labels. Keep the official vector path and all execution domains unchanged.
- Remove the remaining default borders from five standard FilterChip sites after actual capture exposed outlined adjustment presets; preserve media choices, viewer toggles and adjustment actions.
- Route warm workspace links through the canonical tool path and refresh its captured tab using a lambda in `rememberUpdatedState`. The first canonical-only correction still failed the unchanged native Back-to-Files test; a subsequent host trace and exact Kotlin/Compose bytecode identified an initial-Home callback retained by function-reference equality. Preserve the cold-link registration unchanged. The latest matching candidate-2 artifact passed the unchanged native warm-link regression; cold-link execution remains unverified.
- Align RTL comparison clipping, divider and drag coordinates with localized Before/After badges and slider progress; render only the batch-rename machine-pattern value as LTR. The unchanged native component regression reproduced the pixel defect before correction and passed in the subsequent API 26 two-method run; the warm-link case in that same run failed. The later matching candidate-2 pair now passes both methods. Corrected host screenshots were reviewed separately, with their blocked states and static-image limits retained.

## Added

- Bounded structured crash/nonfatal logs, Android crash-handler delegation, an optional reporting sink disabled by default, and debug StrictMode.
- 47 distinct pinned official Material Symbols Rounded filled tool vectors, nine annotation vectors and complete source/license mapping.
- Searchable flat usage-ranked tool catalogue, synchronized frequent tools on Home and shared tool-to-workspace icon transitions.
- Interactive text/logo watermark studios; transparent signature pad and reusable signature library; multiple placements; real form overlays; annotation styles; crop handles; thumbnail page editing; merge order/count; image comparison divider and collage slot gestures.
- Actual OCR text/copy/export panel and streamed duplicate-hash result cards.
- Direct multipage ML Kit PDF/JPEG scanning with CameraX/gallery fallback and bounded EXIF-normalized fallback PDF export.
- Behavioral host and Android regressions for cancellation, storage/provider errors, recovery, credentials, PDF/Office/studio outputs, gestures, large fonts and catalogue behavior.
- Strengthen two existing native methods without increasing the 139-method inventory: repeat warm-link/Back/Files/Tools checks twice, and check comparison pixels, badge positions, divider dragging, semantic progress and slider-track taps in both directions and themes. Source assertions and runtime results remain separately recorded.
- Real Roborazzi activity/fixture screenshot harness with explicit coverage manifests, visible-route/title checks, actual preview readiness and failure diagnostics. Block unsupported host native-PDF states and reject stale frames as evidence.
- Keep bounded real Back actions and progress checks in capture drivers before root-tab selection, with strict route/readiness assertions. A changed workspace title is not proof of a successful Back pop; preserve the actual failure and navigation trace.
- Opt-in native Android capture variants for English/Arabic and light/dark, using real activity/imported fixtures and a required clean test library. Captures and tool-operation acceptance remain separate; missing external fixtures stay explicitly blocked.
- Encoded image conversion, compression and target-size sample previews using the production encoder, with actual sample dimensions/bytes and visible sampling limits.
- Owner-signing configuration and strict full-runtime ARM64 APK size/publishable-release gates.
- Current audit, decisions, route/UI/icon specifications, privacy/permission drafts, Arabic/English store copy, release checklist and honest verification matrices.

## Changed

- All enabled tools remain directly reachable in the flat grid instead of being hidden behind category groupings.
- Newly edited controls use tonal surfaces; the existing Arabic/English fonts, black dark theme, five-tab navigation and allowed settings are retained.
- Backups exclude private files, databases, preferences and external app data from cloud backup and device transfer.
- Host, emulator and physical-device evidence are reported separately. Recovered reports are historical, and no old artifact hash or test result is promoted to current evidence.
- AndroidX host test dependencies are aligned with the existing Android test versions for the genuine screenshot harness.
- CI source configuration runs the device job on ordinary push/PR events and enforces the full-media release size, AAB and lint gates; no remote CI execution is claimed.

## Removed

- No functioning tool was removed to satisfy APK size or testing.
- Formula-only compression prediction, plaintext backend-token persistence and raw service exception logging were replaced by the implementations above.

## Candidate-2 verification checkpoint

- The [matching API 26 native pair](verification/current/device/api26-ui-regression-candidate2/CURATION_NOTES.md) records **2 PASS / 0 FAIL / 0 SKIP**, with 137 other instrumentation methods not run in that pair. The unchanged warm-link method covers its repeated Files/workspace/Back path; the unchanged comparison method covers four language/theme configurations. JUnit duration was 109.115s, not an app performance measurement.
- [Host screenshot attempt 6](verification/current/screenshots/attempt6/README.md) records **4 capture methods PASS**, **260 recorded PNGs visually reviewed**, and **124 explicitly blocked states**. Canonical [host captures](SCREENSHOTS/roborazzi/README.md) are byte-identical to that fresh run. The known Compare Images heading-only card remains disclosed; static capture is not Android/tool-operation acceptance.
- The pair and captures bind to the same 747-input candidate-2 fingerprint `6d6663e62a5d76effe2905d17029a309162d3aab3f656f9f0763696ebfabb558`. The native pair uses app `51f9ccef05dd7065d6e9dd0a3faf7a72802b8a924130da49daab4db17cd68e59` and test `02e9bd40e25373c8e55cf865a6b8a6a9ff09eb9dd256e6b05f67aa637dfcb5f5`. Earlier native failures and [attempt 5's host preview timeout](verification/current/screenshots/attempt5/README.md) remain preserved. The unchanged successful host retry does not establish the timeout's cause or erase that failure.

## Known release blockers

The originally reported paste crash still requires a matching reproduction and
logcat root cause; defensive changes alone do not establish it. The three
pre-existing disabled registry entries remain blocked. Owner signing, Play
declarations/policy review, the complete live-provider matrix, physical-device
qualification and measured performance/profile acceptance must be completed
before publication. Exact remaining failures and NOT VERIFIED items belong in
TEST_REPORT.md.

