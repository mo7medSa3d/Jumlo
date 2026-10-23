package com.jumlo.app.core

/** Persisted manual pause must never be confused with an unexpected worker interruption. */
object JobLifecyclePolicy {
    private val recoverable = setOf("QUEUED", "VALIDATING", "RUNNING", "FINALIZING", "INTERRUPTED")
    private val pausable = setOf("QUEUED", "VALIDATING", "RUNNING")

    fun shouldRecover(stage: String): Boolean = stage in recoverable
    fun canPause(stage: String): Boolean = stage in pausable
}
