# Jumlo production audit — 23 October 2026

## Evidence and baseline

This audit was written **before product source changes** in this execution. Source of truth: the uploaded `JUMLO_SOURCE_LATEST_RECOVERABLE_20261023(1).zip`, SHA-256 `7b29a9773dbedb2aa653ecce3a9e2932648e0835ca41fdc705546ae1dd1b334f` (5,264,402 bytes, 641 archive entries). The exact requested scope is in `PRODUCTION_BRIEF.md`. Recovered audits and test claims are retained under `docs/history/` and are **not evidence of current verification**.

**Initial verdict: NOT production qualified.** Substantial working implementations are present, but current-build/device evidence, complete tool interaction coverage, measured size and Play qualification are missing. No item below is called runtime-working solely from source review. Tests and statuses will be recorded in the new `TEST_REPORT.md`.

## Priority risks and confirmed defects

| Priority | Classification | Finding / action |
| --- | --- | --- |
| P0 | Missing verification | Reported link-paste crash has no supplied logcat; reproduce before claiming its root cause. Existing code already moves ordinary metadata I/O off Main and catches ordinary metadata/clipboard failures. |
| P0 | Missing for production | No process crash handler, structured crash diagnostic hook, or debug StrictMode in JumloApplication. Add privacy-safe diagnostics with opt-in external hook. |
| P0 | Broken failure path | Some AppViewModel launches can escape (notification preference writes, draft write in allowNewRun, outer intake errors). Add consistent coroutine boundary and visible localized errors while preserving cancellation. |
| P0 | Missing verification | Full release APK <=60,000,000 bytes, signed AAB, native compatibility and device matrix must be measured on this source. |
| P1 | Broken error visibility | Chosen-folder copy failures can be silently discarded; backend stream failures and extractor age restrictions lose actionable messages. |
| P1 | Broken privacy | Optional media-service HTTP logging includes client IP and full stream URL query. |
| P1 | Missing for production | Tool list is grouped and hides specialist tools behind text-only chips; multiple tools share icon aliases. |
| P1 | Missing for production | Compression size estimate has no <=15% calibration; DOCX headings and PDF permission toggles are missing. |
| P1 | Missing for production | Interactive watermark/signature transforms, some per-page editors, screenshot matrix and measured startup/frame benchmarks need further work. |
| P1 | Policy risk | Social extraction cannot by itself establish content authorization or platform permission. Document authorized-use operation and required distribution review; retain existing functionality. |
| P2 | Redundant / legacy | Persisted old haptic/motion/Wi-Fi/concurrency preferences remain for backwards compatibility; visible Settings should not expose the removed controls. |

## Build and release configuration

Six Gradle modules: app, benchmark, core-common, downloader-core, document-core, storage-core. Kotlin/Compose Android app, four reusable JVM core modules, Android Macrobenchmark module. compileSdk/targetSdk **36**, minSdk **26**, Java/Kotlin bytecode **17**. AGP **8.10.1**, Gradle wrapper **8.14** with SHA-256 pin; Kotlin **2.0.21**, KSP **2.0.21-1.0.28**.

- `debug`: unminified, .debug application suffix, arm64-v8a and x86_64. `release`: R8 full mode + resource shrinking, arm64-v8a only, en/ar resource configurations and AAB language/ABI splits.
- `compact`: release-equivalent debug-signed personal test build; it is **not a publishable release**. `benchmark`: release-like debug-signed profiling target.
- Release signing reads a gitignored properties file but has no environment variable route. Missing signing currently leaves an unsigned artifact, not a debug-signed release; strengthen an explicit publish gate.
- The existing `verifyCompactApkSize` checks compact only; add an actual release artifact gate and top-contributor analysis. Preserve full media runtime in the measured build.
- ProGuard rules are included below; actual optimized build and runtime compatibility remain unverified until execution.
- Available local toolchain was discovered (JDK17, SDK36, API26/API34 emulator images). No /dev/kvm or attached physical device has yet been established. The first sandbox build failed before configuration because Gradle could not open its local networking; an authorized build with local socket access is in progress.

### ProGuard/R8 rules

```proguard
# Jumlo — R8 / ProGuard Configuration
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,RuntimeVisibleAnnotations

# Keep Room Entities & DAOs
-keep class com.jumlo.app.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Kotlinx Serialization models, settings, and type-safe Navigation routes
-keepclassmembers class com.jumlo.app.model.** { *; }
-keepclassmembers class com.jumlo.app.data.processing.** { *; }
-keepclassmembers class com.jumlo.app.ui.navigation.** { *; }
-keep @kotlinx.serialization.Serializable class com.jumlo.app.** { *; }
-keepclassmembers class com.jumlo.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# PDFBox-Android (Apache-2.0)
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# zip4j (Apache-2.0)
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp & TLS
-dontwarn okhttp3.**
-dontwarn okio.**

```

## Direct dependency inventory

| Alias | Coordinate | Declared version |
| --- | --- | --- |
| androidx-core-ktx | androidx.core:core-ktx | 1.15.0 |
| androidx-appcompat | androidx.appcompat:appcompat | 1.7.0 |
| androidx-lifecycle-runtime-ktx | androidx.lifecycle:lifecycle-runtime-ktx | 2.8.7 |
| androidx-lifecycle-runtime-compose | androidx.lifecycle:lifecycle-runtime-compose | 2.8.7 |
| androidx-lifecycle-viewmodel-compose | androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 |
| androidx-activity-compose | androidx.activity:activity-compose | 1.9.3 |
| androidx-compose-bom | androidx.compose:compose-bom | 2024.11.00 |
| androidx-compose-ui | androidx.compose.ui:ui | Compose BOM |
| androidx-compose-ui-graphics | androidx.compose.ui:ui-graphics | Compose BOM |
| androidx-compose-ui-tooling | androidx.compose.ui:ui-tooling | Compose BOM |
| androidx-compose-ui-tooling-preview | androidx.compose.ui:ui-tooling-preview | Compose BOM |
| androidx-compose-ui-test-manifest | androidx.compose.ui:ui-test-manifest | Compose BOM |
| androidx-compose-ui-test-junit4 | androidx.compose.ui:ui-test-junit4 | Compose BOM |
| androidx-compose-material3 | androidx.compose.material3:material3 | Compose BOM |
| androidx-compose-material3-window-size | androidx.compose.material3:material3-window-size-class | Compose BOM |
| androidx-navigation-compose | androidx.navigation:navigation-compose | 2.8.4 |
| hilt-android | com.google.dagger:hilt-android | 2.54 |
| hilt-compiler | com.google.dagger:hilt-android-compiler | 2.54 |
| androidx-hilt-navigation-compose | androidx.hilt:hilt-navigation-compose | 1.2.0 |
| androidx-hilt-work | androidx.hilt:hilt-work | 1.2.0 |
| androidx-hilt-compiler | androidx.hilt:hilt-compiler | 1.2.0 |
| androidx-room-runtime | androidx.room:room-runtime | 2.7.2 |
| androidx-room-ktx | androidx.room:room-ktx | 2.7.2 |
| androidx-room-compiler | androidx.room:room-compiler | 2.7.2 |
| androidx-room-testing | androidx.room:room-testing | 2.7.2 |
| androidx-datastore-preferences | androidx.datastore:datastore-preferences | 1.1.1 |
| androidx-work-runtime-ktx | androidx.work:work-runtime-ktx | 2.10.0 |
| okhttp | com.squareup.okhttp3:okhttp | 4.12.0 |
| coil-compose | io.coil-kt:coil-compose | 2.7.0 |
| kotlinx-coroutines-android | org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.9.0 |
| kotlinx-coroutines-test | org.jetbrains.kotlinx:kotlinx-coroutines-test | 1.9.0 |
| kotlinx-serialization-json | org.jetbrains.kotlinx:kotlinx-serialization-json | 1.7.3 |
| androidx-exifinterface | androidx.exifinterface:exifinterface | 1.3.7 |
| androidx-documentfile | androidx.documentfile:documentfile | 1.0.1 |
| androidx-window | androidx.window:window | 1.3.0 |
| junit | junit:junit | 4.13.2 |
| pdfbox-android | com.tom-roush:pdfbox-android | 2.0.27.0 |
| zip4j | net.lingala.zip4j:zip4j | 2.11.5 |
| mlkit-text-recognition | com.google.android.gms:play-services-mlkit-text-recognition | 19.0.1 |
| androidx-core-splashscreen | androidx.core:core-splashscreen | 1.0.1 |
| androidx-junit | androidx.test.ext:junit | 1.2.1 |
| androidx-espresso-core | androidx.test.espresso:espresso-core | 3.6.1 |
| androidx-benchmark-macro-junit4 | androidx.benchmark:benchmark-macro-junit4 | 1.3.4 |
| androidx-profileinstaller | androidx.profileinstaller:profileinstaller | 1.4.1 |
| androidx-uiautomator | androidx.test.uiautomator:uiautomator | 2.3.0 |
| local media engine | io.github.junkfood02.youtubedl-android:library + ffmpeg | 0.18.1 |
| document scanner | com.google.android.gms:play-services-mlkit-document-scanner | 16.0.0 |
| Flow testing | app.cash.turbine:turbine | 1.2.0 |

No dependency is declared unused without resolving its call sites. Platform availability, security updates, licenses and native ABI compatibility are separate release gates. Do not blanket-upgrade this pinned graph without a tested compatibility reason.

## Permission inventory

- `android.permission.INTERNET`
- `android.permission.VIBRATE`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`

No broad storage, MANAGE_EXTERNAL_STORAGE, media-read or camera permission is requested by the base manifest. It uses SAF/Photo Picker and the Play services scanner. The full media overlay permits cleartext traffic; constrain and review this against the explicit local/direct-HTTP feature and HTTPS metadata backend. WorkManager foreground service declares `dataSync`. Exported MainActivity receives constrained shares and `jumlo://workspace` links. FileProvider is unexported and grants per-URI access.

## Screen source inventory

- `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/downloads/DownloadsScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/settings/StorageScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/tools/ToolsScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt`

## ViewModel inventory

- `app/src/main/java/com/jumlo/app/printing/PrintViewModel.kt`
- `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt`
- `app/src/main/java/com/jumlo/app/ui/downloads/MediaViewModel.kt`

## Settings data inventory

```kotlin

    val hasCompletedOnboarding: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val respectReducedMotion: Boolean = false,
    val hapticFeedbackOnCommit: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.LIGHT,
    val soundsEnabled: Boolean = true,
    val downloadDestinationFolder: String = "Download/Jumlo",
    val maxConcurrentDownloads: Int = 2,
    val defaultVideoQuality: String = "1080",
    val defaultAudioQuality: Int = 192,
    val fileViewMode: FileViewMode = FileViewMode.GRID,
    val accentColor: String = "BLUE",
    val fontScale: String = "NORMAL",
    val wifiOnlyDefault: Boolean = false,
    val notificationsGranted: Boolean = false,
    val quickActionIds: List<String> = listOf("pdf_02", "pdf_03", "img_02", "dl_media"),
    val recentToolIds: List<String> = emptyList(),
    val mostUsedToolIds: List<String> = emptyList(),
    /** Local only; UUIDs of outputs pinned by the user, not cloud identities. */
    val favoriteOutputIds: Set<String> = emptySet(),
    /** The initial ViewModel placeholder is never presented as a saved preference. */
    val isLoaded: Boolean = false,
    /** User-facing document names; verified output payloads remain immutable. */
    val outputNames: Map<String,String> = emptyMap(),
    /** The extraction service the downloader may use; empty means direct links only. */
    val mediaServiceUrl: String = "",
    /** The shared secret of the downloader service, when it asks for one. */
    val mediaServiceToken: String = ""
)

```


---

# Downloader and stability source audit

Audit date: 2026-10-23. Scope: the newly extracted Jumlo source tree. This is source evidence only. No existing report is treated as execution evidence; no device/emulator or logcat reproduction has been performed. The reported paste-link crash is **NOT VERIFIED**, and its runtime root cause remains unproven.

## Inventory

- UI: `DownloadsScreen`, `MediaWorkspace`, `MediaViewModel`; generic/direct download workspace is hosted by the shared workspace and `AppViewModel`. `AppViewModel` submits, pauses, cancels, retries and recovers durable jobs.
- `MediaViewModel`: URL, platform, analysis/generation, fetched asset, actual format choices, selected source, video/audio mode, output encoding/bitrate, filename, direct-file transformation, submission deduplication and error state. In-memory state survives Activity recreation through the retained ViewModel; process-death restoration of an unsubmitted URL/metadata is not implemented.
- Downloader core: `DownloadLinkPolicy`, `DriveLink`, `BrowserProfilePolicy`, `PlatformHeaderPolicy`, `StreamTransfer`, `ResumePolicy`, `DownloadRestartPolicy`, `DownloadCompressionPolicy`, `MediaSubmissionKey`; media models, catalog/choice policy, URL normalization, process runner, JSON guard, HTML5 discovery, native-library guards and submission slot.
- Runtime: interface `MediaRuntime`; `RoutingMediaRuntime`; packaged yt-dlp/Python/FFmpeg/QuickJS in the default extended build; optional `RemoteExtractorRuntime`; `NativeDirectMediaRuntime`; direct metadata, probe metadata/sample and cancellable HTTP adapter.
- Storage/jobs: `DownloadOperationHandler`, `MediaOperationHandler`, `JobStore`, `DownloadAdmission`, `SourceStore`, `EphemeralSecretStore`/`EncryptedDownloadSecrets`, `OutputStore`/`PublicOutputStore`; `EngineOperationWorker`, `ProcessingNotification`, `CancelJobReceiver`.
- Related settings: save destination, default video height/audio bitrate; optional extractor endpoint/token; website session headers/cookies. Wi-Fi, unmetered and cleartext fields remain in persisted schemas for compatibility; current worker code forcibly uses no network restriction and allows HTTP.
- Dependencies in this slice: WorkManager/Hilt worker integration, Room, kotlinx.coroutines, serialization, OkHttp/Okio, Android Keystore, DocumentFile/MediaStore, Coil thumbnail loading, youtubedl-android/FFmpeg/native resources, Android MediaExtractor. Python backend uses the standard library and external yt-dlp/FFmpeg.
- Test inventory: `StreamTransferResumeTest`, `DownloadRestartPolicyTest`, `MediaLinkRoutingTest`, `TransferAndNamingPolicyTest`, `RemoteExtractorRuntimeTest`, `MediaRoutingTest`, `MediaNativeLibrariesTest`, metadata/probe tests, download admission/secret persistence tests, instrumented packaged runtime and durable secret cleanup tests; host test scripts; Python network guard/bootstrap/backend tests; live qualification matrix template. Inventory does **not** mean these tests have been executed in this task.

## Findings

| Priority/status | Source evidence | Finding and required action |
|---|---|---|
| P0 missing for production | `app/src/main/java/com/jumlo/app/JumloApplication.kt:16–19` | No process-wide crash handler, debug StrictMode or flagged crash reporting hook. Existing `ErrorLog` safely stores codes but is only called by `AppViewModel` events; fatal paste failures could escape useful diagnostics. Parent owns application changes. |
| P0 missing evidence | `MediaViewModel.kt:223–274`; `MediaWorkspace.kt:65–70,120–125`; `AndroidManifest.xml:77–78`; `ProcessingNotification.kt:48–51` | Inspection is dispatched to IO, ordinary analysis exceptions are caught, clipboard reads catch RuntimeException, foreground `dataSync` type exists. These checks rule out simplistic source-only claims, but do not establish the crash cause. Capture reproducible paste steps + device logcat before calling the crash fixed. |
| P1 broken diagnostics | `AppViewModel.kt:787–790` | `allowNewRun` launches `async` and writes draft state without exception handling or a visible error state. Parent should protect this and audit other launch/async bodies and cleanup blocks. |
| P1 broken async handling | `AppViewModel.kt:375–422,270–278` | Share intake has an outer `try/finally` without catch, leaving setup, routing and final callback exceptions unhandled. Two record/job collectors silently consume non-cancellation exceptions. These are source-level error-state gaps; neither is a proven paste crash cause. |
| P1 broken privacy | `tools/media-service/service.py:237–238` with `/stream` at 252–263 | Default HTTP request logging formats client address and full request line; `/stream?url=...` therefore leaks pasted source URLs/query tokens and IP addresses. Replace with route/status/error-code-only events and test logging redaction. This optional backend must not be deployed as-is. |
| P1 broken error handling | `app/src/main/java/com/jumlo/app/data/media/RemoteExtractorRuntime.kt:212–226` | Stream transport ignores the service JSON failure body. Backend private/removed/rate/age failures become generic `HTTP_422` via `StreamTransfer`, losing actionable guidance. Parse bounded failure bodies into a fixed public error vocabulary before passing successful/redirect/range responses to transfer. |
| P1 missing error mapping | `app/src/media/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt:210–226`; `tools/media-service/service.py:37–76` | No explicit age-restriction category; age-related login errors become session/generic failures. The two engines duplicate divergent classifier logic. Add shared pure Kotlin classifier and matching backend cases, plus localized age guidance. |
| P1 broken destination reporting | `app/src/main/java/com/jumlo/app/data/output/OutputStore.kt:59–62` | Chosen-folder/MediaStore copy failures are silently converted to null; output/job is still recorded created. Preserve the verified private file, but surface an actionable destination warning/error and allow re-export. Parent/PDF storage owner should coordinate this shared path. |
| P1 incomplete publication check | `PublicOutputStore.kt:94–98` | The return count from clearing `MediaStore.IS_PENDING` is ignored. A vanished row can be reported saved although it is not visible. Require a successful commit/update and retain recovery behavior. |
| P2 broken filename conversion | `downloader-core/src/main/kotlin/com/jumlo/app/core/media/MediaPolicies.kt:242–249` | `displayName` strips only the target suffix. Converting `clip.mp4` to MP3 produces `clip.mp4.mp3`. Remove a recognized source media suffix before adding the selected output extension, retaining unknown dotted titles. Add Arabic/Unicode/path and conversion tests. |
| P2 inaccurate badge | `DownloadLinkPolicy.kt:67` | Snapchat is labeled Threads by a combined branch; split exact platform badges. Required LinkedIn and Drive host recognition already exists. |
| P2 missing user feedback | `MediaWorkspace.kt:120–125` | Unavailable/empty clipboard is silently ignored despite a localized `CLIPBOARD_UNAVAILABLE` message elsewhere. UI owner should surface it. |
| P2 missing process-death draft | `MediaViewModel.kt:66–77,137–160` | Retained ViewModel handles Activity recreation, but unsubmitted URL/current analysis state is not restored after process death. Preserve a safe draft handle (do not place sensitive full URLs in saved-state bundles), then re-fetch metadata before selection. |
| P2 missing release evidence | build/source runtime factories | Default runtime is packaged on-device; `-PextendedMedia=false` retains a backend/direct option. Native versus backend APK size impact has not been freshly measured. Do not change default to the reduced build to pass the size rule; measure both and record the full-feature decision. |
| P1 missing credential protection | `app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt:274–291` | Optional extractor endpoint validation checks only prefix/length; the service token is stored as ordinary DataStore text. Validate endpoint origin and secure the token with Android Keystore before enabling a remote production service. |
| P1 missing platform proof | existing platform routing and extractor | YouTube, TikTok, Instagram, Facebook, LinkedIn, Drive and direct routes exist, but no current matrix proves extraction or playback. An extractor's presence does not guarantee platform compatibility or terms compliance. Mark each live/device scenario NOT VERIFIED until executed. |

## Existing source safeguards to preserve

- Quality/audio controls render only inside the fetched `asset` branch (`MediaWorkspace.kt:285+`); direct options render only for a validated direct preview. Real extractor metadata feeds `MediaCatalog`, which excludes DRM/unusable sources, bounds format count, validates selector IDs, and avoids fabricated qualities.
- `MediaViewModel` cancels old inspections, checks generations, uses IO for probes, and serializes request identity. `guardMediaRuntime` converts LinkageError and OutOfMemoryError at runtime boundaries to safe failure codes.
- `StreamTransfer` binds a resumed partial to validators and final-URL digest, validates Content-Range/size, prevents HTTPS downgrade, bounds bytes/storage, syncs data and supports cancellable reads. A server ignoring Range triggers a restart rather than unsafe concatenation.
- WorkManager plus Room job leases, heartbeat, persisted pause/retry stages and recovery exist. Selected URLs are represented in Room by encrypted secret handles. Actual pause/resume/kill/background behavior remains NOT VERIFIED on Android.
- User-chosen folders use SAF; default Android 10+ output uses `MediaStore.Downloads` with pending publication, hash verification and duplicate-safe names. Older Android defaults to the app's external files area and can use SAF for an explicit folder.
- Source/stream URLs are hashed in transfer journals and opaque IDs are used in notifications. Do not regress these while adding diagnostics.

## Recommended bounded implementation

After root AUDIT.md is written: add regression cases and correct extractor error classification, backend response errors, media suffix handling, platform badge, and worker failure fencing. Backend log redaction is a necessary privacy fix if that path is assigned. Parent handles application-level crash capture/StrictMode/async guards and shared output reporting; UI owner adds localized guidance and clipboard feedback. Build and host tests provide only their stated evidence; the reported paste crash and platform/device matrix require a real Android runtime.


---

# Document and image engine audit — 2026-10-23

Scope: read-only review of `document-core`, PDF/image/Office/OCR processing, renderer and existing tests. This is source evidence, not a claim that an Android build or device test passed in the current workspace. No product code was edited during this audit.

## Existing implementation to preserve

| Area | Source evidence | Current conclusion |
| --- | --- | --- |
| Compression | `PdfImageCompressor.kt` walks shared images and Form XObjects, recompresses supported JPEG images through a codec seam, downsamples RGB/gray Flate scans, applies bitmap/encoded-byte budgets, supports cancellation, and preserves masks/color mappings it cannot safely convert. `PdfOperationHandler.kt:480–533` reopens candidates and preserves the original when no smaller valid candidate exists. | Real implementation, not a renamed/copy-only compressor. Android execution is NOT VERIFIED here. |
| Compression presets | `PdfPageGeometry.kt:80–156` defines BEST_QUALITY 240/90, BALANCED 180/75, SMALL_FILE 135/58 and STRONG 108/45, target-byte profiles and slider profiles. Low/Medium/High UI strings already exist. | Keep backwards-compatible saved preset IDs and compression semantics. |
| Office packages | `OfficePackageWriter.kt` streams one DOCX text page or PPTX JPEG/text layer at a time into a temporary ZIP; validation reopens parts, checks XML/internal relationships, limits expansion, rejects entities, supports cancellation and preserves earlier destinations on failure. | Existing robust foundation; avoid introducing a heavyweight Office dependency. |
| Office adapter | `PdfOfficeOperationHandler.kt` executes on IO, reads selected pages, falls back to unbundled OCR for empty Word pages, bounds raster sizes, releases each bitmap, checks free space/cancellation, validates before publication and rejects all-empty DOCX output. | Digital/scanned workflows exist; device and actual Microsoft Office qualification are NOT VERIFIED here. |
| PDF parsing/structural tools | `PdfBoxBackend.open`, merge and assembled documents use `MemoryUsageSetting.setupTempFileOnly()`. Merge, split, organize, crop, images-to-PDF, page images, forms, metadata, watermark, annotations and protection have production handlers. | Features should be repaired incrementally, not removed. |
| Rendering safety | `DocumentRenderPolicy` bounds dimensions and bitmap bytes. `PdfPageRasterizer` owns one page/bitmap at a time. `PdfRendererFactory` preflights SDK 26/27 inputs with cancellable strict PDFBox parsing and a scratch-space cap. | Existing legacy-framework mitigation is valuable; runtime qualification remains required. |
| Image processing | `BitmapTransforms` validates dimensions/format, handles EXIF orientation, refuses unsupported animation explicitly, budgets resident/output bitmaps, and supports real size-bound encoding. Image join inspects first and retains one source beside the output. | Large images may be refused with a memory-budget error rather than silently resized. Device memory behavior NOT VERIFIED. |
| OCR | `MlKitOcrEngine` uses an unbundled Latin recognizer, waits for actual module completion, and waits for native recognition before releasing its bitmap on cancellation. | Latin OCR only. Scanned Arabic is unsupported by this selected recognizer; embedded Arabic text can still be extracted. |
| Signature source | `ScanScreen.kt:98–118` creates an ARGB_8888 bitmap and writes PNG without a white background. | Alpha-intended implementation exists, but no signature-export pixel assertion was found. |

## Concrete gaps and risks

1. **Missing for production — compression estimate accuracy.** `PdfCompressionPolicy.estimateBytes` assumes all source images are 300 dpi and scales compressed bytes by dpi² and a linear quality factor. Tests assert only bounds/ordering, not error against real output. The requested three-sample ≤15% accuracy has no current evidence. A measured preview using the same candidate path is preferable to claiming this formula is accurate. Any preview must expose loading/cancellation and avoid doing full compression on the main thread.

2. **Missing for production — Word headings.** `PdfPageText.extract` produces plain strings, discarding `TextPosition` font sizes/weights; `OfficePackageWriter.writeDocx` emits only Normal paragraphs. Paragraphs, page boundaries, Unicode and first-strong RTL are supported, but original heading semantics are not preserved. A bounded structured-text adapter plus Word heading styles can repair this without rewriting the package engine.

3. **Missing for production — permission toggles.** `PdfBoxBackend.protect` hardcodes modify=false and print=true. `PdfOperationHandler` reads only password/removal settings. No permission choices can reach the PDF encryption dictionary. Existing AES-128 owner/user separation and secret-store behavior should remain.

4. **Missing verification — signature alpha.** `ImagePixelTest` tests alpha-aware image comparison and geometry; it does not export a signature and inspect transparent/ink pixels. `SignatureExportGeometryTest` checks geometry only. A device regression must use the exact production signature renderer to establish alpha, not a test-only bitmap.

5. **Missing verification — large/corrupt/encrypted matrix.** Tests exist for Office encrypted/corrupt refusal, sampled raster limits, image budgets, and a 200-page compressor. They have not been executed in this workspace. The 200-page test relies on generated scan fixtures; missing fixtures must stay failures, not skips or synthetic successes.

6. **Missing for production — current evidence.** Supplied `TEST_REPORT.md` cites historical host, emulator and LibreOffice outcomes plus files under `reports/`, but this extracted tree has no `reports/` directory and no generated Office/PDF samples or APK/AAB artifacts were found. Treat those statements as inherited narrative, not reproducible current evidence. Record new command logs and artifact identities separately.

7. **Known scope limit — Arabic OCR and layout reconstruction.** The selected ML Kit Latin recognizer does not establish scanned Arabic conversion. DOCX does not reconstruct complex tables/columns; PPTX uses page images and a transparent text layer. These limits are already stated in existing documentation and must remain visible in user-facing conversion copy.

8. **Remaining resource risk — scratch storage.** Ordinary PDFBox operations are temp-file backed, but most `PdfBoxBackend.open` calls have no scratch-cap argument; only the SDK 26/27 renderer preflight explicitly caps scratch size. Existing free-space checks are not universal per parser read. Maliciously complex PDFs and low-disk conditions still require device qualification.

9. **Potential error-message gap — general corrupt PDF inputs.** `PdfPageText.open` explicitly maps IO failures to `PDF_INVALID`, while structural `PdfOperationHandler.openUnencrypted` catches only password failures. The shared error mapper should be checked so a corrupt merge/crop/protect input does not fall through to a generic processing error.

## Test inventory and useful current runs

- `scripts/test-office-packages.sh`: 11 host checks covering paragraphs/Unicode/RTL, ZIP parts, cancellation including late-validation cancellation, image bytes, streaming order and invalid output refusal. Requires Java and Kotlin compiler jars. Independent parser qualification requires generated files plus `python-docx`/`python-pptx`.
- `scripts/test-pdf-compression.sh`: real PDF/JPEG/Flate host regressions through the production compressor and a host codec; `--measure` generates a deterministic scan corpus. Requires Java/Kotlin/PDFBox dependencies. Do not run parallel Gradle/download work while coordinator owns toolchain setup.
- PDF JVM tests cover compression policy/budgets, structure, forms, metadata, overlays, ink and security. Android tests cover Office handlers, parser recovery, image reliability/pixels, native OCR cancellation, and corpus/200-page compression.
- Source-only Python checks such as `scripts/test-large-pdf-wiring.py` can detect changed wiring but cannot establish compression quality, valid Android output, or a working device feature.

Current execution state: audit only. Android build, host Kotlin suites, independent generated-Office parsing, emulator and physical-device tests are **NOT VERIFIED in this session** until fresh commands succeed and logs are retained.


---

# UI audit fragment — 2026-10-23

Read-only source audit against the supplied brief. No device/runtime assertions: every runtime behavior below is NOT VERIFIED until built and exercised. Product files were not modified for this fragment.

## Main findings

- **Broken requirement: tool discovery.** ToolsScreen uses a LazyColumn of workspace/category headings, filter chips, and hidden More tools text-only chips. It ignores persisted mostUsedToolIds despite DataStore usage recording in AppViewModel.chooseTool.
- **Broken requirement: icon system.** 47 registry entries currently share ~20 aliased ic_prism_* glyphs. Zero ic_tool_* files. Need unique official Material Symbols Rounded filled 24dp assets and complete attribution.
- **Missing for production: workspace consistency.** Root bar supplies title/back but no tool icon; Advanced settings are a modal with many numeric fields duplicating richer controls; several empty input states lack their own CTA.
- **Missing for production: requested studios.** PDF watermark has drag only and lacks selection frame/handles/pinch/rotate/zoom; image watermark is fixed-position; signature draw pad lacks styling/history/library and signature placement lacks rotation/multiple objects. Preserve real renderers while extending.
- **Missing for production: image and document interactions.** Merge lacks total pages/drag; adjustment is toggle not split view; forms are text inputs below preview rather than on-page; OCR lacks inline result/copy/export; batch names lack pattern chips and thumbnails.
- **Existing source meets design direction:** pure-black neutral dark surfaces; dynamic color off by default; current Settings already removes forbidden user controls; 5-slot contained dock; Home hero four required actions and recent files; Files list/grid, sorting/filtering/grouping/trash/storage/save folder.
- **Missing for production: usage sync on Home.** Four fixed primary quick actions exist, but no section bound to persisted most-used tools.
- **Runtime qualification missing:** no valid current device/screenshot/performance evidence. Existing ScreenConfigSemanticsTest captures only principal screens, not all tools/loaded states. No Roborazzi/Paparazzi dependency discovered.

## Screens, routes and view models

| Screen / entry | Route type / arguments | Source status / remaining concern |
|---|---|---|
| HomeScreen | HomeRoute | Real hero/recent/jobs/draft; missing most-used synchronization |
| ToolsScreen | ToolsRoute | Functional dispatch; grouped layout violates brief |
| FilesScreen | FilesRoute | Real database/provider UI; lifecycle and empty/loaded states unverified |
| DownloadsScreen | DownloadsRoute | Job manager; downloader owner audits |
| SettingsScreen | SettingsRoute; SettingsDetailRoute | Tab plus origin-returning details; requested visible list mostly present |
| StorageScreen | StorageRoute | Real size values supplied by VM; cleanup must be device proven |
| WorkspaceScreen | ToolRoute(toolId); WorkspaceRoute legacy deep link | Tool-dependent real controls; extensive gaps in table below |
| MediaWorkspace | ToolRoute(dl_media) / workspace shell | Metadata-driven dedicated workspace; downloader owner audits |
| PdfViewerScreen / ViewerWorkspace | ViewerRoute(uri,title); ToolRoute(pdf_01) | Real renderer/zoom/pages |
| ScanScreen / SmartDocumentScanLauncher | ScanRoute | Launches document scanner directly |
| ActivityScreen | JobRoute(jobId); ActivityRoute | Progress/results/recovery |
| PrintScreen / Print page manager | PrintRoute | Existing specialist route remains outside five-tab dock |
| OnboardingGuide, AccountHubModal | Modal overlays, no route arguments | Existing entry points; requested behavior unverified |
| Settings choice, storage, privacy/about dialogs; Files rename/delete/new-folder dialogs; preview and unsaved-draft dialogs | Modal state | Source present; touch/font/RTL test gaps |

ViewModels: AppViewModel (Android state and operations); MediaViewModel (metadata/download session); PrintViewModel (print queue/state); PdfPageEditorSession (history); VisualDocumentEditorSession (history). No claim of unused/dead class without proving reachability. AccentPickerDialog and HomeActionEditor appear retained but are not reachable from current Settings/Home; root audit can classify after reference search.

Typed Navigation Compose route objects exist for every listed route and ToolRoute/ViewerRoute/JobRoute use @Serializable arguments. switchTab uses saveState/restoreState/launchSingleTop; PredictiveBackHandler tracks tab history. Existing instrumentation checks back/tab state but has not been executed here. No shared-element transition API is present.

## Settings inventory

| Visible setting | Source status |
|---|---|
| Theme: system/light/dark | Present, DataStore |
| Dynamic color | Android 12+ optional; default BLUE/off |
| Language: English/Arabic | Present; theme sets layout direction and font |
| Save folder + open/reset | Present; URI persistence backend requires device check |
| Default video quality BEST/1080/720/480 | Present preference; actual picker must still derive metadata |
| Default audio bitrate 320/192/128 | Present preference |
| Storage usage | Real VM flow; refresh on entry |
| Clear cache | Confirmation + VM cleanTemporary; actual deletion NOT VERIFIED |
| About + version + privacy + diagnostics + guide replay | Present |
| Reduce motion, haptics/intensity, Wi-Fi-only, HTTP policy, concurrency count | Not visible in Settings; old model/DataStore fields retained for compatibility and require policy migration review |

## Complete tool inventory

| Tool ID | Registry label | Legacy metadata route | Current icon name | Audit classification / UI status |
|---|---|---|---|---|
| `pdf_to_docx` | PDF to Word | `tools/pdf/word` | `description` | missing for production: Real PDF input/page picker + Word limits note; no inline editable conversion preview. Arabic/OCR engine qualification delegated. |
| `pdf_to_pptx` | PDF to PowerPoint | `tools/pdf/powerpoint` | `slideshow` | missing for production: Real PDF input/page picker + slides limits note; no rendered slide result preview before publishing. |
| `pdf_02` | Images to PDF | `images_to_pdf` | `picture_as_pdf` | missing for production: Real paper preview, source thumbnails and page-size/sliders; order has arrows only, page sizes are chips rather than visual cards. |
| `pdf_03` | Merge PDF | `merge_pdf` | `file_copy` | missing for production: Real first-page thumbnail strip and accessible order arrows; drag reorder and live combined page count missing. |
| `pdf_05` | Split PDF | `split_pdf` | `content_cut` | missing for production: Real page grid, range/boundary/every-N modes and live output count; drag range marking missing. |
| `pdf_04` | Organize Pages | `organize_pages` | `dashboard` | missing for production: Real grid, drag reorder, rotate, delete, undo/redo source implemented; runtime evidence absent. |
| `pdf_06` | Extract Pages | `extract_pages` | `file_open` | missing for production: Real page grid + exact range entry; full output runtime evidence absent. |
| `pdf_07` | PDF to Images | `pdf_to_images` | `photo_library` | missing for production: Real page selection and grayscale preview; output format/quality controls source implemented. |
| `pdf_01` | View PDF | `pdf_viewer` | `visibility` | missing for production: PDF reader has real render, page count, thumbnails, zoom and scroll. Runtime evidence absent. |
| `pdf_compress` | Compress PDF | `pdf_compress` | `compress` | missing for production: Real source preview, 4 preset chips, live analytical estimate; requested 3 large Low/Medium/High cards missing; estimate accuracy not measured. |
| `pdf_crop` | Crop PDF Pages | `pdf_crop` | `crop` | missing for production: Real handles and page selection; per-range rectangle exists; per-page different crop state absent. |
| `pdf_annotate` | Annotate PDF | `pdf_annotate` | `edit` | missing for production: Real region/ink editor with history; tool selector text chips, fixed red annotation color/width and no bottom icon toolbar. |
| `pdf_stamp` | Watermark & Page Numbers | `pdf_stamp` | `approval` | missing for production: Real PDF watermark preview with drag; no selection frame, pinch scale/rotation, zoom, font choice or image mode. |
| `pdf_signature` | Sign PDF | `pdf_signature` | `draw` | missing for production: Transparent PNG pad and real page placement frame; pad lacks thickness/color/undo/redo/library; placement lacks object rotation and multiple signatures. |
| `pdf_image` | Add Image to PDF | `pdf_image` | `add_photo_alternate` | missing for production: Real page overlay placement with handles; no multi-object placement/rotation. |
| `pdf_protect` | Password Protect PDF | `pdf_protect` | `lock` | missing for production: Real input preview + add/remove password fields; no show/hide, strength meter or permission controls. |
| `pdf_metadata` | PDF Metadata | `pdf_metadata` | `info` | missing for production: Real preview + title/author/subject/keywords form; vertically stacked rather than beside page on wide layouts. |
| `pdf_clean` | Clean Metadata | `pdf_clean` | `shield_lock` | missing for production: Real preview + remove-title option; limits note; no output preview distinction. |
| `pdf_forms` | Fill PDF Form | `pdf_forms` | `assignment` | missing for production: Discovers real AcroForm text/checkbox/radio fields; fields listed below page, not clickable overlays; raw JSON visible in advanced. |
| `pdf_ocr` | Extract Text | `tools/pdf/ocr` | `text_fields` | missing for production: Real page selector + conversion limits; no side-by-side recognized text/copy/export workspace. |
| `scan_doc` | Smart Scan | `tools/scan` | `document_scanner` | missing for production: Direct ML Kit multi-page launch path; scanner fallback pending owner audit. |
| `img_01` | Convert Images | `convert_images` | `photo_library` | missing for production: Real input preview + format/quality controls; no compressed output preview. |
| `img_02` | Resize Images | `resize_images` | `aspect_ratio` | missing for production: Real rendered preview with sampled transforms + sliders; alternate advanced numeric inputs remain. |
| `img_03` | Crop, Rotate & Flip | `crop_rotate_flip` | `crop` | missing for production: Real crop/rotate/flip with handles + history; alternate advanced numeric inputs remain. |
| `img_04` | Compress Images | `compress_images` | `compress` | missing for production: Real source preview and quality/format controls; no encode/decode live output preview. |
| `img_exif` | EXIF Inspector | `img_exif` | `info` | missing for production: Real image preview + remove-all/location toggles; no metadata inspection result shown inline. |
| `img_watermark` | Watermark Images | `img_watermark` | `approval` | missing for production: Real sampled transformed preview, text/logo and opacity; fixed lower corner and size; no direct-manipulation object studio. |
| `img_join` | Join / Collage | `img_join` | `grid_view` | missing for production: Real collage preview + layout chips/gap; source order arrows; visual layout cards/drag placement missing. |
| `img_adjust` | Adjust & Pad | `img_adjust` | `tune` | missing for production: Real sampled adjusted preview + sliders; before/after toggle exists, split-view divider missing. |
| `img_target` | Target Size | `img_target` | `compress` | missing for production: Byte-target log slider/chips and real source preview; no target output preview. |
| `img_compare` | Compare Images | `img_compare` | `compare` | missing for production: Backend tool exists but deliberately hidden in ToolCatalogue; no visible tile. |
| `img_stitch` | Stitch Screenshots | `img_stitch` | `view_agenda` | missing for production: Real stitched preview, gap/overlap sliders; source ordering arrows. |
| `dl_media` | Video, Audio & File Downloads | `media_download` | `download` | missing for production: Dedicated MediaWorkspace; metadata and job backend audited by separate agent. |
| `dl_02` | New Direct Download | `add_single_url` | `add_link` | missing for production: URL field, paste guard, validation and queue; top-level tile currently folded into text chip. |
| `dl_03` | Batch URL Intake | `batch_url_intake` | `download` | missing for production: Multi-URL field + valid/duplicate/invalid totals; folded text chip. |
| `dl_settings` | Download Settings | `settings_overview` | `tune` | missing for production: Navigation alias to Settings, not a separate processing workspace. |
| `file_02` | Browse Storage | `folder_explorer` | `folder_open` | missing for production: Navigation alias to Files; hidden from tools. |
| `file_07` | Create ZIP Archive | `create_zip` | `folder_zip` | missing for production: ZIP level presets/slider and ordered source list; nonvisual files represented by icon/name. |
| `file_08` | Extract ZIP Archive | `extract_zip` | `unarchive` | missing for production: Archive input + extraction note; hidden under More tools text chip. |
| `file_encrypted_zip` | Encrypted ZIP | `encrypted_zip` | `lock` | missing for production: Encrypt/extract toggle/password; password masking lacks reveal control. |
| `file_batch_rename` | Batch Rename (copies) | `batch_rename` | `drive_file_rename_outline` | missing for production: Live normalized names computed off-main; list is text-only; pattern chips missing. |
| `file_duplicates` | Duplicate Finder | `duplicates` | `copy_all` | missing for production: Source list and backend report; no visual duplicate results grid. |
| `file_trash` | Managed Trash Info | `trash` | `delete` | missing for production: Navigation alias to Files managed trash; hidden from catalogue. |
| `file_drive` | Google Drive Import / Export | `google_drive` | `cloud` | enabled: real Drive v3 workflow, user token-gated; device/account qualification outstanding. |
| `sha256` | SHA-256 verification | `sha256` | `verified` | missing for production: Expected-hash text field and source list; backend digest report. |

## Principal workspace composables

WorkspaceScreen dispatches InputPreview, ViewerWorkspace, PdfPageEditor, VisualDocumentEditor, VisualWatermarkEditor/PdfWatermarkPreview, PdfFormEditor, SourceOrderStrip, ImageResultPreview, VisualImageSettings, ImagePdfPreview, CompressionEstimate, ProcessingPresetsPanel, PdfInsertionControls, SignaturePad, ContentToolSettings/TargetImageSettings, DocumentPageControls and common slider/toggle/counter controls. Previews are actual imported inputs, sampled to bounded dimensions; preserve this pipeline.

## Testing inventory and missing tests

Existing Compose/device tests: JumloUiTest, FiveTabNavigationTest, ScreenConfigSemanticsTest, PdfViewerInteractionTest, WorkspaceRecoveryInteractionTest, HomeActionEditorInteractionTest, OnboardingGuideInteractionTest, ProductionRowInteractionTest, ProviderFilesInteractionTest, AccountHubInteractionTest. Existing screenshot recorder is source code only until executed. Needed: flat-grid all-tools and usage ordering tests; unique glyph/resource mapping test; signature transparency/style/undo test; watermark gesture state test; per-tool empty/loaded four theme/direction screenshot sweep; 200% text on all requested controls; device memory/latency/performance evidence.

No product mutation was performed before parent AUDIT.md confirmation.


## Official policy references (reviewed 23 October 2026)

- Target API: https://support.google.com/googleplay/android-developer/answer/11926878 — current new-app/update requirement is API 36; this source already targets 36.
- Native page-size compatibility: https://developer.android.com/guide/practices/page-sizes — inspect every bundled native dependency and nested executable payload; manifest targeting alone proves nothing.
- Intellectual property and unauthorized downloads: https://support.google.com/googleplay/android-developer/answer/9888072 — preserve authorized-use functionality, avoid unauthorized-content promotion, and require rights/platform review before release.
- User data/permissions and foreground services will be documented in PRIVACY_AND_PERMISSIONS.md with exact references.

## Audit completion boundary

This is a complete inventory-level audit of the supplied app and focused source review of the requested risk areas, not a claim that every line or every external platform was tested. Agent fragments retain source-based findings and explicit uncertainty. Implementation follows this written audit. Final report must distinguish PASS, FAIL, BLOCKED and NOT VERIFIED; no inherited runtime numbers are to be represented as newly measured.

## Remediation register — after implementation

The inventory above records the supplied archive before edits. Its descriptions
of missing controls, shared transitions, icon reuse and analytical compression
must not be mistaken for the final source. The current per-tool specification is
`TOOL_UI_SPEC.md`; the current verification authority is `TEST_REPORT.md` and its
linked raw evidence. An implemented fix is not automatically a device PASS.

| Original risk / gap | Current source outcome | Acceptance boundary |
| --- | --- | --- |
| Unhandled recoverable ViewModel work / missing bounded diagnostics | Guarded operation scopes and sanitized bounded events; fatal handling still delegates to Android; optional remote reporting hook defaults off; debug StrictMode enabled | Original pasted-link crash was not reproduced with the two baseline inputs. No causal-fix claim. |
| Storage failures could obscure private/public output distinction | Persist validated private output first; public-save error and same-output retry; cancellation-aware URI and input-stream ownership; owner-fenced expired-lease recovery | Host cancellation/SQL evidence covers specific boundaries. Provider/process-kill atomicity needs device qualification. |
| Extractor error/naming/token weaknesses | Shared error taxonomy, bounded backend errors, filename extension policy, Drive resource keys, origin-bound encrypted credentials and atomic endpoint/token reads | Live provider/account/restriction matrix and Android Keystore behavior need their own results. |
| Compression estimate did not measure actual export | Shared bounded compressor produces estimate and output; three real host-codec samples agree | Android codecs, device memory and 300 ms/loading behavior remain separately qualified. |
| DOCX structure/blank output and PPTX interoperability | Paragraph/headings/RTL output, blank rejection, rendered-page slide images plus text, independent package parsing | Scanned Arabic OCR and exact complex-layout reconstruction remain unsupported; actual Office apps not qualified by parser checks. |
| Missing tool visibility and unique icons | All 44 enabled tools in flat usage-ranked grid; 47 unique official tool vectors, nine annotation vectors; synchronized frequent tools | Three inherited disabled capabilities remain blocked, documented and inventoried. |
| Missing studio controls and preview fidelity | Watermark/signature transforms, reusable signatures, transparent pad, form overlays, crop/page controls, comparison divider, encoded image samples, duplicate cards and rename previews added | No blanket gesture/export/accessibility PASS; consult individual emulator/host results and remaining UI limits. |
| Scanner fallback absent | Direct ML Kit multipage launch retained; CameraX/gallery fallback added | Play-services availability, physical capture, permission denial and recreation require device tests. |
| Navigation/state/transition gaps | Typed origin arguments, outgoing transition identity, separate Home/Tools namespaces and tab return behavior | Predictive Back, deep links, recreation and tab restoration are scoped to actual test results. |
| Signing and packaging ambiguity | Owner-only release signing configuration; unsigned release without credentials; strict complete-runtime ARM64 APK gate; en/ar AAB splits | Owner key/certificate, final signed size, actual ARM64/16 KB execution and Play review remain gates. |
| Test/documentation claims inherited from recovered archive | Historical reports isolated; current artifact identities, per-test evidence, explicit blocked matrices and source manifest added | Host, compilation, emulator and physical-device evidence are kept distinct. |

Correctness-related lint triage retains the full media bootstrap resource and
official vector geometry. Count-neutral localized labels and the input-stream
handoff fix address concrete findings. Bouncy Castle EST trust-manager reachability
must be checked against final R8 output; an unused source path alone is not proof
that a release APK excludes it. Generic HTTP downloads remain an intentional,
documented cleartext compatibility path with no HTTP preference or separate
consent dialog. No broad lint suppression was introduced to manufacture a pass.

---

# Current source audit — executed 2026-10-24

This table supersedes the inventory above for the **current** source tree. It was
produced by reading the source in this session. The sandbox has **no JDK, no
Gradle distribution, no Android SDK, no emulator/KVM and no physical device**, and
the Gradle/Maven/Android hosts are network-blocked, so `:app:assembleDebug`,
`:app:testDebugUnitTest` and `:app:lintRelease` could not be run. "SOURCE" means
the handler is implemented and is **not** a device PASS. "HOST-TESTED" means an
executed host/JVM suite covers the real backend path (see TEST_REPORT.md).
Verification status comes from `core-common/.../CapabilityPolicy.kt`.

| Tool ID | Tool | Category | Source status | Verified by | Usage value | Decision |
|---|---|---|---|---|---|---|
| `scan_doc` | Smart Scan | Scan & Capture | SOURCE (ML Kit + CameraX fallback) | wiring contract | high | KEEP-AND-IMPROVE |
| `pdf_01` | View PDF | Organize PDF | SOURCE | wiring contract | high | KEEP-AND-IMPROVE |
| `pdf_03` | Merge PDF | Organize PDF | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_04` | Organize Pages | Organize PDF | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_05` | Split PDF | Organize PDF | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_06` | Extract Pages | Organize PDF | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `pdf_crop` | Crop PDF Pages | Organize PDF | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `pdf_to_docx` | PDF to Word | Convert | SOURCE | HOST-TESTED (OOXML) | high | KEEP-AND-IMPROVE |
| `pdf_to_pptx` | PDF to PowerPoint | Convert | SOURCE | HOST-TESTED (OOXML) | medium | KEEP-AND-IMPROVE |
| `pdf_02` | Images to PDF | Convert | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_07` | PDF to Images | Convert | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_ocr` | Extract Text (OCR) | Convert | SOURCE (Latin only) | wiring contract | high | KEEP-AND-IMPROVE |
| `img_01` | Convert Images | Convert | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `pdf_compress` | Compress PDF | Optimize | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `img_04` | Compress Images | Optimize | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `img_02` | Resize Images | Optimize | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `img_03` | Crop, Rotate & Flip | Optimize | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `img_adjust` | Adjust & Pad | Optimize | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `img_target` | Target Size | Optimize | SOURCE | HOST-TESTED | medium | MERGE candidate → `img_04` |
| `img_join` | Join / Collage | Optimize | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `img_watermark` | Watermark Images | Optimize | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `img_exif` | EXIF Inspector | Optimize | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `img_stitch` | Stitch Screenshots | Optimize | SOURCE | HOST-TESTED | low | REMOVE candidate (documented, not executed) |
| `img_compare` | Compare Images | Optimize | SOURCE | HOST-TESTED | low | REMOVE candidate (documented, not executed) |
| `pdf_annotate` | Annotate PDF | Edit & Sign | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_signature` | Sign PDF | Edit & Sign | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_stamp` | Watermark & Page Numbers | Edit & Sign | SOURCE | HOST-TESTED | high | KEEP-AND-IMPROVE |
| `pdf_image` | Add Image to PDF | Edit & Sign | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `pdf_forms` | Fill PDF Form | Edit & Sign | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `pdf_metadata` | PDF Metadata | Edit & Sign | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `pdf_clean` | Clean Metadata | Edit & Sign | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `pdf_protect` | Password Protect PDF | Protect | SOURCE | HOST-TESTED (AES-128) | high | KEEP-AND-IMPROVE |
| `dl_media` | Video, Audio & File Downloads | Download | SOURCE | wiring contract | high | KEEP-AND-IMPROVE |
| `dl_02` | New Direct Download | Download | SOURCE | HOST-TESTED | medium | MERGE candidate → `dl_media` |
| `dl_03` | Batch URL Intake | Download | SOURCE | HOST-TESTED | medium | MERGE candidate → `dl_media` |
| `dl_settings` | Download Settings | Download | Navigation alias → Settings | wiring contract | low | REMOVE candidate (alias) |
| `file_02` | Browse Storage | Files | Navigation alias → Files | wiring contract | low | REMOVE candidate (alias) |
| `file_07` | Create ZIP Archive | Files | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `file_08` | Extract ZIP Archive | Files | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `file_encrypted_zip` | Encrypted ZIP | Files | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `file_batch_rename` | Batch Rename | Files | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `file_duplicates` | Duplicate Finder | Files | SOURCE | HOST-TESTED | medium | KEEP-AND-IMPROVE |
| `file_trash` | Managed Trash Info | Files | Navigation alias → Files | wiring contract | low | REMOVE candidate (alias) |
| `sha256` | SHA-256 verification | Files | SOURCE | HOST-TESTED | low | REMOVE candidate (documented, not executed) |
| `file_drive` | Google Drive Import/Export | Files | ENABLED | token-gated Drive v3 | medium | ENABLED (real impl; Drive direct links remain in downloader) |

**Removal/merge disposition (Phase 1).** Removing a tool is not a one-line edit:
each requires deleting its route, its `CapabilityRegistry` entry, its strings
(EN+AR), its icon asset and any dispatch wiring. That work cannot be
build-verified in this sandbox, so destructive removals were **not** executed
here. The concrete disposition recorded above is the input for a build-verified
pass: merge `img_target` into `img_04`; fold `dl_02`/`dl_03` into the single
`dl_media` input; drop the `file_02`, `file_trash` and `dl_settings` navigation
aliases; and remove the low-value `img_stitch`, `img_compare` and `sha256`. The
three blocked tools stay disabled until their security/privacy gates clear. No
route, string or asset was left dead by this session.

**Catalogue reorganisation (executed).** The Tools screen now renders the
brief's sections — Scan & Capture, Organize PDF, Convert, Edit & Sign, Optimize,
Protect, Download, Files — each a full-width header over its tiles, with Pinned
and Recent rows at the top fed by `settings.quickActionIds` /
`settings.recentToolIds`, and one search field. Search still collapses to a flat
keyed list. Ungrouped tools fall into a trailing "All tools" group so a new
tool is never hidden.
