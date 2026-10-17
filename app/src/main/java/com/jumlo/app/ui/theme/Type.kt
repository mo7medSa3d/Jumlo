package com.jumlo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jumlo.app.R

/**
 * Jevlo 2026 type scale.
 * - Font: IBM Plex Sans (bundled variable font, weights 400–700).
 * - Arabic uses the bundled IBM Plex Sans Arabic face.
 * - Display / titles are bold and tight to give the "poster" headline feel of modern apps.
 */
val IbmPlexSansFont = FontFamily(
    Font(R.font.ibm_plex_sans_variable, FontWeight(400)),
    Font(R.font.ibm_plex_sans_variable, FontWeight(500)),
    Font(R.font.ibm_plex_sans_variable, FontWeight(600)),
    Font(R.font.ibm_plex_sans_variable, FontWeight(700))
)

@Immutable
data class QuietPrecisionTypography(
    val display: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp
    ),
    val screenTitle: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    val body: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    val button: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    val metadata: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    val metadataStrong: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    val navigation: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    val badge: TextStyle = TextStyle(
        fontFamily = IbmPlexSansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)

val DefaultQuietTypography = QuietPrecisionTypography()

val Material3Typography = Typography(
    displaySmall = DefaultQuietTypography.display,
    headlineLarge = DefaultQuietTypography.screenTitle,
    headlineSmall = DefaultQuietTypography.screenTitle,
    titleLarge = DefaultQuietTypography.sectionTitle,
    titleMedium = DefaultQuietTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = DefaultQuietTypography.metadataStrong,
    bodyLarge = DefaultQuietTypography.body,
    bodyMedium = DefaultQuietTypography.bodyMedium,
    bodySmall = DefaultQuietTypography.metadata,
    labelLarge = DefaultQuietTypography.button,
    labelMedium = DefaultQuietTypography.navigation,
    labelSmall = DefaultQuietTypography.badge
)

val LocalQuietTypography = staticCompositionLocalOf { DefaultQuietTypography }

val IbmPlexSansArabicFont = FontFamily(Font(R.font.ibm_plex_sans_arabic_regular))
fun QuietPrecisionTypography.withFont(family:FontFamily) = copy(
    display=display.copy(fontFamily=family), screenTitle=screenTitle.copy(fontFamily=family),
    sectionTitle=sectionTitle.copy(fontFamily=family), body=body.copy(fontFamily=family),
    bodyMedium=bodyMedium.copy(fontFamily=family), button=button.copy(fontFamily=family),
    metadata=metadata.copy(fontFamily=family), metadataStrong=metadataStrong.copy(fontFamily=family),
    navigation=navigation.copy(fontFamily=family), badge=badge.copy(fontFamily=family)
)
fun Typography.withFont(family:FontFamily) = copy(
    displayLarge=displayLarge.copy(fontFamily=family), displayMedium=displayMedium.copy(fontFamily=family), displaySmall=displaySmall.copy(fontFamily=family),
    headlineLarge=headlineLarge.copy(fontFamily=family), headlineMedium=headlineMedium.copy(fontFamily=family), headlineSmall=headlineSmall.copy(fontFamily=family),
    titleLarge=titleLarge.copy(fontFamily=family), titleMedium=titleMedium.copy(fontFamily=family), titleSmall=titleSmall.copy(fontFamily=family),
    bodyLarge=bodyLarge.copy(fontFamily=family), bodyMedium=bodyMedium.copy(fontFamily=family), bodySmall=bodySmall.copy(fontFamily=family),
    labelLarge=labelLarge.copy(fontFamily=family), labelMedium=labelMedium.copy(fontFamily=family), labelSmall=labelSmall.copy(fontFamily=family)
)
