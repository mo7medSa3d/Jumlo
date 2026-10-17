# Latest execution status for the updated source ZIP — 2026-10-17

> **Most recent source revision — 2026-10-17 (hardened ZIP follow-up): NO-GO.**
> This entry supersedes older current-session totals further below. Actual local
> `LC_ALL=C.UTF-8 LANG=C.UTF-8 bash scripts/verify-host.sh` returned **0**
> (117 Kotlin host, 8 media guard, 10 SQLite, 14 source/font/localization).
> Workspace cleanup **6/6**, Home/fold **11/11**, synthetic FFmpeg **5/5**,
> media-source gate **5/5**, feature matrix regeneration **0**. Logs:
> `verification/current-session/followup-2026-10-17/`.
> The host ran on **Java 21 / Kotlin CLI 1.9.0**, not the required
> CI toolchain. Gradle 8.14 wrapper failed before task execution due to
> unavailable `services.gradle.org` DNS; the Android SDK is absent.
> `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`,
> `:app:assembleRelease`, `:app:bundleRelease`, `:benchmark:assembleBenchmark`,
> all API 26/36 tests, native output files and screenshots remain
> **UNVERIFIED**. New zip4j, Room and Drive JUnit assertions were authored,
> **NOT run**. No platform media live tests, Google OAuth device sessions,
> reproducible startup/jank measurements or owner-signed release.
> Release media stays blocked until exact FFmpeg source/flags/license and
> all platform matrices are approved. See `followup-2026-10-17/REPORT.md`.

**Release decision: NO-GO; Android build/installed app UNVERIFIED for these edits.** The historical report below was bundled with the input archive and describes **a different source revision**. Do not treat its previous Gradle APK hashes, 84 unit tests, lint results or R8 results as evidence that this newly edited source passes. No GitHub CI run or owner-signature installation was executed.

The current session extracted the supplied source into an isolated working directory and changed ZIP processing, local diagnostic retention, release-only media source/dependency isolation, a CI native dependency gate, and test/doc artifacts. A local `./gradlew --no-daemon --offline :app:assembleDebug` attempt exited **1** before task execution because the wrapper could not download a Gradle distribution; `ANDROID_HOME`/`ANDROID_SDK_ROOT` and Android SDK tooling were unavailable. Network name resolution failed. Debug compilation, JVM Android tests (including new `EncryptedZipSafetyTest`), lint, R8 release, benchmark assembly, and API 26/36 instrumentation are **NOT RUN**. Do not describe them as PASS.

Reproduced host results: the final `LC_ALL=C.UTF-8 LANG=C.UTF-8 bash scripts/verify-host.sh` executed **113/113 core host cases**, including seeded property checks of **256 page-range, 256 archive-path and 256 URL cases**; the same final execution passed **8/8 media bootstrap, 9/9 SQLite and 12/12 static/font/translation checks**. Before adding the fuzz properties, `scripts/test-core.sh` after the log fix **110/110 passed**; `scripts/verify-host.sh` with UTF-8 locale before this follow-up **108/108 core, 8/8 media/bootstrap, 9/9 SQL, 12/12 resource/structural checks passed**; `scripts/test-workspace.sh` **6/6**; the original `run-home-layout-tests.sh` **11/11**; `scripts/test-media-ffmpeg.py` **5/5 synthetic FFmpeg checks**; and `scripts/test-release-media-isolation.py` **3/3 source isolation checks**. The host results do not establish Android API behavior, output accessibility or live service support. Logs are in `verification/current-session/`.

The release-safe source split now routes `src/base/java` to `release` and `benchmark`, while an explicitly opted-in `debug` variant alone takes `src/media/java`. The optional media AARs are `debugImplementation`, not `implementation`. `scripts/verify-native.sh gate` now asks Gradle to inspect the **releaseRuntimeClasspath** again with `extendedMedia=true`, failing CI if any external media artifact appears. This is a proposed and statically inspected safeguard, **not** a reproduced Gradle graph or APK inspection in the current session.

Owner sign-offs required: (1) exactly which media libraries/bundled FFmpeg build flags and GPL/LGPL obligations are acceptable, with FFmpeg native AAR hash and source/license notice provenance; (2) retain the two IBM Plex files pinned by exact SHA-256 (source check passed), or choose another verified font distribution method; (3) keep external-media release feature gated until legal/technical approval and device security verification; (4) which platforms, if any, pass actual live video/audio output checks (currently **none**); (5) signing key, authentic Room v1 upgrade, device output roundtrips, locale/RTL accessibility and performance baselines.

Current commands for CI-equivalent verification after extracting ZIP on an Android runner with JDK 17, API 36 and Gradle wrapper network access:

```bash
LC_ALL=C.UTF-8 bash scripts/verify-host.sh
python3 scripts/test-release-media-isolation.py
python3 scripts/test-media-ffmpeg.py
bash scripts/verify-native.sh gate
# With Android API 26 and 36 emulator/device:
bash scripts/verify-native.sh instrumentation
# On a designated physical performance device:
bash scripts/verify-native.sh baseline
bash scripts/verify-native.sh benchmark
```

A passing native gate must include debug, Android JVM unit, lint, optimized R8 release, release bundle, dependency graphs for both media flags and benchmark compile; this step was **not** executed locally. The current `docs/FEATURE_STATUS.md` lists all 45 definitions without promoting any to end-to-end VERIFIED.

---


# Jumlo release and verification guide — 2026-10-17

## Delivery decision

This is an updated-source handoff for the supplied ZIP. **Production release remains NO-GO until the device, historical migration, signing and exact-SHA CI criteria below are satisfied.** Build success, source review and authored instrumentation are not interchangeable with installed-device qualification.

The supplied source ZIP SHA-256 is `37c3d4445c6455a1c0da2cd036fd6b597781a4eea4944bba4b0ee6eca794952e`. No remote commit, push or workflow run was performed. The archive cannot be represented as the plan's earlier inspected GitHub SHA. Final native source/config hashes and file modes are recorded in `verification/production-2026-10-17/build/source-input-manifest.json`; the delivered ZIP also contains its complete per-file `CONTENTS_SHA256.json`.

## Observed native outcomes

All required local compilation, unit, lint, optimized packaging, benchmark assembly and base-dependency stages completed successfully. The actual connected-device commands failed because no device was attached. These are separate recorded commands with explicitly scoped input revisions, not one untouched all-green invocation or an installed-device pass.

| Command / check | Actual result | Evidence under `verification/production-2026-10-17/build/final/` |
|---|---|---|
| `:app:assembleDebug` after profile correction | **PASS**, 32.19 s | `debug-profile-correction.log` |
| `:app:testDebugUnitTest` | **84 passed**, zero failures/errors/skips | `unit-genuine-execution.log`, `unit-test-summary.json`, `junit/` |
| `:app:compileDebugAndroidTestKotlin` | **PASS**, 53.97 s | `android-test-compile.log` |
| `:app:lintDebug` | **PASS**, 0 errors, 266 warnings, 2 hints | `lint-debug.log`, full reports and hashes in `lint/` |
| `:app:assembleRelease` | **PASS**, 253.40 s; R8 and resource shrinking active; unsigned APK | `assemble-release.log`, `r8-evidence.json` |
| `:app:bundleRelease` | **PASS**, 36.55 s; unsigned AAB | `bundle-release.log` |
| `:app:assembleBenchmark :benchmark:assembleBenchmark` | **PASS**, 374.02 s; actual optimized app and test APKs built | `benchmark-assemble.log`, `benchmark-artifact-metadata.json` |
| Release runtime dependency graph | **PASS**, 24.32 s; recorded standard-build dependency scan | `base-release-dependencies.log`, `base-dependency-check.json` |
| `:app:connectedDebugAndroidTest` | **BLOCKED**, exit 1 after 60.03 s: `No connected devices!` | `connected-debug.log` |
| `:benchmark:connectedBenchmarkAndroidTest`, filtered to `BaselineProfileGenerator` | **BLOCKED**, exit 1 after 35.28 s: `No connected devices!` | `baseline-generation.log` |

Exact arguments, exits and durations are retained in the [native command ledger](../verification/production-2026-10-17/build/final/native-suite-results.json). The [native build report](../verification/production-2026-10-17/build-report.md) explains each repair and evidence boundary. Earlier successful and failed diagnostics remain under `build/` and are not relabeled as final results. No physical performance run, measured app baseline profile, API26/36 instrumentation result, or owner-signed install/update result is claimed.

The genuine 84-test execution took 58.91 s. A later command restored the exact same JUnit XML bytes from Gradle's build cache in 24.72 s; it is not another fresh execution of 84 tests. Kotlin, resources, test code, dependencies and build configuration are identical to those used for final packaging. Only the three ART text profile files changed after the unit/compile/lint checks; all affected packaging gates were rerun. [Evidence scope](../verification/production-2026-10-17/build/final/source-evidence-scope.json) records that delta, and the [final input check](../verification/production-2026-10-17/build/final/source-input-check.json) verifies the 201-file content/mode manifest. Focused host counts below overlap several native tests and must not be added as unique tests.

An actual generated-output snapshot encountered a transient workspace `.rsync-tmp` path. The subsequent local commands therefore used a recorded scratch Gradle init script to place generated build directories and the project-state cache under `/tmp`. This affected only local output locations; delivered Gradle/CI configuration does not depend on that script. No processor validation, incremental checks, tests, lint or optimization were disabled. The [local isolation record](../verification/production-2026-10-17/build/final/local-build-isolation.json) preserves the exact script hash, reason and diagnostic log.

### Actual built artifacts

The following are verified build outputs; their binaries are excluded from the requested source ZIP. Byte sizes and SHA-256 values come from the actual files, not size estimates or a comparison with the original source snapshot.

| Artifact | Bytes | Signing | SHA-256 |
|---|---:|---|---|
| Debug APK | 51,935,674 | Android Debug certificate; verification passed | `1551ea95dd1e6d160ac6984942737d995a75b4fcfbd0a205abd5a043774e715c` |
| Release APK | 28,402,708 | Unsigned; signature checker exit 1 is expected | `f27e76449ce6209283810f1000e18fe91f71dcbb699adb026ba5ec9690ed4622` |
| Release AAB | 33,108,595 | Unsigned; no JAR signer entries | `6877727c6d5811b088556f5c950f12d397e4a90fc9f4b26640372646631bd4e2` |

All three passed the recorded known external-media marker and ELF64 PT_LOAD alignment checks. Both APKs passed `zipalign -c -P 16 4`. The release APK contains one DEX entry and retained R8/resource-shrink evidence; this is a packaging observation, not a measured startup or memory improvement. See [content inspection](../verification/production-2026-10-17/build/final/standard-content.json), [signature/ZIP checks](../verification/production-2026-10-17/build/final/artifact-checks.json), and [R8 evidence](../verification/production-2026-10-17/build/final/r8-evidence.json). These checks do not establish complete licensing, RELRO or 16KB-device runtime qualification. The optimized benchmark app and benchmark test APK hashes are recorded separately in `benchmark-artifact-metadata.json`.

## Executed host and focused JVM evidence

| Scope | Actual observed result | Evidence location under `verification/production-2026-10-17/` |
|---|---|---|
| Core/file/process/HTTP regressions | 108 passed, zero failed in the authorized loopback runs | `engine-copy-green-full.log`, `files-host-suite-loopback.log` |
| Media bootstrap / storage SQLite / source consistency | 8 / 9 / 12 cases passed in their recorded runs | `files-host-suite-loopback.log`, `files-source-static-final.log`, `files-host-final-aux.log` |
| Launcher and offline preflight guards | 4 passed | `files-host-launchers.log` |
| Temporary cleanup filesystem safety | 6 passed | `workspace-green.log` |
| Expanded workspace settings + secret rebinding | 20 passed after five reproduced missing-validation failures | `workspace-essentials-red.log`, `workspace-essentials-green.log` |
| Files permissions and rename proposal policy | 13 passed | `files-focused-jvm.log` |
| Independent current workspace/secret + Files check | 33 passed; overlaps the 20 and 13 cases above | `review-supplemental-jvm.log` |
| Home choices and fold rectangle policy | 11 passed | `ui/home-layout-green.log` |
| Source packaging | Deterministic/exclusion/mode/CRC/hash tests and corruption rejection passed | `engine-package-green.log`, `engine-package-validation.log` |

Counts are scoped; several focused tests overlap the native unit suite and earlier review runs. They must not be added into an invented total of unique tests. Restricted-socket attempts are retained as failures with `SocketException: Operation not permitted`; the authorized loopback reruns are separate actual executions. Source-only checks do not prove Android behavior.

## Build baseline and compatibility repair

Use a full **JDK 17**, Gradle **8.14** from the checked wrapper, Android SDK platform **36**, and Build Tools **36.0.0**. The app keeps minSdk **26**, compileSdk/targetSdk **36**, AGP **8.10.1**, Kotlin/Compose compiler **2.0.21**, and Compose BOM **2024.11.00**. Both application and test Android plugins have one version-catalog owner; `:benchmark` remains included.

Actual repeated native commands exposed a sequence of processor compatibility defects. KSP1 collided on generated Hilt shadow outputs even with incremental processing disabled. Switching to KSP2 then exposed the older Room compiler's JVM-signature failure and the older Hilt plugin's missing KSP2 task configuration. The final narrow compatibility set is **KSP 2.0.21-1.0.28 with KSP2, Room 2.7.2, and Hilt 2.54**. The matching KSP patch provides the exact task argument API used by Hilt. No processor validation, test suite, lint gate or release optimization was disabled to suppress these errors.

The actual failure logs, upstream URLs and class/API inspection are preserved in `build/processor-compatibility-sources.json`, `build/ksp-api-inspection.log` and the version-labeled diagnostic logs. Room's generated v2 schema remained byte-identical to the supplied v2 JSON after the processor change; `room-272-ksp2-evidence.log` records the identity comparison. Compatibility is accepted from the actual final commands, not inferred solely from release notes.

The actual debug APK also exposed 4KB ELF load alignment in CameraX 1.3.4's `libimage_processing_util_jni.so` on both 64-bit ABIs. CameraX **1.4.0** is the next stable release line and its official Google Maven AAR has 16KB load alignment. The native gate now rejects insufficient 64-bit ELF load alignment and checks APK ZIP alignment; the older actual APK fails that new check as recorded in `build/camera134-elf64-red.json`. These binary checks do not establish runtime behavior on a 16KB device or comprehensive RELRO qualification. The [Android 16KB support guide](https://developer.android.com/guide/practices/page-sizes) explains the separate binary, packaging and runtime checks.

## Reproduce the verification

Configure the local Android SDK and JDK, then run:

```bash
bash scripts/restore-wrapper.sh --check
bash scripts/verify-host.sh
bash scripts/test-workspace.sh
bash verification/production-2026-10-17/ui/run-home-layout-tests.sh
python3 scripts/test-media-ffmpeg.py
python3 scripts/generate-feature-matrix.py
bash scripts/verify-native.sh gate
```

Host checks require Kotlin CLI 2.0.21 and Python; the FFmpeg suite additionally requires FFmpeg. HTTP fixtures need permitted local loopback sockets. The source includes an executable `gradlew`; the wrapper JAR/distribution are checksum checked. Do not reuse unverified third-party wrapper binaries.

`verify-native.sh gate` records independent configuration, debug, unit, lint, release APK, release AAB, benchmark compilation and base-dependency commands. It stops on the first failed task and preserves its exit code. It also inspects actual APK/AAB contents for excluded external-media payload markers and insufficient 64-bit ELF load alignment, then checks APK ZIP alignment. The gate does not insert per-task cleans or silent retries.

On connected API 26 and API 36 devices/emulators, run independently:

```bash
bash scripts/verify-native.sh instrumentation
```

These checks include Room, file/output, image, UI, preference and recovery tests. Compile success alone is not an instrumentation pass. Capture each device's API/build fingerprint, source/artifact hashes, test reports, screenshots, logcat and real fixture outputs. Actual process death, force-stop, low disk, grant revocation and old-install migration require their acceptance scenarios as well.

## Signing and versioning

Debug uses AGP's normal local Android Debug key; no repository keystore is required. `release` keeps R8 minification and resource shrinking. Without owner-supplied `keystore.properties`, a release build is **unsigned** and cannot establish the owner's production installation/update chain. The benchmark target inherits release optimization, is non-debuggable/profileable, and uses debug signing for local installation.

For an owner-signed release, copy `keystore.properties.template` to the excluded `keystore.properties` and supply `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Relative store paths resolve from the repository root. Private keys, credentials and local properties are excluded from the source ZIP. No production key was provided or created for this task.

Current default version properties are `versionCode=10842` and `versionName=1.4-media-candidate`. Assign the intended next release version explicitly; for example:

```bash
./gradlew -PversionCode=10843 -PversionName=1.5.0 :app:assembleRelease :app:bundleRelease
```

An example version is not a published release. Install the correctly signed candidate and verify upgrade/data retention from a real earlier installation before distribution.

## Database migration boundary

`app/schemas/com.jumlo.app.data.local.AppDatabase/2.json` is the authentic supplied/current schema and was compared with actual generated code. Historical searches did not locate an authentic v1 export or old database identity. `legacy-v1-structure.sql` is explicitly a reconstructed structural fixture; host SQLite checks and the Android migration test do not turn it into authentic historical installation evidence.

Obtain a real previous database, exported schema or APK and execute an update test preserving its data and identity. B03 remains open until that evidence exists. See [schema provenance](../app/schemas/schema-provenance.md). The schema/fixture/provenance directory is source-trackable and included in the ZIP; it is no longer hidden by a blanket `schemas/` ignore rule.

## Baseline profiles and performance

The three original handwritten ART profile files contained unmeasured rules, including invalid method flags on class entries that failed actual release parsing. Their individual contents and hashes are retained in `build/profile-removal-provenance.json` and the referenced historical copies. All three active paths now contain comment-only pointers with zero ART rules; the files remain present because deleted files were restored by workspace synchronization. `BaselineProfileGenerator` and `StartupBenchmark` are executable source with a release-like target. No generated measured profile or physical-device performance result is asserted here.

On designated representative physical hardware:

```bash
bash scripts/verify-native.sh baseline
bash scripts/verify-native.sh benchmark
```

Retain actual generated rules, JSON and Perfetto traces, then compare equivalent profile-on/profile-off builds before packaging a measured baseline. [PERFORMANCE_REPORT.md](PERFORMANCE_REPORT.md) documents the required cold/warm startup, frame, memory and processing measurements. Build-host durations and emulator performance cannot stand in for physical-device results.

## CI and distribution review

`.github/workflows/ci.yml` runs checksum-pinned host tooling/tests and the serial native gate before the API 26/36 instrumentation matrix. It preserves per-SHA logs, test/lint reports and real built artifacts. `.github/workflows/baseline-profile.yml` is an explicit generation workflow. These workflows were edited locally; there is no newly pushed SHA or observed remote green run in this delivery.

The base build sets `extendedMedia=false`. All public availability decisions also reject unqualified media, high-risk PDF/ZIP, Drive, OCR and webpage operations. A feature flag alone is not a licensing qualification; see [dependency notices](DEPENDENCY_NOTICES.md) and actual built-content inspection results.

Review store privacy/data-safety declarations against the shipped manifest, SDKs and actual enabled flows. [PRIVACY.md](PRIVACY.md) describes storage, explicit downloads and permissions. The platform network configuration permits cleartext; application policy requires per-job HTTP consent. Do not reuse the supplied older guide's claim that the manifest itself globally blocks cleartext, or blindly copy blanket collection/sharing answers without the final distribution review.

## Open release criteria

- Authentic old-install database migration and owner-signed install/update evidence.
- Installed API 26/36 instrumentation, 16KB-page runtime qualification, real file roundtrips, process-death/cancellation, provider/grant and low-space scenarios.
- EN/AR, light/dark, compact/tablet/fold, 200% fonts, TalkBack, keyboard/IME and gesture/three-button interaction acceptance with actual screenshots.
- Representative physical startup/frame/memory measurements and a generated/validated baseline profile.
- Final built-manifest/assets/DEX/dependency and licensing/privacy review.
- Required remote CI green on exactly the intended final SHA, with every P0/P1 closure criterion met.

The requested source archive retains the implementation, schemas, tests, scripts, current reports and raw evidence. It excludes generated APK/AAB outputs, local SDK/JDK/cache directories and private signing material.
