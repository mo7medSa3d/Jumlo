# Scanner Full-Page Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the scanner from wrongly falling back to CameraX, and make the fallback a full-screen, full-resolution camera so the whole page is captured.

**Architecture:** `SmartDocumentScanButton` owns the primary ML Kit launcher and replaces its sticky `fallback` boolean with a small pure policy (retry a transient failure, fall back when Play Services is absent or after repeated failure). `CameraXScanFallback` becomes a full-screen preview with page frame and bottom controls, capturing at the device's best available resolution instead of the fixed 1600x2200 crop.

**Tech Stack:** Kotlin, Jetpack Compose, CameraX 1.4.0, ML Kit Document Scanner (GMS) 16.0.0.

**Spec:** `docs/superpowers/specs/2026-11-19-scanner-fullpage-capture-design.md`

## Global Constraints
- Keep `GmsDocumentScannerOptions.SCANNER_MODE_FULL`, `setPageLimit(100)` and both `RESULT_FORMAT_PDF` + `RESULT_FORMAT_JPEG`.
- Keep CameraX 1.4.0 and all existing dependencies; add no new dependency.
- Keep the PDF/JPEG result stores and publication flow unchanged.
- Keep the 100-page cap, cancellation and existing resource budgets.
- Build with JDK 17 (`JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk`), SDK at `~/Android/Sdk`; `gradlew` has no exec bit, run `bash gradlew`.

## Review Focus
- GMS available but ML Kit module download fails once: expect a retry, not a permanent fallback.
- GMS absent: expect immediate fallback with no retry loop or spinner.
- Very tall A4 page under a wide preview: expect the full frame captured, no forced 1600x2200 crop.
- Rapid double-tap on Capture or reaching 100 pages: expect exactly one page per tap and no overflow.
- Existing scan results and export: expect unchanged PDF/JPEG output and page count.

---

### Task 1: Pure fallback decision policy

**Files:**
- Create: `app/src/main/java/com/jumlo/app/ui/scan/ScannerFallbackPolicy.kt`
- Test: `app/src/test/java/com/jumlo/app/ui/scan/ScannerFallbackPolicyTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `internal object ScannerFallbackPolicy { fun shouldFallback(playServicesAvailable: Boolean, failures: Int): Boolean }` — true when Play Services is unavailable or `failures >= 3`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.jumlo.app.ui.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFallbackPolicyTest {
    @Test fun transientFailureRetries() {
        assertFalse(ScannerFallbackPolicy.shouldFallback(true, 0))
        assertFalse(ScannerFallbackPolicy.shouldFallback(true, 1))
        assertFalse(ScannerFallbackPolicy.shouldFallback(true, 2))
    }
    @Test fun repeatedFailureFallsBack() {
        assertTrue(ScannerFallbackPolicy.shouldFallback(true, 3))
        assertTrue(ScannerFallbackPolicy.shouldFallback(true, 9))
    }
    @Test fun missingPlayServicesFallsBackImmediately() {
        assertTrue(ScannerFallbackPolicy.shouldFallback(false, 0))
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `bash gradlew :app:testDebugUnitTest --tests 'com.jumlo.app.ui.scan.ScannerFallbackPolicyTest' --no-daemon --console=plain`
Expected: FAIL (unresolved reference `ScannerFallbackPolicy`).

- [ ] **Step 3: Implement the policy**

Create `ScannerFallbackPolicy.kt` with exactly the signature above. Body: `!playServicesAvailable || failures >= 3`. No Android imports.

- [ ] **Step 4: Run the test and verify it passes**

Run: same command as Step 2.
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jumlo/app/ui/scan/ScannerFallbackPolicy.kt app/src/test/java/com/jumlo/app/ui/scan/ScannerFallbackPolicyTest.kt
git commit -m "feat(scan): add pure fallback decision policy"
```

---

### Task 2: Retry the ML Kit scanner instead of sticking to the fallback

**Files:**
- Modify: `app/src/main/java/com/jumlo/app/ui/scan/SmartDocumentScanButton.kt:49-52,59-126`
- Modify strings: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml`

**Interfaces:**
- Consumes: `ScannerFallbackPolicy.shouldFallback(playServicesAvailable, failures)` from Task 1.
- Produces: unchanged `SmartDocumentScanLauncher` composable signature; a `Retry smart scan` and `Use basic camera` action rendered in the waiting screen.

- [ ] **Step 1: Add a guard assertion for the fallback policy**

Add to `app/src/test/java/com/jumlo/app/ui/scan/ScannerFallbackPolicyTest.kt`:

```kotlin
@Test fun fallbackPolicyIsMonotonicInFailures() {
    val seen = (0..4).map { ScannerFallbackPolicy.shouldFallback(true, it) }
    assertTrue(seen == listOf(false, false, false, true, true))
}
```

- [ ] **Step 2: Run the test and verify it passes**

Run: same command as Task 1 Step 2. Expected: PASS.

- [ ] **Step 3: Replace the sticky fallback with retry state**

In `SmartDocumentScanLauncher`: keep `error` as `rememberSaveable`; replace `fallback` initialisation with `val playServicesAvailable = GoogleApiAvailability... == SUCCESS` and `var failures by rememberSaveable { mutableIntStateOf(0) }`; keep `fallback` but only set it from `ScannerFallbackPolicy.shouldFallback(playServicesAvailable, failures)` inside the failure listeners (do not set it in the initial state unless Play Services is unavailable). Increment `failures` on every `getStartScanIntent` failure/catch. Do not change the successful-result handling.

- [ ] **Step 4: Add the retry and explicit-fallback actions**

In the waiting `Column` (lines 127-136), when `error && !fallback`, show a `QuietPrimaryButton` labelled with a new `scan_smart_retry` string that calls `startScan()` and a `QuietTonalButton` labelled `scan_use_basic_camera` that sets `fallback = true`. Add both strings in English and Arabic. Keep `LaunchedEffect(Unit)` auto-start exactly once.

- [ ] **Step 5: Verify the build**

Run: `bash gradlew :app:assembleDebug --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/jumlo/app/ui/scan/SmartDocumentScanButton.kt app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml app/src/test/java/com/jumlo/app/ui/scan/ScannerFallbackPolicyTest.kt
git commit -m "fix(scan): retry ML Kit scanner before falling back to camera"
```

---

### Task 3: Full-screen, full-resolution fallback camera

**Files:**
- Modify: `app/src/main/java/com/jumlo/app/ui/scan/CameraXScanFallback.kt:82-84,161-260`

**Interfaces:**
- Consumes: existing `pagePaths`, `capture`, `previewView`, `gallery`, `onScannedPdf`, `onScannedJpegs`.
- Produces: the same composable signature; no new public types.

- [ ] **Step 1: Remove the crop-producing fixed resolution**

In `CameraXScanFallback`, change the `ImageCapture` builder to omit `setTargetResolution(Size(1600,2200))`; use `CAPTURE_MODE_MINIMIZE_LATENCY` only, so CameraX selects the device's best available resolution. Keep `capture.targetRotation` updates and the 100-page cap unchanged.

- [ ] **Step 2: Make the preview full-screen**

Replace the `Column` + `LazyColumn(Modifier.weight(1f))` wrapper (starting at line 161) with a `Box(Modifier.fillMaxSize().testTag("scan.cameraFallback"))`. Place `AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())` as the background layer (no fixed `height(340.dp)`), and overlay the controls at the bottom in a `Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(...))`. Keep existing test tags, buttons, page list and export actions; move them into the overlay without changing their behaviour. Use `PreviewView.ScaleType.FILL_CENTER` (spec: no FIT_CENTER letterboxing) now that capture has no fixed target resolution — the preview fills the screen and the 24dp page guide sits over the feed rather than in letterbox bars. Bound the overlay with `heightIn(max = 0.5 * screenHeightDp)` and the thumbnail strip with a fixed height so the preview is never fully covered.

- [ ] **Step 3: Add a page frame guide**

Overlay a `Canvas(Modifier.matchParentSize())` above the preview and below the controls that draws an inset rounded rectangle stroke (page guide) using `MaterialTheme.colorScheme.onSurface` at low alpha. No edge-detection or warp logic.

- [ ] **Step 4: Verify the build**

Run: `bash gradlew :app:assembleDebug --no-daemon --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jumlo/app/ui/scan/CameraXScanFallback.kt app/src/main/java/com/jumlo/app/ui/scan/ScannerFallbackPolicy.kt app/src/test/java/com/jumlo/app/ui/scan/ScannerFallbackPolicyTest.kt
git commit -m "feat(scan): full-screen fallback camera with uncropped capture"
```

---

### Task 4: Full verification

**Files:** none.

- [ ] **Step 1: Run host and unit tests**

```bash
bash scripts/test-core.sh
bash gradlew :app:testDebugUnitTest --no-daemon --console=plain
```
Expected: host `RESULT passed=194 failed=0`; unit tests PASS.

- [ ] **Step 2: Run static scan tests that do not need a device**

```bash
python3 -B scripts/test-smart-scan-wiring.py 2>/dev/null || true
```
Expected: no regression in static wiring checks (report actual output).

- [ ] **Step 3: Build the APK**

```bash
bash gradlew :app:assembleDebug --no-daemon --console=plain
```
Expected: BUILD SUCCESSFUL; note the APK path in the final report.

- [ ] **Step 4: Report clearly what could not be verified**

State that no emulator/device is connected, so live camera framing, edge quality and the ML Kit module download path remain unverified on hardware; ask the user to test the two screens.
