package com.jumlo.app.core

enum class ToolWorkspace(val id: String) {
    PDF("pdf"),
    SCANNER("scanner"),
    IMAGES("images"),
    FILES("files"),
    DOWNLOADS("downloads")
}

data class WorkspaceEntry(
    val workspace: ToolWorkspace,
    val primary: List<String>,
    val merged: List<String>
)

object ToolCatalogue {

    private val entries = listOf(
        WorkspaceEntry(
            ToolWorkspace.PDF,
            primary = listOf(
                "pdf_03",       // Merge PDF
                "pdf_05",       // Split PDF
                "pdf_compress", // Compress PDF
                "pdf_to_docx",  // PDF to Word (.docx)
                "pdf_to_pptx",  // PDF to PowerPoint (.pptx)
                "pdf_02",       // Image to PDF
                "pdf_04",       // Organize Pages
                "pdf_07",       // PDF to Images
                "pdf_signature" // Sign PDF
            ),
            merged = listOf(
                "pdf_01",       // PDF Viewer
                "pdf_06",       // Extract Pages
                "pdf_crop",     // Crop Pages
                "pdf_image",    // Add Image to PDF
                "pdf_forms",    // Fill PDF Form
                "pdf_annotate", // Annotate PDF
                "pdf_stamp",    // Watermark & Page Numbers
                "pdf_metadata", // PDF Metadata
                "pdf_protect",  // Password Protect
                "pdf_clean"     // Clean Metadata
            )
        ),
        WorkspaceEntry(
            ToolWorkspace.SCANNER,
            primary = listOf("scan_doc", "pdf_ocr"),
            merged = emptyList()
        ),
        WorkspaceEntry(
            ToolWorkspace.IMAGES,
            primary = listOf(
                "img_03",       // Crop & Rotate
                "img_02",       // Resize Images
                "img_04",       // Compress Images
                "img_01",       // Convert Images
                "img_join"      // Collage
            ),
            merged = listOf(
                "img_watermark",// Watermark Image
                "img_adjust",   // Adjust & Pad
                "img_target",   // Target Size
                "img_stitch",   // Stitch Screenshots
                "img_exif"      // EXIF Inspector
            )
        ),
        WorkspaceEntry(
            ToolWorkspace.FILES,
            primary = listOf(
                "file_07",            // Create ZIP
                "file_duplicates",    // Duplicate Finder
                "file_batch_rename"   // Batch Rename
            ),
            merged = listOf(
                "file_08",            // Extract ZIP
                "file_encrypted_zip", // Encrypted ZIP
                "sha256"              // SHA-256 Verify
            )
        ),
        WorkspaceEntry(
            ToolWorkspace.DOWNLOADS,
            primary = listOf("dl_media"),
            merged = listOf("dl_02", "dl_03", "dl_settings")
        )
    )

    private val byWorkspace = entries.associateBy { it.workspace }
    private val primaryIds = entries.flatMap { it.primary }.toSet()
    private val mergedIds = entries.flatMap { it.merged }.toSet()

    fun all(): List<WorkspaceEntry> = entries

    fun entry(workspace: ToolWorkspace): WorkspaceEntry? = byWorkspace[workspace]

    fun primaryTools(): List<String> = primaryIds.toList()

    fun mergedTools(): List<String> = mergedIds.toList()

    fun isPrimary(toolId: String): Boolean = toolId in primaryIds

    fun workspaceOf(toolId: String): ToolWorkspace? =
        entries.firstOrNull { toolId in it.primary || toolId in it.merged }?.workspace

    /**
     * Removed/hidden low-value or redundant tools:
     * - img_compare: pixel/dimension diff report nobody needs in a mobile PDF/downloader utility
     * - file_trash: redundant with the Files tab Trash filter
     * - file_02: redundant with the Files tab SAF folder browser
     * - webpage_pdf: unrendered HTML dump superseded by native Android browser Print-to-PDF
     * - file_drive: OAuth Drive relay superseded by Android SAF DocumentProvider + direct Drive links
     * - xf_05: placeholder Vault concept (Encrypted ZIP provides real AES-256 protection)
     */
    private val hidden = setOf("img_compare", "file_trash", "file_02", "file_drive", "xf_05", "webpage_pdf")

    fun isListed(toolId: String): Boolean =
        toolId !in hidden && (toolId in primaryIds || toolId in mergedIds)

    fun hiddenTools(): Set<String> = hidden
}
