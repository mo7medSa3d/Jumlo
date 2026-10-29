package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests real expanded page lists, including repeated ranges that could exhaust heap. */
class PageRangeAllocationBudgetTest {
    @Test fun regularTwoHundredPageSplitRangesStillExpandExactly() {
        val all = PageSelection.groups("1-50,51-100,101-150,151-200", 200)
        assertEquals(4, all.size)
        assertEquals(200, all.sumOf { it.size })
        assertEquals((1..50).toList(), all.first())
        assertEquals(200, all.last().last())
    }

    @Test fun arabicIndicPageNumbersAndCommaRemainSupported() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(5)),
            PageSelection.groups("\u0661-\u0663\u060C\u0665", 200))
    }

    @Test fun repeatedHugeRangesAreRejectedBeforeUnboundedAllocation() {
        val repeated = List(20) { "1-10000" }.joinToString(",")
        val error = runCatching { PageSelection.groups(repeated, 10_000) }.exceptionOrNull()
        assertTrue("large repeated page groups must fail with PAGE_SELECTION_LIMIT, got $error",
            error is IllegalArgumentException && error.message == "PAGE_SELECTION_LIMIT")
        // Parsing selection (deduplicated positions) remains separate and valid.
        assertEquals(10_000, PageSelection.parse(repeated, 10_000).size)
    }

    @Test fun clippingAGroupToPageCountDoesNotCountMissingPages() {
        val groups = PageSelection.groups("198-999999,1-3", 200)
        assertEquals(listOf(listOf(198, 199, 200), listOf(1, 2, 3)), groups)
    }
}
