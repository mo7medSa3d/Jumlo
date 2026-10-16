package com.universalutilities.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.universalutilities.app.MainActivity
import org.junit.Rule
import org.junit.Test

class UniversalUtilitiesUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysFourCorePillarsAndFloatingDock() {
        composeTestRule.onNodeWithText("Universal Utilities").assertIsDisplayed()
        composeTestRule.onNodeWithText("PDF Tools").assertIsDisplayed()
        composeTestRule.onNodeWithText("Image Tools").assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithText("File Manager").assertIsDisplayed()
    }

    @Test
    fun navigation_toToolsCatalogAndMergePdf_worksCleanly() {
        composeTestRule.onNodeWithText("Tools").performClick()
        composeTestRule.onNodeWithText("Tools Catalog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Merge PDF").performClick()
        composeTestRule.onNodeWithText("Pdf Merge Workspace").assertIsDisplayed()
    }
}
