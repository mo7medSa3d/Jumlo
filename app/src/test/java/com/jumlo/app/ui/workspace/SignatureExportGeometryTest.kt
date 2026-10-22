package com.jumlo.app.ui.workspace

import com.jumlo.app.ui.scan.signatureExportGeometry
import org.junit.Assert.*
import org.junit.Test

class SignatureExportGeometryTest {
    @Test fun portraitAndWidePadsKeepTheDrawnSignatureProportions() {
        for((width,height) in listOf(280 to 140,720 to 140,180 to 240)) {
            val out=signatureExportGeometry(width,height)
            // Raster bounds round to whole pixels, while the stroke uses one
            // uniform scale in both directions.
            assertEquals(width*out.scale,out.width.toFloat(),.5f)
            assertEquals(height*out.scale,out.height.toFloat(),.5f)
            assertTrue(out.width<=600 && out.height<=600)
            assertEquals(out.width.toFloat()/width,out.scale,.001f)
        }
    }
}
