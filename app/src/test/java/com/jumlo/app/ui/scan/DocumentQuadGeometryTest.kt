package com.jumlo.app.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentQuadGeometryTest {
    @Test fun validConvexClockwiseQuadAcceptedAndCrossedBowtieRejected() {
        val good = NormalizedQuad(
            tl = QuadPoint(0.1f, 0.1f),
            tr = QuadPoint(0.9f, 0.12f),
            br = QuadPoint(0.88f, 0.92f),
            bl = QuadPoint(0.12f, 0.88f)
        )
        assertTrue(DocumentQuadGeometry.isValid(good))

        val bowtie = NormalizedQuad(
            tl = QuadPoint(0.1f, 0.1f),
            tr = QuadPoint(0.9f, 0.9f),
            br = QuadPoint(0.9f, 0.1f),
            bl = QuadPoint(0.1f, 0.9f)
        )
        assertFalse(DocumentQuadGeometry.isValid(bowtie))

        val outOfBounds = NormalizedQuad(
            tl = QuadPoint(-0.1f, 0.1f),
            tr = QuadPoint(0.9f, 0.1f),
            br = QuadPoint(0.9f, 0.9f),
            bl = QuadPoint(0.1f, 0.9f)
        )
        assertFalse(DocumentQuadGeometry.isValid(outOfBounds))
    }

    @Test fun targetDimensionsPreserveAspectRatioAndMaxEdgeCeiling() {
        val (w, h) = DocumentQuadGeometry.targetDimensions(NormalizedQuad.FULL, 4000, 3000, maxEdge = 2400)
        assertEquals(2400, w)
        assertEquals(1800, h)
    }

    @Test fun detectQuadFromLumaFindsHighContrastPageAndDoesNotSelfCropFlatScene() {
        val w = 100
        val h = 100
        val lum = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x in 15..84 && y in 12..87) 235 else 25
        }
        val detected = DocumentQuadGeometry.detectQuadFromLuma(lum, w, h)
        assertFalse("Strong outer document border should snap inward cleanly", detected.isFullFrame())
        assertTrue(detected.tl.x in 0.10f..0.20f)
        assertTrue(detected.tr.x in 0.80f..0.90f)
        assertTrue(detected.tl.y in 0.08f..0.18f)
        assertTrue(detected.bl.y in 0.82f..0.92f)

        val flatLum = IntArray(w * h) { 180 }
        assertTrue(
            "Flat frame without strong perimeter edge must fall back to full frame",
            DocumentQuadGeometry.detectQuadFromLuma(flatLum, w, h).isFullFrame()
        )
    }
}
