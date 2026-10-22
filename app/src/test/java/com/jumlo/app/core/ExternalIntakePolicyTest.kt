package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalIntakePolicyTest {
    @Test fun `shared PDF opens page editor and multiple PDFs open merge`() {
        assertEquals("pdf_04", ExternalIntakePolicy.toolFor(listOf("application/pdf")))
        assertEquals("pdf_03", ExternalIntakePolicy.toolFor(listOf("application/pdf", "APPLICATION/PDF")))
    }
    @Test fun `shared photos enter image to PDF and mixed documents stay compatible`() {
        assertEquals("pdf_02", ExternalIntakePolicy.toolFor(listOf("image/jpeg", "image/png")))
        assertEquals("file_07", ExternalIntakePolicy.toolFor(listOf("image/jpeg", "application/pdf")))
        assertEquals("file_08", ExternalIntakePolicy.toolFor(listOf("application/zip")))
        assertNull(ExternalIntakePolicy.toolFor(emptyList()))
    }
    @Test fun `notification destinations accept only canonical opaque job ids`() {
        val id = "b7c51f23-7c29-47c8-b742-1f71204c29d3"
        assertEquals(id, ExternalIntakePolicy.jobId(id))
        assertNull(ExternalIntakePolicy.jobId("../../source.pdf"))
        assertNull(ExternalIntakePolicy.jobId(null))
    }
}
