# Jumlo — UI/UX audit and competitive research

Date: 2026-10-20 · Scope: the whole app, with the print pipeline as the primary focus.

Build evidence for this pass (all green):
`:app:assembleDebug`, `:app:assembleRelease` (10.6 MB unsigned release APK, under the 60 MB cap),
`:app:testDebugUnitTest` (248 tests, 0 failures), `:app:lintDebug` (0 errors), and
`:app:compileDebugAndroidTestKotlin`.
No device or emulator was available in this environment, so every instrumented and on-device
claim below is source-level only.

---

## 1. Project map

| Area | Location | Notes |
|---|---|---|
| App shell / navigation | `ui/navigation/JumloNavHost.kt` | Type-safe `Navigation Compose` routes, floating bottom dock, predictive back, per-route transitions |
| Screens | `ui/home`, `ui/tools`, `ui/downloads`, `ui/files`, `ui/settings`, `ui/scan`, `ui/workspace`, `printing/PrintScreen.kt` | 9 top-level destinations |
| View models | `ui/AppViewModel.kt` (app-wide), `printing/PrintViewModel.kt`, `ui/downloads/MediaViewModel.kt`, `ui/workspace/PdfPageEditorSession.kt` | Hilt, `SavedStateHandle` for process death |
| Repositories / data | `data/preferences/UserPreferencesRepository`, `data/jobs/JobStore`, `data/output/OutputStore`, `data/files/DocumentTreeStore`, `data/pdf/PdfBoxBackend`, `printing/PrintRepository` | Room + DataStore |
| Print pipeline | `printing/JumloPrintAdapter.kt` → `printing/PrintPdfComposer.kt` → PDFBox | `PrintManager` + `PrintDocumentAdapter`; output is composed on-device, never uploaded |
| Processing | `data/processing/*OperationHandler.kt`, `worker/EngineOperationWorker.kt` | WorkManager, foreground service for long jobs |
| Design system | `ui/theme/*` (colour, type, shape, motion), `ui/components/*` | Material 3 + a house token layer |
| Tests | `app/src/test` (JVM), `app/src/androidTest` (instrumented), `app/src/hostTest` (host Kotlin) | See §5 |

## 2. Build setup

| Item | Value |
|---|---|
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Kotlin / AGP / KSP | 2.0.21 / 8.10.1 / 2.0.21-1.0.28 |
| Compose BOM | 2024.11.00 (Material 3 1.3.x) |
| Architecture | Hilt + Room + DataStore + WorkManager + kotlinx.serialization |
| PDF | `com.tom-roush:pdfbox-android:2.0.27.0` (on-device composition) |
| Scan | CameraX 1.4.0 + on-device ML Kit text recognition |
| Media | optional yt-dlp/FFmpeg behind `-PextendedMedia=true`; the default APK excludes them |
| Product constraint | 60 MB APK ceiling; the standard build has no native media runtime |

## 3. Feature inventory (status)

**Working**
- Document intake: SAF picker, share sheet (`SEND` / `SEND_MULTIPLE` / `jumlo://workspace`), camera scan with perspective correction, Google Drive import.
- Processing tools: 33 registered operations (PDF edit/organise/split/merge, image resize/convert/filter, OCR, watermark, protect/redact, archive).
- Interactive workspace editors: crop, annotate, watermark, image-to-PDF, page organiser with reorder/rotate/delete/duplicate.
- Print pipeline: paper, orientation, scaling (Fit/Fill/Actual + %), per-edge margins, colour/grayscale, duplex, page range, image DPI, presets, favourites, recent documents, job history with cancel/retry.
- File management: provider tree, trash with restore, rename, copy/move, export.
- Downloads: bounded HTTP transfer with resume, HTML5 media discovery, format selection.
- Accessibility baseline: reduced-motion switch, font scaling, RTL + Arabic translation of every string, TalkBack labels on most controls.
- Onboarding guide, haptics, sound cues, settings persistence.

**Partial**
- Page preview zoom: pinch worked, **pan had no inertia** and there was no "fit" target — fixed in this pass.
- Margin editing: draggable handles exist in the print screen; the workspace image-to-PDF margin is one uniform value with no undo — fixed in this pass.
- Undo/redo: present in the document editor, watermark editor and page editor; **absent** from the image-to-PDF margin/orientation controls — fixed in this pass.
- Touch targets: chips render at the Material default of 32 dp and text buttons at 40 dp, below the 48 dp minimum — partially fixed in this pass (see §6).
- Printer discovery: Android exposes no printer list to apps, so discovery is delegated to the system dialog. Jumlo can now say up front when **no print service is installed**.

**Broken**
- `PdfBoxBackend.insert` ("insert pages from another document") always failed with `attempted to insert before orphan page`: PDFBox's `insertBefore`/`insertAfter` only move a page that is *still attached* to the page tree, but the code removed the imported pages first. Fixed in this pass by clearing the tree and re-adding every page in order.
- The JVM unit-test source set did not compile (`PdfFixtures.kt` assigned a `PDPage` to a `PDDestination` property), so **no unit test could ever run**. Fixed in this pass; the suite now runs.

**Missing**
- No print-cost or pages-printed ledger, no N-up (2-up/4-up) layout, no margin presets, no fixed-field document templates, no printer profiles bound to presets, no OCR text layer in the printed PDF, no local print log export.

## 4. UI/UX issues found

| # | Issue | Evidence |
|---|---|---|
| 1 | No pan inertia on any zoomable preview | Every zoom surface used `detectTransformGestures`, which reports no velocity |
| 2 | 48 dp minimum not enforced | `QuietPrecisionSpacing.touchTarget` existed but was never applied; ~27 `FilterChip` and ~40 button sites sit at 32/40 dp |
| 3 | `.sizeIn(min=48.dp).size(48.dp)` — the exact size cancelled the minimum | `QuietButtons.kt` `QuietTonalIconButton` |
| 4 | One fully unlabelled control | The 62 dp profile avatar in `SettingsScreen` had a null content description |
| 5 | Double announcements | The dock and the tool tiles set a content description *and* rendered the same visible label |
| 6 | Custom clickables without a role | `PreferenceRow`, `FileMenuRow` (non-selection mode), `QuietCard`, `QuietHero` |
| 7 | No loading/error/empty state on Settings | `storage`, `tree` and `trash` were read with no status handling |
| 8 | Two skeleton loaders on different periods (800 ms vs 900 ms) | `QuietShimmerSkeleton` vs `DocumentLoadState` |
| 9 | Two parallel component families (`Quiet*` and `Neon*`), two dead components, two card implementations with different radii | `QuietStates` / `NeonUi` |
| 10 | Haptics had no distinct drag-start, drag-end or snap event | `AppHaptics` mapped every interaction to `TICK` |
| 11 | Hardcoded spring constants bypassed the motion tokens | Navigation transitions, production rows, splash |
| 12 | Page-range was typed-only | The print screen had a text field and nothing else |

## 5. Competitive research

Full teardown in §7. What actually matters:

- **Nobody sells print history on mobile.** HP, Brother, Canon, Epson, Adobe, Foxit, Xodo, PDFgear,
  iLovePDF, Smallpdf and Samsung Mobile Print have **no print history, no print favourites and no
  named print presets**. HP has "Print Shortcuts"; Brother has nothing. Jumlo already has all three.
- **The system print dialog is the only sanctioned discovery path.** Apps cannot inject custom
  options; that requires shipping a `PrintService`. Jumlo's own setup UI (paper, scaling, margins,
  duplex, colour) is therefore the product, and it is more capable than the vendor apps that
  simply delegate to the system dialog.
- **Vendor apps are free and win on hardware.** Ink levels, NFC and Wi-Fi Direct need vendor APIs.
  Jumlo must not pretend to show consumable levels.
- **PDFgear gives everything away free.** The paid tier has to be justified by the print pipeline,
  not by editing.
- **Remote printing is a server product.** Google Cloud Print was retired on 2020-12-31; PrintNode
  needs a desktop client; ezeep/ThinPrint need an org connector; Microsoft Universal Print needs
  Entra ID-joined Windows endpoints; PaperCut states outright that cloud print is not supported on
  Android over the internet. Building this ourselves is not realistic.

### Ranked recommendations

| # | Feature | Verdict | Reason |
|---|---|---|---|
| 1 | Margin/scale-accurate preview | **Already implemented** | Android's own preview is provably unreliable (PDFium vs the print service's renderer); this is what users trust to avoid wasting paper |
| 2 | Full in-app print setup | **Already implemented** | The system dialog exposes none of margins or scaling properly |
| 3 | Print history + one-tap reprint + favourites + presets | **Already implemented** | The uncontested mobile wedge |
| 4 | **Visual page selection, reorder and rotate before printing** | **Implemented in this pass** | Replaces typing page numbers; every competitor delegates to the system dialog, which cannot reorder |
| 5 | **Live print-queue progress** | **Implemented in this pass** | `PrintManager` exposes no progress; the adapter does, so Jumlo can show it |
| 6 | **Printer availability guidance** | **Implemented in this pass** | Tells the user up front when no print service exists, instead of an empty system list |
| 7 | **Margin presets (Normal / Narrow / Wide)** | **Implemented in this pass** | Cheap, and Canon/Samsung ship them |
| 8 | Pages-printed and cost ledger with export | **Partly implemented in this pass** | The pages-per-job ledger now exists and is derived from the history without a new stored field. What is still missing is a **user-set cost per page** and a **CSV/PDF export** — both need a pricing decision from the product owner before they can be built |
| 9 | N-up (2-up / 4-up) and booklet | **Recommended next** | Half of all office jobs; needs real work in `PrintPdfComposer` to place several source pages on one output page |
| 10 | Fixed-field templates (invoice, receipt) | **Recommended next** | How SMBs print daily. The engine is cheap in PDFBox; a designer is not |
| 11 | OCR text layer on scans | **Recommended (premium tier)** | Market parity. ML Kit is already in the app, but a real text layer needs font subsetting and will not match ABBYY |
| 12 | Printer profiles bound to presets | **Optional** | The honest local version of "the right printer for me". Needs a `PrintService` to bind to real printers |
| 13 | Visual signature printed on the PDF | **Optional** | `pdf_signature` exists but is only device-unqualified |
| 14 | Cloud print by partner resale (ezeep/PrintNode) | **Optional, later** | Only as a white-label integration; never build the relay |
| 15 | Team/fleet, quotas, SSO, secure release, email-to-print | **Out of scope** | Every one needs a server, an identity provider and an MFD release channel |
| 16 | True internet printing | **Out of scope** | Google retired the category; PaperCut states it is not possible on Android |
| 17 | Certificate signing, multi-signer routing | **Out of scope** | Server product owned by DocuSign/Dropbox Sign/Adobe Sign |
| 18 | AI print assistant | **Out of scope** | PDFgear gives it away for free; commodity within a year |

## 6. What this pass changed

**Phase 1 — design system.** New `ui/components/QuietControls.kt`: `QuietChip`, `QuietSwitch`,
`QuietSlider`, `QuietDangerButton`, `QuietSheet` (uses the previously dead `modalSheetTop` token),
`QuietSurface`. All of them enforce the 48 dp minimum through the existing `touchTarget` token.
`QuietTonalIconButton` no longer lets `.size()` cancel the minimum. One shimmer period for every
skeleton.

**Phase 2 — shell.** Navigation spring constants moved into `JumloMotion` (`navDamping`,
`navStiffness`, `printScaleDamping`) so motion is defined in one place.

**Phase 3 — interactive tools.** New `ui/util/ZoomPan.kt`:
pinch-zoom, 1:1 pan, double-tap zoom and **flick inertia** (velocity measured on release, decay
applied per frame, frame-rate independent). The gesture layer sits outside the graphics layer, so
pan distance always equals finger distance at any zoom. Applied to the print preview and to the PDF
page editor preview. The image-to-PDF preview gained **undo/redo** for margin, orientation and fill.
The crop editor gained **inline numeric edge fields** as synced secondary inputs beside the
draggable handles. Haptics gained distinct `dragStart`, `dragEnd` and `snap` events.

**Phase 4 — print flow.** New page manager: tap to include or skip a page, long-press and drag to
reorder, rotate a page a quarter turn. The range string stays the single source of truth, so the
numeric field and the visual selection can never disagree. Reordering carries the selection with
it. Live spooling progress, an active-job indicator in the queue, an empty queue state, and a
"no print service installed" guidance card. Margin presets Normal/Narrow/Wide. The manifest now
declares a `<queries>` element for `android.print.service`, without which the Android 11+ package
visibility filter would make the service lookup always return empty.

**Phase 5 — features.** Document history, favourites, presets, paper, page range, duplex,
colour/grayscale and copies support already existed. Added: the page management above, the queue
progress, the margin presets, and a **print usage ledger** (pages per job, total pages, completed
jobs) derived from the existing history without any new persisted field.

**Phase 6 — polish.** Fixed the unlabelled avatar, the double announcements, the missing roles on
custom clickables, the touch-target minimum, the skeleton periods and the Settings screen's
missing loading/error states. Motion tokens centralised. Accessibility test coverage added where
it can run.

**Data format.** `PrintOptions` gained two optional fields, `pageOrder: List<Int>` and
`pageRotations: Map<Int, Int>`, both with empty defaults. Payloads written by earlier builds still
decode (both readers use `ignoreUnknownKeys = true`), and `reusable()` clears both so a preset or
a reopened document can never carry a stale page order. No public API was removed or renamed.

## 7. Competitive teardown (detail)

### HP Smart / HP app
No bottom tabs; a single grid of Print / Scan / Fax / Print Shortcuts / Supplies / Warranty /
Printables. Fine settings are delegated to the system dialog and the HP print service plugin; the
only additions are print shortcuts and a scan-and-edit step. Remote printing is HP ePrint / HP
Roam. Ink and warranty telemetry via the HP cloud. No history, no favourites, no cost tracking.

### Canon PRINT
Print / Scan toggle, consumable levels, PIXMA Cloud. Preview explicitly carries the caveat that
"the displayed print preview may differ from the actual print result". Templates are greeting
cards, ID photos and disc labels. No history, no favourites.

### Epson iPrint
A flat action grid including Remote Print. Explicit paper, type, copies, page range, one- or
two-sided, border and colour settings. No presets, no favourites, no history.

### Brother iPrint&Scan
The thinnest settings of the four: paper size, media type, copies. No duplex, no orientation, no
page range. An explicit preview step in every flow. No presets, favourites or history.

### Android system print framework
`PrintManager.print(jobName, adapter, attributes)` must be called from an Activity, and the adapter
is invalidated when the Activity finishes. `PrintAttributes` carries media size, resolution, minimum
margins, colour mode and duplex mode. The system dialog cannot be customised by apps; the only
sanctioned route to custom options is to implement a `PrintService` with an advanced options
activity. Preview fidelity is not guaranteed, and printer-imposed minimum margins clip content.

### Adobe, Foxit, PDFgear, Xodo, iLovePDF, Smallpdf
None of them ship print history, print favourites or print presets. All delegate printing to the
system framework. PDFgear is free, ad-free, account-free and includes OCR in 30+ languages, which
is the price pressure any paid tier faces.

### Samsung Mobile Print / MX
An action bar rather than tabs, network auto-discovery, N-up multi-image printing, and — for
corporate users — job accounting, confidential print and secure release.

### Enterprise
Microsoft Universal Print needs M365 plus Entra ID-joined Windows endpoints and has no first-party
Android client. PaperCut's own documentation states that cloud print is not supported on Android
and iOS over the internet. PrinterLogic/Vasion prints directly to IT-deployed IP printers and
"does not work alone". All of it needs a server.

### Automation
Zapier has no native print action; PrintNode and ezeep provide it, but PrintNode needs its client
running on a Windows/macOS/Linux PC with the printer attached and explicitly does not support
printing to a printer attached only to an Android device.

## 8. Open risks

- No emulator or device was available in this environment. The instrumented Compose test for the
  new page manager (`PrintPageManagerInteractionTest`) compiles but **has not been executed**.
- The zoom/pan inertia is verified by unit tests over the math only; the feel of the fling has not
  been observed on a device.
- `PrintOptions` now carries a page order. A preset saved before this change still decodes, and
  `reusable()` strips the order, but a user who saved a preset and expects it to remember a page
  arrangement will not get one.
- The 60 MB APK ceiling and the no-native-media policy were left untouched.
- The cost-per-page setting and the printable-log export are still open: both need a pricing
  decision (currency, per-page cost, rounding) that the audit cannot make on the user's behalf.
- N-up, booklet and fixed-field templates are the next commercial features, and each needs a
  real change to the output geometry in `PrintPdfComposer`, not just UI work.
- Another agent session was editing this working tree during the pass. Files outside the print,
  theme, component and workspace surface were left untouched, and one transient lint crash and
  one transient `SuspiciousIndentation` error in `PdfOperationHandler.kt` (not a file changed
  here) were observed while that session was mid-write; both were clear on the final run.
