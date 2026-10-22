package com.jumlo.app.core

import java.io.File

/** A generation changes only for an explicit retry of a failed direct-download job. */
object DownloadRestartPolicy {
    /** A transfer whose bytes already match the known total is complete and only needs publishing. */
    fun isTransferComplete(partialBytes: Long, knownTotalBytes: Long?): Boolean =
        knownTotalBytes != null && knownTotalBytes > 0L && partialBytes == knownTotalBytes

    fun shouldDiscardPartial(currentGeneration: Long, journalGeneration: Long): Boolean {
        require(currentGeneration >= 0 && journalGeneration >= 0) { "INVALID_RESTART_GENERATION" }
        return currentGeneration > journalGeneration
    }
    /** Called only by the executing worker after it has claimed the job lease. */
    fun resetUncommittedFiles(currentGeneration: Long, journalGeneration: Long, partial: File, journal: File, pending: File, checkLease: () -> Unit): Boolean {
        if(!shouldDiscardPartial(currentGeneration,journalGeneration)) return false
        for(file in listOf(partial,journal,pending)) {
            checkLease()
            require(!file.exists() || file.delete()) { "DOWNLOAD_RESTART_FAILED" }
        }
        return true
    }
    fun retryGeneration(toolId: String, stage: String, previous: Long, failureCode: String? = null): Long {
        require(previous >= 0) { "INVALID_RESTART_GENERATION" }
        return if (toolId in setOf("dl_02", "dl_03") && (stage in setOf("FAILED", "PARTIAL") || (stage == "WAITING_FOR_INPUT" && failureCode in setOf("DOWNLOAD_RESTART_REQUIRED", "MISSING_RESUME_VALIDATOR")))) {
            Math.addExact(previous, 1L)
        } else previous
    }
}
