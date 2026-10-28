# Jumlo — verification report

## 2026-10-28 — WP06 lint and size source-guard integration

The older WP06 commit `7fbab51b8e233f38a6a2a7a58120164ef5e12c02` completed both [Actions #40641996652](https://github.com/mo7medSa3d/Jumlo/actions/runs/40641996652) and [#40641999746](https://github.com/mo7medSa3d/Jumlo/actions/runs/40641999746) successfully. This follow-up merges the established KSP2 serialization fix from main, retains full-size checks, adds `:app:lintDebug`, and runs `scripts/test-compact-configuration.py` as a required CI step. **Current combined head: NOT VERIFIED until a fresh run completes.** Device tests, external download integrations, signing and Play Console remain NOT RUN.


## 2026-10-28 — WP03 settings preferences (current-head CI PENDING)

Source changed: `MainActivity.kt`, `UserPreferencesRepository.kt`, `SettingsScreen.kt` and bilingual `account_settings.xml`. Haptics intensity and Reduce motion now have user-facing persisted controls. `PreferencesPersistenceTest` extends its Android assertions. Current-head PR CI has **NOT RUN**; physical haptics/RTL/device motion checks are **NOT VERIFIED**. Previous 2026-10-28 PR CI [#40639799895](https://github.com/mo7medSa3d/Jumlo/actions/runs/40639799895) succeeded at baseline head `b0e0ddac`, but was prior to the latest main CI gate and is not acceptance for the merged-base changes.


## 2026-10-28 WP01 — strict release gates (GitHub CI verified)

- Both compact/release full-feature APKs now enforce 60,000,000 decimal bytes; current `main` already contains that size check from PR #11. The follow-up workflow enables full release-artifact measurement and lintRelease on pull requests, including documentation changes and `wp/**` branches.
- Exact prior WP01 branch [Actions run #40639445934](https://github.com/mo7medSa3d/Jumlo/actions/runs/40639445934) **PASS** on `9571208894ca0c85d0a6b8740450309f538104f0`: compile, unit tests, lintRelease, compact 55,368,299 bytes, release 55,356,059 bytes. A fresh merged-base CI result is required before integrating this update into main.
- Device/emulator flows, release keystore, large scanned-PDF fixtures and Play review remain **NOT VERIFIED**.


## 2026-10-28 work-package verification — release quality gates

- `wp/01-release-quality-gates`: **NOT RUN** until the branch GitHub Actions job reports a result. Source now has `check(apk.length() <= 60_000_000L)` for both compact and release full-media artifacts, and normal CI requests `:app:lintRelease`.
- Baseline `main` Actions run [#40106249318](https://github.com/mo7medSa3d/Jumlo/actions/runs/40106249318): debug build + tests + lintDebug succeeded, compact measured 55,368,299 bytes. It did not run release lint and was not gated by the size maximum.
- Device runs, release signing, Five-PDF-fixture compression, 200-page memory and live authorized downloader matrix remain **NOT VERIFIED**.
- Do not replace or overwrite the October 24 report's original session history with new claims.


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
| Release/compact APK size | Both enforce 60,000,000 decimal bytes in current source; branch CI #40639445934 measured 55,356,059/55,368,299 bytes | PASS for that exact branch SHA; merged-base run pending |
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

## 2026-10-28 — WP02a regression status

- `PreferencesPersistenceTest.appearanceFolderQualityAndViewSurviveRepositoryRecreation` now includes a `#7B4DFF` DataStore round trip and invalid-accent rejection. **NOT RUN** on Android against this branch; monitor CI and instrumented job. No UI screenshot accepted for this source fix.

## 2026-10-28 — WP08 KSP2 worker isolation (release-gate-integrated)

- Confirmed failed run [#40640893645](https://github.com/mo7medSa3d/Jumlo/actions/runs/40640893645) in PR #33: simultaneous kspDebugKotlin, kspCompactKotlin and kspReleaseKotlin crashed with `KaInvalidLifetimeOwnerAccessException` and `PSI has changed since creation`.
- This isolated CI change retains `assembleDebug`, `verifyCompactApkSize`, `verifyReleaseApkSize`, `testDebugUnitTest` and `lintRelease`. The worker workaround is NOT VERIFIED until matching-head Actions complete; it is not device or functionality evidence.

### WP08 measured CI evidence — 2026-10-28

- [GitHub Actions run #40641460117](https://github.com/mo7medSa3d/Jumlo/actions/runs/40641460117), exact source SHA `7de0eeeb2ae68c8eba3ebc8b283f9e9ec561eabd`: **PASS** — `scripts/test-ksp-ci-serialization.py` 2/2, `:app:assembleDebug`, `:app:verifyCompactApkSize`, `:app:verifyReleaseApkSize`, `:app:testDebugUnitTest`, `:app:lintRelease`, media runtime inspection. `BUILD SUCCESSFUL in 3m 17s`. Compact ARM64 APK **55,368,299 bytes** / 60,000,000; release ARM64 APK **55,356,059 bytes** / 60,000,000. This is a GitHub CI result for that exact SHA, not an emulator or device test.
- A separate run for the same SHA was still in progress at the time of this documentation update. The commit containing this paragraph itself requires new CI checks before merge; never transfer PASS to a different SHA without inspecting it.

## 2026-10-28 — WP12 CI duplication mitigation

| Check | Status | Evidence |
|---|---|---|
| CI event matrix: no duplicate WP push builds | NOT RUN until PR CI | scripts/tests/test-ci-event-matrix.py; PR builds still trigger on all main-targeted pull requests |
| Full-media assemble/test/lint/size gates | NOT RUN on WP12 yet | Existing mandatory job, same commands and artifacts; no checks waived |
| Explicit lite and device workflows | Still opt-in | workflow_dispatch preserved; device acceptance remains separate |

The CI efficiency change cannot be considered verified until the PR's own checks run. Other currently queued runs were created before this change and will not be retroactively canceled.

## 2026-10-28 — WP-08 R8 release CI OOM

[PR #31 run 40644691679](https://github.com/mo7medSa3d/Jumlo/actions/runs/40644691679) **FAILED** at `:app:minifyReleaseWithR8` with `Java heap space` while parsing a dependency class signature. Current `gradle.properties` declares `org.gradle.jvmargs=-Xmx2560m`. Full-media release CI now explicitly passes `-Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"` and maintains `--no-parallel --max-workers=1`. This does not weaken R8, byte-size gates or compilation; the new branch and previously failed PR must receive completed green results to qualify. **New run NOT YET VERIFIED** and this does not replace emulator/device testing.
