package com.jumlo.app.ui.theme

/** A curated accent preset with bilingual (EN/AR) display names and a seed hex. */
data class ColorPreset(
    val key: String,
    val nameEn: String,
    val nameAr: String,
    val hex: String
)

/** 20 curated accents. CLAY is the default accent (warm terracotta, the Clay
 *  identity); the legacy named presets follow, including the classic BLUE.
 *  Names are bilingual (EN/AR). */
object ColorPresets {
    val ALL: List<ColorPreset> = listOf(
        ColorPreset("CLAY", "Clay", "طيني", "#C2410C"),
        ColorPreset("PLUM", "Plum", "برقوقي", "#7C3AED"),
        ColorPreset("OLIVE", "Olive", "زيتوني", "#4D7C0F"),
        ColorPreset("ROSE", "Rose", "وردي غامق", "#B4232F"),
        ColorPreset("BLUE", "Blue", "أزرق", "#1E88E5"),
        ColorPreset("TEAL", "Teal", "أزرق مائيّ", "#00897B"),
        ColorPreset("CORAL", "Coral", "مرجان", "#E64A3A"),
        ColorPreset("VIOLET", "Violet", "بنفسجي", "#7B4DFF"),
        ColorPreset("INDIGO", "Indigo", "نيلي", "#3949AB"),
        ColorPreset("GREEN", "Green", "أخضر", "#2E7D32"),
        ColorPreset("PINK", "Pink", "وردي", "#D81B60"),
        ColorPreset("ORANGE", "Orange", "برتقالي", "#F57C00"),
        ColorPreset("AMBER", "Amber", "ذهبي", "#F9A825"),
        ColorPreset("RED", "Red", "أحمر", "#D32F2F"),
        ColorPreset("CYAN", "Cyan", "سماويّ", "#00838F"),
        ColorPreset("MAGENTA", "Magenta", "أرجواني", "#C2185B"),
        ColorPreset("PURPLE", "Purple", "بنفسجي غامق", "#8E24AA"),
        ColorPreset("BROWN", "Brown", "بني", "#6D4C41"),
        ColorPreset("SAGE", "Sage", "أخضر زيتوني", "#6A994E"),
        ColorPreset("INKWELL", "Inkwell", "أزرق داكن", "#37474F")
    )

    /** Hex seed for a preset key, or null. */
    fun hexFor(key: String): String? = ALL.firstOrNull { it.key == key }?.hex

    /** The preset whose hex matches [hex], or null. */
    fun presetForHex(hex: String): ColorPreset? =
        ALL.firstOrNull { it.hex.equals(hex, ignoreCase = true) }
}
