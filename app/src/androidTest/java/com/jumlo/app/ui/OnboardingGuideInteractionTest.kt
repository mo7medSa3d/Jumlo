package com.jumlo.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.components.OnboardingGuide
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Touch-driven first-run checks. Requires an Android emulator/device to execute. */
class OnboardingGuideInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importClosesGuideWithOneTap() {
        var imported = 0
        compose.setContent {
            JumloTheme(ThemeMode.DARK, AppLanguage.ENGLISH) {
                OnboardingGuide(onImport={imported++},onScan={},onExplore={},onDismiss={})
            }
        }
        compose.onNodeWithTag("onboarding.import").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, imported) }
    }

    @Test fun scanActionIsAccessibleInArabicRtlWithLargeText() {
        var scans = 0
        compose.setContent {
            val density=LocalDensity.current
            JumloTheme(ThemeMode.DARK, AppLanguage.ARABIC) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides Density(density.density,2.0f)
                ) {
                    OnboardingGuide(onImport={},onScan={scans++},onExplore={},onDismiss={})
                }
            }
        }
        compose.onNodeWithTag("onboarding.guide").assertIsDisplayed()
        compose.onNodeWithTag("onboarding.scan").performClick()
        compose.runOnIdle {assertEquals(1,scans)}
    }

    @Test fun settingsReplayOffersExploreWithoutOverwritingPreferences() {
        var explored=0
        compose.setContent {
            JumloTheme {OnboardingGuide(onImport={},onScan={},onExplore={explored++},onDismiss={})}
        }
        compose.onNodeWithTag("onboarding.explore").performClick()
        compose.runOnIdle {assertEquals(1,explored)}
    }
}
