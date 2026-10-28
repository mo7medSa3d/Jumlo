package com.jumlo.app.ui.workspace

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jumlo.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Latin-only limit is shown before invoking OCR, in both app languages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi")
class OcrLanguageNoticeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun englishOcrWorkspaceShowsHonestScriptLimitation() {
        compose.setContent { MaterialTheme { OcrLanguageNotice() } }
        compose.onNodeWithTag("ocr.script-limitation").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.ocr_scanned_arabic_limit)).assertExists()
    }

    @Test @Config(qualifiers = "ar-rEG-w411dp-h891dp-mdpi")
    fun arabicOcrWorkspaceShowsLocalizedScriptLimitation() {
        compose.setContent { MaterialTheme { OcrLanguageNotice() } }
        compose.onNodeWithTag("ocr.script-limitation").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.ocr_scanned_arabic_limit)).assertExists()
    }
}
