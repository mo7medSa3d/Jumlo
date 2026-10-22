package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

class ScanGeometryTest {
    @Test fun `landscape photo in portrait viewport maps handles to displayed pixels`() {
        val fit=ScanGeometry.fit(400f,200f,300f,340f)
        assertEquals(0f,fit.left,.001f); assertEquals(95f,fit.top,.001f)
        assertEquals(300f,fit.width,.001f); assertEquals(150f,fit.height,.001f)
        assertEquals(ScanPoint(.5f,.5f),fit.normalized(150f,170f))
        assertEquals(ScanPoint(0f,0f),fit.normalized(0f,0f))
    }
    @Test fun `cropping rejects crossed and degenerate corners`() {
        val quad=listOf(ScanPoint(.1f,.1f),ScanPoint(.9f,.1f),ScanPoint(.9f,.9f),ScanPoint(.1f,.9f))
        assertTrue(ScanGeometry.valid(quad))
        assertFalse(ScanGeometry.valid(listOf(quad[0],quad[2],quad[1],quad[3])))
        assertFalse(ScanGeometry.valid(List(4) {ScanPoint(.5f,.5f)}))
    }
}
