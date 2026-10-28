# Tool UI specification

- WP04 transfer integrity: failed gzip/legacy partials restart cleanly rather than range-appending decoded bytes. User-visible progress/report still requires device validation.
**WP04 2026-10-28:** direct/batch downloads gain automatic mid-transfer recovery when the source supplies an HTTP validator. The user sees existing WorkManager progress; no new UI is claimed. `StreamTransferMidstreamRetryTest` provides three deterministic regression cases (NOT RUN until CI). Pause/foreground/device matrix remains outstanding.


> 2026-10-28 release gate correction: compact/release builds enforce 60,000,000 bytes and CI requests `lintRelease`; tool interaction evidence remains unchanged and device-qualification pending.


## Scope and evidence

This is the source-level contract for all **47 registry entries** after the UI implementation. **44 entries are available; three pre-existing security/privacy blocks remain disabled.** Source presence is not runtime qualification. Every device-dependent behavior in this document is **NOT VERIFIED** until the corresponding build, device test, screenshot or manual result is recorded in `TEST_REPORT.md`.

The catalogue is one keyed lazy grid organised into the brief's sections — Scan &
Capture, Organize PDF, Convert, Edit & Sign, Optimize, Protect, Download, Files —
each introduced by a full-width section header over its tiles. Pinned and
Recent rows sit at the top, fed by the persisted `quickActionIds` and
`recentToolIds`; a tool in either row is not repeated in its section. One search
field collapses the catalogue to a flat keyed list while searching. Tiles keep
the persistent usage ranking within a section, and any tool not listed in a
section falls into a trailing "All tools" group so it is never hidden. On phones
the normal grid has two columns; large font scaling reduces the column count.
Tool icons are unique official Rounded/filled/24dp vectors. The same icon appears
in the workspace header and participates in the navigation-owned shared
transition.

All previews described as real are produced from selected source files. No mock document image or invented output is used. A loading state is shown while decoding, OCR, hashing or compression measurement is in flight. Bitmap work uses bounded sampled previews and the existing serialized bitmap work gate where provided by the ViewModel/backend. Exact source files are retained until a separately validated job publishes an output.

Common empty states use `QuietStateCard` with an icon and one primary import/retry action. Workspace fields use tonal Material surfaces; advanced controls expand inline. Exact page ranges retain an adjacent visual page picker. Tool input parsing, not a preview, remains the final authority for whether execution is enabled.

## Shared ToolScaffold

Every tool page is wrapped in the shared `ToolScaffold` (`ui/components/ToolScaffold.kt`) so the flow is identical: a slim `QuietWorkspaceTopBar` (back, title, subtitle, badge, more) over a scrollable content area (the caller's drop/pick zone, editor and live preview), with a sticky bottom action bar carrying the primary actions and navigation-bar insets. `ToolProgressSheet` overlays progress above the preview without blocking it; `ToolResultScreen` presents the finished output with primary actions (Open / Share / Save / Print / Use in another tool) and a Close button. This is the single chrome every tool reuses; tool-specific content is supplied as the `content` and `bottomActions` slots.

## Cross-tool interaction implementation

- `TransformablePagePreview`: actual bitmap layers, tap selection, 48dp handle hit areas, single-pointer drag, two-pointer scale/rotation, corner scaling, rotation handle, and independent page pan/zoom after deselection. Coordinates remain upright page coordinates in LTR and RTL.
- `PdfWatermarkPreview`: text-point raster parity, selection frame/handles and independent page zoom. `VisualWatermarkEditor` adds the text/image/font/color/opacity/tile/placement/page controls for both PDF and images. Tile centers remain fixed; size/rotation still apply and single-placement controls appear only in Single mode.
- `SignaturePad` + `SignatureRenderer`: alpha PNG, colored/thickness-aware strokes, undo/redo, clear and persisted private signature library. `SignaturePlacementEditor` maps visible instances to production placements.
- `PdfPageEditor`: bounded vertical thumbnail grid, selection drag, reorder, rotation/delete, visible history and edge auto-scroll. `SourceOrderStrip`: first-page/image previews, source drag handles and accessible movement.
- `PdfFormEditor`: backend-discovered real widget coordinates and typed controls; `OcrWorkspacePanel`: real text/OCR, copy and export; `DuplicateResultsPanel`: real streamed hashes and thumbnail groups.
- `CollagePlacementPreview`: pointer hit regions come from production layout plans; moving a slot updates the actual input order. `ImageComparisonPreview`: actual before/after bitmaps with draggable clipping divider.
- `EncodedImagePreviewPanel`: Convert Images, Compress Images and Target Size use an actual private encode/decode through the same production codec/settings. The panel debounces edits, owns/releases both layers, exposes loading/retry, and shows real format/bytes/dimensions/chosen quality. Reduced previews label every output size/quality as sample-only and never claim the full export met its target.
- Settings visible to users: theme, optional dynamic accent, Arabic/English, save location, default qualities, measured storage, clear cache, about/version/privacy/diagnostics. Removed legacy motion/haptic/network/concurrency switches stay hidden. System accessibility motion preferences remain honored.

## Tests and screenshot acceptance

`ToolCatalogueInteractionTest` (3 tests), `StudioGestureInteractionTest` (6 tests), `StudioAccessibilityTest` (5 tests), `EncodedImagePreviewUiTest` (2 real-codec tests), existing navigation/workspace tests, engine pixel tests and the Roborazzi matrix harness are the intended evidence sources. A test source file is not a passing test. The accessibility suite checks English/Arabic 200% catalogue labels, minimum control targets and semantic theme contrast; it does not certify every route or TalkBack flow. `scripts/tests/test-tool-icons.py` was executed successfully for all 47 unique vectors; resource XML parsing found 1,322 matching English/Arabic keys with no duplicate keys. Real device/Robolectric screen captures must be listed individually with their renderer and state. External scanner, unavailable tools and network metadata states must be reported as blocked/absent rather than drawn as fake screenshots.

The requested complete tool x empty/loaded x light/dark x English/Arabic matrix is not certified by this document. Follow the matrix manifest and `TEST_REPORT.md` for actual evidence.

## pdf_to_docx — PDF to Word

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_to_docx` — Material Symbols Rounded filled `article`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Select pages and export editable text as Word.
- **Controls:** Visual page grid, exact range companion, output name.
- **Preview/results:** Actual source PDF pages; extracted paragraphs/headings/OCR are validated by the document backend before publication.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** Visual fidelity is limited to extracted text/paragraphs; not a reconstruction of every original page element. Arabic/Word opening and scanned-page OCR device acceptance remain required.

## pdf_to_pptx — PDF to PowerPoint

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_to_pptx` — Material Symbols Rounded filled `slideshow`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Select PDF pages and export one slide per page.
- **Controls:** Visual page picker, range companion, output name.
- **Preview/results:** Real rendered PDF pages; output includes rendered slide image and extracted text layer.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** PowerPoint/Arabic/large-document acceptance must be checked against generated files, not inferred from preview.

## pdf_02 — Images to PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_02` — Material Symbols Rounded filled `picture_as_pdf`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Arrange imported photos and create a PDF.
- **Controls:** Thumbnail drag handles and move buttons; A4/Letter/Auto/Custom visual cards; orientation, margin and quality sliders; margin drag preview.
- **Preview/results:** Real image drawn using ImagePdfLayout; preview updates from the same page geometry policy.
- **Empty state:** Image/document icon and Import files action; gallery is also available.
- **Limits and remaining acceptance:** Multi-image output order and custom paper must pass device export checks.

## pdf_03 — Merge PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_03` — Material Symbols Rounded filled `merge`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Drag PDF cards into merge order.
- **Controls:** First-page thumbnail cards, drag handles, accessible move buttons, output name.
- **Preview/results:** Real first-page thumbnails and total page count computed from actual PDF reads.
- **Empty state:** Document icon and Import files action; merge disabled until at least two PDFs.
- **Limits and remaining acceptance:** Long source queues and drag auto-scroll require device acceptance; accessible move buttons are retained.

## pdf_05 — Split PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_05` — Material Symbols Rounded filled `content_cut`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Tap or drag through the page grid to choose ranges or split boundaries.
- **Controls:** Thumbnail grid, selection modes, count slider, exact range companion and live output file count.
- **Preview/results:** Actual page thumbnails; selections and boundaries alter the shown output count.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** 100+ page drag-range/autoscroll and resulting ZIP/page counts require device verification.

## pdf_04 — Organize Pages

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_04` — Material Symbols Rounded filled `view_module`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Drag page thumbnails to reorder, then rotate or delete selected pages.
- **Controls:** Virtualized page grid, long-press drag reorder, rotation controls, delete, visible undo/redo/history.
- **Preview/results:** Actual rendered page thumbnails with rotation; stable page-instance IDs preserve duplicates.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** Reordering across distant rows and restored histories must be exercised on device.

## pdf_06 — Extract Pages

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_06` — Material Symbols Rounded filled `file_open`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Select pages visually and export the selected pages.
- **Controls:** Tap/range drag grid, select-all/invert, exact page-range companion.
- **Preview/results:** Actual source-page thumbnails, selected count and range.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** Export ordering, rotated pages and 100+ page files remain device gates.

## pdf_07 — PDF to Images

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_07` — Material Symbols Rounded filled `image`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Choose pages and render them as image files.
- **Controls:** Page picker, PNG/JPEG, DPI and quality sliders, grayscale toggle.
- **Preview/results:** Actual PDF preview with grayscale effect and chosen page selection.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** DPI/output bytes and image fidelity require final encoded-output checks.

## pdf_01 — View PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_01` — Material Symbols Rounded filled `menu_book`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Read imported PDFs in the immersive, content-first viewer.
- **Controls:** One slim (≤56dp) top bar: back, one-line elided file name, "N / total" page indicator (tap = jump-to-page dialog), one overflow button. Overflow menu: open another file, night mode, thumbnails, search, share, print, file info. Immersive mode hides the chrome and system bars after 2s of stillness; tapping the page or scrolling reveals them again. The app's bottom nav is hidden entirely on the viewer route.
- **Preview/results:** `PdfViewerScreen` renders via the Android `PdfRenderer`; fit-to-width default, pinch/double-tap zoom, continuous scroll, lazy thumbnails, remember last page per file, night-mode inversion, keep-screen-on while reading.
- **Empty state:** Loading state with a retry action on failure.
- **Limits and remaining acceptance:** Viewer is navigation, not a processing job; immersive chrome, 500+ page thumbnails, and text search require device evidence (no emulator/device in this environment).

## pdf_compress — Compress PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_compress` — Material Symbols Rounded filled `compress`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Choose a measured compression preset.
- **Controls:** Three large Low/Medium/High cards; custom quality/DPI and target-size sliders inline.
- **Preview/results:** Before/after byte sizes are from validated real candidate compression via measuredCompression, with a clear measuring state. Custom preview uses executionSettings, including Arabic-digit parsing.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** The same engine produces preview candidates and export. Three-file ±15% output comparison and large-file behavior must be measured, not presumed.

## pdf_crop — Crop PDF Pages

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_crop` — Material Symbols Rounded filled `crop`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Drag corner or edge handles over the actual page.
- **Controls:** Aspect presets, page/range picker, per-page crop switch, undo/redo/history, clear per-page overrides.
- **Preview/results:** Real upright page with crop frame, dimmed outside region and guides. Per-page boxes serialize to cropOverrides.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** Rotated CropBox/UserUnit inputs and combinations of global/range/per-page crops require pixel checks.

## pdf_annotate — Annotate PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_annotate` — Material Symbols Rounded filled `edit_note`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Choose an annotation icon, then mark the real page or draw ink.
- **Controls:** Bottom toolbar has highlight, underline, strike, note, square, circle, text box, ink, text; color swatches, applicable stroke thickness, text field, page picker, undo/redo.
- **Preview/results:** Actual document plus current annotation/ink geometry and color. Nine dedicated annotation vectors are bundled.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** One selected annotation operation per execution; multiple freehand strokes are supported. Appearance agreement and annotation persistence need exported-PDF tests.

## pdf_stamp — Watermark & Page Numbers

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_stamp` — Material Symbols Rounded filled `branding_watermark`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Select a text or logo watermark and manipulate it on the page.
- **Controls:** Drag/pinch/rotate and frame/handles; text/image mode; sans/serif/mono, font size or image scale, colors, opacity, rotation presets, placement presets, tile/single, all/current/selected pages, page numbers.
- **Preview/results:** Real PDF page. Text raster uses production font-point geometry; logo uses shared OverlayStylePolicy and the actual image bitmap.
- **Empty state:** Document icon and Import files; image mode has an explicit gallery CTA when the logo is missing.
- **Limits and remaining acceptance:** PDF plus logo requires exactly one PDF and one image. Gestures, color/font, rotation and output pixel parity require device execution.

## pdf_signature — Sign PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_signature` — Material Symbols Rounded filled `signature`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Draw or reuse transparent ink, then place multiple instances on real pages.
- **Controls:** Pen width/color/undo/redo/clear/library; page picker; add/select/delete instances; drag/pinch/rotate/frame/corner handles; rotation/size sliders; placement undo/redo.
- **Preview/results:** Actual PDF and actual alpha PNG; each instance serializes page, normalized box and clockwise rotation. Up to 100 placements use the selected signature image.
- **Empty state:** Import a PDF; the real drawing pad appears until a signature image is supplied.
- **Limits and remaining acceptance:** Different signature images in one operation are not supported: multiple placements reuse one selected image. Pixel alpha, multi-page placement and gesture tests exist but require device execution.

## pdf_image — Add Image to PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_image` — Material Symbols Rounded filled `add_photo_alternate`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Import an image and position it over a PDF page.
- **Controls:** Drag/resizable box, handles, page picker and undo/redo.
- **Preview/results:** Actual PDF and imported image, fitted inside the production normalized box.
- **Empty state:** Import files; add a PDF and an image.
- **Limits and remaining acceptance:** The general image insertion operation retains its original single-placement interface; multi-placement/rotation studio belongs to Sign PDF.

## pdf_protect — Password Protect PDF

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_protect` — Material Symbols Rounded filled `lock`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Protect or unlock a PDF.
- **Controls:** Show/hide password, strength indication, owner/opening password, print/copy/modify/annotation/form permission toggles.
- **Preview/results:** Actual source document preview alongside inline security controls.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** Reader enforcement of permissions varies; use an opening password to restrict access. Device round trips remain mandatory.

## pdf_metadata — PDF Metadata

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_metadata` — Material Symbols Rounded filled `info`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Edit document properties beside its preview.
- **Controls:** Title, author, subject, keywords and output name. Existing second-PDF insertion controls remain available.
- **Preview/results:** Actual first document preview; side-by-side form at >=720dp, stacked form on narrower windows.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** Property edits are verified in the exported document; they do not alter visible page artwork.

## pdf_clean — Clean Metadata

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_clean` — Material Symbols Rounded filled `shield`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Remove identifying document metadata.
- **Controls:** Remove-title toggle and output name.
- **Preview/results:** Actual source page; backend validates sanitized metadata.
- **Empty state:** Document icon and Import files action.
- **Limits and remaining acceptance:** No fabricated visual change: metadata cleaning changes properties. Final property verification remains a gate.

## pdf_forms — Fill PDF Form

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_forms` — Material Symbols Rounded filled `assignment`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Tap a real form widget on the rendered page and fill it.
- **Controls:** Widget overlays at discovered upright CropBox coordinates; checkbox/radio on-page selection; selected text field focus; field chips; page picker; flatten/reset.
- **Preview/results:** Real page and real AcroForm widget bounds/values. Read-only fields remain read-only.
- **Empty state:** Import CTA; when no editable fields exist, an icon state explains this and offers another import.
- **Limits and remaining acceptance:** Widget projection for rotations/UserUnit, complex radio groups and flattening need device pixel/field verification.

## pdf_ocr — Extract Text

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_pdf_ocr` — Material Symbols Rounded filled `text_snippet`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Recognize a page, review text, copy or export it.
- **Controls:** Page picker, recognize/retry, selectable text, copy and export actions.
- **Preview/results:** Actual page beside real embedded text or ML Kit OCR at >=660dp; stacked on phones. Loading/error/empty states are explicit.
- **Empty state:** Document icon and Import files action; recognition panel has a clear Recognize action.
- **Limits and remaining acceptance:** OCR is limited to the backend language/model capability described in the workspace; review recognized text. Model availability and scanned inputs require network/device tests.

## scan_doc — Smart Scan

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_scan_doc` — Material Symbols Rounded filled `document_scanner`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Open the multi-page document scanner immediately.
- **Controls:** Primary ML Kit scanner with gallery import and PDF/JPEG results. CameraX fallback has capture/gallery queue, reorder/delete and PDF creation.
- **Preview/results:** Primary scanner UI is owned by Play services. Fallback uses a lifecycle-bound live camera and sampled real photo thumbnails.
- **Empty state:** Fallback requests camera permission only when chosen; gallery remains available without camera access.
- **Limits and remaining acceptance:** External ML Kit activity, physical camera, denial/recreation and 100-page flows cannot be certified by host screenshots.

## img_01 — Convert Images

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_01` — Material Symbols Rounded filled `photo_library`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Convert imported images to a selected format.
- **Controls:** JPEG/PNG/WebP chips, quality slider, naming controls and batch source order.
- **Preview/results:** Actual encode/decode before/after divider, real output format/bytes/dimensions/quality, loading and retry; uses the exact export settings.
- **Empty state:** Image icon and Import files action; gallery option.
- **Limits and remaining acceptance:** Sampled previews explicitly qualify output bytes/quality as sample-only. Encoded artifact color/alpha/EXIF behavior and unsupported codec errors must be device tested.

## img_02 — Resize Images

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_02` — Material Symbols Rounded filled `photo_size_select_large`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Resize a real image while viewing resulting geometry.
- **Controls:** Percentage/longest-edge/width-height modes, sliders/presets, fit/fill/pad/stretch, upscale toggle and format/quality.
- **Preview/results:** Actual sampled output preview from ImageResizePolicy and ImageGeometry, with original/edited toggle and dimensions.
- **Empty state:** Image icon and Import files action.
- **Limits and remaining acceptance:** Preview is sampled and states this; full-resolution output is independently validated.

## img_03 — Crop, Rotate & Flip

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_03` — Material Symbols Rounded filled `crop_rotate`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Crop, rotate or flip directly on the image.
- **Controls:** Corner/edge handles, aspect presets, rotate/flip, undo/redo and format/quality.
- **Preview/results:** Actual image in the same normalized coordinate space consumed by the crop engine.
- **Empty state:** Image icon and Import files action.
- **Limits and remaining acceptance:** Rotation/aspect/flip combinations and EXIF-normalized output need pixel checks.

## img_04 — Compress Images

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_04` — Material Symbols Rounded filled `photo_size_select_small`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Compress images with controlled quality.
- **Controls:** Format chips, live quality slider and naming controls.
- **Preview/results:** Real before/after codec comparison, measured candidate bytes/dimensions and chosen encode quality; PNG selection follows the same WebP compression policy as export.
- **Empty state:** Image icon and Import files action.
- **Limits and remaining acceptance:** Reduced-preview bytes and quality are labeled as sample-only, not predicted full-export values. Actual device fidelity/size acceptance is pending.

## img_exif — EXIF Inspector

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_exif` — Material Symbols Rounded filled `perm_media`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Inspect/remove image metadata.
- **Controls:** Remove-all or location-only toggles and output format controls where applicable.
- **Preview/results:** Real image preview; metadata backend operates on actual source tags.
- **Empty state:** Image icon and Import files action.
- **Limits and remaining acceptance:** The current UI does not provide a full browsable table of every EXIF tag. Metadata preservation/removal needs a recorded Android EXIF round-trip result; device acceptance remains NOT VERIFIED.

## img_watermark — Watermark Images

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_watermark` — Material Symbols Rounded filled `texture`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Select text or a logo and manipulate it on the real image.
- **Controls:** Drag/pinch/rotate/handles; sans/serif/mono; colors; opacity; scale; rotation/placement presets; single/tile; output format/quality.
- **Preview/results:** Actual image, actual logo or production watermarkTextBitmap, and shared placement geometry. A 70ms sampled preview path also uses the production renderer.
- **Empty state:** Image icon/import action; logo mode adds a clear gallery CTA when the second image is absent.
- **Limits and remaining acceptance:** Logo mode requires one source image plus one logo. Font/alpha/rotation/tile pixel parity and gesture behavior require device execution.

## img_join — Join / Collage

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_join` — Material Symbols Rounded filled `dashboard`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Choose a collage layout and move images between its slots.
- **Controls:** Visual grid/horizontal/vertical cards; gap slider; thumbnail drag handles/move buttons; long-press drag directly in the collage preview.
- **Preview/results:** Real combined image using ImageJoinPolicy; hit regions come from that same plan and reorder actual source IDs.
- **Empty state:** Image icon/import action; at least two images required.
- **Limits and remaining acceptance:** Placement reorders fixed layout slots; arbitrary overlapping/free-position collage layers are not implemented.

## img_adjust — Adjust & Pad

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_adjust` — Material Symbols Rounded filled `tune`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Adjust image appearance and compare before/after.
- **Controls:** Brightness/contrast/saturation/padding sliders, visual presets, draggable comparison divider and accessible comparison slider.
- **Preview/results:** Both comparison layers are real decoded/rendered bitmaps.
- **Empty state:** Image icon and Import files action.
- **Limits and remaining acceptance:** Output matrix/padding parity is covered by pixel tests only when executed on device.

## img_target — Target Size

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_target` — Material Symbols Rounded filled `speed`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Choose a target byte size and compress toward it.
- **Controls:** Target-size slider/presets, JPEG output policy and output naming.
- **Preview/results:** Actual JPEG encode/decode comparison with candidate bytes/dimensions/chosen quality and the requested export target.
- **Empty state:** Image icon and Import files action.
- **Limits and remaining acceptance:** Reduced previews explicitly state that the full export may choose different size/quality. No full-export target success is implied until the actual exported file passes size validation.

## img_compare — Compare Images

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_compare` — Material Symbols Rounded filled `compare`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Compare two actual images.
- **Controls:** Select two inputs, actual source previews and report action.
- **Preview/results:** Real source imagery; backend produces dimension/difference report.
- **Empty state:** Image icon/import action; exactly two inputs required.
- **Limits and remaining acceptance:** The result remains a report flow, not an interactive heat-map diff editor.

## img_stitch — Stitch Screenshots

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_img_stitch` — Material Symbols Rounded filled `view_agenda`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Arrange screenshots vertically and trim overlap.
- **Controls:** Drag source ordering and direct preview slot move, gap/overlap sliders and output format/quality.
- **Preview/results:** Real stitched bitmap with shared ImageJoinPolicy placements and trim values.
- **Empty state:** Image icon/import action; at least two images required.
- **Limits and remaining acceptance:** Overlap is explicit; automatic content alignment is not claimed.

## dl_media — Video, Audio & File Downloads

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_dl_media` — Material Symbols Rounded filled `download`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Paste a media link, inspect it, then choose real formats and download.
- **Controls:** Paste/clear, metadata card, detected platform, video/audio and available format controls only after successful analysis, progress/job actions.
- **Preview/results:** Real resolved thumbnail/title/duration/format metadata; errors and empty clipboard are localized.
- **Empty state:** Link icon and a paste/edit CTA; no invented qualities.
- **Limits and remaining acceptance:** Platform x media x quality x offline/resume matrix remains a separate real-network/device gate.

## dl_02 — New Direct Download

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_dl_02` — Material Symbols Rounded filled `add_link`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Inspect a direct or Drive link and queue its real file.
- **Controls:** Same media/direct intake route, filename, destination and available download choices.
- **Preview/results:** Actual probe metadata and result state; unsupported/private/age/rate errors have actionable copy.
- **Empty state:** Link icon and Paste/edit action.
- **Limits and remaining acceptance:** Permission, Drive interstitial and chosen-folder writes need real endpoint/provider tests.

## dl_03 — Batch URL Intake

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_dl_03` — Material Symbols Rounded filled `download_for_offline`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Validate a batch of links before queueing them.
- **Controls:** Multi-line URL field, paste, per-list valid/duplicate/unsupported/malformed counts.
- **Preview/results:** Actual parsed rows/counts; no content thumbnails are synthesized.
- **Empty state:** Link input with Paste CTA; empty clipboard gives a localized explanation.
- **Limits and remaining acceptance:** Social links requiring individual metadata selection are rejected explicitly; direct download/resume cases require device tests.

## dl_settings — Download Settings

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_dl_settings` — Material Symbols Rounded filled `settings`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Open download defaults and save location.
- **Controls:** Default video/audio quality, save-folder chooser and real storage settings.
- **Preview/results:** SettingsScreen backed by persisted preferences and storage measurements.
- **Empty state:** Not a file-transform workspace; settings rows are immediately actionable.
- **Limits and remaining acceptance:** Navigation alias; no independent processing backend or fake loaded-file state.

## file_02 — Browse Storage

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_file_02` — Material Symbols Rounded filled `folder_open`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Browse app files and granted provider folders.
- **Controls:** Grid/list, sort/filter/group, select, rename/copy/move/delete, destination and storage entry points.
- **Preview/results:** Actual files/provider metadata and real thumbnails where supported.
- **Empty state:** Folder icon and Import/choose-folder CTA.
- **Limits and remaining acceptance:** Navigation alias to Files; provider permissions, selection and deletion require device smoke tests.

## file_07 — Create ZIP Archive

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_file_07` — Material Symbols Rounded filled `folder_zip`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Package selected files into a ZIP.
- **Controls:** Actual input list/thumbnail source strip, compression-level slider, output name.
- **Preview/results:** Real selected-file previews where displayable; encoded archive is validated by the backend.
- **Empty state:** File/folder icon and Import files action.
- **Limits and remaining acceptance:** Archive integrity, name collisions and large streams need actual output tests.

## file_08 — Extract ZIP Archive

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_file_08` — Material Symbols Rounded filled `unarchive`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Extract a selected ZIP archive.
- **Controls:** Input chooser, output naming and existing safe extraction behavior.
- **Preview/results:** Actual archive file information; extraction results flow through published output cards.
- **Empty state:** Archive icon and Import files action.
- **Limits and remaining acceptance:** Traversal/zip-bomb/collision behavior must pass backend/device tests; no synthetic archive preview.

## file_encrypted_zip — Encrypted ZIP

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_file_encrypted_zip` — Material Symbols Rounded filled `enhanced_encryption`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Create or extract an AES ZIP.
- **Controls:** Mode toggle, show/hide password, strength indication, source selection and output name.
- **Preview/results:** Actual source files and published archive/extracted result cards.
- **Empty state:** Archive icon and Import files action.
- **Limits and remaining acceptance:** Cryptographic payload validation and cancellation cleanup require executed tests; this is separate from disabled Vault.

## file_batch_rename — Batch Rename (copies)

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_file_batch_rename` — Material Symbols Rounded filled `drive_file_rename_outline`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Review new names before publishing renamed copies.
- **Controls:** Pattern chips {name}, {n}, {date}, counter slider, live preview rows and thumbnail cards. Date is frozen in the draft/job.
- **Preview/results:** Real source thumbnails with proposed destination names, plus the complete live name list.
- **Empty state:** File icon and Import files action.
- **Limits and remaining acceptance:** Copies are published; originals are retained. Unicode/collision/path validation and frozen-date behavior have tests awaiting execution.

## file_duplicates — Duplicate Finder

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_file_duplicates` — Material Symbols Rounded filled `copy_all`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Find byte-identical selected files.
- **Controls:** Automatic cancellable streamed SHA-256 comparison, grouped result cards, remove-from-selection and export-report action.
- **Preview/results:** Real duplicate groups and actual thumbnails; no name-only matching.
- **Empty state:** File icon/import action; a no-matches state explains content differs and offers more imports.
- **Limits and remaining acceptance:** Removing from selection does not delete original files. Large-set cancellation/storage behavior require device acceptance.

## file_trash — Managed Trash Info

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_file_trash` — Material Symbols Rounded filled `delete`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Open Files and manage recoverable outputs.
- **Controls:** Existing managed-trash restore/purge and file selection controls.
- **Preview/results:** Actual persisted output/trash records.
- **Empty state:** Folder/trash state with restore/import actions appropriate to the active Files view.
- **Limits and remaining acceptance:** Navigation alias to Files; it does not claim provider files are universally recoverable.

## file_drive — Google Drive Import / Export

- **Status:** Enabled (real Drive v3 implementation, user token-gated).
- **Icon:** `ic_tool_file_drive` — Material Symbols Rounded filled `cloud_sync`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Drive import/export via the official Drive v3 API (`FileExtrasHandler.googleDrive`). The user supplies an OAuth token (Android Keystore-bound).
- **Controls:** Import mode (file ID) and export mode (folder ID), Google sign-in button, token field.
- **Preview/results:** Official-account Drive workspace; device/account qualification remains outstanding.
- **Empty state:** Standard workspace state until a token and file/folder ID are supplied.
- **Limits and remaining acceptance:** Official API/account/privacy qualification remains incomplete; the user must supply their own Drive OAuth token. No backend is deployed. Direct public Drive links use the downloader path instead.

## sha256 — SHA-256 verification

- **Status:** Available in source; device acceptance NOT VERIFIED.
- **Icon:** `ic_tool_sha256` — Material Symbols Rounded filled `verified`, 24dp. Exact pinned source/license: `ICON_MAP.md`.
- **Primary interaction:** Compute or verify actual file digests.
- **Controls:** Source selection and optional expected digest text, output/report action.
- **Preview/results:** Actual source metadata; hash report is generated from file bytes.
- **Empty state:** File icon and Import files action.
- **Limits and remaining acceptance:** Digest correctness, large-file streaming and malformed expected values require executed tests.

