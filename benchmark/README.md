# Real-device performance and baseline-profile harness

## Status and acceptance

The benchmark source is implemented; **no performance result or app-specific baseline-profile PASS is claimed until this suite runs on supported hardware**. The supplied profile files remain explanatory comments until real collected ART rules are reviewed and copied into the app. Never replace them with handwritten class lists or synthetic profile text.

The product target is **cold start below 2 seconds on a physical mid-range device**. Tools and Files scrolling have separate `FrameTimingMetric` scenarios. Inspect each actual frame timing result and trace for dropped frames; a test completing is not by itself proof of a jank-free UI. Record device model/SoC, API, refresh rate, build hash, thermal state, compilation mode, fixture set and all raw JSON/Perfetto outputs.

Use a portrait phone at 100% system/app font scale. Prefer API 34+ for these fixture-dependent runs: older Macrobenchmark compilation reset paths can reinstall the target and remove prepared app data. If fixtures disappear, the suite fails rather than substituting an empty grid. Emulator timing is not representative physical-device acceptance.

## Why these selectors work

The production root does not export Compose `testTag` values as Android resource IDs. The out-of-process helper therefore uses existing accessibility descriptions and text, loading both English and Arabic strings from the installed target. It does not mutate production semantics or assume `By.res("tools.grid")` works.

The helper selects the large vertical scrollable node, excluding small horizontal filter rows. It verifies that text/card positions change for every measured scroll. Tools preparation clears the search and verifies at least 24 distinct visible catalogue labels. Files preparation selects Results, filters the actual fixture names, checks side-by-side thumbnail geometry, and enumerates at least 24 distinct real published outputs. Failed selectors, missing fixtures, filtered/empty grids or no-motion gestures fail explicitly.

## Prepare real Files-grid fixtures

The benchmark module runs outside the app process. It cannot inject the app ViewModel or safely publish fake output-store rows. Ordinary imports populate the Sources list, which is not the requested Results grid. The fixture setup is therefore an explicit public-UI precondition:

1. Generate 24 deterministic 1280×960 PNG input images:

   ```bash
   python3 benchmark/generate-fixtures.py --output verification/benchmark-inputs
   adb shell mkdir -p /sdcard/Download/JumloBenchmark
   adb push verification/benchmark-inputs/. /sdcard/Download/JumloBenchmark/
   ```

2. In the installed **benchmark** app, dismiss onboarding, open **Tools → Convert images**, import all 24 PNGs using the normal Android picker, choose JPEG and process them. Let the real processing job complete and verify that its outputs appear in **Files → Results**. Keep `jumlo-benchmark-NNN` in each filename; normal output prefixes/extensions are allowed. Do not import `manifest.json`.
3. Select the Files grid and normal font size. Keep at least 24 of those validated outputs available. Do not clear app data between this step and fixture-dependent runs.

The PNGs are test inputs, not screenshots. They pass through normal import, decode, encode, validation and output publication before the Files benchmark uses them. The harness never fabricates a database row or a thumbnail.

## Commands

Build/installation is coordinated with the main release validation lane. Run on the selected physical test device:

```bash
./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark

./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.benchmark.StartupBenchmark#toolsGridScrollFrameTiming

./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.benchmark.StartupBenchmark#filesGridScrollFrameTiming

./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.benchmark.BaselineProfileGenerator

./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.benchmark.StartupBenchmark
```

`StartupBenchmark` has four scenarios: cold/no compilation, cold/required packaged baseline profile, Tools-grid scrolling, Files-grid scrolling. Grid setup/navigation/precondition scans occur outside the measured block. The measured block performs three down and three up scrolls with movement assertions. Grid scenarios use `UseIfAvailable`; record whether the artifact actually contains a reviewed app-specific profile. The required-profile startup case must not be reported as app-specific profile acceptance merely because a dependency supplied profile rules.

`BaselineProfileGenerator` exercises actual **Home → Tools → Crop pages workspace → Back → Files grid**, plus real Tools/Files scrolling. The workspace is deliberately empty, so this profile does not claim to cover document rendering, PDF processing, downloader networking or the external scanner. Review the generated rules and traces before adding real files to `app/src/main/baselineProfiles/` and rebuilding. Run startup/scroll measurements again against that rebuilt artifact.

Official methodology: [Write a Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) and [Control your app from Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-control-app). These guides explain out-of-process UI control, setup/measurement separation and the limits of emulator performance data.
