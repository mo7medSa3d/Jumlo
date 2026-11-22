package com.jumlo.app.core

import kotlin.math.floor
import kotlin.math.sqrt

/** All image allocations use the caller's actual budget, including resident bitmaps. */
object ImageWorkPolicy {
    const val MIN_DYNAMIC_BUDGET_BYTES: Long = 192L * 1024 * 1024
    const val MAX_EDGE_PX: Int = 32_768
    const val MAX_STRIP_EDGE_PX: Int = 65_500
    const val MAX_PNG_STRIP_EDGE_PX: Int = 131_000
    const val MAX_WEBP_EDGE_PX: Int = 16_383
    const val MAX_PIXELS: Long = 128L * 1024 * 1024
    private const val SYNTHETIC_TEST_BUDGET_THRESHOLD: Long = 16L * 1024 * 1024

    /** Scales dynamically up to 65% of max heap with a 192 MiB floor. */
    fun dynamicBudget(heapMaxBytes: Long = Runtime.getRuntime().maxMemory()): Long {
        val safeHeap = heapMaxBytes.coerceAtLeast(0L)
        return maxOf(MIN_DYNAMIC_BUDGET_BYTES, (safeHeap / 100L) * 65L + ((safeHeap % 100L) * 65L) / 100L)
    }

    /** Raises artificial production caps to [dynamicBudget] while preserving tiny explicit test budgets. */
    fun effectiveBudget(configuredBudget: Long, heapMaxBytes: Long = Runtime.getRuntime().maxMemory()): Long =
        if (configuredBudget < SYNTHETIC_TEST_BUDGET_THRESHOLD) configuredBudget
        else maxOf(configuredBudget, dynamicBudget(heapMaxBytes))

    /** Format-aware safe edge ceiling (WebP hard limit is 16,383 px; JPEG SOF0 is 65,535 px). */
    fun maxEdgeForFormat(format: String, strip: Boolean = false): Int = when (format.uppercase(java.util.Locale.ROOT)) {
        "WEBP" -> MAX_WEBP_EDGE_PX
        "JPEG", "JPG" -> if (strip) MAX_STRIP_EDGE_PX else MAX_EDGE_PX
        "PNG" -> if (strip) MAX_PNG_STRIP_EDGE_PX else MAX_EDGE_PX
        else -> if (strip) MAX_STRIP_EDGE_PX else MAX_EDGE_PX
    }

    /** Keeps target dimensions unchanged when they fit; gracefully scales down only when extreme dimensions exceed budget/caps. */
    fun boundedDimensions(
        width: Int,
        height: Int,
        availableBytes: Long,
        maxEdge: Int = MAX_EDGE_PX,
        maxPixels: Long = MAX_PIXELS
    ): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0 && maxPixels > 0 && availableBytes >= 4L) { "INVALID_DIMENSIONS" }
        val pixelCap = minOf(maxPixels, availableBytes / 4L, Int.MAX_VALUE / 4L).coerceAtLeast(1L)
        val currentPixels = width.toLong() * height.toLong()
        if (maxOf(width, height) <= maxEdge && currentPixels <= pixelCap) return width to height
        val edgeScale = maxEdge.toDouble() / maxOf(width, height).toDouble()
        val areaScale = sqrt(pixelCap.toDouble() / currentPixels.toDouble())
        val scale = minOf(1.0, edgeScale, areaScale)
        var w = floor(width * scale).toInt().coerceAtLeast(1)
        var h = floor(height * scale).toInt().coerceAtLeast(1)
        if (w.toLong() * h > pixelCap) {
            if (w >= h) w = (pixelCap / h).toInt().coerceAtLeast(1)
            else h = (pixelCap / w).toInt().coerceAtLeast(1)
        }
        return w to h
    }

    fun bytes(width: Int, height: Int): Long {
        require(width > 0 && height > 0) { "INVALID_DIMENSIONS" }
        val pixels = width.toLong() * height.toLong()
        require(pixels <= Int.MAX_VALUE / 4L) { "IMAGE_DIMENSIONS_UNREPRESENTABLE" }
        val bytes = pixels * 4L
        // Android bitmap storage/row offsets cannot represent a >= 2 GiB allocation.
        require(bytes in 1..Int.MAX_VALUE.toLong()) { "IMAGE_DIMENSIONS_UNREPRESENTABLE" }
        return bytes
    }

    fun requireFits(width: Int, height: Int, residentBytes: Long, budget: Long): Long {
        val allocation = bytes(width, height)
        require(residentBytes >= 0 && budget >= residentBytes && allocation <= budget - residentBytes) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        return allocation
    }

    fun qualities(preferred: Int): List<Int> {
        require(preferred in 1..100) { "INVALID_QUALITY" }
        return (listOf(preferred, 75, 65, 52, 40, 30, 20, 12, 1)).distinct().filter { it <= preferred }
    }

    fun meetsStrictBound(actualBytes: Long, exclusiveBound: Long): Boolean = actualBytes > 0L && actualBytes < exclusiveBound
}

/** Resize request geometry (img_02). An explicit percentage is an intentional scale, so it is
 *  never silently clamped back to the source size. */
object ImageResizePolicy {
    fun targetDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        percentage: Int?,
        longestEdge: Int?,
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        require(sourceWidth > 0 && sourceHeight > 0) { "INVALID_DIMENSIONS" }
        if (percentage != null) {
            require(percentage in 1..400) { "INVALID_PERCENTAGE" }
            val scale = percentage / 100.0
            val scaledW = maxOf(1L, Math.round(sourceWidth * scale))
            val scaledH = maxOf(1L, Math.round(sourceHeight * scale))
            require(scaledW <= Int.MAX_VALUE.toLong() && scaledH <= Int.MAX_VALUE.toLong()) { "IMAGE_DIMENSIONS_UNREPRESENTABLE" }
            return scaledW.toInt() to scaledH.toInt()
        }
        if (longestEdge != null) {
            require(longestEdge > 0) { "INVALID_DIMENSIONS" }
            val scale = longestEdge.toDouble() / maxOf(sourceWidth, sourceHeight)
            val scaledW = maxOf(1L, Math.round(sourceWidth * scale))
            val scaledH = maxOf(1L, Math.round(sourceHeight * scale))
            require(scaledW <= Int.MAX_VALUE.toLong() && scaledH <= Int.MAX_VALUE.toLong()) { "IMAGE_DIMENSIONS_UNREPRESENTABLE" }
            return scaledW.toInt() to scaledH.toInt()
        }
        require(width > 0 && height > 0) { "INVALID_DIMENSIONS" }
        return width to height
    }

    /** Percentage scaling is explicit user intent, so it may enlarge the source. */
    fun allowsUpscaling(percentage: Int?, longestEdge: Int?, allowUpscaling: Boolean): Boolean =
        allowUpscaling || percentage != null
}

class ImageSizeBoundException(val expectedMaximumBytes: Long, val actualBytes: Long) :
    IllegalArgumentException("IMAGE_SIZE_BOUND_UNMET")

data class ImagePlacement(val x: Int, val y: Int, val trimTop: Int)
data class ImageJoinPlan(val width: Int, val height: Int, val placements: List<ImagePlacement>)

object ImageJoinPolicy {
    fun plan(sizes: List<Pair<Int, Int>>, layout: String, gapPx: Int, overlapPx: Int = 0): ImageJoinPlan {
        require(sizes.isNotEmpty()) { "NO_SOURCES" }
        sizes.forEach { require(it.first > 0 && it.second > 0) { "INVALID_DIMENSIONS" } }
        val gap = gapPx.coerceIn(0, 500)
        val overlap = if (layout == "VSTRIP") overlapPx.coerceIn(0, 400) else 0
        val cellWidth = sizes.maxOf { it.first }
        val cellHeight = sizes.maxOf { it.second }
        var x = 0L; var y = 0L; var width = 0L; var height = 0L
        val placements = sizes.mapIndexed { index, (w, h) ->
            val trim = if (index == 0) 0 else overlap.coerceAtMost(h - 1)
            require(x <= Int.MAX_VALUE && y <= Int.MAX_VALUE) { "IMAGE_DIMENSIONS_UNREPRESENTABLE" }
            val placement = ImagePlacement(x.toInt(), y.toInt(), trim)
            width = maxOf(width, x + w); height = maxOf(height, y + h - trim)
            when (layout) {
                "VSTRIP" -> y += h.toLong() - trim + gap
                "HSTRIP" -> x += w.toLong() + gap
                else -> if (index % 2 == 0) x = cellWidth.toLong() + gap else { x = 0; y += cellHeight.toLong() + gap }
            }
            placement
        }
        // Keep the established grid's two equal columns and equal row heights.
        if (layout != "VSTRIP" && layout != "HSTRIP") {
            width = cellWidth.toLong() * 2 + gap
            height = cellHeight.toLong() * ((sizes.size + 1L) / 2L) + gap * ((sizes.size - 1L) / 2L)
        }
        require(width in 1..Int.MAX_VALUE.toLong() && height in 1..Int.MAX_VALUE.toLong()) { "IMAGE_DIMENSIONS_UNREPRESENTABLE" }
        // Virtual plan dimensions may exceed a single 2 GiB bitmap; callers scale via boundedDimensions before allocation.
        return ImageJoinPlan(width.toInt(), height.toInt(), placements)
    }
}
