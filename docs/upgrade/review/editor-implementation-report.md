# Direct editing implementation report

Workspace: `/workspace/scratch/929aae21eb23/audit-build/Jumlo-main`.

## Implemented behavior

### PDF page organization

- Added immutable per-output-instance page entries with stable IDs, one-based source pages and canonical relative clockwise rotations. Duplicate instances preserve independent rotations and retain their identities through reorder and history.
- Long press selects the instance and starts dragging. Reordering updates the visible order as page boundaries are crossed. Physical viewport edge proximity drives lazy-row autoscroll; continued scrolling advances the dragged page. Reorder uses RTL only to translate display/scroll direction; source page numbering and processing coordinates remain physical.
- Added contextual rotate, duplicate, remove and print-selected actions; select-all/clear; earlier/later buttons and custom accessibility reorder actions. Selection/checkbox state and source-page/output-position/rotation descriptions distinguish duplicates. End-position reorder buttons disable correctly.
- Undo/redo snapshots include order, duplicate identities, deletion and per-instance rotation, bounded to 30 snapshots per direction and retained through configuration in the WorkspaceRoute-scoped PdfPageEditorSession ViewModel. Drag reorder records one history entry on drop. Remove and undo/redo clear stale selection.
- Empty document, blank/duplicate instance IDs, nonpositive source pages and noncanonical rotations cannot replace a valid history state. Loaded page entries are also checked against source page count. Processing performs final source bounds validation.
- Kept existing split boundary markers, split-each-page, range selection and extract selection modes. Legacy order and rotation settings translate into stable initial entries, including negative quarter rotations and `rotatePages=all`.
- Added optional `onPrint: (List<Pair<Int,Int>>) -> Unit = {}` and `printEnabled: Boolean = false`; pairs preserve ordered source-page/relative-rotation values and duplicate instances. Root owns the actual print callback and enables the action only when wired.

### Visual crop, placement and annotation

- The source-space group transforms its image and editing canvas together, reflecting quarter rotation and horizontal/vertical flips in backend order. Backend crops source coordinates before flipping and rotating; the editor retains source coordinates while transforming their display.
- Overlay/annotation positioning uses physical absolute offsets inside an explicitly `AbsoluteAlignment.TopLeft` source-space box. This fixes both the relative-offset mirror and the otherwise remaining RTL Box-start alignment discrepancy.
- Added 1–4× pinch zoom/pan, bounded drawn translation, double-tap fit and a named Fit preview action. Geometry pointer handling uses transformed local coordinates. Gesture motion updates local geometry and writes the draft at gesture completion.
- Geometry/rotation/both-flips/aspect-lock history uses dedicated saveable `VisualEditSnapshot` records, bounded to 25. Reset and presets are undoable. Aspect-ratio chips remain selected and constrain corner resizing. Added vertical flip.
- Added named document-coordinate movement and smaller/larger region buttons as alternatives to dragging. Corner hit testing already uses a 34dp radius (68dp nominal diameter), exceeding the required 48dp target.
- Synced document page with applicable Advanced page fields. Stopped eagerly recycling displayed preview bitmaps; Compose references determine their lifetime and GC releases them after references disappear.

### Watermark preview/history

- Replaced the fixed `.48`/`.26` font multipliers and clipped 55dp label boxes with the same Android DEFAULT-bold text raster metrics used by the backend: initial 84px shaping, long-text size bound, raster padding and dimensions, conversion to requested PDF font points, `.8`/`.36` page-width caps, physical normalized centers and identical 3×3 tile positions.
- Raster preview uses the same text normalization and point scale. Watermark opacity is applied while drawing; changing opacity does not regenerate the text raster on every pointer event. Preview also respects page scope and displays page numbering at the backend baseline/point size.
- Added bounded saveable watermark undo/redo for text, presets, tile/single, color, sliders, positioning, scope and page numbering. Slider/drag changes remain local until gesture completion and become one history entry per completed gesture. Draft secrets are excluded from saved snapshot serialization.
- Added horizontal/vertical position sliders for assistive alternatives, slider labels/current numeric values, and selected swatch semantics with 48dp swatch targets.
- New strings are isolated in `res/values/editor_upgrade.xml` and `res/values-ar/editor_upgrade.xml` (15 paired keys); shared strings.xml was not edited.

## Additive format contract

New file `data/processing/PdfPageEdit.kt`:

```kotlin
@Serializable
data class PdfPageEdit(
    val instanceId: String,
    val sourcePage: Int,
    val rotationDegrees: Int = 0
)
```

`WorkspaceDraft.pageEdits` and `PdfOrganizeSettings.pageEdits` are additive `List<PdfPageEdit> = emptyList()` fields. Draft version remains 1 and all legacy fields remain available. Nonempty entries are authoritative and preserve their exact order and duplicates. Rotation is relative clockwise, added to source intrinsic rotation, and restricted to 0/90/180/270. Legacy drafts/jobs without entries retain their prior order/rotate behavior. `executionSettings()` includes entries; when they are present, stale legacy rotation text cannot block the new request. Root's Advanced organize handlers clear entries on manual legacy field edits, and the editor notices external changes.

The processing agent implements handler/backend support separately; no handler/backend file was edited by this agent.

## Tests and verification

Standalone production Kotlin/JUnit verification (root remains sole Gradle runner):

- `PageEditHistoryTest` + existing `VisualEditGeometryTest`: **14 tests passed**, latest run `OK (14 tests)`.
- Covers independent stable duplicate identities, cumulative per-instance rotation, undo/redo across all page operations, redo branch clearing, history bound, empty-output protection, malformed-entry rejection, legacy order/negative rotation/all-page rotation, all rotation/flip inverse-coordinate round trips, known flip-then-clockwise order, corner constraints, hit testing, physical aspect presets and aspect-locked resize bounds.
- A new malformed-entry regression was observed red (360-degree noncanonical rotation incorrectly accepted), then fixed and verified green.
- `PageEditSettingsTest`: three additional Gradle-suite tests authored for legacy job decoding/default entries, duplicate entry/settings/draft serialization roundtrip, and authoritative entries ignoring stale legacy rotation text. Pending root's Gradle gate.
- Both new localized XML files parsed successfully and contain the same 15 resource keys.
- No Gradle command, device/emulator run, screen recording, TalkBack execution or visual screenshot certification was performed by this agent.

## Remaining qualification and limitations

- Root must compile/run the complete suite, wire and exercise print-selected, and validate device gestures/TalkBack in English/Arabic, large text and RTL before claiming runtime interaction quality.
- Watermark text uses the same Android font/shaping/raster dimensions as the backend. Page-number preview uses Android sans-serif while PDF numbering uses Helvetica; exact page-number glyph outlines are not claimed. Intrinsically rotated or offset/cropped PDFs still require rendered-output comparison because Android's visible PDF page bounds and backend media-box coordinates can differ.
- Page history and potentially large selected-instance lists remain in a route-scoped ViewModel, with one active document retained per route. They never enter the Activity saved-state Bundle. Current edits persist through WorkspaceDraft and seed a fresh session after process death; undo history is deliberately not restored after process death. Other visual-editor histories remain bounded saveable editor snapshots. History across process sessions/navigation is not a durable cross-tool history store.
- Pinch/pan versus corner-drag arbitration, edge autoscroll while holding a dragged page, rotated coordinate hit testing and reduced-motion device behavior remain runtime qualification tasks. New previews use direct state updates without introducing animation requirements.
- Annotation text preview remains the existing compact text style rather than a full backend font/appearance preview; placement RTL is corrected.

No git commit was made.


## Follow-up requirement polish and R7 lifecycle fix

- Selected count and contextual delete/rotate/duplicate/print actions now appear in a top contextual bar above preview/thumbnail content.
- Organizer has a single bounded 280dp focused full-page preview, per-instance rotation, pinch zoom/pan, double-tap zoom/fit, accessible reset, loading/error/retry and focused-instance navigation.
- R7 was verified against the old full-history JSON Saver. Replaced that Saver with a WorkspaceRoute-scoped `PdfPageEditorSession` ViewModel. Large current/undo/redo/selection lists are retained in memory through ordinary configuration changes and excluded from saved instance state. Only one document session is retained at a time so switching source documents releases the previous history. Current document restoration after process death uses the existing durable WorkspaceDraft, with a fresh history.
- Added `PdfPageEditorSessionTest` for ViewModelStore/config retention, process-death draft seeding and releasing old document sessions. These three tests require root's integrated Gradle gate; this agent ran no Gradle command.
