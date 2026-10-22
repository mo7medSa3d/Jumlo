package com.jumlo.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class VisualEditGeometryTest {
    @Test fun cornersNeverCrossAndAlwaysStayOnDocument() {
        val start=VisualRect(.15f,.1f,.85f,.9f)
        val next=start.dragged(VisualHandle.TOP_LEFT,3f,3f)
        assertTrue(next.left < next.right)
        assertTrue(next.top < next.bottom)
        assertTrue(next.left >= 0f && next.bottom <= 1f)
        assertTrue(next.right-next.left >= VisualRect.MIN_EDGE-.0001f)
    }
    @Test fun dragMovesWholeRegionWithoutLosingSize() {
        val r=VisualRect(.2f,.3f,.7f,.8f)
        val next=r.dragged(VisualHandle.BODY,2f,-2f)
        assertEquals(.5f,next.right-next.left,.0001f)
        assertEquals(.5f,next.bottom-next.top,.0001f)
        assertEquals(1f,next.right,.0001f)
        assertEquals(0f,next.top,.0001f)
    }
    @Test fun nearestHandleUsesDisplayPixels() {
        val r=VisualRect(.1f,.1f,.8f,.8f)
        assertEquals(VisualHandle.TOP_LEFT,r.hitHandle(.11f,.12f,300f,500f,34f))
        assertEquals(VisualHandle.BODY,r.hitHandle(.4f,.5f,300f,500f,34f))
        assertEquals(VisualHandle.NONE,r.hitHandle(.95f,.5f,300f,500f,34f))
    }
    @Test fun aspectPresetUsesOriginalSourceDimensions() {
        val r=VisualRect.fitAspect(1600,900,1f)
        val proportion=(r.right-r.left)*1600f/((r.bottom-r.top)*900f)
        assertEquals(1f,proportion,.01f)
    }
    @Test fun invalidValuesResetToWholeImage() {
        assertEquals(VisualRect.WHOLE,VisualRect(Float.NaN,0f,1f,1f).valid())
    }

    @Test fun lockedCornerStopsAtItsOppositeCornerInsteadOfGrowingBackwards() {
        val start=VisualRect(.2f,.2f,.8f,.8f)
        val next=start.draggedLocked(VisualHandle.TOP_LEFT,2f,2f,1f)
        assertEquals(.8f,next.right,.0001f)
        assertEquals(.8f,next.bottom,.0001f)
        assertEquals(VisualRect.MIN_EDGE,next.right-next.left,.0001f)
        assertEquals(VisualRect.MIN_EDGE,next.bottom-next.top,.0001f)
    }

    @Test fun lockedDragDoesNotSnapOneEdgeAndChangeTheAspectRatio() {
        val next=VisualRect(.15f,.15f,.7f,.7f)
            .draggedWithSnap(VisualHandle.BOTTOM_RIGHT,-.203f,-.203f,1f)
        assertEquals(1f,(next.rect.right-next.rect.left)/(next.rect.bottom-next.rect.top),.0001f)
        assertEquals(.15f,next.rect.left,.0001f)
        assertEquals(.15f,next.rect.top,.0001f)
    }

    @Test fun freeRegionStillSnapsToThePageCentre() {
        val next=VisualRect(.2f,.2f,.7f,.8f)
            .draggedWithSnap(VisualHandle.BOTTOM_RIGHT,-.203f,0f,0f)
        assertEquals(.5f,next.rect.right,.0001f)
        assertTrue(next.snapped)
    }

    @Test fun portraitPresetStillMeansPortraitAfterQuarterTurn() {
        val desired=210f/297f
        val box=VisualRect.fitAspect(1600,900,sourceCropAspect(desired,1))
        val outputAspect=(box.bottom-box.top)*900/((box.right-box.left)*1600)
        assertEquals(desired,outputAspect,.001f)
    }
}
