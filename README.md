# Jumlo Android

Kotlin Android utilities application for PDF tools, document conversion, file management, printing, and on-device media/internet downloads. Built with Jetpack Compose, Material 3, Hilt, Room, DataStore, and WorkManager, backed by four JVM core modules (`core-common`, `downloader-core`, `document-core`, `storage-core`) and an optional Python media extraction service (`tools/media-service`).

## Requirements

- **JDK 17** (or JDK 21; Gradle 8.14 rejects JDK 22+)
- **Android SDK** Platform 36 and Build-Tools `36.0.0` (with accepted licenses)
- **Python 3.11+** (for verification scripts and optional `tools/media-service`)
- **Docker** (optional, for containerized `tools/media-service` deployment)
- **Android emulator or authorized ARM64/x86_64 device** (for instrumentation tests)

## Setup

1. Configure the Android SDK via `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=/path/to/android-sdk`.
2. Copy `.env.example` to `.env` for local environment and media-service variables.
3. For signed release builds, copy `keystore.properties.example` to `keystore.properties` (gitignored) or export the signing environment variables below.

## Configuration

| Variable / Property | Source | Purpose |
|---|---|---|
| `ANDROID_HOME` / `sdk.dir` | Env / `local.properties` | Path to local Android SDK |
| `JUMLO_KEYSTORE_PATH` / `storeFile` | Env / `keystore.properties` | Path to release signing keystore (`.jks`) |
| `JUMLO_STORE_PASSWORD` / `storePassword` | Env / `keystore.properties` | Release keystore password |
| `JUMLO_KEY_ALIAS` / `keyAlias` | Env / `keystore.properties` | Release signing key alias |
| `JUMLO_KEY_PASSWORD` / `keyPassword` | Env / `keystore.properties` | Release signing key password |
| `JUMLO_KEYSTORE_BASE64` | GitHub Actions secret | Base64-encoded release keystore for CI owner-signed builds |
| `JUMLO_SERVICE_TOKEN` | `.env` / Env | Shared bearer secret for `tools/media-service` (`Authorization` header) |
| `-PversionCode` / `-PversionName` | Gradle property | Override release version values from `gradle.properties` |
| `-PextendedMedia=false` | Gradle property | Dev-only direct-file build without bundled social-media extractor (rejected by release size gates) |
| `mediaProcessTimeoutMillis` | Instrumentation arg | Override native media timeout during slow emulator runs |

Environment variables take precedence over `keystore.properties`. See `.env.example` and `keystore.properties.example` for templates; never commit real secrets or keystores.

## Build, Run & Test

```sh
# Debug APK, JVM unit tests, and instrumentation test APK
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest

# Size gates (60,000,000-byte ARM64 cap), Play bundle, and release lint
./gradlew :app:verifyCompactApkSize :app:verifyReleaseApkSize :app:bundleRelease :app:lintRelease

# Full signed release verification (requires release signing credentials)
./gradlew :app:verifyPublishableRelease

# Connected device / emulator tests (see verification/current/device/preflight-source/README.md first)
./gradlew :app:connectedDebugAndroidTest
```

Static and contract verification scripts:

```sh
python3 -B scripts/test-tool-matrix-contract.py
python3 -B scripts/test-universal-media-architecture.py
python3 -B scripts/tests/test-release-apk-artifacts.py
python3 -B scripts/tests/test-platform-matrix-parity.py
python3 -B scripts/tests/test-live-media-qualification.py
```

### Build Variants

| Variant | Purpose | ABIs | Signing |
|---|---|---|---|
| `debug` | Development & emulator tests (`com.jumlo.app.debug`) | `arm64-v8a`, `x86_64` | Android debug key |
| `compact` | R8-minified, resource-shrunk ARM64 test installer (<= 60 MB) | `arm64-v8a` | Android debug key (not production) |
| `release` | Production APK / Play Store AAB | `arm64-v8a` (bundle splits enabled) | Owner release keystore |
| `benchmark` | Profileable release-equivalent target | `arm64-v8a` | Android debug key |

## Deployment

### Android Play Store & ARM64 APK

1. Confirm all gates in `RELEASE_CHECKLIST.md`, `PRIVACY_POLICY.md`, `PRIVACY_AND_PERMISSIONS.md`, and `PLAY_STORE_LISTING.md`.
2. Build and verify the signed Play Store bundle locally:
   ```sh
   ./gradlew -PversionCode=10844 -PversionName="1.6-tools" :app:verifyPublishableRelease :app:bundleRelease
   ```
3. **GitHub Actions CI (`Android CI & APK`)**:
   - **Optimized test installer**: Every successful `main` run publishes `Jumlo-Optimized-Installable-ARM64-*` containing `Jumlo-Optimized-ARM64.apk`, its SHA-256 checksum, and `INSTALLATION.txt`. It is R8-minified with the full local media engine (60,000,000-byte gate) and signed with the Android testing key.
   - **Owner-signed production APK**: Configure all four GitHub Actions secrets (`JUMLO_KEYSTORE_BASE64`, `JUMLO_STORE_PASSWORD`, `JUMLO_KEY_ALIAS`, `JUMLO_KEY_PASSWORD`). On `main` pushes, version tags, or manual workflow dispatch, CI runs `:app:verifyPublishableRelease`, verifies the signature with `apksigner`, and uploads `Jumlo-Owner-Signed-Release-ARM64-*`.

### Optional Media Service (`tools/media-service`)

The default Android app runs its on-device extractor behind `MediaRuntime` (`docs/media/UNIVERSAL_ENGINE.md`). To deploy the optional companion extraction adapter:

```sh
# Local Python run
JUMLO_SERVICE_TOKEN="replace-with-strong-secret" \
  python3 tools/media-service/service.py --host 127.0.0.1 --port 8731

# Docker build and run (using .env copied from .env.example)
docker build -t jumlo-media-service ./tools/media-service
docker run --rm --env-file .env -p 8731:8731 jumlo-media-service
```

## Operational Docs & Known Limits

- **Release & Policy**: `CHANGELOG.md`, `RELEASE_CHECKLIST.md`, `PRIVACY_POLICY.md`, `PRIVACY_AND_PERMISSIONS.md`, `PLAY_STORE_LISTING.md`
- **Media & Testing Specs**: `docs/media/UNIVERSAL_ENGINE.md`, `docs/media/NATIVE_16KB_REPAIR.md`, `docs/testing/TOOL_QUALIFICATION.md`, `docs/testing/LIVE_MEDIA_QUALIFICATION.md`
- **Document & Storage Notes**: PDF-to-Word emits paragraphs, headings, and page breaks (not complex table/layout reconstruction); OCR uses unbundled Latin ML Kit models (embedded Arabic Unicode text is preserved, scanned Arabic OCR is unsupported); PDF-to-PowerPoint pairs page images with an editable text layer. Public outputs use MediaStore on Android 10+ (or persisted SAF grants) and app-specific external Downloads on Android 8–9. Bundled Jumlo Sans fonts are OFL-licensed IBM Plex subsets (`app/src/main/assets/licenses/IBM-Plex-OFL.txt`).
