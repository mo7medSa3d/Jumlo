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
                ToolsCatalogue(listOf(tool), AppLanguage.ENGLISH, emptyList(), emptyList(), emptyList(), "", {}, { selected = it.id })
            }
        }
        compose.onNodeWithTag("tool.icon.pdf_crop", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("tool.pdf_crop").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals("pdf_crop", selected) }
    }

    @Test fun persistedUsagePlacesMostUsedToolFirstWithinASection() {
        val tools = listOf("img_03", "img_02").map { id -> CapabilityRegistry.tools.single { it.id == id } }
        compose.setContent {
            JumloTheme(ThemeMode.DARK, AppLanguage.ARABIC) {
                ToolsCatalogue(tools, AppLanguage.ARABIC, listOf("img_02", "img_03"), emptyList(), emptyList(), "", {}, {})
            }
        }
        val first = compose.onNodeWithTag("tool.img_02").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("tool.img_03").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < last.top)
        tools.forEach { compose.onNodeWithTag("tool.${it.id}").assertExists() }
    }

    @Test fun defaultGridHidesAliasesButSearchCanOpenImageComparison() {
        val ids = listOf("img_compare", "file_trash", "file_02", "dl_settings", "img_04")
        val tools = ids.map { id -> CapabilityRegistry.tools.single { it.id == id } }
        val query = androidx.compose.runtime.mutableStateOf("")
        compose.setContent {
            JumloTheme(ThemeMode.LIGHT, AppLanguage.ENGLISH) {
                ToolsCatalogue(tools, AppLanguage.ENGLISH, emptyList(),
                    listOf("img_compare", "file_02"), listOf("file_trash"),
                    query.value, { query.value = it }, {})
            }
        }
        compose.onNodeWithTag("tool.img_04").assertExists()
        for (id in ids.filterNot { it == "img_04" }) {
            compose.onNodeWithTag("tool.$id").assertDoesNotExist()
        }
        compose.runOnIdle { query.value = "Compare" }
        compose.onNodeWithTag("tool.img_compare").assertHasClickAction()
        compose.onNodeWithTag("tool.file_02").assertDoesNotExist()
        compose.onNodeWithTag("tool.file_trash").assertDoesNotExist()
        compose.onNodeWithTag("tool.dl_settings").assertDoesNotExist()
    }

    @Test fun anEmptySearchResultProvidesResetAction() {
        val tool = CapabilityRegistry.tools.single { it.id == "pdf_crop" }
        var reset = false
        compose.setContent {
            JumloTheme(ThemeMode.LIGHT, AppLanguage.ARABIC) {
                ToolsCatalogue(listOf(tool), AppLanguage.ARABIC, emptyList(), emptyList(), emptyList(), "no-such-tool", { reset = it.isEmpty() }, {})
            }
        }
        compose.onNodeWithTag("tool.pdf_crop").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).onLast().performClick()
        compose.runOnIdle { assertEquals(true, reset) }
    }
}
