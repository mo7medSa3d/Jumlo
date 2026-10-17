package com.jumlo.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jumlo.app.MainActivity
import com.jumlo.app.R
import org.junit.Rule
import org.junit.Test

/** Real activity smoke coverage; execution requires an Android device/emulator. */
class JumloUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeShowsWorkingFrequentActionsWithoutHeroDashboard() {
        compose.waitForIdle()
        val welcome=compose.activity.getString(R.string.welcome_get_started)
        if(compose.onAllNodesWithText(welcome).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText(welcome).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_images_pdf)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_merge_pdf)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_resize_image)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_new_download)).assertIsDisplayed().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.workspace_direct_url_single)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.workspace_queue_download)).assertIsNotEnabled()
    }
}
