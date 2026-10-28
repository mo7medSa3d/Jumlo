package com.jumlo.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.FileViewMode
import com.jumlo.app.model.HapticIntensity
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
            preferences.setAccentColor("#7B4DFF")
            preferences.setDefaultVideoQuality("720")
            preferences.setDefaultAudioQuality(128)
            preferences.setFileViewMode(FileViewMode.LIST)
            preferences.setDownloadDestinationFolder("content://com.android.externalstorage.documents/tree/primary%3ADownload")
            preferences.setRespectReducedMotion(true)
            preferences.setHapticIntensity(HapticIntensity.STRONG)
            val restored = UserPreferencesRepository(context).settingsFlow.first()
            assertTrue(restored.isLoaded)
            assertEquals(ThemeMode.DARK, restored.themeMode)
            assertEquals(AppLanguage.ARABIC, restored.language)
            assertEquals("#7B4DFF", restored.accentColor)
            try {
                preferences.setAccentColor("not-a-color")
                fail("Invalid accent must fail")
            } catch (_: IllegalArgumentException) {
                assertEquals("#7B4DFF", UserPreferencesRepository(context).settingsFlow.first().accentColor)
            }
            assertEquals("720", restored.defaultVideoQuality)
            assertEquals(128, restored.defaultAudioQuality)
            assertEquals(FileViewMode.LIST, restored.fileViewMode)
            assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownload",restored.downloadDestinationFolder)
            // The fixed scheduler policy and removed global Wi-Fi-only default are not writable.
            // Per-job Wi-Fi policy remains independent of these obsolete DataStore keys.
            assertFalse(restored.wifiOnlyDefault)
            assertTrue(restored.respectReducedMotion)
            assertTrue(restored.hapticFeedbackOnCommit)
            assertEquals(HapticIntensity.STRONG, restored.hapticIntensity)
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
            preferences.setRespectReducedMotion(original.respectReducedMotion)
            preferences.setHapticIntensity(original.hapticIntensity)
        }
    }
}
