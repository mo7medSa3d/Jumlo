# Jumlo — verification report

Date: 2026-10-24. **NOT APPROVED FOR PRODUCTION.**

This report is the current authority. The report recovered inside the supplied
archive is preserved under `docs/history/`; its claims and artifact identities
are not current evidence. Source implementation, host execution, Android
compilation, emulator execution and physical-device qualification are separate
statuses and are never summed.

## Environment blocker (this session)

The execution sandbox has **no JDK, no Gradle distribution, no Android SDK, no
emulator/KVM and no physical device**, and the Gradle, Maven Central and Android
download hosts are network-blocked (only PyPI, npm and GitHub are reachable).
Therefore `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`,
`:app:lintRelease`, `:app:assembleRelease`, `connectedDebugAndroidTest` and the
size gates **could not be run**. No APK/AAB was built or measured in this
session. Every result below is a Python host suite or a source/wiring contract;
none of them is an Android runtime PASS.

## Executed this session

| Suite | Observed result | Scope / command |
|---|---|---|
| Python host contract suite | **35 / 35 PASS** | `python3 scripts/<test>.py` for the suites listed below |
| Downloader platform-matrix parity | 4 tests PASS | `python3 scripts/tests/test-platform-matrix-parity.py` — binds the 17-platform plan to `DownloadLinkPolicy` routing/badges |
| Live-qualification runner contracts | 12 tests PASS | `python3 scripts/tests/test-live-media-qualification.py` |
| Compression ladder contracts | 6 tests PASS | `python3 scripts/test-compression-ladder.py` — image ladder, metadata strip, guaranteed-smaller |
| Tools-section contracts | 7 tests PASS | `python3 scripts/test-tools-sections.py` |
| PDF viewer immersive contracts | 12 tests PASS | `python3 scripts/test-pdf-viewer-immersive.py` — slim ≤56dp top bar (back / title / page indicator / overflow), seven-entry overflow menu, immersive auto-hide + system-bar control, removed pages/dark-pages tab row and dead `reader_dark_pages` string, EN/AR key parity, shell hides dock + top bar on the viewer route |
| M3 seed-color factory contracts | 7 tests PASS | `python3 scripts/test-m3-color-factory.py` — seed→M3 light/dark/AMOLED scheme (primary/secondary/tertiary/error + containers/on-colors), WCAG AA `ensureContrast` guard, no hard-coded brand hues in the scheme, wired into `JumloTheme` (non-dead), AMOLED theme mode wired end-to-end with EN/AR strings |
| Page-operation logic contracts | 12 tests PASS | `python3 scripts/test-page-operations.py` — `PdfPageEdit` rotate/extract/split/duplicate + `PageDocumentMerger` renumber + `PageEditHistory` undo/redo (static signatures + behavioral port; kotlinc unavailable so the Kotlin is mirrored in Python) |
| Appearance color picker contracts | 12 tests PASS | `python3 scripts/test-appearance-color-picker.py` — 16 bilingual presets, `M3ColorFactory` hex/HSV helpers, picker component + SettingsScreen wiring, repository hex-accent acceptance, hex parse/format behavioral port |
| Tool scaffold contracts | 6 tests PASS | `python3 scripts/test-tool-scaffold.py` — `ToolScaffold`/`ToolProgressSheet`/`ToolResultScreen` signatures, workspace-top-bar reuse, navigation-bar insets, `action_print` string parity |
| Phase 5 Kotlin-suite contracts | 6 tests PASS | `python3 scripts/test-phase5-unit-tests.py` — `PageOperationsTest` (24 JUnit4 cases) + `M3ColorFactoryTest` (15 JUnit4 cases) + `AppearanceColorPickerTest` (Robolectric Compose) exist and cover reorder/rotate/delete/undo/range + contrast/scheme |
| Android qualification plan | 288 cases written, all NOT_RUN | `python3 scripts/qualify-live-media.py init|plan` |
| Bundled-extractor host probe | 1 probe ran, network-blocked | `qualify-live-media.py probe --platform youtube` → `HOST_EXTRACTOR_ERROR` (TLS/SSL EOF); `direct` → `NOT_RUN_MISSING_URL` |
| Whitespace / diff check | PASS | `git diff --check` |

The 35 passing suites are: `test-media-bootstrap`, `test-media-network-guard`,
`test-media-packaged-runtime`, `test-universal-media-architecture`,
`test-release-media-isolation`, `test-downloads-density`, `test-storage-sql`,
`test-source-static`, `test-redesign-static`, `test-premium-ux`, `test-neon-ui`,
`test-visual-editor`, `test-watermark-studio`, `test-smart-scan-wiring`,
`test-local-profile`, `test-output-workflow-wiring`,
`test-flagship-reliability-wiring`, `test-large-pdf-wiring`,
`test-jumlo-brand-motion`, `test-phase1-to-4-regressions`,
`test-compression-ladder`, `test-tools-sections`, `test-pdf-viewer-immersive`,
`test-m3-color-factory`, `test-page-operations`, `test-appearance-color-picker`,
`test-tool-scaffold`, `test-phase5-unit-tests`, `tests/test-media-artifact`,
`tests/test-processing-presets`, `tests/test-reconstruction`,
`tests/test-scan-intake-recovery`, `tests/test-tool-icons`,
`tests/test-live-media-qualification`, `tests/test-platform-matrix-parity`.

Run them with `bash scripts/verify-host.sh` (the two Java-dependent steps at the
top — `test-core.sh` and `test-pdf-compression.sh` — require a JDK and did not
run here).

## Changes verified by the contracts above

1. **Sectioned Tools catalogue** (`ui/tools/ToolsScreen.kt`): sections, full-width
   headers, Pinned/Recent rows from persisted settings, single search field,
   trailing "All tools" fallback. EN/AR strings added in
   `res/values(-ar)/tools_sections.xml`.
2. **Compression strips document metadata** (`data/pdf/PdfCompressionRunner.kt`):
   the shared measured/publication path now calls
   `PdfBoxBackend.scrubMetadata(document)` before saving each candidate, so the
   info dictionary and XMP are removed as part of the size ladder.
3. **Downloader matrix expanded** (`scripts/qualify-live-media.py`): from 7 to
   all 17 routed platforms (YouTube, TikTok, Instagram, Facebook, LinkedIn,
   X/Twitter, SoundCloud, Vimeo, Reddit, Twitch, Dailymotion, Pinterest,
   Bilibili, Threads, Snapchat, Google Drive, direct), with a parity test that
   fails if a route is added without a plan row.

## Current gates

| Gate | Current evidence | Status |
|---|---|---|
| Android build (`:app:assembleDebug`) | No JDK/Gradle/Android SDK in sandbox; hosts blocked | **BLOCKED** |
| `:app:testDebugUnitTest` | Not run | **BLOCKED** |
| `:app:lintRelease` | Not run | **BLOCKED** |
| Release APK size (60MB cap removed) | 60,000,000-byte cap lifted in verifyCompactApkSize/verifyReleaseApkSize; ARM64-only + yt-dlp payload checks retained | NOT VERIFIED (no build) |
| Signed AAB / production signing | Owner credentials absent | BLOCKED |
| Physical device / emulator | None attached; no KVM | NOT VERIFIED |
| Cold start / frame benchmarks | No qualifying device | NOT VERIFIED |
| Live social-platform extraction | Probe ran; social hosts network-blocked in sandbox | NOT VERIFIED |
| Arabic OCR | Selected recognizer is Latin-only | KNOWN LIMITATION |
| Store publication | Not performed | NOT VERIFIED |

## Known limitations (unchanged, still true)

- The unbundled OCR recognizer is Latin-only. Embedded Arabic Unicode text and
  DOCX RTL are distinct from scanned Arabic OCR.
- PDF→Word preserves paragraphs/conservative headings/RTL; it does not
  reconstruct complex tables or page layout. PDF→PowerPoint uses a rendered
  page image plus a text layer.
- Large-document handling (streaming, WorkManager cancel/resume/progress,
  200+ pages without OOM) is implemented and contract-tested, but device memory
  behaviour is not measured here.
- Social extraction cannot establish content authorization; only public,
  user-initiated downloads are supported, and Play policy risk is documented in
  `PLAY_STORE_LISTING.md` / `PRIVACY_AND_PERMISSIONS.md`.


## 2026-10-28 — GitHub CI evidence update (WP-00 audit)

**Do not reinterpret the 2026-10-24 sandbox-specific blocked results above as having run in that environment.** Independently, the current GitHub `main` SHA `d599b8a3159f38623e3d0b83c3d58a23073e70cf` has a [successful CI run #40106249318](https://github.com/mo7medSa3d/Jumlo/actions/runs/40106249318) dated 2026-10-24. The run's build job passed `-PextendedMedia=true :app:assembleDebug :app:verifyCompactApkSize :app:testDebugUnitTest :app:lintDebug`, media artifact inspection, and artifact upload. The optional lite build and API 26/36 device jobs were **SKIPPED**. This was a build-level result, not a successful end-to-end run of every tool.

For this audit PR: `lintRelease` **NOT RUN**; `verifyReleaseApkSize` **NOT RUN**; physical emulator/device matrix **NOT RUN**; authorized live platform download matrix **NOT RUN**; signed production AAB **BLOCKED** (owner signing material absent); five-scanned-PDF fixture measurements **NOT RUN**. `docs/AUDIT_2.md` documents all 45 entries conservatively as **partial / missing end-to-end qualification**. Full TODO/stub sweep **INCOMPLETE** (GitHub code search returned `incomplete_results=true` and GitHub clone was network-blocked). No new APK or screenshot was generated for WP-00.
