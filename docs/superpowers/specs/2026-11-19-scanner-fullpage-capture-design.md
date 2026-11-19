# Scanner: reliable full-page capture

## Intent / why
The scanner currently drops to the CameraX fallback even on devices with working
Google Play Services. A transient failure to obtain the ML Kit document-scanner
module sets `fallback = true` in `SmartDocumentScanButton.kt` and it is remembered
for the rest of the session. The fallback is a 340.dp preview inside a scrolling
`LazyColumn`, uses `FIT_CENTER` (letterboxed), and captures at a fixed
`1600x2200` target resolution that crops the page. The result is the reported
"half the screen, cannot capture the whole page" experience.

The user wants: the professional scanner to actually be used, and when the
fallback is needed it must fill the screen, capture at full device resolution,
and produce accurate, professional page images.

## Goals
1. Keep the ML Kit Document Scanner (`SCANNER_MODE_FULL`, page limit 100,
   PDF + JPEG results) as the primary path and stop falling back on a single
   transient module/download failure.
2. Make the fallback a full-screen camera with the largest usable capture
   resolution, a visible page frame, and the existing four-corner perspective
   correction — same multi-page and export flow as today.

## Non-goals / anti-goals
- No new dependencies, no GMS-free custom edge-detection engine.
- No change to the PDF/JPEG result stores, publication, or export pipeline.
- No change to the download/media work in this session.
- Never silently degrade quality or crop the captured page.

## Constraints
- CameraX 1.4.0, Play-services ML Kit document scanner 16.0.0 (already present).
- Compose screen uses the project's existing QuietTheme components.
- There is no scan-specific perspective editor in the codebase; do not invent one
  here. The professional correction is ML Kit's job on the primary path.
- No device is connected in this environment; verification is build + host tests.

## Approaches
- **A (chosen): repair the primary path and upgrade the fallback.** Fixes the
  root cause of the wrong fallback and the screen the user actually sees.
- **B: repair the primary path only.** Smallest diff; the fallback remains poor
  whenever it is genuinely needed.
- **C: fully custom in-app scanner.** No Play Services dependency but the
  largest change and highest ongoing maintenance; not needed with working GMS.

## Decisions (this design)
- Replace the sticky boolean fallback with a retrying state: a failed
  `getStartScanIntent` shows an actionable, localised message and a "Retry smart
  scan" action; only an explicit user choice, or repeated failures with Play
  Services unavailable, selects the fallback.
- Keep `SCANNER_MODE_FULL`; do not lower scanner capability to make init easier.
- Fallback layout: a single full-screen `Box` camera preview (no fixed 340.dp,
  no `FIT_CENTER` letterboxing) with controls overlaid at the bottom.
- Fallback capture: configure `ImageCapture` for maximum available resolution
  (no fixed 1600x2200 target); keep page cap, rotation handling and cancellation.
- No perspective-warp editor in this change: the codebase has no such editor for
  scans (only a generic rectangle-handle helper), and ML Kit already performs
  edge detection and perspective correction on the primary path. The fallback
  captures the untouched full-resolution frame; pro correction stays with ML Kit.

## What is left to the builder
Produce the implementation plan with `writing-plans`, then implement and verify
with `:app:assembleDebug` and the existing host/UI tests.
