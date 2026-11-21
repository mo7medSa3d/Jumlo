# Jumlo Pro iOS 27 Zero-Server Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform Jumlo into a complete, 100% offline, zero-server-cost Pro document studio on Android with iOS 27 Liquid Glass UX that surpasses PDF Expert, Genius Scan, and Adobe Fill & Sign in speed, privacy, and Arabic/English fidelity.

**Architecture:** All new capabilities run strictly on-device using existing modules (`document-core` for pure JVM geometry/layout policies, `app/data/pdf` & `app/data/scan` for PDFBox/Android Canvas/Biometric engines, and `app/ui` for iOS 27 floating Liquid Glass Compose surfaces). Every document mutation produces a verified immutable output via `OutputStore.reserve`/`publish` without altering originals. Monetization and anti-piracy use `Google Play Billing` (Lifetime One-Time + Annual) and `Play Integrity API` behind a clean `playRelease` build flavor.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3 + iOS 27 Liquid Glass tokens in `QuietTheme`), `pdfbox-android`, CameraX + ML Kit Document Scanner, `androidx.biometric:biometric:1.1.0`, `com.android.billingclient:billing-ktx:7.1.1`, `com.google.android.play:integrity:1.4.0`, Python 3.11 static/contract verification suite (`scripts/test-*.py`).

**Spec:** Benchmarked against [PDF Expert iOS 27 Liquid Glass Markup](https://pdfexpert.com/blog/pdfexpert-ios27-ipados27-update), [Genius Scan ISO A4 & Document Filters](https://apps.apple.com/pl/app/scanner-app-genius-scan/id377672876), [Fill & Sign Offline UX](https://www.reddit.com/r/androidapps/comments/1ptjonv/offline_pdf_editor_free_or_onetime_purchase/), and [Google Play Billing](https://developer.android.com/google/play/billing) / [Play Integrity API](https://developer.android.com/google/play/integrity/overview).

## Global Constraints

- **100% On-Device / Zero Server Cost:** Every Pro feature (Fill & Sign, Markup, ID Card 2-in-1, Document Filters, N-Up Print, Permanent Redact, Biometric Vault) must work completely offline with zero external API calls or user accounts.
- **iOS 27 Liquid Glass UI Discipline:** Floating translucent pill toolbars, tactile haptics (`rememberAppHaptics()`), instant visual feedback, and zero multi-sentence instructional paragraphs or clutter.
- **Full Bilingual & BiDi Parity:** Every new string must exist in both `app/src/main/res/values/strings.xml` and `app/src/main/res/values-ar/strings.xml`; mixed Arabic/Latin tool names must use `\u200F` + `\u2066...\u2069` isolation.
- **Non-Destructive Publishing:** Originals are never overwritten in place; all outputs publish atomically via `OutputStore` and pass `PdfBoxBackend.open` / `OutputStore.inspect` verification.
- **Verification Gate:** Every task must add or update deterministic unit/contract tests and keep 100% of `scripts/test-*.py`, XML validation, and EN/AR parity green.

## Review Focus

1. **Rotated & Landscape PDF Pages (`rotation` = 90/180/270 or non-A4 mediaBox):** Annotations, signatures, text stamps, and permanent redactions must map normalized `(0..1, 0..1)` screen coordinates accurately into PDF user space regardless of page rotation or cropBox offset (pinned in Task 1).
2. **Arabic RTL Text in Fill & Sign:** PDFBox Standard-14 Helvetica cannot shape Arabic glyphs; any non-Latin text annotation must render through Android `StaticLayout` into a high-DPI transparent bitmap embedded via `LosslessFactory` so Arabic text never turns into `????` or disconnected letters (pinned in Task 1).
3. **Permanent Redaction Security:** Redacting a region must NOT merely draw a black rectangle over selectable PDF text (where copy-paste would still expose the secret); it must rasterize/burn the redacted page at high DPI with opaque fill before saving so underlying text objects are permanently destroyed (pinned in Task 4).
4. **ID Card Aspect Ratio & Orientation on ISO A4 (`595 × 842 pt`):** Portrait or skewed front/back ID photos must auto-rotate to landscape, normalize to ISO/IEC 7810 ID-1 ratio (`85.6 × 53.98 mm` scaled cleanly with centered margins), and fit both faces onto one A4 page without clipping (pinned in Task 3).
5. **Biometric Vault Hardware / Enrollment Fallback:** On devices without biometric hardware or without enrolled fingerprints/face, the Vault must fall back cleanly to device PIN/pattern/password (`DEVICE_CREDENTIAL`) or display a clear non-crashing setup prompt without locking the user out of their files (pinned in Task 5).

---

### Task 1: PDF Markup, Fill & Sign Domain Model, Saved Signatures Store, and Flatten Engine

**Files:**
- Create: `document-core/src/main/kotlin/com/jumlo/app/core/PdfMarkupModel.kt`
- Create: `app/src/main/java/com/jumlo/app/data/pdf/SavedSignatureStore.kt`
- Create: `app/src/main/java/com/jumlo/app/data/pdf/PdfMarkupFlattener.kt`
- Create: `scripts/test-pro-markup.py`

**Interfaces:**
- Consumes: `PdfBoxBackend.open(file, password)` in `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt`, `OutputStore.reserve`/`publish`.
- Produces:
  - `PdfMarkupItem` sealed interface (`InkStroke`, `HighlightStroke`, `TextBox`, `CheckMark`, `SavedSignatureStamp`) with normalized `[0f..1f]` coordinates and page index.
  - `PdfMarkupPolicy.toPdfRect(pageWidthPt: Float, pageHeightPt: Float, rotationDeg: Int, normX: Float, normY: Float, normW: Float, normH: Float): PdfRect`
  - `SavedSignatureStore.list(): List<SavedSignature>`, `save(pngBytes: ByteArray, label: String): SavedSignature`, `delete(id: String)`
  - `PdfMarkupFlattener.flatten(inputPdf: File, outputPdf: File, items: List<PdfMarkupItem>, checkCancel: () -> Unit): Int` (returns page count).

- [ ] **Step 1: Write the failing contract & geometry test**

Create `scripts/test-pro-markup.py` asserting:
- `PdfMarkupModel.kt` defines `InkStroke`, `HighlightStroke`, `TextBox`, `CheckMark`, `SavedSignatureStamp`, and `PdfMarkupPolicy.toPdfRect` handling `0`, `90`, `180`, `270` rotations.
- `PdfMarkupFlattener.kt` uses `StaticLayout` + `LosslessFactory` for non-Latin/Arabic text boxes, applies multiply/translucent alpha for `HighlightStroke`, and verifies `outputPdf` with `PdfBoxBackend.open`.
- `SavedSignatureStore.kt` persists PNG signatures in app-private storage with bounded count (`MAX_SIGNATURES = 12`).

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 scripts/test-pro-markup.py`
Expected: FAIL (`FileNotFoundError` for `PdfMarkupModel.kt`).

- [ ] **Step 3: Implement `PdfMarkupModel.kt`, `SavedSignatureStore.kt`, and `PdfMarkupFlattener.kt`**

- `PdfMarkupModel.kt`: `@Serializable` immutable data classes for all 5 markup types, `UndoRedoStack<T>` (max depth 50), and `PdfMarkupPolicy.toPdfRect(...)` that handles page rotation (`0/90/180/270`) and Y-axis inversion cleanly.
- `SavedSignatureStore.kt`: Stores transparent PNG signatures in `File(context.filesDir, "saved_signatures")`, capped at 12 signatures / 512 KB each, sorted newest-first.
- `PdfMarkupFlattener.kt`: Renders vector strokes (`PDPageContentStream` with `PDExtendedGraphicsState` alpha `0.35f` for highlighter, `1.0f` for pen), embeds PNG signatures, draws crisp `✓`/`✗` vector marks, and rasterizes Arabic/non-Latin `TextBox` items via Android `StaticLayout` at 300 DPI (`LosslessFactory.createFromImage`) so Arabic shaping is 100% accurate.

- [ ] **Step 4: Run test to verify it passes**

Run: `python3 scripts/test-pro-markup.py`
Expected: PASS (all assertions green).

- [ ] **Step 5: Commit**

```bash
git add document-core/src/main/kotlin/com/jumlo/app/core/PdfMarkupModel.kt \
  app/src/main/java/com/jumlo/app/data/pdf/SavedSignatureStore.kt \
  app/src/main/java/com/jumlo/app/data/pdf/PdfMarkupFlattener.kt \
  scripts/test-pro-markup.py
git commit -m "feat(pdf): add markup model, saved signature store, and Arabic-safe flattener"
```

---

### Task 2: iOS 27 Liquid Glass Markup, Fill & Sign Studio inside `PdfViewerScreen`

**Files:**
- Create: `app/src/main/java/com/jumlo/app/ui/viewer/PdfMarkupOverlay.kt`
- Create: `app/src/main/java/com/jumlo/app/ui/viewer/SignaturePadSheet.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml`

**Interfaces:**
- Consumes: `PdfMarkupItem`, `UndoRedoStack`, `SavedSignatureStore`, `PdfMarkupFlattener.flatten` from Task 1.
- Produces:
  - `AppViewModel.flattenPdfMarkup(sourceUri: String, displayName: String, items: List<PdfMarkupItem>, onSaved: (OutputRecord) -> Unit)`
  - `PdfMarkupOverlay(pageIndex, items, activeTool, activeColor, strokeWidth, onAddItem, onMoveItem, onDeleteItem)`
  - `SignaturePadSheet(savedSignatures, onPickSaved, onSaveNew, onDeleteSaved, onDismiss)`

- [ ] **Step 1: Extend `scripts/test-pro-markup.py` with UI & ViewModel contracts**

Add assertions verifying:
- `PdfViewerScreen.kt` exposes an Edit/Markup toggle button in the top bar that reveals the floating iOS 27 translucent pill toolbar (`Highlighter`, `Pen`, `Text`, `Date`, `Check`, `Signature`, `Undo`, `Redo`, `Save`).
- `SignaturePadSheet.kt` supports smooth Bezier finger-drawing with ink color choices (Black, Blue, Red), clear button, and saved signature gallery.
- `AppViewModel.kt` wires `flattenPdfMarkup(...)` through `outputStore.reserve` + `outputStore.publish`.
- All new EN/AR strings exist with 100% parity.

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 scripts/test-pro-markup.py`
Expected: FAIL on missing `PdfMarkupOverlay.kt` and `SignaturePadSheet.kt`.

- [ ] **Step 3: Implement `PdfMarkupOverlay.kt`, `SignaturePadSheet.kt`, and wire into `PdfViewerScreen.kt` & `AppViewModel.kt`**

- Floating iOS 27 Liquid Glass pill bar (`QuietTheme.shapes.pill`, `surfaceContainerHigh.copy(alpha = 0.90f)`, 1dp border, soft shadow) docked cleanly above system nav bar with 48dp touch targets and zero text paragraphs.
- Interactive Canvas layer over the active PDF page: finger draws smooth quadratic Bezier curves when Pen/Highlighter is active; tapping places Text/Date/Checkmark/Signature with drag-to-move and corner resize handle + quick delete chip.
- Instant `Undo` / `Redo` buttons right in the toolbar (matching PDF Expert iOS 27).
- Tapping **Save** flattens edits into a new verified PDF (`<name> - Annotated.pdf`) in `OutputStore` and shows a subtle confirmation toast.

- [ ] **Step 4: Run test suite to verify pass**

Run: `python3 scripts/test-pro-markup.py && python3 scripts/test-premium-ux.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jumlo/app/ui/viewer/PdfMarkupOverlay.kt \
  app/src/main/java/com/jumlo/app/ui/viewer/SignaturePadSheet.kt \
  app/src/main/java/com/jumlo/app/ui/viewer/PdfViewerScreen.kt \
  app/src/main/java/com/jumlo/app/ui/AppViewModel.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml \
  scripts/test-pro-markup.py
git commit -m "feat(viewer): add iOS 27 Liquid Glass Fill, Sign & Annotate studio"
```

---

### Task 3: Smart Scan Pro — ID Card / Passport 2-in-1 A4 Composer & Pro Document Filters

**Files:**
- Create: `app/src/main/java/com/jumlo/app/data/scan/DocumentFilterEngine.kt`
- Create: `app/src/main/java/com/jumlo/app/data/scan/IdCardA4Composer.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/scan/CameraXScanFallback.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml`
- Create: `scripts/test-pro-scan.py`

**Interfaces:**
- Consumes: `OutputStore`, `PdfBoxBackend`, `SmartDocumentScanLauncher`.
- Produces:
  - `enum class ScanFilter { ORIGINAL, CLEAN_DOCUMENT, CRISP_BW, MAGIC_COLOR }`
  - `DocumentFilterEngine.apply(source: Bitmap, filter: ScanFilter): Bitmap`
  - `IdCardA4Composer.compose(front: Bitmap, back: Bitmap, filter: ScanFilter, outputPdf: File, checkCancel: () -> Unit)` (exact ISO A4 `595 × 842 pt`, auto-landscape normalization, centered ID-1 proportion, zero watermark).

- [ ] **Step 1: Write the failing test**

Create `scripts/test-pro-scan.py` verifying:
- `DocumentFilterEngine.kt` implements all 4 `ScanFilter` modes (`ORIGINAL`, `CLEAN_DOCUMENT` with local background illumination normalization / shadow removal, `CRISP_BW` adaptive high-contrast threshold, `MAGIC_COLOR` vibrance + white-point boost) and recycles intermediate bitmaps.
- `IdCardA4Composer.kt` outputs exact ISO A4 (`595f × 842f`), auto-rotates portrait ID captures to landscape, centers front (top half) and back (bottom half) with balanced margins, and never adds any watermark.
- `ScanScreen.kt` offers a clean iOS 27 segmented pill switcher: **Document** (`مستند`) vs **ID Card 2-in-1** (`بطاقة وجه وظهر`) + filter thumbnail strip before saving.

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 scripts/test-pro-scan.py`
Expected: FAIL (`FileNotFoundError` for `DocumentFilterEngine.kt`).

- [ ] **Step 3: Implement `DocumentFilterEngine.kt`, `IdCardA4Composer.kt`, and wire into `ScanScreen.kt`, `CameraXScanFallback.kt`, `AppViewModel.kt`**

- `DocumentFilterEngine`: pure Android `Bitmap` + `ColorMatrix` + fast downsampled box-blur division for shadow removal (`CLEAN_DOCUMENT`) so faded receipts and uneven desk lighting become crisp white paper.
- `IdCardA4Composer`: guides the user through Step 1 (Front face) → Step 2 (Back face) → live A4 preview with filter strip → 1-tap Save to Files library as `ID-Card-<timestamp>.pdf`.

- [ ] **Step 4: Run test to verify it passes**

Run: `python3 scripts/test-pro-scan.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jumlo/app/data/scan/DocumentFilterEngine.kt \
  app/src/main/java/com/jumlo/app/data/scan/IdCardA4Composer.kt \
  app/src/main/java/com/jumlo/app/ui/scan/ScanScreen.kt \
  app/src/main/java/com/jumlo/app/ui/scan/CameraXScanFallback.kt \
  app/src/main/java/com/jumlo/app/ui/AppViewModel.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml \
  scripts/test-pro-scan.py
git commit -m "feat(scan): add ID Card 2-in-1 A4 composer and pro document filters"
```

---

### Task 4: Pro Print (`N-Up` 2-in-1 / 4-in-1 PDF) & Permanent Privacy (`PDF Redact` + EXIF Strip)

**Files:**
- Create: `app/src/main/java/com/jumlo/app/data/pdf/PdfNupComposer.kt`
- Create: `app/src/main/java/com/jumlo/app/data/pdf/PdfRedactionEngine.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/processing/Settings.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt`
- Modify: `app/src/main/java/com/jumlo/app/model/WorkspaceDraft.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml`
- Create: `scripts/test-pro-print-privacy.py`

**Interfaces:**
- Consumes: `PdfBoxBackend`, `PdfPageRasterizer`, `PdfMarkupPolicy.toPdfRect`.
- Produces:
  - Tool ID `pdf_nup` (`2-in-1` / `4-in-1` pages per ISO A4 sheet, optional grayscale ink-saver, optional subtle page border).
  - Tool ID `pdf_redact` (user draws opaque boxes on target pages → `PdfRedactionEngine.burnRedactions(...)` rasterizes each redacted page at 200 DPI with burned-in `#000000` / `#FFFFFF` boxes so underlying text/vectors are permanently destroyed, while leaving untouched pages lossless!).

- [ ] **Step 1: Write the failing test**

Create `scripts/test-pro-print-privacy.py` asserting:
- `pdf_nup` and `pdf_redact` are registered in `CapabilityRegistry.kt` (with BiDi-safe Arabic titles `\u2066PDF\u2069`), routed in `OperationDispatcher.kt`, serialized in `WorkspaceDraft.kt`, and handled in `PdfOperationHandler.kt`.
- `PdfRedactionEngine.kt` rasterizes/burns redacted pages (never leaves selectable text beneath a black box) and strips PDF document info metadata (`author`, `creator`, `producer`, `keywords`).
- `PdfNupComposer.kt` supports `pagesPerSheet in setOf(2, 4)` and `grayscale: Boolean`.

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 scripts/test-pro-print-privacy.py`
Expected: FAIL.

- [ ] **Step 3: Implement `PdfNupComposer.kt`, `PdfRedactionEngine.kt`, and wire both tools end-to-end**

- `PdfNupComposer`: uses `LayerUtility.importPageAsForm` (vector-preserving!) for color mode and clean grayscale pass for ink-saver mode, placing 2 landscape or 4 portrait pages cleanly on each ISO A4 sheet.
- `PdfRedactionEngine`: reuses visual box selection on the PDF page, burns redacted pages permanently via `PdfPageRasterizer` + `Canvas.drawRect` + `JPEGFactory`, strips PDF metadata, and keeps unredacted pages untouched.

- [ ] **Step 4: Run test suite to verify pass**

Run: `python3 scripts/test-pro-print-privacy.py && python3 scripts/test-new-tools.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jumlo/app/data/pdf/PdfNupComposer.kt \
  app/src/main/java/com/jumlo/app/data/pdf/PdfRedactionEngine.kt \
  app/src/main/java/com/jumlo/app/data/processing/Settings.kt \
  app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt \
  app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt \
  app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt \
  app/src/main/java/com/jumlo/app/model/WorkspaceDraft.kt \
  app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml \
  scripts/test-pro-print-privacy.py
git commit -m "feat(pdf): add N-Up print layout and permanent PDF redaction tools"
```

---

### Task 5: Biometric Secure Vault (`BiometricPrompt`) inside `FilesScreen`

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (add `androidx.biometric:biometric:1.1.0`)
- Create: `app/src/main/java/com/jumlo/app/data/files/BiometricVaultGuard.kt`
- Modify: `app/src/main/java/com/jumlo/app/model/UserPreferences.kt` (add `lockedFolderIds: Set<String> = emptySet()`)
- Modify: `app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt`, `app/src/main/java/com/jumlo/app/ui/files/FileFolderComponents.kt`, `app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml`
- Create: `scripts/test-pro-vault.py`

**Interfaces:**
- Consumes: `FileFolder`, `UserPreferences`, `FragmentActivity` (via `MainActivity`).
- Produces:
  - `BiometricVaultGuard.authenticate(activity: FragmentActivity, title: String, subtitle: String, onSuccess: () -> Unit, onUnavailable: () -> Unit, onError: (String) -> Unit)` supporting `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
  - Locked folders hide their files from root search, type filters, and `HomeScreen` recent files until unlocked for the active foreground session (auto-locks on app background via `Lifecycle.Event.ON_STOP`).

- [ ] **Step 1: Write the failing test**

Create `scripts/test-pro-vault.py` verifying:
- `androidx.biometric:biometric` is declared in `libs.versions.toml` and `app/build.gradle.kts`.
- `BiometricVaultGuard.kt` allows `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` and handles `BIOMETRIC_ERROR_NONE_ENROLLED` / `BIOMETRIC_ERROR_NO_HARDWARE` gracefully.
- `FilesScreen.kt` & `HomeScreen.kt` exclude files inside `lockedFolderIds` from unfiltered recents/search until unlocked, and re-lock on `Lifecycle.Event.ON_STOP`.

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 scripts/test-pro-vault.py`
Expected: FAIL.

- [ ] **Step 3: Implement `BiometricVaultGuard.kt` and wire Vault lock/unlock into `FilesScreen.kt` & `HomeScreen.kt`**

- Add "Lock with Biometrics" / "Unlock Vault" action in `FolderActionsSheet` with a clean iOS 27 lock badge on locked folder cards.
- Keep `unlockedFolderIds` strictly in transient memory (never persisted) and clear on `ON_STOP`.

- [ ] **Step 4: Run test to verify it passes**

Run: `python3 scripts/test-pro-vault.py`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/jumlo/app/data/files/BiometricVaultGuard.kt \
  app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt \
  app/src/main/java/com/jumlo/app/ui/files/FileFolderComponents.kt \
  app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml \
  scripts/test-pro-vault.py
git commit -m "feat(files): add Biometric Secure Vault with auto-lock and recents hiding"
```

---

### Task 6: Commercial Readiness — iOS 27 `Jumlo Pro` Paywall, Play Billing, Play Integrity & `playRelease` Flavor

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (add `billing-ktx:7.1.1`, `play-integrity:1.4.0`, and `productFlavors { create("play"); create("direct") }` with `BuildConfig.ENABLE_MEDIA_DOWNLOADER`)
- Create: `app/src/main/java/com/jumlo/app/data/billing/JumloBillingManager.kt`
- Create: `app/src/main/java/com/jumlo/app/data/security/PlayIntegrityVerifier.kt`
- Create: `app/src/main/java/com/jumlo/app/ui/pro/JumloProSheet.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt` (hide `dl_media`/`dl_02`/`dl_03` when `!BuildConfig.ENABLE_MEDIA_DOWNLOADER`)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml`
- Create: `scripts/test-pro-monetization.py`

**Interfaces:**
- Consumes: `Google Play Billing Library`, `Play Integrity Standard API`.
- Produces:
  - `JumloBillingManager.entitlement: StateFlow<ProEntitlement>` supporting `jumlo_pro_lifetime` (One-Time INAPP) and `jumlo_pro_annual` (SUBS), with purchase acknowledgement and restore purchases.
  - `PlayIntegrityVerifier.requestToken(requestHash: String): Result<String>`
  - `JumloProSheet(entitlement, products, onPurchase, onRestore, onDismiss)` — clean iOS 27 modal sheet highlighting Lifetime One-Time Purchase, 100% Offline Privacy, and Zero Watermarks.
  - `play` flavor (`ENABLE_MEDIA_DOWNLOADER = false`) for 100% Google Play policy compliance; `direct` flavor (`ENABLE_MEDIA_DOWNLOADER = true`) keeping full downloader.

- [ ] **Step 1: Write the failing test**

Create `scripts/test-pro-monetization.py` asserting:
- `app/build.gradle.kts` defines `play` (`ENABLE_MEDIA_DOWNLOADER = false`) and `direct` (`ENABLE_MEDIA_DOWNLOADER = true`) flavors plus `billing-ktx` and `play-integrity`.
- `JumloBillingManager.kt` handles both `ProductType.INAPP` (`jumlo_pro_lifetime`) and `ProductType.SUBS` (`jumlo_pro_annual`), calls `acknowledgePurchase`, and exposes `restorePurchases()`.
- `JumloProSheet.kt` displays localized pricing from Play Store `ProductDetails` (no hardcoded currency strings).

- [ ] **Step 2: Run test to verify it fails**

Run: `python3 scripts/test-pro-monetization.py`
Expected: FAIL.

- [ ] **Step 3: Implement `JumloBillingManager.kt`, `PlayIntegrityVerifier.kt`, `JumloProSheet.kt`, and `play`/`direct` build flavors**

- Wire Pro badge in Settings & gentle upgrade prompt only when invoking Pro-tier features (`pdf_redact`, `pdf_nup`, Biometric Vault, unlimited saved signatures), keeping all core tools free and watermark-free.

- [ ] **Step 4: Run full verification suite**

Run: `python3 -m unittest discover -s tools/media-service -p 'test_*.py' && for s in scripts/test-*.py; do python3 "$s" || exit 1; done`
Expected: 100% of contract scripts, unit tests, XML validation, and EN/AR string parity PASS.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/jumlo/app/data/billing/JumloBillingManager.kt \
  app/src/main/java/com/jumlo/app/data/security/PlayIntegrityVerifier.kt \
  app/src/main/java/com/jumlo/app/ui/pro/JumloProSheet.kt \
  app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml \
  scripts/test-pro-monetization.py
git commit -m "feat(pro): add Play Billing lifetime/annual paywall, Play Integrity, and play flavor"
```
