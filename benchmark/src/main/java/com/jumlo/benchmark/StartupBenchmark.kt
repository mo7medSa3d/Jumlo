package com.jumlo.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start and frame-timing macrobenchmark.
 * Targets: cold start < 1s on a mid-range profile, < 5% jank frames during scroll.
 * Run: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdNoCompilation() = benchmarkRule.measureRepeated(
        packageName = "com.jumlo.app",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None()
    ) { pressHome(); startActivityAndWait() }

    @Test
    fun startupColdWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = "com.jumlo.app",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial()
    ) { pressHome(); startActivityAndWait() }

    @Test
    fun homeCatalogScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = "com.jumlo.app",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial()
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg("com.jumlo.app")), 5_000)
        device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
        device.waitForIdle()
    }
}
