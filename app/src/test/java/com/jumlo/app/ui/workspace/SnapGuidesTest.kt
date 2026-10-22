package com.jumlo.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snapping is what makes direct manipulation feel exact: an edge that lands on the
 * page centre or the thirds must lock onto it, and the guide has to be reported so
 * the UI can draw it and confirm the snap with a haptic tick.
 */
class SnapGuidesTest {
    private val tolerance = 0.001f

    @Test fun theGuideLinesAreTheCentreTheThirdsAndTheBorders() {
        val targets = SnapGuides.targets()
        assertEquals(0f, targets[0], tolerance)
        assertEquals(1f / 3f, targets[1], tolerance)
        assertEquals(0.5f, targets[2], tolerance)
        assertEquals(2f / 3f, targets[3], tolerance)
        assertEquals(1f, targets[4], tolerance)
    }

    @Test fun anEdgeNearTheCentreSnapsToIt() {
        val result = SnapGuides.snap(VisualRect(0.495f, 0.2f, 0.9f, 0.4f), VisualHandle.TOP_LEFT)
        assertTrue("the edge must snap", result.snapped)
        assertEquals(0.5f, result.guideX!!, tolerance)
        assertEquals(0.5f, result.rect.left, tolerance)
    }

    @Test fun anEdgeFarFromEveryGuideDoesNotSnap() {
        val result = SnapGuides.snap(VisualRect(0.2f, 0.2f, 0.4f, 0.35f), VisualHandle.TOP_LEFT)
        assertFalse("nothing is near a guide", result.snapped)
        assertNull(result.guideX)
        assertNull(result.guideY)
        assertEquals(0.2f, result.rect.left, tolerance)
    }

    @Test fun aCornerSnapsOnBothAxes() {
        val result = SnapGuides.snap(VisualRect(0.66f, 0.33f, 0.8f, 0.45f), VisualHandle.TOP_LEFT)
        assertTrue(result.snapped)
        assertEquals(2f / 3f, result.guideX!!, tolerance)
        assertEquals(1f / 3f, result.guideY!!, tolerance)
        assertEquals(2f / 3f, result.rect.left, tolerance)
        assertEquals(1f / 3f, result.rect.top, tolerance)
    }

    @Test fun aDraggedBodySnapsAsAWholeAndDoesNotJump() {
        // The body is slightly off the centre line: it must move by the small
        // correction, not jump to the nearest line.
        val rect = VisualRect(0.2f, 0.2f, 0.6f, 0.4f)
        val result = SnapGuides.snap(rect, VisualHandle.BODY)
        // The centre of this rect is 0.4, which is 0.1 from the centre line: no snap.
        assertFalse(result.snapped)
        assertEquals(rect.left, result.rect.left, tolerance)

        val near = VisualRect(0.2f, 0.2f, 0.79f, 0.4f)
        val snapped = SnapGuides.snap(near, VisualHandle.BODY)
        assertTrue("the centre is within tolerance of 0.5", snapped.snapped)
        assertEquals(0.5f, snapped.guideX!!, tolerance)
        // The whole rectangle moved by the same amount on the snapped axis.
        val deltaX = snapped.rect.left - near.left
        assertEquals(deltaX, snapped.rect.right - near.right, tolerance)
        // The other axis did not snap, so it did not move.
        assertEquals(0f, snapped.rect.top - near.top, tolerance)
    }

    @Test fun aSnappedRectangleStaysValid() {
        val result = SnapGuides.snap(VisualRect(0.98f, 0.98f, 0.99f, 0.99f), VisualHandle.BOTTOM_RIGHT)
        val rect = result.rect
        assertTrue("left must stay inside the page", rect.left >= 0f)
        assertTrue("right must stay inside the page", rect.right <= 1f)
        assertTrue("the rectangle must keep a minimum edge", rect.right - rect.left >= VisualRect.MIN_EDGE - 0.0001f)
    }

    @Test fun draggingTheRightEdgeDoesNotMoveTheLeftOne() {
        val rect = VisualRect(0.2f, 0.2f, 0.49f, 0.4f)
        val result = SnapGuides.snap(rect, VisualHandle.BOTTOM_RIGHT)
        assertEquals(0.2f, result.rect.left, tolerance)
        assertEquals(0.5f, result.rect.right, tolerance)
    }

    @Test fun aSnappedBodyReportsTheVerticalGuideOnlyWhenItSnapped() {
        val horizontalOnly = SnapGuides.snap(VisualRect(0.2f, 0.2f, 0.79f, 0.4f), VisualHandle.BODY)
        assertNotNull(horizontalOnly.guideX)
        assertNull(horizontalOnly.guideY)
    }

    @Test fun aToleranceWiderThanTheGapStillSnaps() {
        val far = VisualRect(0.4f, 0.2f, 0.9f, 0.4f)
        val loose = SnapGuides.snap(far, VisualHandle.TOP_LEFT, tolerance = 0.2f)
        assertTrue("a loose tolerance must still snap", loose.snapped)
        val tight = SnapGuides.snap(far, VisualHandle.TOP_LEFT, tolerance = 0.001f)
        assertFalse("a tight tolerance must not snap", tight.snapped)
    }
}
