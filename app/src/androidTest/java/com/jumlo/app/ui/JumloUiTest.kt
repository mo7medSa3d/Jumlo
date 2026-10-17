package com.jumlo.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jumlo.app.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Smoke tests against the REAL current UI text (previous version asserted
 * fixture-era strings such as "Pdf Merge Workspace" that no longer exist).
 * The four-configuration (light/dark × en/ar) matrix is covered by
 * ScreenConfigSemanticsTest for theme/locale composition; golden-image
 * screenshot tests still require recorded Paparazzi goldens (see
 * docs/FEATURE_STATUS.md).
 */
class JumloUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_showsAppNameAndHero() {
        composeTestRule.onNodeWithText("Jumlo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Powerful file tools").assertIsDisplayed()
    }
}
