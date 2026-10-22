> Historical Phase 0 audit of the supplied source, before the authorized upgrade. Findings and proposed reproductions below describe that baseline. See ../UPGRADE_REPORT.md for current changes and fresh verification.

# Phase 0: UI, interaction, localization and accessibility source audit

Source root: `/workspace/scratch/929aae21eb23/audit-source/Jumlo-main`.
All file references below are relative to that extracted source. Source/config/tests were not modified. No Gradle command, emulator, screenshot, TalkBack session, device or performance measurement was run by this auditor. Every behavioral consequence is a source-based prediction and is runtime-unverified. P1 = high impact, P2 = medium, P3 = low. This is an audit, not Phase 1 implementation.

## Coverage and existing strengths

The navigation graph has eight typed routes, all mapped at `app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt:404-411`: Home, Tools, Downloads, Files, Settings, Scan, Workspace and Activity. Workspace has general document/file tooling and a separate media/direct-download experience. This source contains substantial working UI; absence of a named enhancement does not imply the existing tool is fake or absent.

### Screen and state matrix

| Screen or surface | Implemented interactions / strengths | Loading, empty, failure and recovery | Gaps / static risks |
|---|---|---|---|
| Launch splash | Actual app mark, app name/tagline; scale/opacity spring; reduced-motion snap. Startup ends on settings/workspace readiness, without a timed fake delay. `MainActivity.kt:51-78`, `ui/components/JumloSplash.kt:33-55`. | Splash while settings/workspace initialize. | No startup failure/retry surface is present here; startup failure behavior depends on the ViewModel. Hardcoded dark branding intentionally independent of light theme. No device startup timing claim. |
| First-use welcome dialog | Localized title/body; one Get started action. `ui/navigation/JumloNavHost.kt:414-420`. | Blocks until onboarding completed; explicit non-dismissable dialog. | No multi-page tutorial, language selection or permissions flow here; those are not required for tool correctness. |
| Root shell / floating dock | Home, Documents, Scan action, Tools and Me; Downloads remains accessible via Home and Tools. Dock exposes Role.Tab, selected and localized name, and has 52dp+ targets. Semantic labels remain when visual text hides at fontScale >=1.5. Insets and one safe hinge pane. `ui/components/QuietNavigation.kt:55-195`. | Shell snackbar displays localized messages; trash supports Undo action. | SharedTransitionLayout wrapper has no sharedElement/sharedBounds consumer. No navigation rail/two-pane layout; deliberate one-pane policy. |
| Home | Scan hero; four configurable quick actions; Download/All tools buttons; current draft resume; up to two active tasks; recent real output previews; favorites; tool suggestions. Uses adaptive 2/4 action columns and lazy outer list. `ui/home/HomeScreen.kt:33-254`. | Empty recent list gets explanatory text. Active tasks use real TransferRow progress/state/actions. | No explicit initial data loading or repository-error state on Home; initial empty list can resemble no content. Actual impact depends on startup gating. |
| Quick action customization | Add/remove/reorder via named up/down IconButtons, reset defaults, Save/Cancel; Back exits editing. `ui/home/HomeActionEditor.kt:20-47`, `HomeScreen.kt:62-69`. | Save disabled for empty selection; max four enforced on additions. | Inline editor is visually nested under root shell/dock; bottom action area lacks navigationBarsPadding, and list bottom lacks dock buffer. Check last items and Save/Cancel overlap at runtime. |
| Tools catalog | Live search in both titles/English subtitle; category chips; recents/most used; grouped icon grid; large-text reduces columns; tool nodes have explicit names and Role.Button. `ui/tools/ToolsScreen.kt:38-210`. | Filtered empty state with reset. Catalog only renders visible/enabled tools. | Search does not inspect subtitleAr (:54-55). Dense grid uses two-line ellipsis; full label remains in contentDescription. |
| Downloads | Real active/history jobs; new download and batch intake; selection and cancel selected; counts. `ui/downloads/DownloadsScreen.kt:27-148`. | Empty card; real indeterminate/determinate progress; pause/resume/cancel/retry and details in TransferRow. | Cancellation not swipe-driven. Active selection has M3 Checkbox; grid-style missing-state problems below do not apply to these standard checkboxes. |
| Media/direct-download workspace | URI keyboard, IME Go, paste, clear, debounced analyze; platform badge; real detected direct-file information; PDF/image processing choices; video/audio type; format/bitrate/quality; output rename; Wi-Fi/unmetered/HTTP controls; pinned queue button. `ui/downloads/MediaWorkspace.kt:51-543`. | Analyze progress + reduced-motion-aware shimmer; localized restricted/unsupported/general errors, retry where appropriate; queue disabled while analyze/submit or no valid choice. | Selected media type and quality are visual-only semantics (UX-05); no explicit empty-quality state if an asset returns zero choices; mode/quality cards use fixed two columns, needing large-font runtime QA. Network booleans correctly expose Role.Switch. |
| Activity / operation results | All-history or selected-job filter; active work first, durable results next, history; output open/share/save/reuse/trash/favorite/details; start new run. `ui/downloads/DownloadsScreen.kt:151-253`. | Empty card; errors per outcome; real progress; expired download secret/restart state can re-enter URL and retry. | No explicit loading skeleton/error for initial job/output subscriptions. Re-enter URL uses default text direction/keyboard unlike initial URI input. |
| Files: outputs | Search, type/favorites filters, date/name/size/type sort; list/grid/gallery switch; multi-select/select all; share/bundle/merge PDFs/images-to-PDF/delete; favorite; per-output overflow/details. `ui/files/FilesScreen.kt:41-205,275-362`. | Empty output card; private-output warning and Save as guidance; deletion confirmation, trash undo snackbar. | Fixed grid/gallery heights, window-wide column count and no font-scale response (UX-09). Grid selected state not semantic (UX-05). Empty filtered outputs use generic no-recent-files copy instead of no-results reset action. |
| Files: imported sources | Snapshot list, single reuse; multi-select/select all, bundle, merge or images-to-PDF. `FilesScreen.kt:206-253`. | Truly empty snapshots get import CTA. | When search produces zero matches but imported list is nonempty, no empty-search message is shown. |
| Files: granted folder | SAF folder picker, folder names, browse child folders, open/import/copy readable files, capability-aware rename/delete, new folder if writable. `FilesScreen.kt:263-272,386-414`. | No grant: explanation + choose-folder CTA. Rename validates invalid/colliding/unchanged/access-lost/provider states and disables confirm. `:416-452`. | Listing-level loading/error and folder-parent breadcrumb/back action are not represented in this screen. Browsing delegated to system tree picker. Zero search matches and empty granted folder have no row explaining emptiness. |
| Files: trash | Restore by tap/menu, purge individual/all, destructive confirmation. `FilesScreen.kt:254-261,362`. | Empty trash explanatory card; retained trash notice. | No swipe-to-dismiss/restore and no undo purge (purge is explicitly confirmed). Filtered trash with zero matches has no search-empty message. |
| General Workspace | Import from device/photos; real queue; per-file preview/move up/down/remove; smart defaults; inline validation/count/type errors; pinned execute action; advanced sheet; output name; all applicable parameter fields. `ui/workspace/WorkspaceScreen.kt:66-307,587-765`. | Choose-input card; busy linear progress; disabled execution until capability, validation, count, source readiness pass. Batch rename preview computes off main thread and cannot enable on stale result; validation error visible. | Some count errors are generic/wrong for two-source tools (:205-207,271). Advanced is essential for many operations; no per-field inline validation in generic Field. Dormant secret fields are UX-12, gated off from current catalog. |
| Preview and PDF viewer | HorizontalPager, 1-4x pinch, bounded pan, 1↔2.5x double-tap, reset pill, page/size badge, labeled 48dp prev/next. Page swipe disabled while zoomed; zoom resets per page. `WorkspaceScreen.kt:316-538`. | Per-page progress/shimmer and localized failure; no infinite corrupt-PDF spinner. | No semantic zoom-in action (UX-03); viewport fixed 300dp/440dp, modal content not scrollable; landscape/large-text fit needs runtime check. Prev/next always animateScrollToPage, no explicit snap branch here for local reduce-motion preference. |
| PDF page editor: organize / split / extract | Bounded lazy real thumbnails; page count; selected sets; select all; long-press horizontal reordering; labeled 48dp earlier/later fallback; delete selected with at least one remaining; split marker/each-page modes; order undo/redo up to30. `ui/workspace/PdfPageEditor.kt:42-239`. | Loading sentinel -1 then failure0; bad PDF gets unavailable text. Tiles distinguish loading / unavailable. | Rotation/undo defects UX-04; selected state visual-only UX-05; reorder lacks edge autoscroll/live placement, moves only at drop (UX-10). |
| Visual document editor: image/PDF crop, image/signature placement, annotation, gated redaction | Real image/PDF source; four corners/body drag; normalized coordinates; actual imported overlay; crop thirds/shading; reset; geometric undo/redo up to25; page prev/next; crop all/current scope; free/square/document presets; rotate/flip controls. `ui/workspace/VisualDocumentEditor.kt:73-392`. | Real preview loading shimmer, unavailable text, overlay missing hints; signature pad if required. Advanced numeric fallback exists for crop, signature, annotation and redaction. | RTL preview UX-02; transforms invisible UX-06; preset ratios only initial rectangle, not locked; history limited to geometry/current composition; preview page can desync from typed Advanced page field (local currentPage only initialized once). |
| PDF watermark studio | Real PDF preview; text and localized presets; single/tile; six named swatches; opacity/size/rotation sliders; draggable center; page navigator/scope; page numbering switch. `ui/workspace/VisualWatermarkEditor.kt:52-315`. | Loading spinner or preview-error copy; controls remain available if preview fails. | Approximate WYSIWYG UX-07; pointer-only center UX-03; missing labels/selected semantics UX-08. No undo/redo for watermark edits. |
| Visual image/creation settings | Resize percentage/presets; adjust filters/sliders + original/edited comparison; compression presets; quality; images-to-PDF paper mode and fit/cover; watermark opacity. `ui/workspace/VisualImageSettings.kt:20-163`. | Uses draft execution validation in Workspace. | Sliders lack programmatic label; displayed exact numeric setting generally absent (UX-08). No creation-layout preview for paper size/margin/orientation. AUTO/LETTER shown as raw English enum in inline paper chips :132-136; Advanced labels localized. |
| Scan / CameraX fallback | Camera permission CTA, live preview, capture, gradient-derived quad initialization, drag corner refinement, retake/apply, perspective correction; optional MLKit scanner offers native gallery/batch/filter/cleanup. `ui/scan/ScanScreen.kt:63-185,283-335`, `SmartDocumentScanButton.kt:38-142`. | Camera bind/capture/process/smart errors -> recovery card; processing progress; smart scanner busy indicator; CameraX fallback remains. | Quad image/coordinate mapping UX-01; no accessible corner adjustment UX-03; unconstrained quad can cross itself and invalid matrix result not checked. Capture lacks capture-in-flight disabling, so repeated capture taps should be device-tested. |
| Draw signature | Multi-stroke canvas, clear, save PNG, preview stroke color tracks theme; output PNG deliberately black ink on transparency. `ScanScreen.kt:339-430`. | Save disabled until ink. | Pointer-only canvas has no name/state/custom actions; imported image signature offers a separate alternate path. No stroke-level undo/redo. |
| Settings | Theme/language/accent/font scale/reduce-motion/haptics/intensity/sound/network/concurrency/save location/notifications/file view/storage/folder grant/cache/trash/privacy/log export/about. Native permission/settings routes; radio groups and full-row switches have roles and state. `ui/settings/SettingsScreen.kt:44-298`. | Storage refresh; resume-aware notification status; cache/purge confirmations, disabled busy actions; named destructive state. | Profile avatar itself is clickable but has null icon description; adjacent localized Open profile action gives same function. Choice dialogs are non-scrollable columns; large fonts/short landscape height need runtime check. |
| Account/local data sheet + storage/privacy/about/profile | Correctly framed as local data, no fake membership; scrollable sheet, localized headings/preferences; count/storage; storage breakdown progress; privacy/about body scrolls. `ui/components/AccountHubModal.kt:27-61`, `ui/settings/PreferenceComponents.kt:31-112`, `SettingsScreen.kt:207-230`. | Real current local count/storage; storage groups only when nonzero. | Storage breakdown includes a separate label and progress bar without a semantic association; device TalkBack QA needed. |
| Global dialogs | Rename, new folder, provider original deletion, output trash/purge, leave-workspace Save/Keep/Discard. `JumloNavHost.kt:422-467`. | Leave respects busy; snackbar global errors localize by resource. | New folder confirmation not disabled for blank name (validation happens downstream); no spinner within create/rename operation dialogs. |

### Theme, typography, RTL and physical targets

`ui/theme/Theme.kt:27-90` supplies Material3 light/dark semantic schemes, surface containers, errors, outline tokens. `:100-113` follows system/light/dark and Android12+ dynamic colors when selected; pre12 dynamic selection falls back to curated base. Curated blue/teal/coral/violet primary pairs are adapted by dark/light (:118-136); dynamic colors feed key pillar surfaces/actions/dock (:140-149), while categorical hues remain curated. Shape/spacing/type are centralized. Not every Material3 optional token is explicitly set; defaults fill others, so this audit does not label an incomplete token list a defect.

Read-only WCAG luminance calculation on the actual curated primary/onPrimary pairs: light teal6.61, blue8.21, coral6.94, violet7.24; dark teal11.15, blue9.24, coral10.35, violet8.93. onSurfaceVariant on surfaceContainerLow light5.76/dark10.68; onSurface light14.49/dark16.66. These are static token-pair ratios, not whole-screen contrast certification, and do not verify dynamic wallpaper schemes, disabled state or alpha compositing.

IBM Plex Sans variable is bundled; fontTools reports wght100-700/wdth75-100. Arabic static regular is also bundled; Arabic uses zero tracking and 1.42 line height (`Type.kt:114-123`). MainActivity sets AppCompat locale before/while composition; theme forces RTL for Arabic (`Theme.kt:151-175`). All858 English string resource names have Arabic equivalents across all values XML files. URLs explicitly LTR and many filenames use Content direction. Execution normalizes Arabic/Persian digits and Arabic decimal separator (`model/WorkspaceDraft.kt:108`); PDF order also accepts Arabic comma/digits. Translation completeness by key does not certify naturalness or actual rendered shaping.

System fontScale is preserved and multiplied by app Small=.91/Normal1/Large1.15 (`Theme.kt:154-156`), not clamped away. Settings, account, main lists scroll. Tools adapts column count for fontScale>=1.4. Dock removes visible captions at1.5 but retains semantic labels. Fixed file tile heights and several horizontal control rows remain risks (UX-09).

Physical target evidence: shared icon button explicitly enforces48×48 (`QuietButtons.kt:75-78`), dock min52, Scan66, Viewer prev/next48, page reorder arrows48, settings radio rows56 and switch rows64. M3 Button/IconButton/FilterChip/Surface provide platform/Material target behavior. A 42dp visual watermark swatch must not be automatically reported inaccessible merely because literal48dp is absent. No hit-target runtime scan was performed.

Motion: common press feedback observes pointer without consuming existing clicks; springs are transform/opacity based, reduced-motion snap. Dock and progress adapt; stagger caps12 and skips delay in reduced mode; shimmer becomes a static block; splash snaps. System animator/transition/window zero scales are observed (`SystemMotion.kt:11-31`). MainActivity uses local toggle OR system reduced state (:61). Navigation uses RTL-aware slide+fade, predictive workspace back with dirty confirm (`JumloNavHost.kt:140-149,379-402`). Fade transitions remain explicit even when reduce-motion removes slide. There is a SharedTransitionLayout wrapper at374 but no element/bounds sharing modifier anywhere in UI. No60fps/jank, frame pacing, adaptive hardware behavior or predictive-back device behavior was verified.

Haptics: real AppHaptics honors app enabled, intensity OFF, View enabled and system haptic setting; LIGHT uses View feedback; medium/strong use waveform with fallback; independent sound flag. Navigation, selection, execution, error, deletion and download success use it. PDF long-press reorder does not call longPress or slider tick even though helpers exist. Completion haptic is download-only (`JumloNavHost.kt:79-85`). Source event wiring is not tactile quality verification.

## Actionable findings

### UX-01 / P1 — Fallback scan quad and image have different coordinate spaces

**Evidence:** `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt:296-303,307-327,253-254`.
**Reproduce:** capture a clearly bordered4:3 or16:9 page with fallback CameraX, view in a roughly portrait preview, drag corners onto visible page corners, Apply, compare exported crop. On a hypothetical320×340 preview with a4:3 landscape image, rendered image is320×240 with50px vertical letterboxing. y=.08 draws at27.2px but corresponding image coordinate belongs at69.2px.
**Observed static behavior:** photo uses ContentScale.Fit; Canvas draws and normalizes against entire box; perspectiveQuad multiplies normalized coordinates by original bitmap dimensions.
**Expected:** image bounds, hit-testing, overlay and output mapping share fit scale and letterbox offsets.
**Root cause:** container coordinates treated as image coordinates. **Confidence:** source-confirmed geometry; runtime-unverified exported appearance.

### UX-02 / P1 — Arabic overlay/annotation preview shifts away from physical crop box

**Evidence:** `app/src/main/java/com/jumlo/app/ui/workspace/VisualDocumentEditor.kt:212-227,232-245,266-293`; compare correct `VisualWatermarkEditor.kt:132-135` absoluteOffset.
**Reproduce:** Arabic; open Add Image to PDF or Sign PDF; import asymmetric image and PDF; drag box to left. Compare overlay location against box and output. Annotate PDF has same relative offset use.
**Observed:** Image/Text use layout-relative offset; Canvas and draft coordinates are physical top-left. In RTL positive x offset moves content from logical start/right while guide stays at physical left fraction.
**Expected:** preview content and guide stay together in both locales and match engine coordinates.
**Root:** logical vs physical positioning mixed. **Confidence:** source-confirmed; runtime unverified.

### UX-03 / P2 — Several gesture-only tasks lack an assistive alternative

**Evidence:** `ui/scan/ScanScreen.kt:303-321,349-368`; `ui/workspace/VisualWatermarkEditor.kt:142-158`; `WorkspaceScreen.kt:380-406,463-482`; all under `app/src/main/java/com/jumlo/app/`.
**Reproduce:** enable TalkBack/Switch Access; try to refine scan corner, reposition watermark, or zoom in a PDF using accessibility actions alone.
**Observed:** custom Canvas/pointerInput exposes no labeled corner nodes, adjust/custom actions, or center coordinate controls. Zoom-out/reset appears only once already zoomed. Generic Advanced pdf_stamp fields (:670-672) expose text/pages, no centerX/Y fallback.
**Expected:** named adjustable corners, non-gesture positioning or actions and explicit zoom controls.
**Root:** visual gesture state not represented in semantics/alternative controls. **Caveats:** crop/signature/annotation/redaction have Advanced numeric coordinates; page reorder has arrow alternatives; signature can import an external image. Do not claim every direct editor is inaccessible. **Confidence:** missing source paths confirmed; actual assistive operation unverified.

### UX-04 / P2 — Page rotation overwrites prior rotation choices and bypasses Undo

**Evidence:** `ui/workspace/PdfPageEditor.kt:83-89,124-152`, `data/processing/PdfOperationHandler.kt:210-213`, `model/WorkspaceDraft.kt:53`.
**Reproduce:** organize3-page PDF; select page1, Rotate; tap Rotate again; select only page2, Rotate; process. Also reorder then rotate then Undo.
**Observed:** each tap replaces text2 with current selected pages and num1 with90; repeated tap remains90; later selection cancels rotation on previous pages. Undo stores only order text1, so last rotation cannot be undone; thumbnails show original unrotated bitmap.
**Expected:** cumulative explicit per-page edits, visible rotation state and undo covering the edit the user just made, or UI clearly describes a replaceable global rotation setting.
**Root:** a single global rotate-set/degrees pair reused as a direct editing command; history stores order only. **Confidence:** source-confirmed; runtime unverified.

### UX-05 / P2 — Selected states are only visual in page/file/media controls

**Evidence:** `ui/workspace/PdfPageEditor.kt:214-228`; `ui/files/FilesScreen.kt:142-159`; `ui/downloads/MediaWorkspace.kt:428-453,553-575`.
**Reproduce:** TalkBack; select/deselect a page thumbnail, file grid tile, video/audio type or media quality; refocus the same item.
**Observed:** these controls use clickable/Surface(onClick), changed container/border and a decorative null-description check. No selected/toggleable/selectable state is attached. File tile's same Select files icon description applies to both selected/unselected images.
**Expected:** item name plus checked/selected state and correct role; state updates announced through framework semantics.
**Root:** presentation boolean not supplied to semantic selection component. **Caveat:** M3 chips, standard checkboxes, settings radios/switches and dock already provide state; this is limited to custom listed controls. **Confidence:** source-confirmed semantic omission; TalkBack output unverified.

### UX-06 / P2 — Rotate/Flip edits do not update visual crop preview or its Undo

**Evidence:** `ui/workspace/VisualDocumentEditor.kt:205-207,124-150,357-365`; `data/processing/ImageOperationHandler.kt:67`, `model/WorkspaceDraft.kt:44`.
**Reproduce:** Crop Rotate Flip with asymmetric photo; Rotate once or Flip; compare preview; press Undo; process.
**Observed:** original frame drawn unchanged, no rotation/flip transform; controls mutate turns/flipX, engine applies them. Rect-only history neither records nor reverts transforms. Reset similarly resets rectangle only.
**Expected:** visual feedback for selected transform and undo/reset scope clearly covering edits.
**Root:** geometric rect state/history separated from transform draft fields with no transformed rendering. **Confidence:** source-confirmed; output runtime unverified.

### UX-07 / P2 — Watermark studio is an approximate layout preview without saying so

**Evidence:** `ui/workspace/VisualWatermarkEditor.kt:108-115,163-167,289-296`; `data/pdf/PdfBoxBackend.kt:500-537`.
**Reproduce:** enter long watermark, vary font size, enable page numbers, compare preview/output on narrow phone and larger window; change system font scale.
**Observed:** preview renders fontSize*.48sp (tile*.26sp), fixed55dp text box, variable fraction width and ellipsis; this is unrelated to page point-to-preview scale. Engine sizes actual full rasterized string from fontSizePt/page bounds. System fontScale changes preview text without changing PDF point size. Page number toggle does not draw a page number in preview.
**Expected:** output-faithful proportional rendering, or explicit approximate-preview copy before applying.
**Root:** decorative Compose Text simulation independent of engine page geometry. **Confidence:** source-confirmed mismatch; precise device appearance unverified.

### UX-08 / P2 — Slider/switch/color controls lack contextual accessible names/state

**Evidence:** `ui/workspace/VisualWatermarkEditor.kt:240-258,289-296,310-314`; `ui/workspace/VisualImageSettings.kt:34-38,153-162`.
**Reproduce:** TalkBack traverse opacity/size/rotation or brightness/contrast/saturation sliders, watermark numbering switch and color swatches.
**Observed:** labels are separate sibling Text nodes; Slider has normal percentage/progress semantics but no associated name/unit. Numbering Switch has no contentDescription/parent toggleable merge. Swatches provide color names but no semantic selected flag. Sliders generally display no actual numeric setting visually either.
**Expected:** name and current meaningful units, e.g. Rotation -35 degrees; named switch; selected swatch state.
**Root:** separate text and control semantics, rather than grouping/labeling. **Confidence:** source-confirmed omission; exact TalkBack utterance runtime-unverified. Do not report M3 Slider itself as wholly inaccessible.

### UX-09 / P2 — File grid sizing ignores safe-pane width and large text

**Evidence:** `ui/files/FilesScreen.kt:64-65,138-185`; `ui/adaptive/HingeSafeViewport.kt:49-54`.
**Reproduce:** Files grid/gallery on large folding display whose separating hinge halves the window; also test320dp phone at200% font scale. Add long filenames and enable selection.
**Observed:** columns based on LocalConfiguration.screenWidthDp (2/3/4), but shell may constrain actual content to one half-pane. Cards have fixed288/340dp height and preview150/194; action row contains two48dp icons. Four columns in420dp safe pane yields~86dp outer width before24dp card padding, less than96dp action row. Font scale never adjusts columns or heights.
**Expected:** columns follow measured actual available width and content minimums; scalable card height/action stacking.
**Root:** global configuration breakpoint plus fixed card heights ignores local constraints/text metrics.
**Confidence:** source-confirmed inconsistency; clipping/overlap extent requires runtime. Adjacent ToolsScreen uses BoxWithConstraints and fontScale-aware columns, so this is not app-wide absence of adaptation.

### UX-10 / P3 — Long-press reorder has no edge autoscroll or live insertion feedback

**Evidence:** `ui/workspace/PdfPageEditor.kt:112-123,197-212`.
**Reproduce:**20-page PDF; drag page1 toward far end past viewport edge and hold.
**Observed:** LazyRow has no retained list state or scrollBy/auto-scroll; dragged thumbnail receives translationX; order changes only on drag end from rounded total dx/126dp. No insertion marker or live rearrangement.
**Expected:** edge autoscroll and target feedback for a polished long-document drag workflow.
**Root:** local displacement/drop math, no list-aware drag controller.
**Confidence:** source-confirmed feature gap; existing earlier/later buttons remain usable fallback.

### UX-11 / P3 — Failed image output thumbnails render blank without recovery feedback

**Evidence:** `ui/components/NeonUi.kt:233-242`; Media thumbnail similar `ui/downloads/MediaWorkspace.kt:291-297`.
**Reproduce:** open Files grid or Home recent outputs when image URI is unreadable or image decoder fails.
**Observed:** AsyncImage has no placeholder/error/fallback state handling; image branch stays selected, so semantic file icon fallback is never used for failed image loads. PDF branch does have icon fallback while unavailable.
**Expected:** stable file icon/unavailable state, retaining filename and actionable row/menu.
**Root:** image request state not handled. **Confidence:** source-confirmed absent fallback; failure rendering/runtime unverified.

### UX-12 / P3 latent — Secret input fields would show raw text if gated tools become available

**Evidence:** `ui/workspace/WorkspaceScreen.kt:570-571,680,710,716`; availability policy governs reachability.
**Reproduce only in an intentionally enabled build:** enable Password PDF/Encrypted ZIP/Drive capability; Advanced; type password/token.
**Observed:** same generic text Field/KeyboardType.Text, no password visual transformation or secret-specific keyboard semantics.
**Expected:** protected field and controlled reveal for passwords/tokens.
**Root:** generic form helper used for secret strings.
**Reachability:** these tools are currently capability-gated out of normal catalog/navigation. This is dormant UI debt, not a claim of exposed default app secret-entry vulnerability. **Confidence:** source-confirmed latent code; runtime unverified.

## Additional feature gaps and verification boundaries

- Swipe-to-dismiss/delete/cancel does not exist in active UI. Output trash and restore are implemented through menus/buttons with confirmation and global Snackbar Undo (`JumloNavHost.kt:157-171`). Do not claim undo is absent. Cancellation has explicit actions, not undo.
- Direct crop supports free/square/document starting rectangles; no persistent aspect-ratio lock: all three chips pass selected=false and dragged geometry remains freely resizable (`VisualDocumentEditor.kt:338-352`, `VisualEditGeometry.kt`). No zoom/pan inside direct geometry editors, though viewer separately supports pinch/pan/double tap.
- Image-to-PDF paper AUTO/A4/LETTER/CUSTOM, dimensions, orientation AUTO/PORTRAIT/LANDSCAPE, numeric margins and fill fit exist (`WorkspaceScreen.kt:628-651`). Margins are numeric, not directly draggable; no output page-layout preview. No queue thumbnail long-press reorder: queue has menu Move up/down; PDF organize has real page long-press reorder.
- Geometric undo/redo lives in remember state and is lost on composition removal/configuration recreation. Page order history is rememberSaveable but still editor-local; draft itself is durable. Watermark, image settings, signature strokes and general field changes have no unified history. These are scope gaps, not absence of all undo/redo.
- Arabic font file is actual static regular400/no variable axes; same file is registered as all four400/500/600/700 weights (`Type.kt:114-118`). Thus all aliases point at identical outlines and the declared weight may prevent normal synthesis. Render normal vs bold Arabic to qualify impact; do not assert screenshot hierarchy verified.
- No shared-element transition implementation despite wrapper. No measured60fps/jank evidence. No device-tested TalkBack focus order, 48dp hit-region verification, contrast overlay scan, IME/landscape/dialog fitting, fold runtime or Arabic glyph-shaping qualification. These should remain explicit Phase1/device validation gates, not static pass claims.
- Unused legacy `QuietFileRow` / custom `QuietCheckbox` are not called from current screens. Custom QuietCheckbox lacks checked state semantics, but do not inflate active-screen findings with this dormant helper.

## Suggested Phase 1 order (not executed)

First reconcile scan/display/export geometry and physical RTL overlays. Then make direct edits faithfully preview and reversible (rotation state, transform history, watermark scale), expose semantic selected states and gesture alternatives, and repair responsive file sizing. Qualify all changes later on English/Arabic, system/app light/dark/dynamic, normal/large200% text, portrait/landscape, fold-safe panes, TalkBack/Switch Access, reduced motion, device failure/loading and repeated-import/edit flows. Phase0 stops here.
