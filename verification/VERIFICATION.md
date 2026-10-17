# Jumlo — Implementation and Verification Evidence (Updated 2026-10-17)

Date: October 17, 2026. Exact UTC start times, command arrays, and exit codes are recorded in `verification/final/*.log` and `verification/final/results.json`.

## Summary of 2026 Fixes Applied

1. **Core Tools & Guaranteed-Smaller Compression**:
   - `PdfBoxBackend.kt` & `PdfOperationHandler.kt`: Fixed `PDF_COMPRESS` bug where `LosslessFactory.createFromImage` re-encoded JPEG images as larger lossless Flate streams. Replaced with `JPEGFactory.createFromImage` + 3-stage `compressPdfGuaranteedSmaller` (structural pass → tighter structural pass → progressive page-by-page JPEG rasterization via `PdfRenderer`) guaranteeing `output.length() < source.length()`.
   - `BitmapTransforms.kt` & `ImageOperationHandler.kt`: Added `encodeGuaranteedSmaller` so `img_04` and `img_target` always produce files strictly smaller than the input, and recorded `originalSizeBytes` in `OutputMetadata`.
   - `ProductionRows.kt`, `HomeScreen.kt`, `FilesScreen.kt`, `DownloadsScreen.kt`: Display Before → After file size and percentage saved (`%1$s ← %2$s (-%3$d%%)`).
   - `DriveLink.kt`, `DownloadLinkPolicy.kt`, `StreamTransfer.kt`, `DownloadOperationHandler.kt`: Added automatic conversion of shared Google Drive / Docs / Sheets / Slides links into direct download endpoints, raised HTTP redirect limit from 5 to 10, added transparent GZIP stream decompression, and enforced Google Play / App Store policy blocking YouTube, TikTok, Instagram, Facebook, and LinkedIn with a clear 1-sentence localized explanation (`UNSUPPORTED_SOCIAL_PLATFORM`).
2. **PDF to Images (`pdf_07`) & Custom `outputName`**:
   - `AutoOutputName.kt`, `StoragePaths.kt`, `PdfOperationHandler.kt`, `PublicOutputStore.kt`: Named each exported page `<اسم الملف> - صفحة 003.png` (3-digit zero-padded), saved all pages inside a single subfolder named after the original PDF (`Download/Jumlo/Images/<اسم الملف>/`), and respected user-specified `outputName` across all tools including Split PDF (`pdf_05`).
3. **Removal of Artificial Size Limits & Technical Error Codes**:
   - `WorkBudget.kt`, `PdfBoxBackend.kt`, `PdfOperationHandler.kt`, `BitmapTransforms.kt`, `ImageOperationHandler.kt`, `OcrWebHandler.kt`, `OcrTextPolicy.kt`: Removed `BITMAP_BUDGET_EXCEEDED` and `OUTPUT_COUNT_BUDGET_EXCEEDED` throws; large images and PDFs now automatically downsample using `inSampleSize` and stream page-by-page with live progress reporting.
   - `UserMessages.kt`, `values/error_messages.xml`, `values-ar/error_messages.xml`: Replaced all technical error codes and scary size-limit warnings with clear Arabic and English user messages.
4. **UI/UX, RTL, Material 3 Dynamic Color & 2026 PDF Previewer**:
   - `JumloNavHost.kt`, `QuietNavigation.kt`, `Theme.kt`, `Type.kt`: Made `QuietFloatingDock` truly float over screen content inside the root `Box`, added Material 3 Dynamic Color (`dynamicDarkColorScheme`/`dynamicLightColorScheme`), fixed the active dock indicator to move Right-to-Left in Arabic (`LayoutDirection.Rtl`), and removed negative `letterSpacing` on Arabic typography.
   - `HomeScreen.kt`, `ToolsScreen.kt`, `FilesScreen.kt`, `SettingsScreen.kt`: Unified 2-column card grids, fixed card heights, unified typography scale, and added `listBottomBuffer` padding.
   - `WorkspaceScreen.kt`: Applied smart defaults so zero manual fields are required on the main workspace, placed all advanced controls behind a single `إعدادات متقدمة` button, localized option chips, and upgraded `InputPreview` to a 2026 `HorizontalPager` + pinch-to-zoom & double-tap zoom PDF/Image previewer with full RTL support.
5. **Dead Code Removal**:
   - Removed unused `MediaBrowser.kt`, `ToolRow` (`ProductionRows.kt`), `QuietIsolationFooter` (`QuietStates.kt`), and unused `ExecutionContext` constructor parameter from `OcrWebHandler.kt`.

## Checks Actually Executed

| Check | Actual Result | Scope / Evidence |
|---|---|---|
| `python3 scripts/test-storage-sql.py` | **PASS (10 passed)** | `verification/final/storage-sql.log` |
| `python3 scripts/test-source-static.py` | **PASS (14 passed)** | `verification/final/source-static.log` |
| `python3 scripts/test-redesign-static.py` | **PASS (10 passed)** | Redesign wiring & contract checks |
| `python3 scripts/test-jumlo-brand-motion.py` | **PASS (14 passed)** | Brand, RTL dock, motion & contrast checks |
| `python3 scripts/test-media-bootstrap.py` | **PASS (8 passed)** | Transport URL & redirect security checks |
| `python3 scripts/test-release-media-isolation.py` | **PASS (4 passed)** | Media variant configuration checks |
| `python3 scripts/test-media-ffmpeg.py` | **PASS (5 passed)** | Host FFmpeg audio/video muxing checks |
| `python3 scripts/test-jumlo-fixes-2026.py` | **PASS (6 passed)** | All 2026 Jumlo fixes across Problems 1–5 |
| `bash scripts/tests/test-verify-local.sh` | **PASS (2 passed)** | `verification/final/offline-preflight.log` |
| `bash scripts/tests/test-launcher.sh` | **PASS (2 passed)** | `verification/final/launcher-safety.log` |
| `:app:assembleDebug` | **BLOCKED (exit 2)** | `verification/final/assemble-debug.log` (no Android SDK / cached Gradle 8.14 in offline container) |
| `:app:testDebugUnitTest` | **BLOCKED (exit 2)** | `verification/final/gradle-unit-tests.log` (no cached Gradle 8.14 in offline container) |
| `:app:lintDebug` | **BLOCKED (exit 2)** | `verification/final/android-lint.log` (no Android SDK / cached Gradle 8.14 in offline container) |
| `:app:connectedDebugAndroidTest` | **BLOCKED (exit 2)** | `verification/final/android-instrumentation.log` (no connected device/emulator in container) |
