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
import org.junit.Rule
import org.junit.Test

/** Real Settings Compose switch and DataStore persistence; requires an Android device. */
class InteractionSoundSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun interfaceSoundsSwitchPersistsAcrossActivityRecreation() {
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(15_000) { vm.settings.value.isLoaded }
        val before = vm.settings.value.soundsEnabled
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("navigation.bar").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithTag("onboarding.dismiss").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("onboarding.dismiss").performClick()
            }
            compose.onNodeWithTag("nav.SETTINGS").performClick()
            compose.onNodeWithTag("settings.screen").performScrollToNode(hasTestTag("settings.sounds"))
            vm.setSoundsEnabled(false)
            compose.waitUntil(10_000) { !vm.settings.value.soundsEnabled }
            compose.onNodeWithTag("settings.sounds").assertIsOff().performClick()
            compose.waitUntil(10_000) { vm.settings.value.soundsEnabled }
            compose.onNodeWithTag("settings.sounds").assertIsOn()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("settings.screen").performScrollToNode(hasTestTag("settings.sounds"))
            compose.onNodeWithTag("settings.sounds").assertIsOn()
        } finally {
            ViewModelProvider(compose.activity)[AppViewModel::class.java].setSoundsEnabled(before)
        }
    }
}
