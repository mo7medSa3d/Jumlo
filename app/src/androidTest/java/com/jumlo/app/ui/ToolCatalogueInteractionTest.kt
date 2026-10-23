package com.jumlo.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.tools.ToolsCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ToolCatalogueInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun specialistToolIsDirectlyAvailableAsAnIconTile() {
        val tool = CapabilityRegistry.tools.single { it.id == "pdf_crop" }
        var selected = ""
        compose.setContent {
            JumloTheme(ThemeMode.LIGHT, AppLanguage.ENGLISH) {
                ToolsCatalogue(listOf(tool), AppLanguage.ENGLISH, emptyList(), "", {}, { selected = it.id })
            }
        }
        compose.onNodeWithTag("tool.icon.pdf_crop", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("tool.pdf_crop").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals("pdf_crop", selected) }
    }

    @Test fun persistedUsagePlacesMostUsedToolFirstWithoutRemovingOtherTools() {
        val tools = listOf("pdf_crop", "pdf_03", "img_02").map { id -> CapabilityRegistry.tools.single { it.id == id } }
        compose.setContent {
            JumloTheme(ThemeMode.DARK, AppLanguage.ARABIC) {
                ToolsCatalogue(tools, AppLanguage.ARABIC, listOf("img_02", "pdf_crop"), "", {}, {})
            }
        }
        val first = compose.onNodeWithTag("tool.img_02").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("tool.pdf_03").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < last.top)
        tools.forEach { compose.onNodeWithTag("tool.${it.id}").assertExists() }
    }

    @Test fun anEmptySearchResultProvidesResetAction() {
        val tool = CapabilityRegistry.tools.single { it.id == "pdf_crop" }
        var reset = false
        compose.setContent {
            JumloTheme(ThemeMode.LIGHT, AppLanguage.ARABIC) {
                ToolsCatalogue(listOf(tool), AppLanguage.ARABIC, emptyList(), "no-such-tool", { reset = it.isEmpty() }, {})
            }
        }
        compose.onNodeWithTag("tool.pdf_crop").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(true, reset) }
    }
}
