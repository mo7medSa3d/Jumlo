# Jumlo Android

Android utilities application in Kotlin: PDF tools, document conversion, files, printing and internet downloads. This source accompanies the 1.6-tools rebuild. Read **TEST_REPORT.md** for the executed results and remaining release gates; implemented code is not a claim that every website or device has been qualified.

## Build

Use JDK 17, Python 3, Android SDK platform 36 and build-tools 36.0.0. The checked-in Gradle wrapper pins Gradle 8.14 and verifies its distribution checksum. SDK licenses must be accepted in your development environment. Set `ANDROID_HOME` or create a local `local.properties` containing `sdk.dir=...`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
./gradlew :app:verifyReleaseApkSize :app:verifyCompactApkSize :app:bundleRelease
./gradlew :app:lintRelease
./gradlew :app:connectedDebugAndroidTest
```

The last command requires a booted emulator or connected, authorized device. Some media instrumentation uses real packaged native binaries and can run slowly under software emulation. A timeout override is available through the `mediaProcessTimeoutMillis` instrumentation argument. Play services provide the primary scanner and unbundled Latin OCR model. The CameraX scanner fallback supports devices where the primary scanner cannot launch; OCR still requires its supported model.

Before the unfiltered connected-test task, follow the [instrumentation preflight](verification/current/device/preflight-source/README.md). `PdfCompressionDeviceTest` requires the five `scan_01pages.pdf`, `scan_03pages.pdf`, `scan_06pages.pdf`, `scan_10pages.pdf` and `scan_30pages.pdf` inputs in the debug app's external `files/bench` directory, normally `/sdcard/Android/data/com.jumlo.app.debug/files/bench`. The corpus recipe is `scripts/test-pdf-compression.sh --measure`; it writes inputs and separate host measurements beneath the JVM temporary directory's `jumlo-compression-measure` folder. Stage only the five scan inputs, preserve their identities, and record hashes for any newly generated corpus. The preserved preflight binds the actual supplied corpus hashes and explains the generated 200-page document, simultaneous scratch files, guest free-space checks and host QCOW growth. Its 4–4.5 GiB guest planning allowance is an estimate, not a measured peak or a guarantee. Missing fixtures, SDK-filtered methods and opt-in screenshot skips must remain explicit in the results.

| Variant | Purpose | CPU architectures | Signing |
|---|---|---|---|
| `debug` | Development and emulator tests; application ID `com.jumlo.app.debug` | ARM64 and x86_64 | Local Android debug key |
| `compact` | Minified, resource-shrunk installer for ARM64 testing | ARM64 | Local Android debug key; **not a production release key** |
| `release` | Production APK / Android App Bundle | ARM64; bundle split configuration enabled | User-provided release credentials, otherwise unsigned |
| `benchmark` | Profileable release-equivalent performance target | ARM64 | Local debug key |

The full local media engine is the default. `-PextendedMedia=false` is an explicit development-only direct-file build and cannot extract social-media pages. It is rejected by both full-feature size gates. `verifyCompactApkSize` measures the actual APK and fails above 60,000,000 bytes. The requested distribution ceiling is not inferred from a dependency estimate.

Common extractor, pins, licenses and ARM64 repair assets live in `app/src/media/assets`. The five x86_64 repair assets live in `app/src/mediaDebug/assets` and are included only in the full-runtime debug build. ARM64 release/compact/benchmark and explicitly lightweight builds exclude that debug asset directory.

For production signing, set `JUMLO_KEYSTORE_PATH`, `JUMLO_STORE_PASSWORD`, `JUMLO_KEY_ALIAS`, `JUMLO_KEY_PASSWORD`, or create an untracked `keystore.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Environment values take precedence. `verifyPublishableRelease` requires the complete optimized release, signing configuration, size and lint gates. Supply your own key securely. No production signing key is included. Release version values come from `gradle.properties`, and may be overridden with `-PversionCode=... -PversionName=...`.

## Architecture

The existing Compose / Material 3 / typed Navigation / Hilt / Room / DataStore / WorkManager architecture is retained. WorkManager foreground work declares `dataSync`. Download and file jobs keep their durable ownership in Room; publication uses verified private files plus a MediaStore or persisted SAF destination.

Four actual Kotlin/JVM modules separate reusable logic from Android adapters:

| Module | Responsibility |
|---|---|
| `core-common` | Validation, names, catalogs, budgets and shared policies |
| `downloader-core` | Media selection/routing, transfer/resume and process contracts |
| `document-core` | PDF geometry/text policies, image policies and OOXML packages |
| `storage-core` | Verified file copies, paths, publication and cleanup policies |

These modules have no Android imports, but use JVM APIs. They are **not Kotlin Multiplatform/iOS binaries**. See `iOS_PLAN.md` for the required ports and native adapters.

The on-device extractor remains behind `MediaRuntime`; direct files use OkHttp. The repository's optional service adapter is a development option and is not a deployed backend. Native payload repacking validates upstream and output hashes, checks ELF dependencies, and preserves retained compressed ZIP records without host-dependent recompression. Details are in `docs/media/UNIVERSAL_ENGINE.md`.

Treat payload-size figures in the media architecture note as historical. The [preserved artifact inventory](verification/current/artifact-checkpoints/checkpoint-1/final-release-packaging.json) distinguishes raw archive bytes from compressed APK entries for that checkpoint; final-candidate measurements and signing status belong in `TEST_REPORT.md` after the fresh build and inspection.

## Documents and limits

- `AUDIT.md`: source inventory and the decisions behind retained, hidden and removed items.
- `docs/AUDIT_2.md`: 2026-10-28 evidence-backed capability inventory and release gap register.
- `docs/AUDIT_2_SOURCE_SCAN.md`: 244-file Kotlin/Java source lexical candidate scan with file:line locations; not Android-runtime evidence.
- `ROUTES.md`: typed routes, entry points, tab ownership and back behavior.
- `CHANGELOG.md`: concrete implementation changes.
- `TEST_REPORT.md`: measured builds, sizes, tests and outstanding device/platform checks.
- `iOS_PLAN.md`: practical reuse and porting plan.
- `TOOL_UI_SPEC.md` / `ICON_MAP.md`: per-tool interaction contract and official icon provenance.
- `PRIVACY_AND_PERMISSIONS.md`, `PRIVACY_POLICY.md`, `PLAY_STORE_LISTING.md`: reviewed data flows and owner-completed publication drafts.
- `RELEASE_CHECKLIST.md`: signing, device, performance and Play gates.
- `SCREENSHOTS/`: actual captured UI states and an explicit coverage/blocker manifest.

PDF to Word produces basic paragraphs, detected headings and page breaks; it does not reconstruct complex tables or page layout. Textless pages request Latin OCR. Arabic text already embedded in a PDF is preserved as Unicode, but scanned Arabic recognition is not supported by the selected recognizer. PDF to PowerPoint uses a page image with an editable extracted-text layer, not reconstructed presentation shapes. OCR model/scanner installation may require a first network connection.

The fonts are OFL-licensed subsets of IBM Plex Sans and IBM Plex Sans Arabic, internally renamed Jumlo Sans and Jumlo Sans Arabic. The license is bundled in `app/src/main/assets/licenses/IBM-Plex-OFL.txt`. `tools/subset-fonts.py` reproduces the subsets from the original upstream fonts using fontTools. It is not needed for ordinary builds. Third-party native licenses and notices remain in the media assets.

On Android 10+ default public copies use MediaStore; a selected SAF folder persists its URI grant. On Android 8–9 the default copy is in the app-specific external Downloads directory; choose a SAF folder for a durable shared location. Clearing cache leaves source documents, published outputs and active job state intact.

This rebuild does not establish the original paste-crash stack trace, live provider availability, successful physical-device tests, or Microsoft Office application compatibility. Refer to the explicit evidence and pending matrix in `TEST_REPORT.md` before shipping.

Before the unfiltered connected-test task, follow the [instrumentation preflight](verification/current/device/preflight-source/README.md). `PdfCompressionDeviceTest` requires the five `scan_01pages.pdf`, `scan_03pages.pdf`, `scan_06pages.pdf`, `scan_10pages.pdf` and `scan_30pages.pdf` inputs in the debug app's external `files/bench` directory, normally `/sdcard/Android/data/com.jumlo.app.debug/files/bench`. The corpus recipe is `scripts/test-pdf-compression.sh --measure`; it writes inputs and separate host measurements beneath the JVM temporary directory's `jumlo-compression-measure` folder. Stage only the five scan inputs, preserve their identities, and record hashes for any newly generated corpus. The restored preflight gives a reproducible recipe to generate the five scan PDFs, record new input hashes per run, and stage Android fixtures. No fixed hashes or device results are bundled. Its 4–4.5 GiB guest planning allowance is an estimate, not a measured peak or a guarantee. Missing fixtures, SDK-filtered methods and opt-in screenshot skips must remain explicit in the results.
