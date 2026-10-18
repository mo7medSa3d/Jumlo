# Performance report — 2026-10-17

## Measurement status

**No before/after physical-device performance measurements were available in this environment.** The source delivery does not claim faster measured startup, lower measured RSS, a jank percentage, or a generated/validated baseline profile. The original repository's handwritten ART rules were removed from active packaging. All three original files and hashes are retained as unmeasured historical evidence; the active paths contain comment-only pointers and zero ART rules.

There is no `/dev/kvm` and the recorded `adb devices -l` inventory contains zero connected devices. Build/JVM/compiler durations describe this build host only; they are not app-performance measurements. Current build artifact sizes, if built successfully, are recorded in RELEASE/native artifact evidence, not substituted for runtime measurements.

| Requested metric | Supplied baseline | Updated source | Acceptance target from plan |
|---|---|---|---|
| Cold TTID median / p90 | Not measured | Not measured | Suggested <=1.2 s median, <=2.0 s p90 on designated mid-range hardware |
| TTFD / warm startup | Not measured | Not measured | Calibrate after actual baseline |
| Routine scroll/navigation jank | Not measured | Not measured | Suggested <5% |
| Frozen frames | Not measured | Not measured | None above 700 ms in acceptance scenarios |
| Java/native heap high-water mark, processing RSS | Not measured | Not measured | Bounded supported workloads, no OOM |
| ANRs | No acceptance run | No acceptance run | Zero in defined acceptance scenarios |
| APK/AAB | No comparable built baseline | See actual artifacts in RELEASE | Compare equivalent variants/signing/resources |
| Baseline-profile benefit | No measured profile | Generator configured; hardware execution gate open | Measure equivalent baseline-profile off/on variants |

Targets are goals from the approved plan, not observed results or universal device promises.

## Changes intended to improve responsiveness

`JumloNavHost` is a navigation/launcher shell rather than the previous inline collection of feature screens. Home, Tools, Files, Downloads/Activity, Settings and Workspace collect their relevant flows with lifecycle awareness. Shared components are split by purpose. Account storage/output flows are only collected while the hub is open. This reduces unnecessary shared ownership; it is not a measured recomposition improvement and a complete screen-owned immutable UiState migration has not been attempted.

Previews decode off the main thread with a bounded target, cancellation and explicit bitmap/renderer cleanup. Obsolete preview work is cancelled. Image collage/logo/comparison now accounts for multiple simultaneously resident buffers, and PDF embedded-image downscaling checks allocation before large raw arrays. Dispatcher bitmap concurrency remains one; direct transfer concurrency is two. Static limits are not equivalent to measured total process RSS because native libraries and the OS allocate separately.

Storage scans and temporary cleanup run on IO and observe cancellation. Starting a newer storage scan cancels the obsolete one. Only reproducible expired cache is eligible for user cleanup. Job/output publication retains its durability checks; file safety is not traded for apparent responsiveness.

Navigation adapts to the unobstructed pane reported by WindowManager, with the rail threshold evaluated against that pane's width (>=600dp). Pure rectangle tests cover physical bounds, separating/occluding hinges and RTL tie-breaking. Favorites remain an ordered local preference. Batch rename previews use the actual naming algorithm off the main thread, and obsolete preview results cannot enable a changed request. Essential workspace controls and one execution CTA reduce visual density. Reduced-motion settings affect navigation and preview loading treatment. These source/policy checks do not replace resolved screen contrast, 200% text, fold/IME or interaction-jank acceptance on devices.

## Reproducible measurement setup

The benchmark module is included. The app's benchmark variant inherits release optimization, is non-debuggable and shell-profileable, and uses debug signing for installation. The generator must produce real rules from actual execution; no copied/generated-looking rule file is accepted as measurement evidence.

1. Choose and record one representative physical mid-range device: model, SoC/RAM, Android/API, display refresh, build fingerprint, power/thermal state and available storage. Also run API 26/36 smoke on emulators for compatibility, not physical performance.
2. Build the original supplied snapshot and final source with the same JDK/SDK/Gradle variants; record source manifests and artifact SHA-256, signing mode, dependency versions and build flags. Do not compare debug versus minified release as if it were a source improvement.
3. Install, seed the same actual PDF/image/archive/transfer fixtures, allow first-run initialization, control network/thermal conditions and document repetition/warm-up counts. Run cold startup and navigation benchmarks with trace capture.
4. Run the committed commands on the connected physical device:

```bash
bash scripts/verify-native.sh benchmark
bash scripts/verify-native.sh baseline
```

5. The supplied StartupBenchmark covers startup and home-screen scrolling. Expand actual navigation/workspace/processing journeys as required by the final acceptance fixtures. The baseline generator's documented launcher coverage does not claim to exercise every tool. Preserve JSON, Perfetto traces, generated rules, raw timings and screenshots.
6. Compute median/p90 startup and frame percentiles from actual samples, identify frozen frames and regressions, and compare peak heap/native RSS under the same maximum supported inputs. Verify the generated profile's packaging/benefit with a second equivalent run before including it in a release.

## Open gates

No measured trace/golden set is supplied. Real device benchmark execution, generated baseline publication, representative processing-memory sampling, full-route recomposition traces, real old-app update and accessibility/IME/fold testing remain open. The release decision remains NO-GO until these and the other criteria in RELEASE are satisfied.


## Current modified-source evidence boundary

No SDK or Android device was available to rebuild/benchmark this subsequent source revision. The 1-second cold-start goal and <5% jank goal are acceptance targets only, NOT results. The source includes the baseline/macrobenchmark modules but no measured profile was generated here. The updated media build split must also pass a measured release/benchmark APK composition and startup run before release.
