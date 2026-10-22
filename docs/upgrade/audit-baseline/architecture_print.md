> Historical Phase 0 audit of the supplied source, before the authorized upgrade. Findings and proposed reproductions below describe that baseline. See ../UPGRADE_REPORT.md for current changes and fresh verification.

# Phase 0 architecture and Android printing audit

Scope: read-only inspection of the exact extracted source under `audit-source/Jumlo-main`. No source/configuration/test changes; no Gradle run. Conclusions below are static-confirmed unless specifically marked runtime-unverified. Repository documentation was not accepted as implementation evidence.

## Findings

### AP-01 — HIGH: Android printer submission is entirely unimplemented

Classification: missing requested capability, rather than a broken implementation of an existing adapter. Severity assumes direct printing is part of the intended product scope.

Evidence: `app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt:253–265` defines only external open (`ACTION_VIEW`), share (`ACTION_SEND`), and save/export (`ACTION_CREATE_DOCUMENT`) handoffs. Files/Activity output wiring at lines 314–325 and 358 has no print callback. `app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt:35–43` lists every processing handler and has no print handler. `app/src/main/java/com/jumlo/app/model/OperationContracts.kt:10–25` contains processing jobs and output records, without any Android PrintJob identity/state. `app/src/main/java/android/print/PrintAdapterHelper.java:6–53` only constructs callback objects and has no callers anywhere in shipped sources.

An exhaustive search across `app/src/main`, `app/src/base`, `app/src/media`, all three test source sets, `scripts`, and `benchmark` finds no PrintManager, PrintJob, PrintAttributes, createPrintDocumentAdapter, printer discovery, or print submission call. PrintDocumentAdapter appears only in the unused helper declaration. The `PdfRenderer.Page.RENDER_MODE_FOR_PRINT` calls at `data/processing/PdfOperationHandler.kt:567` and `DownloadOperationHandler.kt:185` rasterize PDF pages; they do not submit print jobs.

Plausible reproduction: generate an Images-to-PDF result, open its result actions in Activity or Files, and attempt to print directly. There is no Print action or system print dialog. Opening the PDF in a separate capable viewer may let that external app print; that would not be a Jumlo print job.

Expected: a clearly available Print action invoking Android PrintManager with a valid PrintDocumentAdapter and meaningful failure/cancellation handling, if direct printing is an agreed requirement. Actual: output generation and external file handoffs only. Root cause: the app has no integration boundary between its output records and the Android print framework. Confidence: static-confirmed absence; no printer/device run performed.

Consequences grouped under this one finding, not counted as separate bugs: no in-app route to printer selection/discovery; no adapter layout/write/page-range/cancellation behavior to audit; no selected-printer media/color/duplex/copies integration; no app-created printer-job status/cancel/restart surface; no print history, favorite/recent printers or reusable print presets; no print-specific tests. Printed output parity cannot be certified because there is no emitted print document.

### AP-02 — MEDIUM: notification taps lose the identity of the operation

Evidence: `app/src/main/java/com/jumlo/app/worker/ProcessingNotification.kt:20–31` receives a job ID and includes it in the cancel PendingIntent (lines 26–29), but the content PendingIntent (line 30) uses a plain MainActivity intent with no job ID, route, URI, or action. All jobs use requestCode 0 and the same activity intent. `MainActivity.kt:45,80–82` dispatches only incoming SEND/SEND_MULTIPLE to the ViewModel. `ui/navigation/JumloNavHost.kt:109,151–152,358` maintains selectedJob purely as navigation composition state, with no notification-intent consumer.

Reproduction: start a long job, return to Home/background the app, tap that job's ongoing notification. It opens the generic activity/current screen rather than the particular job's Activity view; with multiple jobs, different notices share the same generic destination. Expected: tap opens the operation represented by the notification. Root cause: job context is omitted from the open PendingIntent and never consumed by the navigation layer. Confidence: static-confirmed missing routing; exact activity/task-stack presentation runtime-unverified.

Existing test `app/src/androidTest/java/com/jumlo/app/worker/ProcessingNotificationTest.kt:15–23` verifies privacy, ongoing flag, cancel action presence, and foreground type; it does not exercise content-intent routing.

### AP-03 — MEDIUM: shared file intake neither selects a compatible tool nor opens its workspace

Evidence: `MainActivity.kt:45,80–82` passes file shares to `AppViewModel.intake`. In `ui/AppViewModel.kt:239–277`, only the shared-text/URL branch chooses a tool and sends the workspace-open event (lines 242–263). The URI branch merely inspects and appends sources to the existing form (lines 267–270). A fresh `model/WorkspaceDraft.kt:12` defaults to `pdf_02` (Images to PDF). `WorkspaceScreen.kt:101–110,155–158` rejects a PDF input under that image-only tool. `HomeScreen.kt:140–147` exposes a generic Resume draft card without adapting the tool.

Reproduction: on a fresh app/default draft, share an application/pdf file from another app to Jumlo. It is copied successfully but Home remains displayed. Tap Resume: the PDF is attached to Images-to-PDF, which fails the MIME gate and cannot execute. With an existing image tool session, the same share appends a PDF to that unrelated session. Expected: route the shared document into a compatible viewer/workspace or explicit tool chooser. Root cause: text-link intake has a dedicated navigation/selection handoff; file intake reuses the picker append path without an entry-point-specific handoff. Confidence: static-confirmed branch behavior; runtime interaction unverified. Bytes remain available as an imported source, so this is navigation/workflow failure, not proven data loss.

Compare working provider-file handoff: `AppViewModel.kt:279–289` resolves the source MIME, chooses a compatible tool with its source ID, then navigates through onReady; `AppViewModel.kt:337–345` similarly handles an existing imported source.

## Architecture inventory

### Modules and variants

`settings.gradle.kts:23–25` includes two Gradle modules: `:app` (the shipping Android application) and `:benchmark` (macrobenchmark/baseline profile test target). Features are packages inside one app module, not separate Gradle feature/domain/data modules. `app/build.gradle.kts:22–41,100–117` uses package com.jumlo.app, compile/target SDK 36, minimum 26, JVM/Java 17, Compose, Kotlin serialization and generated BuildConfig; resources retain English and Arabic.

`app/build.gradle.kts:14,85–97,144–149` chooses standard `src/base/java` or opt-in `src/media/java` factories using `extendedMedia`; standard uses `NativeDirectMediaRuntime` (`app/src/base/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt:7–10`). Extended adds yt-dlp/ffmpeg wrapper dependencies and media assets/manifest overlays. Debug, release and benchmark build types exist at app lines 60–83. This is a variant-dependent media runtime boundary, not a separate printing variant.

### Entry points

* `JumloApplication.kt:10–24`: Hilt application, initializes PDFBox and supplies HiltWorkerFactory-backed WorkManager configuration.
* `MainActivity.kt:41–82`: sole application activity, Hilt AppViewModel owner; installs edge-to-edge Compose, waits for settings/workspace recovery, sets locale/theme/haptics/audio/reduced-motion composition locals, installs NavHost; handles initial and singleTop file/text shares.
* `AndroidManifest.xml:32–65`: exported launcher, `jumlo://workspace` VIEW deep link, SEND for PDF/images/ZIP/text, SEND_MULTIPLE for PDF/images/ZIP.
* `worker/EngineOperationWorker.kt:13–27`: Hilt CoroutineWorker reads opaque job_id, requests foreground operation notification, executes JobStore, retries only lease contention, records infrastructure failure.
* `worker/CancelJobReceiver.kt:13–25`: non-exported Hilt broadcast receiver persists cancellation before WorkManager cancellation; notification action includes opaque job ID.
* Manifest lines 69–93: dataSync foreground service, FileProvider for outputs, WorkManager default initializer removed so custom Hilt factory is used.

### Complete navigation and screen map

Routes are serializable objects at `JumloNavHost.kt:59–66`; registrations are at 404–411. Every route shares AppViewModel.

| Route | UI implementation | Principal transitions |
| --- | --- | --- |
| HomeRoute | `ui/home/HomeScreen.kt` | tool catalog, scan, downloads, draft workspace, selected job Activity, file output external open, Files |
| ToolsRoute | `ui/tools/ToolsScreen.kt` | capability-selected tool workspace; special tools map to Scan/Settings/Files |
| DownloadsRoute | `ui/downloads/DownloadsScreen.kt:27` | new download/media tool workspace, selected job Activity |
| FilesRoute | `ui/files/FilesScreen.kt:41` | output/imported/granted-folder/trash sections; picker import; SAF browse/create/copy/rename/delete; external open/share/export; compatible source/result handoff to workspace |
| SettingsRoute | `ui/settings/SettingsScreen.kt:44` | preferences, permission request, logs export, folder selection, saved folder, Files |
| ScanRoute | `ui/scan/ScanScreen.kt:63`, `SmartDocumentScanButton.kt` | scanner/manual camera results become immutable image sources for Images-to-PDF workspace |
| WorkspaceRoute | `ui/workspace/WorkspaceScreen.kt:66` or `ui/downloads/MediaWorkspace.kt:51` | shared tool form, preview and specialized editors; submit to selected job Activity; `jumlo://workspace` deep link |
| ActivityRoute | `ui/downloads/DownloadsScreen.kt:151` | ongoing/history/result list; pause/cancel/retry; open/share/export/reuse result; new run; show all jobs |

`JumloNavHost.kt:266–275` special-cases scan_doc, dl_settings, file_02/file_trash; ordinary catalog tools share WorkspaceRoute and are distinguished by draft.toolId. `WorkspaceRoute` hosts MediaWorkspace when form tool is dl_media or dl_02 (line 355); ordinary batch direct download dl_03 remains generic WorkspaceScreen. The active dock actually contains Home, Files, central Scan action, Tools, Settings (`components/QuietNavigation.kt:109–138`); Downloads is reached from Home/Tools rather than a dock slot. Root shell routes are Home/Tools/Downloads/Files/Settings; Scan/Workspace/Activity get a back bar. Workspace back has dirty-state save/discard handling and predictive gesture at NavHost lines 134–148 and 455–466. Dialogs include onboarding, account hub, SAF rename/delete/new-folder and unsaved draft; these are not separate routes.

Specialized workspace children include full input PDF/image preview, PdfPageEditor for organize/split/extract, VisualDocumentEditor for crop/annotation/signature/image insertion, VisualWatermarkEditor, VisualImageSettings and generic advanced fields (`WorkspaceScreen.kt:171–189`). No printer, printer-settings or print-history screen exists.

### ViewModels, DI, stores and data flow

Exactly two concrete ViewModels exist. `ui/AppViewModel.kt:49–60` is a 733-line activity-scoped AndroidViewModel injected with preferences, immutable source store, output store, durable job store, draft store, ephemeral secret store, SAF tree store, work budget and SavedStateHandle. It combines the major screen state flows, workspace draft recovery/debounced persistence, source intake/selection, processing submission, job actions, file operations, output reuse/export, settings mutations and PDF/image preview decoding. Most UI features share this broad coordinator. `ui/downloads/MediaViewModel.kt:55–69` is a 274-line Hilt ViewModel hosted by MediaWorkspace's navigation owner, injected with MediaRuntime/JobStore/secrets/OkHttp/session store; handles URL probing, format analysis/selection and media submission. It resets by sessionEpoch (`MediaWorkspace.kt:57–74`). Neither is a print ViewModel.

`di/AppModule.kt:17–56` installs singleton providers for MediaRuntime, WorkBudget, Room database, OkHttp client and legacy DAOs; other stores/handlers use constructor @Inject and @Singleton. Room database is `universal_utilities_sandbox.db`, version 2; schemas include sources, jobs, outputs, drafts plus legacy managed_files/download_tasks (`data/local/AppDatabase.kt:91–99`). Active jobs serialize immutable JobRequest and JobSnapshot; outputs have a unique (jobId,itemKey) index (`ProcessingEntities.kt:6–13`).

Flow: Compose selects tool/imports files -> AppViewModel updates versioned WorkspaceDraft -> typed immutable execution settings -> DraftStore reserves stable submission identity transactionally -> JobStore persists QUEUED snapshot and enqueues unique `job:<UUID>` WorkManager work -> EngineOperationWorker -> JobStore acquires fenced durable lease/heartbeat -> OperationDispatcher -> PDF/image/archive/download/media/file-extras handlers -> SourceStore -> staged OutputStore publication -> validated private output + hash + Room record + attempted public mirror -> Room Flows update AppViewModel and Activity/Files/Home. Relevant evidence: `AppViewModel.kt:493–545`; `DraftStore.kt:14–31`; `JobStore.kt:41–58,123–188`; `OperationDispatcher.kt:14–43`; `OutputStore.kt:27–65`.

SourceStore always snapshots incoming content into private inbound files, uses offered grants, identifies generic MIME from bytes, and reuses owned seekable snapshots for PDF operations (`SourceStore.kt:29–71,103–131`). OutputStore validates and hashes private published files, verifies existing outputs, creates FileProvider URIs and attempts a public mirror without destroying a valid private output if mirroring fails (`OutputStore.kt:40–65`); it also reconciles interrupted publication/trash. DraftStore strips secrets from stored drafts; submission credentials/URLs use ephemeral handles. OperationDispatcher serializes expensive img_/pdf_ work with one semaphore and Drive with two slots; download admission has a separate concurrency limit. Existing processing job lifecycle has queued/validating/running/finalizing/completed/partial/failed/cancelled/interrupted/paused/waiting-for-input states. These refer to processing output generation, not physical printing.

### Availability boundary

`core/CapabilityPolicy.kt:20–41` distinguishes navigation-only tools from executable operations and blocks unknown IDs. OCR/webpage rendering, PDF protection/redaction, encrypted ZIP, Drive and Vault are currently hidden/disabled despite some catalog/handler remnants. Current `data/processing/OcrWebHandler.kt:11–16` unconditionally throws CAPABILITY_UNAVAILABLE. The old `docs/deferred/OcrWebHandler.kt` uses a WebView print adapter for webpage-to-PDF creation, outside all declared source sets. It is neither a shipping webpage capture implementation nor a physical printer integration.

## Printing capability checklist and responsibilities

| Requirement | Actual shipped source |
| --- | --- |
| Android PrintManager job submission | Absent |
| PrintDocumentAdapter.onLayout/onWrite | No implementation/consumer; unused callback helper only |
| CancellationSignal and exact completion callbacks | No active pipeline to evaluate; helper interfaces expose success/failure but omit cancellation, unused today |
| PageRange subset writing / truthful reported ranges | No active onWrite; cannot verify or call it correct |
| PrintAttributes media size/margins/resolution/color/duplex | No usage |
| System printer picker/service discovery | No entry into the system print dialog |
| Printer job status, cancel, restart | No PrintJob usage; WorkManager controls apply to file processing only |
| App print history/favorite/recent printers/presets | No printer model/preferences/history |
| Copies | No print settings/model; no app-side repeated-page workaround |
| Existing adjacent features | File output favorites; recent/most-used tools; durable processing history, pause/cancel/retry; PDF page-range extraction/split/rasterization; Images-to-PDF Auto/A4/Letter/Custom page geometry, orientation/margins/fill; PDF raster export DPI/grayscale |

Adjacent feature evidence: `UserPreferencesRepository.kt:29–47,70–99`; `Models.kt:101–120`; `DownloadsScreen.kt:151–205,235–244`; `WorkspaceDraft.kt:20–21,46–55`; `WorkspaceScreen.kt:628–658`. None is connected to Android printer capabilities.

For a normal Android client app, system print UI and installed print services own discovery, printer capability advertisement/selection, device transport and physical device execution. Jumlo would own its Print action, adapter-produced PDF, handling changed attributes/requested ranges/cancellation, and accurate app-facing job/result labels. A custom printer discovery stack or PrintService is not required merely to add client printing. Job queries are limited to the calling app's current print jobs; durable app history/favorites/presets are separate product work. Hardware supported options must follow the installed service/device; copying pages to imitate copies/duplex would be an inappropriate substitute.

Primary official documentation read by this agent (root must open before citing):

* https://developer.android.com/reference/android/print/PrintManager
* https://developer.android.com/reference/android/print/PrintDocumentAdapter
* https://developer.android.com/reference/android/print/PrintJob
* https://developer.android.com/reference/android/printservice/PrintService
* https://developer.android.com/training/printing/custom-docs

## Tests and limits

There are no printing-specific unit, instrumentation, host or source-wiring tests in the searched active trees. Existing PDF geometry/page-selection/domain tests and durable processing/publication tests are useful prerequisites but do not test printer output. Once printing is approved for implementation, needed meaningful qualification would cover generated versus preview page geometry, requested single/disjoint/all page ranges and exact callback reporting, relayout across media/orientation/margins, cancellation/failed writes and destination lifetime, off-main-thread large-document rendering, app-created PrintJob state mapping, printer unavailable/no-service path, and an actual system spooler/service/device or emulator flow. Current absence means these are future acceptance requirements, not failed tests of a present feature.

No hardware, spooler, UI, instrumentation or Gradle execution was performed by this agent. AP-01 is established by missing entry points/implementations, while AP-02/AP-03 are static-confirmed branch/routing defects requiring device reproduction before claiming observed runtime presentation. No fixes have been made. Phase 0 must stop for approval before any implementation.
