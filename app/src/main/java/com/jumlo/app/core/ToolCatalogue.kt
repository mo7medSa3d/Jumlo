package com.jumlo.app.core

/**
 * The visible catalogue.
 *
 * The app carries forty-five capabilities, but a grid of forty-five tiles is noise:
 * somebody opening a scanner app wants "make a PDF", "scan" and "fix a photo", not
 * "extract pages". The registry still holds every capability, the search still finds
 * all of them, and every capability still runs. What changed is the *surface*: the
 * screens present a handful of workspaces, and the operations that belong together
 * sit inside one of them instead of competing for the same row.
 */
enum class ToolWorkspace(val id: String) {
    PDF("pdf"),
    SCANNER("scanner"),
    IMAGES("images"),
    FILES("files"),
    DOWNLOADS("downloads")
}

/** One workspace: the tool that opens it, and the capabilities merged into it. */
data class WorkspaceEntry(
    val workspace: ToolWorkspace,
    /** The tools that get a tile. */
    val primary: List<String>,
    /** The tools that are reachable, but folded into the workspace. */
    val merged: List<String>
)

object ToolCatalogue {

    private val entries = listOf(
        WorkspaceEntry(
            ToolWorkspace.PDF,
            primary = listOf(
                "pdf_03",       // merge
                "pdf_05",       // split
                "pdf_04",       // organise pages
                "pdf_compress", // compress
                "pdf_07",       // pdf to images
                "pdf_signature",// sign
                "pdf_clean"     // clean the authoring trail
            ),
            merged = listOf(
                "pdf_02",       // images to pdf
                "pdf_06",       // extract pages
                "pdf_crop",     // crop pages
                "pdf_image",    // add an image
                "pdf_forms",    // fill a form
                "pdf_annotate", // annotate
                "pdf_stamp",    // watermark and page numbers
                "pdf_metadata", // document properties
                "pdf_protect",  // password
                "pdf_01",       // view
                "pdf_ocr",      // searchable pdf
                "webpage_pdf"   // webpage to pdf
            )
        ),
        WorkspaceEntry(
            ToolWorkspace.SCANNER,
            primary = listOf("scan_doc"),
            merged = listOf("pdf_ocr")
        ),
        WorkspaceEntry(
            ToolWorkspace.IMAGES,
            primary = listOf(
                "img_03",       // crop, rotate, flip
                "img_02",       // resize
                "img_04",       // compress
                "img_01",       // convert
                "img_join",     // join and collage
                "img_watermark" // watermark
            ),
            merged = listOf(
                "img_adjust", // adjust and pad
                "img_target", // target size
                "img_stitch", // stitch screenshots
                "img_exif"    // exif inspector
            )
        ),
        WorkspaceEntry(
            ToolWorkspace.FILES,
            primary = listOf(
                "file_07",            // create an archive
                "file_duplicates",    // duplicate finder
                "file_batch_rename"   // batch rename
            ),
            merged = listOf(
                "file_08",            // extract an archive
                "file_encrypted_zip", // encrypted archive
                "sha256"              // verify a digest
            )
        ),
        WorkspaceEntry(
            ToolWorkspace.DOWNLOADS,
            // One download manager covers a direct link, a batch of links and media.
            primary = listOf("dl_02"),
            merged = listOf("dl_03", "dl_media", "dl_settings")
        )
    )

    private val byWorkspace = entries.associateBy { it.workspace }
    private val primaryIds = entries.flatMap { it.primary }.toSet()
    private val mergedIds = entries.flatMap { it.merged }.toSet()

    fun all(): List<WorkspaceEntry> = entries

    fun entry(workspace: ToolWorkspace): WorkspaceEntry? = byWorkspace[workspace]

    /** The tools that get a tile in the catalogue. */
    fun primaryTools(): List<String> = primaryIds.toList()

    /** The tools that are folded into a workspace but stay reachable. */
    fun mergedTools(): List<String> = mergedIds.toList()

    /** True when the tool appears as a tile rather than as a folded action. */
    fun isPrimary(toolId: String): Boolean = toolId in primaryIds

    fun workspaceOf(toolId: String): ToolWorkspace? =
        entries.firstOrNull { toolId in it.primary || toolId in it.merged }?.workspace

    /**
     * The tools that are deliberately kept out of the catalogue: specialists that
     * belong inside a bigger tool, and comparisons that most people never want.
     */
    private val hidden = setOf("img_compare", "file_trash", "file_02", "file_drive", "xf_05")

    /** True when the tool should be offered in the catalogue at all. */
    fun isListed(toolId: String): Boolean =
        toolId !in hidden && (toolId in primaryIds || toolId in mergedIds)

    /** The capabilities that are deliberately kept out of the catalogue. */
    fun hiddenTools(): Set<String> = hidden
}
