# Verification & Task Status (`docs/approved/tasks.json`)

## Session Verification Summary

| Check / Command | Status in This Session | Details |
|---|---|---|
| `bash scripts/restore-wrapper.sh --check` | **PASS** | `gradle/wrapper/gradle-wrapper.jar` matches official Gradle 8.14 SHA-256 (`7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`). |
| `python3 scripts/test-source-static.py` | **PASS (12/12)** | Balanced delimiters & compile-hazard scan across all 61 `.kt` files, XML parse across all 15 `.xml` files, 100% English/Arabic string key & format-placeholder parity (317 keys each), every `R.string.*` code reference resolved, Room schemas v1 & v2 validated, `CancelJobReceiver` non-exported, FileProvider restricted to `processing/outputs/`, pinned IBM Plex fonts verified. |
| `python3 scripts/test-storage-sql.py` | **PASS (9/9)** | Real SQLite verification of Room schema v1→v2 migration, job lease claiming, stale-worker fencing, cancellation atomicity, immutable snapshot indexing, and output lookup/trash deletion queries. |
| `python3 scripts/test-media-bootstrap.py` | **PASS (8/8)** | URL schemes, explicit HTTP consent, HTTPS→HTTP downgrade rejection, cross-origin `Authorization` stripping, and `Cookie` header blocking. |
| `python3 scripts/test-media-ffmpeg.py` | **PASS (5/5)** | Real FFmpeg + ffprobe verification of video+audio MP4 remuxing, MP3/M4A/Opus audio-only extraction, and malformed input rejection. |
| `bash scripts/tests/test-verify-local.sh` & `test-launcher.sh` | **PASS** | Fail-closed offline wrapper and local verification gate behavior verified. |
| `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` | **NOT RUN (Blocked by VM)** | Blocked in this sandbox VM because only JRE 17 (`java`) is installed (no `javac`, no `kotlinc`, no cached Gradle distribution, no `ANDROID_HOME`, no internet). Configured in `.github/workflows/ci.yml`. |
| `./gradlew :app:connectedDebugAndroidTest` (API 26 & API 36) | **NOT RUN (Blocked by VM)** | Blocked in this sandbox VM because no Android SDK or emulator is installed. Configured in `.github/workflows/ci.yml`. |

## All Approved Tasks (`docs/approved/tasks.json`) + Google Drive (`file_drive`)

| ID | Title | Status | Implementation & Test Evidence |
|---|---|---|---|
| F01 | Real File Intake, URI Permissions, and Source Snapshot Layer | IMPLEMENTED_UNTESTED | `SourceStore`, `FileIntakeGuard`, `PureDomainAlgorithmsTest` |
| F02 | Persistent Workspace Drafts and Selection Recovery | VERIFIED (`test-storage-sql.py`) + IMPLEMENTED_UNTESTED | `WorkspaceDraftRepository`, `ProcessingDao`, `test-storage-sql.py` |
| F03 | Real Job Execution, Progress, Cancellation, and Verified Output Publication | VERIFIED (`test-storage-sql.py`) + IMPLEMENTED_UNTESTED | `ProcessingJobRunner`, `OutputStore`, `CancelJobReceiver`, `test-storage-sql.py`, `PublicationAndErrorLogTest` |
| F04 | Real Images-to-PDF Engine | IMPLEMENTED_UNTESTED | `RealFileProcessingEngine.imagesToPdf`, `ImagesToPdfLayoutTest` |
| P01 | Real PDF Viewer and PDF-to-Images Export | IMPLEMENTED_UNTESTED | `RealFileProcessingEngine.pdfToImages`, `PageSelection`, `PureDomainAlgorithmsTest` |
| P02 | Real PDF Merge and Split/Extract Engine | IMPLEMENTED_UNTESTED | `PdfBoxBackend.merge` / `split` / `extract`, `PdfStructureCoreTest` |
| P03 | Real PDF Page Organizer (Reorder, Rotate, Delete, Insert) | IMPLEMENTED_UNTESTED | `PdfBoxBackend.organize` / `rotate` / `insert`, `PdfStructureCoreTest` |
| I01 | Real Image Convert and Resize Engine | IMPLEMENTED_UNTESTED | `RealFileProcessingEngine.convertAndResizeImages`, `BitmapExtras.codecCapabilities` |
| I02 | Real Image Crop, Rotate, Flip, and Quality/Size Compression | IMPLEMENTED_UNTESTED | `ImageTransformMath`, `BitmapTransforms`, `ImageGeometryTest` |
| A01 | Real Storage Browser and File Operations | IMPLEMENTED_UNTESTED | `DocumentTreeStore` (browse, create folder, rename, delete, `copyOrMove` with collision policy & SHA-256 verify) |
| A02 | Real ZIP Create, ZIP Extract, and Checksum Verification | IMPLEMENTED_UNTESTED | `ZipArchiveEngine`, `ZipSafetyValidator`, `Sha256VerifyEngine`, `ZipSafetyAndChecksumTest` |
| D01 | Real Direct-URL Download Engine | VERIFIED (`test-media-bootstrap.py`) + IMPLEMENTED_UNTESTED | `OkHttpDownloadEngine`, `HttpRedirectPolicy`, `test-media-bootstrap.py` |
| D02 | Download Queue Controls, Pause/Resume Honesty, and Retry | IMPLEMENTED_UNTESTED | `DownloadResumePolicy`, `StreamTransfer` backoff retry, `JobStore.pause`, `EphemeralSecretStore`, `PublicationAndErrorLogTest` |
| Q01 | Truthful Home, Search, Activity, and Settings Wiring | IMPLEMENTED_UNTESTED | `JumloNavHost`, `AppViewModel`, `ErrorLog` |
| Q02 | Honest Capability Gate for Deferred / Unqualified Surfaces | VERIFIED (`test-source-static.py`) | `Capabilities.kt`, `xf_05` removed from UI/navigation, `test-source-static.py` |
| Q03 | End-to-End Verification Matrix and Zero-Fake Regression Gate | VERIFIED (Host Suite) + CI Configured | `.github/workflows/ci.yml`, `test-source-static.py`, `DatabaseMigrationAndDaoTest`, `ScreenConfigSemanticsTest` |
| E01 | PDF Size Compression Without Flattening Text | IMPLEMENTED_UNTESTED | `PdfBoxBackend.compressImages`, `PdfStructureCoreTest` |
| E02 | PDF Crop and Page Geometry | IMPLEMENTED_UNTESTED | `PdfBoxBackend.setCrop` (normalized `PDRectangle` width/height), `PdfStructureCoreTest` |
| E03 | PDF Annotations and Overlay Text | IMPLEMENTED_UNTESTED | `PdfBoxBackend.overlayText` & `addTextAnnotation`, `PdfStructureCoreTest` |
| E04 | PDF Watermark and Page Numbers | IMPLEMENTED_UNTESTED | `PdfBoxBackend.stampWatermarkAndNumbers`, `PdfStructureCoreTest` |
| E05 | PDF Signature (Drawn and Imported) | IMPLEMENTED_UNTESTED | `SignaturePadScreen`, `PdfBoxBackend.stampImage` |
| E06 | PDF Password Protect and Remove | IMPLEMENTED_UNTESTED | `PdfBoxBackend.protect` / `removeProtection`, session-only `EphemeralSecretStore`, `PdfStructureCoreTest` |
| E07 | PDF Metadata Edit and Page Insertion | IMPLEMENTED_UNTESTED | `PdfBoxBackend.setMetadata` + 2-PDF `insert` in `PdfOperationHandler.metadata`, `PdfStructureCoreTest` |
| E08 | Scan to PDF and Webpage to PDF | IMPLEMENTED_UNTESTED | `ScanScreen` (`LocalLifecycleOwner` + `detectDocumentQuad` + `Perspective.warpQuad`), `OcrWebHandler.webpage` |
| E09 | PDF Form Fill and Explicit Flatten | IMPLEMENTED_UNTESTED | `PdfBoxBackend.fillForm`, `PdfStructureCoreTest` |
| E10 | Non-AI PDF Comparison | IMPLEMENTED_UNTESTED | `PdfBoxBackend.compareText`, `PdfStructureCoreTest` |
| E11 | True PDF Redaction | IMPLEMENTED_UNTESTED | `PdfBoxBackend.redact` (`ContentRedactor` operator & CTM handling + verification step), `PdfStructureCoreTest` |
| E12 | EXIF Viewer and Selective Metadata Removal | IMPLEMENTED_UNTESTED | `ImageOperationHandler.exif` (`removeLocation` vs `removeAll` selective preservation) |
| E13 | Image Text and Logo Watermark | IMPLEMENTED_UNTESTED | `BitmapExtras.watermarkText` & `watermarkLogo` |
| E14 | Image Join, Collage, and Screenshot Stitch | IMPLEMENTED_UNTESTED | `BitmapExtras.join` (`VERTICAL`, `HORIZONTAL`, `GRID`, `overlapPx` trim) |
| E15 | Image Adjustments and Padding/Canvas | IMPLEMENTED_UNTESTED | `BitmapExtras.adjust` (`ColorMatrix` + canvas padding) |
| E16 | Target-Size Image Compression | IMPLEMENTED_UNTESTED | `BitmapExtras.compressToTarget` |
| E17 | Image Comparison and Optional Format Encoders | IMPLEMENTED_UNTESTED | `BitmapExtras.compare` + `BitmapExtras.codecCapabilities` |
| E18 | Provider-Aware File Copy/Move and Conflict Handling | IMPLEMENTED_UNTESTED | `DocumentTreeStore.copyOrMove` (`KEEP_BOTH`, `SKIP`, `REPLACE` + SHA-256 check) |
| E19 | Authorized-Source Media and Site Extraction | VERIFIED (`test-media-ffmpeg.py` / `test-media-bootstrap.py`) / UNVERIFIED (Live Sites) | `MediaRuntime`, `MediaFfmpegTools`, `MediaWorkspace`, `test-media-ffmpeg.py` |
| E20 | Camera Document Scan Pipeline | IMPLEMENTED_UNTESTED | `ScanScreen.kt`, `Perspective.kt`, `PerspectiveTest` |
| E21 | Encrypted ZIP Support | IMPLEMENTED_UNTESTED | `EncryptedZip.kt` (zip4j AES-256 + pre-extraction & streaming `ZipSafetyValidator`), `EncryptedZipTest` |
| E22 | Batch Rename Engine | IMPLEMENTED_UNTESTED | `FileExtras.batchRename`, `FileExtrasTest` |
| E23 | Duplicate Detection by Content Hash | IMPLEMENTED_UNTESTED | `FileExtras.duplicateGroups`, `FileExtrasTest` |
| E24 | App-Managed Trash and Retention Policy | VERIFIED (`test-storage-sql.py`) + IMPLEMENTED_UNTESTED | `OutputStore.moveToTrash` / `restoreFromTrash` / `purgeTrash` / `cleanExpiredTempFiles`, `test-storage-sql.py` |
| E25 | Real Encrypted Vault (`xf_05`) | REMOVED | Removed from `JumloDestination` and Tools catalog (`Q02` rule: never ship a control without a qualified crypto backend) |
| G01 | Official Google Drive v3 API Import/Export (`file_drive`) | IMPLEMENTED_UNTESTED | `GoogleDriveClient`, `FileExtrasHandler.driveTransfer`, `GoogleDriveClientTest` |
