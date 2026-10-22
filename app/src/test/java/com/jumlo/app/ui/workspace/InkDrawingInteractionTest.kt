package com.jumlo.app.ui.workspace

import com.jumlo.app.core.PdfInkDrawing
import org.junit.Assert.*
import org.junit.Test

class InkDrawingInteractionTest {
    private val first=listOf(.1f,.1f,.2f,.2f)
    private val second=listOf(.7f,.8f,.8f,.9f)
    private fun snapshot(strokes:List<List<Float>> = emptyList())=
        VisualEditSnapshot(VisualRect.WHOLE,0,false,false,0f,inkStrokes=strokes)

    @Test fun twoPenGesturesKeepSeparateStrokesAndUndoRemovesOnlyTheLast() {
        var current=snapshot()
        var timeline=VisualEditTimeline()
        for(stroke in listOf(first,second)) {
            timeline=timeline.record(current)
            current=current.copy(inkStrokes=PdfInkDrawing.append(current.inkStrokes,stroke))
        }
        assertEquals(listOf(first,second),current.inkStrokes)
        val undone=timeline.undo(current)!!
        assertEquals(listOf(first),undone.snapshot.inkStrokes)
        val redone=undone.timeline.redo(undone.snapshot)!!
        assertEquals(listOf(first,second),redone.snapshot.inkStrokes)
    }

    @Test fun clearIsUndoableAndANewStrokeAfterUndoDiscardsRedo() {
        val drawn=snapshot(listOf(first,second))
        val cleared=snapshot()
        val undone=VisualEditTimeline().record(drawn).undo(cleared)!!
        assertEquals(drawn,undone.snapshot)
        val newTimeline=undone.timeline.record(drawn)
        assertNull(newTimeline.redo(snapshot(listOf(first,second,first))))
    }

    @Test fun cancelledGestureDoesNotAppendToCommittedStrokes() {
        val committed=listOf(first)
        val live=InkStrokeCapture().append(.7f,.8f).append(.8f,.9f)
        assertEquals(second,live.completedStroke())
        val cancelled=live.cancel()
        assertNull(cancelled.completedStroke())
        assertTrue(cancelled.points.isEmpty())
        assertEquals(committed,cancelled.commitTo(committed))
    }

    @Test fun legacyStrokeIsRetainedWhenAnotherStrokeIsAdded() {
        val strokes=PdfInkDrawing.resolve(emptyList(),first)
        assertEquals(listOf(first,second),PdfInkDrawing.append(strokes,second))
        assertEquals(listOf(second),PdfInkDrawing.resolve(listOf(second),first))
        val legacy=snapshot().copy(inkPoints=first)
        val appended=snapshot(PdfInkDrawing.append(strokes,second))
        assertEquals(legacy,VisualEditTimeline().record(legacy).undo(appended)!!.snapshot)
    }

    @Test fun captureStopsAtItsBudgetWithoutChangingEarlierPoints() {
        val capture=InkStrokeCapture(maxPoints=2).append(.1f,.1f).append(.2f,.2f).append(.9f,.9f)
        assertEquals(first,capture.completedStroke())
        assertTrue(capture.limitReached)
        assertEquals(0,PdfInkDrawing.strokeCapacity(List(PdfInkDrawing.MAX_STROKES) {first}))
    }

    @Test fun invalidOrExcessiveStrokesAreRejectedBeforeTheyReachThePdf() {
        val invalid=listOf(
            emptyList(), listOf(.1f,.2f), listOf(.1f,.2f,.3f),
            listOf(Float.NaN,.2f,.3f,.4f),listOf(.1f,Float.POSITIVE_INFINITY,.3f,.4f),
            listOf(-.1f,.2f,.3f,.4f),listOf(.1f,.2f,1.1f,.4f),
            List((PdfInkDrawing.MAX_POINTS_PER_STROKE+1)*2) {.5f}
        )
        invalid.forEach {stroke-> assertTrue(runCatching {PdfInkDrawing.requireValid(listOf(stroke))}.isFailure)}
        assertTrue(runCatching {PdfInkDrawing.requireValid(emptyList())}.isFailure)
        assertTrue(runCatching {PdfInkDrawing.requireValid(List(PdfInkDrawing.MAX_STROKES+1) {first})}.isFailure)
        val longStroke=List(PdfInkDrawing.MAX_POINTS_PER_STROKE*2) {.5f}
        assertTrue(runCatching {PdfInkDrawing.requireValid(List(PdfInkDrawing.MAX_TOTAL_POINTS/PdfInkDrawing.MAX_POINTS_PER_STROKE+1) {longStroke})}.isFailure)
    }
}
