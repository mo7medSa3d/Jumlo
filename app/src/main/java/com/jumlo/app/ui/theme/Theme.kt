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
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceTint = Color.Transparent
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
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceTint = Color.Transparent
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
        ThemeMode.AMOLED -> true
    }
    val isAmoled = themeMode == ThemeMode.AMOLED

    val context = LocalContext.current
    val useDynamicColor = accentColor == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseColors: ColorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> QuietDarkColorScheme
        else -> QuietLightColorScheme
    }
    val colorScheme: ColorScheme = if (useDynamicColor) {
        // Dynamic color changes accents; OLED and readable neutral surfaces remain stable.
        val neutral = if (darkTheme) QuietDarkColorScheme else QuietLightColorScheme
        baseColors.copy(background = neutral.background, onBackground = neutral.onBackground,
            surface = neutral.surface, onSurface = neutral.onSurface,
            surfaceVariant = neutral.surfaceVariant, onSurfaceVariant = neutral.onSurfaceVariant,
            surfaceDim = neutral.surfaceDim, surfaceBright = neutral.surfaceBright,
            surfaceContainerLowest = neutral.surfaceContainerLowest,
            surfaceContainerLow = neutral.surfaceContainerLow,
            surfaceContainer = neutral.surfaceContainer,
            surfaceContainerHigh = neutral.surfaceContainerHigh,
            surfaceContainerHighest = neutral.surfaceContainerHighest,
            outline = neutral.outline, outlineVariant = neutral.outlineVariant,
            surfaceTint = Color.Transparent)
    } else {
        // Seed-generated M3 accents from one color; the neutral tonal surface
        // ladder stays stable (from baseColors) while primary/secondary/tertiary
        // and error are derived from the seed with a WCAG AA contrast guard.
        val seeded = M3ColorFactory.scheme(M3ColorFactory.seedFor(accentColor), dark = darkTheme, amoled = isAmoled)
        baseColors.copy(
            primary = seeded.primary,
            onPrimary = seeded.onPrimary,
            primaryContainer = seeded.primaryContainer,
            onPrimaryContainer = seeded.onPrimaryContainer,
            secondary = seeded.secondary,
            onSecondary = seeded.onSecondary,
            secondaryContainer = seeded.secondaryContainer,
            onSecondaryContainer = seeded.onSecondaryContainer,
            tertiary = seeded.tertiary,
            onTertiary = seeded.onTertiary,
            tertiaryContainer = seeded.tertiaryContainer,
            onTertiaryContainer = seeded.onTertiaryContainer,
            error = seeded.error,
            onError = seeded.onError,
            errorContainer = seeded.errorContainer,
            onErrorContainer = seeded.onErrorContainer,
            surfaceTint = Color.Transparent
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
