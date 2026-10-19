package com.jumlo.app.core

import kotlin.math.min

/** Canonicalizes user-selected PDF/image inputs without changing the user's stored selection. */
object PdfOverlaySourcePolicy {
    fun ordered(sources: List<Pair<String, String?>>): List<String> {
        require(sources.size == 2 && sources.map { it.first }.distinct().size == 2) {
            "PDF_AND_IMAGE_REQUIRED"
        }
        val pdf = sources.singleOrNull { it.second == "application/pdf" }?.first
        val image = sources.singleOrNull { it.second?.startsWith("image/") == true }?.first
        require(pdf != null && image != null) { "PDF_AND_IMAGE_REQUIRED" }
        return listOf(pdf, image)
    }
}

/** Aspect-fit for signatures and photographic overlays in PDF points.
 * Both the real PDF output and its UI preview must preserve original proportions.
 */
data class PdfImagePlacement(val x: Float, val y: Float, val width: Float, val height: Float)

object PdfImagePlacementPolicy {
    fun fit(
        slotX: Float, slotY: Float, slotWidth: Float, slotHeight: Float,
        imageWidth: Int, imageHeight: Int
    ): PdfImagePlacement {
        require(listOf(slotX, slotY, slotWidth, slotHeight).all { it.isFinite() }
            && slotWidth > 0f && slotHeight > 0f && imageWidth > 0 && imageHeight > 0) {
            "INVALID_IMAGE_PLACEMENT"
        }
        val scale = min(slotWidth / imageWidth, slotHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        return PdfImagePlacement(
            slotX + (slotWidth - width) / 2f,
            slotY + (slotHeight - height) / 2f,
            width, height
        )
    }
}
