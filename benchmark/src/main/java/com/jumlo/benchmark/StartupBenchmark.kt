package com.jumlo.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start and frame-timing macrobenchmark.
 * Target: cold start < 2s on a physical mid-range device. Inspect scroll frame timing/traces
 * for dropped frames; source presence is not a measured performance result.
 * Run: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()
    private lateinit var preparedUi:BenchmarkUi

    @Test
    fun startupColdNoCompilation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None()
    ) { pressHome(); startActivityAndWait() }

    @Test
    fun startupColdWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(baselineProfileMode=BaselineProfileMode.Require)
    ) { pressHome(); startActivityAndWait() }

    @Test
    fun toolsGridScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        compilationMode = CompilationMode.Partial(baselineProfileMode=BaselineProfileMode.UseIfAvailable),
        setupBlock = {
            startActivityAndWait()
            preparedUi=BenchmarkUi(device).also {it.tools()}
        }
    ) {preparedUi.scrollGrid()}

    @Test
    fun filesGridScrollFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = null,
        compilationMode = CompilationMode.Partial(baselineProfileMode=BaselineProfileMode.UseIfAvailable),
        setupBlock = {
            startActivityAndWait()
            preparedUi=BenchmarkUi(device).also {it.filesGrid()}
        }
    ) {preparedUi.scrollGrid()}
}
