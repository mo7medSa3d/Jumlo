package com.jumlo.app.data.jobs

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadAdmissionFlowTest {
    @Test fun cancellingTheActiveFlowLetsTheWaitingDownloadProceed()=runTest {
        val gate=DownloadAdmission()
        val active=flow {
            gate.withSlot(1,{}) { emit("active");awaitCancellation() }
        }
        active.test {
            assertEquals("active",awaitItem())
            flow { gate.withSlot(1,{}) { emit("resumed") } }.test {
                expectNoEvents()
                this@runTest.testScheduler.runCurrent()
                expectNoEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        flow { gate.withSlot(1,{}) { emit("next") } }.test {
            assertEquals("next",awaitItem())
            awaitComplete()
        }
    }
    @Test fun queuedCancellationDoesNotConsumeTheNextAvailableSlot()=runTest {
        val gate=DownloadAdmission()
        val release=CompletableDeferred<Unit>()
        flow { gate.withSlot(1,{}) { emit("first");release.await() } }.test {
            assertEquals("first",awaitItem())
            flow { gate.withSlot(1,{}) { emit("cancelled-waiter") } }.test {
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            release.complete(Unit)
            awaitComplete()
        }
        flow { gate.withSlot(1,{}) { emit("available") } }.test {
            assertEquals("available",awaitItem());awaitComplete()
        }
    }
}
