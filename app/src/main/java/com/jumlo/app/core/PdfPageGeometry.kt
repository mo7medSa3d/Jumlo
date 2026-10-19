package com.jumlo.app.core

import kotlin.math.max
import kotlin.math.min

/** Page dimensions are PDF points, not image pixels. AUTO preserves each image's aspect ratio. */
data class PdfPageSize(val widthPt: Float, val heightPt: Float)

object PdfPageGeometry {
    fun resolve(
        imageWidth: Int,
        imageHeight: Int,
        preset: String = "AUTO",
        customWidth: Int = 595,
        customHeight: Int = 842,
        orientation: String = "AUTO"
    ): PdfPageSize {
        require(imageWidth > 0 && imageHeight > 0) { "INVALID_IMAGE_SIZE" }
        require(orientation in setOf("AUTO", "PORTRAIT", "LANDSCAPE")) { "INVALID_PDF_SETTINGS" }
        if (preset == "AUTO") {
            // The longest dimension is about the length of an A4 page. Never use source pixels as points.
            val scale = 842.0 / max(imageWidth, imageHeight).toDouble()
            val width = (imageWidth * scale).toFloat()
            val height = (imageHeight * scale).toFloat()
            require(min(width, height) >= 1f) { "IMAGE_ASPECT_TOO_EXTREME" }
            return PdfPageSize(width, height)
        }
        val (portraitWidth, portraitHeight) = when (preset) {
            "A4" -> 595 to 842
            "LETTER" -> 612 to 792
            "CUSTOM" -> {
                require(customWidth in 72..14400 && customHeight in 72..14400) { "INVALID_PDF_SETTINGS" }
                min(customWidth, customHeight) to max(customWidth, customHeight)
            }
            else -> throw IllegalArgumentException("INVALID_PDF_SETTINGS")
        }
        val landscape = orientation == "LANDSCAPE" ||
            (orientation == "AUTO" && imageWidth > imageHeight)
        return if (landscape) PdfPageSize(portraitHeight.toFloat(), portraitWidth.toFloat())
            else PdfPageSize(portraitWidth.toFloat(), portraitHeight.toFloat())
    }
}

/** Immutable editing commands; the UI retains previous snapshots for undo/redo. */
data class PdfPageEdit(val order: List<Int>, val selected: Set<Int> = emptySet()) {
    companion object {
        fun start(count: Int): PdfPageEdit {
            require(count > 0) { "INVALID_PAGE_COUNT" }
            return PdfPageEdit((1..count).toList())
        }
    }

    fun move(page: Int, toPosition: Int): PdfPageEdit {
        require(page in order && toPosition in 1..order.size) { "INVALID_PAGE_INDEX" }
        val updated = order.toMutableList()
        updated.remove(page)
        updated.add(toPosition - 1, page)
        return copy(order = updated)
    }

    fun toggle(page: Int): PdfPageEdit {
        require(page in order) { "INVALID_PAGE_INDEX" }
        return copy(selected = if (page in selected) selected - page else selected + page)
    }

    fun selectRange(first: Int, last: Int): PdfPageEdit {
        require(first <= last && first in order && last in order) { "INVALID_PAGE_INDEX" }
        return copy(selected = selected + (first..last))
    }

    fun deleteSelected(): PdfPageEdit {
        val remaining = order.filterNot { it in selected }
        require(remaining.isNotEmpty()) { "AT_LEAST_ONE_PAGE_REQUIRED" }
        return PdfPageEdit(remaining)
    }
}

/** Candidate structure verification is mandatory before publishing a smaller compressed PDF. */
object PdfCompressionPolicy {
    /** Explore increasingly compact image resolution at the user's chosen JPEG quality.
     * Unlike a quality percentage, output byte reduction cannot be guaranteed in advance.
     * Digital text and vector graphics are never rasterized by these profiles. */
    fun profiles(dpi: Int, quality: Int): List<Pair<Int, Int>> {
        val cappedDpi = dpi.coerceIn(36, 600)
        val selectedQuality = quality.coerceIn(10, 95)
        val options = mutableListOf(cappedDpi to selectedQuality)
        if (selectedQuality <= 85) options += min(cappedDpi, 170) to selectedQuality
        if (selectedQuality <= 70) options += min(cappedDpi, 135) to selectedQuality
        if (selectedQuality <= 60) options += min(cappedDpi, 108) to selectedQuality
        return options.distinct()
    }

    /** Pick a safe decode subsampling factor before creating an Android ARGB Bitmap.
     * Page-sized scans regularly exceed 32 MiB uncompressed at 300 DPI.
     * Subsampled decode avoids rejecting them altogether.
     */
    fun decodeSubsampling(width: Int, height: Int, heapBytes: Long): Int {
        if (width <= 0 || height <= 0 || width > 30000 || height > 30000 || heapBytes <= 0L)
            return 0
        val budget = min(32L * 1024 * 1024, heapBytes / 10L).coerceAtLeast(1L)
        var step = 1
        while (((width.toLong() + step - 1) / step) *
                ((height.toLong() + step - 1) / step) * 4L > budget) {
            if (step >= 128) return 0
            step *= 2
        }
        return step
    }
    /** Refuse a PDFBox full-bitmap decode if the worst-case resident ARGB copy is too large. */
    fun canDecodeImage(width: Int, height: Int, heapBytes: Long): Boolean {
        if (width <= 0 || height <= 0 || width > 30000 || height > 30000 || heapBytes <= 0L) return false
        val bytes = width.toLong() * height * 4L
        return bytes <= min(32L * 1024 * 1024, heapBytes / 12L)
    }
    fun accept(originalBytes: Long, candidateBytes: Long, originalPages: Int, candidatePages: Int): Boolean =
        originalBytes > 0 && candidateBytes > 0 && candidateBytes < originalBytes &&
            originalPages > 0 && candidatePages == originalPages
    /** Prefer the smallest candidate that still reopens with the original page count. */
    fun betterCandidate(originalBytes: Long, bestBytes: Long, candidateBytes: Long,
                        originalPages: Int, candidatePages: Int): Boolean =
        accept(originalBytes, candidateBytes, originalPages, candidatePages) &&
            candidateBytes < bestBytes
}
