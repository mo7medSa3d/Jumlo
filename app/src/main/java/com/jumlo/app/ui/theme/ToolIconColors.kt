package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tool icon colors, fixed by design — deliberately NOT the chosen color
 * system. Every tool tile used to inherit its pillar accent from the
 * system, so in a warm system (Sunset) every icon read as the same orange.
 * These colors are vivid, one per tool, and identical in light and dark
 * mode: the tile itself is the color, so it needs no theme to read.
 *
 * Hue plan: each family has a center hue (PDF red-orange, scanner teal,
 * images green, files violet, downloads blue, organize amber, other
 * magenta) and each tool steps 37 degrees inside a 90-degree window
 * around it. 37 and 90 are coprime, so all 47 catalogue tools land on
 * distinct hues. Saturation 0.85 / lightness 0.55 keeps every tile vivid;
 * glyphOn picks black or white by WCAG contrast (worst pair 4.82).
 * Unknown ids fall back to a hash step so no tool ever renders colorless.
 */
object ToolIconColors {
    private val ORDERED = listOf(
        // PDF workspace, catalogue order (primary then merged).
        "pdf_to_docx", "pdf_to_pptx", "office_to_pdf", "pdf_ocr", "pdf_02",
        "pdf_03", "pdf_05", "pdf_04", "pdf_compress", "pdf_07", "pdf_signature",
        "pdf_clean", "pdf_06", "pdf_crop", "pdf_image", "pdf_forms", "pdf_annotate",
        "pdf_stamp", "pdf_metadata", "pdf_protect", "pdf_compare", "pdf_redact", "pdf_01",
        // Scanner.
        "scan_doc",
        // Images workspace.
        "img_03", "img_02", "img_04", "img_01", "img_join", "img_watermark",
        "img_adjust", "img_target", "img_stitch", "img_exif",
        // Files workspace.
        "file_07", "file_duplicates", "file_batch_rename", "file_08",
        "file_encrypted_zip", "file_drive", "sha256",
        // Downloads workspace.
        "dl_media", "dl_02", "dl_03",
        // Hidden / search-only specialists.
        "img_compare", "file_trash", "file_02"
    )

    private val INDEX = ORDERED.withIndex().associate { it.value to it.index }

    private fun pillarCenter(toolId: String): Float = when {
        toolId == "scan_doc" -> 185f
        toolId.startsWith("pdf_") || toolId == "office_to_pdf" -> 12f
        toolId.startsWith("img_") -> 150f
        toolId.startsWith("dl_") -> 220f
        toolId.startsWith("file_") || toolId == "sha256" -> 275f
        toolId.startsWith("xf_") -> 45f
        else -> 320f
    }

    /** The fixed tile color for a tool. Never follows the color system. */
    fun iconColorFor(toolId: String): Color {
        val index = INDEX[toolId] ?: ((toolId.hashCode() and Int.MAX_VALUE) % ORDERED.size)
        val hue = (pillarCenter(toolId) + ((index * 37) % 90) - 45 + 360) % 360
        return hsl(hue, 0.85f, 0.55f)
    }

    /** Black or white, whichever reads better on the tile (WCAG contrast). */
    fun glyphOn(icon: Color): Color {
        val lum = luminance(icon)
        val onWhite = 1.05f / (lum + 0.05f)
        val onBlack = (lum + 0.05f) / 0.05f
        return if (onWhite >= onBlack) Color.White else Color.Black
    }

    /** Linear mix of two colors, for tile borders that echo the icon. */
    fun mix(a: Color, b: Color, amount: Float): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue = a.blue + (b.blue - a.blue) * t,
            alpha = 1f
        )
    }

    private fun luminance(color: Color): Float {
        fun linear(channel: Float): Float =
            if (channel <= 0.03928f) channel / 12.92f
            else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * linear(color.red) + 0.7152f * linear(color.green) +
            0.0722f * linear(color.blue)
    }

    private fun hsl(hueDeg: Float, saturation: Float, lightness: Float): Color {
        val h = (hueDeg % 360) / 360f
        val c = (1 - Math.abs(2 * lightness - 1)) * saturation
        val x = c * (1 - Math.abs((h * 6) % 2 - 1))
        val m = lightness - c / 2
        val (r, g, b) = when ((h * 6).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(red = r + m, green = g + m, blue = b + m, alpha = 1f)
    }
}
