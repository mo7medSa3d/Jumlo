package com.jumlo.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// Jumlo color systems, 2026: the user picks exactly one of four systems and
// it drives the whole app — surfaces, actions, tool tiles, workspaces and
// splash. Each system carries its own light and dark scheme; every text pair
// below was verified WCAG AA (4.5) and every tool icon on its tile 3.0+ by
// scripts/test-color-systems.py. The ramps are the four reference artworks:
// Luigi greens, Aurora blue-to-rose, Viola violet dusk, Sunset amber dusk.
// Viola's printed hex labels in its artwork repeat Luigi's, so its ramp was
// measured from the artwork's own swatches (780E94, CD4FBA, FFC57B, FFE1C0).
// Error, success and warning stay semantic constants across systems: they
// mean something, they are not brand colors. Document ink and paper colors
// are deliberately untouched — chroma belongs to the UI, not file content.
// Settings row icons keep their own fixed colors (see Color.kt) by an
// earlier explicit request; they never follow the chosen system.
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
 * One color system: a 4-stop ramp (light to deep, the artwork's own
 * swatches), a light and a dark scheme, six tool accents per mode (PDF,
 * Images, Downloads, Files, Organize, Utilities — same slot order as the
 * old tool accents), and the splash artwork colors.
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
    val LUIGI = ColorSystem(
        key = "LUIGI", nameEn = "Luigi", nameAr = "لويجي",
        taglineEn = "Fresh greens", taglineAr = "أخضر منعش",
        ramp = listOf(Color(0xFFFFE3B3), Color(0xFFC2DD6C), Color(0xFF79CE67), Color(0xFF30B155)),
        light = SystemScheme(
            background = Color(0xFFFFF2DD), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFFFF0D7), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFFF9EE),
            surfaceContainer = Color(0xFFFFF6E5), surfaceHigh = Color(0xFFFFEDCE),
            surfaceHighest = Color(0xFFFFEAC6),
            onSurface = Color(0xFF165026), onSurfaceVariant = Color(0xFF406D47),
            outline = Color(0xFF6E8D6C), outlineVariant = Color(0xFFDEDCC3),
            primary = Color(0xFF24833F), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFEAC6), onPrimaryContainer = Color(0xFF21783A),
            secondary = Color(0xFF4B8040), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD7F0D1), onSecondaryContainer = Color(0xFF416F38),
            tertiary = Color(0xFF69773A), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFEDF5D3), onTertiaryContainer = Color(0xFF616E36),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF0C130E), surface = Color(0xFF111B13),
            surfaceDim = Color(0xFF0C130E), surfaceBright = Color(0xFF24402C),
            surfaceLowest = Color(0xFF0C130E), surfaceLow = Color(0xFF152116),
            surfaceContainer = Color(0xFF1A281C), surfaceHigh = Color(0xFF203323),
            surfaceHighest = Color(0xFF283F2B),
            onSurface = Color(0xFFFFF2DD), onSurfaceVariant = Color(0xFFCECAB4),
            outline = Color(0xFF79826E), outlineVariant = Color(0xFF324733),
            primary = Color(0xFF79CE67), onPrimary = Color(0xFF071B0D),
            primaryContainer = Color(0xFF1F7337), onPrimaryContainer = Color(0xFFFFEED1),
            secondary = Color(0xFFC2DD6C), onSecondary = Color(0xFF071B0D),
            secondaryContainer = Color(0xFF2C4823), onSecondaryContainer = Color(0xFFD1E691),
            tertiary = Color(0xFFFFE3B3), onTertiary = Color(0xFF071B0D),
            tertiaryContainer = Color(0xFF2F432B), onTertiaryContainer = Color(0xFFFFE9C2),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFF258A42), Color(0xFF4D8442), Color(0xFF71803F),
            Color(0xFF258741), Color(0xFF4E8543), Color(0xFF70803E)
        ),
        darkTools = listOf(
            Color(0xFF79CE67), Color(0xFFC2DD6C), Color(0xFFFFE3B3),
            Color(0xFF9ADA8D), Color(0xFFD1E691), Color(0xFFB5E4AB)
        ),
        splashStart = Color(0xFF0D3218), splashMid = Color(0xFF1A612F), splashEnd = Color(0xFF248540),
        splashGlow1 = Color(0xFFC2DD6C), splashGlow2 = Color(0xFF79CE67), splashOrbit = Color(0xFFFFE3B3),
        splashLogoStart = Color(0xFF30B155), splashLogoEnd = Color(0xFFC2DD6C), splashTag = Color(0xFFFFE3B3)
    )
    val AURORA = ColorSystem(
        key = "AURORA", nameEn = "Aurora", nameAr = "أورورا",
        taglineEn = "Blue to rose", taglineAr = "من الأزرق إلى الوردي",
        ramp = listOf(Color(0xFFFBCFE8), Color(0xFFA78BFA), Color(0xFF60A5FA), Color(0xFF3B82F6)),
        light = SystemScheme(
            background = Color(0xFFFDE9F5), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFFDE5F3), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFEF4FA),
            surfaceContainer = Color(0xFFFEEFF7), surfaceHigh = Color(0xFFFCE0F0),
            surfaceHighest = Color(0xFFFCDBEE),
            onSurface = Color(0xFF1B3A6F), onSurfaceVariant = Color(0xFF435A87),
            outline = Color(0xFF717DA2), outlineVariant = Color(0xFFDDD1E2),
            primary = Color(0xFF3370D4), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFCDBEE), onPrimaryContainer = Color(0xFF2C60B6),
            secondary = Color(0xFF4577B4), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCFE4FE), onSecondaryContainer = Color(0xFF3A6396),
            tertiary = Color(0xFF7C67B9), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE5DCFE), onTertiaryContainer = Color(0xFF68569B),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF0B101D), surface = Color(0xFF101827),
            surfaceDim = Color(0xFF0B101D), surfaceBright = Color(0xFF24395C),
            surfaceLowest = Color(0xFF0B101D), surfaceLow = Color(0xFF141E30),
            surfaceContainer = Color(0xFF18243A), surfaceHigh = Color(0xFF1E2E48),
            surfaceHighest = Color(0xFF263A5A),
            onSurface = Color(0xFFFDE9F5), onSurfaceVariant = Color(0xFFCDC0CF),
            outline = Color(0xFF79798C), outlineVariant = Color(0xFF333D55),
            primary = Color(0xFF60A5FA), onPrimary = Color(0xFF091425),
            primaryContainer = Color(0xFF2654A0), onPrimaryContainer = Color(0xFFFDE2F1),
            secondary = Color(0xFFA78BFA), onSecondary = Color(0xFF091425),
            secondaryContainer = Color(0xFF293059), onSecondaryContainer = Color(0xFFBDA8FB),
            tertiary = Color(0xFFFBCFE8), onTertiary = Color(0xFF091425),
            tertiaryContainer = Color(0xFF313751), onTertiaryContainer = Color(0xFFFCD9ED),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFF3472D8), Color(0xFF4577B4), Color(0xFF7C67B9),
            Color(0xFF326ED1), Color(0xFF4576B2), Color(0xFF7D68BB)
        ),
        darkTools = listOf(
            Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFFBCFE8),
            Color(0xFF88BCFB), Color(0xFFBDA8FB), Color(0xFFA8CEFC)
        ),
        splashStart = Color(0xFF112445), splashMid = Color(0xFF204887), splashEnd = Color(0xFF2C62B8),
        splashGlow1 = Color(0xFFA78BFA), splashGlow2 = Color(0xFF60A5FA), splashOrbit = Color(0xFFFBCFE8),
        splashLogoStart = Color(0xFF3B82F6), splashLogoEnd = Color(0xFFA78BFA), splashTag = Color(0xFFFBCFE8)
    )
    val VIOLA = ColorSystem(
        key = "VIOLA", nameEn = "Viola", nameAr = "فيولا",
        taglineEn = "Violet dusk", taglineAr = "بنفسجي الغروب",
        ramp = listOf(Color(0xFFFFE1C0), Color(0xFFFFC57B), Color(0xFFCD4FBA), Color(0xFF780E94)),
        light = SystemScheme(
            background = Color(0xFFFFF2E3), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFFFEFDD), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFFF8F1),
            surfaceContainer = Color(0xFFFFF5EA), surfaceHigh = Color(0xFFFFECD7),
            surfaceHighest = Color(0xFFFFE8D0),
            onSurface = Color(0xFF360643), onSurfaceVariant = Color(0xFF5A315F),
            outline = Color(0xFF82607F), outlineVariant = Color(0xFFE3D1CC),
            primary = Color(0xFF780E94), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFE8D0), onPrimaryContainer = Color(0xFF780E94),
            secondary = Color(0xFFB847A7), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF0CAEA), onSecondaryContainer = Color(0xFF903782),
            tertiary = Color(0xFF8F6E45), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFEED7), onTertiaryContainer = Color(0xFF856640),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF120812), surface = Color(0xFF1A0C1E),
            surfaceDim = Color(0xFF120812), surfaceBright = Color(0xFF33163E),
            surfaceLowest = Color(0xFF120812), surfaceLow = Color(0xFF201024),
            surfaceContainer = Color(0xFF271330), surfaceHigh = Color(0xFF30173C),
            surfaceHighest = Color(0xFF3B1C4A),
            onSurface = Color(0xFFFFF2E3), onSurfaceVariant = Color(0xFFD1C2BC),
            outline = Color(0xFF816E78), outlineVariant = Color(0xFF3F2940),
            primary = Color(0xFFCD4FBA), onPrimary = Color(0xFF120216),
            primaryContainer = Color(0xFF4E0960), onPrimaryContainer = Color(0xFFFFEDD9),
            secondary = Color(0xFFFFC57B), onSecondary = Color(0xFF120216),
            secondaryContainer = Color(0xFF442631), onSecondaryContainer = Color(0xFFFFD49C),
            tertiary = Color(0xFFFFE1C0), onTertiary = Color(0xFF120216),
            tertiaryContainer = Color(0xFF3D2438), onTertiaryContainer = Color(0xFFFFE7CD),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFF780E94), Color(0xFFBD49AB), Color(0xFF947247),
            Color(0xFF660C7E), Color(0xFFAE439E), Color(0xFF937247)
        ),
        darkTools = listOf(
            Color(0xFFCD4FBA), Color(0xFFFFC57B), Color(0xFFFFE1C0),
            Color(0xFFDA7BCB), Color(0xFFFFD49C), Color(0xFFE49ED9)
        ),
        splashStart = Color(0xFF220429), splashMid = Color(0xFF420851), splashEnd = Color(0xFF5A0A6F),
        splashGlow1 = Color(0xFFFFC57B), splashGlow2 = Color(0xFFCD4FBA), splashOrbit = Color(0xFFFFE1C0),
        splashLogoStart = Color(0xFF780E94), splashLogoEnd = Color(0xFFFFC57B), splashTag = Color(0xFFFFE1C0)
    )
    val SUNSET = ColorSystem(
        key = "SUNSET", nameEn = "Sunset", nameAr = "الغروب",
        taglineEn = "Amber dusk", taglineAr = "كهرمان الغروب",
        ramp = listOf(Color(0xFFFFE3B3), Color(0xFFFBB931), Color(0xFFF88F22), Color(0xFFEA6113)),
        light = SystemScheme(
            background = Color(0xFFFFF2DD), surface = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFFFF0D7), surfaceBright = Color(0xFFFFFFFF),
            surfaceLowest = Color(0xFFFFFFFF), surfaceLow = Color(0xFFFFF9EE),
            surfaceContainer = Color(0xFFFFF6E5), surfaceHigh = Color(0xFFFFEDCE),
            surfaceHighest = Color(0xFFFFEAC6),
            onSurface = Color(0xFF692C09), onSurfaceVariant = Color(0xFF844F2F),
            outline = Color(0xFFA27759), outlineVariant = Color(0xFFEAD7BF),
            primary = Color(0xFFC55110), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFEAC6), onPrimaryContainer = Color(0xFFB24A0E),
            secondary = Color(0xFFA96117), onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFDDDBD), onSecondaryContainer = Color(0xFF905314),
            tertiary = Color(0xFF926B1C), onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFEEAC1), onTertiaryContainer = Color(0xFF836019),
            error = LightError, onError = LightOnError,
            errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer
        ),
        dark = SystemScheme(
            background = Color(0xFF17100A), surface = Color(0xFF201409),
            surfaceDim = Color(0xFF17100A), surfaceBright = Color(0xFF4A2C12),
            surfaceLowest = Color(0xFF17100A), surfaceLow = Color(0xFF26180C),
            surfaceContainer = Color(0xFF2E1D0E), surfaceHigh = Color(0xFF3A2410),
            surfaceHighest = Color(0xFF472C12),
            onSurface = Color(0xFFFFF2DD), onSurfaceVariant = Color(0xFFD6C6B1),
            outline = Color(0xFF8F7966), outlineVariant = Color(0xFF543927),
            primary = Color(0xFFF88F22), onPrimary = Color(0xFF230F03),
            primaryContainer = Color(0xFF983F0C), onPrimaryContainer = Color(0xFFFFEED1),
            secondary = Color(0xFFFBB931), onSecondary = Color(0xFF230F03),
            secondaryContainer = Color(0xFF57330C), onSecondaryContainer = Color(0xFFFCCA64),
            tertiary = Color(0xFFFFE3B3), onTertiary = Color(0xFF230F03),
            tertiaryContainer = Color(0xFF52341E), onTertiaryContainer = Color(0xFFFFE9C2),
            error = DarkError, onError = DarkOnError,
            errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer
        ),
        lightTools = listOf(
            Color(0xFFC95310), Color(0xFFAE6418), Color(0xFF9C731E),
            Color(0xFFC75210), Color(0xFFB16618), Color(0xFF9A711E)
        ),
        darkTools = listOf(
            Color(0xFFF88F22), Color(0xFFFBB931), Color(0xFFFFE3B3),
            Color(0xFFFAAB59), Color(0xFFFCCA64), Color(0xFFFBC185)
        ),
        splashStart = Color(0xFF421B05), splashMid = Color(0xFF81350A), splashEnd = Color(0xFFB0490E),
        splashGlow1 = Color(0xFFFBB931), splashGlow2 = Color(0xFFF88F22), splashOrbit = Color(0xFFFFE3B3),
        splashLogoStart = Color(0xFFEA6113), splashLogoEnd = Color(0xFFFBB931), splashTag = Color(0xFFFFE3B3)
    )

    /** Picker order follows the reference artworks. */
    val ALL: List<ColorSystem> = listOf(LUIGI, AURORA, VIOLA, SUNSET)

    /** Sunset is the default: closest to the previous warm Clay identity. */
    val DEFAULT: ColorSystem = SUNSET

    /** Unknown or legacy keys (including every old accent preset) fall back. */
    fun systemFor(key: String): ColorSystem =
        ALL.firstOrNull { it.key == key } ?: DEFAULT
}

/** The system JumloTheme provides to the whole tree. */
val LocalColorSystem = staticCompositionLocalOf { ColorSystems.DEFAULT }

/**
 * Pillar (tool family) colors for a system. Slots: PDF, Images, Downloads,
 * Files, Organize. Success and warning stay semantic constants. The card,
 * dock and pill fields are overridden from the scheme by JumloTheme, as
 * before; the values here only back the unprovided default.
 */
fun pillarColors(system: ColorSystem, dark: Boolean): PillarColors {
    val s = system.scheme(dark)
    val tools = system.tools(dark)
    // Per-system family primaries, containers, on-containers and subtitles
    // (all pairs verified 4.5+ by scripts/test-color-systems.py). Each family
    // contributes exactly four entries, in that order.
    val familyColors =
        if (!dark) when (system.key) {
            "LUIGI" -> listOf(
                tools[0], Color(0xFFE5F1E8), Color(0xFF21793A), Color(0xFF248540),
                tools[1], Color(0xFFEAF0E8), Color(0xFF46773B), Color(0xFF4C8141),
                tools[2], Color(0xFFEEF0E8), Color(0xFF637137), Color(0xFF6C7B3C),
                tools[3], Color(0xFFE5F1E8), Color(0xFF217A3B), Color(0xFF248540),
                tools[4], Color(0xFFEAF0E8), Color(0xFF45753B), Color(0xFF4B8040))
            "AURORA" -> listOf(
                tools[0], Color(0xFFE7EEFA), Color(0xFF2F67C3), Color(0xFF3370D4),
                tools[1], Color(0xFFE9EFF6), Color(0xFF3E6BA2), Color(0xFF4577B4),
                tools[2], Color(0xFFEFEDF7), Color(0xFF725FAA), Color(0xFF7C67B9),
                tools[3], Color(0xFFE6EEF9), Color(0xFF2F68C5), Color(0xFF326ED1),
                tools[4], Color(0xFFE9EFF6), Color(0xFF3F6CA4), Color(0xFF4576B2))
            "VIOLA" -> listOf(
                tools[0], Color(0xFFEFE2F2), Color(0xFF780E94), Color(0xFF780E94),
                tools[1], Color(0xFFF7E9F5), Color(0xFFA64097), Color(0xFFB947A8),
                tools[2], Color(0xFFF2EEE9), Color(0xFF82653F), Color(0xFF8E6E44),
                tools[3], Color(0xFFEDE2EF), Color(0xFF660C7E), Color(0xFF660C7E),
                tools[4], Color(0xFFF5E8F3), Color(0xFFA74098), Color(0xFFAE439E))
            else -> listOf(
                tools[0], Color(0xFFF9EAE2), Color(0xFFB1490E), Color(0xFFC15010),
                tools[1], Color(0xFFF5ECE3), Color(0xFF9C5A15), Color(0xFFAA6217),
                tools[2], Color(0xFFF3EEE4), Color(0xFF89651B), Color(0xFF956E1D),
                tools[3], Color(0xFFF8EAE2), Color(0xFFAF490E), Color(0xFFC35110),
                tools[4], Color(0xFFF6EDE3), Color(0xFF9C5A15), Color(0xFFAA6217))
        } else when (system.key) {
            "LUIGI" -> listOf(
                tools[0], Color(0xFF275B2C), Color(0xFF8ED67F), Color(0xFF79CE67),
                tools[1], Color(0xFF375E2E), Color(0xFFC2DD6C), Color(0xFFC2DD6C),
                tools[2], Color(0xFF445F3D), Color(0xFFFFE3B3), Color(0xFFFFE3B3),
                tools[3], Color(0xFF2E5D35), Color(0xFF9ADA8D), Color(0xFF9ADA8D),
                tools[4], Color(0xFF3A6036), Color(0xFFD1E691), Color(0xFFD1E691))
            "AURORA" -> listOf(
                tools[0], Color(0xFF244676), Color(0xFF83B9FB), Color(0xFF60A5FA),
                tools[1], Color(0xFF344076), Color(0xFFBCA7FB), Color(0xFFA78BFA),
                tools[2], Color(0xFF464F72), Color(0xFFFBCFE8), Color(0xFFFBCFE8),
                tools[3], Color(0xFF2D4B76), Color(0xFF8FC0FB), Color(0xFF88BCFB),
                tools[4], Color(0xFF394676), Color(0xFFC2AFFC), Color(0xFFBDA8FB))
            "VIOLA" -> listOf(
                tools[0], Color(0xFF4C154F), Color(0xFFD772C8), Color(0xFFCD4FBA),
                tools[1], Color(0xFF572F41), Color(0xFFFFC57B), Color(0xFFFFC57B),
                tools[2], Color(0xFF573550), Color(0xFFFFE1C0), Color(0xFFFFE1C0),
                tools[3], Color(0xFF4F1F53), Color(0xFFDA7BCB), Color(0xFFDA7BCB),
                tools[4], Color(0xFF573248), Color(0xFFFFD49C), Color(0xFFFFD49C))
            else -> listOf(
                tools[0], Color(0xFF73380C), Color(0xFFFAA853), Color(0xFFF88F22),
                tools[1], Color(0xFF734210), Color(0xFFFBB931), Color(0xFFFBB931),
                tools[2], Color(0xFF744B2C), Color(0xFFFFE3B3), Color(0xFFFFE3B3),
                tools[3], Color(0xFF733F19), Color(0xFFFAAE60), Color(0xFFFAAB59),
                tools[4], Color(0xFF74451B), Color(0xFFFCCA64), Color(0xFFFCCA64))
        }
    // List destructuring in Kotlin supports only component1 through component5.
    // Split each four-slot family before destructuring to preserve the exact
    // PDF, Images, Downloads, Files and Vault color ordering.
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
        dockSurface = s.surfaceHigh, dockBorder = s.outlineVariant,
        activePillContainer = s.primaryContainer, activePillContent = s.onPrimaryContainer
    )
}
