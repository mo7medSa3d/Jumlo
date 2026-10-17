# Feature status — honest accounting

Statuses:
- **VERIFIED (<test name>)** = verified in this session via host Python/SQLite/FFmpeg/static verification suites (`test-source-static.py`, `test-storage-sql.py`, `test-media-bootstrap.py`, `test-media-ffmpeg.py`, `restore-wrapper.sh --check`, `test-verify-local.sh`, `test-launcher.sh`); note that Android Gradle/emulator tests did not run in this session (see Environment Honesty Note below);
- **IMPLEMENTED_UNTESTED** = backend + UI + JVM/instrumented tests implemented and statically verified in source, but Gradle/JDK/Android SDK/emulator execution was blocked in this sandbox environment;
- **PARTIAL** = works for a documented subset with explicit bounds;
- **REMOVED** = UI entry removed so no non-functional control ships.

> **Important Environment Honesty Note**: The execution environment for this session has OpenJDK 17 JRE (`java` only, no `javac` or `kotlinc`), no cached Gradle 8.14 distribution, no Android SDK (`ANDROID_HOME` unset, no `emulator`/`adb`), and no outbound internet access. Therefore, Gradle tasks (`:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleRelease`, `:app:connectedDebugAndroidTest`) and live network media extractions did not run in this session. Every feature whose verification relies on running the Android/JVM JUnit or instrumented suite is marked `IMPLEMENTED_UNTESTED` (with its target test class/method recorded) until `.github/workflows/ci.yml` executes in a full Android SDK runner.

## Core Pipeline (`tasks.json` F01–F04)

| Feature | Status | Evidence / limit |
|---|---|---|
| F01 Source intake & immutable snapshots | IMPLEMENTED_UNTESTED | `SourceStore` + `FileIntakeGuard` (max 100 files, 512 MiB/file, 1.5 GiB total, 64 MiB free-space headroom); JVM tests in `PureDomainAlgorithmsTest.FileIntakeGuardTest` |
| F02 Persistent recoverable drafts | VERIFIED (`test-storage-sql.py::test_schema_v2_creates_and_roundtrips`) + IMPLEMENTED_UNTESTED (device UI) | `WorkspaceDraftRepository` + Room `workspace_drafts` table + schema v1/v2 verified in `test-storage-sql.py` (9/9 pass) |
| F03 Real job execution, progress, cancel & verified publication | VERIFIED (`test-storage-sql.py::test_lease_claim_and_stale_worker_fence`, `test_cancel_prevents_late_completion`) + IMPLEMENTED_UNTESTED (device runner) | `ProcessingJobRunner`, `OutputStore`, `ProcessingDao` lease/cancel SQL verified in `test-storage-sql.py`; `PublicationRecoveryTest` covers process death during publication, download, PDF merge, and scan |
| F04 Images → PDF | IMPLEMENTED_UNTESTED | `RealFileProcessingEngine.imagesToPdf` (sampled decode, EXIF rotation, incremental page streaming); JVM tests in `ImagesToPdfLayoutTest` |

## PDF (`tasks.json` P01–P03, E01–E11)

| Feature | Status | Evidence / limit |
|---|---|---|
| P01 Page → PNG/JPEG export & real viewer | IMPLEMENTED_UNTESTED | `PdfRenderer` handler + page range syntax (`PageSelection.parse`); JVM tests in `PureDomainAlgorithmsTest.PageSelectionTest` |
| P02 Merge & Split/Extract | IMPLEMENTED_UNTESTED | `PdfBoxBackend.merge` / `split` / `extract`; JVM tests in `PdfStructureCoreTest.merge_combines_page_counts_and_preserves_order` & `split_extracts_selected_pages_in_order` |
| P03 Organize (reorder, rotate, delete, insert) | IMPLEMENTED_UNTESTED | `PdfBoxBackend.organize` / `rotate` / `insert`; JVM tests in `PdfStructureCoreTest.organize_reorders_and_deletes_pages` & `rotate_adds_normalized_rotation_to_selected_pages` |
| E01 Compress (text preserved) | IMPLEMENTED_UNTESTED | `PdfBoxBackend.compressImages` downscales Flate 8-bit RGB/Gray embedded images above target DPI while keeping text/vector streams untouched; `PdfStructureCoreTest.compress_preserves_extractable_text` |
| E02 Crop / page geometry | IMPLEMENTED_UNTESTED | `PdfBoxBackend.setCrop` sets normalized `PDRectangle(x0, y0, width, height)` on `cropBox`; `PdfStructureCoreTest.crop_updates_page_crop_box_dimensions` |
| E03 Annotations & overlay text | IMPLEMENTED_UNTESTED | `PdfBoxBackend.overlayText` (Helvetica stream) & `addTextAnnotation` (`PDAnnotationText`); `PdfStructureCoreTest.overlay_text_and_annotation_are_written_to_page` |
| E04 Watermark & page numbers | IMPLEMENTED_UNTESTED | `PdfBoxBackend.stampWatermarkAndNumbers` with graphics-state alpha and bottom-center numbering; `PdfStructureCoreTest.watermark_and_page_numbers_preserve_document_validity` |
| E05 Signature (drawn + imported) | IMPLEMENTED_UNTESTED | `SignaturePadScreen` (Compose canvas → PNG snapshot) + `PdfBoxBackend.stampImage` |
| E06 Password protect / remove (owned files) | IMPLEMENTED_UNTESTED | `PdfBoxBackend.protect` / `removeProtection` with `EphemeralSecretStore` session-only password handles and `CharArray` zeroization in `finally`; rejects encrypted PDFs on non-`pdf_protect` tools (`PDF_ENCRYPTED`); `PdfStructureCoreTest.password_protect_and_remove_round_trip` |
| E07 Metadata edit & page insertion | IMPLEMENTED_UNTESTED | `PdfBoxBackend.setMetadata` + 2-PDF `insert` at `insertAtPage`; `PdfStructureCoreTest.metadata_and_insert_update_document_info_and_page_count` |
| E08 Camera scan + Webpage → PDF | IMPLEMENTED_UNTESTED | CameraX capture (`LocalLifecycleOwner`) + luminance-gradient auto quad detection (`detectDocumentQuad`) with draggable four-corner override + homography perspective warp (`Perspective.warpQuad`, `PerspectiveTest`); WebView print adapter with HTTPS guard |
| E09 AcroForm fill + explicit flatten | IMPLEMENTED_UNTESTED | `PdfBoxBackend.fillForm` (supports both `key=value` lines and JSON map) + widget appearance stream stamp on flatten; `PdfStructureCoreTest.acroform_fill_and_flatten_removes_editable_fields` |
| E10 Compare (non-AI) | IMPLEMENTED_UNTESTED | `PdfBoxBackend.compareText` deterministic page-by-page text diff report; `PdfStructureCoreTest.compare_produces_deterministic_page_diff` |
| E11 Redaction with verification | IMPLEMENTED_UNTESTED | `PdfBoxBackend.redact` rewrites content stream text operators (`Tj`, `TJ`, `'`, `"`), paints black rectangles, re-extracts page text to verify target removal, and fails loudly (`REDACTION_VERIFICATION_FAILED`) on unsupported nested XObjects; `PdfStructureCoreTest.redact_removes_target_text_from_stream_and_verifies` |
| OCR → searchable PDF + `.txt` | PARTIAL | ML Kit on-device text recognition exports `.txt` + stamps `RenderingMode.NEITHER` invisible text layer per page (`stampInvisibleText`); coarse line placement rather than word-box alignment |

## Images (`tasks.json` I01–I02, E12–E17)

| Feature | Status | Evidence / limit |
|---|---|---|
| I01 Convert JPEG/PNG/WebP & Resize | IMPLEMENTED_UNTESTED | `RealFileProcessingEngine.convertAndResizeImages` + `CodecCapability` matrix (`codec_capabilities.txt`) |
| I02 Crop, Rotate, Flip & Quality Compress | IMPLEMENTED_UNTESTED | `ImageTransformMath` + `BitmapTransforms` + `PureDomainAlgorithmsTest.ImageGeometryTest` |
| E12 EXIF inspection & selective removal | IMPLEMENTED_UNTESTED | Exports `exif_report.txt` and cleans images: `removeLocation = true` strips GPS tags while preserving non-location EXIF tags; `removeAll = true` strips all metadata |
| E13 Text & logo watermark | IMPLEMENTED_UNTESTED | `BitmapExtras.watermarkText` & `watermarkLogo` (uses 2nd selected image as logo) |
| E14 Join, collage & screenshot stitch | IMPLEMENTED_UNTESTED | `BitmapExtras.join` (`VERTICAL`, `HORIZONTAL`, `GRID`, plus `overlapPx` top-overlap trimming for screenshot stitching) |
| E15 Adjustments & canvas padding | IMPLEMENTED_UNTESTED | `BitmapExtras.adjust` (`ColorMatrix` brightness/contrast/saturation + canvas padding) |
| E16 Target-size compression | IMPLEMENTED_UNTESTED | `BitmapExtras.compressToTarget` binary search + iterative downscale fallback |
| E17 Image comparison | IMPLEMENTED_UNTESTED | `BitmapExtras.compare` pixel diff ratio, mean channel delta, and `image_diff.png` heatmap |

## Files (`tasks.json` A01–A02, E18, E21–E25, Google Drive)

| Feature | Status | Evidence / limit |
|---|---|---|
| A01 SAF tree browser & provider-aware file ops | IMPLEMENTED_UNTESTED | `DocumentTreeStore` browse, create folder, rename, delete, `copyOrMove` with `CollisionPolicy` (`KEEP_BOTH`, `SKIP`, `REPLACE`) and SHA-256 verification |
| A02 ZIP create/extract + SHA-256 verify | IMPLEMENTED_UNTESTED | `ZipArchiveEngine` + `ZipSafetyValidator` (slip, symlink, ratio, count, byte caps) + `Sha256VerifyEngine`; `PureDomainAlgorithmsTest.ZipSafetyAndChecksumTest` |
| E21 Encrypted ZIP create/extract | IMPLEMENTED_UNTESTED | `EncryptedZip` (zip4j AES-256) with pre-extraction and streaming `ZipSafetyValidator` checks (rejects `..`, `/`, `\`, `:`, symlinks, entry-count overflow, and streamed byte-limit overflow) + session-only password zeroization; `PureDomainAlgorithmsTest.EncryptedZipTest` |
| E22 Batch rename (preview; copies only) | IMPLEMENTED_UNTESTED | `FileExtras.batchRename` (`{name}`, `{n}` pattern + collision avoidance, copies only) + `rename_preview.txt`; `PureDomainAlgorithmsTest.FileExtrasTest` |
| E23 Duplicate detection by SHA-256 | IMPLEMENTED_UNTESTED | `FileExtras.duplicateGroups` exact SHA-256 grouping report (`duplicates_report.txt`); `PureDomainAlgorithmsTest.FileExtrasTest` |
| E24 Managed trash & retention cleanup | VERIFIED (`test-storage-sql.py::test_output_lookup_and_delete`) + IMPLEMENTED_UNTESTED (device UI) | `OutputStore.moveToTrash`, `restoreFromTrash`, `purgeTrash`, and `cleanExpiredTempFiles`; snackbar Undo on trash; SQL delete/lookup verified in `test-storage-sql.py` |
| Google Drive official API import/export (`file_drive`) | IMPLEMENTED_UNTESTED | `GoogleDriveClient` (`https://www.googleapis.com/drive/v3/files` & `/upload/drive/v3/files?uploadType=multipart`) + `AccountManager` OAuth2 (`drive.file`) session-only token in `EphemeralSecretStore` + `FileExtrasHandler.driveTransfer`; `PureDomainAlgorithmsTest.GoogleDriveClientTest` |
| E25 Encrypted vault (`xf_05`) | REMOVED | Removed from Tools catalog and navigation (`JumloDestination.entries.none { it.id == "xf_05" }`) so no unimplemented vault control is shipped |

## Downloads & Media (`tasks.json` D01–D02, E19, Phase 2)

| Feature | Status | Evidence / limit |
|---|---|---|
| D01–D02 Direct URL download + pause/resume + retry with backoff | VERIFIED (`test-media-bootstrap.py`) + IMPLEMENTED_UNTESTED (device runner) | `OkHttpDownloadEngine` + `StreamTransfer` (exponential backoff retry on transient `IOException` / HTTP 429/502/503/504, range-safe `.part` + `.properties` ETag/`Last-Modified` validators, honest restart note when validators absent) + `JobStore.pause`; URL/redirect guards verified in `test-media-bootstrap.py` (8/8 pass); unit tests in `PublicationAndErrorLogTest` & `TransferTests` |
| Phase 2 Media analysis, format picker, audio-only (MP3/M4A/Opus + source codec), thumbnail & metadata | VERIFIED (`test-media-ffmpeg.py` — 5/5 pass for FFmpeg mux/transcode) / IMPLEMENTED_UNTESTED (live platforms) | FFmpeg merge/transcode verified in `test-media-ffmpeg.py` (5/5 pass); granular error mapping (`MEDIA_PRIVATE_CONTENT`, `MEDIA_GEO_BLOCKED`, `MEDIA_REMOVED`, `MEDIA_ENCRYPTED_SOURCE`, `MEDIA_RATE_LIMITED`, `MEDIA_SESSION_REQUIRED`, `MEDIA_SITE_UNSUPPORTED`, `MEDIA_NETWORK_ERROR`); persisted first-use rights dialog (`mediaTermsAccepted`); disabled in release builds pending GPL-3.0 sign-off |
| Per-platform live capability matrix (YouTube, TikTok, Instagram, Facebook, LinkedIn, X, Reddit, Vimeo, Dailymotion, SoundCloud, Google Drive) | UNVERIFIED (live network) | Not run in this session (sandbox VM has no internet access and no Android runtime); marked `UNVERIFIED` in UI (`MediaPlatformMatrix`) and `docs/MEDIA_CAPABILITY_MATRIX.md` |

## Backend, Navigation, Animation & UI (Steps 4–6)

| Item | Status | Evidence / limit |
|---|---|---|
| Room exported schemas v1 & v2 + MigrationTestHelper | VERIFIED (`test-source-static.py`, `test-storage-sql.py`) + IMPLEMENTED_UNTESTED (`DatabaseMigrationAndDaoTest`) | `app/schemas/com.jumlo.app.data.local.AppDatabase/1.json` & `2.json` validated in `test-source-static.py` and `test-storage-sql.py` (v1→v2 migration verified on real SQLite); `MigrationTestHelper` in `DatabaseMigrationAndDaoTest` |
| Parser property & fuzz tests | IMPLEMENTED_UNTESTED | Deterministic fuzz tests for ZIP entry paths (including malicious ZIP-slip entries), PDF page ranges, and URL/redirect policies in `PureDomainAlgorithmsTest` |
| Sanitized local error log + export | IMPLEMENTED_UNTESTED | `ErrorLog` strips URLs/paths/tokens (`PureDomainAlgorithmsTest.ErrorLogSanitizationTest`) and exports from Settings |
| Type-safe Navigation Compose + deep links + Predictive Back | IMPLEMENTED_UNTESTED | `@Serializable` routes (`HomeRoute`, `ToolsRoute`, `DownloadsRoute`, `FilesRoute`, `SettingsRoute`, `ScanRoute`, `SignatureRoute`, `WorkspaceRoute`, `ActivityRoute`), `BackHandler`, `SharedTransitionLayout` |
| Baseline Profile & Macrobenchmark | IMPLEMENTED_UNTESTED | `:benchmark` module with `StartupTimingMetric`, `FrameTimingMetric`, and `BaselineProfileRule` + `app/src/main/baseline-prof.txt` |
| Full English + Arabic localization & RTL parity | VERIFIED (`test-source-static.py::test_string_parity_and_references`) | 100% key and format-placeholder parity across `values/` and `values-ar/` (317 strings each) verified by `test-source-static.py` (12/12 pass) |
| Screenshot & 200% font-scale semantics tests (light/dark, EN/AR) | IMPLEMENTED_UNTESTED | `ScreenConfigSemanticsTest` covers EN/AR RTL, light/dark themes, 200% font scale, and PNG screenshot capture across core surfaces |
