# Jumlo Android

Bilingual (EN/AR — Jumlo / جملو) Kotlin Android utilities app with **22 primary tools** (plus 5 PDF Studio actions and 7 legacy aliases; 34 registered tool IDs total) for PDF editing and in-place markup (Select, Pen, Highlighter, Eraser, Text, Date, Check, Signature), Office/text viewing (`.docx`, `.pptx`, `.xlsx`, `.txt`, `.md`, `.csv`, `.json`), scanning/OCR, format conversion, image editing, archiving, recoverable Trash, printing, and on-device media downloads.

Built with Jetpack Compose, Material 3, Hilt, Room, DataStore, and WorkManager across four JVM core modules (`core-common`, `downloader-core`, `document-core`, `storage-core`) plus an optional Python companion media service (`tools/media-service`).

## Requirements

- **JDK 17** (or JDK 21; Gradle 8.14 rejects JDK 22+)
- **Android SDK** Platform 36 and Build-Tools `36.0.0` (licenses accepted)
- **Python 3.11+** (contract test suite and optional `tools/media-service`)
- **Docker** (optional, for containerized `tools/media-service`)
- **Android emulator or authorized ARM64/x86_64 device** (for instrumentation tests)

## Setup

1. Point Gradle at your Android SDK via `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=/path/to/android-sdk`.
2. Copy `.env.example` to `.env` (gitignored) and replace placeholder values for local SDK, release signing, or `tools/media-service` (or copy `tools/media-service/.env.example` when deploying the media service standalone).
3. For signed release builds, copy `keystore.properties.example` to `keystore.properties` (gitignored) or export the `JUMLO_*` signing variables below.

## Configuration

Environment variables take precedence over `keystore.properties`. Never commit `.env`, `keystore.properties`, or `.jks` keystores.

| Variable / Property | Template / Source | Default | Purpose |
|---|---|---|---|
| `ANDROID_HOME` (`sdk.dir`) | `.env.example` / `local.properties` | `/path/to/android-sdk` | Local Android SDK root |
| `JUMLO_KEYSTORE_PATH` (`storeFile`) | `.env.example` / `keystore.properties.example` | `release-keystore.jks` | Path to release signing keystore (`.jks`) |
| `JUMLO_STORE_PASSWORD` (`storePassword`) | `.env.example` / `keystore.properties.example` | *(required for release)* | Release keystore password |
| `JUMLO_KEY_ALIAS` (`keyAlias`) | `.env.example` / `keystore.properties.example` | `universal_utilities_release` | Release signing key alias |
| `JUMLO_KEY_PASSWORD` (`keyPassword`) | `.env.example` / `keystore.properties.example` | *(required for release)* | Release signing key password |
| `JUMLO_KEYSTORE_BASE64` | GitHub Actions secret | *(CI only)* | Base64-encoded release keystore for owner-signed CI builds |
| `JUMLO_MEDIA_HOST` | `.env.example`, `tools/media-service/.env.example` | `127.0.0.1` | Bind address for `tools/media-service` (Docker: `0.0.0.0`) |
| `JUMLO_MEDIA_PORT` | `.env.example`, `tools/media-service/.env.example` | `8787` | Listen port for `tools/media-service` (`1`–`65535`) |
| `JUMLO_MEDIA_TOKEN` | `.env.example`, `tools/media-service/.env.example` | *(required off-loopback)* | Bearer secret (`Authorization: Bearer <token>`) for `tools/media-service` (legacy alias: `JUMLO_SERVICE_TOKEN`) |
| `JUMLO_MEDIA_RATE_LIMIT` | `.env.example`, `tools/media-service/.env.example` | `60` | Per-IP sliding-window limit per 60s (`/health`, `/ready`, and `/metrics` exempt) |
| `JUMLO_MEDIA_MAX_CONCURRENT` | `.env.example`, `tools/media-service/.env.example` | `8` | Max concurrent `/extract` and `/stream` operations |
| `JUMLO_MEDIA_LOG_LEVEL` | `.env.example`, `tools/media-service/.env.example` | `INFO` | Structured log verbosity (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `JUMLO_MEDIA_CORS_ORIGIN` | `.env.example`, `tools/media-service/.env.example` | *(empty — disabled)* | Optional `Access-Control-Allow-Origin` value; empty disables CORS |
| `-PversionCode` / `-PversionName` | Gradle property (`gradle.properties`) | `10844` / `1.6-tools` | Override release version at build time |
| `-PextendedMedia=false` | Gradle property | `true` | Dev-only direct-file build without bundled media extractor (rejected by release size gates) |
| `-PcrashReportingEnabled=true` | Gradle property | `false` | Toggles `BuildConfig.CRASH_REPORTING_ENABLED` |
| `mediaProcessTimeoutMillis` | Instrumentation arg | *(default)* | Overrides native media timeout on slow emulators |

## Run

```sh
# Verify JDK 17/21 build environment
bash scripts/check-build-env.sh

# Build direct-flavor debug APK
./gradlew :app:assembleDirectDebug

# Run optional companion media service locally (loopback only)
JUMLO_MEDIA_TOKEN="replace-with-strong-random-token" \
  python3 tools/media-service/service.py --host 127.0.0.1 --port 8787
```

### Build Flavors & Variants

Product flavors (`distribution` dimension):
- **`direct`**: Full feature set (`ENABLE_MEDIA_DOWNLOADER = true`) for direct ARM64 APK distribution.
- **`play`**: Play Store distribution (`ENABLE_MEDIA_DOWNLOADER = false`) for AAB uploads.

| Variant | Purpose | ABIs | Signing |
|---|---|---|---|
| `directDebug` / `playDebug` | Development & emulator tests (`com.jumlo.app.debug`) | `arm64-v8a`, `x86_64` | Android debug key |
| `directCompact` | R8-minified, resource-shrunk ARM64 test installer (`<= 70 MB`) | `arm64-v8a` | Android debug key (not production) |
| `directRelease` / `playRelease` | Production ARM64 APK / Play Store AAB | `arm64-v8a` (bundle splits enabled) | Owner release keystore |
| `directBenchmark` / `playBenchmark` | Profileable release-equivalent target | `arm64-v8a` | Android debug key |

## Test

```sh
# Standalone Python contract suites (no bytecode writes)
PYTHONDONTWRITEBYTECODE=1 bash -c 'for t in scripts/test*.py scripts/tests/test*.py; do python3 "$t"; done'

# Companion media service unit tests
PYTHONDONTWRITEBYTECODE=1 python3 tools/media-service/test_service.py

# JVM unit tests and instrumentation test APK
./gradlew :core-common:test :downloader-core:test :document-core:test :storage-core:test :app:testDirectDebugUnitTest :app:assembleDirectDebugAndroidTest

# Size gates (70,000,000-byte ARM64 cap), Play bundle, and release lint
./gradlew :app:verifyCompactApkSize :app:verifyReleaseApkSize :app:bundlePlayRelease :app:lintPlayRelease

# Full signed release verification (requires release signing credentials)
./gradlew :app:verifyPublishableRelease

# Connected device / emulator tests (complete verification/current/device/preflight-source/README.md first)
./gradlew :app:connectedDirectDebugAndroidTest
```

## Deploy

### Android Release (Play Store AAB & ARM64 APK)

1. Confirm release gates in `RELEASE_CHECKLIST.md`, `PRIVACY_POLICY.md`, `PRIVACY_AND_PERMISSIONS.md`, and `PLAY_STORE_LISTING.md`.
2. Build and verify the signed release locally:
   ```sh
   ./gradlew -PversionCode=10844 -PversionName="1.6-tools" :app:verifyPublishableRelease :app:bundlePlayRelease
   ```
3. **GitHub Actions (`Android CI & APK`)**:
   - **Optimized test installer**: Every `main` run publishes `Jumlo-Optimized-Installable-ARM64-*` (`Jumlo-Optimized-ARM64.apk`, SHA-256 checksum, `INSTALLATION.txt`) — R8-minified with the full local media engine (70,000,000-byte gate), signed with the Android testing key.
   - **Owner-signed production APK**: Configure `JUMLO_KEYSTORE_BASE64`, `JUMLO_STORE_PASSWORD`, `JUMLO_KEY_ALIAS`, and `JUMLO_KEY_PASSWORD` in GitHub Actions secrets. On `main` pushes, `v*` tags, or manual dispatch, CI runs `:app:verifyPublishableRelease`, verifies signatures with `apksigner`, and uploads `Jumlo-Owner-Signed-Release-ARM64-*`.

### Standalone Media Service (`tools/media-service`)

The Android app runs its on-device extractor behind `MediaRuntime` (`docs/media/UNIVERSAL_ENGINE.md`). Deploy the optional companion service via Docker or `systemd`:

**Docker** (non-root `jumlo` user, pinned `yt-dlp==2026.10.26`, built-in `HEALTHCHECK`):
```sh
docker build -f tools/media-service/Dockerfile -t jumlo-media-service .
docker run --rm --env-file .env -e JUMLO_MEDIA_HOST=0.0.0.0 -p 8787:8787 jumlo-media-service
```

**systemd** (install `yt-dlp` and `ffmpeg`, copy `tools/media-service/.env.example` to `/etc/jumlo/media-service.env` with mode `0600` and a strong `JUMLO_MEDIA_TOKEN`):
```ini
[Unit]
Description=Jumlo Companion Media Service
After=network-online.target

[Service]
Type=simple
User=jumlo
EnvironmentFile=/etc/jumlo/media-service.env
ExecStart=/usr/bin/python3 -B /opt/jumlo/service.py
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict

[Install]
WantedBy=multi-user.target
```

Health, readiness, and metrics endpoints (rate-limit-exempt; require `Authorization: Bearer <JUMLO_MEDIA_TOKEN>` when a token is configured):
- `GET` / `HEAD /health` — liveness probe returning `200` with `yt-dlp` version, `ffmpeg` availability, `uptime_seconds`, and embedded `metrics`.
- `GET` / `HEAD /ready` — readiness probe returning `200` when `yt-dlp` is available and not shutting down (`503` otherwise).
- `GET` / `HEAD /metrics` — JSON observability snapshot (`uptime_seconds`, `requests_total`, `extractions_total`, `errors_total`, `rate_limited_total`, `active_extractions`, `max_concurrent`, `version`).

## Operational Docs & Known Limits

- **Release & Policy**: `CHANGELOG.md`, `RELEASE_CHECKLIST.md`, `PRIVACY_POLICY.md`, `PRIVACY_AND_PERMISSIONS.md`, `PLAY_STORE_LISTING.md`
- **Media & Testing Specs**: `docs/media/UNIVERSAL_ENGINE.md`, `docs/media/NATIVE_16KB_REPAIR.md`, `docs/testing/TOOL_QUALIFICATION.md`, `docs/testing/LIVE_MEDIA_QUALIFICATION.md`
- **Document & Storage Limits**: PDF-to-Word emits paragraphs, headings, and page breaks (not complex table/layout reconstruction); OCR combines ML Kit (Latin/CJK/Devanagari) with Tesseract4Android (`ArabicTesseractOcrEngine`) for Arabic; PDF-to-PowerPoint pairs page images with an editable text layer. Public outputs use MediaStore on Android 10+ (or persisted SAF grants) and app-specific external Downloads on Android 8–9. Bundled Jumlo Sans fonts are OFL-licensed IBM Plex subsets (`app/src/main/assets/licenses/IBM-Plex-OFL.txt`).
