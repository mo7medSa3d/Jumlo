# Jumlo

Jumlo (`com.jumlo.app`, `minSdk 26`, `targetSdk/compileSdk 36`) is an on-device Android file utility app built with Kotlin, Jetpack Compose + Material 3 ("Aurora" Quiet Precision design system), Hilt, Room, WorkManager, and OkHttp.

## Key Capabilities

- **Core Pipeline**: Immutable source snapshots (`SourceStore`), persistent workspace drafts (`WorkspaceDraftRepository`), background WorkManager execution with database lease recovery (`ProcessingJobRunner`), cooperative cancellation, and verified output publication (`OutputStore`) with Managed Trash and Undo.
- **PDF Suite (`PdfBoxBackend` + `PdfRenderer`)**: Images→PDF, PDF→Images (PNG/JPEG), merge, split, extract, organize (reorder/rotate/delete/insert), image-stream compression without flattening text, normalized crop, text overlay & note annotations, watermark & page numbering, drawn & imported signatures, session-only AES-128 password protection & removal, metadata editing, AcroForm fill & explicit flatten, deterministic text comparison, verified content-stream redaction, and on-device ML Kit OCR (`.txt` + searchable PDF).
- **Image Suite (`BitmapTransforms` + `ImageOperationHandler`)**: Convert (`JPEG`/`PNG`/`WEBP`), resize, crop, rotate, flip, EXIF inspection with selective GPS or full metadata removal, text & logo watermark, vertical/horizontal/grid collage & screenshot stitching with overlap trimming, brightness/contrast/saturation adjustments, canvas padding, target-size compression, and pixel-diff comparison.
- **Capture**: CameraX document scanner (`ScanScreen`) with automatic luminance-gradient quad edge detection, draggable 4-corner refinement, and homography perspective correction; HTTPS Webpage-to-PDF capture via WebView print adapter.
- **Files, Archives & Google Drive**: SAF folder manager with provider-aware copy/move and collision policies (`Keep both`, `Skip`, `Replace`), standard & AES-256 encrypted ZIP create/extract with pre-extraction and streaming ZIP-slip/symlink/ratio/size guards (`EncryptedZip`), batch rename (preview + copies only), SHA-256 duplicate finder, SHA-256 checksum verifier, official Google Drive v3 API import/export (`GoogleDriveClient` with session-only OAuth2 token), Managed Trash, and 7-day temp retention cleanup.
- **Direct & Media Downloads**: Direct HTTPS downloader with ETag/`Last-Modified` range-safe pause/resume and exponential-backoff retry, plus an optional debug-gated media extractor (`-PextendedMedia=true`) with persisted first-use rights confirmation, DRM rejection, isolated WebView sign-in (zero cookie export), format/quality picker, audio-only extraction (`MP3`/`M4A`/`Opus`/source codec), per-platform capability badges, and in-app extractor updates.

## Verification Commands

### Host Verification Suite (Runs Without Android SDK or Internet)

```bash
bash scripts/restore-wrapper.sh --check
python3 scripts/test-source-static.py
python3 scripts/test-storage-sql.py
python3 scripts/test-media-bootstrap.py
python3 scripts/test-media-ffmpeg.py
bash scripts/tests/test-verify-local.sh
bash scripts/tests/test-launcher.sh
```

### Full Android Gradle & Emulator Verification (Requires JDK 17 + Android SDK + Emulator; Configured in `.github/workflows/ci.yml`)

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:connectedDebugAndroidTest
```

See `CHANGELOG.md`, `AUDIT.md`, `docs/FEATURE_STATUS.md`, `docs/PDF_SUPPORT_MATRIX.md`, `docs/MEDIA_CAPABILITY_MATRIX.md`, `docs/PRIVACY.md`, `docs/RELEASE.md`, and `docs/DEPENDENCY_NOTICES.md` for complete architectural and verification details.
