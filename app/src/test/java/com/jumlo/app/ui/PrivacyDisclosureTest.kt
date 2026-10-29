package com.jumlo.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.jumlo.app.R
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Public-facing disclosure contract for the same local/online flows in both languages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivacyDisclosureTest {
    private fun localeContext(language: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return base.createConfigurationContext(config)
    }

    @Test fun bothLanguagesDiscloseOptionalGoogleDriveTransfersInWelcomeAndSettings() {
        for (language in listOf("en", "ar")) {
            val context = localeContext(language)
            val welcome = context.getString(R.string.welcome_card_body)
            val settings = context.getString(R.string.settings_privacy_summary)
            assertTrue("Welcome must disclose Drive in $language", welcome.contains("Drive"))
            assertTrue("Settings must disclose Drive in $language", settings.contains("Drive"))
        }
    }

    @Test fun noWelcomeOrTaglineClaimsUniversalOfflineProcessingOrZeroCloud() {
        for (language in listOf("en", "ar")) {
            val context = localeContext(language)
            val strings = listOf(
                R.string.tagline_subtitle,
                R.string.offline_pledge_short,
                R.string.welcome_badge,
                R.string.welcome_card_title,
                R.string.welcome_card_body,
                R.string.import_subtitle
            ).map(context::getString)
            for (value in strings) {
                assertFalse("Absolute 100% claim in $language: $value", value.contains("100%"))
                assertFalse("Incorrect cloud-account promise in $language: $value",
                    value.contains("zero cloud", ignoreCase = true))
            }
        }
    }
}
