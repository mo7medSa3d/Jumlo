package com.jumlo.app.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPageSelectionTest {
    private val base = listOf(PrintPage(1), PrintPage(2), PrintPage(3))

    @Test fun emptyOrderKeepsTheSourceOrder() {
        assertEquals(base, PrintPageSelection.arrange(base, emptyList(), emptyMap()))
    }

    @Test fun rotationsAreAppliedAgainstTheSourceOrder() {
        val arranged = PrintPageSelection.arrange(base, emptyList(), mapOf(2 to 90))
        assertEquals(0, arranged[0].rotationDegrees)
        assertEquals(90, arranged[1].rotationDegrees)
        assertEquals(0, arranged[2].rotationDegrees)
    }

    @Test fun orderIsAppliedAfterRotations() {
        val arranged = PrintPageSelection.arrange(base, listOf(3, 1, 2), mapOf(3 to 180))
        assertEquals(listOf(3, 1, 2), arranged.map { it.sourcePage })
        assertEquals(180, arranged[0].rotationDegrees)
    }

    @Test fun anIncompleteOrDuplicatedOrderIsIgnoredSoNoPageIsLost() {
        assertEquals(base, PrintPageSelection.arrange(base, listOf(1, 2), emptyMap()))
        assertEquals(base, PrintPageSelection.arrange(base, listOf(1, 1, 2), emptyMap()))
        assertEquals(base, PrintPageSelection.arrange(base, listOf(1, 2, 9), emptyMap()))
    }

    @Test fun rangeTextAbbreviatesOnlyAFullySelectedDocument() {
        assertEquals("", PrintPageSelection.rangeText(listOf(0, 1, 2), 3))
        assertEquals("1-2", PrintPageSelection.rangeText(listOf(0, 1), 3))
        assertEquals("1,3", PrintPageSelection.rangeText(listOf(0, 2), 3))
        assertEquals("2", PrintPageSelection.rangeText(listOf(1), 3))
        // A one page document is always "all pages".
        assertEquals("", PrintPageSelection.rangeText(listOf(0), 1))
    }

    @Test fun rangeTextRoundTripsThroughTheRangeParser() {
        val positions = listOf(0, 1, 4)
        val text = PrintPageSelection.rangeText(positions, 6)
        assertEquals("1-2,5", text)
        assertEquals(positions, PrintPageSelection.positions(text, 6))
    }

    @Test fun positionsTreatBlankAsEveryPageAndRejectNonsense() {
        assertEquals(listOf(0, 1, 2), PrintPageSelection.positions("", 3))
        assertTrue(PrintPageSelection.positions("9", 3).isEmpty())
        assertTrue(PrintPageSelection.positions("abc", 3).isEmpty())
    }

    @Test fun moveReordersTheVisibleList() {
        assertEquals(listOf(2, 1, 3), PrintPageSelection.move(listOf(1, 2, 3), 3, 0, 1))
        assertEquals(listOf(1, 3, 2), PrintPageSelection.move(listOf(1, 2, 3), 3, 2, 1))
        // Out of range moves are ignored.
        assertEquals(listOf(1, 2, 3), PrintPageSelection.move(listOf(1, 2, 3), 3, 0, 9))
    }

    @Test fun staleInvalidOrderIsReplacedByCurrentSourceOrderBeforeDragging() {
        // Old saved selections may refer to pages that no longer exist. The UI
        // renders the source order, so dragging must operate on that same order.
        val malformedOrders = listOf(listOf(1, 2), listOf(3, 3, 1), listOf(1, 2, 3, 4), listOf(4, 2, 1))
        for (malformed in malformedOrders) {
            val moved = PrintPageSelection.move(malformed, size = 3, from = 0, to = 2)
            assertEquals("drag must commit visible 2,3,1 order for persisted $malformed", listOf(2, 3, 1), moved)
            assertEquals(listOf(2, 3, 1), PrintPageSelection.arrange(base, moved, emptyMap()).map { it.sourcePage })
        }
    }

    @Test fun normalizedOrderProvidesSafeBaseForDragReordering() {
        assertEquals(listOf(1, 2, 3), PrintPageSelection.normalizedOrder(listOf(3, 1, 1), 3))
        assertEquals(listOf(3, 1, 2), PrintPageSelection.normalizedOrder(listOf(3, 1, 2), 3))
    }

    @Test fun permutationGuardAcceptsOnlyCompleteUniqueOrders() {
        assertTrue(PrintPageSelection.isPermutation(listOf(2, 1, 3), 3))
        assertFalse(PrintPageSelection.isPermutation(listOf(2, 1), 3))
        assertFalse(PrintPageSelection.isPermutation(listOf(1, 1, 2), 3))
    }

    @Test fun reusableOptionsDropThePageOrderSoPresetsNeverCarryAStaleOrder() {
        val options = PrintOptions(range="1-2", pageOrder=listOf(2, 1), pageRotations=mapOf(1 to 90))
        val reusable = options.reusable()
        assertEquals("", reusable.range)
        assertTrue(reusable.pageOrder.isEmpty())
        assertTrue(reusable.pageRotations.isEmpty())
    }
}
