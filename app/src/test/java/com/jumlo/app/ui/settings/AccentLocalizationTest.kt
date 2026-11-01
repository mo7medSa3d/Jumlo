package com.jumlo.app.ui.settings

import com.jumlo.app.model.AppLanguage
import com.jumlo.app.ui.theme.ColorSystems
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSystemLocalizationTest {
    @Test fun arabicPreferenceUsesArabicSystemName() {
        val system = ColorSystems.ALL.first { it.key == "VIOLA" }
        assertEquals(system.nameAr, colorSystemLabel(system.key, AppLanguage.ARABIC))
        assertEquals(system.nameEn, colorSystemLabel(system.key, AppLanguage.ENGLISH))
    }

    @Test fun allSystemNamesHaveBilingualSettingsLabels() {
        ColorSystems.ALL.forEach { system ->
            assertEquals(system.nameAr, colorSystemLabel(system.key, AppLanguage.ARABIC))
            assertEquals(system.nameEn, colorSystemLabel(system.key, AppLanguage.ENGLISH))
        }
    }

    @Test fun invalidStoredSystemFallsBackToLocalizedSunset() {
        val sunset = ColorSystems.ALL.first { it.key == "SUNSET" }
        assertEquals(sunset.nameAr, colorSystemLabel("unrecognised", AppLanguage.ARABIC))
        assertEquals(sunset.nameEn, colorSystemLabel("unrecognised", AppLanguage.ENGLISH))
    }

    @Test fun legacyAccentKeysFallBackToSunsetInsteadOfInventingNames() {
        val sunset = ColorSystems.DEFAULT
        assertEquals(sunset.nameEn, colorSystemLabel("CLAY", AppLanguage.ENGLISH))
        assertEquals(sunset.nameEn, colorSystemLabel("#3478CC", AppLanguage.ENGLISH))
    }
}
