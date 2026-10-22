package com.jumlo.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.jumlo.app.MainActivity
import com.jumlo.app.R
import org.junit.Rule
import org.junit.Test

/** Real activity smoke coverage; execution requires an Android device/emulator. */
class JumloUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeFrequentActionsReachDownloadWorkspace() {
        compose.waitForIdle()
        val welcome=compose.activity.getString(R.string.welcome_get_started)
        if(compose.onAllNodesWithText(welcome).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(welcome).performClick()
        }
        // Home is scrollable on compact displays, and its tiles use catalogue titles.
        listOf(R.string.tool_images_to_pdf,R.string.tool_merge_pdf,R.string.tool_resize_images).forEach {
            scrollToText(compose.activity.getString(it))
        }
        val homeShortcut=scrollToText(compose.activity.getString(R.string.neon_shortcut_download))
        homeShortcut.performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.neon_download_headline)).assertIsDisplayed()
        val newDownload=scrollToText(compose.activity.getString(R.string.ui_new_download))
        newDownload.performClick()
        val mediaLink=compose.onNodeWithText(compose.activity.getString(R.string.media_link))
        mediaLink.performScrollTo()
        mediaLink.assertIsDisplayed().assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        compose.onNodeWithText(compose.activity.getString(R.string.redesign_download_now))
            .assertIsDisplayed().assertIsNotEnabled()
    }

    private fun scrollToText(text:String):SemanticsNodeInteraction {
        // Select the page list, excluding nested recent-file carousels.
        val page=compose.onNode(hasScrollToNodeAction() and !hasAnyAncestor(hasScrollToNodeAction()))
        page.performScrollToNode(hasText(text))
        val target=compose.onNodeWithText(text)
        // ScrollToNode only respects the list bounds; the floating dock overlays
        // their bottom. Keep the real touch target in the upper half before tapping.
        val targetBounds=target.fetchSemanticsNode().boundsInRoot
        val pageBounds=page.fetchSemanticsNode().boundsInRoot
        val distance=(targetBounds.bottom-pageBounds.center.y).coerceAtLeast(0f)
        if(distance>0f) page.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy->scrollBy(0f,distance) }
        target.assertIsDisplayed()
        return target
    }
}
