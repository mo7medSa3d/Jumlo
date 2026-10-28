package com.jumlo.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.FileViewMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesPersistenceTest {
    @Test fun appearanceFolderQualityAndViewSurviveRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = UserPreferencesRepository(context)
        val original = preferences.settingsFlow.first()
        try {
            preferences.setThemeMode(ThemeMode.DARK)
            preferences.setLanguage(AppLanguage.ARABIC)
            preferences.setAccentColor("DYNAMIC")
            preferences.setDefaultVideoQuality("720")
            preferences.setDefaultAudioQuality(128)
            preferences.setFileViewMode(FileViewMode.LIST)
            preferences.setDownloadDestinationFolder("content://com.android.externalstorage.documents/tree/primary%3ADownload")
            preferences.setWifiOnlyDefault(true)
            preferences.setRespectReducedMotion(true)
            preferences.setHapticIntensity(com.jumlo.app.model.HapticIntensity.STRONG)
            preferences.setMaxConcurrentDownloads(6)
            val restored = UserPreferencesRepository(context).settingsFlow.first()
            assertTrue(restored.isLoaded)
            assertEquals(ThemeMode.DARK, restored.themeMode)
            assertEquals(AppLanguage.ARABIC, restored.language)
            assertEquals("DYNAMIC", restored.accentColor)
            assertEquals("720", restored.defaultVideoQuality)
            assertEquals(128, restored.defaultAudioQuality)
            assertEquals(FileViewMode.LIST, restored.fileViewMode)
            assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownload",restored.downloadDestinationFolder)
            // Removed legacy preferences cannot silently keep blocking mobile downloads
            // or disable animation after the corresponding controls disappear.
            assertFalse(restored.wifiOnlyDefault)
            assertTrue(restored.respectReducedMotion)
            assertEquals(com.jumlo.app.model.HapticIntensity.STRONG, restored.hapticIntensity)
            assertTrue(restored.hapticFeedbackOnCommit)
            assertEquals(2, restored.maxConcurrentDownloads)
        } finally {
            preferences.setThemeMode(original.themeMode)
            preferences.setLanguage(original.language)
            preferences.setAccentColor(original.accentColor)
            preferences.setDefaultVideoQuality(original.defaultVideoQuality)
            preferences.setDefaultAudioQuality(original.defaultAudioQuality)
            preferences.setFileViewMode(original.fileViewMode)
            if(original.downloadDestinationFolder.startsWith("content://")) preferences.setDownloadDestinationFolder(original.downloadDestinationFolder)
            else preferences.resetDownloadDestinationFolder()
            preferences.setWifiOnlyDefault(original.wifiOnlyDefault)
            preferences.setRespectReducedMotion(original.respectReducedMotion)
            preferences.setHapticIntensity(original.hapticIntensity)
            preferences.setMaxConcurrentDownloads(original.maxConcurrentDownloads)
        }
    }
}
