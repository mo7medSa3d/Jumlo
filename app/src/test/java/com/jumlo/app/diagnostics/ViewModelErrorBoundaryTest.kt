package com.jumlo.app.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelErrorBoundaryTest {
    @Test fun failedLaunchShowsAnErrorAndDoesNotCancelSiblingWork() = runTest {
        var shown: Exception? = null
        var siblingCompleted = false
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            viewModelErrorBoundary("workspace") { shown = it })
        val failure = IllegalArgumentException("private message must not be logged")
        try {
            scope.launch { throw failure }
            scope.launch { siblingCompleted = true }
            advanceUntilIdle()
            assertSame(failure, shown)
            assertTrue(siblingCompleted)
        } finally { scope.cancel() }
    }

    @Test fun cancellationIsNotReportedAsAnApplicationFailure() = runTest {
        var errors = 0
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
            viewModelErrorBoundary("media") { errors++ })
        try {
            val work = scope.launch { throw CancellationException("screen closed") }
            advanceUntilIdle()
            assertTrue(work.isCancelled)
            assertEquals(0, errors)
        } finally { scope.cancel() }
    }
}
