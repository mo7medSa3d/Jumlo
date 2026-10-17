package com.jumlo.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start macrobenchmark. Target: cold start < 1s on a mid-range profile.
 * Run: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdNoCompilation() = benchmarkRule.measureRepeated(
        packageName = "com.jumlo.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None()
    ) { pressHome(); startActivityAndWait() }

    @Test
    fun startupColdWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = "com.jumlo.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial()
    ) { pressHome(); startActivityAndWait() }
}
