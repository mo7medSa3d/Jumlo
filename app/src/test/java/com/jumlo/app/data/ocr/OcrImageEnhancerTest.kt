package com.jumlo.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.tan

class OcrImageEnhancerTest {
    @Test fun baselineVectorMapsToExactQuarterTurnOrientation() {
        assertEquals(0, OcrImageEnhancer.quarterTurnFromBaselineVector(100f, 5f))
        assertEquals(180, OcrImageEnhancer.quarterTurnFromBaselineVector(-100f, 3f))
        assertEquals(270, OcrImageEnhancer.quarterTurnFromBaselineVector(2f, 100f))
        assertEquals(90, OcrImageEnhancer.quarterTurnFromBaselineVector(-2f, -100f))
        assertEquals(0, OcrImageEnhancer.quarterTurnFromBaselineVector(0f, 0f))
    }

    @Test fun estimateSkewDetectsTiltedTextRowsAndStaysZeroOnLevelPages() {
        val w = 120
        val h = 120
        val level = IntArray(w * h) { 245 }
        for (y in listOf(30, 50, 70, 90)) {
            for (x in 15..105) {
                level[y * w + x] = 20
                level[(y + 1) * w + x] = 20
            }
        }
        assertEquals(0f, OcrImageEnhancer.estimateSkewDegrees(level, w, h), 0.1f)

        // Create crisp dark horizontal lines tilted by +5 degrees clockwise.
        val tilted = IntArray(w * h) { 245 }
        val slope = tan(Math.toRadians(5.0))
        for (baseY in listOf(28, 48, 68, 88)) {
            for (x in 14..106) {
                val y = (baseY + (x - 60) * slope).toInt()
                if (y in 2 until h - 2) {
                    tilted[y * w + x] = 15
                    tilted[(y + 1) * w + x] = 15
                }
            }
        }
        val correction = OcrImageEnhancer.estimateSkewDegrees(tilted, w, h)
        assertTrue("Expected deskew correction near -5 deg, got $correction", correction in -7f..-3f)
        assertTrue(abs(correction) >= 1.5f)
    }
}
