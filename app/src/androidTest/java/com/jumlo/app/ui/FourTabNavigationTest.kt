package com.jumlo.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.components.QuietFloatingDock
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runtime geometry and selection assertions; requires an Android target. */
class FourTabNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requestedFourTabsAreOrderedAndSettingsHasItsOwnSelectedSlot() {
        render(AppLanguage.ENGLISH)
        val order = listOf("HOME", "TOOLS", "FILES", "SETTINGS")
        val centers = order.map { compose.onNodeWithTag("nav.$it").fetchSemanticsNode().boundsInRoot.center.x }
        assertTrue(centers.zipWithNext().all { (left, right) -> left < right })
        compose.onNodeWithTag("nav.DOWNLOADS").assertDoesNotExist()
        compose.onNodeWithTag("nav.HOME").assertIsSelected()
        compose.onNodeWithTag("nav.SETTINGS").performClick().assertIsSelected()
        compose.waitForIdle()
        assertIndicatorContained("SETTINGS")
        compose.onNodeWithTag("nav.PRINT").assertDoesNotExist()
    }

    @Test fun arabicMirrorsFourTabOrderAndContainsEveryIndicator() {
        render(AppLanguage.ARABIC)
        val order = listOf("HOME", "TOOLS", "FILES", "SETTINGS")
        val centers = order.map { compose.onNodeWithTag("nav.$it").fetchSemanticsNode().boundsInRoot.center.x }
        assertTrue(centers.zipWithNext().all { (first, second) -> first > second })
        compose.onNodeWithTag("nav.DOWNLOADS").assertDoesNotExist()
        order.forEach { name ->
            compose.onNodeWithTag("nav.$name").performClick().assertIsSelected()
            compose.waitForIdle()
            assertIndicatorContained(name)
        }
    }

    private fun assertIndicatorContained(tab: String) {
        val cell = compose.onNodeWithTag("nav.$tab").fetchSemanticsNode().boundsInRoot
        val indicator = compose.onNodeWithTag("nav.indicator.$tab", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(indicator.left >= cell.left && indicator.right <= cell.right)
        assertTrue(indicator.top >= cell.top && indicator.bottom <= cell.bottom)
    }

    private fun render(language: AppLanguage) {
        compose.setContent {
            var selected by remember { mutableStateOf(DockTab.HOME) }
            JumloTheme(ThemeMode.DARK, language) {
                QuietFloatingDock(selected, language, { selected = it }, onScan = {})
            }
        }
    }
}
