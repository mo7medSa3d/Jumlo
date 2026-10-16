# Jumlo Release Guide

## Versioning

`versionCode` / `versionName` come from Gradle properties with safe defaults:

```sh
./gradlew -PversionCode=10843 -PversionName="1.5" :app:assembleRelease
```

Defaults live in `gradle.properties` (`10842` / `1.4-media-candidate`).
Increase `versionCode` on every Play release; `versionName` may follow the
git tag (e.g. tag `v1.5` → `-PversionName="1.5"`). The mapping is manual and
deliberate: the build never invents a version from git state.

## Signing

1. Copy `keystore.properties.template` to `keystore.properties` (never
   committed) and fill in store path, passwords, alias.
2. `./gradlew :app:assembleRelease` then signs with that config; without the
   file the build produces an unsigned APK for local inspection only.
3. Keep the keystore and `keystore.properties` out of backups shared with
   third parties.

## Pre-release checklist (must all pass in one session)

```sh
bash scripts/restore-wrapper.sh --check
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:connectedDebugAndroidTest   # needs an emulator; see below
```

Lint fails the build on errors. The base (no-flag) build must never contain
the GPL media payload; confirm with the release DEX check in `AUDIT.md`.
Extended-media builds (`-PextendedMedia=true`) are debug/qualification only
until the licensing decision in `docs/DEPENDENCY_NOTICES.md` is signed off.

Emulator (API 26 and API 36) is not installed in this environment; install
system images via `sdkmanager` and create AVDs before running instrumented
tests for a release you intend to ship.

## Baseline Profile

Skipped. AGP 8.10.1 does not resolve the `baselineProfile` DSL used by the
profile plugin and the checked-in `baseline-prof.txt` format was rejected,
so the module was removed rather than shipped broken. Revisit on AGP upgrade
with a macrobenchmark startup run to prove the gain.

## Play Console + data-safety answers

- Category: Tools. No ads, no in-app purchases, no accounts.
- Data collected: none off-device (see `docs/PRIVACY.md`). Declare "No data
  collected" only after confirming the release build has no media payload
  and no added telemetry; files the user explicitly downloads are
  user-initiated content, not collection.
- Permissions declared: Internet, network state, notifications, foreground
  service (data sync). State the user-facing purpose for each, matching
  `docs/PRIVACY.md`.
- Target API 36, min API 26. Upload the signed AAB/APK, complete the
  advertising-ID / news / COVID / data-safety questionnaires with the
  answers above, and roll out to internal testing before production.
