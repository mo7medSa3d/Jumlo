# Changelog

All changes made in the working copy during this engineering session, grouped by step and file path.

## Step 1 — Build, Compile Fixes, Encrypted ZIP Hardening & Static Checks

- `app/schemas/com.jumlo.app.data.local.AppDatabase/1.json` (**Created**): Restored missing Room database schema version 1 (`source_snapshots`, `workspace_drafts`, `processing_jobs`, `published_outputs`) so Room `MigrationTestHelper` and `scripts/test-storage-sql.py` can verify the v1 → v2 migration.
- `scripts/test-media-ffmpeg.py` (**Created**): Added host-executable FFmpeg + ffprobe verification suite testing video+audio MP4 remuxing, MP3 extraction, M4A source-codec stream copy, Opus encoding, and malformed input rejection.
- `app/src/main/java/com/jumlo/app/JumloApplication.kt` (**Modified**): Added `PDFBoxResourceLoader.init(applicationContext)` in `onCreate()` so `com.tom_roush.pdfbox` loads bundled font metrics and resources on Android.
- `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt` (**Modified**): Added `extract`, `compressImages`, `overlayText`, `stampWatermarkAndNumbers`, and `compareText` methods so all PDF handler calls and test references resolve cleanly.
- `app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt` (**Modified**): Enforced `PDF_ENCRYPTED` rejection on non-`pdf_protect` operations and added dual format parsing (`key=value` lines and JSON map) for `pdf_forms` AcroForm filling.
- `app/src/main/java/com/jumlo/app/data/files/EncryptedZip.kt` (**Created**): Extracted and hardened AES-256 zip4j `EncryptedZip` helper to prevent ZIP-slip and resource exhaustion. Pre-validates every header via `ZipSafetyValidator.validateEntryPath`, rejects symlinks (`header.isSymbolicLink`), enforces `maxEntries` (`ARCHIVE_ENTRY_LIMIT`), and streams entry payloads via `zip.getInputStream(header)` while enforcing `maxBytes` (`ARCHIVE_SIZE_LIMIT`) and canonical target boundary checks.
- `app/src/main/java/com/jumlo/app/data/processing/FileExtrasHandler.kt` (**Modified**): Updated `encryptedZip` to call `EncryptedZip.create` / `EncryptedZip.extract` with `ZipSafetyValidator` limits and wired `driveTransfer` (`file_drive`).
- `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt` (**Modified**): Fixed CameraX lifecycle binding to use `LocalLifecycleOwner.current` (instead of `ctx as? LifecycleOwner` which fails under Hilt `ContextWrapper`), switched `takePicture` executor to `ContextCompat.getMainExecutor(context)`, replaced `Card` with `QuietCard`, and localized UI strings.
- `app/src/androidTest/java/com/jumlo/app/ui/ScreenConfigSemanticsTest.kt` (**Modified**): Fixed broken imports and non-existent symbol references (`JumloDestination`, `QuietHeader`, `QuietPrimaryActionButton`, `Icons.Outlined.Folder`, `actionText`) to match the actual `DockTab` and `Quiet*` component signatures, and added a 200% font scale (`fontScale = 2.0f`) semantics + screenshot test.
- `app/proguard-rules.pro` (**Modified**): Hardened R8 keep rules for `@kotlinx.serialization.Serializable` navigation routes, `com.jumlo.app.data.processing.**`, `com.tom_roush.**`, `net.lingala.zip4j.**`, and `com.google.mlkit.**`.
- `app/src/main/baseline-prof.txt` (**Modified**): Added startup baseline profile rules for `MainActivity`, `JumloApplication`, `AppViewModel`, `JumloNavHost`, and `ui/components/**`.
- `scripts/test-source-static.py` (**Modified**): Added static verification for `EncryptedZip.kt` ZIP-slip protection and English/Arabic `String.format` specifier parity (`test_string_format_placeholders_match`).

## Step 2 & Step 3 — Feature Completeness, Official Google Drive API & Media Downloader

- `app/src/main/java/com/jumlo/app/data/files/GoogleDriveClient.kt` (**Created**): Implemented official Google Drive v3 REST client (`fetchMetadata`, `downloadFile`, `uploadFile`, `extractFileId`, `classifyHttpError`) restricted to files accessible by the signed-in user's session-only OAuth2 token.
- `app/src/main/java/com/jumlo/app/domain/model/Capabilities.kt` (**Modified**): Added `file_drive` (`Google Drive Import & Export`) to `CapabilityRegistry`.
- `app/src/main/java/com/jumlo/app/domain/model/Settings.kt` (**Modified**): Added `driveMode` and `driveFileIdOrUrl` to `ProcessingOptions`, plus `mediaTermsAccepted` to `UserSettings`.
- `app/src/main/java/com/jumlo/app/domain/model/Models.kt` (**Modified**): Added `MEDIA_NETWORK_ERROR` to `FailureCode`.
- `app/src/main/java/com/jumlo/app/domain/model/Validation.kt` (**Modified**): Added `file_drive` preflight validation (`DRIVE_TOKEN_REQUIRED`, `DRIVE_FILE_ID_REQUIRED`, and local source requirement for export).
- `app/src/main/java/com/jumlo/app/data/local/WorkspaceDraft.kt` (**Modified**): Added serialization and restoration of `driveMode` and `driveFileIdOrUrl` in `DraftOptionsJson`.
- `app/src/main/java/com/jumlo/app/data/processing/OperationDispatcher.kt` (**Modified**): Routed `file_drive` to `FileExtrasHandler.driveTransfer`.
- `app/src/main/java/com/jumlo/app/core/StreamTransfer.kt` (**Modified**): Added bounded retry with exponential backoff (`maxRetries = 2`, `retryBackoffMs = 150L`) on transient `IOException` and HTTP `429`/`502`/`503`/`504`, plus `RetryableHttpException`.
- `app/src/main/java/com/jumlo/app/data/jobs/JobStore.kt` (**Modified**): Added `pause(jobId)` to transition active downloads into `INTERRUPTED` state while keeping `.part` and `.properties` ETag/`Last-Modified` validators intact for range-safe resume.
- `app/src/main/java/com/jumlo/app/core/media/MediaModels.kt` (**Modified**): Added `MediaPlatformStatus`, `MediaPlatformCapability`, and `MediaPlatformMatrix` covering YouTube, TikTok, Instagram, Facebook, LinkedIn, X, Reddit, Vimeo, Dailymotion, SoundCloud, Google Drive (`UNVERIFIED`), and Direct File HTTPS (`HOST_VERIFIED`).
- `app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt` (**Modified**): Persisted `mediaTermsAccepted` in DataStore preferences and added `setMediaTermsAccepted(Boolean)`.
- `app/src/main/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt` (**Modified**): Mapped network/timeout/DNS failures to `FailureCode.MEDIA_NETWORK_ERROR`.
- `app/src/main/java/com/jumlo/app/ui/media/MediaViewModel.kt` (**Modified**): Connected `termsAccepted` to persisted `UserPreferencesRepository.settingsFlow` (`mediaTermsAccepted`), exposed `platformMatrix`, and mapped `MEDIA_NETWORK_ERROR` to localized `R.string.media_error_network`.
- `app/src/main/java/com/jumlo/app/ui/media/MediaWorkspace.kt` (**Modified**): Unified all UI controls on `Quiet*` design system components (`QuietHero`, `QuietCard`, `QuietSectionTitle`, `QuietPrimaryButton`, `QuietTonalButton`, `QuietTonalIconButton`) and added the Platform Capability Matrix card displaying per-platform `UNVERIFIED` / `HOST_VERIFIED` badges.

## Step 4, Step 5 & Step 6 — Backend Hardening, Navigation, Localization & UI Design System

- `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt` (**Modified**): Added `driveSessionAccount` state, `setDriveAccessToken(accountName, accessToken)` storing the OAuth2 token in `EphemeralSecretStore`, `clearDriveSession()`, `updateDriveOptions(...)`, and `pauseDownload(jobId)`.
- `app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt` (**Modified**):
  - Added Google Drive workspace UI (`file_drive`) with Android `AccountManager.newChooseAccountIntent` + `getAuthToken("oauth2:https://www.googleapis.com/auth/drive.file")` sign-in, session clear, Import/Export mode toggle, and file ID/URL input.
  - Added Pause action button on active downloads alongside Resume, Retry, and Cancel.
  - Replaced raw `Card`/`Button`/`OutlinedButton` across Home, Tools, Downloads, Files, Workspace, and Settings with `QuietCard`, `QuietPrimaryButton`, `QuietTonalButton`, `QuietFileRow`, `QuietBeforeAfterPreview`, and `QuietStateCard`, and localized all remaining inline strings.
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-ar/strings.xml` (**Modified**): Fixed format placeholders (`home_resume_workspace`, `settings_storage_summary`, `workspace_queue_summary`) and added 20 new English and Arabic strings for Google Drive, scan status, and download pause/resume controls (317 total keys in each locale).
- `app/src/main/res/values/media_strings.xml` & `app/src/main/res/values-ar/media_strings.xml` (**Modified**): Added `media_error_network` in English and Arabic.
- `app/src/test/java/com/jumlo/app/domain/PureDomainAlgorithmsTest.kt` (**Modified**): Added `EncryptedZipTest` (round-trip AES-256 ZIP, wrong password rejection, malicious ZIP-slip entry rejection, entry-count limit, total-byte limit) and `GoogleDriveClientTest` (URL/ID parsing, HTTP status classification, metadata & download with mock OkHttp interceptor, oversized file rejection).
- `app/src/test/java/com/jumlo/app/core/PublicationAndErrorLogTest.kt` (**Modified**): Added process-death recovery tests for PDF merge (`recoverInterruptedJobs_marks_running_pdf_merge_interrupted_and_cleans_staging`) and document scan (`recoverInterruptedJobs_marks_running_scan_interrupted_and_cleans_staging`), plus `downloadToFile_retries_transient_503_with_backoff_and_succeeds`.
- `app/src/hostTest/kotlin/TransferTests.kt` (**Modified**): Added `downloadToFile_retries_transient_503_with_backoff_and_succeeds` to the standalone host test suite.

## Step 7 — Release Readiness & Documentation

- `CHANGELOG.md` (**Created**): Recorded every file created or modified in this session.
- `AUDIT.md`, `README.md`, `verification/TASK_STATUS.md`, `docs/FEATURE_STATUS.md`, `docs/MEDIA_CAPABILITY_MATRIX.md`, `docs/PRIVACY.md`, `docs/RELEASE.md` (**Modified**): Updated with honest session verification results, Google Drive v3 API documentation, ZIP-slip test evidence, and owner sign-off items.
