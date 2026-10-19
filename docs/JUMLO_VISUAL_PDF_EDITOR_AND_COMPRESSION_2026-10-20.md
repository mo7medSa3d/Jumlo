# Jumlo — Interactive PDF & Image Editing + Quality-Aware Compression

Date: 2026-10-20. Target: Android Jetpack Compose (the repository has iOS icon assets, not an iOS application).

## Audit and design references

Reviewed existing workspace navigation, `VisualDocumentEditor`, `VisualImageSettings`, the PDFBox image compressor, `PdfCompressionPolicy`, and production `WorkspaceDraft` serialization. Benchmarked common scan/PDF interaction patterns against the documented mobile workflows of CamScanner and Acrobat: draggable crop handles, automatic page boundary detection, page thumbnail operations, direct visual placement, and in-context tools.

Changes build **on top of** the newer interactive-crop main commit; they preserve its geometry/test code and do not roll back concurrent edits.

## Root causes of insufficient PDF savings

1. Previous `compressPdfGuaranteedSmaller` returned the **first** candidate smaller than the original, not the smallest viable candidate across presets.
2. The prior bitmap safety cutoff skipped high-resolution scanned photos whose full ARGB footprint exceeded 32 MiB; the original compressed JPEG could pass through untouched.
3. The backend only enumerated direct page image XObjects. Scans nested in Form XObjects could be missed.
4. Some PDFs are already efficient or mostly text/vector data. A JPEG quality value (e.g., 50%) sets visual encoding fidelity, **not** a target percentage reduction of the file's byte size.

## Implemented features

- Shared, safe direct-manipulation editor for document/scan image cropping, PDF cropping, PDF signatures, annotation placement, and **new Add Image to PDF** workflow. Real source previews, draggable corners and body, reset, aspect presets, page navigation, and all-page/current-page crop options.
- Add Image to PDF accepts one imported PDF and one image, regardless of selection order. Selection is verified before execution. The image preview renders the actual imported media, not a fake asset.
- PDF signatures can be drawn from the main editing screen when not already imported. Existing page-organizer drag/reorder/undo, scan auto-corner detection, and PDF processing remain unchanged.
- Interactive image controls: size presets and slider, real brightness/contrast/saturation presets (Original, Document, Vivid, Grayscale, High Contrast), and live GPU preview with Before/After comparison.
- Four human-readable compression presets: Best Quality, Balanced (default), Small File and Strong. Exact numeric control remains optional in Advanced. Built-in haptics and both Arabic/English translations.
- PDF compression profiles compare **every valid candidate** and keep the smallest. Decode of large JPEG/photo XObjects uses safe subsampling; nested Form XObjects are traversed. Protected alpha/mask and monochrome assets are not JPEG-transcoded. Files are re-opened to validate page count and accepted only if smaller.
- No third-party runtime or remote service was introduced; existing bitmap/PDFBox resources and handlers are reused.

## File integrity and limitations

- Original source bytes remain immutable. Candidate PDF must reopen and match page count before publication; failed/oversized candidates are discarded.
- Text/vector elements remain preserved rather than rasterizing the whole document. Image recompression is lossy, depending on the chosen preset.
- Extreme compression cannot promise a fixed percentage saving without lowering image fidelity or rasterizing vector content. Already optimized PDFs may be published at original size; UI must not describe such outputs as reduced.
- Currently disabled security-sensitive PDF Redaction remains disabled in standard releases.
- iOS native screens are not present in this repository and have not been implemented.
- Existing PDFBox PDF core test uses a real image+text fixture. Added regression tests for compression profile selection, safe subsampling, candidate acceptance and image-overlay settings.
- **Native build/device verification remains pending** until a GitHub Actions runner can execute Gradle. Previous Actions jobs failed *before allocating a runner* (`runner_id=0`, zero executed steps), which is not evidence of either a successful or failed app compilation.

## Suggested next qualified features (not silently shipped)

- Render-side pixel-diff screenshot tests for editor gestures and Before/After color matrix.
- Incremental embedded-image extraction with original-format retention, multipage thumbnail performance on large PDFs, document deskew, and on-device scan shadow removal with deterministic benchmarks.
- Compression image fidelity metrics (SSIM/PSNR or OCR readability), and device profiling on 4–8 GiB Android phones for scanned 50–100 MiB PDFs.

## Validation gates

1. `bash scripts/verify-host.sh` — source-contract checks and pure Kotlin host checks.
2. `bash scripts/verify-native.sh gate` — Debug/Release APK, JUnit, lint and AAB checks (requires Android SDK and working runner).
3. Device QA: gesture positioning, zoom, page switching, image transparency, Arabic RTL, correct saved exports, scanned multipage PDFs, corrupted/encrypted PDFs and low-memory stress.

## Finishing pass: user-facing reliability and quality

- Visual crop/image overlay/annotation editor gains bounded **Undo / Redo** history (25 edits), with genuine changes to the existing validated coordinates; reset and aspect-ratio presets are reversible.
- Main resize presets say **Small / Medium / Original / Large** in both locales; percentage sliders continue to drive real processing values, but raw numbers are hidden from the primary UI. Advanced retains exact inputs.
- The PDF compressor reports monotonic, overall progress across all profile attempts, rather than resetting its progress for each attempt.
- Signature PDF/image source order is normalized by MIME type as already done by Add Image to PDF; one PDF and one image remain mandatory.
- Subsampling decodes use rounded-up pixel estimates to remain within the memory budget.
- Files lists display actual percentage saved only when the persisted `originalSizeBytes` is greater than the verified output size. Already-optimized files show no invented savings.
- Two additional bilingual resource labels and regression contracts document and protect these behaviors.

**Verification scope:** source-contract checks and Kotlin core unit tests cover logical invariants, but visual placement accuracy, actual 50% JPEG quality, PDF byte savings, 60fps motion, and packaging require Android execution. Do not claim those runtime qualities from source inspection alone.
