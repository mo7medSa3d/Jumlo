package com.jumlo.app.printing

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class PrintExecutionPolicyTest {
    @Test fun cancellationWinsOverSuccessAlreadyWaitingForMainThread() {
        val gate=PrintCallbackGate()
        gate.cancel()
        assertEquals(PrintCallbackGate.Result.CANCELED,gate.finish(PrintCallbackGate.Result.SUCCESS))
        assertNull(gate.finish(PrintCallbackGate.Result.FAILURE))
        assertNull(gate.finish(PrintCallbackGate.Result.CANCELED))
    }
    @Test fun concurrentWriteFailureAndCancellationDeliverOnlyOneTerminalCallback() {
        val gate=PrintCallbackGate();val start=CountDownLatch(1);val delivered=AtomicInteger()
        val threads=List(24) {index ->Thread {
            start.await()
            if(index%2==0) gate.cancel()
            if(gate.finish(if(index%3==0) PrintCallbackGate.Result.SUCCESS else PrintCallbackGate.Result.FAILURE)!=null) delivered.incrementAndGet()
        }.also {it.start()}}
        start.countDown();threads.forEach {it.join()}
        assertEquals(1,delivered.get())
    }
    @Test fun completedWriteCannotEmitAnotherCallbackAfterLateCancellation() {
        val gate=PrintCallbackGate()
        assertEquals(PrintCallbackGate.Result.SUCCESS,gate.finish(PrintCallbackGate.Result.SUCCESS))
        gate.cancel();assertNull(gate.finish(PrintCallbackGate.Result.CANCELED))
    }
    @Test fun cameraImageSamplingBoundsResidentPixelsWithoutChangingPhysicalDpi() {
        val sample=PrintImageBudget.sampleSize(4032,3024)
        assertEquals(4,sample)
        assertTrue(((4032+sample-1)/sample).toLong()*((3024+sample-1)/sample)<=2L*1024*1024)
        assertEquals(1,PrintImageBudget.sampleSize(100,100))
        assertThrows(IllegalArgumentException::class.java) {PrintImageBudget.sampleSize(0,5)}
    }
    @Test fun numbersAcceptArabicAndPersianDecimalInput() {
        assertEquals(12.5,PrintNumberInput.parse("١٢٫٥")!!,.0001)
        assertEquals(210.0,PrintNumberInput.parse("۲۱۰")!!,.0001)
        assertEquals(5.4,PrintNumberInput.parse("5,4")!!,.0001)
        assertNull(PrintNumberInput.parse("NaN"))
    }
    @Test fun snapshotLeaseProtectsConcurrentCopyAndHistoryReferencesSurviveCleanup() {
        val root=Files.createTempDirectory("print-snapshot-test").toFile()
        try {
            val original=File(root,"original.pdf").apply {writeText("original")}
            val policy=PrintSnapshotPolicy(File(root,"printing"))
            val lease=policy.acquire(PrintSource(original.path,"Original","application/pdf"))
            File(lease.source.path).writeText("copy")
            policy.cleanup(emptySet())
            assertTrue(File(lease.source.path).exists())
            policy.release(lease,setOf(lease.source.path))
            assertTrue(File(lease.source.path).exists())
            policy.cleanup(emptySet())
            assertFalse(File(lease.source.path).exists())
            assertEquals("original",original.readText())
        } finally {root.deleteRecursively()}
    }
    @Test fun canceledUnregisteredSnapshotIsDeletedButExistingHistorySourceIsKept() {
        val root=Files.createTempDirectory("print-cancel-test").toFile()
        try {
            val original=File(root,"original.pdf").apply {writeText("original")}
            val policy=PrintSnapshotPolicy(File(root,"printing"))
            val source=PrintSource(original.path,"Original","application/pdf")
            val canceled=policy.acquire(source);File(canceled.source.path).writeText("canceled")
            policy.release(canceled,emptySet());assertFalse(File(canceled.source.path).exists())
            val saved=policy.acquire(source);File(saved.source.path).writeText("history")
            policy.release(saved,setOf(saved.source.path))
            val reused=policy.acquire(saved.source)
            assertFalse(reused.created)
            policy.release(reused,setOf(saved.source.path));assertTrue(File(saved.source.path).exists())
            assertTrue(original.exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun renameKeepsRecentIdentityButOrderedPageChangesDoNot() {
        val source=PrintSource("/private/inbound/file.bin","A.pdf","application/pdf",listOf(PrintPage(2),PrintPage(1)))
        assertEquals(PrintSourceIdentity.documentKey(source),PrintSourceIdentity.documentKey(source.copy(name="B.pdf")))
        assertNotEquals(PrintSourceIdentity.documentKey(source),PrintSourceIdentity.documentKey(source.copy(pages=source.pages.reversed())))
        assertEquals(64,PrintSourceIdentity.key(source).length)
    }
    @Test fun historyEvictionCannotEvictAnActivePrintJob() {
        val source=PrintSource("/private/printing/source.bin","Source","application/pdf")
        val oldActive=PrintHistoryEntry("active",source,PrintOptions(),PrintJobState.STARTED,0)
        val history=(1..120).map {PrintHistoryEntry("done-$it",source,PrintOptions(),PrintJobState.COMPLETED,it.toLong())}+oldActive
        val retained=PrintSnapshotPolicy.retain(history)
        assertTrue(oldActive in retained)
        assertEquals(101,retained.size)
    }
}
