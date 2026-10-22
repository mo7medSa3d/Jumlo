# Printing implementation report

Scope: new printing subsystem under `app/src/main/java/com/jumlo/app/printing`, matching English/Arabic resources, pure/ViewModel tests and device adapter tests. Existing navigation, AppViewModel, manifest, processing handlers and build files are owned by the root integration agent and were not edited by this implementation agent. No new dependency and no git commit.

## Public integration contract

- `@Serializable PrintPage(sourcePage: Int, rotationDegrees: Int = 0)`: one-based original page and relative clockwise rotation. Ordered references can repeat.
- `@Serializable PrintSource(path: String, name: String, mimeType: String, pages: List<PrintPage> = emptyList())`: immutable private source; empty page list means every original page.
- `@Composable PrintScreen(source: PrintSource?, onBack: () -> Unit, onPickDocument: () -> Unit, modifier: Modifier = Modifier, viewModel: PrintViewModel = hiltViewModel())`.
- Screen content uses the root navigation shell; it does not add a Scaffold or second fixed top bar. Root owns document picker/import and incoming-source routing.

## Implemented behavior

### Geometry and document preparation

Pure point-based geometry governs preview and output: A4/A5/Letter/Legal/source/custom media; portrait/landscape; fit/fill/actual-size; 10–400% scaling; physical left/top/right/bottom margins; printer minimum margins combined by edge. CropBox offsets, natural PDF rotation, editor-relative rotation, UserUnit and repeated/reordered source pages are represented explicitly.

The PDF color path imports pages as PDFBox form XObjects, retaining text/vector content and using the shared affine transform. It deliberately replaces PDFBox LayerUtility's rotation matrix because that port applies aspect-distorting transforms for quarter-turn rotations. Printable, visible annotation appearances are painted into the in-memory source document before import; source bytes are never modified. Dynamic XFA or a printable annotation with no usable appearance fails explicitly rather than silently dropping it. Password-restricted PDFs and print permission restrictions fail with localized guidance.

Grayscale deliberately rasterizes at 300 DPI using at most 1024×1024 pixel tiles, then embeds those tiles into a PDF. The UI explains loss of vector/text representation in grayscale. Color remains vector. Images are decoded separately for printing with a 2-megapixel sampled decode budget and EXIF rotation/mirroring; their physical size uses original dimensions and explicit user DPI, so sampled bitmap dimensions never silently determine actual-size/source-size geometry. Animated PNG/WebP and unsupported still formats are rejected.

Preview composes the same one-page PDF using the same options/model and then renders that result. Main preview, lazy page thumbnails, selected-page semantics, pinch/pan/double-tap zoom, physical-coordinate margin handles, numeric millimeter controls, media/scaling/color/duplex controls and localized page-range input are implemented. Margin handles remain physical left/right in Arabic. Zoom uses existing spring/reduced-motion tokens; commits and drag boundaries use existing haptics. Published Compose bitmaps are GC-owned to avoid recycling an image retained by RenderThread.

### Android adapter and lifecycle

`JumloPrintAdapter` invokes real framework layout/write callbacks, computes known page count, responds to changed media/color/margins, filters requested spooler page ranges in logical document coordinates, and reports the original requested ranges actually emitted. Repeated writes use independent PDF documents/temp files and immutable layout snapshots. Copies are explicitly left to the Android print dialog; no duplicated-page simulation is used for copies or duplex.

Each write has a unit-tested atomic completion gate. Cancellation wins over success still waiting for main-thread delivery; exactly one terminal result is emitted. The adapter closes destination descriptors and all temporary files; source originals are never deleted. Cancellation listener removal is posted to main, avoiding synchronous CancellationSignal listener-cleanup deadlock. onFinish cancels adapter work.

The screen hands printer discovery, service selection and capabilities to Android. It provides system print-service settings and meaningful missing source, unsupported format, no system printing, layout, preview retry and preparation errors. Public Android PrintManager/PrintJob APIs are used. PrintJobId persistence uses its public string representation; hidden flattenToString was removed during compilation review.

### State, persistence and retention

The ViewModel persists current options and compact input identity through SavedStateHandle and stores last reusable settings, named presets, recent/favorite documents, session source and print history with a separate preferences DataStore. Presets and reusable last settings intentionally exclude document-specific ranges; the current session retains its range. Source identity and image-DPI invalidation prevent recomposition resets or stale inspection after selecting a saved image preset.

The input identity is SHA-256, not a second full source JSON. Current source JSON is included in the Bundle only below 16 KiB; large ordered selections use durable sessionSource, preventing this subsystem from duplicating large editor selections in Android's saved-state transaction.

Before submission, the repository creates a private durable job-owned source copy. This copy remains usable if the originating output is subsequently moved to trash. Snapshot leases protect the copy -> Android submission -> history persistence gap. Reference-aware cleanup retains all history, recent/favorite and current-session paths plus in-flight leases; canceled/unregistered copies and evicted unreferenced copies are removed. Known active jobs are retained independently of the 100 inactive-history limit. If the spooler accepts a job but history storage fails, the in-process lease is conservatively retained rather than deleting a potentially active source.

History distinguishes created/queued/started/blocked/completed/failed/canceled/unavailable states. Public Android client APIs expose neither job-state listeners nor print progress percentages on this SDK, so actual app-created job states refresh every two seconds only while the screen lifecycle is STARTED. Preparation progress is distinct; completed means the print service reported completion, not physically verified paper. Active jobs can be canceled; retained failed spooler jobs can be restarted; other history entries reopen their checked private source/options for submission. No printer credentials are stored.

## Tests and evidence

Actually executed by this agent, without Gradle:

- Pure tests were compiled with the existing Kotlin toolchain and executed via JUnit 4.13.2.
- Final host result: **20 tests passed**, zero failures, for `PrintGeometryTest` and `PrintExecutionPolicyTest`.
- Coverage: printable fit/fill/actual-size; edge-specific minimum margins; rotated offset crop boxes and UserUnit; invalid margins/scales; ordered/duplicated source pages; requested disjoint/spanning spooler ranges; invalid range rejection; Arabic/Persian numerals, Arabic comma and all-page text; reusable presets; missing spooler state; concurrent terminal callback racing; cancellation before delivered success; late cancellation after terminal success; 4032×3024 camera sampling; localized decimal parsing; lease/cleanup/cancellation retention; active-job eviction protection; stable renamed-document identity.
- Initial pure tests were written before model implementation and compiled red for absent types. The Arabic-range regression was observed failing with PRINT_RANGE before normalization, then passing. Additional review-driven tests were executed after those fixes; this report does not claim every new Android behavior was exercised through a full red/green cycle.
- XML validation found all 100 referenced English print strings present and all 100 Arabic counterparts present, with no duplicate print resource names at that checkpoint.

Added for root-owned Gradle execution:

- `PrintViewModelTest`: seven actual ViewModel cases with fake PrintStore/PrintEngine and the existing coroutine test dispatcher: recomposition/new source behavior, invalid-range recovery, missing-source retry, process-restored options/presets, image-DPI preset race, bounded saved state for large selection, canceled pending inspection.
- `JumloPrintAdapterTest` device source: two framework adapter tests using real ParcelFileDescriptor destinations and the existing callback helper. Tests call onLayout/onWrite, assert subset ordering/range reporting, repeat writes, parse emitted PDF page counts, and test no-layout failure without rendering. Device execution is not claimed. Root is responsible for compiling the androidTest variant.
- Root owns the integrated APK/unit-test/lint builds. Initial integrated compilation identified hidden PrintJobId.flattenToString and a nested Compose receiver; both were corrected. Root also reported one resource build-cache filesystem failure and one mixed in-progress-source test compile; neither was labeled an implementation pass. Final root build evidence should be appended below.

## Files

New production Kotlin:

1. `app/src/main/java/com/jumlo/app/printing/PrintModels.kt`
2. `app/src/main/java/com/jumlo/app/printing/PrintGeometry.kt`
3. `app/src/main/java/com/jumlo/app/printing/PrintContracts.kt`
4. `app/src/main/java/com/jumlo/app/printing/PrintRepository.kt`
5. `app/src/main/java/com/jumlo/app/printing/PrintSnapshotPolicy.kt`
6. `app/src/main/java/com/jumlo/app/printing/PrintExecutionPolicy.kt`
7. `app/src/main/java/com/jumlo/app/printing/PrintImageDecoder.kt`
8. `app/src/main/java/com/jumlo/app/printing/PrintPdfComposer.kt`
9. `app/src/main/java/com/jumlo/app/printing/JumloPrintAdapter.kt`
10. `app/src/main/java/com/jumlo/app/printing/PrintViewModel.kt`
11. `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt`

Resources: `app/src/main/res/values/printing.xml`, `app/src/main/res/values-ar/printing.xml`.

Tests: `app/src/test/java/com/jumlo/app/printing/PrintGeometryTest.kt`, `PrintExecutionPolicyTest.kt`, `PrintViewModelTest.kt`; `app/src/androidTest/java/com/jumlo/app/printing/JumloPrintAdapterTest.kt`.

## Data formats and dependencies

Existing dependencies only: PDFBox Android 2.0.27.0; Compose/Material3 and lifecycle; Hilt; preferences DataStore; Kotlin serialization; existing AndroidX EXIF; Kotlin coroutines; existing JUnit/coroutines-test/device test dependencies.

DataStore name `jumlo_print_v1`, preference key `print_data`, versioned JSON `PrintSavedData(version=1)`. Fields: sessionSource, lastOptions, presets (24), recent/favorites (60 total, up to 24 nonfavorite recents), history (100 inactive plus known active). Paths are canonicalized and allowlisted to `filesDir/inbound`, `filesDir/processing/results`, `filesDir/printing/sources`, checked for file/readability/nonempty content and rejected for partial files. Print snapshots are UUID `.bin` files; partial writes use `.bin.part` and atomic move. Temp preview/write/vector PDFs use cacheDir and deterministic finally cleanup. The source copy budget is 1 GiB; prepared PDF publication is capped at 512 MiB; preparation requires 64 MiB free-space reserve. Page selections are capped at 10,000. Custom media is 25–1000 mm on either dimension.

## Limits and remaining qualification

- No hardware printer, emulator spooler, actual printer service, or interactive touch/RTL/accessibility device run was available to this agent. Device adapter tests are source-level additions until root compiles/runs them. Do not claim paper output, universal printer compatibility, or measured render parity on device.
- Full color PDF content is vector-preserving; grayscale intentionally rasterizes text/vector content at 300 DPI. Large grayscale PDFs can take time and disk despite bounded bitmap memory.
- The print image decoder intentionally reduces pixel resolution to fit memory. Explicit DPI and physical size remain unchanged; maximum photographic detail depends on that bounded sampled decode.
- Dynamic XFA, encrypted/password-required PDFs and missing printable appearance streams are not silently approximated. Error messages ask for a suitable unlocked/flattened source. Digital signature appearance may print, but recomposed output is not the original cryptographically signed PDF.
- A print job uses one media size. Source-size means the first selected source page; mixed-size remaining pages use the selected fit/fill/actual mode.
- Android framework descriptors and bitmap paths still require device testing. The pure callback guard proves its atomic state transitions, not every platform binder or service behavior.
- The fixed public PrintJobId string representation is used as the history key; an OS version changing that representation can yield an unavailable historical state, never a fabricated completion.
- Old private copies are cleaned only when unreferenced. Favorites and current session intentionally retain their source copies. If Android accepts a submission but local history persistence fails, safe retention can leave a copy until later reconciliation/restart.

## Root final integrated validation

Pending root's final coherent-source build and test run at the time this report was written. No Gradle command was run by this implementation agent.

### Final coherent root build evidence (appended after completion)

Read-only verification of root's `audit-evidence/phase6-coherent.log` reports **BUILD SUCCESSFUL**, including `:app:testDebugUnitTest`, `:app:assembleDebugAndroidTest`, and `:app:lintDebug`, in 2m 1s. The generated printing JUnit XML files contain **27 passing tests, zero failures/errors/skips**: PrintGeometryTest 11, PrintExecutionPolicyTest 9, PrintViewModelTest 7. The actual ViewModel tests were therefore executed, not merely compiled. The Android test APK compiled; no device/instrumentation execution is claimed. This supersedes the earlier pending-build note above.
