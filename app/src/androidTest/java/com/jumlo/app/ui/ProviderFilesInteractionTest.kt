package com.jumlo.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.R
import com.jumlo.app.data.files.DocumentEntry
import com.jumlo.app.data.files.DocumentListing
import com.jumlo.app.ui.files.ProviderFileRow
import com.jumlo.app.ui.files.ProviderRenameDialog
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Actual production row/dialog components; execution requires an Android device. */
class ProviderFilesInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun readOnlyFileExposesReadActionsWithoutMutationControls() {
        var actions = 0
        val file = DocumentEntry("source", "report.pdf", "application/pdf", 16, false, true, false, true, true)
        compose.setContent { JumloTheme {
            ProviderFileRow(file, false, { actions++ }, { actions++ }, { actions++ },
                { actions++ }, { actions++ }, { actions++ })
        } }
        compose.onNodeWithContentDescription(text(R.string.ui_more_actions)).performClick()
        compose.onNodeWithText(text(R.string.action_open)).assertExists()
        compose.onNodeWithText(text(R.string.files_import_action)).assertExists()
        compose.onNodeWithText(text(R.string.action_copy)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_rename)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.files_delete_original)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test fun inaccessibleDirectoryDoesNotExposeAnEmptyOverflow() {
        val directory = DocumentEntry("directory", "Private", null, null, true, false, true, true, true)
        compose.setContent { JumloTheme {
            ProviderFileRow(directory, true, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithContentDescription(text(R.string.ui_more_actions)).assertDoesNotExist()
    }

    @Test fun renameDialogBlocksCollisionThenSubmitsTheDisplayedNormalizedName() {
        var entered by mutableStateOf("original.pdf")
        var submitted: String? = null
        val source = DocumentEntry("source", "original.pdf", "application/pdf", 16, false, true, true, true, true)
        val other = DocumentEntry("other", "Report.PDF", "application/pdf", 16, false, true, true, true, true)
        val listing = DocumentListing("tree", "Documents", listOf(source, other), true)
        compose.setContent { JumloTheme {
            ProviderRenameDialog(source.uri, entered, listing, { entered = it }, {}, { submitted = it })
        } }
        val confirm = compose.onNode(hasText(text(R.string.action_rename)) and hasClickAction())
        confirm.assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("report.pdf")
        compose.onNodeWithText(text(R.string.files_rename_exists)).assertExists()
        confirm.assertIsNotEnabled()
        compose.runOnIdle { assertNull(submitted) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Cafe\u0301.pdf")
        compose.onNodeWithText("Café.pdf").assertExists()
        confirm.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("Café.pdf", submitted) }
    }

    @Test fun renameDialogRechecksChangedListingBeforeConfirmation() {
        var writable by mutableStateOf(true)
        var submitted: String? = null
        compose.setContent { JumloTheme {
            val source = DocumentEntry("source", "original.pdf", "application/pdf", 16,
                false, true, writable, true, true)
            ProviderRenameDialog(source.uri, "new.pdf", DocumentListing("tree", "Documents", listOf(source), true),
                {}, {}, { submitted = it })
        } }
        val confirm = compose.onNode(hasText(text(R.string.action_rename)) and hasClickAction())
        confirm.assertIsEnabled()
        compose.runOnIdle { writable = false }
        confirm.assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.msg_provider_unavailable)).assertExists()
        compose.runOnIdle { assertNull(submitted) }
    }
}
