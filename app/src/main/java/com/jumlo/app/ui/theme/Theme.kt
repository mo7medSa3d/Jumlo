package com.jumlo.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode

private val QuietLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

private val QuietDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

@Composable
fun JumloTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    language: AppLanguage = AppLanguage.ENGLISH,
    accentColor: String = "BLUE",
    fontScale: String = "NORMAL",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val useDynamicColor = accentColor == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseColors: ColorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> QuietDarkColorScheme
        else -> QuietLightColorScheme
    }
    val colorScheme: ColorScheme = if (useDynamicColor) {
        baseColors
    } else {
        // Deliberate curated accent pairs preserve contrast for light and dark surfaces.
        val selectedAccent = when (accentColor) {
            "BLUE" -> if (darkTheme) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
            "TEAL" -> if (darkTheme) Color(0xFF5EEAD4) else Color(0xFF0F766E)
            "CORAL" -> if (darkTheme) Color(0xFFFDBA74) else Color(0xFFC2410C)
            "VIOLET" -> if (darkTheme) Color(0xFFC4B5FD) else Color(0xFF6D28D9)
            else -> if (darkTheme) DarkPrimary else LightPrimary
        }
        val selectedContainer = when (accentColor) {
            "BLUE" -> if (darkTheme) Color(0xFF172554) else Color(0xFFDBEAFE)
            "TEAL" -> if (darkTheme) Color(0xFF042F2E) else Color(0xFFCCFBF1)
            "CORAL" -> if (darkTheme) Color(0xFF431407) else Color(0xFFFFEDD5)
            "VIOLET" -> if (darkTheme) Color(0xFF2E1065) else Color(0xFFEDE9FE)
            else -> if (darkTheme) DarkPrimaryContainer else LightPrimaryContainer
        }
        baseColors.copy(
            primary = selectedAccent,
            onPrimary = if (darkTheme) Color(0xFF111318) else Color.White,
            primaryContainer = selectedContainer,
            onPrimaryContainer = if (darkTheme) Color(0xFFF8F8FD) else Color(0xFF182027)
        )
    }

    val basePillars = if (darkTheme) DarkPillarColors else LightPillarColors
    val pillarColors: PillarColors = basePillars.copy(
        cardSurface = colorScheme.surfaceContainerLow,
        cardBorder = colorScheme.outlineVariant,
        primaryActionBackground = colorScheme.primary,
        primaryActionText = colorScheme.onPrimary,
        dockSurface = colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        dockBorder = colorScheme.outlineVariant.copy(alpha = 0.55f),
        activePillContainer = colorScheme.primaryContainer,
        activePillContent = colorScheme.onPrimaryContainer
    )
    val layoutDirection = if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

    val fontFamily = if (language.isRtl) IbmPlexSansArabicFont else IbmPlexSansFont
    val originalDensity = LocalDensity.current
    val multiplier = when (fontScale) { "SMALL" -> 0.91f; "LARGE" -> 1.15f; else -> 1f }
    val adaptedDensity = Density(originalDensity.density, originalDensity.fontScale * multiplier)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalPillarColors provides pillarColors,
        LocalQuietTypography provides DefaultQuietTypography.withFont(fontFamily, isArabic = language.isRtl),
        LocalQuietShapes provides QuietPrecisionShapes(),
        LocalQuietSpacing provides QuietPrecisionSpacing(),
        LocalQuietElevation provides QuietPrecisionElevation(),
        LocalLayoutDirection provides layoutDirection,
        LocalDensity provides adaptedDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Material3Typography.withFont(fontFamily, isArabic = language.isRtl),
            shapes = Material3Shapes,
            content = content
        )
    }
}

object QuietTheme {
    val elevation: QuietPrecisionElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalQuietElevation.current

    val pillars: PillarColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPillarColors.current

    val typography: QuietPrecisionTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalQuietTypography.current

    val shapes: QuietPrecisionShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalQuietShapes.current

    val spacing: QuietPrecisionSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalQuietSpacing.current
}

/** All motion-related components use this local rather than assuming motion is enabled. */
val LocalReducedMotion = staticCompositionLocalOf { false }
