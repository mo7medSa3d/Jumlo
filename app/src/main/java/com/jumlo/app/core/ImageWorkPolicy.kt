package com.jumlo.app.core

/** All image allocations use the caller's actual budget, including resident bitmaps. */
object ImageWorkPolicy {
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
            return maxOf(1, Math.round(sourceWidth * scale).toInt()) to maxOf(1, Math.round(sourceHeight * scale).toInt())
        }
        if (longestEdge != null) {
            require(longestEdge > 0) { "INVALID_DIMENSIONS" }
            val scale = longestEdge.toDouble() / maxOf(sourceWidth, sourceHeight)
            return maxOf(1, Math.round(sourceWidth * scale).toInt()) to maxOf(1, Math.round(sourceHeight * scale).toInt())
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
        sizes.forEach { ImageWorkPolicy.bytes(it.first, it.second) }
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
                "VSTRIP" -> y += h - trim + gap
                "HSTRIP" -> x += w + gap
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
        ImageWorkPolicy.bytes(width.toInt(), height.toInt())
        return ImageJoinPlan(width.toInt(), height.toInt(), placements)
    }
}
