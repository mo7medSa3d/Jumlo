package com.jumlo.app.core.office

import kotlin.math.floor
import kotlin.math.sqrt

/** Bounded raster dimensions for viewing, OCR and Office export; independent of Android bitmap APIs. */
object DocumentRenderPolicy {
    fun dimensions(width: Int, height: Int, maxEdge: Int, maxBitmapBytes: Long): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0 && maxBitmapBytes >= 4) { "INVALID_PDF_PAGE_GEOMETRY" }
        val target = minOf(maxEdge.toDouble() / maxOf(width, height), 4.0)
        val maxPixels = minOf(maxBitmapBytes / 4, Int.MAX_VALUE / 4L)
        val factor = minOf(target, sqrt(maxPixels.toDouble() / width.toDouble() / height.toDouble()))
        var w = floor(width * factor).toInt().coerceAtLeast(1)
        var h = floor(height * factor).toInt().coerceAtLeast(1)
        // Clamping a subpixel short edge to one pixel can otherwise exceed a tiny budget.
        if (w.toLong() * h > maxPixels) {
            if (w >= h) w = (maxPixels / h).toInt().coerceAtLeast(1)
            else h = (maxPixels / w).toInt().coerceAtLeast(1)
        }
        require(w.toLong() * h <= maxPixels) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        return w to h
    }
}
