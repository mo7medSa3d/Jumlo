package com.jumlo.app.data

import com.jumlo.app.data.output.withPublicationOwnership
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class PublicationHandoffTest {
    @Test fun cancellationAfterCommitBeforeCallerResumesRollsBackTheUnclaimedCopy() {
        val caller = QueuedDispatcher()
        val worker = QueuedDispatcher()
        val removed = mutableListOf<String>()
        val task = CoroutineScope(SupervisorJob() + caller).async {
            withPublicationOwnership(worker, removed::add) { "content://fixture/committed" }
        }
        caller.next() // Dispatch publication to IO.
        worker.next() // Commit succeeded; return to caller is queued.
        task.cancel()
        while (caller.hasWork || worker.hasWork) {
            if (caller.hasWork) caller.next()
            if (worker.hasWork) worker.next()
        }
        assertTrue(task.isCancelled)
        assertEquals(listOf("content://fixture/committed"), removed)
    }

    @Test fun successfulHandoffDoesNotDeleteTheReturnedCopy() {
        val caller = QueuedDispatcher()
        val worker = QueuedDispatcher()
        val removed = mutableListOf<String>()
        var received: String? = null
        val task = CoroutineScope(SupervisorJob() + caller).async {
            received = withPublicationOwnership(worker, removed::add) { "content://fixture/committed" }
        }
        caller.next()
        worker.next()
        caller.next()
        assertTrue(task.isCompleted)
        assertFalse(task.isCancelled)
        assertEquals("content://fixture/committed", received)
        assertTrue(removed.isEmpty())
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        val hasWork get() = queue.isNotEmpty()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun next() = queue.removeFirst().run()
    }
}
