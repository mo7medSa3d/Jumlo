package com.jumlo.app.ui.settings

import com.jumlo.app.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentLabelLocalizationTest {
    @Test fun savedNamedAccentsFollowExplicitAppLanguage() {
        assertEquals("Violet", previewAccentLabel("VIOLET", AppLanguage.ENGLISH))
        assertEquals("بنفسجي", previewAccentLabel("VIOLET", AppLanguage.ARABIC))
        assertEquals("Orange", previewAccentLabel("ORANGE", AppLanguage.ENGLISH))
        assertEquals("برتقالي", previewAccentLabel("ORANGE", AppLanguage.ARABIC))
    }

    @Test fun unknownAccentsUseLocalizedFallbackAndCustomHexIsPreserved() {
        assertEquals("Blue", previewAccentLabel("not-a-preset", AppLanguage.ENGLISH))
        assertEquals("أزرق", previewAccentLabel("not-a-preset", AppLanguage.ARABIC))
        assertEquals("#7B4DFF", previewAccentLabel("#7B4DFF", AppLanguage.ARABIC))
    }
}
