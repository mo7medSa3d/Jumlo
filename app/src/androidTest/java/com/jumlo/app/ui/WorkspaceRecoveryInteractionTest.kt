package com.jumlo.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.R
import com.jumlo.app.model.*
import com.jumlo.app.ui.components.TransferRow
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.workspace.ContentToolSettings
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI recovery/serialization regressions; durable job retry is tested by the job-store suite. */
class WorkspaceRecoveryInteractionTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun waitingPdfOffersRetryWithoutReplacementLink() = verifyWaitingRetry("pdf_02")
    @Test fun waitingDownloadOffersRetryWithoutForcingReplacementLink() = verifyWaitingRetry("dl_02")

    private fun verifyWaitingRetry(toolId:String) {
        val job=JobSnapshot(JobRequest("existing-job",toolId,listOf("source"),1,"{}",1L),
            JobProgress(JobStage.WAITING_FOR_INPUT,0L,null),emptyList(),1L)
        var retriedId:String?=null
        var retries=0
        compose.setContent {JumloTheme {TransferRow(job,AppLanguage.ENGLISH,null,{}, {},{retriedId=job.request.id;retries++})}}
        compose.onNodeWithContentDescription(context.getString(R.string.ui_more_actions)).performClick()
        compose.onNode(isPopup()).assertIsDisplayed()
        compose.runOnIdle {assertEquals(null,retriedId);assertEquals(0,retries)}
        // The row also has an inline Retry button; exercise the menu we just opened.
        compose.onNode(hasText(context.getString(R.string.action_retry_failed)) and hasAnyAncestor(isPopup()))
            .assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals("existing-job",retriedId);assertEquals(1,retries)}
        compose.onNode(isPopup()).assertDoesNotExist()
    }

    @Test fun watermarkContentIsEditableWithoutOpeningAdvancedAndLogoModeHidesText() {
        var draft by mutableStateOf(WorkspaceDraft.forTool("img_watermark",listOf("photo","logo")))
        compose.setContent {JumloTheme {ContentToolSettings(draft) {edit->draft=edit(draft)}}}
        compose.onNodeWithText(context.getString(R.string.field_watermark_text)).assertIsDisplayed().performTextReplacement("Owner")
        compose.runOnIdle {
            assertEquals("Owner",Json.parseToJsonElement(draft.executionSettings()).jsonObject.getValue("text").jsonPrimitive.content)
        }
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText(context.getString(R.string.field_watermark_text)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ui_watermark_logo_required)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(true,Json.parseToJsonElement(draft.executionSettings()).jsonObject.getValue("useLogo").jsonPrimitive.boolean)
        }
    }
}
