package com.jumlo.app.ui.settings

import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.AppSettingsState
import com.jumlo.app.ui.theme.ColorSystems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ColorSystemLocalizationTest {
    @Test fun arabicPreferenceUsesArabicSystemName() {
        val system = ColorSystems.ALL.first { it.key == "AMETHYST" }
        assertEquals(system.nameAr, colorSystemLabel(system.key, AppLanguage.ARABIC))
        assertEquals(system.nameEn, colorSystemLabel(system.key, AppLanguage.ENGLISH))
    }

    @Test fun allSystemNamesHaveBilingualSettingsLabels() {
        ColorSystems.ALL.forEach { system ->
            assertEquals(system.nameAr, colorSystemLabel(system.key, AppLanguage.ARABIC))
            assertEquals(system.nameEn, colorSystemLabel(system.key, AppLanguage.ENGLISH))
        }
    }

    @Test fun invalidStoredSystemFallsBackToLocalizedCobalt() {
        val cobalt = ColorSystems.ALL.first { it.key == "COBALT" }
        assertEquals(cobalt.nameAr, colorSystemLabel("unrecognised", AppLanguage.ARABIC))
        assertEquals(cobalt.nameEn, colorSystemLabel("unrecognised", AppLanguage.ENGLISH))
    }

    @Test fun legacyKeysMigrateCleanlyToStudioSystems() {
        assertEquals(ColorSystems.COBALT.nameEn, colorSystemLabel("SUNSET", AppLanguage.ENGLISH))
        assertEquals(ColorSystems.EMERALD.nameEn, colorSystemLabel("LUIGI", AppLanguage.ENGLISH))
        assertEquals(ColorSystems.SAPPHIRE.nameEn, colorSystemLabel("AURORA", AppLanguage.ENGLISH))
        assertEquals(ColorSystems.AMETHYST.nameEn, colorSystemLabel("VIOLA", AppLanguage.ENGLISH))
        assertEquals(ColorSystems.DEFAULT.nameEn, colorSystemLabel("CLAY", AppLanguage.ENGLISH))
        assertEquals(ColorSystems.DEFAULT.nameEn, colorSystemLabel("#3478CC", AppLanguage.ENGLISH))
    }

    @Test fun settingsDefaultToSystemLanguage() {
        assertEquals(AppLanguage.SYSTEM, AppSettingsState().language)
        assertEquals("", AppLanguage.SYSTEM.code)
        assertTrue(AppLanguage.ARABIC.isRtl)
        assertFalse(AppLanguage.ENGLISH.isRtl)
    }

    @Test fun systemLanguageFollowsDeviceLocaleRtlAndLabels() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertTrue(AppLanguage.SYSTEM.isRtl)
            assertEquals(ColorSystems.COBALT.nameAr, colorSystemLabel("COBALT", AppLanguage.SYSTEM))

            Locale.setDefault(Locale.US)
            assertFalse(AppLanguage.SYSTEM.isRtl)
            assertEquals(ColorSystems.COBALT.nameEn, colorSystemLabel("COBALT", AppLanguage.SYSTEM))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
