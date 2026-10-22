# Processing reliability implementation report

Implemented in `/workspace/scratch/929aae21eb23/audit-build/Jumlo-main`. No commit, no Gradle invocation by this agent, no gated capability activation, no dependency additions. Root owns complete Android compilation, broader suite execution, ViewModel/UI integration and localized messages.

## Changed existing production files

- `app/src/main/java/com/jumlo/app/data/processing/BitmapTransforms.kt`
- `app/src/main/java/com/jumlo/app/data/processing/ImageOperationHandler.kt`
- `app/src/main/java/com/jumlo/app/data/processing/DownloadOperationHandler.kt`
- `app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt`
- `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt`
- `app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt`
- `app/src/main/java/com/jumlo/app/data/jobs/JobStore.kt`
- `app/src/main/java/com/jumlo/app/model/OperationContracts.kt`

New production files:

- `app/src/main/java/com/jumlo/app/core/ImageWorkPolicy.kt`
- `app/src/main/java/com/jumlo/app/core/DownloadRestartPolicy.kt`
- `app/src/main/java/com/jumlo/app/data/pdf/FormInputParser.kt`
- `app/src/main/java/com/jumlo/app/data/jobs/BitmapWorkGate.kt`

Test additions/changes:

- `app/src/test/java/com/jumlo/app/core/ImageWorkPolicyTest.kt`
- `app/src/test/java/com/jumlo/app/core/DownloadRestartPolicyTest.kt`
- `app/src/test/java/com/jumlo/app/pdf/PdfStructureCoreTest.kt`
- `app/src/test/java/com/jumlo/app/pdf/PdfFixtures.kt`
- `app/src/test/java/android/graphics/PointF.java`
- `app/src/androidTest/java/com/jumlo/app/data/ImageProcessingReliabilityTest.kt`

## Download retry recovery

`JobSnapshot.downloadRestartGeneration: Long = 0` and trailing defaulted `ExecutionContext.downloadRestartGeneration: Long = 0` are additive. Existing serialized jobs retain generation zero; request schemaVersion remains one and Room schema is unchanged.

`retryFailed()` advances this generation for FAILED/PARTIAL direct download jobs (`dl_02`, `dl_03`), or WAITING_FOR_INPUT whose latest failure requires restarting byte state. PAUSED/INTERRUPTED, and waiting for a URL secret/network/Wi-Fi, retain the generation. This avoids resetting a valid partial on ordinary pause/resume or same-resource cold-session URL reentry.

The executing handler checks committed output keys first. Only after claiming the execution lease does it remove an uncommitted item's `.part`, `.properties`, and `.pending` when journal generation is older. Lease/cancellation checks run before each removal. New transfer metadata persists the generation before body bytes. Consequently a resumed attempt in the same generation retains its valid partial; an explicit failed retry starts fresh. Missing resume validators normalize to DOWNLOAD_RESTART_REQUIRED, so recovery follows the same user input/retry path. Changed URLs without explicit restart still cannot silently append to another resource.

Root's existing `retryFailed(jobId)` ViewModel call remains valid; no new UI API or worker forwarding layer is required.

## Image correctness and memory

- Removed sampling from the shared exact decoder. Convert and Compare now use actual source geometry or fail with an explicit resource error. EXIF dimensions are inspected and normalized; resize percentages/longest edge use the original EXIF-aware metadata, independent of decoded bitmap dimensions.
- Removed silent resize/join/PDF raster geometry clamps. Requested geometry/DPI is honored, otherwise resource preflight fails before the output allocation.
- Actual byte budgets include resident source, transformed output, EXIF normalization copy, logo, JPEG matte, and comparison row arrays. No artificial 16 MiB/4 MiB lower budget floors remain. Allocation math uses Long and checks Android's representable bitmap storage.
- Join/Stitch inspect source dimensions first, then decode and draw one source at a time beside the exact output canvas; the old resident list plus redundant copies is gone. The existing two-10x10 / 1000-byte join test now has a genuine combined-memory rejection path.
- Image/PDF handlers and downloaded-file transformations share one process-wide bitmap-work gate. Download network transfers remain concurrent; conversion acquires the gate after transfer.
- Images-to-PDF fit placement and fill clipping use root's `ImagePdfLayout` helper, matching preview geometry. Existing fill rendering policy is retained.

## Compression and target size

`encodeGuaranteedSmaller` now copies to its destination only when the actual candidate is positive and strictly below its exclusive bound. It never rounds tiny bounds up to 512 bytes. If no candidate can satisfy the bound, it removes the destination candidate and throws `ImageSizeBoundException(expectedMaximumBytes, actualBytes)` with stable IMAGE_SIZE_BOUND_UNMET code. The search reaches representable 1x1 dimensions instead of imposing a 16-pixel floor, stays at or below requested quality, and skips dimensions whose temporary memory does not fit.

Target Size uses the requested inclusive cap exactly (`targetBytes + 1` passed as the exclusive bound), without an unrelated original-file-size/1024-byte clamp. Normal Compress returns explicit UNCHANGED / IMAGE_NOT_SMALLER when no smaller encoding exists; the source is preserved and no over-bound candidate is published. Download's optional image compression retains the downloaded original if no smaller candidate exists.

Expected/actual byte values remain exception metadata rather than newly persisted ItemOutcome fields, by root's integration decision. The user receives localized honest target-unmet feedback and can see the requested size in workspace.

## PDF forms

`FormInputParser` preserves JSON strings, numbers and booleans as lexical input, and supports key=value consistently. Actual AcroForm type decides whether input is text, a checkbox boolean or a radio index. Numeric/boolean-looking values can therefore fill text fields; legacy programmatic FormEntry.Text/Check/Radio remain supported.

Flattening captures existing values, regenerates and verifies appearances, finds missing widget `/P` pointers through actual page annotations, preflights all widget bounds/matrices/appearances, paints every appearance, then removes widgets and AcroForm. It refuses unresolved widgets, XFA or unusable appearances without silently dropping form content. Output reopens and retains page count. The immutable original file is not overwritten.

Test fixtures now contain real checkbox appearance states and page annotation registrations. A narrowly scoped JVM-only PointF arithmetic implementation is necessary because Android's returnDefaultValues test jar discards constructor coordinates; pinned PDFBox uses those coordinates in appearance generation. This test shim implements vector values only and does not claim to emulate Android rendering/codecs. The previous permissive flatten scaling hid those zero-box artifacts.

## PDF organize

Uses the editor agent's additive `PdfOrganizeSettings.pageEdits` contract: nonempty list retains exact instance order, duplicates and per-instance clockwise relative rotations, added to source intrinsic rotation. IDs must be distinct/nonblank, source pages in bounds and rotations normalized quarter turns. Empty/missing list follows the unchanged legacy order/rotatePages path.

Pinned PDFBox importPage was inspected to confirm independent page-root dictionaries on each import. Each imported instance captures the source's natural rotation separately. Inherited page resources are explicitly cloned into the target to avoid the port dropping inherited resources. The source document is not rotated/mutated.

## Verification actually executed

Focused standalone Kotlin 2.0.21/JDK17 compile of production PDF backend/parser and relevant core policies, followed by real PDF fixture JUnit execution, passed **31 tests**. Log: `/workspace/scratch/929aae21eb23/audit-evidence/processing-focused-tests.log`.

Coverage includes actual stale-file/journal deletion, lease refusal, committed output preservation, pause/network/secret generation retention, strict size-bound arithmetic, caller quality ceilings, resident memory arithmetic, join geometry, independent duplicate rotations plus intrinsic rotation, inherited resources, JSON/key=value primitive text, existing-value appearance regeneration, missing widget page recovery and fail-before-removal. Existing structural PDF tests also passed in that focused invocation.

Four new Android instrumentation tests cover impossible real JPEG byte targets/source preservation, true decode budget refusal with exact geometry, EXIF-aware percentage resize and combined memory limits for adjust/logo/text watermark/resize. They have been authored but **not run on a device/emulator by this agent**. The pre-existing Android ImagePixelTest 1000-byte join case remains in place; it was not executed here.

`git diff --check` was run without whitespace errors. Root reported independent active core host suite passing 181/181; that is root's result, not this agent's execution. Whole application Gradle/build/device qualification remains root-owned.

## Limits and material follow-up

- Bitmap budget preflight bounds application-owned raster allocations; Android codec/native/PDFBox internal scratch buffers and driver overhead are not an exact whole-process heap guarantee. Very large exact images can now fail explicitly rather than produce silent low-resolution conversions. Tiled/region-based full-resolution codecs are not implemented.
- Actual Android codec, EXIF pixel rendering, PDF appearance rendering, UI output messaging, pause/process-kill HTTP fixtures and device memory pressure need instrumentation/device execution before full end-to-end qualification.
- Flatten supports static AcroForm appearances and rejects XFA. Source bytes survive failures, including output save/verification failure. Appearance planning precedes annotation/form removal; a failure after painting can mutate the temporary in-memory document, which is discarded without publishing.
- Completed derived-download outputs are preserved, although retry can need to download source bytes again before it discovers an already-created derived archive/page output.
- Error codes for root localization: IMAGE_MEMORY_BUDGET_EXCEEDED, IMAGE_DIMENSIONS_UNREPRESENTABLE, IMAGE_DECODE_DIMENSIONS_CHANGED, IMAGE_SIZE_BOUND_UNMET, INVALID_IMAGE_SIZE_BOUND, IMAGE_NOT_SMALLER, DOWNLOAD_RESTART_FAILED, INVALID_FORM_INPUT, FORM_APPEARANCE_UNAVAILABLE, FORM_WIDGET_PAGE_MISSING, FORM_VALUE_CHANGED, FORM_FLATTEN_UNSUPPORTED, INVALID_PAGE_INSTANCE, PDF_PAGE_IMPORT_FAILED, INVALID_RESTART_GENERATION. No new raw secrets or URLs are persisted.

## Independent review follow-up: visible PDF overlay geometry (P2)

Root assigned the cropped/rotated/UserUnit watermark parity issue after the initial report. Added `app/src/main/java/com/jumlo/app/core/PdfDisplayCoordinates.kt`, shared by backend watermark image placement and `stampText` (including page numbers). The helper constructs the visible oriented CropBox frame in physical points and its inverse affine transform to raw PDF coordinates. Consequently normalized centers and baselines follow the preview's top-left displayed frame, watermark angle follows the preview's clockwise direction, width caps use displayed crop width, and /UserUnit no longer scales the requested physical font/image size. Public stamping APIs and Android raster metrics are retained. An internal raster-image placement primitive allows genuine PDF operator tests without faking Android bitmap rendering.

Added `app/src/test/java/com/jumlo/app/pdf/PdfOverlayCoordinatesTest.kt`. Real saved PDF fixtures test all four intrinsic rotations, offset CropBox, UserUnit=2, watermark centers and orientation, physical image width/height, page-number baseline and physical 10pt font, crop-based width cap and clockwise style rotation. Tests parse actual saved `cm`, `Do`, `Tf`, `Td`, and `Tj` operators. Focused suite now passes **33/33**, same evidence log. This verifies content geometry, not Android font rasterization or viewer pixel equivalence. New geometry rejection code: INVALID_PDF_PAGE_GEOMETRY.

Also applied the independent-review download fallback correction: optional IMAGE_COMPRESSED now retains the completed downloaded original after expected IMAGE_MEMORY_BUDGET_EXCEEDED or IMAGE_DIMENSIONS_UNREPRESENTABLE preflight rejection, as it already does after an impossible size bound. Malformed/unsupported image errors and cancellation propagate distinctly. No device execution or additional Gradle invocation was performed.

## Final direct-edit parity correction

Root requested extending the visible-frame mapping beyond watermark/text. `setCrop` now interprets normalized selections against the current displayed CropBox, including intrinsic rotation and UserUnit; selecting the full visible rectangle preserves an existing crop rather than expanding back to MediaBox. `stampImage`/signature aspect-fit occurs in displayed physical coordinates before the inverse affine mapping. Unicode sticky-note anchors and their established 20pt physical rectangles use the same mapping; ordinary-page anchor behavior and annotation flags are retained. Public APIs remain unchanged. An internal image-object placement primitive isolates codec-independent saved-PDF geometry for JVM fixtures; actual imported-file decoding is still an Android qualification boundary.

Extended `PdfOverlayCoordinatesTest` with real saved-PDF image/signature placement and Unicode note rectangles, plus CropBox identity/selection cases for 0/90/180/270 rotations, offset crop and UserUnit=2. Tests inspect operators, aspect ratios, physical dimensions, annotation page/content/rectangles, crop values and retained MediaBox/rotation/UserUnit. Focused suite now contains **35 tests**. Final current result is in `processing-focused-tests.log`. No Gradle/device execution by this agent. These corrections supersede the earlier feature-status draft's raw-MediaBox limitations for these primitives; actual viewer pixels/annotation icon styling remain unqualified.
