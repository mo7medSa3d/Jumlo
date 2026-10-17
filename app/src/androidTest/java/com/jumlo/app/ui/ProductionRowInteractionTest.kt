package com.jumlo.app.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.R
import com.jumlo.app.ui.components.FileMenuRow
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies overflow opening does not trigger the row or a destructive action. */
class ProductionRowInteractionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun overflowRequiresExplicitAction() {
        var opened=0
        var deleted=0
        compose.setContent {JumloTheme {FileMenuRow("sample.pdf","24 KiB",{opened++}) {close->
            DropdownMenuItem(text={Text("Delete sample")},onClick={close();deleted++})
        }}}
        val more=InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ui_more_actions)
        compose.onNodeWithContentDescription(more).performClick()
        compose.runOnIdle {assertEquals(0,opened);assertEquals(0,deleted)}
        compose.onNodeWithText("Delete sample").performClick()
        compose.runOnIdle {assertEquals(0,opened);assertEquals(1,deleted)}
    }
}
