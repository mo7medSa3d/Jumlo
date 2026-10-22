package com.jumlo.app.printing

/**
 * Local print usage.
 *
 * The pages a job actually sent are derivable from the entry itself: the source
 * carries the ordered page list and the options carry the range, so no extra
 * persisted field is needed and old history entries remain readable.
 */
object PrintUsage {
    /** Pages this job sent to the print service. */
    fun pages(entry: PrintHistoryEntry): Int {
        val total = entry.source.pages.size
        if (total <= 0) return 0
        return runCatching {
            PrintRanges.select(PrintRanges.sourcePages(entry.source.pages, total), entry.options.range).size
        }.getOrDefault(0)
    }

    /** Pages across a list of jobs. */
    fun totalPages(history: List<PrintHistoryEntry>): Int = history.sumOf { pages(it) }

    /** Jobs that the print service reported as completed. */
    fun completedJobs(history: List<PrintHistoryEntry>): Int =
        history.count { it.state == PrintJobState.COMPLETED }
}
