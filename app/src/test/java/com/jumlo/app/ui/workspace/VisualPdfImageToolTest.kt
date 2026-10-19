package com.jumlo.app.ui.workspace

import com.jumlo.app.core.CapabilityPolicy
import com.jumlo.app.data.processing.PdfSignatureSettings
import com.jumlo.app.model.WorkspaceDraft
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VisualPdfImageToolTest {
    @Test fun newImageOverlayToolHasUsableDefaultRegion() {
        val draft=WorkspaceDraft.forTool("pdf_image",emptyList())
        assertEquals("Document.pdf",draft.outputName)
        val box=VisualRect(
            draft.cropLeft.toFloat(), draft.cropTop.toFloat(),
            draft.cropRight.toFloat(), draft.cropBottom.toFloat()
        ).valid()
        assertTrue(box.left > 0f && box.right < 1f)
        assertTrue(box.top > 0f && box.bottom < 1f)
        assertTrue(box.right > box.left && box.bottom > box.top)
    }

    @Test fun overlayUsesExistingValidatedPdfImageStampSettings() {
        val draft=WorkspaceDraft.forTool("pdf_image",listOf("pdf-id","image-id"))
        val settings=Json.decodeFromString<PdfSignatureSettings>(draft.executionSettings())
        assertEquals(1,settings.page)
        assertEquals(0.18,settings.left,.00001)
        assertEquals(0.78,settings.bottom,.00001)
        assertTrue(CapabilityPolicy.canProcess("pdf_image"))
    }

    @Test fun compressionOpensInBalancedPresetByDefault() {
        val settings=WorkspaceDraft.forTool("pdf_compress",listOf("pdf-id"))
        assertEquals("180",settings.num1)
        assertEquals("75",settings.quality)
        assertNotNull(settings.executionSettings())
    }
}
