# SDD ledger — plan: docs/approved/tasks.json (+ session phases 0-8 brief)

Pre-flight: all 41 tasks consume shared architecture (CapabilityRegistry → OperationDispatcher → handlers → NavHost WorkspaceFields). Interface rows: P02/E01-E11 consume P01 PdfBackend; E12-E17 consume ImageEngine; A02/E24 consume ArchiveCore; Phase 4 navigation consumes all routes. Rulings recorded below as they arise.

## Phase 0 — Build gate

- Wrapper SHA-256: PASS. `sha256sum gradle/wrapper/gradle-wrapper.jar` = 7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172 — exact match with official Gradle 8.14 (independently computed, not via restore-wrapper.sh).
- Env: JDK 17.0.x Temurin (/usr/lib/jvm/java-17-temurin-jdk), Gradle 8.14 cached, Android SDK $HOME/adb (platforms 33-36, build-tools 33-36), no emulator initially.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` → EXIT=0 (but mostly UP-TO-DATE/FROM-CACHE).
- `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` → EXIT=0 (61 from cache).
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-build-cache --rerun-tasks` → EXIT=1: 3 lintAnalyze tasks crashed with lint-internal FileNotFoundException (KotlinLocalVirtualFile) — **the exact flake AUDIT.md documented**. All 93 other tasks executed: compileDebugKotlin OK, unit tests 19/19 pass (4 suites: MediaMetadataTest 4, EphemeralSecretStoreTest 4, PureDomainAlgorithmsTest 8, WorkspaceSettingsTest 3), minifyReleaseWithR8 executed OK, unsigned release APK 2.1MB.
- `./gradlew :app:lintDebug :app:assembleDebug` retry → EXIT=0. Ruling: lintAnalyze crash under --rerun-tasks is an AGP 8.10.1/lint worker flake, not a source defect; normal invocations pass. Cost if wrong: a real lint-detector bug could hide behind this classification.
- Emulator: /dev/kvm accessible (stat OK). sdkmanager install of `emulator`, `system-images;android-26;default;x86_64`, `system-images;android-36;google_apis;x86_64` launched in background. connectedDebugAndroidTest pending AVD creation.
- ZERO source changes made in Phase 0: the Aurora redesign in this tree compiles clean (contradicts "unverified" brief; evidence above).
