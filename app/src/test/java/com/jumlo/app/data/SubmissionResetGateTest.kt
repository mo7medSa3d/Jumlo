package com.jumlo.app.data

import com.jumlo.app.data.jobs.SubmissionResetGate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SubmissionResetGateTest {
    @Test fun failedResetBlocksSubmissionButNextExplicitResetCanRecover() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        var submitted = 0
        val gate = SubmissionResetGate(scope) {
            attempts++
            if (attempts == 1) throw IOException("DRAFT_SAVE_FAILED")
        }
        try {
            gate.request()
            try { gate.awaitLatest(); submitted++; fail("Failed persistence must block submission") }
            catch (_: IOException) { }
            assertEquals(0, submitted)
            assertTrue(scope.isActive)
            gate.request()
            gate.awaitLatest()
            submitted++
            assertEquals(2, attempts)
            assertEquals(1, submitted)
        } finally { scope.cancel() }
    }

    @Test fun multipleRequestsAreSerializedAndSubmissionWaitsForTheLatestReset() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var attempts = 0
        val gate = SubmissionResetGate(scope) {
            val attempt = ++attempts
            events += "start $attempt"
            if (attempt == 1) first.await() else second.await()
            events += "end $attempt"
        }
        try {
            gate.request()
            val waiting = async(Dispatchers.Unconfined) { gate.awaitLatest() }
            gate.request()
            assertEquals(listOf("start 1"), events)
            first.complete(Unit)
            assertEquals(listOf("start 1", "end 1", "start 2"), events)
            assertFalse("A reset requested while awaiting must also finish", waiting.isCompleted)
            second.complete(Unit)
            waiting.await()
            assertEquals(listOf("start 1", "end 1", "start 2", "end 2"), events)
        } finally { scope.cancel() }
    }

    @Test fun cancelledScopeCancelsPendingResetsAndAwaitStillThrowsCancellation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        val gate = SubmissionResetGate(scope) { attempts++; awaitCancellation() }
        gate.request()
        gate.request()
        scope.cancel()
        try { gate.awaitLatest(); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(1, attempts)
        assertFalse(scope.isActive)
    }

    @Test fun fatalResetFailureIsNotConvertedIntoSuccessfulCompletion() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fatal = LinkageError("fixture fatal failure")
        val gate = SubmissionResetGate(scope) { throw fatal }
        try {
            gate.request()
            try { gate.awaitLatest(); fail("Fatal failure must propagate") }
            catch (caught: LinkageError) {
                assertEquals(fatal.javaClass, caught.javaClass)
                // Coroutine stack-trace recovery may copy the error while retaining the original cause.
                val original = generateSequence<Throwable>(caught) { it.cause }.last()
                assertSame("The original fatal error must remain the root cause", fatal, original)
            }
        } finally { scope.cancel() }
    }
}
