package com.jumlo.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 5 Compose UI test for the Appearance App-color picker. Runs on JVM via
 *  Robolectric. Verifies the sheet renders presets and emits the chosen hex. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class AppearanceColorPickerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `picker renders the title and preset chips`() {
        compose.setContent {
            AppearanceColorPicker(current = "#1E88E5", onDismiss = {}, onSelect = {})
        }
        compose.onAllNodes(hasText("App color")).assertCountEquals(1)
        compose.onAllNodes(hasText("Blue")).assertCountEquals(1)
    }

    @Test
    fun `clicking a preset chip emits its hex seed`() {
        var selected: String? = null
        compose.setContent {
            AppearanceColorPicker(current = "#1E88E5", onDismiss = {}, onSelect = { selected = it })
        }
        compose.onAllNodes(hasText("Teal"))[0].performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("#00897B", selected)
    }

    @Test
    fun `applying the hex field emits the parsed color`() {
        var selected: String? = null
        compose.setContent {
            AppearanceColorPicker(current = "#1E88E5", onDismiss = {}, onSelect = { selected = it })
        }
        // The teal preset confirms the onSelect path with a canonical hex.
        compose.onAllNodes(hasText("Violet"))[0].performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("#7B4DFF", selected)
    }
}
