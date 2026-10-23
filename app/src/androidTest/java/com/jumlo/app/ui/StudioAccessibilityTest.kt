package com.jumlo.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jumlo.app.core.PdfAnnotationKind
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.theme.ToolPalette
import com.jumlo.app.ui.tools.ToolsCatalogue
import com.jumlo.app.ui.tools.shortToolLabel
import com.jumlo.app.ui.workspace.AnnotationToolbar
import com.jumlo.app.ui.workspace.InteractiveIntSlider
import com.jumlo.app.ui.workspace.InteractiveToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** These are control-level assertions, not a claim of full TalkBack or complete-route acceptance. */
class StudioAccessibilityTest {
    @get:Rule val compose=createComposeRule()

    @Test fun catalogueAt200PercentEnglishKeepsFullLabelAndLargeTarget() = verifyLargeCatalogue(AppLanguage.ENGLISH)

    @Test fun catalogueAt200PercentArabicKeepsFullLabelAndLargeTarget() = verifyLargeCatalogue(AppLanguage.ARABIC)

    private fun verifyLargeCatalogue(language:AppLanguage) {
        val tool=CapabilityRegistry.tools.single {it.id=="pdf_to_pptx"}
        compose.setContent {LargeType(language) {
            Column(Modifier.width(320.dp)) {ToolsCatalogue(listOf(tool),language,emptyList(),"",{},{})}
        }}
        compose.onNodeWithTag("tool.${tool.id}").assertIsDisplayed().assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(shortToolLabel(tool,language),useUnmergedTree=true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {assertFalse("Tool label overflow at 200%: ${language.code}",it.hasVisualOverflow)}
    }

    @Test fun annotationTargetsStayAtLeast48DpWithLargeArabicText() {
        compose.setContent {LargeType(AppLanguage.ARABIC) {
            Column(Modifier.width(320.dp)) {AnnotationToolbar(WorkspaceDraft.forTool("pdf_annotate",emptyList()),{})}
        }}
        PdfAnnotationKind.entries.forEach {kind ->
            val tag="annotation.kind.${kind.id}"
            compose.onNodeWithTag("annotation.tools").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        listOf("#FFEB3B","#111111","#1746A2","#B42332","#145D4C").forEach {color ->
            val tag="annotation.color.$color"
            compose.onNodeWithTag("annotation.colors").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test fun slidersAndTogglesExposeAccessibleActionsAnd48DpTargets() {
        var opacity by mutableIntStateOf(40)
        var enabled by mutableStateOf(false)
        compose.setContent {LargeType(AppLanguage.ENGLISH) {
            Column(Modifier.width(320.dp)) {
                InteractiveIntSlider("Opacity",opacity,1,100,40) {opacity=it}
                InteractiveToggle("Allow printing",enabled) {enabled=it}
            }
        }}
        compose.onNodeWithContentDescription("Opacity").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.SetProgress) {it(65f)}
        compose.onNodeWithText("Allow printing").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {assertEquals(65,opacity);assertTrue(enabled)}
    }

    @Test fun semanticTextAndToolIconsMeetContrastAcrossAllFixedAccents() {
        val schemes=linkedMapOf<Pair<ThemeMode,String>,ColorScheme>()
        compose.setContent {
            listOf(ThemeMode.LIGHT,ThemeMode.DARK).forEach {mode ->
                listOf("BLUE","TEAL","CORAL","VIOLET").forEach {accent ->
                    key(mode,accent) {JumloTheme(mode,AppLanguage.ENGLISH,accent) {
                        val colors=MaterialTheme.colorScheme
                        SideEffect {schemes[mode to accent]=colors}
                    }}
                }
            }
        }
        compose.runOnIdle {
            assertEquals(8,schemes.size)
            schemes.forEach {(configuration,colors) ->
                val textPairs=listOf(
                    colors.onSurface to colors.background,
                    colors.onSurface to colors.surfaceContainerLow,
                    colors.onSurfaceVariant to colors.surfaceContainerHighest,
                    colors.onPrimary to colors.primary,
                    colors.onPrimaryContainer to colors.primaryContainer,
                    colors.onErrorContainer to colors.errorContainer,
                    colors.error to colors.background
                )
                textPairs.forEach {(foreground,background) ->
                    assertTrue("Text contrast for $configuration: ${contrast(foreground,background)}",contrast(foreground,background)>=4.5f)
                }
                val dark=configuration.first==ThemeMode.DARK
                if(dark) assertEquals(Color.Black,colors.background)
                CapabilityRegistry.tools.forEach {tool ->
                    val tint=ToolPalette.accentFor(tool.id,dark)
                    val container=ToolPalette.containerFor(tool.id,colors.surfaceContainerLow,dark)
                    assertTrue("Icon contrast for ${tool.id} $configuration",contrast(tint,container)>=3f)
                }
            }
        }
    }

    private fun contrast(a:Color,b:Color):Float=(max(a.luminance(),b.luminance())+.05f)/(min(a.luminance(),b.luminance())+.05f)

    @Composable private fun LargeType(language:AppLanguage,content:@Composable ()->Unit) {
        val context=LocalContext.current
        val baseDensity=LocalDensity.current
        val configuration=remember(language) {Configuration(context.resources.configuration).apply {setLocale(Locale.forLanguageTag(language.code));fontScale=2f}}
        val localized=remember(language,context) {context.createConfigurationContext(configuration)}
        CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides configuration,
            LocalDensity provides Density(baseDensity.density,2f)) {JumloTheme(ThemeMode.DARK,language,content=content)}
    }
}
