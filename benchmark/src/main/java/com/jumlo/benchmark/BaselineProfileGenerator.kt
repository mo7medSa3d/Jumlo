package com.jumlo.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects an actual launcher-startup baseline, with no performance target claimed.
 * Run scripts/verify-native.sh baseline on an API 28+ rooted test device.
 * Review generated benchmark output before copying a profile into the app.
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
        device.waitForIdle()
    }
}
