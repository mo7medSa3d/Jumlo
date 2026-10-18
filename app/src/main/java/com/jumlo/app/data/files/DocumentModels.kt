package com.jumlo.app.data.files

import com.jumlo.app.core.SafeNames
import java.text.Normalizer

data class DocumentEntry(
    val uri: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val isDirectory: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    private val providerRenameSupported: Boolean = false,
    private val providerDeleteSupported: Boolean = false
) {
    // A provider's capability flags do not grant this application write access.
    val canRename: Boolean get() = writable && providerRenameSupported
    val canDelete: Boolean get() = writable && providerDeleteSupported
}

data class DocumentListing(
    val treeUri: String,
    val name: String,
    val entries: List<DocumentEntry>,
    val writable: Boolean = false
)

data class DocumentRenamePreview(val name: String?, val errorCode: String?, val canConfirm: Boolean)

/** The displayed name is the exact normalized name passed to the provider writer. */
fun previewDocumentRename(listing: DocumentListing?, sourceUri: String, value: String): DocumentRenamePreview {
    val name = try {
        SafeNames.basename(value)
    } catch (_: IllegalArgumentException) {
        return DocumentRenamePreview(null, "INVALID_FILENAME", false)
    }
    val source = listing?.entries?.firstOrNull { it.uri == sourceUri }
        ?: return DocumentRenamePreview(name, "ACCESS_LOST", false)
    if (source.isDirectory || !source.canRename) {
        return DocumentRenamePreview(name, "PROVIDER_RENAME_UNAVAILABLE", false)
    }
    if (listing.entries.any { it.uri != sourceUri && sameDocumentName(it.name, name) }) {
        return DocumentRenamePreview(name, "DESTINATION_EXISTS", false)
    }
    return DocumentRenamePreview(name, null, source.name != name)
}

/** Match both case and canonical Unicode equivalence before a destructive provider rename. */
internal fun sameDocumentName(first: String, second: String): Boolean =
    Normalizer.normalize(first, Normalizer.Form.NFC)
        .equals(Normalizer.normalize(second, Normalizer.Form.NFC), ignoreCase = true)
