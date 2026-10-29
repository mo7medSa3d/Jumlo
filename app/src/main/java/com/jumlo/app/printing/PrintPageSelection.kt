package com.jumlo.app.printing

/**
 * Page management for the print preview: which pages print, in which order, and how
 * each page is rotated.
 *
 * The persisted shape stays a range string plus two optional collections, so payloads
 * written by earlier builds still decode. `pageOrder` and `pageRotations` are both
 * expressed against the *source* order (1-based), which keeps them stable while the
 * user reorders the visible list.
 */
object PrintPageSelection {
    /**
     * Apply per-page rotations, then the user's order.
     * An order that is not a complete permutation of the source list is ignored so a
     * stale preset can never silently drop pages.
     */
    fun arrange(base: List<PrintPage>, order: List<Int>, rotations: Map<Int, Int>): List<PrintPage> {
        val rotated = base.mapIndexed { index, page ->
            val degrees = rotations[index + 1] ?: return@mapIndexed page
            val normalized = PrintGeometry.rotation(degrees)
            if (normalized == page.rotationDegrees) page else page.copy(rotationDegrees = normalized)
        }
        if (!isPermutation(order, rotated.size)) return rotated
        return order.map { position -> rotated[position - 1] }
    }

    fun isPermutation(order: List<Int>, size: Int): Boolean =
        order.size == size && order.all { it in 1..size } && order.toSet().size == order.size

    /**
     * A stale persisted order is not the visible order: arrange() displays source
     * order when a permutation is incomplete/duplicated. Editors must use that
     * same visible fallback before applying drag, move or per-page rotation.
     */
    fun normalizedOrder(order: List<Int>, size: Int): List<Int> =
        if (isPermutation(order, size)) order else (1..size).toList()

    /**
     * Range text for 0-based positions in the arranged document.
     * Empty text means "every page", so a full document is only abbreviated when the
     * caller confirms the document really has that many pages.
     */
    fun rangeText(positions: List<Int>, count: Int): String {
        val sorted = positions.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val total = maxOf(count, sorted.size)
        if (sorted.size == total && sorted.first() == 0 && sorted.last() == total - 1) return ""
        return PrintRanges.compact(sorted).joinToString(",") { range ->
            if (range.first == range.last) "${range.first + 1}" else "${range.first + 1}-${range.last + 1}"
        }
    }

    /** 0-based positions described by range text. Invalid text yields no positions. */
    fun positions(range: String, count: Int): List<Int> =
        runCatching { PrintRanges.parse(range, count) }.getOrDefault(emptyList())

    /**
     * Move the page at [from] to [to] inside the current order.
     * Returns the new order, or the old one when the move is out of bounds.
     */
    fun move(order: List<Int>, size: Int, from: Int, to: Int): List<Int> {
        val base = normalizedOrder(order, size)
        if (from !in base.indices || to !in base.indices || from == to) return order
        val next = base.toMutableList()
        next.add(to, next.removeAt(from))
        return next
    }
}
