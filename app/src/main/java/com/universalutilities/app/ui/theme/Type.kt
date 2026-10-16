package com.universalutilities.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Quiet Precision Typography Scale (from DESIGN.md)
 * - Display: 28sp / 36sp line-height / SemiBold (600)
 * - Screen Title: 22sp / 30sp line-height / SemiBold (600)
 * - Section Title: 18sp / 26sp line-height / SemiBold (600)
 * - Body: 16sp / 24sp line-height / Regular (400)
 * - Button: 16sp / 24sp line-height / SemiBold (600)
 * - Metadata: 14sp / 20sp line-height / Regular (400) with tabular numbers (tnum)
 * - Navigation: 13sp / 18sp line-height / Medium (500)
 */
@Immutable
data class QuietPrecisionTypography(
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp
    ),
    val screenTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val button: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val metadata: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    val metadataStrong: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum"
    ),
    val navigation: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    val badge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    )
)

val DefaultQuietTypography = QuietPrecisionTypography()

val Material3Typography = Typography(
    displaySmall = DefaultQuietTypography.display,
    headlineSmall = DefaultQuietTypography.screenTitle,
    titleLarge = DefaultQuietTypography.screenTitle,
    titleMedium = DefaultQuietTypography.sectionTitle,
    bodyLarge = DefaultQuietTypography.body,
    bodyMedium = DefaultQuietTypography.metadata,
    labelLarge = DefaultQuietTypography.button,
    labelMedium = DefaultQuietTypography.navigation,
    labelSmall = DefaultQuietTypography.badge
)

val LocalQuietTypography = staticCompositionLocalOf { DefaultQuietTypography }
