package com.jumlo.app.ui.workspace

import com.jumlo.app.data.processing.PdfPageEdit
import org.junit.Assert.*
import org.junit.Test

class PageEditHistoryTest {
    private fun pages() = listOf(PdfPageEdit("a",1),PdfPageEdit("b",2),PdfPageEdit("c",3))
    @Test fun duplicatesKeepStableIndependentIdentitiesAndRotationAccumulates() {
        val start=PageEditHistory(pages())
        val copied=start.duplicate(setOf("b"))
        assertEquals(listOf(1,2,2,3),copied.current.map { it.sourcePage })
        assertEquals(4,copied.current.map { it.instanceId }.toSet().size)
        val id=copied.current[2].instanceId
        val rotated=copied.rotate(setOf(id)).rotate(setOf(id))
        assertEquals(180,rotated.current[2].rotationDegrees)
        assertEquals(0,rotated.current[1].rotationDegrees)
        assertEquals(copied.current,rotated.undo().undo().current)
        assertEquals(rotated.current,rotated.undo().redo().current)
    }
    @Test fun allOperationsUndoAndBranchingClearsRedo() {
        val start=PageEditHistory(pages())
        val changed=start.move("c",0).rotate(setOf("c")).delete(setOf("b"))
        assertEquals(start.current,changed.undo().undo().undo().current)
        assertTrue(changed.undo().rotate(setOf("a")).future.isEmpty())
        assertEquals(changed.current,changed.undo().redo().current)
    }
    @Test fun deletingEveryPageIsRejectedAndHistoryIsBounded() {
        val start=PageEditHistory(pages())
        assertEquals(start,start.delete(setOf("a","b","c")))
        var edited=start
        repeat(50) { edited=edited.rotate(setOf("a")) }
        assertEquals(30,edited.past.size)
    }
    @Test fun invalidEntriesNeverReplaceAValidDocument() {
        val start=PageEditHistory(pages())
        assertEquals(start,start.commit(listOf(PdfPageEdit("bad",0))))
        assertEquals(start,start.commit(listOf(PdfPageEdit("bad",1,360))))
        assertEquals(start,start.commit(listOf(PdfPageEdit("",1))))
        assertEquals(start,start.commit(listOf(PdfPageEdit("same",1),PdfPageEdit("same",2))))
    }
    @Test fun legacyOrderAndRotationTranslateExactly() {
        val pages=initialPageEdits(3,"3,1","1",-90)
        assertEquals(listOf(3,1),pages.map { it.sourcePage })
        assertEquals(listOf(0,270),pages.map { it.rotationDegrees })
        assertEquals(2,pages.map { it.instanceId }.toSet().size)
    }
    @Test fun legacyAllPageRotationIsRetained() {
        assertEquals(listOf(90,90,90),initialPageEdits(3,"","all",90).map { it.rotationDegrees })
    }
    @Test fun transformRoundTripsPhysicalCoordinatesRegardlessOfLocale() {
        for(turns in 0..3) for(x in listOf(false,true)) for(y in listOf(false,true)) {
            val p=VisualTransform(turns,x,y)
            val display=p.toDisplay(.17f,.73f)
            val original=p.toSource(display.first,display.second)
            assertEquals(.17f,original.first,.0001f)
            assertEquals(.73f,original.second,.0001f)
        }
    }
    @Test fun transformOrderIsFlipThenClockwiseRotation() {
        val display=VisualTransform(turns=1,flipX=true,flipY=false).toDisplay(.2f,.7f)
        assertEquals(.3f,display.first,.0001f)
        assertEquals(.8f,display.second,.0001f)
        assertEquals(.2f,VisualTransform(turns=2,flipX=true,flipY=true).toDisplay(.2f,.7f).first,.0001f)
    }
    @Test fun aspectLockSurvivesCornerDragAndBounds() {
        val start=VisualRect(.2f,.2f,.8f,.8f)
        val next=start.draggedLocked(VisualHandle.BOTTOM_RIGHT,.8f,.1f,1f)
        assertEquals(next.right-next.left,next.bottom-next.top,.0001f)
        assertTrue(next.right<=1f && next.bottom<=1f)
    }
}
