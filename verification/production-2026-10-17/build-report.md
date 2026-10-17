# Native build, CI, migration and performance evidence

**Scope:** approved B01/B03/B05/B06 work on the supplied Jumlo source, integrated with the frozen production UI/domain changes. This build/CI/schema work made no commits or pushes. No remote workflow run or production signing occurred. The native gate results below are local, separate Gradle invocations. Device qualification and authentic historical migration remain open.

## Build repairs and selected versions

The application and `com.android.test` plugin now share the AGP catalog version at the root, and `:benchmark` is included again. Actual Gradle configuration exposed two further benchmark DSL defects: a nonexistent release build type in the test-only module and signing lookup against the wrong container. These are corrected. The application benchmark variant inherits the optimized release configuration, uses a local debug certificate for installation, and exposes shell profiling through its benchmark manifest.

Debug uses AGP's standard generated debug key instead of a missing project keystore. Release keeps R8 and resource shrinking. Release signing remains conditional on externally supplied keystore properties; no signing key is included in the source or verification evidence. Root-relative release keystore resolution now matches the supplied property template. The wrapper JAR and Gradle distribution checksums were checked, and the real Gradle8.14 wrapper ran successfully. The launcher is executable (0755).

A real repeat-invocation processor failure required a narrow compatibility repair. The final stack keeps AGP8.10.1, Kotlin and Compose compiler2.0.21, and Compose BOM2024.11.00. It selects KSP2 engine / matching KSP2.0.21-1.0.28, Room2.7.2 and Hilt2.54. These changes follow distinct reproduced failures:

1. Original KSP1 generated a genuine debug APK but failed a subsequent independent command with `FileAlreadyExistsException` while copying Hilt shadow outputs. A clean and `ksp.incremental=false` did not prevent recurrence. That attempted flag was removed; no per-task cleaning or test disabling is the repair.
2. KSP2 with Room2.6.1 failed inside XProcessing with `unexpected jvm signature V`. Official Room2.7 documentation adds Kotlin2/KSP2 support; stable2.7.2 processes the real schema successfully.
3. Hilt2.52 did not configure KSP2 tasks and produced incorrect missing-entrypoint-base errors. Official Hilt2.54 fixes that exact KSP2 task integration.
4. Hilt2.54 expects the `KspAATask.commandLineArgumentProviders` API missing in matching KSP patch1.0.27. Official matching1.0.28 source and inspected binary supply it. Kotlin itself was retained.

Primary URLs and concise rationale are retained in `build/processor-compatibility-sources.json`; binary/API inspection is in `build/ksp-api-inspection.log`. Original failure logs remain separate under `build/` with `ksp1-*`, `ksp2-room261-*`, `ksp2-room272-hilt252-*` and `hilt254-ksp1027-*` names. Source, resource and processor failures were corrected or reported to the owning agent; none were hidden by disabling processors, lint or tests.

## Reproduction environment

The local toolchain was provisioned outside the repository: full Temurin JDK17.0.20.1+1, Android platform36/build-tools36.0.0/platform-tools, Gradle8.14 through the checked wrapper and KotlinCLI2.0.21 for host tests. Tool downloads were checked against official published digests. The original environment's Java17 was a JRE without a compiler; that baseline failure is preserved.

Each native invocation uses `bash ./gradlew TASK --no-daemon --max-workers=2 --console=plain --stacktrace -PextendedMedia=false -Pkotlin.compiler.execution.strategy=in-process`. The last property and worker cap are local execution settings; they are not committed build workarounds. `build/final/native-suite-results.json` records exact arguments, exit status, duration and raw log path for each independent invocation. The aggregate suite does not clean between commands. Its local scratch init script redirects generated output and project-state directories to `/tmp` after actual workspace synchronization introduced a transient `.rsync-tmp` into a Gradle output snapshot. No task-state validation, incremental check or processor is disabled. Exact script contents/hash and paths are retained in `build/final/local-build-isolation.gradle` and `.json`; the failed environment invocation remains in `build/camera140-rsync-interference/`. A prior explicit clean was used once after changing processor stacks; its raw diagnostic debug log is kept outside `final/`.

## Migration evidence and explicit historical gap

No authentic v1 Room export was recovered. Both current and former package-path GitHub histories for `1.json` are empty; earliest available AppDatabase source at commit `73171962101267cdc6fc83213a8aed7f27d019b5` already declares version2. Public release and tag listings were empty. Raw API/history/source evidence is retained in `build/`, with hashes in `build/schema-source-hashes.json`.

`app/schemas/legacy-v1-structure.sql` reconstructs the two legacy table structures from that available source. It has no invented Room identity. The Android migration test seeds Arabic/null managed-file records and a paused download, migrates to2 with `MigrationTestHelper.runMigrationsAndValidate`, reopens through generated Room, checks retained values, and writes a new processing job. The fixture is loaded from the instrumentation APK's assets. Existing DAO round-trip coverage remains active.

The host SQLite structural check passes retained rows, column order/affinity/nullability/primary keys across all six v2 tables, declared indices, output logical uniqueness and database integrity (`build/structural-migration-sqlite.log`). Actual Room2.7.2/KSP2 regeneration leaves the complete tracked v2 JSON byte-identical to the pre-upgrade snapshot: identity `1c7241abd5b8ab41f5c26f14eef8d64f`, JSON SHA-256 `40877ee6f5bbd8d6010665548494bc3600a9908826d6774c71e0a7ae630d51e6`. The generated source and comparison are retained in `build/final/generated-AppDatabase_Impl.kt` and `schema-comparison.json`.

These facts establish structural coverage and real code generation. They cannot establish the identity of an installed historical v1 app. B03 remains blocked on authentic v1 JSON or a non-sensitive database from an actual v1 APK, followed by a real app-update migration test on API26 and36. No fabricated `1.json`, guessed identity or destructive-migration fallback was added. Schema artifacts are source-trackable after removing the blanket schema ignore rule.

## CI and baseline measurement

The CI workflow runs pinned Kotlin host behavior/storage/source/launcher checks, temporary-workspace cleanup safety and all11 standalone Home/fold policy cases before native Gradle tasks. The native gate runs configuration, debug compile, unit tests, lint, optimized release APK/AAB, actual artifact payload inspection, benchmark assembly and base release dependencies. It fails at the first nonzero stage. The API26/36 instrumentation matrix depends on this gate, has no automatic task retry, and archives raw logs, JUnit XML, lint reports, artifacts, logcat and screenshots under the exact GitHub SHA. Permissions are read-only. Local YAML validation does not constitute remote green CI or branch protection.

All three hand-written unmeasured profile inputs now contain comment-only pointers with zero app ART rules. Their distinct original bytes/hashes are retained under `build/historical-app_src_main_*.txt` and `build/profile-removal-provenance.json`; original unlinking had been resynchronized, so stable in-place replacement was necessary. The manual profile workflow compiles the release-like target and actual generator, runs its startup interaction on a rootable emulator and preserves measured output. The generator describes only its actual launcher-startup scenario; the startup benchmark fails if it cannot find the scrollable target. No generated profile, physical-device timing, startup/jank number or zero-jank claim is supplied without device execution.

## Actual native alignment correction

Inspection of the actual compiled debug APK found CameraX1.3.4's `libimage_processing_util_jni.so` with4096-byte ELF PT_LOAD alignment in arm64-v8a and x86_64. The artifact inspector correctly returns exit1 against that APK (`build/camera134-elf64-red.json`), preserving the failing SHA and library entries. The next stable CameraX1.4.0 official camera-core AAR has16384-byte load alignment on all four ABIs and minCompileSdk34. The shared CameraX pin is advanced only to1.4.0, with official AAR SHA/URL and Android guide in `build/camera140-correction.json`.

The packaging gate now checks actual APK/AAB native ELF64 PT_LOAD alignment alongside known extended-media path and DEX markers. It also runs build-tools36 `zipalign -c -P 16 4` on actual APKs. ELF alignment and ZIP alignment are separate checks. Neither establishes runtime behavior on a16KB device, correct RELRO behavior, broad store compliance or a complete dependency license audit.

The first actual lint run also found two `RestrictedApi` errors in the new hinge viewport's construction of WindowLayoutInfo. The UI owner replaced those with public tracker events mapped to the app's own HingeBounds list. No suppression/baseline was introduced. The original failed report, all266warnings and category summary remain under `build/pre-camera140-lint-restrictedapi/`.

## Verified native stage results

**Final local outcome:** all attempted configuration/compilation, unit, lint and packaging stages pass. Both connected commands fail with exit1 specifically because no devices are connected; no instrumentation, migration-on-device or performance execution is claimed.

These are separate local commands and explicitly scoped revisions, not a single untouched all-green invocation. The current direct unit execution used the same Kotlin/resources/dependency inputs as final packaging. The only later source-input change was the three ART text pointers; `build/final/source-evidence-scope.json` lists the exact delta.

| Stage | Exit | Seconds | Evidence |
|---|---:|---:|---|
| assemble-debug-final | 0 | 48.98 | `build/final/assemble-debug-final.log` |
| unit-tests | 0 | 24.72 | `build/final/unit-tests.log` |
| android-test-compile | 0 | 53.97 | `build/final/android-test-compile.log` |
| lint-debug | 0 | 124.26 | `build/final/lint-debug.log` |
| debug-profile-correction | 0 | 32.19 | `build/final/debug-profile-correction.log` |
| assemble-release | 0 | 253.4 | `build/final/assemble-release.log` |
| bundle-release | 0 | 36.55 | `build/final/bundle-release.log` |
| benchmark-assemble | 0 | 374.02 | `build/final/benchmark-assemble.log` |
| base-release-dependencies | 0 | 24.32 | `build/final/base-release-dependencies.log` |
| connected-debug | 1 | 60.03 | `build/final/connected-debug.log` |
| baseline-generation | 1 | 35.28 | `build/final/baseline-generation.log` |

The connected-debug and baseline-generation raw logs both contain `com.android.builder.testing.api.DeviceException: No connected devices!`. The latter invokes `:benchmark:connectedBenchmarkAndroidTest` with the BaselineProfileGenerator class. No API26/API36 test ran, and no profile or physical timing was produced.

The genuine current-source unit task executed all **84 tests,0 failures,0 errors,0 skipped** in58.91s before output relocation (`unit-genuine-execution.log`). The later isolated unit task restored those exact XML bytes from the Gradle build cache in24.72s; it is not a second fresh84-test execution. Raw XML, XML hashes and exact-byte comparison are retained in `build/final/junit/` and `unit-test-summary.json`. Instrumentation sources compile; compilation is not device execution.

Lint passes with **0 errors,266 warnings,2 hints**. Full HTML/XML/text and category/report hashes are retained in `build/final/lint/`. Warning triage is documented in `lint-warning-review.md`: the warnings are not silently suppressed and the passing lint exit does not establish broad security qualification.

## Actual packaged artifacts

| Artifact | Bytes | SHA-256 | Inspection |
|---|---:|---|---|
| `/tmp/jumlo-native-generated/app/outputs/apk/debug/app-debug.apk` | 51935674 | `1551ea95dd1e6d160ac6984942737d995a75b4fcfbd0a205abd5a043774e715c` | PASS |
| `/tmp/jumlo-native-generated/app/outputs/apk/release/app-release-unsigned.apk` | 28402708 | `f27e76449ce6209283810f1000e18fe91f71dcbb699adb026ba5ec9690ed4622` | PASS |
| `/tmp/jumlo-native-generated/app/outputs/bundle/release/app-release.aab` | 33108595 | `6877727c6d5811b088556f5c950f12d397e4a90fc9f4b26640372646631bd4e2` | PASS |

`standard-content.json` records ZIP CRC, actual native/DEX entries, known extended-media marker checks and ELF64 load alignment. APK ZIP alignment and signature/badging logs are separate. Both APK ZIP alignment checks pass. Debug APK-v2 signing verifies against an Android Debug certificate. Release APK signature verification correctly exits1 because it is unsigned; the AAB has no JAR signer entries (`artifact-checks.json`). Actual R8 mapping/usage/configuration hashes and resource-shrink task evidence are retained in `r8-evidence.json`. Benchmark APK metadata and the actual profileable target manifest are retained in `benchmark-artifact-metadata.json` and `benchmark-app-manifest.log`. The resolved base release graph contains none of the excluded extended-media groups (`base-dependency-check.json`). Release is unsigned unless explicit evidence says otherwise; no key was supplied. APK/AAB binaries are intentionally excluded from the source ZIP; real binary metadata and raw build evidence are retained.

## Input identity and remaining release gates

The final manifest covers **201 inputs**, including source, schemas, wrapper/configuration, modes, CI and host verification fixtures. Digest: `949f411ef070e0d5f49076389f6149143559b86c00a3e60c4c331dbdcae030d6`. It includes `gradlew`0755, the valid package-only host pointer, both standalone Home/fold test files and the schema ignore correction. Final content/mode comparison is retained in `source-input-check.json`.

| Requirement | Implemented / locally verified | Remaining qualification |
|---|---|---|
| B01 native build | Consistent AGP, restored benchmark, default debug signing, exact processor repair; debug/unit/AndroidTest compile/lint and optimized APK/AAB/benchmark assembly pass | API26/36 runtime qualification, production signing and remote exact-SHA CI remain open |
| B03 migration | Authentic source provenance, honest structural fixture, actual v2 Room generation, SQLite data/structure checks; Android test compiles | Authentic historical v1 identity and installed-app/API26/36 migration execution unavailable |
| B05 performance | Both benchmark APKs assemble; actual target manifest exposes profileable shell=true; real generator/workflow, no active hand-written app rules | Measured profile, startup/jank/physical-device results not available without actual device execution |
| B06 signing | Standard AGP debug signing; release stays unsigned without owner-supplied signing properties; no private keys included | Production owner-signed install/update, key custody and release signing remain open |
| Phase1 CI | Host/native fast-fail gate, API26/36 dependency matrix, exact-SHA artifacts, no silent retries | No remote workflow run/push or repository branch-protection change occurred |

Actual `adb devices -l` reports zero attached devices and `/dev/kvm` is absent. API26/36 instrumentation, real old-install migration,16KB-device/RELRO behavior, measured baseline generation and physical-device performance remain open. Production signing, store/privacy/license qualification and a green remote run on the eventual pushed SHA remain release gates. No production-ready or measured-performance claim is made.

Relevant delivered changes are in root/settings/catalog/properties Gradle files, app and benchmark Gradle modules, benchmark sources/manifest, the three ART pointers, app schemas/provenance, `DatabaseMigrationAndDaoTest.kt`, CI/baseline workflows, `scripts/verify-native.sh` and `scripts/inspect-standard-artifact.py`. Historical failures, schema evidence and toolchain checks remain in `build/`; final reports, logs and binary metadata remain in `build/final/`.
