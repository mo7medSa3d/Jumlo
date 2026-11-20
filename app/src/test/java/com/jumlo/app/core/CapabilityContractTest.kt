package com.jumlo.app.core

import com.jumlo.app.data.jobs.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityContractTest {
    @Test fun allRegisteredCapabilitiesAreVisibleAndHaveExplicitKinds() {
        val registered = CapabilityRegistry.tools
        assertEquals("Jumlo must expose the curated 30 capabilities", 30, registered.size)
        assertEquals("Capability ID must be unique", registered.size, registered.map { it.id }.distinct().size)
        registered.forEach { tool ->
            val policy = CapabilityRegistry.availability(tool.id)
            assertTrue("${tool.id} unexpectedly hidden by policy", policy.visible)
            assertTrue("${tool.id} unexpectedly disabled by policy", policy.enabled)
            assertTrue("${tool.id} has no declared route/handler", policy.kind != CapabilityKind.UNAVAILABLE)
        }
    }

    @Test fun unknownOperationsCannotBeEnqueued() {
        assertEquals(CapabilityKind.UNAVAILABLE, CapabilityRegistry.availability("missing_tool").kind)
        assertFalse("unknown operations must fail closed", CapabilityRegistry.canProcess("missing_tool"))
    }

    @Test fun retiredToolsFailClosedAsUnavailable() {
        val retired = listOf(
            "pdf_annotate", "pdf_image", "pdf_metadata", "pdf_clean", "pdf_forms", "pdf_compare", "pdf_redact",
            "img_exif", "img_adjust", "img_target", "img_compare", "img_stitch",
            "file_duplicates", "file_trash", "sha256", "file_drive"
        )
        retired.forEach { id ->
            assertEquals("$id must be unavailable", CapabilityKind.UNAVAILABLE, CapabilityRegistry.availability(id).kind)
            assertFalse("$id must not be executable", CapabilityRegistry.canProcess(id))
        }
    }

    @Test fun implementedPdfSpecialistsAreRegisteredAsProcessingCapabilities() {
        listOf("office_to_pdf", "pdf_to_docx", "pdf_to_pptx", "pdf_ocr").forEach { id ->
            assertEquals("$id must expose its implemented processing capability", CapabilityKind.PROCESSING, CapabilityRegistry.availability(id).kind)
            assertTrue("$id must be executable", CapabilityRegistry.canProcess(id))
        }
    }

    @Test fun navigationOnlyCapabilitiesCannotBeSubmittedAsWork() {
        listOf("pdf_01", "scan_doc").forEach { id ->
            assertEquals("$id must remain navigation-only", CapabilityKind.NAVIGATION, CapabilityRegistry.availability(id).kind)
            assertFalse("$id must never reach an operation handler", CapabilityRegistry.canProcess(id))
        }
    }

    @Test fun internalDownloadIdsRemainExecutableAfterCatalogueDedup() {
        // dl_02/dl_03 are no longer user-visible tiles but their handlers stay live
        // for direct-link and batch job routing in AppViewModel.
        listOf("dl_media", "dl_02", "dl_03").forEach { id ->
            assertTrue("$id must remain executable", CapabilityRegistry.canProcess(id))
        }
    }

    @Test fun representativeRealOperationsRemainEnabled() {
        listOf("pdf_03", "pdf_protect", "pdf_to_docx", "file_encrypted_zip",
            "dl_media", "dl_02", "img_04", "img_join").forEach { id ->
            assertEquals("$id lost its handler policy", CapabilityKind.PROCESSING, CapabilityRegistry.availability(id).kind)
            assertTrue("$id must remain executable", CapabilityRegistry.canProcess(id))
        }
    }
}
