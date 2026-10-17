# Jumlo Technical Audit & Verification Report

## 1. Environment & Build Gate Honesty (Step 1)

- **Gradle Wrapper Integrity**: `gradle/wrapper/gradle-wrapper.jar` verified against official Gradle 8.14 SHA-256 (`7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`) via `bash scripts/restore-wrapper.sh --check` (**PASS**).
- **Codebase & API Compile-Error Audit (`61` `.kt` files, `15` `.xml` files, `2` Room schema JSON files)**:
  Verified by `python3 scripts/test-source-static.py` (**12/12 PASS**). Issues found and fixed in this session:
  1. **Missing Room v1 schema**: Created `app/schemas/com.jumlo.app.data.local.AppDatabase/1.json` matching the v1 `ProcessingDao` tables so Room migration tests and `test-storage-sql.py` validate the v1→v2 upgrade path.
  2. **Missing FFmpeg verification script**: Created `scripts/test-media-ffmpeg.py` and verified 5/5 real `ffmpeg`/`ffprobe` tests pass (MP4 H.264+AAC remuxing, MP3 extraction, M4A source-codec copy, Opus Ogg encoding, and malformed input rejection).
  3. **PDFBox Initialization & API surface**: Added `PDFBoxResourceLoader.init(applicationContext)` in `JumloApplication.onCreate()`; added `extract`, `compressImages`, `overlayText`, `stampWatermarkAndNumbers`, and `compareText` methods on `PdfBoxBackend`; enforced `PDF_ENCRYPTED` rejection on non-`pdf_protect` operations and added JSON + `key=value` AcroForm parsing in `PdfOperationHandler`.
  4. **Encrypted ZIP ZIP-Slip Hardening**: Created `app/src/main/java/com/jumlo/app/data/files/EncryptedZip.kt` with pre-extraction and streaming validation (`ZipSafetyValidator.validateEntryPath`, symlink rejection, `ARCHIVE_ENTRY_LIMIT`, and streamed `ARCHIVE_SIZE_LIMIT` enforcement) instead of unguarded `zip.extractAll()`, plus unit tests in `EncryptedZipTest` covering `../outside.txt`, `/abs/outside.txt`, `nested/../../outside.txt`, `..\\backslash.txt`, `C:drive.txt`, entry-count overflow, and byte-limit overflow.
  5. **CameraX Lifecycle Binding**: Fixed `ScanScreen.kt` to bind `ProcessCameraProvider` to `LocalLifecycleOwner.current` instead of `ctx as? LifecycleOwner` (which fails under Hilt `ContextWrapper`) and use `ContextCompat.getMainExecutor(context)`.
  6. **Instrumented Test Compile Errors**: Fixed `ScreenConfigSemanticsTest.kt` to reference the actual `DockTab`, `QuietHero`, `QuietSectionTitle`, `QuietFileRow`, `QuietBeforeAfterPreview`, `QuietStateCard(actionLabel=...)`, `QuietPrimaryButton`, `QuietFloatingDock`, and `Icons.Rounded.Bolt` APIs, and added a 200% font scale (`fontScale = 2.0f`) test.
  7. **String Format Placeholder Mismatches**: Fixed `home_resume_workspace`, `settings_storage_summary`, and `workspace_queue_summary` in both `values/strings.xml` and `values-ar/strings.xml` so format specifiers match `stringResource(...)` arguments in `JumloNavHost.kt`, and added format-specifier parity checking to `scripts/test-source-static.py`.
- **What Did and Did Not Run in This Session**:
  - **Ran and Passed in This Session**:
    - `bash scripts/restore-wrapper.sh --check` (**PASS**)
    - `python3 scripts/test-source-static.py` (**12/12 PASS**)
    - `python3 scripts/test-storage-sql.py` (**9/9 PASS**)
    - `python3 scripts/test-media-bootstrap.py` (**8/8 PASS**)
    - `python3 scripts/test-media-ffmpeg.py` (**5/5 PASS**)
    - `bash scripts/tests/test-verify-local.sh` (**PASS**)
    - `bash scripts/tests/test-launcher.sh` (**PASS**)
  - **Did NOT Run in This Session (Blocked by Host VM Environment)**:
    - `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` — Blocked because the sandbox VM only has an OpenJDK 17 JRE (`/usr/bin/java`, no `javac` or `kotlinc`), no cached Gradle 8.14 distribution under `~/.gradle`, no Android SDK (`ANDROID_HOME` unset), and no outbound internet access. Configured in `.github/workflows/ci.yml`.
    - `./gradlew :app:connectedDebugAndroidTest` on API 26 and API 36 emulators — Blocked because no Android SDK or emulator binaries exist in the sandbox VM. Configured in `.github/workflows/ci.yml`.
    - Live platform media download tests — Blocked because the sandbox VM has no outbound internet access and no Android runtime.

## 2. Feature Completeness & Google Drive Official API (Steps 2–3)

- **Google Drive Official API (`file_drive`)**:
  - Implemented `GoogleDriveClient.kt` against the official Google Drive v3 REST endpoints (`GET /drive/v3/files/{fileId}`, `GET /drive/v3/files/{fileId}?alt=media`, `/export?mimeType=application/pdf`, and `POST /upload/drive/v3/files?uploadType=multipart`).
  - Wired Android `AccountManager.newChooseAccountIntent` + `getAuthToken(..., "oauth2:https://www.googleapis.com/auth/drive.file", ...)` in `JumloNavHost.kt` and stored the OAuth2 access token exclusively in `EphemeralSecretStore` (session-only memory handle, never persisted or logged).
  - Downloads only files the signed-in user has permission to access and maps HTTP error responses to `LOGIN_REQUIRED`, `PRIVATE_CONTENT`, `RATE_LIMITED`, `REMOVED`, `UNSUPPORTED`, and `NETWORK_ERROR`.
- **Media Downloader Hardening**:
  - Added exponential-backoff retry (`maxRetries = 2`, `retryBackoffMs = 150L`) in `StreamTransfer.downloadToFile` for transient network errors and HTTP `429`/`502`/`503`/`504`.
  - Added `JobStore.pause(jobId)` and `AppViewModel.pauseDownload(jobId)` so active downloads can be paused into `INTERRUPTED` state while preserving `.part` files and `.properties` ETag/`Last-Modified` validators for range-safe resume (or explicitly notifying the user when a server omits validators and requires a full restart).
  - Persisted first-use rights & terms confirmation (`mediaTermsAccepted`) in DataStore preferences.
  - Added `MediaPlatformMatrix` in `MediaModels.kt` and UI status chips in `MediaWorkspace.kt` showing `UNVERIFIED` on all 11 external platforms until live checks pass.
- **Process-Death Recovery & Fuzz Tests (Step 4)**:
  - `PublicationRecoveryTest` in `PublicationAndErrorLogTest.kt` covers process-death recovery during publication, range-resumable download, PDF merge staging cleanup, and camera scan staging cleanup.
  - Deterministic fuzz and unit tests in `PureDomainAlgorithmsTest.kt` cover `ZipSafetyValidator`, `EncryptedZip`, `PageSelection`, `HttpRedirectPolicy`, `GoogleDriveClient`, and `ErrorLog`.
