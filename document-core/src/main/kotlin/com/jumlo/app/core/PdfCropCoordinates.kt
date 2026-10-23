package com.jumlo.app.core

/** PDF points use a bottom-left origin; Jumlo crop controls use a top-left origin. */
data class PdfCropRect(val x: Float, val y: Float, val width: Float, val height: Float)

object PdfCropCoordinates {
    fun fromTopLeft(
        mediaX: Float, mediaY: Float, mediaWidth: Float, mediaHeight: Float,
        left: Double, top: Double, right: Double, bottom: Double
    ): PdfCropRect {
        require(listOf(mediaX, mediaY, mediaWidth, mediaHeight).all(Float::isFinite) &&
            mediaWidth > 0f && mediaHeight > 0f) { "INVALID_PDF_PAGE_BOX" }
        Box(left, top, right, bottom).normalized()
        return PdfCropRect(
            (mediaX + left * mediaWidth).toFloat(),
            (mediaY + (1.0 - bottom) * mediaHeight).toFloat(),
            ((right - left) * mediaWidth).toFloat(),
            ((bottom - top) * mediaHeight).toFloat()
        )
    }
}
