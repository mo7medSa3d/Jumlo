package com.jumlo.app.data.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class InputStreamOwnershipTest {
    @Test fun cancellationAfterOpenBeforeCallerResumesClosesTheUndeliveredStream() {
        val handoff = Handoff()
        val stream = TrackedStream()
        var received: InputStream? = null
        var failure: Throwable? = null
        val cancelled = CancellationException("caller stopped")
        try {
            val task = handoff.scope.async {
                try { received = withInputStreamOwnership(handoff.worker) { stream } }
                catch (error: Throwable) { failure = error; throw error }
            }
            handoff.callerScheduler.runCurrent()
            handoff.workerScheduler.runCurrent() // Opened; the return is queued on the caller.
            assertNull(received)
            assertEquals(0, stream.closeCalls)

            task.cancel(cancelled)
            handoff.finish()

            assertTrue(task.isCompleted)
            assertTrue(task.isCancelled)
            assertNull(received)
            assertEquals("An undelivered stream must be closed exactly once", 1, stream.closeCalls)
            assertTrue(failure is CancellationException)
            assertSame(cancelled, originalFailure(requireNotNull(failure)))
        } finally { handoff.scope.cancel() }
    }

    @Test fun successfulReturnLeavesTheStreamOpenForItsCaller() {
        val handoff = Handoff()
        val stream = TrackedStream()
        var received: InputStream? = null
        try {
            val task = handoff.scope.async {
                received = withInputStreamOwnership(handoff.worker) { stream }
            }
            handoff.finish()

            assertTrue(task.isCompleted)
            assertFalse(task.isCancelled)
            assertSame(stream, received)
            assertEquals(0, stream.closeCalls)
            assertEquals(42, requireNotNull(received).use { it.read() })
            assertEquals(1, stream.closeCalls)
        } finally { handoff.scope.cancel() }
    }

    @Test fun failureWhileOpeningRemainsTheReportedFailure() {
        val handoff = Handoff()
        val expected = IOException("open failed")
        var failure: Throwable? = null
        try {
            val task = handoff.scope.async {
                try { withInputStreamOwnership(handoff.worker) { throw expected } }
                catch (error: Throwable) { failure = error; throw error }
            }
            handoff.finish()

            assertTrue(task.isCompleted)
            assertTrue(task.isCancelled)
            assertTrue(failure is IOException)
            assertSame(expected, originalFailure(requireNotNull(failure)))
            assertTrue(requireNotNull(failure).suppressed.isEmpty())
        } finally { handoff.scope.cancel() }
    }

    @Test fun closeFailureIsSuppressedWithoutReplacingCancellation() {
        val handoff = Handoff()
        val closeFailure = IOException("close failed")
        val stream = TrackedStream(closeFailure)
        val cancelled = CancellationException("caller stopped")
        var failure: Throwable? = null
        try {
            val task = handoff.scope.async {
                try { withInputStreamOwnership(handoff.worker) { stream } }
                catch (error: Throwable) { failure = error; throw error }
            }
            handoff.callerScheduler.runCurrent()
            handoff.workerScheduler.runCurrent()
            task.cancel(cancelled)
            handoff.finish()

            assertTrue(task.isCompleted)
            assertTrue(task.isCancelled)
            assertEquals(1, stream.closeCalls)
            assertTrue(failure is CancellationException)
            assertSame(cancelled, originalFailure(requireNotNull(failure)))
            assertArrayEquals(arrayOf(closeFailure), requireNotNull(failure).suppressed)
        } finally { handoff.scope.cancel() }
    }

    @Test fun cancellationBeforeOpeningDoesNotAcquireAStream() {
        val handoff = Handoff()
        var opened = 0
        try {
            val task = handoff.scope.async {
                withInputStreamOwnership(handoff.worker) { opened++; TrackedStream() }
            }
            handoff.callerScheduler.runCurrent()
            task.cancel()
            handoff.finish()

            assertTrue(task.isCompleted)
            assertTrue(task.isCancelled)
            assertEquals(0, opened)
        } finally { handoff.scope.cancel() }
    }

    private class Handoff {
        val callerScheduler = TestCoroutineScheduler()
        val workerScheduler = TestCoroutineScheduler()
        private val caller = StandardTestDispatcher(callerScheduler)
        val worker = StandardTestDispatcher(workerScheduler)
        val scope = CoroutineScope(SupervisorJob() + caller)

        fun finish() {
            // Open, resume the caller, close an orphan if needed, then finish the caller.
            repeat(3) {
                callerScheduler.runCurrent()
                workerScheduler.runCurrent()
            }
        }
    }

    private class TrackedStream(private val closeFailure: Throwable? = null) :
        ByteArrayInputStream(byteArrayOf(42)) {
        var closeCalls = 0
            private set

        override fun close() {
            closeCalls++
            super.close()
            closeFailure?.let { throw it }
        }
    }

    // Coroutine stack-trace recovery may copy an exception and retain the original as its cause.
    private fun originalFailure(error: Throwable): Throwable =
        generateSequence(error) { it.cause }.last()
}
