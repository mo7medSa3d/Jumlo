package com.jumlo.app.core

/** Validated, offline PDF watermark style; positions use normalized top-left coordinates. */
data class PdfWatermarkStyle(
    val centerX: Double = 0.5,
    val centerY: Double = 0.5,
    val fontSizePt: Int = 42,
    val opacityPercent: Int = 28,
    val rotationDegrees: Int = -35,
    val tiled: Boolean = false,
    val colorHex: String = "#64748B",
    val fontFamily: String = "sans",
    val imageScale: Double = 1.0
) {
    init {
        require(centerX.isFinite() && centerX in 0.0..1.0 &&
            centerY.isFinite() && centerY in 0.0..1.0) { "INVALID_WATERMARK_POSITION" }
        require(fontSizePt in 12..90) { "INVALID_WATERMARK_SIZE" }
        require(opacityPercent in 5..100) { "INVALID_WATERMARK_OPACITY" }
        require(rotationDegrees in -180..180) { "INVALID_WATERMARK_ROTATION" }
        require(Regex("^#[0-9A-Fa-f]{6}$").matches(colorHex)) { "INVALID_WATERMARK_COLOR" }
        OverlayStylePolicy.requireFont(fontFamily)
        require(imageScale.isFinite() && imageScale in .1..3.0) { "INVALID_WATERMARK_SIZE" }
    }
}
