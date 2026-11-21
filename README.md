# Jumlo Android

Bilingual (EN/AR — Jumlo / جملو) Kotlin Android utilities application for PDF tools, document conversion, file management, printing, and on-device media/internet downloads. Built with Jetpack Compose, Material 3, Hilt, Room, DataStore, and WorkManager, backed by four JVM core modules (`core-common`, `downloader-core`, `document-core`, `storage-core`) and an optional Python media extraction service (`tools/media-service`).

## Requirements

- **JDK 17** (or JDK 21; Gradle 8.14 rejects JDK 22+)
- **Android SDK** Platform 36 and Build-Tools `36.0.0` (with accepted licenses)
- **Python 3.11+** (for contract verification scripts and optional `tools/media-service`)
- **Docker** (optional, for containerized `tools/media-service` deployment)
- **Android emulator or authorized ARM64/x86_64 device** (for instrumentation tests)

## Setup

1. Configure the Android SDK via `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=/path/to/android-sdk`.
2. Copy `.env.example` to `.env` (gitignored) and replace placeholder values for local development or `tools/media-service`.
3. For signed release builds, copy `keystore.properties.example` to `keystore.properties` (gitignored) or export the signing variables below.

## Configuration

| Variable / Property | Default | Purpose |
|---|---|---|
| `ANDROID_HOME` (`sdk.dir`) | `/path/to/android-sdk` | Path to local Android SDK |
| `JUMLO_KEYSTORE_PATH` (`storeFile`) | `release-keystore.jks` | Path to release signing keystore (`.jks`) |
| `JUMLO_STORE_PASSWORD` (`storePassword`) | *(required for release)* | Release keystore password |
| `JUMLO_KEY_ALIAS` (`keyAlias`) | `universal_utilities_release` | Release signing key alias |
| `JUMLO_KEY_PASSWORD` (`keyPassword`) | *(required for release)* | Release signing key password |
| `JUMLO_KEYSTORE_BASE64` | *(CI secret)* | Base64-encoded release keystore for GitHub Actions owner-signed builds |
| `JUMLO_MEDIA_HOST` | `127.0.0.1` | Bind address for `tools/media-service` (container default `0.0.0.0`) |
| `JUMLO_MEDIA_PORT` | `8787` | Listen port for `tools/media-service` (`1`–`65535`) |
| `JUMLO_MEDIA_TOKEN` | *(required off-loopback)* | Bearer secret (`Authorization: Bearer <token>`) for `tools/media-service` (legacy alias: `JUMLO_SERVICE_TOKEN`) |
| `JUMLO_MEDIA_RATE_LIMIT` | `60` | Per-IP requests per minute for `tools/media-service` |
| `JUMLO_MEDIA_MAX_CONCURRENT` | `8` | Max concurrent extractions/streams in `tools/media-service` |
| `JUMLO_MEDIA_LOG_LEVEL` | `INFO` | Log level for `tools/media-service` (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `JUMLO_MEDIA_CORS_ORIGIN` | *(empty — disabled)* | Optional allowed CORS origin for `tools/media-service`; empty disables CORS headers |
| `-PversionCode` / `-PversionName` | `gradle.properties` | Override release version values at build time |
| `-PextendedMedia=false` | `true` | Dev-only direct-file build without bundled social-media extractor (rejected by release size gates) |
| `-PcrashReportingEnabled=true` | `false` | Optional build flag toggling `BuildConfig.CRASH_REPORTING_ENABLED` |
| `mediaProcessTimeoutMillis` | *(default)* | Instrumentation arg overriding native media timeout on slow emulators |

Environment variables take precedence over `keystore.properties`. See `.env.example` and `keystore.properties.example` for templates; never commit real secrets or keystores.

## Run

```sh
# Verify JDK 17/21 build environment before invoking Gradle
bash scripts/check-build-env.sh

# Build and install debug APK
./gradlew :app:assembleDebug

# Run optional companion media service locally (loopback only)
JUMLO_MEDIA_TOKEN="replace-with-strong-random-token" \
  python3 tools/media-service/service.py --host 127.0.0.1 --port 8787
```

### Build Variants

| Variant | Purpose | ABIs | Signing |
|---|---|---|---|
| `debug` | Development & emulator tests (`com.jumlo.app.debug`) | `arm64-v8a`, `x86_64` | Android debug key |
| `compact` | R8-minified, resource-shrunk ARM64 test installer (<= 60 MB) | `arm64-v8a` | Android debug key (not production) |
| `release` | Production APK / Play Store AAB | `arm64-v8a` (bundle splits enabled) | Owner release keystore |
| `benchmark` | Profileable release-equivalent target | `arm64-v8a` | Android debug key |

## Test

```sh
# JVM unit tests and instrumentation test APK
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest

# Size gates (60,000,000-byte ARM64 cap), Play bundle, and release lint
./gradlew :app:verifyCompactApkSize :app:verifyReleaseApkSize :app:bundleRelease :app:lintRelease

# Full signed release verification (requires release signing credentials)
./gradlew :app:verifyPublishableRelease

# Connected device / emulator tests (see verification/current/device/preflight-source/README.md first)
./gradlew :app:connectedDebugAndroidTest

# Standalone Python contract suite (56 scripts, no bytecode writes)
PYTHONDONTWRITEBYTECODE=1 bash -c 'for t in scripts/test-*.py scripts/tests/test*.py; do python3 "$t"; done'

# Companion media service unit tests
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/media-service -p "test_*.py"
```

## Deploy

### Android Play Store & ARM64 APK

1. Confirm all gates in `RELEASE_CHECKLIST.md`, `PRIVACY_POLICY.md`, `PRIVACY_AND_PERMISSIONS.md`, and `PLAY_STORE_LISTING.md`.
2. Build and verify the signed Play Store bundle locally:
   ```sh
   ./gradlew -PversionCode=10844 -PversionName="1.6-tools" :app:verifyPublishableRelease :app:bundleRelease
   ```
3. **GitHub Actions CI (`Android CI & APK`)**:
   - **Optimized test installer**: Every successful `main` run publishes `Jumlo-Optimized-Installable-ARM64-*` (`Jumlo-Optimized-ARM64.apk`, SHA-256 checksum, and `INSTALLATION.txt`) — R8-minified with the full local media engine (60,000,000-byte gate), signed with the Android testing key.
   - **Owner-signed production APK**: Configure all four GitHub Actions secrets (`JUMLO_KEYSTORE_BASE64`, `JUMLO_STORE_PASSWORD`, `JUMLO_KEY_ALIAS`, `JUMLO_KEY_PASSWORD`). On `main` pushes, version tags, or manual workflow dispatch, CI runs `:app:verifyPublishableRelease`, verifies the signature with `apksigner`, and uploads `Jumlo-Owner-Signed-Release-ARM64-*`.

### Docker Media Service (`tools/media-service`)

The default Android app runs its on-device extractor behind `MediaRuntime` (`docs/media/UNIVERSAL_ENGINE.md`). To deploy the optional companion extraction adapter in Docker:

```sh
# Build and run from repo root using .env copied from .env.example
docker build -f tools/media-service/Dockerfile -t jumlo-media-service .
docker run --rm --env-file .env -e JUMLO_MEDIA_HOST=0.0.0.0 -p 8787:8787 jumlo-media-service
```

Health and readiness probes (require `Authorization: Bearer <JUMLO_MEDIA_TOKEN>` when a token is configured):

- `GET` / `HEAD /health` — liveness probe returning `200` with `yt-dlp` version and `ffmpeg` availability.
- `GET` / `HEAD /ready` — readiness probe returning `200` when `yt-dlp` is available and the server is not shutting down, or `503` otherwise.

## Operational Docs & Known Limits

- **Release & Policy**: `CHANGELOG.md`, `RELEASE_CHECKLIST.md`, `PRIVACY_POLICY.md`, `PRIVACY_AND_PERMISSIONS.md`, `PLAY_STORE_LISTING.md`
- **Media & Testing Specs**: `docs/media/UNIVERSAL_ENGINE.md`, `docs/media/NATIVE_16KB_REPAIR.md`, `docs/testing/TOOL_QUALIFICATION.md`, `docs/testing/LIVE_MEDIA_QUALIFICATION.md`
- **Document & Storage Notes**: PDF-to-Word emits paragraphs, headings, and page breaks (not complex table/layout reconstruction); OCR uses unbundled Latin ML Kit models (embedded Arabic Unicode text is preserved, scanned Arabic OCR is unsupported); PDF-to-PowerPoint pairs page images with an editable text layer. Public outputs use MediaStore on Android 10+ (or persisted SAF grants) and app-specific external Downloads on Android 8–9. Bundled Jumlo Sans fonts are OFL-licensed IBM Plex subsets (`app/src/main/assets/licenses/IBM-Plex-OFL.txt`).
