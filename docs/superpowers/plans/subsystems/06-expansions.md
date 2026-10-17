# Full Expansion Feature Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans after the plan set is reviewed. Steps use checkbox (`- [ ]`) syntax. Do not start a separate application.

**Goal:** Implement the 25 retained expansion families without presenting undocumented concepts as working features.

**Architecture:** Extensions consume the same real IO/job/publication services. Security and codec qualification are explicit per-capability gates, not decoration.

**Tech Stack:** The pinned native Android stack and qualified adapters in the root plan.

**Spec:** `../../specs/2026-10-16-completion-design.md`

## Global Constraints

Inherit every constraint, fixed build version, shared interface and verification rule in `../2026-10-16-completion-plan.md`; it travels with this plan. In particular: preserve originals; no fabricated success; no unapproved local downloads; no font binaries, credentials or caches in deliverables. The exact approved spec is linked above.

## Review Focus

The five cross-cutting failure classes are recorded once in the root plan. Their owning regression tests are assigned to this plan's tasks below; do not substitute generic happy-path tests.

---

### Task E01: PDF optimization without flattening the document

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfOptimizer.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfOptimizerSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfOptimizerScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion01Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfOptimizer.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfOptimizerSettings`: preset enum (BALANCED,SCREEN,PRINT,CUSTOM), maxImageDpi Int?, jpegQuality Int?, stripUnusedObjects Boolean.

- [ ] **Step 1: Write failing regressions.** `downsamplesImagesButPreservesText` uses text plus a high-resolution embedded image; `largerOutputIsReported` uses an already optimized file.

```kotlin
assertEquals(originalTextTokens, textTokens(output))
assertEquals(inputHashes, hashesOfSources())
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion01Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use the qualified PDF adapter to rewrite supported image objects and remove unreachable objects on full save. Do not rasterize whole pages. Reject unsupported preservation cases. Report measured bytes, explicitly state intentional image quality loss, and preserve source dimensions/page boxes. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-01`.

### Task E02: PDF crop and page geometry

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfPageGeometry.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfPageGeometrySettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfPageGeometryScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion02Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfPageGeometry.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfPageGeometrySettings`: pageIndexes List<Int>, cropRectNormalized List<Float>?, targetWidthPt Float?, targetHeightPt Float?, placement enum (FIT,CENTER).

- [ ] **Step 1: Write failing regressions.** `cropChangesBoxNotSecretContent` verifies CropBox geometry and retained underlying content; `mixedPageSizesRespectPerPageScope` tests unequal pages.

```kotlin
assertEquals(expectedCropBoxes, pageBoxes(output))
assertTrue(unredactedTextStillExists(output))
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion02Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use PDF box/matrix operations with explicit per-page scope and preview. Cropping is visibility/geometry, never secure removal. Validate nonempty page rectangles and convert normalized coordinates after accounting for rotation; target-paper fit does not stretch text. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-02`.

### Task E03: PDF annotations and overlay text

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfAnnotations.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfAnnotationsSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfAnnotationsScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion03Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfAnnotations.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfAnnotationsSettings`: marks List<AnnotationSpec>; AnnotationSpec=(pageIndex,kind,normalizedBounds,points,text?,argb,strokeWidthPt,opacity).

- [ ] **Step 1: Write failing regressions.** `annotationRoundTripRetainsOriginalContents`, `deleteAnnotationOnlyRemovesChosenMark`, and `arabicOverlayIsShapedCorrectly` exercise text and scanned pages.

```kotlin
assertEquals(originalTextTokens, underlyingTextTokens(output))
assertEquals(expectedMarks, reopenedAnnotationCount)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion03Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Support text notes, text-layer highlight/underline/strikeout, free ink and simple shapes through supported PDF annotation objects. Scanned-page highlighting is geometric only. Use platform shaping for complex-script overlay appearance where needed; an image appearance is disclosed and is not original-text editing. Undo/redo acts on marks; no implicit flattening. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-03`.

### Task E04: PDF watermark and page numbering

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfStamping.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfStampingSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfStampingScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion04Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfStamping.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfStampingSettings`: mode enum (WATERMARK,NUMBERING), text String?, imageSourceId String?, pageIndexes List<Int>, anchor enum, offsetsPt pair, rotationDeg Float, opacity Float, startNumber Int, prefix/suffix String.

- [ ] **Step 1: Write failing regressions.** `numbersSelectedPagesUsingChosenStart`, `watermarkOpacityAndPositionMatchPreview` and `imageStampUsesActualLogo` preserve base pages.

```kotlin
assertEquals(listOf("Page 7", "Page 8"), selectedPageNumberLabels)
assertEquals(originalTextTokens, underlyingTextTokens(output))
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion04Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Append supported content/appearance streams, preserving original resources. Nine-point anchors use page boxes/rotation. Selected-page numbering advances only across the selected sequence and previews that policy. Do not embed unprovided fonts; complex-script appearance follows E03 policy. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-04`.

### Task E05: Drawn and imported signature appearance

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfDrawnSignature.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfDrawnSignatureSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfDrawnSignatureScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion05Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfDrawnSignature.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfDrawnSignatureSettings`: pageIndex Int, normalizedBounds List<Float>, strokes List<List<Float>>?, imageSourceId String?, saveForReuse Boolean=false.

- [ ] **Step 1: Write failing regressions.** `drawnSignatureAppearsOnlyOnSelectedPage`, `removeSavedSignatureDeletesManagedCopy`, and `signatureIsNeverCertified`.

```kotlin
assertEquals(expectedSignaturePage, pageContainingSignatureAppearance)
assertFalse(metadataClaimsCertificateSignature)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion05Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Compose an actual user-drawn/imported transparent mark and place it on the intended page. Reuse storage is opt-in and locally deletable. Label it Drawn signature, never validated digital signature or a legal-validity guarantee. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-05`.

### Task E06: Authorized PDF protection and removal

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfProtection.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfProtectionSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfProtectionScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion06Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfProtection.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfProtectionSettings`: mode enum (PROTECT,REMOVE), ephemeralSecretHandle String, protectionProfile enum (AES256_SUPPORTED).

- [ ] **Step 1: Write failing regressions.** `protectedCopyNeedsCorrectPassword`, `wrongPasswordPublishesNothing`, and `removalRequiresAuthorizedInput` with a known generated encrypted fixture.

```kotlin
assertEquals("PASSWORD_REQUIRED", inspectWithoutPassword.code)
assertEquals(expectedText, inspectWithCorrectPassword.text)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion06Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Obtain password material only from the ephemeral secret store, not persisted JSON. Qualify the backend encryption profile and recipient compatibility. Known-password removal creates a new unprotected copy after confirmation; no cracking/owner-permission bypass. Zero mutable secret buffers when released; process recovery requests secrets again. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-06`.

### Task E07: PDF metadata and page insertion

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfMetadataAndInsertion.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfMetadataAndInsertionSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfMetadataAndInsertionScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion07Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfMetadataAndInsertion.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfMetadataAndInsertionSettings`: operation enum (METADATA,INSERT,DUPLICATE), fields Map<String,String?>, insertionIndex Int?, sourcePageRefs List<PageCopy>, blankSizePt pair?.

- [ ] **Step 1: Write failing regressions.** `metadataRemovalDoesNotClaimHiddenContentRemoval`, `insertedPageOrderMatchesPreview`, and `duplicatePageUsesIndependentResources`.

```kotlin
assertEquals(listOf("FIRST", "INSERTED", "SECOND"), textTokens(output))
assertNull(readInfoDictionary(output)["Author"])
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion07Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Edit explicit Info/XMP fields only when supported and show the exact fields affected; do not promise removal of every private datum. Insert blank/image/PDF pages or duplicate selected pages through structural copying; use independent resources where later edits would otherwise affect both copies. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-07`.

### Task E08: Manual camera document capture

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/ManualDocumentCapture.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ManualDocumentCaptureSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/ManualDocumentCaptureScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion08Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ManualDocumentCapture.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ManualDocumentCaptureSettings`: imageSourceIds List<String>, perImageCorners Map<String,List<Float>>, rotationSteps Map<String,Int>, grayscale Boolean, contrast Float, output enum (PDF,IMAGES).

- [ ] **Step 1: Write failing regressions.** `fourCornerCorrectionMapsKnownGrid`, `cameraCancellationKeepsExistingPages`, and `deniedCaptureHasImportAlternative`.

```kotlin
assertEquals(expectedRectifiedCornerColors, outputCornerColors)
assertEquals(previousPageCount, countAfterCancelledCapture)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion08Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use a platform capture intent with a job-owned content URI or explicit photo import; no compulsory CameraX/ML downloads. Implement manual four-corner perspective correction with platform matrices, rotate/contrast/grayscale and PDF grouping using F04. No OCR, edge-detection model or AI claim. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-08`.

### Task E09: Supported AcroForms and explicit flattening

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfForms.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfFormsSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfFormsScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion09Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfForms.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfFormsSettings`: mode enum (FILL,FLATTEN), values Map<String,String>, fieldsToFlatten List<String>, flattenAnnotations Boolean=false.

- [ ] **Step 1: Write failing regressions.** `fillSupportedFieldTypes`, `flattenOnlyRequestedFields`, `xfaIsRejectedBeforeWrite` and `signedInputDoesNotLoseStatusSilently`.

```kotlin
assertEquals("Alice", reopenedFieldValue("name"))
assertEquals(expectedRemainingFields, interactiveFieldNames(flattenedOutput))
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion09Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Restrict initial support to verified text, checkbox, radio and choice AcroFields. Required-field validation is explicit. Rebuild appearances with qualified font behavior; unsupported XFA/complex appearances fail before mutation. Flattening is a named lossy operation and does not imply prevention of further editing. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-09`.

### Task E10: Non-AI PDF comparison

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfCompare.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfCompareSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfCompareScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion10Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfCompare.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfCompareSettings`: mode enum (VISUAL,TEXT_LAYER), pagePairs List<Pair<Int?,Int?>>, dpi Int=150, pixelTolerance Int=0, report Boolean.

- [ ] **Step 1: Write failing regressions.** `differentTokenAndMarkIsDetected`, `identicalFilesHaveNoDifferences`, and `unmatchedPagesRemainVisible`.

```kotlin
assertTrue(differenceBoxes.contains(changedMarkBounds))
assertEquals(emptyList<Any>(), equalDocumentDifferences)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion10Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Render equal-scale paired pages using P03 and compare pixels under an explicit threshold; optional digital-text diff uses P01 text, never OCR/semantic inference. Report mismatched rotation/page sizes instead of guessing alignment. Bound memory one pair at a time and generate a real difference report through F03 when selected. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-10`.

### Task E11: Content-removing PDF redaction with strict qualification

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/pdf/PdfRedaction.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/PdfRedactionSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/pdf/PdfRedactionScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion11Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `PdfRedaction.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `PdfRedactionSettings`: pageRegions Map<Int,List<List<Float>>>, digitalTextQueries List<String>, appearanceArgb Int, removeMetadata Boolean=false, removeAttachments Boolean=false.

- [ ] **Step 1: Write failing regressions.** `redactedSentinelAbsentFromAllReachableStreams`, `sharedFormResourcesDoNotLeakSecret`, `incrementalHistoryIsNotRetained`, and `unsupportedContentBlocksApply`.

```kotlin
assertFalse(allDecodedReachableContent(output).contains(secretSentinel))
assertFalse(anyResidualSecretImageObject(output))
assertEquals("UNSUPPORTED_REDACTION", unsupportedResult.errorCode)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion11Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Require a reviewed stream/object rewriting implementation for an explicit supported operator/resource subset; remove intersecting content, recompute references, purge unused resources and write a full nonincremental document. Clone shared resources before edits; no overlays, cropping or claimed removal with hidden originals. Complex nested forms/transparency/unsupported operators reject before output. Validate text, decoded streams, attachments when selected and rendered regions. This task stays BLOCKED unless those leakage tests and security review pass; hiding it is not completion. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xp-11`.

### Task E12: Image metadata inspector and selective removal

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/images/ImageMetadata.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ImageMetadataSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/images/ImageMetadataScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion12Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ImageMetadata.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ImageMetadataSettings`: removeGps Boolean, removeCameraIdentifiers Boolean, selectedFields Set<String>.

- [ ] **Step 1: Write failing regressions.** `metadataReportShowsMissingAsUnknown`, `orientationSurvivesRemoval`, and `sourceRemainsUnchanged`.

```kotlin
assertNull(outputExif.gpsLatitude)
assertEquals(expectedUprightCorner, decodedOutput.corner)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion12Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Read genuine EXIF and image details. Preserve/normalize orientation and needed profile information; remove only supported identifiers. No claim that identifying content within pixels is erased. Support batches through ordinary outcomes. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xi-01`.

### Task E13: Image text/logo watermark

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/images/ImageWatermark.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ImageWatermarkSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/images/ImageWatermarkScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion13Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ImageWatermark.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ImageWatermarkSettings`: text String?, logoSourceId String?, normalizedAnchor pair, offsetNormalized pair, opacity Float, rotationDeg Float, relativeSize Float, tile Boolean.

- [ ] **Step 1: Write failing regressions.** `watermarkContainsActualLogo`, `arabicTextAppearanceUsesCorrectDirection`, and `batchAnchorsRespectDifferentDimensions`.

```kotlin
assertEquals(expectedLogoPixel, watermarkRegionSample)
assertEquals(expectedNormalizedAnchor, observedAnchor)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion13Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use selected pixels and platform text shaping, alpha-aware compositing and normalized positioning. Support explicit all/per-image scope and real export through I01. Watermarking is user-requested; never introduce a product branding mark. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xi-02`.

### Task E14: Image joining and collage

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/images/ImageComposition.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ImageCompositionSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/images/ImageCompositionScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion14Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ImageComposition.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ImageCompositionSettings`: layout enum (VERTICAL,HORIZONTAL,GRID,HERO), sourceOrder List<String>, rows/columns Int, gapPx Int, targetWidth/Height Int, cellFit enum, matteArgb Int.

- [ ] **Step 1: Write failing regressions.** `allCellsUseSelectedImages`, `joiningOrderIsStable`, and `oversizedCanvasFailsBeforeAllocation`.

```kotlin
assertEquals(sourceColorOrder, sampledCellColors)
assertEquals("WORKLOAD_TOO_LARGE", oversizedCanvas.code)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion14Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Replace colored-cell fixtures with actual decoded source content. Compute cell geometry from target pixels, not screen dp. Preserve selected order, per-cell crop and common-width/height alignment; enforce aggregate bitmap budget and preview exact output dimensions. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xi-03`.

### Task E15: Image adjustments, padding and canvas

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/images/ImageAdjustments.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ImageAdjustmentsSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/images/ImageAdjustmentsScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion15Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ImageAdjustments.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ImageAdjustmentsSettings`: brightness Float, contrast Float, saturation Float, grayscale Boolean, paddingPx Int, borderPx Int, matteArgb Int.

- [ ] **Step 1: Write failing regressions.** `neutralSettingsPreservePixels`, `paddingChangesCanvasNotContentScale`, and `grayscaleProducesEqualRgb`.

```kotlin
assertEquals(sourcePixels, identityOutputPixels)
assertEquals(sourceWidth + 2*paddingPx, output.width)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion15Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Apply deterministic color matrices and explicit canvas expansion to actual inputs. Only fill transparent alpha when asked; do not remove subjects or infer backgrounds. Reset is a true identity operation; source color-space limitations follow I01. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xi-04`.

### Task E16: Best-effort target size and image comparison

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/images/ImageTargetAndCompare.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ImageTargetAndCompareSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/images/ImageTargetAndCompareScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion16Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ImageTargetAndCompare.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ImageTargetAndCompareSettings`: mode enum (TARGET_SIZE,COMPARE), targetBytes Long?, minQuality Int=40, allowResize Boolean=false, compareTolerance Int=0.

- [ ] **Step 1: Write failing regressions.** `unattainableTargetIsReported`, `targetSearchKeepsBestValidCandidate`, and `comparisonUsesSameScale`.

```kotlin
assertFalse(unattainableResult.reachedTarget)
assertTrue(candidateBytesAreMeasuredNotPredicted)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion16Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Bound encoding search to a documented finite attempt count (12), keep the smallest validated candidate meeting minimum quality, and never silently resize. Comparison is pixel/dimension based with a consistent view scale; no AI aesthetic analysis or guaranteed byte target. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xi-05`.

### Task E17: Manual screenshot stitching and codec capabilities

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/images/ImageStitching.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ImageStitchingSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/images/ImageStitchingScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion17Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ImageStitching.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ImageStitchingSettings`: sourceOrder List<String>, overlapsPx List<Int>, commonWidthPx Int, alignment enum; codec mode reads capability matrix.

- [ ] **Step 1: Write failing regressions.** `manualSeamsMatchChosenOverlap`, `decodeSupportDoesNotImplyEncodeSupport`, and `animatedInputNeedsExplicitStaticConsent`.

```kotlin
assertEquals(sumHeightsMinusOverlaps, stitchedHeight)
assertFalse(codecMatrix["HEIC"]!!.canEncode)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion17Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Join at manually selected boundaries, with proportional width alignment and source-order preview. Build a per-API decoder/encoder matrix from real platform probes. Additional TIFF/SVG/RAW/AVIF entries remain explicitly unsupported unless qualified by their own fixture paths; never invent universal format support. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xi-06`.

### Task E18: SHA-256 file verification

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/downloads/ChecksumVerification.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/ChecksumVerificationSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/downloads/ChecksumVerificationScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion18Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `ChecksumVerification.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `ChecksumVerificationSettings`: expectedSha256 String?; optional whitespace/case normalization.

- [ ] **Step 1: Write failing regressions.** `knownSha256Matches`, `mismatchIsNotSuccess`, and `checksumDoesNotImplySafety` use a fixed byte fixture.

```kotlin
assertEquals(expectedDigest, actualDigest)
assertEquals("MISMATCH", wrongExpectedValue.status)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion18Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Stream the actual managed/downloaded file through MessageDigest, validate supplied hex, and expose Match/Mismatch/Not supplied. Digest agreement only proves equality with the supplied value; no antivirus/authenticity certification. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xd-01`.

### Task E19: Scheduled transfer rules and bounded multipart

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/downloads/AdvancedTransfer.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/AdvancedTransferSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/downloads/AdvancedTransferScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion19Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `AdvancedTransfer.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `AdvancedTransferSettings`: notBeforeEpochMs Long?, requiresCharging Boolean, wifiOnly Boolean, unmeteredOnly Boolean, requestedParts Int=1 (allowed 1..4).

- [ ] **Step 1: Write failing regressions.** `multipartRecombinesExactBytes`, `unsupportedServerFallsBackBeforeWriting`, `scheduleUsesNotBeforeNotExactTime`, and `changedValidatorRejectsAssembly`.

```kotlin
assertEquals(referenceHash, combinedDownloadHash)
assertTrue(parts.all { it.validator == sharedStrongValidator })
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion19Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Reuse D01 validation and D02 persistence. Multipart begins only with a known size and a consistent strong validator; each range is stored separately and final assembly verified, with single-stream fallback before incompatible bytes mix. Do not advertise a speed multiplier or exact scheduled delivery; native OS timing constraints remain visible. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xd-02`.

### Task E20: Authorized browser download and webpage PDF capture

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/downloads/BrowserCapture.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/BrowserCaptureSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/downloads/BrowserCaptureScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion20Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `BrowserCapture.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `BrowserCaptureSettings`: sourceUrl String, explicitDownloadUrl String?, capturePdf Boolean, clearSession Boolean.

- [ ] **Step 1: Write failing regressions.** `webContentCannotInvokeNativeFilesystem`, `externalSchemeRequiresUserAction`, `downloadUsesSameQueue`, and `captureReflectsLoadedPage`.

```kotlin
assertFalse(javaScriptNativeBridgeExists)
assertEquals(downloadCoordinatorJobId, displayedJobId)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion20Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use a constrained, user-requested browser component only for this feature; the application remains native Compose, not a WebView wrapper. Disable file/universal-file access and native script bridges, enforce safe navigation/TLS and route direct downloads through D02. Use platform print-to-PDF for the loaded page where supported, with layout limits disclosed. No credential harvesting or protected-platform extraction; browser content uses internet explicitly. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xd-03`.

### Task E21: Batch rename with preview and conflict safety

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/files/BatchRename.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/BatchRenameSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/files/BatchRenameScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion21Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `BatchRename.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `BatchRenameSettings`: prefix/suffix String, find/replacement String, startingIndex Int, padWidth Int, preserveExtension Boolean=true, collisionPolicy CollisionPolicy.

- [ ] **Step 1: Write failing regressions.** `previewEqualsFinalNames`, `cycleRenameDoesNotOverwriteSources`, `invalidNameBlocksOnlyAffectedRows`, and `providerRefusalReportsPartialResults`.

```kotlin
assertEquals(previewNames, successfulFinalNames)
assertEquals(originalHashes, renamedContentHashes)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion21Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Plan rename targets first and detect collisions/cycles before provider operations. Use temporary unique names only on providers supporting safe rollback; otherwise reject cycles rather than overwrite. Track per-item outcomes and preserve extension by default. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xf-01`.

### Task E22: Exact duplicates and scoped storage analysis

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/files/DuplicateAndStorageScan.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/DuplicateAndStorageScanSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/files/DuplicateAndStorageScanScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion22Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `DuplicateAndStorageScan.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `DuplicateAndStorageScanSettings`: locationUris List<String>, recursive Boolean, mode enum (DUPLICATES,STORAGE).

- [ ] **Step 1: Write failing regressions.** `sameNameDifferentBytesNotDuplicate`, `sameDigestRequiresByteEquality`, and `cancelledScanReportsIncompleteCoverage`.

```kotlin
assertEquals(expectedByteIdenticalGroups, groups)
assertFalse(scan.completeAfterUnreadableSource)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion22Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Scan accessible user-selected locations only, group by size then digest then byte comparison, and revalidate before any selected deletion. No automatic deletion, near-duplicate AI, or whole-device coverage claims. Storage totals include explicit exclusions and incomplete scans. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xf-02`.

### Task E23: Favorites and real managed trash

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/files/FavoritesAndTrash.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/FavoritesAndTrashSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/files/FavoritesAndTrashScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion23Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `FavoritesAndTrash.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `FavoritesAndTrashSettings`: mode enum (FAVORITE,UNFAVORITE,TRASH,RESTORE,DELETE_PERMANENT), sourceIds List<String>, restoreCollisionPolicy CollisionPolicy.

- [ ] **Step 1: Write failing regressions.** `favoriteDoesNotDuplicateBytes`, `externalDeletionCannotOfferFalseRestore`, and `restoreConflictKeepsBoth`.

```kotlin
assertEquals(1, physicalCopyCountAfterFavoriting)
assertEquals(expectedRestoredHash, actualRestoredHash)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion23Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Favorite a stable reference; unavailable content requests reconnect. Trash only app-managed files with journaled reversible movement and original location metadata. Never promise recovery of arbitrary external-provider deletions. Permanent deletion is explicit; no forensic-erasure claim. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xf-03`.

### Task E24: Encrypted archives and explicit archive format support

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/expansions/files/EncryptedArchives.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/EncryptedArchivesSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/files/EncryptedArchivesScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion24Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `EncryptedArchives.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `EncryptedArchivesSettings`: mode enum (CREATE,EXTRACT), format enum (ZIP), ephemeralSecretHandle String, encryption enum (AES256), collisionPolicy CollisionPolicy.

- [ ] **Step 1: Write failing regressions.** `encryptedArchiveRoundTrip`, `wrongPasswordPublishesNoPlaintext`, `tamperStopsExtraction`, and `unsupportedFormatHasNoFakeFallback`.

```kotlin
assertEquals(inputHashes, extractedHashes)
assertEquals(0, publishedPlainFilesAfterWrongPassword)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion24Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Qualify `net.lingala.zip4j:zip4j:2.11.6` as the candidate [R10], with explicit costs/license/transitive review before dependency acquisition. Use streams and the A02 path/budget rules, not unchecked bulk extractAll. Additional RAR/7z/TAR read/write pairs require separate verified adapters in a capability table; do not imply RAR writing. Encryption failure never creates a plaintext fallback. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xf-04`.

### Task E25: Authenticated local encrypted vault

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/MainActivity.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/vault/VaultKeyAccess.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/vault/VaultSessionStore.kt`
- Modify: `app/src/main/java/com/universalutilities/app/data/jobs/CapabilityRegistry.kt`
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/CryptoVaultEngine.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/files/FilesAndVaultScreens.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/universalutilities/app/expansions/files/VaultOperations.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/expansions/VaultOperationsSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/expansions/files/VaultOperationsScreen.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/expansions/Expansion25Test.kt`

**Interfaces:**
- Consumes: F03 execution/output contracts and the completed core handler for this pillar; qualified codecs/backends only.
- Produces: `VaultOperations.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`; typed `VaultOperationsSettings`: mode enum (IMPORT,EXPORT,DELETE), sourceIds List<String>, authenticatedSessionId String, timeoutSeconds Int (30 or 120), lockOnBackground Boolean=true.

- [ ] **Step 1: Write failing regressions.** `unauthenticatedCallCannotDecrypt`, `tamperedOrTruncatedCiphertextNeverPublishes`, `relockInvalidatesSession`, `keyLossHasNoSilentReset` and `hardwareLabelMatchesKeyInfo`.

```kotlin
assertEquals("AUTH_REQUIRED", decryptWithoutAuthentication.errorCode)
assertEquals(0, publishedPlaintextAfterTamper)
assertTrue(vault.isLockedAfterBackground)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.expansions.Expansion25Test`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Replace CryptoVaultEngine and the initially unlocked screen. Use a FragmentActivity-compatible host for the existing AndroidX BiometricPrompt flow; preserve Compose setContent and Hilt integration. Qualify Tink Android 1.23.0 streaming AEAD (AES256_GCM_HKDF_1MB) for content [R7/R8]; wrap its key material with a user-auth-required Keystore AES-GCM key using a BiometricPrompt CryptoObject, not a Compose flag. Encrypt names/metadata as well as content; no plaintext keyset/preferences/log fallback. Sessions are process-local and expire on background/timeout; export requires fresh authentication. Bind ciphertext to version/file identity with nonsecret AAD, verify all segments before publishing exported plaintext, and remove temp plaintext on failure. Hardware protection applies to the wrapping key and is reported from actual capability [R9]. Key loss is unrecoverable without an explicit designed recovery mechanism. No zero-knowledge, universal StrongBox or secure-erasure guarantee. Keep this separately classified from the 16 V1 tool entries; enable only its demonstrated capability, not the whole expansion group.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement and verify xf-05`.
