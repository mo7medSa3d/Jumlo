package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

class ImagePdfLayoutTest {
    @Test fun `fit respects physical margins and centers a landscape image`() {
        val p=ImagePdfLayout.resolve(2000,1000,PdfPageSize(600f,800f),10,false,false)
        assertEquals(10*72f/25.4f,p.marginPt,.001f)
        assertEquals(p.marginPt,p.leftPt,.001f)
        assertEquals(p.widthPt/2,p.heightPt,.001f)
        assertEquals(800f/2,p.topPt+p.heightPt/2,.001f)
    }
    @Test fun `fill exceeds one clip edge and auto margins retain narrow image behavior`() {
        val p=ImagePdfLayout.resolve(2000,1000,PdfPageSize(600f,800f),10,false,true)
        assertTrue(p.leftPt<p.marginPt)
        assertEquals(p.marginPt,p.topPt,.001f)
        assertEquals(8f,ImagePdfLayout.resolve(100,842,PdfPageSize(100f,842f),100,true,false).marginPt,.001f)
    }
}
