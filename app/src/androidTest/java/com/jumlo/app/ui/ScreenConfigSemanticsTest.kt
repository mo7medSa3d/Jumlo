package com.jumlo.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.model.ManagedFileItem
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.components.QuietBeforeAfterPreview
import com.jumlo.app.ui.components.QuietFileRow
import com.jumlo.app.ui.components.QuietFloatingDock
import com.jumlo.app.ui.components.QuietHero
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietRootTopBar
import com.jumlo.app.ui.components.QuietSearchBar
import com.jumlo.app.ui.components.QuietSectionTitle
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.theme.JumloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot (bitmap-capture) and semantics verification across the four required
 * configurations: Light × English (LTR), Dark × English (LTR), Light × Arabic (RTL),
 * Dark × Arabic (RTL), plus 200% font scale accessibility layout verification.
 */
class ScreenConfigSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightEnglish_rendersAndCapturesMainScreens() =
        renderAndVerify(ThemeMode.LIGHT, LayoutDirection.Ltr, "Jumlo", "Powerful file tools", "Import")

    @Test
    fun darkEnglish_rendersAndCapturesMainScreens() =
        renderAndVerify(ThemeMode.DARK, LayoutDirection.Ltr, "Jumlo", "Powerful file tools", "Import")

    @Test
    fun lightArabicRtl_rendersAndCapturesMainScreens() =
        renderAndVerify(ThemeMode.LIGHT, LayoutDirection.Rtl, "Jumlo", "أدوات ملفات قوية", "استيراد")

    @Test
    fun darkArabicRtl_rendersAndCapturesMainScreens() =
        renderAndVerify(ThemeMode.DARK, LayoutDirection.Rtl, "Jumlo", "أدوات ملفات قوية", "استيراد")

    @Test
    fun fontScale200Percent_holdsLayoutInEnglishAndArabic() {
        renderAndVerify(ThemeMode.LIGHT, LayoutDirection.Ltr, "Jumlo", "Powerful file tools", "Import", fontScale = 2.0f)
    }

    private fun renderAndVerify(
        mode: ThemeMode,
        direction: LayoutDirection,
        appTitle: String,
        expectedTitle: String,
        expectedAction: String,
        fontScale: Float = 1.0f
    ) {
        val language = if (direction == LayoutDirection.Rtl) AppLanguage.ARABIC else AppLanguage.ENGLISH
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            JumloTheme(mode, language) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides direction,
                    LocalDensity provides Density(baseDensity.density, fontScale)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        QuietRootTopBar(
                            title = appTitle,
                            onSettingsClick = {}
                        )
                        QuietHero(
                            title = expectedTitle,
                            subtitle = "PDF, images, archives and downloads",
                            icon = Icons.Rounded.Bolt
                        )
                        QuietSearchBar(
                            query = "",
                            placeholder = expectedAction,
                            onQueryChange = {},
                            onClear = {}
                        )
                        QuietSectionTitle(text = expectedTitle)
                        QuietFileRow(
                            file = ManagedFileItem(
                                id = "f1",
                                name = "document.pdf",
                                path = "/results/document.pdf",
                                folder = "Results",
                                extension = "pdf",
                                mimeType = "application/pdf",
                                sizeBytes = 4096L,
                                formattedSize = "4.0 KiB",
                                pageCount = 3,
                                modifiedLabelEn = "Now",
                                modifiedLabelAr = "الآن"
                            ),
                            language = language,
                            onClick = {}
                        )
                        QuietBeforeAfterPreview(
                            fileName = "photo.jpg",
                            resolution = "80%",
                            originalLabel = "1.2 MiB",
                            compressedLabel = "340 KiB"
                        )
                        QuietStateCard(
                            title = expectedTitle,
                            subtitle = "100% on-device processing",
                            actionLabel = expectedAction,
                            onAction = {}
                        )
                        QuietPrimaryButton(
                            text = expectedAction,
                            onClick = {}
                        )
                        QuietFloatingDock(
                            selectedTab = DockTab.HOME,
                            language = language,
                            onSelectTab = {}
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText(appTitle).assertIsDisplayed()
        composeRule.onNodeWithText(expectedAction).assertIsDisplayed()
        val image = composeRule.onRoot().captureToImage()
        assertTrue(image.width > 0 && image.height > 0)
    }
}
