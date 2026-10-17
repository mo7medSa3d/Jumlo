package com.jumlo.app.data.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentEntryPermissionsTest {
    private fun entry(writable: Boolean, rename: Boolean, delete: Boolean) =
        DocumentEntry("content://test/document/1", "report.pdf", "application/pdf", 16,
            false, true, writable, rename, delete)

    @Test fun readOnlyGrantHidesProviderSupportedMutations() {
        val document = entry(writable = false, rename = true, delete = true)
        assertFalse("Provider support alone cannot authorize rename", document.canRename)
        assertFalse("Provider support alone cannot authorize deletion", document.canDelete)
    }

    @Test fun writeGrantStillRequiresEachProviderCapability() {
        val renameOnly = entry(writable = true, rename = true, delete = false)
        assertTrue(renameOnly.canRename)
        assertFalse(renameOnly.canDelete)
        val deleteOnly = entry(writable = true, rename = false, delete = true)
        assertFalse(deleteOnly.canRename)
        assertTrue(deleteOnly.canDelete)
    }

    @Test fun writeGrantCannotOverrideProviderRefusal() {
        val document = entry(writable = true, rename = false, delete = false)
        assertFalse(document.canRename)
        assertFalse(document.canDelete)
    }
}
