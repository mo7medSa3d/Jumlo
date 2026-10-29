package com.jumlo.app.core

import com.jumlo.app.data.jobs.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityContractTest {
    @Test fun allRegisteredCapabilitiesAreVisibleAndHaveExplicitKinds() {
        val registered = CapabilityRegistry.tools
        assertTrue("Jumlo must retain all registered capabilities", registered.size >= 45)
        assertEquals("Capability ID must be unique", registered.size, registered.map { it.id }.distinct().size)
        registered.forEach { tool ->
            val policy = CapabilityRegistry.availability(tool.id)
            assertTrue("${tool.id} unexpectedly hidden by policy", policy.visible)
            assertTrue("${tool.id} unexpectedly disabled by policy", policy.enabled)
            assertTrue("${tool.id} has no declared route/handler", policy.kind != CapabilityKind.UNAVAILABLE)
        }
    }

    @Test fun noGhostPdfRoutesCanEnqueueUnavailableOperations() {
        listOf("pdf_compare", "pdf_redact", "missing_tool").forEach { id ->
            assertEquals("$id is not a registry operation", CapabilityKind.UNAVAILABLE, CapabilityRegistry.availability(id).kind)
            assertFalse("$id may not enqueue work", CapabilityRegistry.canProcess(id))
        }
    }

    @Test fun navigationOnlyCapabilitiesCannotBeSubmittedAsWork() {
        listOf("pdf_01", "scan_doc", "file_02", "file_trash", "dl_settings").forEach { id ->
            assertEquals("$id must remain navigation-only", CapabilityKind.NAVIGATION, CapabilityRegistry.availability(id).kind)
            assertFalse("$id must never reach an operation handler", CapabilityRegistry.canProcess(id))
        }
    }

    @Test fun representativeRealOperationsRemainEnabled() {
        listOf("pdf_03", "pdf_protect", "pdf_clean", "pdf_to_docx", "file_drive",
            "dl_media", "dl_02", "img_04", "img_compare", "sha256").forEach { id ->
            assertEquals("$id lost its handler policy", CapabilityKind.PROCESSING, CapabilityRegistry.availability(id).kind)
            assertTrue("$id must remain executable", CapabilityRegistry.canProcess(id))
        }
    }
}
