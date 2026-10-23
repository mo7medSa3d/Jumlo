package com.jumlo.app.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/** A last-resort boundary for UI launches; cancellation and VM-fatal errors keep their normal semantics. */
fun viewModelErrorBoundary(scope: String, present: (Exception) -> Unit) = CoroutineExceptionHandler { _, error ->
    when (error) {
        is CancellationException -> Unit
        is Exception -> { AppDiagnostics.report(scope, error); present(error) }
        else -> throw error
    }
}
