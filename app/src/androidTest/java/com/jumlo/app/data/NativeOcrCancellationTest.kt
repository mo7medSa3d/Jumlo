package com.jumlo.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import com.jumlo.app.data.ocr.awaitNativeCompletion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeOcrCancellationTest {
    @Test fun cancellationKeepsNativeInputOwnedUntilTaskCompletesAndDiscardsItsResult() = runBlocking {
        val native = TaskCompletionSource<String>()
        var inputReleased = false
        var resultDelivered = false
        // UNDISPATCHED runs through listener registration before control returns to this test.
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                native.task.awaitNativeCompletion()
                currentCoroutineContext().ensureActive()
                resultDelivered = true
            } finally { inputReleased = true }
        }
        worker.cancel()
        assertFalse("The bitmap must still be owned while native code reads it", inputReleased)
        assertFalse(worker.isCompleted)
        native.setResult("recognized after cancellation")
        withTimeout(5_000) { worker.join() }
        assertTrue(inputReleased)
        assertTrue(worker.isCancelled)
        assertFalse(resultDelivered)
    }

    @Test fun nativeFailureReleasesInputAndPropagatesItsOriginalException() = runBlocking {
        val native = TaskCompletionSource<String>()
        val failure = IllegalStateException("native processing failure")
        native.setException(failure)
        val observed = runCatching { native.task.awaitNativeCompletion() }.exceptionOrNull()
        assertSame(failure, observed)
    }
}
