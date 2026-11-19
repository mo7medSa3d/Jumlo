package com.jumlo.app.ui.settings

import com.jumlo.app.model.AppLanguage
import com.jumlo.app.ui.theme.ColorSystems
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
