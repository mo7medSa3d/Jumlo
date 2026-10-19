package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

class PdfOverlaySourcePolicyTest {
    @Test fun pdfFirstAndImageFirstProduceIdenticalCanonicalOrder() {
        val pdf = "document" to "application/pdf"
        val image = "picture" to "image/png"
        assertEquals(listOf("document","picture"), PdfOverlaySourcePolicy.ordered(listOf(pdf,image)))
        assertEquals(listOf("document","picture"), PdfOverlaySourcePolicy.ordered(listOf(image,pdf)))
    }

    @Test fun rejectsDuplicateOrUnsupportedSources() {
        for (sources in listOf(
            listOf("same" to "application/pdf","same" to "image/png"),
            listOf("a" to "application/pdf","b" to "application/pdf"),
            listOf("a" to "application/pdf","b" to "text/plain"),
            listOf("a" to "image/jpeg"),
            emptyList()
        )) {
            try {
                PdfOverlaySourcePolicy.ordered(sources)
                fail("Expected validation error for $sources")
            } catch (expected:IllegalArgumentException) {
                assertEquals("PDF_AND_IMAGE_REQUIRED", expected.message)
            }
        }
    }

    @Test fun imagesAreCenteredAndKeepOriginalAspectRatio() {
        val placement=PdfImagePlacementPolicy.fit(10f,20f,200f,100f,300,600)
        assertEquals(85f,placement.x,.001f)
        assertEquals(20f,placement.y,.001f)
        assertEquals(50f,placement.width,.001f)
        assertEquals(100f,placement.height,.001f)
        assertEquals(300f/600f,placement.width/placement.height,.001f)
    }

    @Test fun landscapeImagesFitTallSlotsWithoutDistortion() {
        val result=PdfImagePlacementPolicy.fit(0f,0f,100f,200f,1600,900)
        assertEquals(100f,result.width,.01f)
        assertEquals(56.25f,result.height,.01f)
        assertEquals(71.875f,result.y,.01f)
    }

    @Test fun invalidSlotsCannotReachPdfBackend() {
        for (bad in listOf(0f,-1f,Float.NaN)) {
            try {
                PdfImagePlacementPolicy.fit(0f,0f,bad,100f,100,100)
                fail("Expected invalid placement")
            } catch (expected:IllegalArgumentException) {
                assertEquals("INVALID_IMAGE_PLACEMENT",expected.message)
            }
        }
    }
}
