package com.jumlo.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo Studio Color Systems: four modern, high-craft studio palettes anchored
// by Cobalt — matching the Jumlo app launcher icon (#3837BC -> #8658F4 with
// luminous mint #83F5D2 highlight) on crisp cool-neutral surfaces.
//
// Each system carries dedicated light and dark schemes; every text pair is
// verified WCAG AA (>= 4.5:1) and every tool/pillar accent >= 4.5:1 on cards.
// Error, success, and warning remain semantic constants across all systems.
// Document ink and paper colors stay neutral so file content is never tinted.
// Settings row icons retain their fixed semantic identity (see Color.kt).
// ============================================================================

/** Every Material role a scheme needs, light or dark. */
data class SystemScheme(
    val background: Color,
    val surface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color
)

/**
 * One color system: a 4-stop ramp (light to deep), light and dark schemes,
 * six tool accents per mode (PDF, Images, Downloads, Files, Organize,
 * Utilities), and studio hero/splash gradient colors.
 */
data class ColorSystem(
    val key: String,
    val nameEn: String,
    val nameAr: String,
    val taglineEn: String,
    val taglineAr: String,
    val ramp: List<Color>,
    val light: SystemScheme,
    val dark: SystemScheme,
    val lightTools: List<Color>,
    val darkTools: List<Color>,
    val splashStart: Color,
    val splashMid: Color,
    val splashEnd: Color,
    val splashGlow1: Color,
    val splashGlow2: Color,
    val splashOrbit: Color,
    val splashLogoStart: Color,
    val splashLogoEnd: Color,
    val splashTag: Color
) {
    fun scheme(dark: Boolean): SystemScheme = if (dark) this.dark else light
    fun tools(dark: Boolean): List<Color> = if (dark) darkTools else lightTools
}

private val LightError = Color(0xFFB3261E)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFDE6E0)
private val LightOnErrorContainer = Color(0xFF6E150C)
private val DarkError = Color(0xFFFFB4A6)
private val DarkOnError = Color(0xFF5E0E08)
private val DarkErrorContainer = Color(0xFF7A1F16)
private val DarkOnErrorContainer = Color(0xFFFDE6E0)

object ColorSystems {
    /** Signature Jumlo palette — mirrors the Cobalt-Indigo, Violet & Mint launcher icon. */
    val COBALT = ColorSystem(
        key = "COBALT", nameEn = "Cobalt", nameAr = "كوبالت",
        taglineEn = "Signature indigo", taglineAr = "نيلي مميز",
        ramp = listOf(Color(0xFFE0E7FF), Color(0xFF83F5D2), Color(0xFF8658F4), Color(0xFF3837BC)),
        light = SystemScheme(
            background = Color(0xFFF4F6FB), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFEAEEF7), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFECEFF8), surfaceHigh = Color(0xFFE2E7F4),
            surfaceHighest = Color(0xFFD6DDEF),
            onSurface = Color(0xFF0F172A), onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFF64748B), outlineVariant = Color(0xFFD8DFEE),
            primary = Color(0xFF3837BC), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE4E7FF), onPrimaryContainer = Color(0xFF1F1E7A),
            secondary = Color(0xFF0F766E), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCCFBF1), onSecondaryContainer = Color(0xFF114E48),
            tertiary = Color(0xFF6D28D9), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF4C1D95),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF0B0E17), surface = Color(0xFF111625),
            surfaceDim = Color(0xFF0B0E17), surfaceBright = Color(0xFF28324F),
            surfaceLowest = Color(0xFF0E1320), surfaceLow = Color(0xFF151B2C),
            surfaceContainer = Color(0xFF1B2237), surfaceHigh = Color(0xFF222A42),
            surfaceHighest = Color(0xFF2B3552),
            onSurface = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFFA3B1C6),
            outline = Color(0xFF8B99B0), outlineVariant = Color(0xFF2A344D),
            primary = Color(0xFF8C8FFF), onPrimary = Color(0xFF111047),
            primaryContainer = Color(0xFF272A72), onPrimaryContainer = Color(0xFFE4E7FF),
            secondary = Color(0xFF5EEAD4), onSecondary = Color(0xFF042F2E),
            secondaryContainer = Color(0xFF134E4A), onSecondaryContainer = Color(0xFFCCFBF1),
            tertiary = Color(0xFFC4B5FD), onTertiary = Color(0xFF2E1065),
            tertiaryContainer = Color(0xFF4C1D95), onTertiaryContainer = Color(0xFFEDE9FE),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFF3837BC), Color(0xFF0F766E), Color(0xFF6D28D9),
            Color(0xFF2563EB), Color(0xFF4F46E5), Color(0xFF0E7490)
        ),
        darkTools = listOf(
            Color(0xFF8C8FFF), Color(0xFF5EEAD4), Color(0xFFC4B5FD),
            Color(0xFF7DD3FC), Color(0xFFA5B4FC), Color(0xFF67E8F9)
        ),
        splashStart = Color(0xFF1A1B5C), splashMid = Color(0xFF3837BC), splashEnd = Color(0xFF6741D9),
        splashGlow1 = Color(0xFF83F5D2), splashGlow2 = Color(0xFF8658F4), splashOrbit = Color(0xFFE0E7FF),
        splashLogoStart = Color(0xFF3837BC), splashLogoEnd = Color(0xFF8658F4), splashTag = Color(0xFF83F5D2)
    )

    val EMERALD = ColorSystem(
        key = "EMERALD", nameEn = "Emerald", nameAr = "زمردي",
        taglineEn = "Crisp forest", taglineAr = "أخضر نقي",
        ramp = listOf(Color(0xFFD1FAE5), Color(0xFF34D399), Color(0xFF10B981), Color(0xFF047857)),
        light = SystemScheme(
            background = Color(0xFFF3F7F5), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFE7EFEA), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFE8F1EC), surfaceHigh = Color(0xFFDCEAE2),
            surfaceHighest = Color(0xFFCEE0D6),
            onSurface = Color(0xFF0F1D17), onSurfaceVariant = Color(0xFF3F574C),
            outline = Color(0xFF5A7569), outlineVariant = Color(0xFFCFE0D7),
            primary = Color(0xFF047857), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF064E3B),
            secondary = Color(0xFF0F766E), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCCFBF1), onSecondaryContainer = Color(0xFF115E59),
            tertiary = Color(0xFF1D4ED8), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFDBEAFE), onTertiaryContainer = Color(0xFF1E3A8A),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF09110E), surface = Color(0xFF0F1A15),
            surfaceDim = Color(0xFF09110E), surfaceBright = Color(0xFF233C31),
            surfaceLowest = Color(0xFF0C1511), surfaceLow = Color(0xFF14211C),
            surfaceContainer = Color(0xFF1A2B24), surfaceHigh = Color(0xFF21352D),
            surfaceHighest = Color(0xFF294138),
            onSurface = Color(0xFFECFDF5), onSurfaceVariant = Color(0xFF9CB8AC),
            outline = Color(0xFF7E9C8F), outlineVariant = Color(0xFF273C33),
            primary = Color(0xFF34D399), onPrimary = Color(0xFF022C22),
            primaryContainer = Color(0xFF065F46), onPrimaryContainer = Color(0xFFD1FAE5),
            secondary = Color(0xFF5EEAD4), onSecondary = Color(0xFF042F2E),
            secondaryContainer = Color(0xFF115E59), onSecondaryContainer = Color(0xFFCCFBF1),
            tertiary = Color(0xFF93C5FD), onTertiary = Color(0xFF172554),
            tertiaryContainer = Color(0xFF1E3A8A), onTertiaryContainer = Color(0xFFDBEAFE),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFF047857), Color(0xFF0F766E), Color(0xFF1D4ED8),
            Color(0xFF057A55), Color(0xFF0E7490), Color(0xFF1E40AF)
        ),
        darkTools = listOf(
            Color(0xFF34D399), Color(0xFF5EEAD4), Color(0xFF93C5FD),
            Color(0xFF6EE7B7), Color(0xFF67E8F9), Color(0xFFA5B4FC)
        ),
        splashStart = Color(0xFF053829), splashMid = Color(0xFF047857), splashEnd = Color(0xFF0A6E56),
        splashGlow1 = Color(0xFF6EE7B7), splashGlow2 = Color(0xFF34D399), splashOrbit = Color(0xFFD1FAE5),
        splashLogoStart = Color(0xFF047857), splashLogoEnd = Color(0xFF34D399), splashTag = Color(0xFFD1FAE5)
    )

    val SAPPHIRE = ColorSystem(
        key = "SAPPHIRE", nameEn = "Sapphire", nameAr = "ياقوتي",
        taglineEn = "Oceanic azure", taglineAr = "أزرق محيطي",
        ramp = listOf(Color(0xFFDBEAFE), Color(0xFF38BDF8), Color(0xFF2563EB), Color(0xFF1D4ED8)),
        light = SystemScheme(
            background = Color(0xFFF3F6FB), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFE7EDF7), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFE8EEF8), surfaceHigh = Color(0xFFDCE5F3),
            surfaceHighest = Color(0xFFCEDBEB),
            onSurface = Color(0xFF0F172A), onSurfaceVariant = Color(0xFF45556C),
            outline = Color(0xFF61738B), outlineVariant = Color(0xFFD1DCEB),
            primary = Color(0xFF1D4ED8), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDBEAFE), onPrimaryContainer = Color(0xFF1E3A8A),
            secondary = Color(0xFF0E7490), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCFFAFE), onSecondaryContainer = Color(0xFF164E63),
            tertiary = Color(0xFF4F46E5), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE0E7FF), onTertiaryContainer = Color(0xFF312E81),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF090E19), surface = Color(0xFF0F1726),
            surfaceDim = Color(0xFF090E19), surfaceBright = Color(0xFF253759),
            surfaceLowest = Color(0xFF0C1220), surfaceLow = Color(0xFF141F32),
            surfaceContainer = Color(0xFF1B2840), surfaceHigh = Color(0xFF22324F),
            surfaceHighest = Color(0xFF2B3E5F),
            onSurface = Color(0xFFF0F6FF), onSurfaceVariant = Color(0xFF9FB0C9),
            outline = Color(0xFF8497B3), outlineVariant = Color(0xFF283854),
            primary = Color(0xFF60A5FA), onPrimary = Color(0xFF0B1F47),
            primaryContainer = Color(0xFF1E3A8A), onPrimaryContainer = Color(0xFFDBEAFE),
            secondary = Color(0xFF38BDF8), onSecondary = Color(0xFF082F49),
            secondaryContainer = Color(0xFF164E63), onSecondaryContainer = Color(0xFFCFFAFE),
            tertiary = Color(0xFFA5B4FC), onTertiary = Color(0xFF1E1B4B),
            tertiaryContainer = Color(0xFF312E81), onTertiaryContainer = Color(0xFFE0E7FF),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFF1D4ED8), Color(0xFF0E7490), Color(0xFF4F46E5),
            Color(0xFF2563EB), Color(0xFF0369A1), Color(0xFF4338CA)
        ),
        darkTools = listOf(
            Color(0xFF60A5FA), Color(0xFF38BDF8), Color(0xFFA5B4FC),
            Color(0xFF7DD3FC), Color(0xFF5EEAD4), Color(0xFF93C5FD)
        ),
        splashStart = Color(0xFF0E2252), splashMid = Color(0xFF1D4ED8), splashEnd = Color(0xFF0369A1),
        splashGlow1 = Color(0xFF38BDF8), splashGlow2 = Color(0xFF60A5FA), splashOrbit = Color(0xFFDBEAFE),
        splashLogoStart = Color(0xFF1D4ED8), splashLogoEnd = Color(0xFF38BDF8), splashTag = Color(0xFFDBEAFE)
    )

    val AMETHYST = ColorSystem(
        key = "AMETHYST", nameEn = "Amethyst", nameAr = "أرجواني",
        taglineEn = "Royal orchid", taglineAr = "بنفسجي ملكي",
        ramp = listOf(Color(0xFFF3E8FF), Color(0xFFC084FC), Color(0xFF9333EA), Color(0xFF6B21A8)),
        light = SystemScheme(
            background = Color(0xFFF7F4FB), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFEEE8F6), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFEFE9F8), surfaceHigh = Color(0xFFE5DCF3),
            surfaceHighest = Color(0xFFD9CEEC),
            onSurface = Color(0xFF181126), onSurfaceVariant = Color(0xFF514666),
            outline = Color(0xFF6F6285), outlineVariant = Color(0xFFDCD3EB),
            primary = Color(0xFF7E22CE), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF3E8FF), onPrimaryContainer = Color(0xFF4A117A),
            secondary = Color(0xFF4338CA), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE0E7FF), onSecondaryContainer = Color(0xFF2A227A),
            tertiary = Color(0xFFBE185D), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFCE7F3), onTertiaryContainer = Color(0xFF70103B),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF0F0A18), surface = Color(0xFF171024),
            surfaceDim = Color(0xFF0F0A18), surfaceBright = Color(0xFF342450),
            surfaceLowest = Color(0xFF130D1E), surfaceLow = Color(0xFF1E152E),
            surfaceContainer = Color(0xFF261B3A), surfaceHigh = Color(0xFF2F2248),
            surfaceHighest = Color(0xFF3A2A58),
            onSurface = Color(0xFFF5F0FF), onSurfaceVariant = Color(0xFFB3A5C9),
            outline = Color(0xFF9788B0), outlineVariant = Color(0xFF362850),
            primary = Color(0xFFC084FC), onPrimary = Color(0xFF2B084D),
            primaryContainer = Color(0xFF581C87), onPrimaryContainer = Color(0xFFF3E8FF),
            secondary = Color(0xFFA5B4FC), onSecondary = Color(0xFF1B1454),
            secondaryContainer = Color(0xFF312E81), onSecondaryContainer = Color(0xFFE0E7FF),
            tertiary = Color(0xFFF472B6), onTertiary = Color(0xFF4A0826),
            tertiaryContainer = Color(0xFF831843), onTertiaryContainer = Color(0xFFFCE7F3),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFF7E22CE), Color(0xFF4338CA), Color(0xFFBE185D),
            Color(0xFF6D28D9), Color(0xFF4F46E5), Color(0xFF9D174D)
        ),
        darkTools = listOf(
            Color(0xFFC084FC), Color(0xFFA5B4FC), Color(0xFFF472B6),
            Color(0xFFD8B4FE), Color(0xFFC4B5FD), Color(0xFFF9A8D4)
        ),
        splashStart = Color(0xFF2B0E5C), splashMid = Color(0xFF6B21A8), splashEnd = Color(0xFF7E22CE),
        splashGlow1 = Color(0xFFC084FC), splashGlow2 = Color(0xFFA855F7), splashOrbit = Color(0xFFF3E8FF),
        splashLogoStart = Color(0xFF6B21A8), splashLogoEnd = Color(0xFFC084FC), splashTag = Color(0xFFF3E8FF)
    )

    /** Picker order leads with the signature Cobalt studio identity. */
    val ALL: List<ColorSystem> = listOf(COBALT, EMERALD, SAPPHIRE, AMETHYST)

    /** Cobalt is the default — matches the Jumlo launcher icon. */
    val DEFAULT: ColorSystem = COBALT

    /** Maps current and legacy keys cleanly so upgrading users never lose a theme choice. */
    fun systemFor(key: String): ColorSystem = when (key) {
        "COBALT", "SUNSET" -> COBALT
        "EMERALD", "LUIGI" -> EMERALD
        "SAPPHIRE", "AURORA" -> SAPPHIRE
        "AMETHYST", "VIOLA" -> AMETHYST
        else -> DEFAULT
    }
}

/** The system JumloTheme provides to the whole tree. */
val LocalColorSystem = staticCompositionLocalOf { ColorSystems.DEFAULT }

/**
 * Pillar (tool family) colors for a system. Slots: PDF, Images, Downloads,
 * Files, Organize. Success and warning stay semantic constants.
 */
fun pillarColors(system: ColorSystem, dark: Boolean): PillarColors {
    val s = system.scheme(dark)
    val tools = system.tools(dark)
    val familyColors =
        if (!dark) when (system.key) {
            "EMERALD" -> listOf(
                tools[0], Color(0xFFD1FAE5), Color(0xFF065F46), Color(0xFF047857),
                tools[1], Color(0xFFCCFBF1), Color(0xFF115E59), Color(0xFF0F766E),
                tools[2], Color(0xFFDBEAFE), Color(0xFF1E40AF), Color(0xFF1D4ED8),
                tools[3], Color(0xFFDCFCE7), Color(0xFF166534), Color(0xFF057A55),
                tools[4], Color(0xFFCFFAFE), Color(0xFF155E75), Color(0xFF0E7490))
            "SAPPHIRE" -> listOf(
                tools[0], Color(0xFFDBEAFE), Color(0xFF1E40AF), Color(0xFF1D4ED8),
                tools[1], Color(0xFFCFFAFE), Color(0xFF155E75), Color(0xFF0E7490),
                tools[2], Color(0xFFE0E7FF), Color(0xFF3730A3), Color(0xFF4F46E5),
                tools[3], Color(0xFFE0F2FE), Color(0xFF075985), Color(0xFF0369A1),
                tools[4], Color(0xFFEDE9FE), Color(0xFF4338CA), Color(0xFF4338CA))
            "AMETHYST" -> listOf(
                tools[0], Color(0xFFF3E8FF), Color(0xFF581C87), Color(0xFF7E22CE),
                tools[1], Color(0xFFE0E7FF), Color(0xFF312E81), Color(0xFF4338CA),
                tools[2], Color(0xFFFCE7F3), Color(0xFF831843), Color(0xFFBE185D),
                tools[3], Color(0xFFEDE9FE), Color(0xFF4C1D95), Color(0xFF6D28D9),
                tools[4], Color(0xFFE0E7FF), Color(0xFF3730A3), Color(0xFF4F46E5))
            else -> listOf(
                tools[0], Color(0xFFE7E9FF), Color(0xFF282699), Color(0xFF3837BC),
                tools[1], Color(0xFFCCFBF1), Color(0xFF115E59), Color(0xFF0F766E),
                tools[2], Color(0xFFEDE9FE), Color(0xFF5B21B6), Color(0xFF6D28D9),
                tools[3], Color(0xFFDBEAFE), Color(0xFF1D4ED8), Color(0xFF2563EB),
                tools[4], Color(0xFFE0E7FF), Color(0xFF3730A3), Color(0xFF4F46E5))
        } else when (system.key) {
            "EMERALD" -> listOf(
                tools[0], Color(0xFF0B4232), Color(0xFF6EE7B7), Color(0xFF34D399),
                tools[1], Color(0xFF11403D), Color(0xFF5EEAD4), Color(0xFF5EEAD4),
                tools[2], Color(0xFF1B3566), Color(0xFF93C5FD), Color(0xFF93C5FD),
                tools[3], Color(0xFF0E442F), Color(0xFF6EE7B7), Color(0xFF6EE7B7),
                tools[4], Color(0xFF133E4F), Color(0xFF67E8F9), Color(0xFF67E8F9))
            "SAPPHIRE" -> listOf(
                tools[0], Color(0xFF1A3466), Color(0xFF93C5FD), Color(0xFF60A5FA),
                tools[1], Color(0xFF123D54), Color(0xFF7DD3FC), Color(0xFF38BDF8),
                tools[2], Color(0xFF29266B), Color(0xFFC7D2FE), Color(0xFFA5B4FC),
                tools[3], Color(0xFF153C5C), Color(0xFF7DD3FC), Color(0xFF7DD3FC),
                tools[4], Color(0xFF124242), Color(0xFF5EEAD4), Color(0xFF5EEAD4))
            "AMETHYST" -> listOf(
                tools[0], Color(0xFF43196B), Color(0xFFDFB5FF), Color(0xFFC084FC),
                tools[1], Color(0xFF282469), Color(0xFFC7D2FE), Color(0xFFA5B4FC),
                tools[2], Color(0xFF61143A), Color(0xFFF9A8D4), Color(0xFFF472B6),
                tools[3], Color(0xFF3C1D6B), Color(0xFFE9D5FF), Color(0xFFD8B4FE),
                tools[4], Color(0xFF2B246B), Color(0xFFDDD6FE), Color(0xFFC4B5FD))
            else -> listOf(
                tools[0], Color(0xFF252766), Color(0xFFB4B6FF), Color(0xFF8C8FFF),
                tools[1], Color(0xFF134240), Color(0xFF5EEAD4), Color(0xFF5EEAD4),
                tools[2], Color(0xFF3B1D73), Color(0xFFD8CCFF), Color(0xFFC4B5FD),
                tools[3], Color(0xFF1A3561), Color(0xFF7DD3FC), Color(0xFF7DD3FC),
                tools[4], Color(0xFF27296A), Color(0xFFC7D2FE), Color(0xFFA5B4FC))
        }
    val (pdfP, pdfC, pdfOn, pdfSub) = familyColors.subList(0, 4)
    val (imgP, imgC, imgOn, imgSub) = familyColors.subList(4, 8)
    val (dlP, dlC, dlOn, dlSub) = familyColors.subList(8, 12)
    val (filP, filC, filOn, filSub) = familyColors.subList(12, 16)
    val (vltP, vltC, _, _) = familyColors.subList(16, 20)

    val (successPrimary, successContainer, warningPrimary, warningContainer) = if (!dark)
        listOf(Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF92400E), Color(0xFFFEF3C7))
    else
        listOf(Color(0xFF4ADE80), Color(0xFF052E16), Color(0xFFFCD34D), Color(0xFF451A03))
    return PillarColors(
        pdfPrimary = pdfP, pdfContainer = pdfC, pdfOnContainer = pdfOn, pdfSubtitle = pdfSub,
        imagesPrimary = imgP, imagesContainer = imgC, imagesOnContainer = imgOn, imagesSubtitle = imgSub,
        downloadsPrimary = dlP, downloadsContainer = dlC, downloadsOnContainer = dlOn, downloadsSubtitle = dlSub,
        filesPrimary = filP, filesContainer = filC, filesOnContainer = filOn, filesSubtitle = filSub,
        vaultPrimary = vltP, vaultContainer = vltC,
        successPrimary = successPrimary, successContainer = successContainer,
        warningPrimary = warningPrimary, warningContainer = warningContainer,
        cardSurface = s.surfaceLow, cardBorder = s.outlineVariant,
        primaryActionBackground = s.primary, primaryActionText = s.onPrimary,
        dockSurface = if (dark) s.surfaceHigh else s.surfaceLowest, dockBorder = s.outlineVariant,
        activePillContainer = s.primaryContainer, activePillContent = s.onPrimaryContainer
    )
}
