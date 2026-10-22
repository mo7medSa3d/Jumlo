package com.jumlo.app.printing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.R
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The print page manager must let a user select, rotate and reorder pages by touch
 * instead of by typing page numbers.
 */
class PrintPageManagerInteractionTest {
    @get:Rule val compose=createComposeRule()

    private val pages = listOf(PrintPage(1), PrintPage(2), PrintPage(3))
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun pageDescription(position: Int) =
        context.getString(R.string.print_page_description, position, position)

    private fun setContent(
        selected: Set<Int> = setOf(0),
        focused: Int = 0,
        onFocus: (Int) -> Unit = {},
        onToggle: (Int) -> Unit = {},
        onSelectAll: () -> Unit = {},
        onOrder: (List<Int>) -> Unit = {},
        onRotate: (Int) -> Unit = {}
    ) {
        compose.setContent {
            JumloTheme {
                PrintPageManager(
                    pages = pages,
                    selected = selected,
                    focused = focused,
                    onFocus = onFocus,
                    onToggle = onToggle,
                    onSelectAll = onSelectAll,
                    onOrder = onOrder,
                    onRotate = onRotate,
                    renderThumbnail = { _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }
    }

    @Test fun tappingAPageSelectsItAndReportsThePosition() {
        var toggled = -1
        setContent(onToggle = { toggled = it })
        compose.onNodeWithContentDescription(pageDescription(2)).performClick()
        compose.runOnIdle { assertEquals(1, toggled) }
    }

    @Test fun rotateAndSelectAllActionsAreReachable() {
        var rotated = -1
        var selectedAll = false
        setContent(onRotate = { rotated = it }, onSelectAll = { selectedAll = true })
        compose.onNodeWithText(context.getString(R.string.print_rotate_page)).performClick()
        compose.onNodeWithText(context.getString(R.string.print_select_all_pages)).performClick()
        compose.runOnIdle {
            assertEquals(0, rotated)
            assertEquals(true, selectedAll)
        }
    }

    @Test fun longPressDragCommitsANewOrder() {
        var order: List<Int>? = null
        setContent(onOrder = { order = it })
        compose.onNodeWithContentDescription(pageDescription(1)).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(200f, 0f))
            advanceEventTime(120)
            up()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertNotNull("the drag must commit a new order", order) }
    }

    @Test fun aPlainTapNeverCommitsAnOrder() {
        var order: List<Int>? = null
        setContent(onOrder = { order = it })
        compose.onNodeWithContentDescription(pageDescription(1)).performClick()
        compose.runOnIdle { assertNull(order) }
    }
}
