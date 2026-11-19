package com.jumlo.app.ui.screenshots

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.jumlo.app.JumloApplication
import com.jumlo.app.MainActivity
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.AppViewModel
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/**
 * Fast opt-in Roborazzi visual audit capturing real MainActivity screens (Home, Tools, Files,
 * Downloads, Settings) in Light, Dark, Arabic RTL, and Tablet layouts for before/after comparison.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = JumloApplication::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DesignAuditScreenshotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun phaseDir(): File {
        val phase = System.getenv("DESIGN_AUDIT_PHASE") ?: System.getProperty("design.audit.phase")
        assumeTrue("Set DESIGN_AUDIT_PHASE=baseline|after with -Proborazzi.test.record=true", !phase.isNullOrBlank())
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        val appDir = if (File(cwd, "src/main/AndroidManifest.xml").isFile) cwd else File(cwd, "app")
        return File(appDir, "build/outputs/design-audit/$phase").apply { mkdirs() }
    }

    private fun viewModel(): AppViewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    private fun captureAllTabs(variant: String, language: AppLanguage, theme: ThemeMode) {
        val outDir = File(phaseDir(), variant).apply { mkdirs() }
        compose.waitUntil(20_000) { viewModel().settings.value.isLoaded && viewModel().workspaceReady.value }
        if (compose.onAllNodesWithTag("onboarding.dismiss").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("onboarding.dismiss").performClick()
            compose.waitForIdle()
        }
        compose.runOnUiThread {
            viewModel().setLanguage(language)
            viewModel().setThemeMode(theme)
        }
        compose.waitUntil(15_000) { viewModel().settings.value.let { it.language == language && it.themeMode == theme } }
        compose.waitForIdle()

        // 1. Home
        compose.onRoot().captureRoboImage(File(outDir, "01_home.png").absolutePath)

        // 2. Downloads (from Home button)
        if (compose.onAllNodesWithTag("home.manageDownloads").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("home.manageDownloads").performClick()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(outDir, "02_downloads.png").absolutePath)
        }

        // 3. Tools tab
        if (compose.onAllNodesWithTag("nav.TOOLS").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("nav.TOOLS").performClick()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(outDir, "03_tools.png").absolutePath)
        }

        // 4. Files tab
        if (compose.onAllNodesWithTag("nav.FILES").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("nav.FILES").performClick()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(outDir, "04_files.png").absolutePath)
        }

        // 5. Settings tab
        if (compose.onAllNodesWithTag("nav.SETTINGS").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("nav.SETTINGS").performClick()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(File(outDir, "05_settings.png").absolutePath)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-mdpi")
    fun captureEnglishLightPhone() = captureAllTabs("en_light_phone", AppLanguage.ENGLISH, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-mdpi")
    fun captureEnglishDarkPhone() = captureAllTabs("en_dark_phone", AppLanguage.ENGLISH, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-notnight-mdpi")
    fun captureArabicRtlLightPhone() = captureAllTabs("ar_rtl_light_phone", AppLanguage.ARABIC, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "en-rUS-w900dp-h768dp-notnight-mdpi")
    fun captureEnglishLightTablet() = captureAllTabs("en_light_tablet", AppLanguage.ENGLISH, ThemeMode.LIGHT)
}
