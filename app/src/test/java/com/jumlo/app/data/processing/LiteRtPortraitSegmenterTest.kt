package com.jumlo.app.data.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtPortraitSegmenterTest {
    @Test fun upsampleMaskBilinearInterpolatesSmoothlyBetweenCorners() {
        val src = floatArrayOf(
            0.0f, 1.0f,
            0.0f, 1.0f
        )
        val out = LiteRtPortraitSegmenter.upsampleMaskBilinear(src, srcW = 2, srcH = 2, dstW = 5, dstH = 5)
        assertEquals(25, out.size)
        // Left edge stays 0, middle interpolated cleanly to 0.5, right edge stays 1.
        assertEquals(0.0f, out[0], 0.001f)
        assertEquals(0.5f, out[2], 0.001f)
        assertEquals(1.0f, out[4], 0.001f)
        assertEquals(0.5f, out[2 * 5 + 2], 0.001f)
    }
}
