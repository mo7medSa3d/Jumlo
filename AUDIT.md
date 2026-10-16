# AUDIT.md — Jumlo current build truth (this session, 2026-10-17)

Supersedes all earlier "passed" claims. Every result below was reproduced in
this session on: Temurin JDK 17.0.20.1, Gradle 8.14 (verified wrapper),
Android SDK with platforms 33–36 + build-tools 36.0.0, no emulator.

## Reproduction commands (from repo root)

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk
export ANDROID_HOME=$HOME/adb
bash scripts/restore-wrapper.sh --check
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:assembleDebug -PextendedMedia=true --console=plain
```

## Results

| Check | Result | Evidence |
|---|---|---|
| Wrapper SHA-256 (`restore-wrapper.sh --check`) | PASS | `7d3a4ac4…df296172` matches official Gradle 8.14; script needed no fix |
| `:app:assembleDebug` | PASS | `app-debug.apk` 19.3 MB |
| `:app:testDebugUnitTest` | PASS | 4 files, 19 tests, 0 failures/errors/skips (incl. new `EphemeralSecretStoreTest`, 4 tests) |
| `:app:lintDebug` | PASS | `abortOnError=true` now enforced in `app/build.gradle.kts`. One earlier combined `--rerun-tasks` invocation failed on `lintAnalyze*` tasks, then the identical command passed three times — treated as flakes, see risks |
| `:app:assembleRelease` (R8) | PASS | unsigned `app-release-unsigned.apk` 2.2 MB |
| `-PextendedMedia=true assembleDebug` | PASS | flag wiring works; GPL payload resolves but MUST NOT ship — see `docs/DEPENDENCY_NOTICES.md` |
| `:app:connectedDebugAndroidTest` | NOT RUN | no emulator / system images installed; CI now runs API 26 + 36 matrix |

## Changes made this session (branch `release-hardening`)

- Removed stale `app/schemas/com.universalutilities.app…` export (package is
  `com.jumlo.app`); created `docs/DESIGN.md` so the `DESIGN.md` references in
  `Color.kt`/`Shape.kt`/`Type.kt` resolve.
- Dropped unused `VIBRATE` / `USE_BIOMETRIC` permissions and the unreferenced
  `androidx.biometric` dependency (zero code references).
- Media downloader: in-app ToS/copyright notice + explicit session-scoped
  checkbox gates Analyze and Download (`MediaWorkspace.kt`, en+ar strings).
  Still debug + `-PextendedMedia` gated; licensing decision still open.
- Added `EphemeralSecretStoreTest` (single-use consume, input copying,
  remove/clear, handle uniqueness). Secrets stay in-memory only; signed URLs
  never reach logs/notifications/Room/backups (backup configs verified).
- `versionCode`/`versionName` from `-PversionCode`/`-PversionName` (defaults in
  `gradle.properties`); signing via `keystore.properties` (template only).
- Added `docs/PRIVACY.md`, `docs/RELEASE.md`, rewrote
  `docs/DEPENDENCY_NOTICES.md` (per-dependency licenses + GPL decision gate),
  `docs/DESIGN.md`. CI: JDK 17 + Gradle cache + debug/unit/lint/release +
  new API 26/36 emulator instrumented matrix; lint `abortOnError`.
- FileProvider scoped to `processing/results/`; cloud backup disabled with
  full exclusion rules; cleartext only with per-job consent.

## Deliberately NOT done (risks / needs owner decision)

- No Navigation-Compose type-safe rewrite, no `QuietPrecisionComponents.kt`
  split, no per-screen ViewModel/UiState refactor: the string-route host works
  and is build-verified; a rewrite without an emulator to validate it risks
  shipping regressions.
- No structural PDF ops (merge/split/organize/extract): no qualified backend;
  cards stay honestly gated with reason text, no dead buttons.
- No dynamic color, no motion overhaul, no 200%-font/RTL device pass, no
  screenshots: needs a device/emulator. Fixed brand palettes apply everywhere.
- No Room migration test: only schema v2 exported, no v1 artifact to migrate
  from. No vault, no cookie export, no DRM bypass — by design.
- Baseline Profile skipped (AGP 8.10.1 `baselineProfile` DSL unresolved);
  documented in `docs/RELEASE.md`.
- Some icons use hard-coded English content descriptions instead of string
  resources; full unused-string sweep not done.
- GPL-3.0 yt-dlp payload: base builds are clean; extended-media builds are
  qualification-only until the owner signs the decision in
  `docs/DEPENDENCY_NOTICES.md`.
