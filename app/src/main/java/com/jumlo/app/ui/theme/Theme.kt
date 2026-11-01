package com.jumlo.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode

/** A Material scheme straight from a color system's own palette — no seeds,
 *  no generated accents, no dynamic color. surfaceVariant follows the
 *  system's high container, as the old Clay scheme did. */
private fun colorSchemeFor(s: SystemScheme, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme(
        primary = s.primary,
        onPrimary = s.onPrimary,
        primaryContainer = s.primaryContainer,
        onPrimaryContainer = s.onPrimaryContainer,
        secondary = s.secondary,
        onSecondary = s.onSecondary,
        secondaryContainer = s.secondaryContainer,
        onSecondaryContainer = s.onSecondaryContainer,
        tertiary = s.tertiary,
        onTertiary = s.onTertiary,
        tertiaryContainer = s.tertiaryContainer,
        onTertiaryContainer = s.onTertiaryContainer,
        error = s.error,
        onError = s.onError,
        errorContainer = s.errorContainer,
        onErrorContainer = s.onErrorContainer,
        background = s.background,
        onBackground = s.onSurface,
        surface = s.surface,
        onSurface = s.onSurface,
        surfaceVariant = s.surfaceHigh,
        onSurfaceVariant = s.onSurfaceVariant,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        surfaceContainerLowest = s.surfaceLowest,
        surfaceContainerLow = s.surfaceLow,
        surfaceContainer = s.surfaceContainer,
        surfaceContainerHigh = s.surfaceHigh,
        surfaceContainerHighest = s.surfaceHighest,
        surfaceDim = s.surfaceDim,
        surfaceBright = s.surfaceBright,
        surfaceTint = Color.Transparent
    ) else lightColorScheme(
        primary = s.primary,
        onPrimary = s.onPrimary,
        primaryContainer = s.primaryContainer,
        onPrimaryContainer = s.onPrimaryContainer,
        secondary = s.secondary,
        onSecondary = s.onSecondary,
        secondaryContainer = s.secondaryContainer,
        onSecondaryContainer = s.onSecondaryContainer,
        tertiary = s.tertiary,
        onTertiary = s.onTertiary,
        tertiaryContainer = s.tertiaryContainer,
        onTertiaryContainer = s.onTertiaryContainer,
        error = s.error,
        onError = s.onError,
        errorContainer = s.errorContainer,
        onErrorContainer = s.onErrorContainer,
        background = s.background,
        onBackground = s.onSurface,
        surface = s.surface,
        onSurface = s.onSurface,
        surfaceVariant = s.surfaceHigh,
        onSurfaceVariant = s.onSurfaceVariant,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        surfaceContainerLowest = s.surfaceLowest,
        surfaceContainerLow = s.surfaceLow,
        surfaceContainer = s.surfaceContainer,
        surfaceContainerHigh = s.surfaceHigh,
        surfaceContainerHighest = s.surfaceHighest,
        surfaceDim = s.surfaceDim,
        surfaceBright = s.surfaceBright,
        surfaceTint = Color.Transparent
    )
    return base
}

@Composable
fun JumloTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    language: AppLanguage = AppLanguage.ENGLISH,
    colorSystem: String = "SUNSET",
    fontScale: String = "NORMAL",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
    }
    val isAmoled = themeMode == ThemeMode.AMOLED

    val system = ColorSystems.systemFor(colorSystem)
    val scheme = system.scheme(darkTheme)
    // AMOLED keeps the system's hues but drops its backgrounds to pure black.
    val colorScheme: ColorScheme = if (isAmoled) {
        colorSchemeFor(scheme, dark = true).copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black
        )
    } else {
        colorSchemeFor(scheme, darkTheme)
    }

    val basePillars = pillarColors(system, darkTheme)
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
        LocalColorSystem provides system,
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
