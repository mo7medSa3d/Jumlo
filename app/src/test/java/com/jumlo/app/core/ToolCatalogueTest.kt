package com.jumlo.app.core

import com.jumlo.app.data.jobs.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards registry reachability and keeps the default catalogue intentionally scannable. */
class ToolCatalogueTest {
    private val registryIds = CapabilityRegistry.tools.map { it.id }.toSet()

    @Test fun everyListedToolExistsInTheRegistry() {
        val listed = ToolCatalogue.primaryTools() + ToolCatalogue.mergedTools()
        listed.forEach { id -> assertTrue("$id is not a registered capability", id in registryIds) }
    }

    @Test fun noToolIsListedTwice() {
        val listed = ToolCatalogue.primaryTools() + ToolCatalogue.mergedTools()
        assertEquals("a capability is listed twice", listed.size, listed.distinct().size)
    }

    @Test fun theVisibleSurfaceIsSmallEnoughToScan() {
        val primary = ToolCatalogue.primaryTools()
        assertEquals("the catalogue must expose all 29 primary tiles", 29, primary.size)
        assertTrue(
            "the catalogue shows ${primary.size} tiles; it must stay a scannable list",
            primary.size in 12..30
        )
        assertTrue(
            primary.containsAll(
                listOf("pdf_to_docx", "pdf_to_pptx", "pdf_ocr", "pdf_05", "pdf_long_image", "img_04", "file_07", "dl_media", "scan_id_card")
            )
        )
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
        val hidden = ToolCatalogue.hiddenTools()
        registryIds.forEach { id ->
            if (id in hidden) return@forEach
            assertTrue("$id is in the registry but in no workspace", id in listed)
        }
    }

    @Test fun theWorkspaceOfAToolIsTheOneThatContainsIt() {
        ToolCatalogue.all().forEach { entry ->
            entry.primary.forEach { id ->
                assertEquals("$id has the wrong workspace", entry.workspace, ToolCatalogue.workspaceOf(id))
                assertTrue("$id must be a primary tool", ToolCatalogue.isPrimary(id))
            }
            entry.merged.forEach { id ->
                assertEquals("$id has the wrong merged workspace", entry.workspace, ToolCatalogue.workspaceOf(id))
                assertTrue("$id must not be a primary tool", !ToolCatalogue.isPrimary(id))
            }
        }
        assertEquals(null, ToolCatalogue.workspaceOf("missing_tool"))
    }

    @Test fun retiredToolsAreAbsentFromRegistryCatalogueAndSearch() {
        val retired = listOf(
            "pdf_annotate", "pdf_image", "pdf_metadata", "pdf_clean", "pdf_forms", "pdf_compare", "pdf_redact",
            "img_exif", "img_adjust", "img_target", "img_compare", "img_stitch",
            "file_duplicates", "file_trash", "sha256", "file_drive"
        )
        for (id in retired) {
            assertTrue("$id must not remain in registry", id !in registryIds)
            assertTrue("$id must not be listed", !ToolCatalogue.isListed(id))
            assertTrue("$id must not be searchable", !ToolCatalogue.isSearchable(id))
        }
        for (id in listOf("pdf_03", "pdf_05", "pdf_long_image", "pdf_ocr", "scan_doc", "scan_id_card", "dl_media", "img_04", "file_07", "office_to_pdf")) {
            assertTrue("$id must remain reachable from the Tools grid", ToolCatalogue.isListed(id))
            assertTrue("$id must remain searchable", ToolCatalogue.isSearchable(id))
        }
    }

    @Test fun allRestoredToolsAreListedAndSearchable() {
        val restored = listOf("img_01", "img_03", "file_encrypted_zip", "dl_02", "dl_03", "pdf_06", "pdf_07", "pdf_long_image")
        assertTrue(ToolCatalogue.hiddenTools().isEmpty())
        restored.forEach { id ->
            assertEquals("$id must resolve to itself", id, ToolCatalogue.canonicalId(id))
            assertTrue("$id must be listed", ToolCatalogue.isListed(id))
            assertTrue("$id must be searchable", ToolCatalogue.isSearchable(id))
            assertTrue("$id must be primary", ToolCatalogue.isPrimary(id))
        }
        assertTrue(ToolCatalogue.searchAliases("pdf_05").contains("extract pages", ignoreCase = true))
        assertTrue(ToolCatalogue.searchAliases("pdf_05").contains("استخراج صفحات"))
        assertTrue(ToolCatalogue.searchAliases("pdf_long_image").contains("pdf to images", ignoreCase = true))
        assertTrue(ToolCatalogue.searchAliases("pdf_long_image").contains("تحويل صور"))
    }

    @Test fun downloadsExposeMediaAndDirectTools() {
        val downloads = ToolCatalogue.entry(ToolWorkspace.DOWNLOADS)!!
        assertEquals(listOf("dl_media", "dl_02", "dl_03"), downloads.primary)
        assertTrue("direct/batch tools are primary tiles", downloads.merged.isEmpty())
        for (id in listOf("dl_media", "dl_02", "dl_03")) {
            assertTrue("$id must be listed", ToolCatalogue.isListed(id))
            assertTrue("$id must be searchable", ToolCatalogue.isSearchable(id))
        }
    }

    @Test fun theScannerIsItsOwnWorkspace() {
        assertEquals(ToolWorkspace.SCANNER, ToolCatalogue.workspaceOf("scan_doc"))
        assertTrue("the scanner must be a primary entry", ToolCatalogue.isPrimary("scan_doc"))
    }
}
