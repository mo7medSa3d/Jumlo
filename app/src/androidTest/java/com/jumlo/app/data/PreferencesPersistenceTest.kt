package com.jumlo.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesPersistenceTest {
    @Test fun savedAppearanceAndDownloadPolicySurviveRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = UserPreferencesRepository(context)
        val original = preferences.settingsFlow.first()
        try {
            preferences.setThemeMode(ThemeMode.DARK)
            preferences.setLanguage(AppLanguage.ARABIC)
            preferences.setWifiOnlyDefault(true)
            preferences.setRespectReducedMotion(true)
            preferences.setMaxConcurrentDownloads(6)
            val restored = UserPreferencesRepository(context).settingsFlow.first()
            assertTrue(restored.isLoaded)
            assertEquals(ThemeMode.DARK, restored.themeMode)
            assertEquals(AppLanguage.ARABIC, restored.language)
            assertTrue(restored.wifiOnlyDefault)
            assertTrue(restored.respectReducedMotion)
            assertEquals(6, restored.maxConcurrentDownloads)
        } finally {
            preferences.setThemeMode(original.themeMode)
            preferences.setLanguage(original.language)
            preferences.setWifiOnlyDefault(original.wifiOnlyDefault)
            preferences.setRespectReducedMotion(original.respectReducedMotion)
            preferences.setMaxConcurrentDownloads(original.maxConcurrentDownloads)
        }
    }
}
