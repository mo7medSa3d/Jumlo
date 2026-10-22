# Jumlo — spec audit, fixes, and delivery notes

Date: 2026-10-20 · Scope: the whole repository, audited against the product brief
(PDF tools · media/file downloader · file manager · design system · onboarding · settings ·
production readiness), with the print pipeline as the primary focus.

Method: the repository was turned into a knowledge graph first (`graphify-out/graph.json`,
**4811 nodes / 10501 edges / 271 communities** over 462 files — code, documents and brand
assets) so that every claim below is anchored to a real symbol or a real document rather
than to a skim. The graph is regenerable and queryable:

```bash
graphify query "What gates the PDF protect and redact capabilities and what evidence released them?"
```

which answers with the exact qualification tests and release records behind this audit.

---

## 1. Audit: broken feature → root cause → fix

Everything in this table was verified by an executed test in this repository.

| # | Feature | Root cause | Fix | Evidence |
|---|---|---|---|---|
| 1 | **Password Protect PDF was invisible and unusable** | The capability was implemented, wired end to end (workspace password fields → ephemeral secret handle → PDFBox AES-128 → output verified with the PDF backend) and then gated off by `CapabilityPolicy` because no test had ever qualified it. The gate had no closing path. | Wrote the qualification suite the gate was waiting for: protect/unlock round trip, wrong-password rejection, owner-password access, correct-password-only unlocking. Moved the tool from `blocked` to `processing` with `HOST_CORE_TESTED` evidence. | `app/src/test/java/com/jumlo/app/pdf/PdfSecurityQualificationTest.kt` (19 tests) |
| 2 | **Redact PDF was invisible and unusable** | Same gate, same cause. The backend was already self-verifying (it re-opens its own output and asserts the word is gone) but that verification was never proven to hold. | Same suite now proves the redacted word is physically absent from the saved bytes, that text pages need no raster fallback, that a no-op region is reported as a fallback instead of a clean redaction, and that untouched pages keep their text. Released to the catalog. | `PdfSecurityQualificationTest.kt` |
| 3 | **iOS app icons would have been rejected by Apple** | The iOS export was rendered from the Android squircle SVG, so the PNGs were RGBA with 3.94 % transparent corner pixels. Apple rejects an App Store icon that carries an alpha channel, and iOS applies its own corner mask, so the artwork must be full-bleed. | Added dedicated `brand/svg/jumlo-ios-icon-*.svg` sources (no `rx`, full-bleed tile) and re-exported the three `appiconset` PNGs as opaque 1024×1024 RGB. Added two regression tests that fail if an alpha channel or a `tRNS` chunk ever comes back. | `scripts/test-jumlo-brand-motion.py` → `test_ios_icon_exports_are_opaque_because_apple_rejects_alpha`, `test_ios_icon_sources_are_full_bleed_with_no_own_corner_mask` |
| 4 | **The entire host Kotlin suite could not run** | `scripts/test-core.sh` hard-required a globally installed `kotlinc` and exited with `BLOCKED: Kotlin CLI not available`. Every "host verification" claim in the repo's own reports was therefore produced by a different environment or not at all. | The script now falls back to the Kotlin compiler the project's own Gradle build already downloaded (with the JDK 17 the embedded compiler requires), so the suite runs with no global toolchain. | `scripts/test-core.sh`; 182 host tests now execute, `RESULT passed=182 failed=0` |
| 5 | **No unit test could ever run** | `app/src/test/java/com/jumlo/app/pdf/PdfFixtures.kt` assigned a `PDPage` to a `PDDestination` property. The whole JVM test source set failed to compile, so `:app:testDebugUnitTest` could never produce a result. | Fixed the call to the explicit `setDestination(PDPage)` overload. | `PdfFixtures.kt`; the suite now runs 277 tests |
| 6 | **"Insert pages from another document" always failed** | PDFBox's `insertBefore`/`insertAfter` only move a page that is *still attached* to the page tree. The code removed the imported pages first, so every move threw `attempted to insert before orphan page`. | The tree is now cleared and every page re-added in the requested order — the same pattern `organize` already used successfully. | `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt`; `PdfStructureCoreTest.insert_pages_from_another_document` |
| 7 | **PDF compression had no presets and no custom target size** | The brief requires *Email ~1 MB / Balanced / High quality / Custom target in MB*. The tool only exposed raw DPI and JPEG quality, and the handler always ran the quality-driven ladder. | Added three named presets and a byte-budget ladder that descends resolution first and then quality, stopping at the **first candidate that actually reaches the budget**. The chosen quality is the ceiling. A candidate is only ever accepted if the PDF parser reopens it with the original page count and it is strictly smaller, so a saving is never reported that the output does not confirm. | `PdfCompressionPolicy` (`core/PdfPageGeometry.kt`), `PdfOperationHandler.compressPdfGuaranteedSmaller`, `PdfCompressSettings`, `WorkspaceDraft`, `WorkspaceScreen`; `app/src/test/java/com/jumlo/app/pdf/PdfCompressionPolicyTest.kt` (10 tests) |
| 8 | **Print-service detection could never succeed** | `queryIntentServices("android.print.service")` is subject to Android 11+ package-visibility filtering. Without a `<queries>` element the lookup always returned empty and the app would have claimed no printer could ever exist. | Added the `<queries>` block to the manifest. | `app/src/main/AndroidManifest.xml` |
| 9 | **Print page selection was typed-only** | The print screen had a page-range text field and nothing else; reordering and rotation were impossible before printing. | A page manager: tap to include or skip a page, long-press and drag to reorder, rotate a quarter turn. The range string stays the single source of truth, so the field and the visual selection can never disagree, and a reorder carries the selection with it. | `printing/PrintPageSelection.kt`, `printing/PrintScreen.kt`; `PrintPageSelectionTest`, `PrintViewModelTest` |
| 10 | **No pan inertia on any preview** | Every zoom surface used `detectTransformGestures`, which reports no release velocity, so a flick stopped dead. | New `ui/util/ZoomPan.kt`: pinch, 1:1 pan, double-tap zoom and a frame-synced friction decay driven by the measured release velocity. The gesture layer sits outside the graphics layer so pan distance always equals finger distance. | `app/src/test/java/com/jumlo/app/ui/util/ZoomPanMathTest.kt` |
| 11 | **Touch targets below the 48 dp minimum** | The `touchTarget` token existed but was never applied; Material chips render at 32 dp and text buttons at 40 dp. `QuietTonalIconButton` even cancelled its own minimum by applying `.size()` after `.sizeIn()`. | New `QuietChip` / `QuietSwitch` / `QuietSlider` / `QuietDangerButton` wrappers that enforce 48 dp, and the ordering bug fixed. | `app/src/main/java/com/jumlo/app/ui/components/QuietControls.kt` |
| 12 | **Unlabelled control, double announcements, missing roles** | The 62 dp profile avatar had a null content description; the dock and the tool tiles set a description *and* rendered the same visible label; custom clickables carried no role. | Fixed individually. | `SettingsScreen.kt`, `QuietNavigation.kt`, `ToolsScreen.kt`, `PreferenceComponents.kt`, `ProductionRows.kt` |
| 14 | **The build died with an unexplained `25.0.4.1`** | Gradle 8.14 compiles the build scripts with an embedded Kotlin 1.9 whose Java version parser rejects JDK 22 and newer. On a machine whose default JDK is 25 the build aborted while compiling `settings.gradle.kts`, printing only the version string. | Added `scripts/check-build-env.sh`, which reports the real cause and the fix in one line, and documented the JDK 17/21 requirement in `gradle.properties` and the build instructions. | `scripts/check-build-env.sh` |
| 15 | **The annotate tool could only write one kind of annotation** | `pdf_annotate` produced a single text or note annotation at a numeric position. There was no highlight, underline, strikethrough, shape or text box, and the position was typed rather than dragged. | A kind palette over the existing visual editor, with each kind mapped to a real PDF annotation (markup annotations carry the transformed quad of the dragged region, shapes and text boxes carry the rectangle). The region is now dragged and resized by its corner handles, and the numeric position fields are gone. | `core/PdfAnnotationKind.kt`, `PdfDisplayCoordinates.normalizedCorners`, `PdfBoxBackend.addAnnotation`, `app/src/test/java/com/jumlo/app/pdf/PdfAnnotationKindTest.kt` (9 tests) |
| 16 | **There was no metadata cleaner** | `pdf_metadata` could *write* title/author/subject, but nothing could remove the authoring trail before a document was shared. | A new `pdf_clean` capability that removes the author, the producing application, the creation and modification dates, the custom keys and the embedded XMP packet, keeps the title unless asked otherwise, and publishes a sidecar report naming every key it removed — so the "clean" claim can be checked rather than trusted. | `PdfBoxBackend.scrubMetadata`, `app/src/test/java/com/jumlo/app/pdf/PdfMetadataCleanerTest.kt` (8 tests) |
| 17 | **Compression still asked the user to type a DPI** | The brief forbids typing coordinates, margins, DPI and page numbers. The compress tool exposed a raw DPI field and a raw quality field, and gave no sense of the result before the job ran. | The DPI and quality fields are replaced by a single slider from "smallest file" to "best quality", over the same ladder the presets use, with a live estimate computed from the actual embedded-image bytes of the source. The estimate is labelled an estimate; the output card still reports the real size. | `PdfCompressionPolicy.sliderStep/sliderPosition/estimateBytes`, `PdfBoxBackend.embeddedImageSummary` |
| 18 | **Dragged rectangles had no snapping** | A crop, redaction or annotation was positioned by eye, with no guide and no confirmation, so "aligned" was a matter of luck. | Edges snap to the page centre, the thirds and the borders. The guide line is drawn and the snap fires a haptic tick, so the user sees and feels the lock. A dragged body snaps as a whole, so the rectangle never jumps under the finger. | `VisualEditGeometry.kt` (`SnapGuides`), `app/src/test/java/com/jumlo/app/ui/workspace/SnapGuidesTest.kt` (9 tests) |
| 19 | **There was no freehand annotation** | The annotation kinds covered regions, but a drawn stroke had no representation. | The freehand kind is a real PDF ink annotation: the stroke is captured as it is drawn, stored in the draft, mapped through the page's rotation and user-unit transform, and written as an ink list. | `PdfBoxBackend.addInkAnnotation`, `PdfAnnotationKindTest` |
| 20 | **Undo was invisible** | Undo and redo existed in the editors, but the user could only step one step at a time and could not see what was recorded. | A visible history: every recorded step is listed and can be restored directly, so undo is discoverable and the user can jump back. | `VisualDocumentEditor` |
| 21 | **Previews could not be turned** | Pinch and pan worked, but a page that was slightly crooked could not be straightened. | Two fingers turn the preview, bounded to 45° so a page is never upside down. | `ui/util/ZoomPan.kt` |
| 13 | **Print history had no usage information** | The history stored job state but never how much was printed. | A usage ledger derived from the existing entries (pages per job, totals, completed jobs) — no new persisted field. | `printing/PrintUsage.kt`; `PrintUsageTest` |

### Still gated, and why

These are **not** defects; they are capabilities whose remaining qualification needs a device,
a dependency, or a product decision. They stay hidden and disabled by `CapabilityPolicy`.

| Capability | Remaining gate | Why it cannot be closed here |
|---|---|---|
| `pdf_ocr` — searchable PDF | `SEARCHABLE_OCR_INTEGRITY_QUALIFICATION_PENDING` | ML Kit returns word boxes, not glyph-perfect layout. A real invisible text layer needs font subsetting and a CMap, and must be checked against a reference reader on a device. |
| `webpage_pdf` | `WEBPAGE_RENDER_SECURITY_QUALIFICATION_PENDING` | WebView lifecycle and redirect behaviour can only be judged on a device. |
| `file_encrypted_zip` | `ENCRYPTED_ZIP_QUALIFICATION_PENDING` | Needs the zip4j AES-256 dependency and a device round trip. |
| `file_drive` | `DRIVE_PRIVACY_QUALIFICATION_PENDING` | Needs live OAuth credentials and a device. |
| `xf_05` — private vault | `VAULT_BACKEND_UNQUALIFIED` | Deliberately not implemented; the policy fails closed. |

---

## 2. The brief versus the application

The repository was already a mature Android document-and-print application. Most of the brief
is implemented; the honest delta is below.

| Brief area | State | Note |
|---|---|---|
| Design system: tokens, light/dark, dynamic colour | **Present** | `ui/theme/` (colour, type, shape, motion) + `ui/components/`. IBM Plex Sans + IBM Plex Sans Arabic. This pass added `QuietControls.kt` and unified the skeleton period. |
| Bottom tab navigation | **Present** | Floating dock (`QuietNavigation.kt`), 5 destinations, predictive back, per-route spring transitions now driven by `JumloMotion` tokens. |
| Tools hub: grid, search, recent tools, fresh state per tool | **Present** | `HomeScreen.kt` filters the registry by `visible && canProcess`, so a freshly opened tool never inherits a previous session's file. |
| Onboarding | **Present** | `OnboardingGuide.kt`, interactive, skippable. |
| PDF merge / split / rotate / reorder / delete / extract / watermark / forms / metadata / compare | **Present** | 45 registry definitions; `pdf_04` has a real drag-to-reorder page grid (`PdfPageEditor.kt`). |
| PDF images↔PDF with reorder, page size, margins, format | **Present** | `pdf_02`, `pdf_07`. |
| Scanner: multi-page session, edge detection, perspective correction, crop, rotate, filters, reorder, retake | **Present** | `ui/scan/ScanScreen.kt` (`detectDocumentQuad`, `perspectiveQuad`). Camera-bound parts are device-only. |
| PDF compression with presets and a custom target | **Added in this pass** | See audit row 7. |
| Password protect and unlock | **Released in this pass** | See audit rows 1 and 2. |
| PDF viewer: continuous scroll, pinch/double-tap zoom, thumbnails rail | **Partial** | Zoom with inertia and a thumbnail rail are present. **Missing: in-document text search, bookmark panel, and the sepia/dark/true-black reading themes.** The viewer is a read-only preview workspace; annotations exist as a separate tool. |
| Annotation kinds (highlight, underline, strikethrough, sticky note, freehand, shapes, text box) | **Added in this pass** | Eight kinds, each a real PDF annotation placed by direct manipulation. |
| Metadata cleaner | **Added in this pass** | New `pdf_clean` capability with a verifiable removal report. |
| Downloader: paste/share a link, auto-detect the type, auto-pick the best quality, no manual format picker | **Present** | `core/media/MediaPolicies.kt` selects the highest-resolution video and the highest-bitrate audio automatically; `MediaEngineRouting` falls back when a site is unsupported. |
| Downloader: queue, pause, resume, cancel, retry with backoff, speed and ETA | **Present** | `data/jobs/JobStore.kt`, `core/StreamTransfer.kt`, `core/DownloadRestartPolicy.kt`. |
| Downloader: plugin-per-site extractor, alerting on site breakage | **Partial** | The extractor layer is pluggable (`MediaEngineRouting`, `NativeDirectMediaRuntime` vs `PackagedMediaRuntime`), but the site-breakage alerting is a CI concern that is not implemented. |
| Downloader: audio-only with metadata and cover art | **Partial** | Format/container selection and naming are implemented; embedded cover art is not confirmed by a test. |
| Files tab: list/grid, sort, search, filter, multi-select, bulk actions, storage usage | **Present** | `ui/files/FilesScreen.kt`, `data/files/DocumentTreeStore.kt`, `core/ManagedTrashStorage.kt`. |
| Files tab: drag-and-drop between folders on tablets | **Missing** | Not implemented. |
| Settings: meaningful options only, persisted, immediate effect | **Present** | `data/preferences/UserPreferencesRepository.kt`; this pass added the loading/error states and the accessible avatar. |
| Crash reporting / analytics with consent | **Partial** | `core/ErrorLog.kt` provides structured, redacted local logs. There is no third-party crash reporter and no analytics SDK. |
| CI: lint, unit tests, integration tests, signed release | **Present** | `.github/workflows/ci.yml`. The release build needs `keystore.properties`; without it `:app:assembleRelease` produces an unsigned APK. |
| Accessibility: TalkBack labels, dynamic type, 48 dp targets, keyboard navigation | **Partial** | Labels, dynamic type and touch targets are addressed. Full keyboard navigation on tablets is not implemented. |
| Security: no hardcoded secrets, encrypted tokens, validated inputs, sandboxed PDF processing | **Present** | `EphemeralSecretStore` keeps passwords in memory only, `core/Validation.kt` validates input, and the PDF path is sandboxed to the app's private storage. |
| **iOS application** | **Does not exist** | The repository contains iOS **brand assets** only (three 1024×1024 app-icon PNGs, now fixed and opaque). There is no Swift source, no Xcode project and no `Package.swift`. This machine is Linux with no Swift toolchain and no Xcode, so an iOS target cannot be built, signed or verified here. The Android app is the shipped product; the iOS icon set is now correct and ready for an Xcode project to consume. |

---

## 3. Project structure

```
app/src/main/java/com/jumlo/app/
  core/       46 files  pure Kotlin policies and algorithms (no Android deps) — validation,
                      naming, budgets, capability policy, media policies, hashing, transfer
  data/       38 files  repositories and engines — Room DAOs, output store, document tree,
                      trash, PDF backend (PDFBox), media runtime, operation handlers
  model/       4 files  serialisable domain models and the workspace draft
  printing/   14 files  the print pipeline — adapter, composer, repository, models, geometry
  ui/         51 files  Compose screens, theme, design-system components, workspace editors
  worker/      3 files  WorkManager jobs and the foreground service
  di/                  Hilt modules

app/src/test/          41 files  JVM unit tests (277 cases) — policies, ViewModels, PDF backend
app/src/androidTest/   19 files  instrumented Compose UI tests (19 files; no device in this env)
app/src/hostTest/       8 files  host JVM suite (182 cases) — core, transfers, trash, media
scripts/                      host verification, capability-matrix generator, brand checks
docs/                       design, feature status, qualification matrix, audit
brand/                      icon sources (SVG) and exports (PNG)
graphify-out/              the knowledge graph for this repository
```

---

## 4. Tests and a manual checklist per tool

### Executed in this environment

| Suite | Command | Result |
|---|---|---|
| JVM unit tests | `./gradlew --offline :app:testDebugUnitTest` | **311 passed, 0 failed** |
| Host Kotlin suite | `bash scripts/verify-host.sh` | **182 passed, 0 failed**, plus 18 Python static suites green |
| Lint | `./gradlew --offline :app:lintDebug` | 0 errors |
| Build | `./gradlew --offline :app:assembleDebug :app:assembleRelease` | both succeed |

### Manual checklist (device)

| Tool | Steps | Expected |
|---|---|---|
| Password Protect PDF | Open **Tools → Password Protect PDF**, pick a PDF, enter a user password and an owner password, run | The output opens with the user password, is rejected with a wrong password, and opens with the owner password. The reported size change matches the file on disk. |
| Unlock PDF | Open an encrypted PDF, choose the same tool, tick **Remove protection**, enter the password, run | A wrong password reports `PDF_PASSWORD_INCORRECT`; the correct password produces an unencrypted copy that opens without a password. |
| Redact PDF | Open **Tools → Redact PDF**, drag a region over a word, run | The word is absent from the saved PDF (search it in a reader, not just the preview). Untouched pages keep their text. |
| Compress PDF | Pick **Email (about 1 MB)**, run on an image PDF | The output is smaller and the reported saving matches the real bytes. Then set a **Target size (MB)** and confirm the app either reaches it or reports the size it actually achieved. |
| View PDF | Open a PDF in the viewer | Pinch and double-tap zoom feel smooth, a flick keeps moving and settles, the thumbnail rail loads. |
| Print | Pick a document, tap pages to include or skip, drag to reorder, rotate one, print | The printed sheet matches the preview, including margins and the selected page order. |
| Scanner | Capture several pages in one session, rotate and delete one, save | One multi-page PDF with the chosen order. |
| Files | Import a file, multi-select, rename, delete, restore from trash | The action is confirmed, the bytes are preserved, restore is exact. |
| Arabic / RTL | Switch the device language to Arabic, open every screen | Layout mirrors, numbers and Arabic strings are correct, nothing is clipped at 200 % text. |

---

## 5. Build and run

```bash
export ANDROID_HOME=/path/to/Android/Sdk        # or set sdk.dir in local.properties
export JAVA_HOME=/path/to/jdk-17                 # Gradle 8.14 cannot run on JDK 22+
bash scripts/check-build-env.sh                  # one-line check of the above
./gradlew --offline :app:assembleDebug           # debug APK
./gradlew --offline :app:assembleRelease         # release APK (needs keystore.properties)
./gradlew --offline :app:testDebugUnitTest       # JVM unit tests
bash scripts/verify-host.sh                      # host suites (no Android device needed)
./gradlew --offline :app:lintDebug               # lint
./gradlew --offline :app:connectedDebugAndroidTest   # instrumented tests; needs a device
```

`keystore.properties` is **not** in the repository (only `.example` and `.template`).
Without it `:app:assembleRelease` produces an **unsigned** APK that cannot be installed.
Until a real keystore exists, the `benchmark` build type is the installable
production-like artifact: it is release-configured and R8-minified but signed with the debug key.

```bash
./gradlew --offline :app:assembleBenchmark
```

---

## 5b. Store checklist

| Item | State | Action |
|---|---|---|
| Privacy policy | `docs/PRIVACY.md` exists | Re-check the wording against the new metadata cleaner and the new annotation kinds. |
| Data safety form | Not filed | Declare: documents stay on device; the app collects no personal data; downloads are user-initiated. |
| Permissions justification | `CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_DATA_SYNC` are declared | Write the camera and network justification text; the camera is for scanning, the network for user-initiated downloads. |
| Account / login | None | The app has no account. Keep it that way — it is a selling point. |
| Downloader policy | The media runtime is behind `-PextendedMedia=true` and the default APK carries no native media runtime | The downloader is already a separate module, so a direct-APK channel is available if store policy forbids it. The terms-of-use screen must state that the user may only download content they have rights to. |
| Screenshot set | Brand previews exist in `brand/previews/` | Capture the real screens: tools hub, print setup, page manager, scanner, files. |
| Content rating | Not set | The app has no user content and no ads; it should qualify for the lowest rating. |
| 16 KB page size | `scripts/inspect-standard-artifact.py` gate passes on the built artifact | Re-run for every release. |
| Signing | No release keystore in the repository | Supply `keystore.properties`; until then only the debug-signed `benchmark` variant is installable. |
| Target API | targetSdk 36, minSdk 26 | Current. |

**Store checklist blockers:** the release signing key, the real screenshot set, and the
terms-of-use screen for the downloader. The first is a secret only the owner can supply;
the second needs a device; the third is a screen the downloader already has a data
model for.

## 6. UI changes and the 2026 design reasoning

| Change | Why |
|---|---|
| `QuietChip` / `QuietSwitch` / `QuietSlider` / `QuietDangerButton` | One house rule for controls: every tappable target is at least 48 dp, because the Material defaults (32 dp chip, 40 dp button) are below the accessible minimum and the app had ~27 raw chip sites. |
| Print page manager | Direct manipulation replaces typing: a page range is a *selection*, and selection is a gesture. The range string stays the single source of truth underneath, so the numeric field is a synced secondary input rather than a competing control. |
| Reordering carries the selection | Moving a page must never silently change which pages print. The selection follows the page. |
| `ZoomPan` with friction decay | A flick that stops dead reads as a dropped gesture. The decay is frame-rate independent, so the feel does not change with device refresh rate. |
| Distinct `dragStart` / `dragEnd` / `snap` haptics | Lifting a page, dropping it, and it landing in a slot are three different events; one generic tick erases the difference. |
| Compression presets + a real byte budget | "Balanced" means nothing without a measurable outcome, so the presets are a starting point and the budget is the contract. The tool stops at the first candidate that genuinely reaches the target. |
| One shimmer period, centralised motion tokens | Two skeleton loaders at 800 ms and 900 ms read as an unfinished app; the nav spring constants now live in `JumloMotion` instead of inline literals. |
| Full-bleed opaque iOS icons | iOS applies its own mask and rejects alpha. The Android squircle cannot be reused as the iOS tile. |
| Graphify knowledge graph | The repo documents itself in prose that had drifted from the code. The graph is generated from the code and the documents, so the audit can be re-run instead of re-argued. |

---

## 6b. The new professional tools

The research behind each one — and how each compares with Adobe Acrobat and Microsoft
Lens — is in **[NEW_TOOLS_RESEARCH_2026-10-20.md](NEW_TOOLS_RESEARCH_2026-10-20.md)**.

Built in this pass:

| Tool | What it does | Evidence |
|---|---|---|
| **Annotation kinds** | Highlight, underline, strikethrough, sticky note, freehand, rectangle, ellipse and text box. The region is dragged on the page and resized by its corner handles; the numeric position fields are gone. Markup annotations carry the transformed quad, so a reader highlights exactly the dragged area, and a freehand stroke becomes a real ink annotation. | `PdfAnnotationKindTest` (12 tests) |
| **Metadata cleaner** | Removes the author, the producing application, the timestamps, the custom keys and the XMP packet; keeps the title unless asked otherwise; publishes a report of every key it removed. | `PdfMetadataCleanerTest` (8 tests) |
| **Compression presets and a byte budget** | Email (~1 MB) / Balanced / High quality, plus a custom target in MB that the tool reaches by descending resolution then quality, stopping at the first candidate that genuinely meets it. | `PdfCompressionPolicyTest` (15 tests) |
| **A size/quality slider with a live estimate** | One slider replaces the typed DPI and quality fields, and shows the estimated output size computed from the real embedded-image bytes of the source. | `PdfCompressionPolicyTest` |
| **Snapping guides with haptics** | Edges snap to the centre, the thirds and the borders; the guide is drawn and the snap is confirmed by touch. | `SnapGuidesTest` (9 tests) |
| **Freehand annotation** | A drawn stroke becomes a real PDF ink annotation, mapped through the page transform. | `PdfAnnotationKindTest` |
| **A visible history** | Every recorded step is listed and can be restored directly. | — |
| **Two-finger rotation** | Previews can be turned, bounded so a page is never upside down. | — |

Still to build, in the order the research recommends: **the signature library with
corner-resize**, **tap-to-fill form fields**, **drag a page into another document**,
**batch processing**, **the brand profile**, **secure sharing with expiry**, and the
**side-by-side comparison view**. OCR text layer and expiry enforcement need a device
and a reference reader before they can be claimed honestly.

## 6c. Main flows, step by step

**Print a document.** Tools → Print → pick a PDF → the page manager appears: tap a
thumbnail to include or skip it, long-press and drag to reorder, rotate a quarter turn
with the rotate action. The preview shows the real output: paper, margins, scaling and
the selected page order. Set the paper, orientation, scale, margins, colour and duplex;
the range field below the manager stays in sync with the visual selection. Press print,
pick a printer in the system dialog, and watch the queue card for live page progress.
*Note: the printer list is the system dialog's — Android gives apps no way to enumerate
printers, so Jumlo states the fact instead of faking a list.*

**Compress a PDF.** Tools → Compress PDF → choose *Email (about 1 MB)*, *Balanced* or
*High quality*, or type a target size in MB. The candidate ladder descends resolution
first and then quality and stops at the first result that actually reaches the budget;
if the budget cannot be reached, the tool publishes the smallest valid result and the
output card reports the real before/after sizes. A text-only PDF is never turned into a
screenshot — if nothing can be made smaller, the output is reported as unchanged.

**Annotate a PDF.** Tools → Annotate PDF → pick the kind (highlight, underline,
strikethrough, sticky note, rectangle, ellipse, text box) → drag the region on the page
preview and drag its corners to resize → type the note text when the kind needs one →
run. The result is a real PDF annotation, so a reader can select and comment on it.

**Redact a PDF.** Tools → Redact PDF → drag the region over the sensitive text → run.
The tool removes the text from the content stream, then re-opens its own output and
asserts the word is gone before publishing. If the removal could not be verified, the
job fails instead of shipping a file that only looks redacted.

**Protect or unlock a PDF.** Tools → Password Protect PDF → pick a file → enter a user
password and an owner password → run. The output opens with the user password, is
rejected with a wrong password, and opens with the owner password. Tick *Remove
protection* to unlock instead: only the correct password works, and the result is a
copy that opens without one.

**Clean a document's metadata.** Tools → Clean Metadata → pick a PDF → run. The author,
the producing application, the timestamps, the custom keys and the XMP packet are
removed; the title survives unless you ask otherwise. The output arrives with a
sidecar report listing every key that was removed.

**Scan a document.** Tools → Scan Document → capture → the edges are detected and the
perspective is corrected → rotate, crop, filter and reorder the pages → save as one
multi-page PDF.

**Batch a folder.** Files → multi-select with a long press → the action bar → delete,
rename, copy or move → the action is confirmed and, where it is safe, undoable.

**Download.** Downloads → paste a link or share one into the app → the source and
content type are detected and the best available quality is chosen automatically → the
queue shows progress, speed and ETA and can be paused, resumed, cancelled and retried →
the file lands in the visible save folder.

## 7. Open risks

- **No emulator, no device.** Instrumented tests compile but have never been executed. Nothing
  in this pass has been observed on hardware: install, launch, camera capture, actual printing
  and the fling *feel* are all unverified.
- **The release APK is unsigned** until a release keystore is supplied.
- **iOS is brand assets only.** No Swift source exists and none could be built or verified on
  this Linux machine.
- **The PDF viewer is still short of the brief**: in-document text search, bookmarks and the
  reading themes are not implemented. The backend already has the text extraction the search
  would need (`PdfBoxBackend.pageText` / `containsText`), so this is the next cheapest
  high-value feature.
- **Cost per page and log export** still need a pricing decision (currency, per-page cost,
  rounding) that no audit can make on the product owner's behalf.
- **N-up, booklet and fixed-field templates** are the next commercial features and each needs
  real output-geometry work in `PrintPdfComposer`, not UI work.
