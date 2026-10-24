package com.jumlo.app.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

/** Immutable editing commands; the UI retains previous snapshots for undo/redo.
 *  Each operation returns a new instance so edits are non-destructive. */
data class PdfPageEdit(
    val order: List<Int>,
    val selected: Set<Int> = emptySet(),
    val rotations: Map<Int, Int> = emptyMap()
) {
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

    fun selectAll(): PdfPageEdit = copy(selected = order.toSet())

    fun selectNone(): PdfPageEdit = copy(selected = emptySet())

    fun invertSelection(): PdfPageEdit = copy(selected = order.toSet() - selected)

    fun deleteSelected(): PdfPageEdit {
        val remaining = order.filterNot { it in selected }
        require(remaining.isNotEmpty()) { "AT_LEAST_ONE_PAGE_REQUIRED" }
        return PdfPageEdit(remaining, rotations.filterKeys { it in remaining })
    }

    fun duplicate(page: Int): PdfPageEdit {
        require(page in order) { "INVALID_PAGE_INDEX" }
        val idx = order.indexOf(page)
        return copy(order = order.toMutableList().apply { add(idx + 1, page) })
    }

    fun rotate(page: Int, degrees: Int): PdfPageEdit {
        require(page in order) { "INVALID_PAGE_INDEX" }
        require(degrees in setOf(90, 180, 270)) { "INVALID_ROTATION" }
        val next = ((rotations[page] ?: 0) + degrees) % 360
        return copy(rotations = rotations + (page to next))
    }

    fun rotateSelected(degrees: Int): PdfPageEdit {
        require(degrees in setOf(90, 180, 270)) { "INVALID_ROTATION" }
        if (selected.isEmpty()) return this
        val next = rotations.toMutableMap()
        selected.forEach { p -> next[p] = ((rotations[p] ?: 0) + degrees) % 360 }
        return copy(rotations = next)
    }

    fun extractSelected(): PdfPageEdit {
        val pages = order.filter { it in selected }
        require(pages.isNotEmpty()) { "NO_PAGES_SELECTED" }
        return PdfPageEdit(pages, emptySet(), rotations.filterKeys { it in selected })
    }

    fun splitEvery(n: Int): List<PdfPageEdit> {
        require(n > 0) { "INVALID_SPLIT_SIZE" }
        return order.chunked(n).map { PdfPageEdit(it, rotations.filterKeys { p in it }) }
    }
}

/** Candidate structure verification is mandatory before publishing a smaller compressed PDF. */
object PdfCompressionPolicy {
    /**
     * Named presets offered in the UI. A quality percentage cannot promise a byte
     * result, so each preset is only a starting point for the candidate ladder.
     *
     * The range in [measuredSavingPercent] is what the preset reached on the
     * measured corpus: the 300 dpi scans and the real phone photos in the
     * repository. The low end of the range is the phone photo, which is already
     * at a low resolution and resists the higher steps of the ladder; the high
     * end is the 300 dpi scan. Measured by `scripts/test-pdf-compression.sh
     * --measure`, on the host, without a device.
     */
    enum class Preset(val dpi: Int, val quality: Int, val measuredSavingPercent: IntRange) {
        /** The lightest compression: the best picture. */
        BEST_QUALITY(240, 90, 0..40),
        /** The middle ground. */
        BALANCED(180, 75, 11..81),
        /** A small file that is still readable. */
        SMALL_FILE(135, 58, 9..92),
        /** The smallest file: the strongest compression. */
        STRONG(108, 45, 28..96);

        companion object {
            /** The old names stay readable so an existing preset keeps working. */
            private val legacy = mapOf("EMAIL_1MB" to STRONG, "BALANCED" to BALANCED,
                "HIGH_QUALITY" to BEST_QUALITY)
            fun fromId(id: String?): Preset? = entries.firstOrNull { it.name == id } ?: legacy[id]
        }
    }

    /**
     * Candidate ladder for a byte budget, best-looking first. Output size cannot be
     * predicted from a quality setting, so the caller runs the ladder and stops at
     * the first candidate that actually meets the budget. The chosen quality is the
     * ceiling: resolution is reduced first, then quality.
     */
    fun targetProfiles(quality: Int): List<Pair<Int, Int>> {
        val ceiling = quality.coerceIn(10, 95)
        return listOf(
            300 to ceiling,
            220 to ceiling,
            160 to ceiling,
            120 to minOf(ceiling, 70),
            96 to minOf(ceiling, 60),
            72 to minOf(ceiling, 45)
        ).distinct()
    }

    /** True when the candidate meets the requested budget. A budget of 0 means "no budget". */
    fun meetsTarget(bytes: Long, targetBytes: Long): Boolean = targetBytes <= 0L || bytes <= targetBytes

    /**
     * The slider positions, from "smallest file" to "best quality".
     * Each step is a (dpi, quality) pair, so the slider and the presets agree.
     */
    fun sliderSteps(): List<Pair<Int, Int>> = listOf(
        72 to 35, 96 to 50, 120 to 60, 150 to 70, 200 to 78, 300 to 88, 400 to 94
    )

    /** The (dpi, quality) pair for a slider position in [0, 1]. */
    fun sliderStep(position: Float): Pair<Int, Int> {
        val steps = sliderSteps()
        if (steps.isEmpty()) return 150 to 70
        val index = (position.coerceIn(0f, 1f) * (steps.size - 1)).roundToInt()
        return steps[index]
    }

    /** The slider position that matches a (dpi, quality) pair. */
    fun sliderPosition(dpi: Int, quality: Int): Float {
        val steps = sliderSteps()
        if (steps.isEmpty()) return 0.5f
        val index = steps.indices.minByOrNull {
            val (d, q) = steps[it]
            kotlin.math.abs(d - dpi) + kotlin.math.abs(q - quality)
        } ?: 0
        return index.toFloat() / (steps.size - 1)
    }

    /**
     * A rough size estimate for the slider.
     *
     * Only the embedded images are re-encoded, so the image share of the file is
     * scaled by the resolution and the quality factor, and everything else is left
     * alone. 300 DPI is the reference for "as captured". It is an *estimate*: the real
     * size is only known after the run, and the tool always reports the real one.
     */
    /** The share of the file the estimate expects to save, as a percentage. */
    fun estimatedSavingPercent(originalBytes: Long, estimatedBytes: Long): Int {
        if (originalBytes <= 0L || estimatedBytes >= originalBytes) return 0
        return (((originalBytes - estimatedBytes).toDouble() / originalBytes.toDouble()) * 100.0)
            .toInt().coerceIn(0, 99)
    }

    fun estimateBytes(originalBytes: Long, imageBytes: Long, dpi: Int, quality: Int): Long {
        if (originalBytes <= 0L) return 0L
        val images = imageBytes.coerceIn(0L, originalBytes)
        val rest = originalBytes - images
        val resolutionFactor = (dpi.toDouble() / 300.0).coerceIn(0.05, 1.0)
        val qualityFactor = (quality.coerceIn(10, 95) / 95.0).coerceAtLeast(0.15)
        val estimatedImages = (images * resolutionFactor * resolutionFactor * qualityFactor).toLong().coerceAtLeast(0L)
        return (rest + estimatedImages).coerceIn(0L, originalBytes)
    }

    /**
     * The rungs of the compression ladder, from the chosen resolution down to the
     * smallest one that is worth trying.
     *
     * The floor used to be the chosen resolution itself: `min(cappedDpi, 170)`
     * and its siblings are never below the choice, so every preset of 170 dpi or
     * less collapsed onto a single rung and a page that resisted it was left
     * alone. The floor is now **below** the chosen resolution, and it follows
     * the quality the user asked for: a low quality means "the smallest file",
     * so the ladder is allowed to go further down.
     */
    fun profiles(dpi: Int, quality: Int): List<Pair<Int, Int>> {
        val cappedDpi = dpi.coerceIn(36, 600)
        val selectedQuality = quality.coerceIn(10, 95)
        val floor = ladderFloor(selectedQuality)
        val rungs = mutableListOf(cappedDpi to selectedQuality)
        for (step in LADDER_STEPS) {
            if (step >= cappedDpi || step < floor) continue
            // The quality the user chose is kept on every rung: the ladder only
            // lowers the resolution, never the quality the user asked for.
            rungs += step to selectedQuality
        }
        return rungs.distinct()
    }

    /** The lowest resolution the ladder may reach for the chosen quality. */
    fun ladderFloor(quality: Int): Int = when {
        quality <= 55 -> 60
        quality <= 70 -> 72
        quality <= 85 -> 96
        else -> 150
    }

    /** The resolutions the ladder walks down through. */
    private val LADDER_STEPS = listOf(300, 220, 170, 135, 108, 96, 72, 60)

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
