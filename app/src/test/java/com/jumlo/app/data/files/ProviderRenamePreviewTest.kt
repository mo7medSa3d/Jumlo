package com.jumlo.app.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRenamePreviewTest {
    private val source = document("source", "original.pdf")
    private fun document(id: String, name: String, writable: Boolean = true, rename: Boolean = true) =
        DocumentEntry(id, name, "application/pdf", 16, false, true, writable, rename, true)
    private fun listing(vararg others: DocumentEntry) =
        DocumentListing("tree", "Documents", listOf(source) + others, true)
    private fun DocumentListing?.previewRename(sourceUri: String, name: String) =
        previewDocumentRename(this, sourceUri, name)

    @Test fun validNameIsTheExactNameAuthorizedForSubmission() {
        val preview = listing().previewRename(source.uri, "final.pdf")
        assertEquals("final.pdf", preview.name)
        assertNull(preview.errorCode)
        assertTrue(preview.canConfirm)
    }

    @Test fun previewShowsTheNormalizedNameUsedByTheWriter() {
        val preview = listing().previewRename(source.uri, "Cafe\u0301.pdf")
        assertEquals("Café.pdf", preview.name)
        assertTrue(preview.canConfirm)
    }

    @Test fun unchangedNameDoesNotOfferANoopCommit() {
        val preview = listing().previewRename(source.uri, source.name)
        assertNull(preview.errorCode)
        assertFalse(preview.canConfirm)
    }

    @Test fun sourceIsExcludedFromCaseOnlyRenameConflicts() {
        val preview = listing().previewRename(source.uri, "ORIGINAL.pdf")
        assertTrue(preview.canConfirm)
    }

    @Test fun caseInsensitiveSiblingCollisionBlocksCommit() {
        val preview = listing(document("other", "Report.PDF")).previewRename(source.uri, "report.pdf")
        assertEquals("report.pdf", preview.name)
        assertEquals("DESTINATION_EXISTS", preview.errorCode)
        assertFalse(preview.canConfirm)
    }

    @Test fun canonicallyEquivalentSiblingCollisionBlocksCommit() {
        val preview = listing(document("other", "Cafe\u0301.pdf")).previewRename(source.uri, "CAFÉ.pdf")
        assertEquals("DESTINATION_EXISTS", preview.errorCode)
        assertFalse(preview.canConfirm)
    }

    @Test fun unsafeAndOversizedNamesCannotBeCommitted() {
        listOf("", " ", ".", "..", "../escape.pdf", "folder/name.pdf", "file\\name.pdf",
            "file:name.pdf", "file\u0000.pdf", "trailing. ", "é".repeat(101)).forEach { name ->
            val preview = listing().previewRename(source.uri, name)
            assertEquals(name, "INVALID_FILENAME", preview.errorCode)
            assertFalse(name, preview.canConfirm)
        }
    }

    @Test fun readOnlySourceCannotBeRenamedEvenWhenProviderSupportsIt() {
        val readOnly = document(source.uri, source.name, writable = false)
        val preview = DocumentListing("tree", "Documents", listOf(readOnly), true)
            .previewRename(source.uri, "new.pdf")
        assertEquals("PROVIDER_RENAME_UNAVAILABLE", preview.errorCode)
        assertFalse(preview.canConfirm)
    }

    @Test fun changedProviderCapabilityBlocksPendingRename() {
        val noRename = document(source.uri, source.name, rename = false)
        val preview = DocumentListing("tree", "Documents", listOf(noRename), true)
            .previewRename(source.uri, "new.pdf")
        assertEquals("PROVIDER_RENAME_UNAVAILABLE", preview.errorCode)
        assertFalse(preview.canConfirm)
    }

    @Test fun changedTreeOrRemovedSourceBlocksPendingRename() {
        val preview = DocumentListing("new-tree", "Other", listOf(document("other", "other.pdf")), true)
            .previewRename(source.uri, "new.pdf")
        assertEquals("ACCESS_LOST", preview.errorCode)
        assertFalse(preview.canConfirm)
        val missing: DocumentListing? = null
        assertFalse(missing.previewRename(source.uri, "new.pdf").canConfirm)
    }
}
