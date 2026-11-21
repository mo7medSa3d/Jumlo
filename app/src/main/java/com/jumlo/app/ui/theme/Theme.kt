package com.jumlo.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
    colorSystem: String = "COBALT",
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

    val system = remember(colorSystem) { ColorSystems.systemFor(colorSystem) }
    // AMOLED keeps the system's hues but drops its backgrounds to pure black.
    // True OLED #000000 in dark mode (battery-saving) and pure crisp white/neutral in light mode.
    val colorScheme: ColorScheme = remember(system, darkTheme, isAmoled) {
        val scheme = system.scheme(darkTheme)
        val baseScheme = colorSchemeFor(scheme, darkTheme)
        val oledDark = baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF26272E),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF0F0F0F),
            surfaceContainer = Color(0xFF181818),
            surfaceContainerHigh = Color(0xFF1F2128),
            surfaceContainerHighest = Color(0xFF26272E),
            outlineVariant = Color(0xFF26272E)
        )
        if (isAmoled) oledDark
        else if (darkTheme) oledDark
        else baseScheme.copy(
            background = Color(0xFFF2F4F8),
            surface = Color(0xFFF2F4F8),
            surfaceDim = Color(0xFFE6EAF2),
            surfaceBright = Color(0xFFFFFFFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFE8ECF4),
            surfaceContainerHigh = Color(0xFFDFE4EE),
            surfaceContainerHighest = Color(0xFFD5DCE8),
            outlineVariant = Color(0xFFDFE3EC)
        )
    }

    val pillarColors: PillarColors = remember(system, darkTheme, colorScheme) {
        val basePillars = pillarColors(system, darkTheme)
        basePillars.copy(
            cardSurface = if (darkTheme) Color(0xFF0F0F0F) else Color(0xFFFFFFFF),
            cardBorder = if (darkTheme) Color(0xFF26272E) else Color(0xFFDFE3EC),
            primaryActionBackground = colorScheme.primary,
            primaryActionText = colorScheme.onPrimary,
            dockSurface = if (darkTheme) Color(0xFF121216) else Color(0xFFFFFFFF),
            dockBorder = if (darkTheme) Color(0xFF26272E) else Color(0xFFE7E4E4),
            activePillContainer = colorScheme.primaryContainer,
            activePillContent = colorScheme.onPrimaryContainer
        )
    }
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val fontFamily = if (language.isRtl) IbmPlexSansArabicFont else IbmPlexSansFont
    val quietTypography = remember(fontFamily, language.isRtl) {
        DefaultQuietTypography.withFont(fontFamily, isArabic = language.isRtl)
    }
    val materialTypography = remember(fontFamily, language.isRtl) {
        Material3Typography.withFont(fontFamily, isArabic = language.isRtl)
    }
    val quietShapes = remember { QuietPrecisionShapes() }
    val quietSpacing = remember { QuietPrecisionSpacing() }
    val quietElevation = remember { QuietPrecisionElevation() }
    val originalDensity = LocalDensity.current
    val multiplier = when (fontScale) { "SMALL" -> 0.91f; "LARGE" -> 1.15f; else -> 1f }
    val adaptedDensity = remember(originalDensity, multiplier) {
        Density(originalDensity.density, originalDensity.fontScale * multiplier)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalColorSystem provides system,
        LocalPillarColors provides pillarColors,
        LocalQuietTypography provides quietTypography,
        LocalQuietShapes provides quietShapes,
        LocalQuietSpacing provides quietSpacing,
        LocalQuietElevation provides quietElevation,
        LocalLayoutDirection provides layoutDirection,
        LocalDensity provides adaptedDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = materialTypography,
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}

/** All motion-related components use this local rather than assuming motion is enabled. */
val LocalReducedMotion = staticCompositionLocalOf { false }
