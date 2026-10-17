# Jumlo Release Guide

## Versioning

`versionCode` / `versionName` come from Gradle properties with safe defaults:

```sh
./gradlew -PversionCode=10843 -PversionName="1.5.0" :app:assembleRelease
```

Defaults live in `gradle.properties` (`10842` / `1.4-media-candidate`).
Increase `versionCode` on every Play release; `versionName` may follow the
git tag (e.g. tag `v1.5.0` → `-PversionName="1.5.0"`). The mapping is explicit
and deterministic.

## Signing

1. Copy `keystore.properties.template` to `keystore.properties` (excluded via
   `.gitignore` and never committed or archived) and fill in `storeFile`,
   `storePassword`, `keyAlias`, and `keyPassword`.
2. `./gradlew :app:assembleRelease` automatically signs the release build when
   `keystore.properties` is present; without it, the build produces an unsigned
   release artifact for local R8/shrinking inspection only.
3. Keep the keystore and `keystore.properties` out of version control and
   shared archives.

## Pre-release checklist (must all pass in one session before calling release-ready)

```sh
bash scripts/restore-wrapper.sh --check
python3 scripts/test-source-static.py
python3 scripts/test-storage-sql.py
python3 scripts/test-media-bootstrap.py
python3 scripts/test-media-ffmpeg.py
bash scripts/tests/test-verify-local.sh
bash scripts/tests/test-launcher.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:connectedDebugAndroidTest   # run on API 26 and API 36 emulators
```

Lint fails the build on errors (`abortOnError = true`, `MissingTranslation` = error).
The base (no-flag) build must never contain the GPL media payload; confirm with
the release DEX check in `AUDIT.md`. Extended-media builds
(`-PextendedMedia=true`) are debug/qualification only until the licensing
decision in `docs/DEPENDENCY_NOTICES.md` is signed off by the owner.

## Baseline Profile & Macrobenchmark

The `:benchmark` module (`benchmark/src/main/java/com/jumlo/benchmark/`)
contains:
- `StartupBenchmark`: measures cold-start latency (`StartupTimingMetric`,
  target < 1 s on mid-range profile) and navigation frame jank
  (`FrameTimingMetric`, target < 5% jank in Home → Tool → Result flow).
- `BaselineProfileGenerator`: generates startup baseline rules via
  `BaselineProfileRule`.
- `:app` ships `androidx.profileinstaller:profileinstaller` and
  `app/src/main/baseline-prof.txt` covering hot startup classes (`MainActivity`,
  `JumloApplication`, `AppViewModel`, `JumloNavHost`, and `ui/components/**`).

## Play Console + Data Safety answers

- **Category**: Tools. No ads, no in-app purchases, no Jumlo accounts.
- **Data collected**: **No data collected** by Jumlo off-device (see
  `docs/PRIVACY.md`). All PDF, image, OCR, scan, and archive processing runs
  locally on the device. Direct downloads, optional Google Drive transfers, and
  optional media extractions connect directly from the user's device to the
  endpoints the user initiates; Jumlo operates no backend servers, analytics,
  or crash telemetry.
- **Data shared with third parties**: **None**.
- **Security practices**:
  - Network traffic uses HTTPS by default (`network_security_config.xml` has
    `cleartextTrafficPermitted="false"` at base; unencrypted HTTP requires
    explicit per-job user consent and blocks credential headers and HTTPS→HTTP
    downgrades).
  - Cloud backup and device-to-device transfer of app sandbox data are disabled
    (`android:allowBackup="false"`, `data_extraction_rules.xml`,
    `backup_rules.xml`).
  - Session secrets (PDF/ZIP passwords, signed download URLs, Google Drive
    OAuth2 tokens) are held only in process memory (`EphemeralSecretStore`) and
    zeroized after use.
- **Permissions declared in `AndroidManifest.xml`**:
  - `INTERNET` & `ACCESS_NETWORK_STATE`: user-initiated direct file downloads,
    Google Drive v3 API import/export, webpage-to-PDF capture, and (in debug
    extended-media builds) media URL extraction.
  - `POST_NOTIFICATIONS`: foreground job progress notifications (Android 13+
    runtime permission with education sheet).
  - `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC`: keeping long-running
    user-initiated file processing and downloads alive when backgrounded.
  - `CAMERA` (`android.hardware.camera` optional): used exclusively by the
    on-device document scanner screen (`ScanScreen`).
  - Unused permissions removed: `VIBRATE` and `USE_BIOMETRIC` are not declared.
