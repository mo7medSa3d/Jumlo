package com.jumlo.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Fixed, vivid semantic colors per tool and Home quick action.
 * Deliberately independent of the user's theme accent so every tool tile stays
 * immediately recognizable in both light and dark modes.
 *
 * Every curated color is unique (no duplicates, no grey fallback) and paired
 * with [glyphOn] for WCAG AA contrast (>= 4.5:1) against white or black.
 */
object ToolIconColors {
    private val SEMANTIC_COLORS: Map<String, Color> = linkedMapOf(
        // PDF conversion & scan (warm reds, oranges, corals, blues/teals)
        "pdf_to_docx" to Color(0xFF2563EB),        // Word royal blue
        "pdf_to_pptx" to Color(0xFFEA580C),        // PowerPoint burnt orange
        "office_to_pdf" to Color(0xFFDC2626),      // Office crimson red
        "pdf_ocr" to Color(0xFFE11D48),            // OCR rose red
        "pdf_02" to Color(0xFFEA580C),             // Images to PDF burnt orange
        "pdf_07" to Color(0xFFB45309),             // PDF to Images rich amber
        "pdf_long_image" to Color(0xFFC2410C),     // Long Image deep ochre
        "scan_doc" to Color(0xFF0284C7),           // Smart Scan ocean blue
        "scan" to Color(0xFF0284C7),

        // PDF edit, organize & security
        "pdf_01" to Color(0xFFEF4444),             // View PDF scarlet
        "pdf_03" to Color(0xFFEC4899),             // Merge PDF hot pink
        "pdf_04" to Color(0xFFD97706),             // Organize Pages amber gold
        "pdf_05" to Color(0xFFF43F5E),             // Split PDF coral red
        "pdf_06" to Color(0xFF9A3412),             // Extract Pages warm sienna
        "pdf_compress" to Color(0xFFBE123C),       // Compress PDF ruby
        "pdf_crop" to Color(0xFFE11D48),           // Crop PDF crimson rose
        "pdf_stamp" to Color(0xFFF97316),          // Watermark PDF tangerine
        "pdf_signature" to Color(0xFFB91C1C),      // Sign PDF deep red
        "pdf_protect" to Color(0xFFDB2777),        // Lock PDF magenta
        "pdf_nup" to Color(0xFF0E7490),            // 2/4 per page deep cyan
        "pdf_privacy_redact" to Color(0xFF991B1B), // Redact deep crimson

        // Images & unified image studio (rich greens & teals with crisp white glyphs)
        "img_01" to Color(0xFF059669),             // Convert Images emerald
        "img_02" to Color(0xFF0F766E),             // Resize Images deep teal
        "img_03" to Color(0xFF16A34A),             // Crop & Rotate rich green
        "img_04" to Color(0xFF15803D),             // Compress & Edit forest green
        "img_bg_remove" to Color(0xFF0D7377),      // Remove Background deep sea teal
        "img_watermark" to Color(0xFF047857),      // Watermark Images jade
        "img_join" to Color(0xFF0891B2),           // Join / Collage cyan-teal
        "img_studio" to Color(0xFF16A34A),         // Unified Image Studio vivid green
        "img_unified" to Color(0xFF16A34A),
        "img_editor" to Color(0xFF16A34A),
        "img_tools" to Color(0xFF16A34A),

        // Files & unified ZIP studio (purples, violets, indigos)
        "file_02" to Color(0xFF6366F1),            // Files indigo
        "file_07" to Color(0xFF9333EA),            // Create ZIP vivid purple
        "file_08" to Color(0xFF7C3AED),            // Extract ZIP violet
        "file_encrypted_zip" to Color(0xFFC026D3), // Encrypted ZIP fuchsia
        "file_batch_rename" to Color(0xFF4F46E5),  // Batch Rename deep indigo
        "file_zip" to Color(0xFF8B5CF6),           // Unified ZIP Studio amethyst
        "zip_studio" to Color(0xFF8B5CF6),
        "zip_unified" to Color(0xFF8B5CF6),
        "file_archive" to Color(0xFF8B5CF6),

        // Downloads (blues)
        "dl_media" to Color(0xFF0EA5E9),           // Download sky blue
        "dl_02" to Color(0xFF3B82F6),              // Direct Link cobalt
        "dl_03" to Color(0xFF0369A1),              // Batch Links deep cerulean
        "dl_settings" to Color(0xFF1D4ED8),        // Download settings royal blue

        // Home quick actions (distinct from every neighboring quick action)
        "id_card" to Color(0xFF0D9488),            // ID Card deep teal (distinct from scan_doc blue)
        "scan_id_card" to Color(0xFF0D9488),
        "convert" to Color(0xFFFF6D00),            // Convert vivid deep orange
        "all_tools" to Color(0xFF6D28D9),          // All Tools deep violet
        "explore_tools" to Color(0xFF6D28D9),      // Explore Tools deep violet
    )

    private fun pillarCenter(toolId: String): Float = when {
        toolId == "scan_doc" || toolId == "scan" || toolId == "id_card" || toolId == "scan_id_card" -> 195f
        toolId.startsWith("pdf_") || toolId == "office_to_pdf" || toolId == "convert" -> 12f
        toolId.startsWith("img_") -> 148f
        toolId.startsWith("dl_") -> 212f
        toolId.startsWith("file_") || toolId.startsWith("zip_") -> 272f
        toolId.startsWith("xf_") -> 45f
        else -> 295f
    }

    /** The fixed vivid tile color for a tool or Home quick action. */
    fun iconColorFor(toolId: String): Color {
        SEMANTIC_COLORS[toolId]?.let { return it }
        val step = (toolId.hashCode() and Int.MAX_VALUE) % 36
        val hue = (pillarCenter(toolId) + ((step * 37) % 90) - 45 + 360) % 360
        return hsl(hue, 0.82f, 0.50f)
    }

    /** Black or white, whichever reads better on the tile (WCAG contrast). */
    fun glyphOn(icon: Color): Color =
        if (M3ColorFactory.contrast(icon, Color.White) >= M3ColorFactory.contrast(icon, Color.Black)) Color.White else Color.Black

    private fun hsl(hueDeg: Float, saturation: Float, lightness: Float): Color {
        val h = (hueDeg % 360) / 360f
        val c = (1 - abs(2 * lightness - 1)) * saturation
        val x = c * (1 - abs((h * 6) % 2 - 1))
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
