# PDF Structural Processing and Viewing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans after the plan set is reviewed. Steps use checkbox (`- [ ]`) syntax. Do not start a separate application.

**Goal:** Complete all seven PDF tool entries without substituting rasterized/demo documents.

**Architecture:** One qualified PDF adapter owns structural edits and digital text; platform rendering owns previews and page-image export.

**Tech Stack:** The pinned native Android stack and qualified adapters in the root plan.

**Spec:** `../../specs/2026-10-16-completion-design.md`

## Global Constraints

Inherit every constraint, fixed build version, shared interface and verification rule in `../2026-10-16-completion-plan.md`; it travels with this plan. In particular: preserve originals; no fabricated success; no unapproved local downloads; no font binaries, credentials or caches in deliverables. The exact approved spec is linked above.

## Review Focus

The five cross-cutting failure classes are recorded once in the root plan. Their owning regression tests are assigned to this plan's tasks below; do not substitute generic happy-path tests.

---

### Task P01: Qualify the structural PDF adapter and preservation policy

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/PdfEngine.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/pdf/PdfBackend.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/pdf/PdfBoxBackend.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/pdf/PdfCapabilityPolicy.kt`
- Create: `docs/PDF_SUPPORT_MATRIX.md`
- Create: `docs/DEPENDENCY_NOTICES.md`
- Test: `app/src/androidTest/java/com/universalutilities/app/pdf/PdfBackendQualificationTest.kt`

**Interfaces:**
- Consumes: F03 staged seekable inputs, short-lived password access, budgets and errors.
- Produces: `PdfBackend.inspect(file: File, password: CharArray?): PdfInfo`; `copyPages(sources: List<PdfInput>, pages: List<PageCopy>, destination: File): Unit`. PdfInput is (file,password); PageCopy is (sourceIndex,physicalPageIndex,rotationDegrees); all processing methods suspend.

- [ ] **Step 1: Write failing regressions.** `copyRetainsTextAndGeometry` copies page SECOND before FIRST; `unsupportedFiltersFailBeforeMutation`, `wrongPasswordHasNoOutput`, `annotationFormSignaturePolicyIsEnforced` and `malformedPdfDoesNotExhaustBudget` qualify the actual Android artifact.

```kotlin
assertEquals(listOf("SECOND", "FIRST"), textTokens(result))
assertEquals(expectedPageBoxes, pageBoxes(result))
assertFalse(resultExistsAfterRejectedInput)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.pdf.PdfBackendQualificationTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Evaluate `com.tom-roush:pdfbox-android:2.0.27.0` as the explicitly proposed candidate, not a safety guarantee [R3/R4]. Inspect transitive licenses/advisory applicability and record artifact size/checksums. Reject unsupported JPX/complex forms rather than accepting the library's possible render omissions. Use bounded temp storage and parser work off the main thread. Qualification failure blocks this backend; retain the tested interface and report the exact failing corpus instead of shipping silent rasterization. No optional JCenter/NDK codecs.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: qualify offline structural pdf backend`.

### Task P02: Real viewer, merge, organize, split and extract

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/PdfEngine.kt`
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/PureDomainAlgorithms.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/pdf/PdfWorkspaceScreens.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/navigation/UniversalUtilitiesNavHost.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/PdfOperationSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/pdf/PdfWorkspaceViewModel.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/pdf/PdfViewerViewModel.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/pdf/PdfStructuralRoundTripTest.kt`
- Test: `app/src/test/java/com/universalutilities/app/pdf/PdfPageSelectionTest.kt`

**Interfaces:**
- Consumes: Qualified P01 backend plus SourceStore/OutputStore; physical page identity is not a printed page label.
- Produces: `PdfEngine.transform(request: JobRequest, context: ExecutionContext): List<ItemOutcome>` for pdf_03 through pdf_06; `PdfViewerViewModel.open(sourceId: String)` and `search(query: String)` with real page rendering/text results.

- [ ] **Step 1: Write failing regressions.** `splitTwentyFourByFive` generates 24 token pages; `extractArabicNumerals` parses Arabic and Latin equivalent ranges; `organizeCannotDeleteEveryPage` and `mergeSourcesRemainUnchanged` verify mutations, geometry and source hashes.

```kotlin
assertEquals(listOf(5,5,5,5,4), outputPageCounts)
assertEquals(listOf(1,2,3,7,10,11,12), parsedPhysicalPages)
assertEquals(27, merge24And3.pageCount)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.pdf.PdfStructuralRoundTripTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Preserve stable page IDs, range normalization, source order and 90-degree rotations; reject reversed/out-of-range/overlapping split ranges and visibly report omissions. Use supported object copying, never drawVectorSamplePage or PDF-to-bitmap round trips. Viewer owns a bounded thumbnail cache and closes page descriptors on route changes; no text layer yields an explanation, not OCR. Draft undo stores edit commands, not image copies. Run pure range tests too.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement text preserving pdf operations`.

### Task P03: Actual PDF page-image exports and resource controls

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/PdfEngine.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/pdf/PdfWorkspaceScreens.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/PdfRasterSettings.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/pdf/PdfRasterExportTest.kt`

**Interfaces:**
- Consumes: F03/F04 output lifecycle and P01 document capability inspection.
- Produces: `PdfEngine.exportPages(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; settings are selected physical pages, JPG/PNG, DPI, conditional quality, grayscale, filename prefix and grouping.

- [ ] **Step 1: Write failing regressions.** `exportsSelectedPageContent` exports physical page 20 from a 24-page marked fixture; `highDpiBudgetRejectsWithoutSilentReduction` injects a small budget; `pngDoesNotUseJpegQuality` checks codec state and alpha policy.

```kotlin
assertTrue(output.displayName.endsWith("p020.png"))
assertEquals(expectedPixelsAt150Dpi, output.metadata.width to output.metadata.height)
assertEquals("WORKLOAD_TOO_LARGE", rejected.errorCode)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.pdf.PdfRasterExportTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Render actual pages at point-to-pixel scaling (DPI/72), use white backing, close/recycle sequentially, support 72/150/300/custom DPI subject to checked allocations. Encode grayscale or original colors; validate output decodability/dimensions. ZIP packaging uses A02 when ready, not a temporary duplicate archive implementation. Declare lost PDF interactivity for image export.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: add validated pdf page image export`.
