package com.jumlo.app.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import com.jumlo.app.MainActivity
import com.jumlo.app.model.HapticIntensity
import org.junit.Rule
import org.junit.Test

/** UI and DataStore interaction qualification; a device is required to run this test. */
class InteractionSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openSettings() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("home.screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("onboarding.dismiss").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("onboarding.dismiss").performClick()
        }
        compose.onNodeWithTag("nav.SETTINGS").performClick()
        compose.onNodeWithTag("settings.screen").performScrollToNode(hasTestTag("settings.reduceMotion"))
    }

    @Test fun motionSwitchUpdatesPreferenceAndPersistsAcrossActivityRecreation() {
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val original = viewModel.settings.value.respectReducedMotion
        try {
            openSettings()
            viewModel.setRespectReducedMotion(false)
            compose.waitUntil(10_000) { !viewModel.settings.value.respectReducedMotion }
            compose.onNodeWithTag("settings.reduceMotion").assertIsOff().performClick()
            compose.waitUntil(10_000) { viewModel.settings.value.respectReducedMotion }
            compose.onNodeWithTag("settings.reduceMotion").assertIsOn()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("settings.screen").performScrollToNode(hasTestTag("settings.reduceMotion"))
            compose.onNodeWithTag("settings.reduceMotion").assertIsOn()
        } finally {
            ViewModelProvider(compose.activity)[AppViewModel::class.java]
                .setRespectReducedMotion(original)
        }
    }

    @Test fun hapticsSwitchDisablesAllIntensityAndAllowsReenable() {
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val originalIntensity = viewModel.settings.value.hapticIntensity
        val originallyEnabled = viewModel.settings.value.hapticFeedbackOnCommit
        try {
            openSettings()
            viewModel.setHapticIntensity(HapticIntensity.MEDIUM)
            compose.waitUntil(10_000) {
                viewModel.settings.value.hapticIntensity == HapticIntensity.MEDIUM
            }
            compose.onNodeWithTag("settings.screen").performScrollToNode(hasTestTag("settings.haptics"))
            compose.onNodeWithTag("settings.haptics").assertIsOn().performClick()
            compose.waitUntil(10_000) { !viewModel.settings.value.hapticFeedbackOnCommit }
            compose.onNodeWithTag("settings.haptics").assertIsOff().performClick()
            compose.waitUntil(10_000) {
                viewModel.settings.value.hapticIntensity == HapticIntensity.LIGHT
            }
            compose.onNodeWithTag("settings.haptics").assertIsOn()
        } finally {
            viewModel.setHapticIntensity(originalIntensity)
            if (!originallyEnabled) viewModel.setHapticFeedback(false)
        }
    }
}
