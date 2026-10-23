package com.jumlo.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects actual Home -> Tools -> crop workspace -> Back -> Files/grid paths.
 * Requires the real Files fixtures described in benchmark/README.md and a supported test device.
 * Review generated benchmark output before copying a profile into the app.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 5
    ) {
        pressHome()
        startActivityAndWait()
        val ui=BenchmarkUi(device)
        ui.home()
        ui.tools()
        ui.scrollGrid()
        ui.openCropWorkspaceAndReturn()
        ui.filesGrid()
        ui.scrollGrid()
    }
}
