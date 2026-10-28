package com.jumlo.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFeedbackPolicyTest {
    @Test fun systemReducedMotionCannotBeOverriddenByAppSetting() {
        assertTrue(AppFeedbackPolicy.reducedMotion(systemReduced = true, userRequested = false))
        assertTrue(AppFeedbackPolicy.reducedMotion(systemReduced = true, userRequested = true))
    }

    @Test fun userReduceMotionWorksWhenSystemAnimationsAreEnabled() {
        assertTrue(AppFeedbackPolicy.reducedMotion(systemReduced = false, userRequested = true))
        assertFalse(AppFeedbackPolicy.reducedMotion(systemReduced = false, userRequested = false))
    }

    @Test fun hapticsAreDisabledByEitherSwitchOrOffIntensity() {
        for (level in HapticIntensity.entries) {
            assertFalse(AppFeedbackPolicy.hapticsEnabled(userEnabled = false, intensity = level))
        }
        assertFalse(AppFeedbackPolicy.hapticsEnabled(userEnabled = true, intensity = HapticIntensity.OFF))
        for (level in listOf(HapticIntensity.LIGHT, HapticIntensity.MEDIUM, HapticIntensity.STRONG)) {
            assertTrue(AppFeedbackPolicy.hapticsEnabled(userEnabled = true, intensity = level))
        }
    }
}
