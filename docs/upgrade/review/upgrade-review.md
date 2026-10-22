# Independent upgrade review

Reviewed 2026-10-20 against `docs/upgrade/IMPLEMENTATION_PLAN.md` and baseline commit `1f5f8d5`, including untracked source/test files. This review inspected the current source without changing app files or running Gradle. Other agents continued implementing fixes concurrently; the findings below describe the inspected implementation and explicitly distinguish pending fixes from verified resolutions. Line numbers identify the reviewed source and may move with those fixes.

## Targeted fix re-review (2026-10-20)

**Final targeted disposition: R1–R9 are resolved in the reviewed source. No remaining source blocker was established within this review scope. Integrated build/test results remain root-owned, and actual Android/device execution qualifications below remain open.**

The original findings below remain as the review trail. The following status supersedes their initial pending labels. This follow-up inspected only the requested fixes and their regression coverage; no Gradle or device execution was performed by the reviewer.

| Finding | Re-review status | Source and evidence |
|---|---|---|
| R1 camera-image decode | **Resolved in source** | `PrintImageDecoder` now samples independently of the exact processing decoder; `PrintImageBudget` limits decoded pixels and the composer draws using original `PrintPageInfo` physical dimensions. The 4032×3024 policy fixture asserts sample 4. Actual Android bitmap decode/EXIF pixels remain device-unverified. |
| R2 stale image DPI | **Resolved in source** | `PrintViewModel.edit` now decides DPI reinspection from source MIME even when metadata is absent; canceled inspection cannot publish. The new `selectingImagePresetBeforeInspectionUsesTheNewDpi` regression checks both the displayed DPI and computed physical width. |
| R3 unbounded print-source lifetime | **Resolved for reported orphan/eviction cases in source** | `PrintSnapshotPolicy` protects in-flight copies with leases, limits inactive history while retaining known active jobs, and deletes unreferenced owned UUID snapshots. `PrintRepository.snapshotReferences` now includes history, recents/favorites and the active durable session. Policy tests cover concurrent cleanup, canceled unregistered copy, referenced reuse, original preservation and active-history retention. The root-flagged missing recent/favorite reference problem is corrected in the inspected source. See R9 below for a separate related recent-item identity defect found during this targeted review. |
| R4 numeric keystroke rewriting | **Resolved in source** | `NumberField` retains its local edit buffer and tracks its last applied numeric value; only genuine external changes synchronize formatted text. The parser also accepts Arabic/Persian digits and rejects nonfinite values. Sequential keystroke behavior still needs Compose/device execution. |
| R5 inverse zoom applied twice | **Resolved in source** | Margin-handle deltas now map directly from local preview dimensions to paper points, without the extra division by zoom. Gesture cancellation also restores initial margins. Actual gesture dispatch remains device-unverified. |
| R6 watermark/crop/rotation/UserUnit | **Resolved in source; real-PDF fixture evidence inspected** | `PdfDisplayCoordinates` supplies the inverse physical visible-frame transform. Watermark-image placement and `stampText`/page numbers both use it. `PdfOverlayCoordinatesTest` reads operators from saved PDFs and checks all four rotations, offset crop, UserUnit=2, intended center, image orientation and physical size, page-number baseline/font, width cap and style rotation. Independently read `processing-focused-tests.log`: **OK (33 tests)**. This verifies content geometry, not Android font rasterization/viewer pixels. |
| R7 oversized page-edit saved state | **Resolved in source** | `PdfPageEditorSession` holds history and selection in a route-scoped ViewModel; `PageEditHistory` is no longer serializable and no full history is put into `rememberSaveable`. Only small focus/scroll state remains saveable. Three tests cover retained ViewModel state across recomposition/configuration, re-seeding current pages after process death, and releasing the prior document session. Current edits continue through the existing debounced durable draft recovery. Undo history intentionally starts fresh after process death; no device lifecycle execution was observed. |
| R8 optional compression memory fallback | **Resolved in source** | `DownloadOperationHandler` now falls back only for the expected memory/unrepresentable-dimension preflight errors, in addition to unmet compression bound; cancellation and malformed/unsupported inputs still propagate. No end-to-end downloaded-image device test was observed. |

### R9 — P2: renamed outputs can create duplicate Print recent-list keys

**Locations at re-review:** `PrintRepository.kt:93–96`; `PrintScreen.kt:133`.

Recent records are deduplicated by complete `PrintSource` equality, which includes the display name. Their LazyColumn key uses only path and page-list hash. Print an output named A, rename it to B using the new Files rename action, then print it again: the canonical payload path and page list remain the same, but two differently named recent records are stored. Rendering Print home supplies duplicate lazy-item keys and can fail. Use the same stable source/ordered-page identity for both deduplication and UI keys, preserving favorites while updating display metadata. Root and print_flow were notified immediately. **Final status: resolved in source.** `PrintSourceIdentity.documentKey` now hashes path plus ordered page references while excluding display name/MIME; both repository deduplication and the LazyColumn use that key. Reopening a renamed output replaces its metadata and preserves its favorite state. The new `renameKeepsRecentIdentityButOrderedPageChangesDoNot` regression checks rename-stable identity and order-sensitive identity.

### Remaining verification limits after fixes

`PrintExecutionPolicyTest` checks concurrent terminal callback gating. The final source also adds **two actual adapter instrumentation tests** in `app/src/androidTest/java/com/jumlo/app/printing/JumloPrintAdapterTest.kt`: successful layout with repeated subset writes checks logical ranges, selected duplicates/rotations, real PDF page counts and closed destination descriptors; write-before-layout checks one failure and no rendering. These tests invoke the real adapter with a fixture composer. They were read during review but **not run by this reviewer or evidenced on a device**. Root may compile them as part of the final gate; compilation alone is not execution. Actual adapter cancellation remains covered only by source review and the pure callback gate policy test, not an instrumented cancellation case.

Root's bitmap-lifetime concern is corrected in source: published PageImage bitmaps are no longer explicitly recycled from `awaitDispose`; undisplayed intermediate cleanup stays in the renderer. No hardware, print-service, TalkBack, gesture or frame-rate passing claim follows from these source checks.

## Original findings and initial disposition

### R1 — P1: ordinary camera images cannot be printed under the new exact-decode contract

**Locations:** `app/src/main/java/com/jumlo/app/printing/PrintPdfComposer.kt:118–132`; `app/src/main/java/com/jumlo/app/data/processing/BitmapTransforms.kt:40–65`.

The print composer calls `BitmapTransforms.decode(file, 32L*1024*1024)`. The processing upgrade changed that shared decoder to full-resolution, no-subsampling decode and requires the decoded bitmap plus EXIF transform copy to fit the supplied budget. A standard 4032×3024 JPEG requires 48,771,072 bytes (about 46.5 MiB), or about 93 MiB while applying an EXIF rotation. Inspection succeeds because it only reads bounds, but every print preview and adapter write fails for such a file. This is a common input, not an extreme-size case.

**Fix:** give printing a bounded sampled decoder, retaining original pixel dimensions/image DPI as physical layout metadata. Account for the decode plus any EXIF copy; do not change the exact processing decoder's contract to repair printing.

**Status:** reported to root and print_flow; fix pending independent re-review.

### R2 — P2: restored image DPI can disagree with actual print size

**Locations:** `app/src/main/java/com/jumlo/app/printing/PrintViewModel.kt:60–72,86–93,149–154`.

`inspect()` captures options before launching. `retryJob()` calls `open(entry.source)` and then `edit(entry.options)`. Opening captures the previous image DPI and clears `info`; editing the history options sees `info == null`, so the DPI-change branch does not restart inspection. The resulting `PrintPageInfo` therefore encodes physical dimensions using the old DPI while the controls display the restored DPI. For example, reuse a completed image printed at 72 DPI/ACTUAL while current preferences use 300 DPI: the reused image prints at 72/300 of the intended physical size. Applying a preset during an initial inspection has the same race.

**Fix:** bind inspection results to source and image DPI and restart/invalidate when either changes, including while metadata is absent.

**Status:** exact scenario supplied to print_flow; fix pending independent re-review.

### R3 — P2: print-source snapshots accumulate without a lifetime policy

**Locations:** `app/src/main/java/com/jumlo/app/printing/PrintRepository.kt:40–59,66–83`; `app/src/main/java/com/jumlo/app/printing/PrintViewModel.kt:121–134`.

Each print from inbound/results copies the original to a fresh durable `printing/sources/<UUID>.bin`. History is capped at 100 records, but no code removes the corresponding unreferenced snapshots. A canceled preparation after a copy, a failed handoff/record, or history eviction leaves these files indefinitely. Existing app storage accounting/cleanup does not include this new directory. Reprinting large documents repeatedly can consume storage even though the displayed history is bounded.

**Fix:** retain snapshots referenced by history/recent/favorites or active spooler/in-flight submissions, and collect orphaned/evicted snapshots after those leases end. Cleanup must never remove a source needed by an active adapter write or retry.

**Status:** print_flow plans reference-aware cleanup and in-flight leases; fix pending independent re-review.

### R4 — P2: numeric print fields rewrite each keystroke

**Location:** `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt:316–318`.

`NumberField` uses `remember(number)` for its edit string and calls `onValue` immediately on each valid keystroke. The callback changes `number`, recreating the state as `%.1f` while the user is still typing. For a margin field cleared by the user, typing `1` changes the string to `1.0`; the next `2` becomes `1.02` and is immediately reformatted again. Normal multi-digit entry is disrupted; pasting the final value is an accidental workaround.

**Fix:** preserve a local edit buffer while focused, normalize on commit/focus loss, and synchronize genuine external numeric changes without rewriting the user's own input.

**Status:** sent directly to print_flow; fix pending independent re-review.

### R5 — P2: zoomed print-margin dragging applies inverse zoom twice

**Location:** `app/src/main/java/com/jumlo/app/printing/PrintScreen.kt:265,279–289`.

The margin-handle pointer input belongs to a child of the scaled `graphicsLayer`. Pointer coordinates for that child are already in the child's local coordinates after the ancestor transform. Dividing the resulting delta by `zoom` a second time makes the margin lag the gesture: at 2× zoom it moves only half the intended local distance.

**Fix:** map the local delta directly from preview dimensions to paper points, removing the extra inverse-zoom factor. A device/Compose gesture test should cover 1× and 2× zoom.

**Status:** root flagged for inspection; hierarchy reviewed and finding sent to print_flow. Fix pending independent re-review.

### R6 — P2: the upgraded watermark preview does not match cropped/rotated PDF output

**Locations:** `app/src/main/java/com/jumlo/app/ui/workspace/VisualWatermarkEditor.kt:179–198`; `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt:583–595,615–620`; frame contract at `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt:827–841`.

The new preview places watermarks and page numbers in the rendered, oriented CropBox using the displayed page's point dimensions. Backend stamping still calculates position, size cap and orientation from raw unrotated MediaBox coordinates. For a media box 600×800 with crop box `(100,200,300,400)`, rotation 90°, and center `(0.1,0.2)`, the preview shows the watermark inside the page but the backend places its center at raw `(60,640)`, outside the crop box. Existing page rotation also changes the apparent watermark orientation. UserUnit affects physical font dimensions. This is a confirmed remaining Phase 3 accuracy requirement, even though the underlying backend limitation predates the updated preview.

**Fix:** use a shared inverse transform from displayed physical CropBox coordinates into raw PDF page coordinates for both watermark images and page-number text, preserving intended physical point sizes and visible rotation.

**Status:** assigned to processing_fixes; exact frame contract and fixture supplied. Fix pending independent re-review.

### R7 — P2: page-edit history can exceed the Activity saved-state transaction budget

**Locations:** `app/src/main/java/com/jumlo/app/ui/workspace/PdfPageEditor.kt:82–85`; `app/src/main/java/com/jumlo/app/ui/workspace/PageEditHistory.kt:15–28`.

The complete current page list plus up to 30 complete undo lists is JSON-serialized into `rememberSaveable`. The bound limits snapshot count, not serialized size. Using the exact three entry fields and even short legacy IDs, a 1000-page document with 30 undo snapshots produces 2,129,050 JSON characters. Android saves this value into the Activity instance-state Bundle; it is too large for safe Binder state transfer and can cause a lifecycle failure on backgrounding or recreation. UUID-backed duplicates are larger still.

**Fix:** retain large undo history outside the Bundle (a retained ViewModel and, if required, a durable history checkpoint) or use a strict byte-bound/delta representation. Persist only a small restoration key in saved state. Keep the durable current page edit list intact.

**Status:** reported to root, which will assign page_editor after this review completes. No fix reviewed.

### R8 — P2: optional download-image compression now fails instead of preserving a large valid original

**Locations:** `app/src/main/java/com/jumlo/app/data/processing/DownloadOperationHandler.kt:256–281`; `app/src/main/java/com/jumlo/app/data/processing/BitmapTransforms.kt:40–65`.

The optional `IMAGE_COMPRESSED` download path falls back to publishing the original only for `ImageSizeBoundException`. The new exact decoder can instead reject a fully downloaded valid image with `IMAGE_MEMORY_BUDGET_EXCEEDED` before compression. This exception escapes to a failed item, despite the documented optional-compression/original-preservation behavior. The old sampled decoder did not reject these inputs for that reason.

**Fix:** treat the specific expected image-memory-budget refusal as a compression-unavailable fallback, delete any candidate, and publish the verified original. Preserve cancellation and do not indiscriminately swallow malformed-image or other failures.

**Status:** reported to root and processing_fixes; fix pending independent re-review.

## Initial verification and requirement gaps (superseded where noted above)

1. **No print-adapter behavior tests in the inspected tree.** Searches found geometry/range policy and ViewModel tests, but no test invoking `JumloPrintAdapter.onLayout`/`onWrite`, callbacks, destination descriptors, cancellation, overlapping/repeated writes or layout replacement. The implementation plan explicitly requires adapter tests and cancellation/retry verification. Source inspection shows per-write temporary files, requested-page mapping and atomic callback-delivery intent, but this is not execution evidence.
2. **No device behavior claimed by this review.** Hardware printing, print-service discovery, framework callback lifecycle, TalkBack, gesture dispatch and rendering frame rates were not exercised. Source-level reasoning about gesture geometry should be backed by the available device test gate if one can be provisioned.
3. **Pending fixes must be re-reviewed and included in root's integrated build/tests.** The review did not run a parallel Gradle invocation. Straightforward compiler failures are outside this report because root and implementation agents own them.
4. **Bitmap display lifetime concern is tracked by root.** Root independently identified immediate recycling of a bitmap already published to Compose in `PageImage.awaitDispose`; root asked print_flow to fix it. This review did not duplicate that as an independent finding or claim it resolved.

## Areas inspected without an additional confirmed finding

- The print vector path replaces PDFBox import rotation with the shared crop-origin, rotation and UserUnit matrix, clips to the printable paper area and preserves duplicate source references. The pure corner/range tests align with that model; actual generated output remains an integration/device verification task.
- Adapter writes capture a layout snapshot, compose the requested logical page positions, report compact requested ranges and use distinct temporary files. The source includes completion handling for cancellation before coroutine entry. No further concrete defect was established beyond the missing execution tests.
- Per-instance page rotation/duplicate/delete/reorder and undo/redo use stable IDs and immutable snapshots. Legacy settings retain additive defaults, and organize execution uses the new instance list with a legacy fallback.
- Output rename stores a display alias; canonical payload paths for printing and reuse are re-resolved through the output store. I did not establish a rename-induced payload-path failure.
- Initial external shares await initialization, select a MIME-compatible tool, and use immutable imported sources. Notification routing validates the job ID and queues navigation. No confirmed new MainActivity/intake or draft initialization race was found.
- No claim is made about gated OCR/web/Drive/vault capabilities: they were intentionally outside this printing-upgrade review.

## Scope discipline

This report contains actionable correctness/lifetime/interaction findings and confirmed validation gaps. It does not include cosmetic preferences, speculative device failures, commits, PRs or publication actions. The report itself is the only file written by the reviewer.

## Final packaging pass: crop, image/signature, note and public screen API

A final narrow read-only review inspected `PdfDisplayCoordinates`, `PdfBoxBackend.setCrop`, `stampImage`/`stampImageObject`, `addTextAnnotation`, and the source-compatible `WorkspaceScreen` overload after the geometry follow-up. **No concrete output-coordinate or API blocker was found in these changes.**

- `normalizedRectangle` converts the normalized top-left selection into physical displayed-page coordinates and maps all four corners through the inverse CropBox/rotation/UserUnit transform. `setCrop` replaces only the CropBox, leaving MediaBox, intrinsic rotation and UserUnit intact. A whole-visible-page selection preserves the existing CropBox. This intentionally changes previously incorrect behavior on already cropped/rotated inputs; ordinary uncropped, unrotated page coordinates retain their prior interpretation.
- Image/signature placement fits the image aspect ratio inside the selected visible physical rectangle, then applies the display-to-page matrix before drawing. This retains intended upright orientation and dimensions across intrinsic page rotation; both signature and image-overlay handlers call this same public method.
- Unicode notes map their physical 20-point icon rectangle through the same frame and explicitly associate the annotation with its page. The prior lower-left anchor convention on ordinary pages is preserved. This verifies note anchor geometry, not popup/icon rendering in every PDF viewer.
- The ten-argument `WorkspaceScreen` overload exactly matches the baseline public parameters, including the final `onExecute` callback, preserving positional and trailing-lambda callers. It delegates with named arguments to the print-enabled overload, whose additional `onPrintPages` parameter is required; therefore the legacy call shape does not become ambiguous.

The new real-PDF tests in `PdfOverlayCoordinatesTest` inspect saved image operators and note rectangles for all four intrinsic rotations with an offset CropBox and UserUnit=2. They check image centers, aspect ratio, physical dimensions/baseline direction, Unicode note contents/page linkage, whole-visible crop preservation and exact selected crop bounds. The focused execution log was independently read after this update and reports **OK (35 tests)**. This reviewer did not run Gradle or execute device tests. No source files were edited in this final pass; only this report was updated.
