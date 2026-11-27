package com.jumlo.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.jumlo.app.MainActivity
import org.junit.Rule
import org.junit.Test

/** Verifies Settings excludes legacy UI sound and touch feedback toggles. */
class InteractionSoundSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsExcludesUiSoundAndTouchFeedbackToggles() {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(15_000) { vm.settings.value.isLoaded }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("navigation.bar").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("onboarding.dismiss").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("onboarding.dismiss").performClick()
        }
        compose.onNodeWithTag("nav.SETTINGS").performClick()
        compose.onNodeWithTag("settings.sounds").assertDoesNotExist()
        compose.onNodeWithTag("settings.haptics").assertDoesNotExist()
    }
}
