package com.jumlo.app.data.source

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Retain ownership until the opened stream has reached its caller. */
internal suspend fun withInputStreamOwnership(
    dispatcher: CoroutineDispatcher,
    open: suspend () -> InputStream
): InputStream {
    var pending: InputStream? = null
    try {
        return withContext(dispatcher) { open().also { pending = it } }
    } catch (error: Throwable) {
        // Cancellation may discard a successful open while the return to the caller is queued.
        pending?.let { stream ->
            // Keep the return from cleanup non-cancellable as well, preserving the original error.
            withContext(NonCancellable) {
                withContext(dispatcher) {
                    try { stream.close() }
                    catch (closeFailure: Throwable) {
                        if (closeFailure !== error) error.addSuppressed(closeFailure)
                    }
                }
            }
        }
        throw error
    }
}
