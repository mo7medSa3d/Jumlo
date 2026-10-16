# AUDIT.md — Jumlo current build truth (2026-10-17, branch `release-hardening`)

Supersedes all earlier "passed" claims. Results below were reproduced in this
session on: Temurin JDK 17.0.20.1, Gradle 8.14 (verified wrapper), Android
SDK with platforms 33–36 + build-tools 36.0.0, no emulator.

## Reproduction commands (from repo root)

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk
export ANDROID_HOME=$HOME/adb
bash scripts/restore-wrapper.sh --check
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:assembleDebug -PextendedMedia=true --console=plain
```

## Results (Aurora tree + re-applied hardening)

| Check | Result | Evidence |
|---|---|---|
| Wrapper SHA-256 (`restore-wrapper.sh --check`) | PASS | `7d3a4ac4…df296172` matches official Gradle 8.14; script needed no fix |
| `:app:assembleDebug` | PASS | builds clean |
| `:app:testDebugUnitTest` | PASS | 4 files, 19 tests, 0 failures/errors/skips (incl. `EphemeralSecretStoreTest`, 4 tests) |
| `:app:lintDebug` | PASS | `abortOnError=true` enforced in `app/build.gradle.kts`; one `InsertDriveFile` deprecation warning remains |
| `:app:assembleRelease` (R8) | PASS | unsigned APK ~2.2 MB |
| `-PextendedMedia=true assembleDebug` | PASS (pre-Aurora; re-run pending) | flag wiring works; GPL payload MUST NOT ship — see `docs/DEPENDENCY_NOTICES.md` |
| `:app:connectedDebugAndroidTest` | NOT RUN | no emulator / system images installed; CI runs API 26 + 36 matrix |

Note: one earlier combined `--rerun-tasks` invocation failed on `lintAnalyze*`
tasks, then the identical command passed repeatedly — treated as flakes.

## What changed

- Aurora retheme + workspace polish (committed as-is, verified green after):
  new palette values under stable token names, `QuietTheme.pillars`, Hero /
  SearchBar / SectionTitle / TonalButton / IconTile components, `JumloNavHost`
  polish. Contrast of the new pairs is unmeasured.
- Re-applied on top: media ToS/copyright notice + explicit session checkbox
  gating Analyze/Download (en+ar); dropped unused `VIBRATE` / `USE_BIOMETRIC`
  permissions and the unreferenced `androidx.biometric` dependency;
  `versionCode`/`versionName` from `-P` properties (defaults in
  `gradle.properties`); lint `abortOnError`; Aurora hard-coded nav strings
  moved to string resources (en+ar).
- Earlier: stale `com.universalutilities.app` schema export removed;
  `docs/DESIGN.md` created for the theme `DESIGN.md` references;
  `EphemeralSecretStoreTest` added; `docs/PRIVACY.md`, `docs/RELEASE.md`,
  full `docs/DEPENDENCY_NOTICES.md`; CI emulator matrix (API 26/36).
- FileProvider scoped to `processing/results/`; cloud backup disabled with
  full exclusions; cleartext only with per-job consent; secrets in-memory
  only (`EphemeralSecretStore`), never in logs/notifications/Room/backups.

## Deliberately NOT done (risks / needs owner decision)

- No Navigation-Compose type-safe rewrite, no per-screen ViewModel/UiState
  refactor: the string-route host works and is build-verified.
- No structural PDF ops (merge/split/organize/extract): no qualified backend;
  cards stay honestly gated with reason text, no dead buttons.
- No dynamic color, no 200%-font/RTL device pass, no screenshots: needs a
  device/emulator. Aurora contrast unmeasured.
- No Room migration test: only schema v2 exported. No vault, no cookie
  export, no DRM bypass — by design. Baseline Profile skipped (AGP 8.10.1
  DSL unresolved); see `docs/RELEASE.md`.
- GPL-3.0 yt-dlp payload: base builds are clean; extended-media builds are
  qualification-only until the owner signs the decision in
  `docs/DEPENDENCY_NOTICES.md`.
