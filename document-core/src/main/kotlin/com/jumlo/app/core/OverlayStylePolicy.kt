package com.jumlo.app.core

/** Shared bounds and placement math for the preview and both watermark exporters. */
object OverlayStylePolicy {
    fun requireFont(font: String) { require(font in setOf("sans", "serif", "mono")) { "INVALID_WATERMARK_FONT" } }
    fun requireColor(color: String) { require(Regex("^#[0-9A-Fa-f]{6}$").matches(color)) { "INVALID_WATERMARK_COLOR" } }
    fun rgb(color: String): FloatArray {
        requireColor(color)
        val value = color.substring(1).toInt(16)
        return floatArrayOf((value shr 16 and 255) / 255f, (value shr 8 and 255) / 255f, (value and 255) / 255f)
    }
    fun centers(x: Double, y: Double, tiled: Boolean): List<Pair<Double, Double>> {
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0) { "INVALID_WATERMARK_POSITION" }
        return if (tiled) listOf(.18, .5, .82).flatMap { cy -> listOf(.18, .5, .82).map { cx -> cx to cy } } else listOf(x to y)
    }
    /** Width starts at 30% of the canvas; aspect is preserved, height capped at 90%. */
    fun size(canvasWidth: Double, canvasHeight: Double, contentWidth: Double, contentHeight: Double, scale: Double): Pair<Double, Double> {
        require(listOf(canvasWidth, canvasHeight, contentWidth, contentHeight, scale).all { it.isFinite() && it > 0 }) { "INVALID_WATERMARK_SIZE" }
        require(scale in .1..3.0) { "INVALID_WATERMARK_SIZE" }
        val width = canvasWidth * .3 * scale
        val height = width * contentHeight / contentWidth
        val fit = minOf(1.0, canvasHeight * .9 / height)
        return width * fit to height * fit
    }
}
