package com.jumlo.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import com.jumlo.app.MainActivity
import com.jumlo.app.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real activity/back-stack coverage. These tests require an Android target. */
class JumloUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun dismissFirstRunGuide() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home.screen").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("onboarding.dismiss").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("onboarding.dismiss").performClick()
        }
    }

    @Test fun downloadManagerIsReachableWithoutAnActiveJobAndBackReturnsHome() {
        compose.onNodeWithTag("home.manageDownloads").performScrollTo().performClick()
        compose.onNodeWithTag("route.downloads").assertIsDisplayed()
        compose.onNodeWithTag("nav.DOWNLOADS").assertDoesNotExist()
        compose.onNodeWithTag("nav.HOME").assertIsSelected()
        pressBack()
        compose.onNodeWithTag("home.screen").assertIsDisplayed()
        compose.onNodeWithTag("nav.HOME").assertIsSelected()
    }

    @Test fun homeDownloadOpensEmptyWorkspaceAndInvalidInputDoesNotCrash() {
        compose.onNodeWithTag("home.action.download").performScrollTo().performClick()
        compose.onNodeWithTag("route.workspace").assertIsDisplayed()
        compose.onNodeWithTag("nav.TOOLS").assertIsSelected()
        val field = mediaLink()
        field.assertIsDisplayed().assertIsEnabled().assertTextEquals("")
        compose.onNodeWithText(compose.activity.getString(R.string.redesign_download_now)).assertIsNotEnabled()
        field.performTextInput("not a valid url")
        field.performImeAction()
        compose.waitForIdle()
        compose.onNodeWithTag("route.workspace").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.redesign_download_now)).assertIsNotEnabled()
    }

    @Test fun clipboardPasteKeepsWorkspaceUsableAfterRecoverableInspectionFailure() {
        compose.onNodeWithTag("home.action.download").performScrollTo().performClick()
        compose.onNodeWithTag("route.workspace").assertIsDisplayed()
        mediaLink().assertTextEquals("")

        // Exercise Android's clipboard and the real Paste handler. The reserved
        // host cannot provide media; no external download success is required.
        val pastedUrl = "https://example.invalid/clip.mp4"
        val clipboard = checkNotNull(compose.activity.getSystemService(ClipboardManager::class.java))
        var previousClip: ClipData? = null
        compose.runOnUiThread {
            previousClip = clipboard.primaryClip
            clipboard.setPrimaryClip(ClipData.newPlainText("Paste regression", pastedUrl))
        }
        try {
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_paste))
                .assertIsDisplayed().assertIsEnabled().performClick()
            mediaLink().assertTextEquals(pastedUrl).assertIsEnabled()
            compose.onNodeWithTag("route.workspace").assertIsDisplayed()
            compose.onNodeWithTag("nav.TOOLS").assertIsSelected()
            assertNoInspectedFormats()

            val retryHint = compose.activity.getString(R.string.redesign_download_retry_hint)
            // Loading/error is the next lazy item after the hero and link input.
            workspaceList().performScrollToIndex(2)
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(retryHint).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(retryHint).assertExists()
            compose.onNodeWithText(compose.activity.getString(R.string.media_analyzing)).assertDoesNotExist()
            mediaLink().assertTextEquals(pastedUrl).assertIsEnabled()
            compose.onNodeWithTag("route.workspace").assertIsDisplayed()
            assertNoInspectedFormats()

            // A failed probe must leave editing and navigation responsive.
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.signature_clear))
                .performClick()
            mediaLink().assertTextEquals("")
            compose.onNodeWithText(retryHint).assertDoesNotExist()
            compose.onNodeWithTag("nav.FILES").performClick()
            compose.onNodeWithTag("files.screen").assertIsDisplayed()
        } finally {
            compose.runOnUiThread {
                clipboard.setPrimaryClip(previousClip ?: ClipData.newPlainText("", ""))
            }
        }
    }

    @Test fun toolsSearchSurvivesTabSwitchAndActivityRecreation() {
        compose.onNodeWithTag("nav.TOOLS").performClick()
        toolsSearch().performTextReplacement("pdf")
        compose.onNodeWithTag("nav.FILES").performClick()
        compose.onNodeWithTag("files.screen").assertIsDisplayed()
        compose.onNodeWithTag("nav.TOOLS").performClick()
        toolsSearch().assertTextEquals("pdf")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("tools.screen").assertIsDisplayed()
        toolsSearch().assertTextEquals("pdf")
    }

    @Test fun settingsShortcutAndStorageBackReturnToTheCallingScreen() {
        compose.onNodeWithTag("nav.FILES").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings_title)).performClick()
        compose.onNodeWithTag("route.settings_detail").assertIsDisplayed()
        compose.onNodeWithTag("settings.screen").performScrollToNode(hasTestTag("settings.storage"))
        compose.onNodeWithTag("settings.storage").performClick()
        compose.onNodeWithTag("storage.screen").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("route.settings_detail").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("files.screen").assertIsDisplayed()
        compose.onNodeWithTag("nav.FILES").assertIsSelected()
    }

    @Test fun existingDownloadSessionSurvivesTabsAndFreshEntryIsEmpty() {
        compose.onNodeWithTag("home.action.download").performScrollTo().performClick()
        mediaLink().performTextInput("a draft link")
        compose.onNodeWithTag("nav.FILES").performClick()
        compose.onNodeWithTag("nav.TOOLS").performClick()
        compose.onNodeWithTag("route.workspace").assertIsDisplayed()
        mediaLink().assertTextEquals("a draft link")
        // Text entry can keep the IME open across restored tabs; this Back tests navigation.
        closeSoftKeyboard()
        // Back leaves this tool; a new Home action starts a distinct clean session.
        pressBack()
        // A typed but unsent draft is dirty. Back must ask before discarding it;
        // never silently drop the restored download session.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.ui_unsaved_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.ui_discard_leave)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home.screen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home.screen").assertIsDisplayed()
        compose.onNodeWithTag("nav.HOME").assertIsSelected()
        compose.onNodeWithTag("nav.TOOLS").performClick()
        compose.onNodeWithTag("tools.screen").assertIsDisplayed()
        compose.onNodeWithTag("nav.HOME").performClick()
        compose.onNodeWithTag("home.action.download").performScrollTo().performClick()
        mediaLink().assertTextEquals("")
    }

    @Test fun imageToolWithoutInputsShowsImportAndKeepsExecuteDisabled() {
        compose.onNodeWithTag("nav.TOOLS").performClick()
        toolsSearch().performTextReplacement("resize")
        compose.onNodeWithTag("tool.img_02").performScrollTo().performClick()
        compose.onNodeWithTag("route.workspace").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.workspace_process_files)).assertIsNotEnabled()
        val importPrompt = compose.activity.getString(R.string.ui_choose_inputs)
        // The lazy empty state follows presets and import actions on compact screens.
        workspaceList().performScrollToNode(hasText(importPrompt))
        compose.onNodeWithText(importPrompt).assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("tools.screen").assertIsDisplayed()
        compose.onNodeWithTag("nav.TOOLS").assertIsSelected()
    }

    @Test fun warmWorkspaceDeepLinkReachesTheRegisteredDestination() {
        compose.onNodeWithTag("nav.FILES").performClick()
        compose.onNodeWithTag("files.screen").assertIsDisplayed()
        val activity = compose.activity
        val launchIntent = Intent(activity.intent)
        try {
            repeat(2) {
                compose.runOnUiThread {
                    // A controlled empty draft isolates navigation from dirty-file dialogs/network work.
                    check(ViewModelProvider(activity)[AppViewModel::class.java].chooseTool("pdf_02"))
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("jumlo://workspace"))
                        .setClass(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("route.workspace").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("route.workspace").assertIsDisplayed()

                // The warm link must preserve the calling Files tab as the real Back destination.
                compose.onNodeWithContentDescription(activity.getString(R.string.scan_back))
                    .assertIsDisplayed().performClick()
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("files.screen").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("files.screen").assertIsDisplayed()
                compose.onNodeWithTag("nav.FILES").assertIsSelected()
                compose.onNodeWithTag("route.workspace").assertDoesNotExist()

                // Closing the linked tool must not leave a stale workspace in the Tools stack.
                compose.onNodeWithTag("nav.TOOLS").performClick()
                compose.onNodeWithTag("tools.screen").assertIsDisplayed()
                compose.onNodeWithTag("route.workspace").assertDoesNotExist()
                compose.onNodeWithTag("nav.FILES").performClick()
                compose.onNodeWithTag("files.screen").assertIsDisplayed()
            }
        } finally {
            // ActivityScenario 1.6.1 matches lifecycle events to the launch intent.
            // Restore it only after testing the real onNewIntent delivery and route.
            compose.runOnUiThread { activity.intent = launchIntent }
        }
    }

    private fun workspaceList() = compose.onNode(hasScrollToIndexAction() and
        hasAnyAncestor(hasTestTag("route.workspace")) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

    private fun mediaLink(): SemanticsNodeInteraction {
        val matcher = hasSetTextAction() and hasText(compose.activity.getString(R.string.media_link))
        workspaceList().performScrollToNode(matcher)
        return compose.onNode(matcher)
    }

    private fun assertNoInspectedFormats() {
        listOf(R.string.media_select_quality, R.string.media_select_type, R.string.media_audio_format).forEach { label ->
            compose.onNodeWithText(compose.activity.getString(label)).assertDoesNotExist()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.redesign_download_now)).assertIsNotEnabled()
    }

    private fun toolsSearch() = compose.onNode(hasSetTextAction() and
        hasContentDescription(compose.activity.getString(R.string.neon_tools_search)))

    private fun SemanticsNodeInteraction.assertTextEquals(value: String): SemanticsNodeInteraction =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
}
