package com.jumlo.app.hosttest

import com.jumlo.app.data.jobs.DownloadAdmission
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

/** Reproduces cancellation while slot accounting is contended. */
fun main() = runBlocking {
    val admission = DownloadAdmission()
    val entered = CompletableDeferred<Unit>()
    val worker = launch(start = CoroutineStart.UNDISPATCHED) {
        admission.withSlot(1) { entered.complete(Unit); awaitCancellation() }
    }
    entered.await()
    // Force the accounting mutex into contention so finally must suspend.
    val field = DownloadAdmission::class.java.getDeclaredField("mutex").apply { isAccessible = true }
    val accounting = field.get(admission) as Mutex
    accounting.lock()
    worker.cancel()
    yield()
    accounting.unlock()
    worker.join()
    val result = withTimeout(1_000) { admission.withSlot(1) { "released" } }
    check(result == "released") { "Cancelled download leaked its slot" }

    val first = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val holder = launch { admission.withSlot(1) { first.complete(Unit); release.await() } }
    first.await()
    val waiter = launch { admission.withSlot(1) { error("Cancelled waiter acquired a slot") } }
    yield()
    waiter.cancelAndJoin()
    release.complete(Unit)
    holder.join()
    withTimeout(1_000) { admission.withSlot(1) { Unit } }
    println("PASS: cancelled holder releases capacity; cancelled waiter does not consume it")
}
