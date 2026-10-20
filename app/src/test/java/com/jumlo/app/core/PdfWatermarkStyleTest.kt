package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

class PdfWatermarkStyleTest {
    @Test fun defaultsRemainValid() {
        assertEquals(42,PdfWatermarkStyle().fontSizePt)
        assertEquals(28,PdfWatermarkStyle().opacityPercent)
    }
    @Test fun acceptsRightToLeftPlacementAndTiling() {
        val s=PdfWatermarkStyle(.17,.83,56,47,-45,true,"#2563EB")
        assertTrue(s.tiled)
        assertEquals(.83,s.centerY,0.00001)
    }
    @Test fun invalidPositionsAndColorsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfWatermarkStyle(centerX=Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfWatermarkStyle(centerY=1.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfWatermarkStyle(colorHex="#XYZXYZ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfWatermarkStyle(opacityPercent=0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfWatermarkStyle(fontSizePt=500)
        }
    }
}
