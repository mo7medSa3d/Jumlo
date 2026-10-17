package com.jumlo.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Rule
import org.junit.Test

/**
 * Semantics-level screen configuration coverage: light/dark × English/Arabic
 * (RTL). This verifies composition and layout direction per configuration;
 * it is NOT a golden-image screenshot test. Golden screenshots (Paparazzi)
 * remain pending first golden recording — tracked in docs/FEATURE_STATUS.md.
 */
class ScreenConfigSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightEnglish_renders() = render(ThemeMode.LIGHT, LayoutDirection.Ltr, "Scan document")

    @Test
    fun darkEnglish_renders() = render(ThemeMode.DARK, LayoutDirection.Ltr, "Scan document")

    @Test
    fun lightArabicRtl_renders() = render(ThemeMode.LIGHT, LayoutDirection.Rtl, "مسح المستند")

    @Test
    fun darkArabicRtl_renders() = render(ThemeMode.DARK, LayoutDirection.Rtl, "مسح المستند")

    private fun render(mode: ThemeMode, direction: LayoutDirection, expected: String) {
        composeRule.setContent {
            JumloTheme(mode, if (direction == LayoutDirection.Rtl) AppLanguage.ARABIC else AppLanguage.ENGLISH) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Column { Text(expected) }
                }
            }
        }
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }
}
