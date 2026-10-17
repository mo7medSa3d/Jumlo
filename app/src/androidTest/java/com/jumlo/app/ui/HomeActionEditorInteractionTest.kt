package com.jumlo.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.ui.home.HomeActionEditor
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeActionEditorInteractionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun accessibleMoveChangesTheOrderSubmittedBySave() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val tools=CapabilityRegistry.tools.filter {it.id in setOf("pdf_02","pdf_03","sha256")}
        var selected by mutableStateOf(listOf("pdf_02","pdf_03"))
        var saved:List<String>?=null
        compose.setContent {JumloTheme {HomeActionEditor(tools,selected,AppLanguage.ENGLISH,{selected=it},{saved=selected},{})}}
        val merge=tools.first {it.id=="pdf_03"}
        compose.onNodeWithContentDescription(context.getString(R.string.ui_action_up,merge.titleEn)).performClick()
        compose.runOnIdle {assertEquals(null,saved)}
        compose.onNodeWithText(context.getString(R.string.ui_actions_save)).performClick()
        compose.runOnIdle {assertEquals(listOf("pdf_03","pdf_02"),saved)}
    }
}
