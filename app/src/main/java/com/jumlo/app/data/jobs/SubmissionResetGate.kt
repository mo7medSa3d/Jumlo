package com.jumlo.app.data.jobs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive

/** Serializes explicit submission resets and exposes the latest reset's result. */
internal class SubmissionResetGate(
    private val scope: CoroutineScope,
    private val reset: suspend () -> Unit
) {
    private val lock = Any()
    private var latest: Deferred<Unit>? = null

    fun request() {
        val task = synchronized(lock) {
            val previous = latest
            scope.async(start = CoroutineStart.LAZY) {
                // Order resets by completion, without inheriting an earlier save
                // failure. This explicit request must be able to retry persistence.
                previous?.join()
                // An immediate dispatcher can resume this child while its parent
                // is still notifying siblings of cancellation during teardown.
                scope.coroutineContext.ensureActive()
                reset()
            }.also { latest = it }
        }
        task.start()
    }

    suspend fun awaitLatest() {
        while (true) {
            val pending = synchronized(lock) { latest } ?: return
            pending.await()
            if (synchronized(lock) { latest === pending }) return
        }
    }
}
