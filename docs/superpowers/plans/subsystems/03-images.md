# Image Conversion, Geometry and Batches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans after the plan set is reviewed. Steps use checkbox (`- [ ]`) syntax. Do not start a separate application.

**Goal:** Transform actual source image pixels with correct format and per-image settings.

**Architecture:** Reuse SourceImageDecoder, shared output publication and per-item results. Preview and full-resolution export have separate bounded work.

**Tech Stack:** The pinned native Android stack and qualified adapters in the root plan.

**Spec:** `../../specs/2026-10-16-completion-design.md`

## Global Constraints

Inherit every constraint, fixed build version, shared interface and verification rule in `../2026-10-16-completion-plan.md`; it travels with this plan. In particular: preserve originals; no fabricated success; no unapproved local downloads; no font binaries, credentials or caches in deliverables. The exact approved spec is linked above.

## Review Focus

The five cross-cutting failure classes are recorded once in the root plan. Their owning regression tests are assigned to this plan's tasks below; do not substitute generic happy-path tests.

---

### Task I01: Real format conversion, metadata and compression

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/ImageEngine.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/images/ImageWorkspaceScreens.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/ImageOperationSettings.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/images/ImageWorkspaceViewModel.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/images/ImageEncodingTest.kt`

**Interfaces:**
- Consumes: F04 normalized decoder and F03 real source/job/output contracts.
- Produces: `ImageEngine.convert(request: JobRequest, context: ExecutionContext): List<ItemOutcome>` and `compress(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`.

- [ ] **Step 1: Write failing regressions.** `inputPixelsSurviveConversion`, `jpgUsesChosenMatte`, `metadataRemovalKeepsOrientation`, `invalidCodecCannotSucceed` and `largerOutputIsNotSavings` exercise real encoded files, not bitmap labels.

```kotlin
assertEquals(expectedOpaqueMatte, decodedJpeg.pixelAtTransparentCorner)
assertEquals(ItemStatus.LARGER, largerResult.status)
assertEquals(2, batch.outcomes.count { it.status == ItemStatus.FAILED })
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.images.ImageEncodingTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** JPG quality 90; WebP lossy default 80, lossless only through supported encoder; PNG has no lossy-quality slider. HEIC is input-only when available. Detect animation/multiple frames and reject or require explicit supported still-frame consent. Normalize orientation, selectively remove identifiers and preserve/convert color information honestly. Compression presets 80/90/65; no resizing unless requested. Decode bounds before allocation; publish actual size/outcomes and retain successful members during retry.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement real image conversion and compression`.

### Task I02: Resize, crop, rotation, flip and per-image overrides

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/ImageEngine.kt`
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/PureDomainAlgorithms.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/images/ImageWorkspaceScreens.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/images/ImageGeometry.kt`
- Test: `app/src/test/java/com/universalutilities/app/images/ImageGeometryTest.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/images/ImageTransformTest.kt`

**Interfaces:**
- Consumes: I01 settings/decoder; normalized crop coordinates in [0,1], orientation already applied.
- Produces: `ImageGeometry.resolve(settings: ImageOperationSettings, width: Int, height: Int): ResolvedGeometry`; `ImageEngine.transform(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`.

- [ ] **Step 1: Write failing regressions.** `fitFillPadStretchAreDistinct` uses a 400x200 source and 100x100 box; `noUpscalePreservesSmallInput`, `rotatedCoordinatesPreserveCorners` and `batchOverrideSurvivesGroupEdit` pin scope and geometry.

```kotlin
assertEquals(100 to 50, fit.outputSize)
assertEquals(100 to 100, pad.outputSize)
assertEquals(expectedCornerPermutation, transformedCorners)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:testDebugUnitTest --tests 'com.universalutilities.app.images.ImageGeometryTest'`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Modes longest edge/width/height/bounding box/percentage; aspect lock on and upscaling off. Fit contains, Fill crops, Pad adds chosen matte, Stretch is explicit distortion. Original ratio comes from the actual image. Crop applies a normalized rectangle, 90-degree steps, horizontal/vertical flip; batch override keys are source IDs. Sub-editor returns geometry to Images-to-PDF instead of exporting intermediate images. Run ImageTransformTest on device before enabling output.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement faithful image geometry and batch scope`.
