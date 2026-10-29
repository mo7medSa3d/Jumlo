package com.jumlo.app.ui.settings

import com.jumlo.app.model.AppLanguage
import com.jumlo.app.ui.theme.ColorPresets
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentLocalizationTest {
    @Test fun arabicPreferenceUsesArabicPresetName() {
        val preset=ColorPresets.ALL.first { it.key == "TEAL" }
        assertEquals(preset.nameAr, previewAccentLabel(preset.key,AppLanguage.ARABIC))
        assertEquals(preset.nameEn, previewAccentLabel(preset.key,AppLanguage.ENGLISH))
    }

    @Test fun allPresetNamesHaveBilingualSettingsLabels() {
        ColorPresets.ALL.forEach { preset ->
            assertEquals(preset.nameAr,previewAccentLabel(preset.key,AppLanguage.ARABIC))
            assertEquals(preset.nameEn,previewAccentLabel(preset.key,AppLanguage.ENGLISH))
        }
    }

    @Test fun invalidStoredAccentFallsBackToLocalizedBlue() {
        val blue=ColorPresets.ALL.first { it.key == "BLUE" }
        assertEquals(blue.nameAr,previewAccentLabel("unrecognised",AppLanguage.ARABIC))
        assertEquals(blue.nameEn,previewAccentLabel("unrecognised",AppLanguage.ENGLISH))
    }

    @Test fun customHexSeedIsReadableWithoutAnInventedTranslation() {
        assertEquals("#3478CC",previewAccentLabel("#3478CC",AppLanguage.ARABIC))
        assertEquals("#3478CC",previewAccentLabel("#3478CC",AppLanguage.ENGLISH))
    }
}
