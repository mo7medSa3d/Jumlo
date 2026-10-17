package com.jumlo.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile covering the home -> tool -> workspace flow.
 * Generate: ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.benchmark.BaselineProfileGenerator
 * The generated profile is consumed through androidx.profileinstaller
 * (declared in :app). Jank target for this flow: < 5% frames over budget,
 * measured by FrameTimingMetric in HomeFlowBenchmark.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.jumlo.app",
        maxIterations = 5
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.res("com.jumlo.app", "tools_catalog_title")), 5_000)
    }
}
