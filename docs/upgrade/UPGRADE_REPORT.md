# Jumlo: printing and interaction upgrade

## Delivery and scope

This report covers the read-only Phase 0 audit and the subsequent implementation of Phases 1–6 in the supplied Kotlin/Jetpack Compose project. The later instruction to apply all phases authorized continued work without an intermediate approval stop. Work used an isolated copy of the existing source; the original input archive was retained. This is an engineering handoff with a debug APK, updated source and reproducible verification evidence.

The main addition is a real Android print flow connected to existing documents and processing results. The upgrade also makes page organization reversible, adds direct crop and margin manipulation, improves bilingual and accessible controls, and repairs several existing processing defects. The app's identity, document publication model, Room database version, existing file formats and dependency versions remain in place.

The distinction between implementation and qualification is explicit throughout this report. Compiling an APK and passing JVM tests establishes useful evidence about the source and logic. It does not establish physical printer output, gesture dispatch, camera behavior, TalkBack usability or measured frame rates on a device.

### What to open

- `Jumlo_Upgraded_Source_2026-10-20.zip`: complete source, updated tests, audit reports and build evidence.
- `Jumlo_2026_Printing_Debug.apk`: installable debug build for Android 8.0/API 26 and later; application ID `com.jumlo.app.debug`.
- `Jumlo_Audit_Upgrade_Report.pdf`: this report, including the complete capability inventory and verification limits.

The source archive contains the full detailed baseline audits under `docs/upgrade/audit-baseline/` and the independent review under `docs/upgrade/review/`. Older project reports remain as historical evidence; this report and `verification/printing-upgrade/` describe this revision.

## 1. Phase 0: project map and baseline

### Structure and responsibilities

| Area | Baseline implementation | Upgrade integration |
| --- | --- | --- |
| Modules | `:app` is the Android application; `:benchmark` supplies macrobenchmark/baseline-profile infrastructure. | Both retained; features remain packages inside `:app`. |
| Entry | `JumloApplication` initializes Hilt, PDFBox and WorkManager; `MainActivity` hosts edge-to-edge Compose and external shares. | Compatible file-share routing and opaque job-notification navigation added. |
| Navigation | Eight typed routes: Home, Tools, Downloads, Files, Settings, Scan, Workspace and Activity. | Print is the ninth route and a main bottom-navigation destination. |
| ViewModels | Activity-scoped `AppViewModel` coordinates drafts, records, previews and file operations; `MediaViewModel` owns download analysis. | `PrintViewModel` owns printing; route-scoped `PdfPageEditorSession` retains large editor history. |
| Preferences | DataStore stores theme, language, motion, haptics, sound, file view, favorites and download preferences. | Output display-name aliases and independent print preferences/history added. |
| Data | Room database `universal_utilities_sandbox.db`, version 2: sources, jobs, outputs, drafts, plus legacy managed-files/download tables. | No database version change or destructive migration. |
| Sources | `SourceStore` imports private immutable snapshots and handles seekable access and URI permissions. | Existing source and verified-output entry points feed printing. |
| Work | `DraftStore`, `JobStore`, WorkManager, leases/heartbeats and typed operation handlers. | Existing processing pipeline preserved; targeted retry, memory and PDF fixes integrated. |
| Outputs | `OutputStore` validates and hashes staged files, journals publication, commits private output and attempts a public mirror. | Rename changes a display alias; printing re-verifies the canonical private payload. |

Evidence: `settings.gradle.kts`, `JumloApplication.kt`, `MainActivity.kt`, `ui/navigation/JumloNavHost.kt`, `ui/AppViewModel.kt`, `data/local/AppDatabase.kt`, and the architecture audit in `docs/upgrade/audit-baseline/architecture_print.md`.

### Screen and navigation map

| Screen | Existing purpose and routes | Current change |
| --- | --- | --- |
| Home | Quick tools, scan entry, draft recovery, activity, recent/favorite output shortcuts. | Saved-data loading/error handling and resilient output thumbnails. |
| Tools | Searchable PDF, image, download and file catalog; opens compatible workspace or special screen. | Catalog and existing tool gates retained. |
| Downloads | Download activity and entry to direct/media download workspaces. | Record loading/error/retry states; existing job actions retained. |
| Files | Saved outputs, imported sources, granted provider folder, trash, search/filter/sort and batch handoff. | Pane-aware grids, long-press selection, swipe actions, rename with undo and Print actions. |
| Settings | Theme/dynamic color, Arabic/English, font scale, motion/haptics/sounds, destination, concurrency, notifications, storage and privacy. | Existing persisted settings retained; new components consume the same theme/accessibility preferences. |
| Scan | ML Kit document scanner and CameraX/manual quadrilateral fallback. | EXIF orientation and letterbox coordinates corrected; precise corner controls added. |
| Workspace | Shared tool form, input preview, page/geometry/watermark editors and processing submission. | Stable page instances, complete page-edit history, focused zoom preview, transform-aware crop and paper-margin preview. |
| Activity | Processing job detail/history, progress, pause/cancel/retry and verified results. | Job-notification deep entry and result printing. |
| Print | Absent in the baseline. | Source selection, actual composed preview, paper/scale/margins/color/duplex/range, presets, recents/favorites and system-job history. |

The bottom dock now presents Home, Files, Print, Tools and Settings. Scan remains available from Home and its existing route; Downloads remains reachable through Home and Tools. The implementation uses short transitions and predictive-back progress while retaining the workspace's unsaved-change handling.

### Baseline printing pipeline

The supplied app produced PDFs and other files but did not call `PrintManager.print`. Its unused `android/print/PrintAdapterHelper.java` constructed callback wrappers; it was not a working submission path. `PdfRenderer.RENDER_MODE_FOR_PRINT` in processing code rasterized pages and did not send a job to a printer. There was no existing connected `PrintDocumentAdapter` to preserve or extend.

The working processing architecture was retained: Compose updates a versioned `WorkspaceDraft`; `DraftStore` reserves an immutable `JobRequest`; `JobStore` enqueues unique WorkManager work and acquires a fenced lease; a handler reads private source snapshots and writes staged results; `OutputStore` validates, hashes, journals and publishes them; Room flows update Files and Activity. The new print path begins from those same snapshots and verified results rather than replacing this pipeline.

### Fresh baseline verification

- The original Android source failed Kotlin compilation because `SmartDocumentScanButton` supplied an `enabled` argument that `QuietTonalButton` did not accept.
- The fresh host suite passed 180 tests and failed one stale compression-profile expectation. The implementation's existing behavior and its Android JUnit expectation agreed; the host expectation was corrected.
- Seventeen of twenty auxiliary scripts passed. Failures involved a missing legacy structural SQL fixture/provenance and a missing optional-media CI job. Later Android-test compilation also exposed stale test constructors and a reference to a UI component that was already absent from production.
- The audit found dark mode, dynamic-color choice, Arabic resources, baseline RTL support, haptics and reduced-motion settings already implemented. They were refined instead of being reported as missing.

Baseline evidence is preserved separately from post-upgrade results. The reconstructed legacy-v1 SQL fixture is explicitly structural evidence derived from retained entities; it is not represented as an authentic historical Room schema export or proof of a real installed-v1 upgrade.

### UI/UX audit findings

| Finding | Baseline evidence and impact | Implemented response |
| --- | --- | --- |
| Scan coordinate mismatch | Corner guides used the whole viewport while the image was letterboxed; capture EXIF was ignored. | Shared fitted-image geometry, orientation normalization and accessible corner controls. |
| RTL document overlays | Logical offsets mirrored image/text overlays while physical crop guides did not move. | Physical document coordinates are independent of UI direction. |
| Gesture-only controls | Some scan, watermark and zoom tasks lacked an assistive action or secondary control. | Named geometry controls, accessible reorder/reset actions and synchronized secondary inputs in the upgraded editors. |
| Page rotation/history | Repeated rotate replaced a global 90-degree setting; undo saved order only. | Stable instances, independent cumulative rotation, complete page-edit snapshots and focused preview. |
| Selection semantics | Some custom page/file/media controls showed selection visually without exposing selected state. | Explicit state/role semantics and contextual selection actions. |
| Crop preview/history | Rotation and flips affected output without updating the visible crop preview or its history. | Preview transforms and full crop/transform snapshots follow processing order. |
| Approximate watermark | Decorative text scaling did not reflect physical output, and page numbers were not previewed. | Shared physical sizing/positioning and saved-PDF geometry regressions. |
| Unnamed adjustments | Several sliders, switches and color controls separated their label from accessible value/state. | Named controls, meaningful values and selection state on touched controls. |
| Dense Files layout | Grid columns followed the whole window and fixed heights, ignoring the safe pane and large text. | Measured-pane columns, font-scale-aware minimum widths and growing cards. |
| Weak reorder feedback | Page order changed only after dropping; no edge auto-scroll. | Live placement, edge scrolling and haptic interaction feedback. |
| Missing load/failure states | Several initial record lists resembled empty data; failed image thumbnails were blank. | Separate repository loading/error states, reduced-motion-aware placeholders and thumbnail fallback. |
| Latent secret fields | Gated password/token tools still used generic unmasked fields. | Capability gates remain closed; activating those unrelated tools requires their existing qualification work. |

Spacing, shape, theme, haptics and localization were already partly centralized. The audit therefore targeted inconsistent controls and missing behavior, rather than assuming that dark mode, RTL, animation or 48-dp controls were absent everywhere. Actual target bounds, text clipping, assistive focus order and frame pacing were not measured in the baseline or this environment. Detailed file/line evidence and reproduction steps remain in `docs/upgrade/audit-baseline/ux_accessibility.md`.

## 2. Build and dependencies

| Build/dependency | Version or setting | Decision |
| --- | --- | --- |
| Android minimum / target / compile | 26 / 36 / 36 | Retained. |
| Build Tools / JVM | 36.0.0 / Java 17 | Retained; verified using the provisioned toolchain. |
| Gradle / Android Gradle Plugin | 8.14 / 8.10.1 | Retained. |
| Kotlin / Compose compiler plugin | 2.0.21 / 2.0.21 | Retained. |
| KSP | 2.0.21-1.0.28 | Retained. |
| Compose BOM | 2024.11.00 | Retained; resolved UI/Foundation 1.7.5 and Material 3 1.3.1. |
| Activity / Navigation / Lifecycle | 1.9.3 / 2.8.4 / 2.8.7 | Existing gesture, typed-navigation and ViewModel APIs reused. |
| Hilt / Hilt Navigation and Work | 2.54 / 1.2.0 | Retained. |
| Room / DataStore / WorkManager | 2.7.2 / 1.1.1 / 2.10.0 | Retained; no Room migration added. |
| Coroutines / JSON serialization | 1.9.0 / 1.7.3 | Existing production and coroutines-test dependencies reused. |
| PDFBox Android / zip4j | 2.0.27.0 / 2.11.5 | Existing PDF and archive engines retained. |
| OkHttp / Coil | 4.12.0 / 2.7.0 | Existing network and image loading retained. |
| CameraX / ML Kit document scanner | 1.4.0 / 16.0.0 | Existing capture/scanner dependencies retained. |
| EXIF / DocumentFile / Window | 1.3.7 / 1.0.1 / 1.3.0 | Existing orientation, SAF and adaptive-layout support reused. |
| JUnit / AndroidX JUnit / Espresso | 4.13.2 / 1.2.1 / 3.6.1 | Existing test framework retained. |
| Optional yt-dlp/FFmpeg wrappers | 0.18.1 | Existing opt-in `extendedMedia` variant retained; not part of the delivered standard APK. |

**New dependencies: none. Changed dependency versions: none.** Platform printing uses Android's public framework APIs. No proprietary designs, assets or external application code were imported. Existing fonts, icons and app identity were reused. Dependency evidence is `gradle/libs.versions.toml` and `app/build.gradle.kts`.

## 3. Phases 1–3: design, shell and direct manipulation

### Design system

The existing Material 3 light/dark palettes and optional Android dynamic colors remain the foundation. Shared spacing, radii, elevation and motion live in `ui/theme`. Interaction durations include 120 ms fast, 220 ms standard, 160 ms exit and 180 ms state changes, with spring damping constants for physical feedback. Reduced motion uses immediate/static alternatives, including loading placeholders. Elevation roles are 0, 2, 8 and 14 dp; the common touch target is 48 dp.

Typography continues to use the bundled English and Arabic fonts and the user's system/app font scale. The Arabic static regular font is registered at its actual weight so the framework can synthesize requested heavier weights instead of treating identical outlines as four different weights. The resource-key audit finds English/Arabic parity; rendered shaping, translation quality and TalkBack order still require device qualification.

UI positioning uses logical start/end. Physical document coordinates deliberately retain a top-left or PDF-defined origin in either locale. This distinction prevents an Arabic layout from moving a watermark or crop guide to the opposite physical edge of a page.

### Navigation and entry points

Print is connected to the bottom dock, saved-output menus, processing results, imported sources and selected page instances. External PDF/image/ZIP shares choose a MIME-compatible tool and open its workspace after import. Processing notification taps now carry an opaque validated job ID and open the associated Activity detail. The existing `jumlo://workspace` link remains.

Predictive back follows gesture progress in Workspace, Print, Scan and Activity. Workspace still checks unsaved changes. Print uses a short spring container transition; reduced-motion paths suppress travel. The earlier unused shared-transition wrapper was removed because it did not implement element sharing.

### Page organization

Each logical page instance has a stable identity, source-page reference and independent rotation. Duplicating a page produces another independently editable instance; repeated rotation accumulates correctly. Long press enters selection and lifts a page; live reordering and edge auto-scroll support longer documents, with haptics on interaction boundaries. Earlier/later actions remain available for assistive use.

A contextual bar above the page content offers delete, rotate, duplicate and Print selected. The focused preview shows the chosen instance and its rotation, with pinch zoom, bounded pan, double-tap fit/zoom, accessible reset and previous/next navigation. Lazy thumbnails preserve document order and expose selection state.

Undo/redo records complete page edits, including order and per-instance transforms, with up to 30 snapshots. Large history and selected-ID lists now live in a route-scoped ViewModel, outside the Activity saved-state Bundle. Configuration changes retain them. After process death, the current edit is restored from the existing durable draft; undo/redo history starts fresh. This avoids serializing megabytes of history through Android's saved-state transaction.

### Crop, margins and visual controls

The crop editor displays the same rotation/flip state that the image pipeline applies. Draggable corners and edges, zoom/pan/fit, aspect locking, and synchronized secondary controls operate in document coordinates. Crop snapshots include the rectangle, rotation, flips and aspect state, so undo/reset no longer leave transformations behind. Physical overlays use absolute document positioning in Arabic as well as English.

Images-to-PDF gains a real paper-layout preview, shared with the processing geometry helper, and draggable uniform margins. Orientation and fit/fill are visual choices; a slider and existing advanced values stay synchronized. The print screen separately offers four physical margin handles, paper-size/orientation options, fit/fill/actual-size selection and a scale slider. Numeric values are secondary; Arabic and Persian digits are accepted where these controls parse input.

Watermark preview and output now share visible-page geometry, including cropped and rotated PDFs and `/UserUnit`. Text/image watermark position, physical sizing and page-number placement are covered by saved-PDF operator fixtures. Preview glyph rasterization and PDF font rendering may still differ slightly; no device pixel-golden equivalence is claimed.

### Files and feedback

Long press enters file/source selection. Swiping a managed output toward deletion moves it to existing recoverable trash with a snackbar Undo; the opposite swipe exposes rename, share and print actions. The same actions remain reachable through menus and accessibility actions. Rename updates the display label and has undo, while the authoritative private file name, publication identity and hash remain stable.

Files grid columns now follow the available pane width and font scale; cards grow with content. Home, Files, Downloads and Activity distinguish loading, empty and failed repository states. Provider folders and managed trash have explicit load/error/retry handling, and filtered output/source/trash/folder lists offer reset or clear-search actions. Tools search also matches Arabic subtitles. Failed image/media previews display a fallback instead of a blank rectangle. New controls use shared labels, selection semantics and appropriate touch targets; the original haptics/sound preferences continue to govern feedback.

Evidence: `ui/theme/`, `ui/navigation/JumloNavHost.kt`, `ui/workspace/`, `ui/files/FilesScreen.kt`, `ui/components/SwipeFileActions.kt`, `ui/components/DocumentStates.kt`, and the editor implementation report in `docs/upgrade/review/`.

## 4. Phases 4–5: real printing and saved workflows

### Print architecture

| Boundary | Responsibility |
| --- | --- |
| `PrintSource` and page instances | Carry a private source path, display name, MIME and ordered page references with relative rotations. |
| `PrintViewModel` | Inspect the document, validate options/ranges, retain the current session, request previews, submit work and reconcile system job states. |
| `PrintGeometry` and range policies | Map crop origin, page rotation and physical units; fit/fill/actual scaling; map logical selected pages to framework-requested page positions. |
| `PrintPdfComposer` | Generate the PDF that is previewed and handed to Android; preserve color PDF vectors/text where supported. |
| `JumloPrintAdapter` | Respond to layout changes and repeated subset writes, capture immutable per-write layout, support cancellation, and deliver one terminal callback. |
| Android print dialog / installed service | Discover/select printers, expose supported capabilities and copies, present final printer-aware preview and spool output. |
| `PrintRepository` | Persist presets, recents/favorites, session and job history; manage private job snapshots with leases and reference-aware cleanup. |

The app-generated preview renders the composed output PDF, not a decorative approximation of the source. Paper size, orientation, scaling and margins use the same geometry as composition. Android may impose larger minimum margins or a different supported paper size after printer selection; `onLayout` re-computes the document using those attributes. The system preview is therefore the final printer-specific preview. This division follows the public Android custom-printing lifecycle.[1]

Color PDF output preserves text and vector graphics. Grayscale is explicitly a 300-DPI tiled rendering path, including text, to bound memory; the interface explains this tradeoff. Image printing uses a separate sampled decoder with EXIF normalization while retaining original physical dimensions through the chosen image DPI. This avoids rejecting ordinary camera photos because an exact-resolution processing decoder has a different memory contract.

Preparation is cancellable and reports completed/total prepared pages. Printer-job history shows Android service states, supports cancellation when permitted, and retries a failed live job or restores its source/options for a new submission. It does not fabricate a physical-print percentage: the public app-facing API supplies states and job actions, not proof that paper emerged from the device.[2][3]

Printer discovery and selection use Android's print dialog and installed print services. Empty/error guidance links to system print-service settings. Copies are selected in that dialog; `PrintAttributes` has no app-settable initial copies field. The app does not promise capabilities that the chosen printer/service lacks.

### Added saved features and options

| Feature | Current implementation |
| --- | --- |
| Print history | Persistent service-state history, cancel/retry/reuse and source retention. Active jobs are retained separately from the bounded inactive history. |
| Recent print documents | Persistent recent sources with stable identity across display-name changes. |
| Favorites | Existing output favorites retained; print recents can also be favorited. |
| Presets | Named reusable print settings; document-specific ranges reset when reusing a preset. |
| Paper selection | A4, A5, Letter, Legal, source size and custom dimensions. A job uses one paper size; source-size mode uses its first selected page. |
| Orientation and scaling | Portrait/landscape; fit, fill and actual size; interactive percentage slider. |
| Margins | Four independent physical print margins, direct handles and synchronized millimeter inputs; printer minima are respected. |
| Color and duplex | Color/grayscale plus single-sided, long-edge and short-edge duplex hints, subject to service capabilities. |
| Range and selected pages | Page ranges over edited order, preserving underlying duplicates/rotations; English, Arabic and Persian numeric input. |
| Copies | Set in Android's final print dialog. |

Source snapshots are leased while being created/submitted and retained when referenced by active/history jobs, recents/favorites or the current session. Unreferenced print-owned copies are cleaned without deleting original imported sources or published outputs. Recent-document deduplication uses path and ordered page references, so renaming a document updates its label rather than creating a duplicate lazy-list key.

Current printing bounds are explicit in source: up to 10,000 logical pages, a 1-GiB private snapshot copy limit and a 512-MiB generated print-PDF limit. These are new print-flow bounds, not new limits on the existing processing/import system. Password-protected PDFs require an unlocked printable input; unusable annotation/form appearances return an actionable error rather than silently printing missing content.

Evidence: `app/src/main/java/com/jumlo/app/printing/`, `app/src/test/java/com/jumlo/app/printing/`, `app/src/androidTest/java/com/jumlo/app/printing/`, and `docs/upgrade/review/print-implementation-report.md`.

## 5. Phase 6: reliability and remaining audit findings

The audit exposed correctness issues beneath the UI. These targeted repairs preserve the surrounding architecture:

- Download retries now distinguish a safe byte-range resume from an explicit restart that must discard a stale partial/validator journal. Session-secret expiry and valid paused work retain resumable state; published outputs remain authoritative.
- Image conversion and percentage resizing use original image dimensions rather than silently treating a sampled preview as the original. Memory preflight includes simultaneously resident inputs and outputs; joins decode sequentially and bitmap-heavy paths share an admission gate.
- Compression and target-size operations cannot publish an over-target or larger image as a successful reduction. When optional download compression cannot meet a size or memory contract, the valid original download is retained.
- PDF form input is interpreted according to actual field type, preserving text such as `42` or `true` in text fields. Flattening checks/regenerates widget appearances and resolves page references before removing editable fields.
- Manual scanning normalizes EXIF orientation and uses the same letterboxed image rectangle for the preview, corner hit testing and output mapping. Invalid/degenerate quadrilaterals are rejected; precise accessible coordinate controls supplement dragging.
- PDF crop, image/signature placement, Unicode note rectangles and watermark/page-number output use the displayed physical crop-box frame, including intrinsic rotation and unit scaling. Saved-PDF fixtures exercise all four right-angle rotations, offset crops and non-default units. Selecting the whole visible crop preserves the existing CropBox.
- Test/CI maintenance restores stale constructor/UI fixtures, documents the reconstructed structural SQL fixture and wires the existing opt-in media build into a separate manual CI job.

The independent review identified nine source-level defects during integration. All nine received targeted fixes and re-review: large-image print decoding, stale image DPI, snapshot cleanup, numeric editing, zoomed margin deltas, crop-aware watermark output, oversized editor saved state, optional compression fallback and renamed-recents identity. The review report distinguishes these resolved source findings from unexecuted device checks.

### Issues that remain outside the completed printing feature scope

Existing security-gated OCR, webpage rendering, Drive integration, private vault, encrypted ZIP, PDF protection and redaction are not advertised as completed features. The catalog inventory below records their actual gate and implementation state. Processing history/import retention, very large historical-publication reconciliation cost, exact screenshot stitching and optional media-provider qualification also retain the limitations described in the baseline audit.

Undo/redo covers the direct page, crop/transform and watermark edit operations implemented here. It is not a global history of every settings change, external provider operation or signature pen stroke. System printer selection/copies and the physical spooler belong to Android. These boundaries are stated so the implemented behavior can be assessed accurately.

## 6. Complete capability inventory

The following inventory covers every catalog ID and the relevant noncatalog app features. The three detailed baseline audit files retain exact source references, proposed reproduction cases and original evidence levels.


Scope: the 46 IDs in `CapabilityRegistry.tools` (45 catalog entries plus `sha256`), compared between the audited baseline and the updated working tree in `audit-build/Jumlo-main`. This is a source and test evidence inventory, **not device certification**. Baseline findings come from `processing_data.md`, `architecture_print.md` and `ux_accessibility.md`; current dispositions were checked against the actual updated handlers, capability policy, UI/navigation, stores and printing packages. The independent review's final R1–R9 disposition is recorded in `upgrade-review.md`.

### Reading the classifications

- **Working:** an implemented path serves its declared, bounded scope, with no confirmed active defect identified for that path in the baseline audit. This label does not establish successful Android execution or unrestricted feature completeness.
- **Partial:** meaningful implementation exists, but its scope is narrower, a requested workflow is incomplete, or a security gate prevents user availability.
- **Broken:** the baseline source contains a confirmed active-path correctness defect described in the audit. Concrete Android reproduction was generally proposed rather than executed.
- **Missing:** no callable implementation or qualified backend exists for the capability.

Evidence markers apply to the particular path stated: **S** = source inspection; **H** = executed host/pure policy tests; **P** = executed real-PDF JVM backend fixtures; **I pending** = Android instrumentation authored but not executed on a device/emulator. A passing helper test does not qualify the entire UI-to-output workflow. Baseline classifications below are principally **S**, with executed baseline host evidence available for core archive/hash/HTTP policies. They also incorporate coordinate defects found during implementation review that were already present in the baseline. Current focused PDF/core verification passed **35 tests** in `processing-focused-tests.log`; root's separate active-core run passed **181 tests** in `upgrade-host-core.log`. Root owns the integrated Android build/test/lint result and should attach its final results separately.

Capability availability is unchanged: **34 processing IDs and four navigation IDs enabled; seven explicitly security-blocked IDs; `scan_doc` remains hidden/unimplemented through the generic catalog policy while its separate Scan route is implemented.** Availability and qualification remain separate.

### PDF catalog: 21 IDs

| ID | Feature | Baseline status | Current implementation disposition | Evidence and remaining limitation |
| --- | --- | --- | --- | --- |
| `pdf_02` | Images to PDF | Partial | Preserved AUTO/A4/LETTER/custom pages, orientation, margins and fit/fill. Exact EXIF-aware processing decode replaces silent source sampling. Backend and new creation-layout preview share `ImagePdfLayout`. | S; H for physical layout. JPEG embedding remains lossy at fixed quality; fill uses the established raster canvas policy. Large exact sources can fail an explicit memory preflight. Android output/render parity remains unexecuted. |
| `pdf_03` | Merge PDF | Working | Preserved PDFBox whole-document merge and encryption rejection. | S, P. Catalog mentions ranges, but this handler still merges complete selected input documents; per-input range composition is not implemented here. Complex real-world PDFs/device providers remain unqualified. |
| `pdf_05` | Split PDF | Partial | Preserved every-N, split boundaries and selected-page split modes, including ZIP fallback above the published-output limit. Existing split selection UI remains available alongside the upgraded organizer. | S, P for structural splits; H for plans. Selected range mode produces individual page parts rather than grouped multi-page range files. Device fan-out/archive execution remains untested. |
| `pdf_04` | Organize Pages | Partial | Added stable page instances, independent cumulative rotations, duplicates, reorder/delete, contextual actions, full-page focus preview, accessible reorder alternatives, bounded undo/redo and print-selected integration. Handler consumes additive `pageEdits`; imports preserve intrinsic rotation and inherited resources. | S, H, P. Actual drag/autoscroll, zoom, TalkBack and configuration behavior require device qualification. Current edits recover from the durable draft; undo history starts fresh after process death. |
| `pdf_06` | Extract Pages | Working | Preserved selected physical page extraction in requested order. | S, P and H page selection. No arbitrary inserted blank/new page creation is implied. Large/complex documents and provider flows remain device-unexecuted. |
| `pdf_07` | PDF to Images | Broken | Removed silent requested-DPI downscaling. Exact requested dimensions are rendered when the bitmap budget permits; otherwise an explicit resource error occurs. Preserved PNG/JPEG, grayscale, ranges and large-output ZIP fallback. | S; I pending for Android codec/pixel coverage. Actual PdfRenderer output, requested-DPI pixels and archive publishing require device tests. Native renderer/codec memory is not a whole-process hard guarantee. |
| `pdf_01` | View PDF | Working | Preserved navigation/external-view intent path and existing local preview/viewer components. Print actions now provide an additional, distinct workflow. | S. External viewer availability, URI grants, PDF interaction and pinch/pan remain device-unexecuted. Viewing is a navigation capability, not a processing job. |
| `pdf_compress` | Compress PDF | Working | Preserved selective PDF image recompression while retaining text/vector streams, candidate reopening/page-count checks and original-file fallback when reduction is not achieved. | S, P for fixture compression; H for policy profiles. No universal reduction guarantee; Android image recompression and complex Form XObjects require device fixtures. DPI settings are compression ceilings/profile choices. |
| `pdf_crop` | Crop PDF Pages | Broken | Crop selection now maps from the actual visible, oriented CropBox through UserUnit-aware coordinates. Full-visible selection preserves an existing crop; ordinary uncropped pages retain their prior behavior. Direct editor gains aspect lock, zoom/pan, assistive geometry controls and undo/redo. | S, P for saved CropBox values across all quarter rotations, offset crop and UserUnit=2; H geometry. Cropping hides content rather than deleting it. Actual renderer/device pixels remain unexecuted. |
| `pdf_annotate` | Annotate PDF | Partial | Preserved ASCII text overlay and sticky-note annotation paths. Direct positioning now avoids RTL mirroring; overlay text and sticky-note rectangles use the corrected displayed CropBox/rotation/UserUnit mapping. | S, P for ordinary stamps/annotations and physical text mapping. Non-ASCII overlay still becomes an annotation; freehand/highlight editing is not implemented. Sticky-note icon styling remains viewer-dependent; actual rendered appearance is not certified. |
| `pdf_stamp` | Watermark and Page Numbers | Broken | Preview and backend now share text raster metrics, requested point sizing, caps, placement/tile coordinates and clockwise angle semantics. Added reversible controls and accessible position sliders. Backend watermark/text/number placement uses the visible rotated CropBox and UserUnit-aware inverse transform. | S, P for actual saved-PDF geometry across all quarter rotations, offset crop and UserUnit=2. Android shaping/raster/viewer pixels are unexecuted; PDF Helvetica page-number glyphs can differ from Android sans preview. |
| `pdf_signature` | Sign PDF | Broken | Preserved drawn/imported visual signature and source selection normalization. Aspect-fit image placement now occurs in the visible oriented physical CropBox, then maps back to raw coordinates. Direct editing gains zoom/pan, history and physical RTL positioning. | S, P for actual saved-image placement operators across all quarter rotations, crop offsets and UserUnit=2. This is a visual signature, not certificate/digital signing. Android file decode, imported image metadata/pixels and final viewer rendering remain device-unexecuted. |
| `pdf_image` | Add Image to PDF | Broken | Imported photo/logo aspect fit now uses displayed physical CropBox coordinates and inverse rotation/UserUnit mapping; direct edit controls/history are improved. | S, P for saved image placement/size/orientation; H aspect-fit policy. No full content/image object editor is implied. Actual Android imported-image decoding and final renderer pixels remain unexecuted. |
| `pdf_protect` | Password Protect PDF | Partial | Backend retained; capability remains hidden and disabled for security qualification. | S, P for simple backend password roundtrip only. Protected-output inspection/device integrity and security qualification remain unresolved. Not user-available or newly activated. |
| `pdf_metadata` | PDF Metadata | Working | Preserved title/author/subject/keywords editing and the existing two-input insertion mode. | S, P for metadata/insertion fixtures. This does not expose every PDF metadata namespace or arbitrary embedded-document editing. |
| `pdf_forms` | Fill PDF Form | Broken | Parser now preserves numeric/boolean-looking text and resolves input by actual AcroForm field type. Flatten regenerates/checks appearances, recovers missing widget page pointers from annotations, validates all widgets before removal and rejects unsafe/unresolved forms. | S, P for JSON/key=value primitives, existing values, regeneration, missing-page discovery and refusal before removal. Static AcroForm scope; XFA rejected. Real-device font/appearance pixels remain unexecuted. |
| `pdf_compare` | Compare PDFs | Working | Preserved page-count and first digital-text mismatch report. | S, P. Text comparison only: same-page-count image/graphics changes can report SAME. No visual diff or scanned-document equivalence guarantee. |
| `pdf_redact` | Redact PDF | Partial | Experimental operator-level backend retained; capability stays hidden/disabled. | S, P for simple synthetic operator fixtures only. Complex page safety and secure content-removal qualification remain open; no user-facing secure-redaction claim. |
| `pdf_ocr` | OCR to searchable PDF/text | Missing | Active handler remains an explicit unavailable stub; no activation. | S. Deferred documentation code is not an APK OCR engine. Searchable-layer integrity/security qualification and a real engine remain absent from this path. |
| `scan_doc` | Scan Document | Broken | Dedicated Scan route remains available from Home. Manual scan now maps quad handles through the actual fitted-image rectangle, normalizes capture EXIF, adds accessible corner alternatives and hands ordered captured pages into Images to PDF with rollback/cleanup. | S and H geometry; camera/ML Kit/device execution pending. Generic catalog policy still hides this ID. Camera capability, corner quality, perspective pixels and physical-device capture require qualification. |
| `webpage_pdf` | Webpage to PDF | Missing | Active handler remains unavailable; no activation. | S. No shipping webpage renderer or webpage-to-PDF security qualification. Old deferred WebView code does not count as active functionality. |

### Image catalog: 11 IDs

| ID | Feature | Baseline status | Current implementation disposition | Evidence and remaining limitation |
| --- | --- | --- | --- | --- |
| `img_01` | Convert Images | Broken | Exact EXIF-aware decode preserves source geometry instead of silently sampling. Preserved JPEG/PNG/lossy WebP export, JPEG matte and metadata stripping. | S, H memory policy; I pending for real decode/codec behavior. Oversized exact work fails explicitly. GIF/animated PNG/WebP rejected; HEIC/HEIF decode depends on platform support. |
| `img_02` | Resize Images | Broken | Percentage/longest-edge calculations use original upright metadata; requested output geometry is no longer silently clamped. Fit/fill/pad and upscaling policy remain. | S, H geometry/budget; I pending EXIF-aware percentage pixels. Very large sources require full exact decode and can exceed budget; no tiled/region pipeline. |
| `img_03` | Crop, Rotate and Flip | Broken | Crop now works on exact source pixels, with bounded allocation. Preview displays rotation/flips in backend order, supports aspect lock, pinch/pan, vertical flip and combined transform/geometry history. | S, H transform/constraint roundtrips; I pending pixel coverage. Gesture dispatch, EXIF output pixels and large-source operation need device tests. |
| `img_04` | Compress Images | Broken | Enforces strictly smaller successful candidates; quality never exceeds caller preference. Impossible reduction yields explicit UNCHANGED/IMAGE_NOT_SMALLER, retaining source bytes. Dimension search can reach 1×1 rather than publishing an over-bound candidate. | S, H size/quality policy; I pending real codec target behavior. Compression can trade dimensions/quality and requested PNG still uses lossy WebP policy. No guaranteed reduction for already optimal inputs. |
| `img_exif` | EXIF Inspector | Partial | Preserved metadata report plus re-encoded output, remove-all/location controls and safe-tag retention. Uses exact processing decode. | S. Re-encoding is still part of the feature; preservation is an allowlist rather than byte-identical/full EXIF retention. Non-remove-all forces JPEG. Real EXIF/container device fixtures pending. |
| `img_watermark` | Watermark Images | Broken | Added true combined source/logo/output/matte preflight; preserves text/logo and opacity behavior. | S, H budgeting; existing/new I pending for real pixel opacity and allocation rejection. Text/logo positions remain fixed rather than a full image watermark placement editor. Codec/native overhead is not precisely bounded by bitmap accounting. |
| `img_join` | Join/Collage | Broken | Sequential source decoding beside one exact output canvas replaces resident input copies and silent canvas shrinking. Real combined budgets reject infeasible work. | S, H join geometry/budget; I pending. Still a two-column grid or horizontal/vertical strip with gaps, not an arbitrary collage editor. Large canvases can fail honestly. |
| `img_adjust` | Adjust and Pad | Broken | Added combined source and expanded-output checks before allocation; preserved brightness/contrast/saturation and padding. Controls have clearer values/semantics. | S, H arithmetic; I pending allocation/pixels. Padding remains bounded by settings and is not a content-aware extension tool. Full bitmap work can be refused on limited heaps. |
| `img_target` | Target Size | Broken | Honors the requested inclusive byte cap exactly, with no 512/1024-byte inflation or unrelated original-size clamp. Unmet targets fail honestly and do not publish an oversized result. | S, H bound/quality policy; I pending real JPEG bound test. JPEG-only; extreme targets may be impossible. Expected/actual candidate bytes remain exception metadata; UI shows localized feedback and requested size. |
| `img_compare` | Compare Images | Broken | Exact decode replaces sampled comparisons; report uses actual upright dimensions and per-pixel RGBA mean difference. Row buffers are budgeted with both images. | S; existing I pending alpha/pixel test. Requires both full images to fit; no tiled large-image comparison or perceptual/similarity model. |
| `img_stitch` | Stitch Screenshots | Broken | Uses the sequential, exact, budgeted vertical join implementation and preserves fixed manual overlap. | S, H strip/overlap geometry; device pixels pending. No automatic overlap detection, seam finding or content alignment; codec capability report still has no device-verified fixture claims. |

### Download catalog: four IDs

| ID | Feature | Baseline status | Current implementation disposition | Evidence and remaining limitation |
| --- | --- | --- | --- | --- |
| `dl_media` | Direct video/audio/file download | Partial | Preserved standard direct-media/static HTML source discovery and optional extended runtime. Existing media analysis and format options remain connected; no gated stub is enabled. | S; H routing/arguments/policies. Standard runtime is not universal site extraction. Optional yt-dlp/FFmpeg/QuickJS runtime, ABI startup, live sites, authenticated extraction/HLS/mux need separate qualification; optional wrapper does not inherit WebView session cookies. |
| `dl_02` | New Direct Download | Broken | Failed/restart-required retry advances a persisted generation; executing lease holder removes only stale uncommitted partial/journal state. Pause/network/secret waits preserve resumable partials. Missing validators use the restart-required recovery path. PDF/image postprocessing remains; optional image compression retains a valid original on size-bound or expected memory rejection. | S, H HTTP/resume policies plus real stale-file/lease/generation tests. No actual Android pause/process-kill/reentry end-to-end result. URLs/credentials remain session-only; cold sessions need URL reentry. Generated SHA-256 is not a server-intent checksum. |
| `dl_03` | Batch URL Intake | Broken | Preserved up to 100 direct URLs and sequential members within a job. Same generation recovery keeps committed publications while restarting uncommitted failed transfers. Multiline intake now selects the batch-capable workspace path. | S, H shared transfer/recovery policies. Batch remains direct HTTP(S), not universal playlist/site extraction. Derived outputs may require redownloading source bytes before an existing derived output is rediscovered. |
| `dl_settings` | Download Settings | Working | Preserved destination, Wi-Fi/unmetered preferences and max-concurrency controls. | S, H admission/network policy. OS/provider behavior, permission loss and Android constraint execution require device tests. Preferences do not make cookies/URLs durable. |

### File/Vault catalog: ten IDs

| ID | Feature | Baseline status | Current implementation disposition | Evidence and remaining limitation |
| --- | --- | --- | --- | --- |
| `file_02` | Browse Storage | Partial | Preserved SAF listing/create/rename/delete and verified copy/move APIs; current navigation wires provider opening/import into compatible tools and exposes copy action. Files gains safer import entry, selection semantics, adaptive grids and clearer output actions. | S, H copy/collision policies. Only granted/immediate provider contents; provider delete is permanent and distinct from managed output trash. Actual provider behavior/device gestures remain unexecuted. |
| `file_07` | Create ZIP | Working | Preserved streamed ArchiveCore creation, safe unique names and compression levels, including the existing document collection-to-ZIP workflow without mutating original outputs. | S, H archive core. Existing expanded-input/entry budgets remain; ZIP may hold several disk copies during import/stage/mirror. Android handler/provider publication pending. |
| `file_08` | Extract ZIP | Working | Preserved validation of paths/depth/expanded bytes and bounded publication. | S, H archive core. Output flattening and current 512 published-member limit remain; no large-extract fan-out ZIP fallback. Real-device low-space/provider cases pending. |
| `file_encrypted_zip` | Encrypted ZIP | Partial | zip4j backend retained but hidden/disabled by security policy. | S. AES create/extract qualification remains open; no newly enabled encrypted-archive guarantee. |
| `file_batch_rename` | Batch Rename (copies) | Working | Preserved pattern/start preview report and renamed published copies. Separate current Files output rename updates display metadata without changing verified bytes. | S, H naming. This catalog tool still creates copies; it does not batch-rename originals in place. Output display rename is a separate feature. |
| `file_duplicates` | Duplicate Finder | Working | Preserved SHA-256 exact-match report for selected sources. | S, H digest/grouping. Not perceptual matching, whole-device scanning or automatic deletion. Large selected sources require streamed disk reads. |
| `file_trash` | Managed Trash | Working | Preserved durable managed-output trash, restore/purge, recovery journals and Snackbar Undo. Current multi-selection actions and UI remain connected. | S, H real filesystem trash/recovery tests. Only managed outputs are recoverable here; provider-original deletion is separate. Actual UI/provider mirror behavior needs device qualification. |
| `file_drive` | Google Drive import/export | Partial | Official Drive v3 backend remains hidden/disabled. Public direct Drive URL rewriting remains available through direct downloads. | S. OAuth/privacy/API integration is unqualified; public link download is not evidence of full Drive account import/export. |
| `xf_05` | Private Vault | Missing | Unavailable expansion concept remains blocked; no qualified crypto backend added. | S. No secure-vault claim or encrypted-storage implementation is supplied by this upgrade. |
| `sha256` | SHA-256 verification | Working | Preserved streamed actual digests, optional expected-value comparison, explicit mismatch failure and published digest record. | S, H real streamed digest tests. Integrity of local selected bytes only; trust in the supplied expected digest remains external. |

### Additional, noncatalog features

| Area | Baseline | Current source disposition and evidence | Remaining boundary |
| --- | --- | --- | --- |
| Android printing | Missing client print integration; unused callback helper only. | Added Print route and print actions from Files, results and selected page instances. Real `PrintManager` submission, `JumloPrintAdapter`, off-main PDF composition, layout/write/ranges, cancellation gate, progress and job-state reconciliation. Supports PDF/image sources, source/custom/standard paper, fit/fill/actual size, margin editing, grayscale, duplex request and explicit image DPI. S; pure policies/real-PDF composition tests; actual adapter instrumentation authored. | System print services own printer discovery/capabilities/copies/physical delivery. No printer, spooler/service, device layout/write or physical-output success has been established by this report. Compiled instrumentation is not executed instrumentation. |
| Print history, recents, favorites and named presets | Missing print-specific persistence. | New versioned PrintSavedData in Preferences DataStore: last reusable options, named presets, recent/favorite documents, durable session and bounded history. Stable document identity ignores rename metadata but preserves ordered-page identity. Snapshot leases/reference cleanup protect active/history/recent/favorite/session sources. S and focused policy/ViewModel tests. | These are favorite/recent documents, not a custom favorite-printer/discovery database. Lost spooler jobs become UNKNOWN rather than invented success. Retry uses immutable owned snapshots. |
| Processing history, presets, favorites and recovery | Existing Room jobs/results, favorite outputs/tools and durable workspace draft; presets mostly predefined choices. | Preserved those systems, added exact page edits and bounded direct-editor undo/redo. Page history is route-scoped ViewModel state outside the Activity Bundle; process death restores current draft and seeds fresh history. S and H/JVM session tests. | No unified durable undo history across every tool or a new general named processing-preset library. General old processing history/import retention remains a separate lifecycle concern. |
| File/output actions | Existing verified publications, SAF actions, managed trash, save/share/open and multi-selection collection handoff; no output display rename. | Added durable output display-name override with Undo, clearer import entry, long-press selection and swipe actions. Retained provider/tool and collection-to-ZIP workflows. Added explicit provider/trash loading and error recovery plus filtered-empty/reset states. Physical output bytes and private publication identity remain unchanged by display rename. S and policy tests. | Not an arbitrary filesystem/folder manager. Imported immutable source snapshots still lack a general user deletion/retention interface; existing favorites may outlive purged output metadata. |
| Share/notification navigation | Shared files could stay in an incompatible/default workspace; notification taps omitted job identity. | External file shares inspect MIME and route to compatible workspace; notification intents carry job identity, consumed on initial/new intents. Print is a new typed destination alongside existing screens. S. | Device task-stack, app restart, intents/URI grants and dirty-workspace edge cases require actual interaction tests. |
| Scan/camera | Letterboxed handle coordinates and ignored capture EXIF were defective. | Fit-rectangle coordinate mapping, EXIF normalization, assistive corner controls and ordered multi-page handoff added. S/H geometry. | CameraX/ML Kit/peripheral permissions, perspective quality and real-device pixel orientation remain unexecuted. |
| RTL/Arabic, theme and dark mode | Existing Arabic resources/RTL theme, bundled typography, curated/dynamic light/dark palettes; specific mirrored physical overlays and incomplete selected semantics. | Physical document editing positions no longer mirror with UI direction; direction-aware navigation, selected controls, responsive layout and paired localized additions are present. Typography/token/control polish uses the existing design system. S; resource/static checks. | Translation-key parity/static token contrast is not full Arabic shaping, dynamic-color contrast, large-text, fold/landscape or screen-reader certification. |
| Accessibility and motion | Existing labeled navigation/progress and reduced-motion support, plus gesture-only editing and several unlabeled/visual-only states. | Added named geometry/reorder/scan/margin alternatives, contextual labels/current values, selected semantics and appropriate target sizing in touched controls; preserved reduced-motion behavior and fit/reset actions. S/H geometry. | TalkBack/Switch Access focus, actual touch regions, gesture arbitration, reduced-motion device behavior and measured frame pacing remain qualification work. No shared-element-transition or 60fps certification is claimed. |


## 7. API and data compatibility

| Boundary | Delta and compatibility |
| --- | --- |
| Page editing format | New serializable `PdfPageEdit(instanceId, sourcePage, rotationDegrees=0)`; trailing/defaulted `pageEdits: List<PdfPageEdit> = emptyList()` in organizer settings and WorkspaceDraft. Nonempty entries are authoritative; missing/empty entries preserve legacy order/rotation fallback. Draft/request schema version remains one. |
| Job retry state | Trailing/defaulted `JobSnapshot.downloadRestartGeneration: Long = 0` and matching ExecutionContext field. Old stored jobs decode as generation zero. Per-item transfer properties gain `restartGeneration`; URLs/passwords remain ephemeral. No new ViewModel retry parameter is required. |
| Form input | New field-aware lexical `FormEntry.Input` and shared parser; existing programmatic Text/Check/Radio alternatives remain supported. No Room migration. |
| Processing internals | New pure size/budget/join/restart/display-coordinate helpers and process-wide BitmapWorkGate. Public PDF crop/stamp APIs retained. Normalized crop/image/note coordinates now consistently address the current visible oriented CropBox; saved jobs on ordinary uncropped pages keep their former mapping. Internal image-placement primitives support real-PDF geometry tests without Android raster emulation. |
| Printing APIs | New PrintStore/PrintEngine boundaries, print models/options/history/presets, PrintViewModel/repository/composer/adapter and Print route/actions. Print data lives in a new `jumlo_print_v1` Preferences DataStore with versioned serialized data, separate from processing jobs. |
| Existing preferences | Additive `output_names_v1` map exposes output display rename without mutating historical bytes or publication journal names. Existing favorite and general settings remain. |
| Database | Existing Room version and existing 1→2 migration remain; no new Room schema migration is introduced by these upgrades. |
| Dependencies and external APIs | **No new library dependencies or dependency-version changes** in this implementation delta (`app/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts` show no delta against the implementation baseline). Reuses Android printing, PDFBox Android, Compose, Room, DataStore, CameraX/ML Kit and existing codecs/runtime boundaries. No new cloud service/API or credential persistence is introduced. |
| Tests | Adds host/JVM policy, real-PDF, session/ViewModel and authored Android image/adapter regressions. JVM-only PointF value arithmetic shim corrects mock Android constructor behavior for PDFBox fixture geometry; it is not renderer or codec emulation. Final integrated test/build/lint outcome belongs to root's execution evidence. |

The changes are additive at the main persistence boundaries. The existing application ID, version defaults, Room database version 2, migration path, `JobRequest.schemaVersion = 1` and `WorkspaceDraft.version = 1` remain. Old drafts and organizer requests that omit `pageEdits` still use their previous page-order and rotation fields. The new page-instance list is authoritative only when present and nonempty.

The original positional and trailing-lambda `WorkspaceScreen` call remains available through a compatibility overload. The print-enabled overload adds its callback explicitly. `QuietTonalButton.enabled` is a defaulted trailing parameter. New print models, store and engine interfaces are isolated under the printing package. Existing PDF crop/stamp method signatures remain; their normalized coordinates now correctly address the displayed crop frame on rotated or previously cropped pages. Ordinary uncropped pages retain their prior coordinate convention.

The print store is a separate Preferences DataStore (`jumlo_print_v1`). Existing processing rows are not converted into printer jobs. Retry-generation metadata defaults to zero when absent. Output rename is an additive display-name map in existing preferences (`output_names_v1`); it preserves canonical payload names, hashes and publication journals. Single-output share/open actions and new exports use the chosen display name. An existing public mirror keeps its physical filename until exported again; batch sharing that mirror also retains its existing name.

The baseline archive and all 493 extracted baseline files were rechecked and remain unchanged. The updated source is a complete handoff produced from the isolated working copy. Its `CONTENTS_SHA256.json` records every included file, mode, size and digest and the SHA-256 of the original archive. Packaging verifies member names, CRCs and all recorded content hashes. Build directories, caches, local SDK configuration and signing keys are excluded.

Evidence: the API/data table above; `WorkspaceScreen.kt`, `QuietButtons.kt`, `WorkspaceDraft.kt`, `OperationContracts.kt`, `JobStore.kt`, `PrintRepository.kt`, `UserPreferencesRepository.kt`, `scripts/package-source.py`, and `verification/printing-upgrade/original-preservation-check.json`.

## 8. Verification and phase checkpoints

| Check | Result | Evidence in `verification/printing-upgrade/` |
| --- | --- | --- |
| Fresh baseline | Android compile failed; host 180 pass / 1 fail; auxiliary 17 / 20 pass. | Baseline audit reports and retained baseline logs. |
| Phase 1 checkpoint | Debug APK, 125 unit tests and lint passed; lint 365 warnings plus one hint. | `phase1-build.log` and result JSON. |
| Coherent integration | Debug APK, 193 unit tests, lint and device-test APK compilation passed. | `phase6-coherent.log` and result JSON. |
| Complete Phase 6 checkpoint | Debug/release builds, 195 unit tests, lint and device-test APK compilation passed before the final Files-state patch. | `phase6-final.log` and result JSON. |
| Final delivery validation | Following a clean rebuild, debug APK and optimized unsigned release APK passed; device-test APK compiled. Final gate elapsed 196.17 seconds. | `package-final-validation.log` and result JSON; clean attempt and memory recovery retained. |
| Final Gradle unit tests | 195 tests across 38 suites; 0 failures, 0 errors, 0 skipped. | `junit/TEST-*.xml`, `FINAL_VERIFICATION.json`. |
| Final lint | 0 errors; 386 warnings. Warnings remain and are not all inherited. | `lint-results-debug.xml`, `lint-results-debug.html`. |
| Final host verification | 181 core tests and all 20 auxiliary scripts passed. Elapsed 22.57 seconds. | `package-host-final.log` and result JSON. |
| Focused PDF/core regressions | 35 tests passed; includes saved-PDF geometry, forms and processing policies. Overlaps integrated JUnit coverage. | `processing-focused-tests.log`. |
| Final APK inspection | 33,163,899 bytes (33.16 MB); signature and ZIP alignment pass; all inspected ELF64 load alignments at least 16 KiB; no known forbidden media markers. | `package-apk-inspection.json`, signature/alignment logs. |
| Baseline preservation | Original archive hash unchanged; all 493 baseline files unchanged. | `original-preservation-check.json`. |
| Device/platform qualification | Instrumentation, physical printing, TalkBack, camera and frame-rate measurements not executed. | Explicit limits in this report and review notes. |


The integrated unit suites include real `PrintViewModel` tests for inspection, settings/session restoration, cancellation/error behavior and persisted state, plus tests for `PdfPageEditorSession`. Pure print tests cover crop/rotation/UserUnit geometry, scale and margins, logical page ranges, preset/history policy and callback completion policy. Saved-PDF fixtures inspect actual document boxes, page instances, annotation dictionaries and content operators. They do not substitute for Android renderer pixels.

The host suite's 181 tests and its 20 auxiliary scripts cover overlapping and additional code paths. They are reported separately from Gradle JUnit results; their counts must not be added and described as unique application tests. Auxiliary scripts include static source contracts, archive/SQL/network policies and tool-specific fixtures, not 20 end-to-end UI tests.

New Android instrumentation includes real print-adapter layout/subset-write tests using file descriptors and image-processing reliability cases. Existing stale Android-test fixtures were corrected so that the test APK compiles. No connected device or emulator was available to execute it. In particular, callback-gate JVM tests and source review do not establish actual adapter cancellation timing on Android.

### Checkpoint interpretation

Phase 0 was recorded before source edits. Phase 1 was built and tested in a separate checkpoint. Phases 2–5 were implemented incrementally by area and integrated together; their intermediate build attempts exposed compiler, fixture and environment issues that were fixed. The early build-cache failure involved a transient generated-file race, and a later attempt observed tests against main classes from an earlier concurrent source snapshot. The package check then found a stale release `R.jar`: the new strings were present in source and `R.txt` but missing from the older compiled resource class. Generated outputs were rebuilt from clean source with incremental Kotlin compilation and the build cache disabled. One older static assertion was updated to ignore whitespace in the now-tested PDF image-placement call.

The clean attempt compiled the app and device-test APK and passed all 195 unit tests, then its Gradle daemon disappeared during R8 optimization. The environment recorded an out-of-memory kill under its 8-GiB limit while an idle Kotlin compiler process retained about 2.8 GiB. That task-owned compiler was stopped; the required gates resumed using one worker, a 3-GiB Gradle heap and in-process Kotlin. Failed attempts are retained as failures. The final validation result and matching inspected APK hash establish the delivery evidence.

Lint completed with zero errors, but warnings remain. The final count and report path are in the table. The earlier Phase 1 checkpoint already had 365 warnings plus one hint; the final warnings are not all claimed to be inherited. No broad dependency migration or warning suppression was used to manufacture a clean report.

### Reproduce on a configured workstation

Use JDK 17, Android SDK platform 36 and Build Tools 36.0.0. Normal builds use the existing standard configuration; no new dependency switch is required.

```bash
./gradlew --no-daemon --console=plain --no-build-cache \
  --no-parallel --max-workers=1 -Pkotlin.incremental=false \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs=-Xmx3072m \
  :app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebugAndroidTest :app:assembleRelease
bash scripts/verify-host.sh
python3 scripts/inspect-standard-artifact.py \
  app/build/outputs/apk/debug/app-debug.apk --output apk-inspection.json
```

The supplied source also retains the benchmark module and existing device/CI tasks. The optional `-PextendedMedia=true` build remains a separate opt-in runtime with its existing dependencies and qualification requirements; it is not the delivered standard APK.

Delivered debug APK SHA-256: `aaf6084b6e2cbc9f73254165a1209a646dd0d5052faafaba98ba179c656cb825`.

## 9. Installation and remaining device qualification

Install the debug APK on an Android 8.0/API 26 or newer device. It uses `com.jumlo.app.debug`, so it can coexist with the production package. It does not automatically take over an existing production installation's private data. The production application ID and version defaults remain unchanged in source. The debug package uses debug signing; publishing a release requires the owner's signing configuration and release process.

To exercise the new workflow, import a PDF or image through Files, then use Print from its action menu or the bottom navigation. For edited output, open Organize Pages, select or duplicate/rotate/reorder pages, and choose Print selected. Adjust paper, orientation, scale, margins and range in the composed preview. Submit to Android's print dialog, choose a printer or Save as PDF, set copies, and inspect the final service-specific preview. Recent documents, favorites, presets and history remain available on the Print screen.

The remaining qualification is concrete:

- Run the authored instrumented suites on API 26 and API 36. Include repeated adapter writes, app-background cancellation, rotation, process death and a long selected-page list.
- Use Android Save as PDF and at least one real print service/printer. Compare margins, orientation, source/custom paper, copies, duplex support, grayscale and selected duplicate pages with the preview. Test service removal, no available printer, cancellation and failed-job retry.
- Exercise Arabic and English, RTL, light/dark/dynamic colors, reduced motion, maximum supported font scaling and TalkBack/Switch Access. Verify focus order, action naming, actual touch bounds and edge auto-scroll on a device.
- Exercise CameraX/ML Kit capture, EXIF-heavy images, provider permission loss, low storage, large but bounded image jobs and failed download recovery.
- Measure frame pacing and memory on designated hardware. Stable page identities, bounded decoders, off-main composition and lazy UI are implementation measures; a 60fps claim requires measured frames.

These are unexecuted hardware/platform checks, not a request for another approval before the completed source handoff. The implementation and build evidence are delivered now. Unrelated gated catalog capabilities and their backend limitations remain explicitly listed in Section 6.

## 10. Evidence and primary references

Within the source archive, start with `docs/upgrade/UPGRADE_REPORT.md`, then the three baseline audit files, the feature inventory and independent review. `verification/printing-upgrade/` contains phase/build result JSON, logs, JUnit results, lint summary/report, host verification, original-preservation evidence and APK inspection. Historical reports elsewhere describe earlier revisions and do not override this handoff.

- [1] [Android Developers: custom document printing](https://developer.android.com/training/printing/custom-docs) (checked 20 October 2026). Used for layout/write lifecycle, cancellation and printer-aware relayout behavior.
- [2] [Android Developers: PrintManager](https://developer.android.com/reference/android/print/PrintManager) (checked 20 October 2026). Used for the app-facing submission and job-list boundary.
- [3] [Android Developers: PrintJob](https://developer.android.com/reference/android/print/PrintJob) (checked 20 October 2026). Used for service states, cancellation and restart behavior.

Original input SHA-256: `44e15f1a54aa5cc721c92ad08d449d3a327e889d14255df8fdd6ed12d31e37f2`.

Report date: 20 October 2026. Evidence labels throughout distinguish implementation, executed tests and remaining device qualification.
