package com.jumlo.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumlo.app.model.AppSettingsState
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.components.AccountHubModal
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountHubInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun myFilesClosesHubAndNavigatesOnce() {
        var closed = 0; var navigated = 0
        compose.setContent {
            JumloTheme(ThemeMode.LIGHT) {
                AccountHubModal(AppSettingsState(), StorageUsage(), 0,
                    onDismiss = { closed++ }, onOpenSettings = { error("Unexpected settings navigation") }, onOpenFiles = { navigated++ })
            }
        }
        compose.onNodeWithTag("account.files").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, closed); assertEquals(1, navigated) }
    }

    @Test fun settingsClosesHubAndNavigatesOnce() {
        var closed = 0; var navigated = 0
        compose.setContent {
            JumloTheme(ThemeMode.DARK) {
                AccountHubModal(AppSettingsState(themeMode = ThemeMode.DARK), StorageUsage(), 3,
                    onDismiss = { closed++ }, onOpenSettings = { navigated++ }, onOpenFiles = { error("Unexpected files navigation") })
            }
        }
        compose.onNodeWithTag("account.settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, closed); assertEquals(1, navigated) }
    }
}
