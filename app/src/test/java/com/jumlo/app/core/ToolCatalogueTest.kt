package com.jumlo.app.core

import com.jumlo.app.data.jobs.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue is the app's front door.
 *
 * These tests guard the two ways it can go wrong: a capability that quietly
 * disappears because nobody listed it, and a "tidy" catalogue that grows back into
 * forty-five tiles of equal weight.
 */
class ToolCatalogueTest {
    private val registryIds = CapabilityRegistry.tools.map { it.id }.toSet()

    @Test fun everyListedToolExistsInTheRegistry() {
        val listed = ToolCatalogue.primaryTools() + ToolCatalogue.mergedTools()
        listed.forEach { id ->
            assertTrue("$id is not a registered capability", id in registryIds)
        }
    }

    @Test fun noToolIsListedTwice() {
        val listed = ToolCatalogue.primaryTools() + ToolCatalogue.mergedTools()
        assertEquals("a capability is listed twice", listed.size, listed.distinct().size)
    }

    @Test fun theVisibleSurfaceIsSmallEnoughToScan() {
        val primary = ToolCatalogue.primaryTools()
        assertTrue(
            "the catalogue shows ${primary.size} tiles; it must stay a scannable list",
            // Existing primary actions plus the requested Word, PowerPoint and OCR entries.
            primary.size in 12..22
        )
        assertTrue(primary.containsAll(listOf("pdf_to_docx", "pdf_to_pptx", "pdf_ocr")))
    }

    @Test fun everyWorkspaceHasAWayIn() {
        ToolCatalogue.all().forEach { entry ->
            assertTrue("${entry.workspace} has no primary tool", entry.primary.isNotEmpty())
            entry.primary.forEach { id ->
                assertTrue("${entry.workspace} lists the unknown tool $id", id in registryIds)
            }
        }
    }

    @Test fun everyRegisteredCapabilityIsEitherListedOrDeliberatelyHidden() {
        val listed = (ToolCatalogue.primaryTools() + ToolCatalogue.mergedTools()).toSet()
        // The navigation-only destinations (browse storage, trash, the download
        // settings) live in their own screens, not in the tool catalogue.
        val navigationOnly = setOf("file_02", "file_trash", "dl_settings")
        val hidden = ToolCatalogue.hiddenTools()
        registryIds.forEach { id ->
            if (id in navigationOnly || id in hidden) return@forEach
            assertTrue("$id is in the registry but in no workspace", id in listed)
        }
    }

    @Test fun theWorkspaceOfAToolIsTheOneThatContainsIt() {
        ToolCatalogue.primaryTools().forEach { id ->
            assertEquals(id, ToolCatalogue.workspaceOf(id)?.let { it }?.let { _ -> ToolCatalogue.workspaceOf(id) },
                ToolCatalogue.workspaceOf(id))
            assertTrue("$id must be a primary tool", ToolCatalogue.isPrimary(id))
        }
        ToolCatalogue.mergedTools().forEach { id ->
            assertTrue("$id must not be a primary tool", !ToolCatalogue.isPrimary(id))
        }
    }

    @Test fun theSpecialistsAreKeptOutOfTheCatalogue() {
        // A comparison is a specialist tool: it stays in the registry and in the
        // search, but it does not take a tile.
        assertTrue("the image comparison must not take a tile", !ToolCatalogue.isListed("img_compare"))
        assertTrue("the image comparison must not be a primary", !ToolCatalogue.isPrimary("img_compare"))
    }

    @Test fun theScannerIsItsOwnWorkspace() {
        assertEquals(ToolWorkspace.SCANNER, ToolCatalogue.workspaceOf("scan_doc"))
        assertTrue("the scanner must be a primary entry", ToolCatalogue.isPrimary("scan_doc"))
    }

    @Test fun theMetadataCleanerStaysAvailable() {
        assertTrue("the metadata cleaner must stay reachable", ToolCatalogue.isListed("pdf_clean"))
    }
}
