package com.jumlo.app.printing

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintUsageTest {
    private fun entry(pages: List<PrintPage>, range: String, state: PrintJobState = PrintJobState.COMPLETED) =
        PrintHistoryEntry("id", PrintSource("/private/inbound/a.bin", "a.pdf", "application/pdf", pages), PrintOptions(range = range), state, 0L)

    @Test fun everyPageIsCountedWhenNoRangeIsGiven() {
        assertEquals(3, PrintUsage.pages(entry(listOf(PrintPage(1), PrintPage(2), PrintPage(3)), "")))
    }

    @Test fun onlyThePagesInTheRangeAreCounted() {
        assertEquals(2, PrintUsage.pages(entry(listOf(PrintPage(1), PrintPage(2), PrintPage(3)), "1-2")))
        assertEquals(1, PrintUsage.pages(entry(listOf(PrintPage(1), PrintPage(2), PrintPage(3)), "3")))
    }

    @Test fun duplicatedPagesCountAsSeparateSheets() {
        assertEquals(3, PrintUsage.pages(entry(listOf(PrintPage(1), PrintPage(1), PrintPage(2)), "")))
    }

    @Test fun anInvalidRangeCountsNothingInsteadOfFailing() {
        assertEquals(0, PrintUsage.pages(entry(listOf(PrintPage(1), PrintPage(2)), "9")))
    }

    @Test fun aHistoryWithoutPagesCountsNothing() {
        assertEquals(0, PrintUsage.pages(entry(emptyList(), "")))
    }

    @Test fun totalsAddUpAcrossJobs() {
        val history = listOf(
            entry(listOf(PrintPage(1), PrintPage(2)), ""),
            entry(listOf(PrintPage(1), PrintPage(2), PrintPage(3)), "1")
        )
        assertEquals(3, PrintUsage.totalPages(history))
        assertEquals(2, PrintUsage.completedJobs(history))
    }
}
