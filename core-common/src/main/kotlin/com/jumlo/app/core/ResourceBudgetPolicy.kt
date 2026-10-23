package com.jumlo.app.core

/** Intake is disk-bounded, not arbitrarily capped at 4 GiB. Archive expansion and output
 * counts retain separate safety limits to avoid zip bombs and record explosions. */
object ResourceBudgetPolicy {
    const val maxImportBytes: Long = Long.MAX_VALUE
    const val maxExpandedArchiveBytes: Long = 4L * 1024 * 1024 * 1024
    const val maxArchiveEntries: Int = 2048
    const val maxPublishedOutputsPerJob: Int = 512
    const val minFreeBytes: Long = 64L * 1024 * 1024
    const val maxMediaWorkspaceBytes: Long = 16L * 1024 * 1024 * 1024

    fun requireOutputCount(count: Int, max: Int = maxPublishedOutputsPerJob) {
        require(count in 1..max && max in 1..maxPublishedOutputsPerJob) { "TOO_MANY_OUTPUTS" }
    }
}
