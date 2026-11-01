package com.jumlo.app.ui.settings

import androidx.activity.ComponentActivity
import com.jumlo.app.model.AppLanguage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric coverage for the four-system Appearance color picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class AppearanceColorPickerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `picker renders all four color systems`() {
        compose.setContent {
            AppearanceColorPicker(current = "SUNSET", onDismiss = {}, onSelect = {})
        }
        listOf("Luigi", "Aurora", "Viola", "Sunset").forEach { name ->
            compose.onAllNodes(hasText(name)).assertCountEquals(1)
        }
    }

    @Test
    fun `selecting a color system emits its stable key`() {
        var selected: String? = null
        compose.setContent {
            AppearanceColorPicker(current = "SUNSET", onDismiss = {}, onSelect = { selected = it })
        }
        compose.onAllNodes(hasText("Aurora"))[0].performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("AURORA", selected)
    }

    @Test
    fun `selecting the default system emits its stable key`() {
        var selected: String? = null
        compose.setContent {
            AppearanceColorPicker(current = "AURORA", onDismiss = {}, onSelect = { selected = it })
        }
        compose.onAllNodes(hasText("Sunset"))[0].performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("SUNSET", selected)
    }

    @Test
    fun `arabic app language displays Arabic system names on an English device`() {
        compose.setContent {
            AppearanceColorPicker(
                current = "SUNSET", onDismiss = {}, onSelect = {},
                language = AppLanguage.ARABIC
            )
        }
        compose.onAllNodes(hasText("لويجي")).assertCountEquals(1)
        compose.onAllNodes(hasText("أخضر منعش")).assertCountEquals(1)
    }
}
