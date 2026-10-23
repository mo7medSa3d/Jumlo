package com.jumlo.app.data.output

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Keep ownership of a committed copy until the caller receives its URI. */
internal suspend fun withPublicationOwnership(
    dispatcher: CoroutineDispatcher,
    rollback: (String) -> Unit,
    onRollbackFailure: (Throwable) -> Unit = {},
    publish: suspend () -> String
): String {
    var committed: String? = null
    try {
        return withContext(dispatcher) { publish().also { committed = it } }
    } catch (error: Throwable) {
        // withContext can discard a successful result when its caller is cancelled
        // while the return continuation is queued on the caller's dispatcher.
        committed?.let { uri ->
            withContext(NonCancellable + dispatcher) {
                try { rollback(uri) }
                catch (cleanup: Throwable) { runCatching { onRollbackFailure(cleanup) } }
            }
        }
        throw error
    }
}
