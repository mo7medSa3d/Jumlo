# Jumlo — Quiet Precision implementation

Updated 2026-10-17. This document describes the native Compose implementation and its remaining verification gates. It does not claim device acceptance or approved screenshots.

## Screen structure

Shared components are split into navigation, top bars, buttons/search, states, file rows, iconography and presentation-only legacy fixtures. The latter remain only for existing component tests.

`JumloNavHost.kt` owns typed routes, Android document/photo/export launchers, navigation, the account sheet and unsaved-work confirmation. Content is extracted into meaningful screen files:

- `ui/home/HomeScreen.kt`: four capability-gated frequent actions (Images to PDF, Merge PDF, Resize Image, Direct Download), search into the catalog, actual saved workspace inputs, active tasks and recent results. No promotional hero, invented progress, fake storage statistics or video-first entry.
- `ui/tools/ToolsScreen.kt`: search and category chips, count derived from the filtered eligible catalog, keyed tool rows. Navigation-only file/settings entries are omitted from the processing catalog. All launch paths recheck visibility and availability.
- `ui/files/FilesScreen.kt`: Results, Imported, Device Folder and Trash tabs; optional search and name ordering; output selection with reversible bulk trash; provider capabilities govern file operations. Permanent deletion requires confirmation of an immutable captured ID set, including “empty trash.” Later arrivals are excluded.
- `ui/downloads/DownloadsScreen.kt`: one new-download primary action, batch-link alternative, real job rows, contextual selection/cancellation of active transfers. Activity details show durable outcomes, failures, URL reentry when required and actual outputs.
- `ui/workspace/WorkspaceScreen.kt`: file selection and actual image/PDF preview, ordered input rows with overflow reorder/remove, a small set of essential fields, a single advanced sheet preserving all existing supported tool fields, and one safe-area bottom execution action. PDF viewing has no processing CTA. A blocked restored tool shows an unavailable state while preserving its inputs.
- `ui/settings/SettingsScreen.kt` and `ui/components/AccountHubModal.kt`: account/data hub and settings remain native and use durable preferences. The root app bar has one account entry; settings is reached through the hub.

Screen-owned jobs, sources, outputs, tree and busy state are collected through `collectAsStateWithLifecycle`. The shell observes only navigation-required form/preferences and snackbar state; account storage/output collection starts when the hub opens. Lists use stable item IDs. Catalog filtering and ordered source mapping are remembered from their real inputs.

## Color and typography

Light background `#FAFAF9`, primary `#4F3DFF`; dark background `#0B0B0D`, primary `#818CF8`, action fill `#B8ADFF`. Neutral surfaces provide separation; category color is limited mainly to icons in retained components. The root account button uses a semantic tonal surface instead of a gradient.

The fixed brand palette is used on all supported API levels. Material 3 semantic roles drive new screens. Numeric sRGB calculations checked 18 opaque foreground/background text pairs at >=4.5:1 and both outline/surface pairs at >=3:1. Results: `verification/production-2026-10-17/ui/contrast-tokens.log`. These checks do not establish contrast for every alpha overlay, image, disabled state or resolved device surface.

Latin uses bundled IBM Plex Sans; Arabic explicitly uses bundled IBM Plex Sans Arabic for both Material and Quiet text roles. The main screen-title role is 26sp/32sp, body 16sp/24sp and metadata 13sp/18sp. No fonts are downloaded at runtime. Existing font license/notices remain part of the repository.

New visible copy is in paired `production_ui.xml` English/Arabic resources. Existing registry tool/category labels remain the source of localized catalog names. Filenames/byte metadata and URL fields use LTR text direction even in RTL layout; actual document/image pixels are never mirrored.

## Interaction and adaptation

Four destinations remain. The navigation rail starts at actual available width >=600dp, without an orientation heuristic. Home uses one, two or four columns based on its available content width. The workspace remains centered with a bounded content width. Root navigation occupies its own layout space, so content is not covered by a second floating bottom action. The workspace footer accounts for IME and navigation bars.

Rounded Material icons are used consistently. New overflow icon actions use Material 48dp touch regions; the account action is at least 48dp. Non-action status rows have no fake details button. Main commitment actions are explicit; destructive provider deletion and managed-trash purge require confirmation.

Normal system back is owned by Navigation Compose. Only a dirty workspace intercepts `PredictiveBackHandler`: the gesture must complete before Keep editing / Save and leave / Discard changes is shown. Save/discard waits for the ViewModel durable-write callback, and buttons disable while busy. Automatic recovery drafts and the explicit save baseline are separate. URLs/passwords must be reentered after process restart. Reduced motion disables route transitions; navigation item colors/layout no longer use decorative spring animation.

Input previews use the existing bounded, background decoder in the ViewModel: sampled images, bounded PDF raster dimensions, cancellation propagation and resource closure. UI effects retire old preview frames; obsolete page pixels are cleared during decoding. No before/after size is fabricated.

## Verification and remaining gates

Static checks cover XML syntax, unique resource names, EN/AR key parity, UI string references, removal of the landscape heuristic and removal of immediate global trash purge. See `verification/production-2026-10-17/ui/static-validation.log`.

Instrumentation source includes a real-activity frequent-action/download-input smoke test and an overflow test proving opening the menu does not execute a row/destructive action. Existing component fixture tests are labeled as component-only checks, not route acceptance. Native compile and instrumentation results belong to the build report; test source is not evidence of execution.

Before release, collect actual route/state screenshots at 360dp, 412dp, tablet and landscape, in light/dark and English/Arabic, with 200% fonts. Verify TalkBack focus/order, keyboard access, reduced-motion settings, fold/hinge avoidance, gesture/three-button navigation and IME. Separating or fully occluding folding features now constrain the route to a contiguous safe pane; physical fold, modal-window and posture-transition validation is still required. No approved screenshot golden set, real-device jank numbers, startup measurements, ANR evidence or performance SLO attainment is asserted here.

Home favorites are explicitly selected/reordered on device and saved through DataStore. Empty or wholly unavailable saved choices fall back to available members of the four original defaults. There is no usage tracking or automatic ranking. A fully immutable aggregate UiState per screen remains follow-up work. All advanced settings remain accessible, but each less-frequent tool still needs a device-specific essential-field/validation review. Common stable engine errors are mapped to localized recovery messages for snackbars, previews and activity outcomes. Unknown errors use a concise localized fallback; diagnostic codes remain in the local log.

## Local favorites and fold-aware viewport follow-up

Home’s Customize quick actions editor adds/removes up to four eligible processing tools, supports accessible move-earlier/move-later controls, resets defaults, and saves the exact selected order to the existing local preferences store. Non-processing destinations and blocked tools cannot become favorites. Arbitrary eligible tools use their actual localized title and registry icon. Editor changes survive recreation; Cancel/back does not write them.

`HingeSafeViewport` listens to lifecycle-aware `WindowInfoTracker` updates from the existing AndroidX Window dependency. Separating folds and FULL occlusion features split the actual root window bounds. The pure `safeViewport` policy selects the largest contiguous pane; equal vertical panes choose the reading start (right for RTL), equal horizontal panes choose the top. It retains physical pixels throughout measurement and uses absolute `place`, so root offsets/insets do not cause a second RTL mirror or dp rounding. Width adaptation then uses the resulting safe pane. No dual-pane UI, custom hinge emulation or synthetic device proof is claimed.

Pure policies were tested first against the old constant-order/unsplit-viewport behavior (9 failed, 2 passed), then implemented and rerun (11 passed). Logs and a standalone Kotlin runner are under `verification/production-2026-10-17/ui/`. Persistence across repository recreation and accessible reorder-to-save instrumentation tests are authored but require device execution.
