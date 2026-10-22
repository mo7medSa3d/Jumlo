package com.jumlo.app.ui.util

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomPanMathTest {
    @Test fun unzoomedContentCannotPan() {
        assertEquals(Offset.Zero, ZoomPanMath.maxOffset(1f, 400f, 600f))
        assertEquals(Offset.Zero, ZoomPanMath.clampOffset(Offset(120f, 90f), 1f, 400f, 600f))
    }

    @Test fun panIsBoundedByHalfTheScaledOverflow() {
        assertEquals(Offset(200f, 300f), ZoomPanMath.maxOffset(2f, 400f, 600f))
        // Overshooting on either axis is pulled back to the edge, not mirrored.
        assertEquals(Offset(200f, -300f), ZoomPanMath.clampOffset(Offset(900f, -900f), 2f, 400f, 600f))
        assertEquals(Offset(-40f, 60f), ZoomPanMath.clampOffset(Offset(-40f, 60f), 2f, 400f, 600f))
    }

    @Test fun unknownViewportKeepsThePreviewStill() {
        assertEquals(Offset.Zero, ZoomPanMath.maxOffset(3f, 0f, 0f))
        assertEquals(Offset.Zero, ZoomPanMath.clampOffset(Offset(50f, 50f), 3f, 0f, 0f))
    }

    @Test fun fitScaleNeverZoomsOutBelowOne() {
        // A page twice as small as the viewport is scaled up until it fits.
        assertEquals(4f, ZoomPanMath.fitScale(100f, 100f, 400f, 600f, 5f), 0.001f)
        // A page much larger than the viewport is never scaled below 1:1.
        assertEquals(1f, ZoomPanMath.fitScale(4000f, 4000f, 400f, 600f, 5f), 0.001f)
        // The scale is capped at the maximum zoom.
        assertEquals(5f, ZoomPanMath.fitScale(80f, 60f, 400f, 600f, 5f), 0.001f)
        // Unknown geometry falls back to 1:1.
        assertEquals(1f, ZoomPanMath.fitScale(0f, 0f, 400f, 600f, 5f), 0.001f)
    }

    @Test fun frictionDecaysAndIsFrameRateIndependent() {
        val perFrame = ZoomPanMath.decay(1000f, 1f / 60f)
        val perSecond = ZoomPanMath.decay(1000f, 1f)
        assertTrue(perFrame < 1000f)
        assertTrue(perSecond < perFrame)
        // Two 500ms steps must land in the same place as one 1s step.
        val split = ZoomPanMath.decay(ZoomPanMath.decay(1000f, 0.5f), 0.5f)
        assertEquals(perSecond, split, 0.0001f)
        // A zero-length frame changes nothing.
        assertEquals(1000f, ZoomPanMath.decay(1000f, 0f), 0.0001f)
    }
}
