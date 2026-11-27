package com.jumlo.app.core

/**
 * The visible catalogue.
 *
 * Frequent actions have primary tiles; working specialist tools stay in their
 * workspace's advanced list. CapabilityPolicy independently gates unavailable
 * registry entries, so catalogue membership never implies runtime qualification.
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
    val primary: List<String>,
    val merged: List<String>
)

object ToolCatalogue {

    private val entries = listOf(
        WorkspaceEntry(
            ToolWorkspace.PDF,
            primary = listOf(
                "pdf_to_docx",   // editable paragraphs
                "pdf_to_pptx",   // slide per page
                "office_to_pdf", // Word, Excel and slides to PDF
                "pdf_ocr",       // text extraction
                "pdf_02",        // images to PDF
                "pdf_03",        // merge
                "pdf_05",        // split PDF
                "pdf_06",        // extract pages
                "pdf_04",        // organise pages
                "pdf_compress",
                "pdf_signature",
                "pdf_protect",   // password protect / unlock
                "pdf_07",        // PDF to images (one image per page)
                "pdf_long_image" // PDF to long image
            ),
            merged = listOf(
                "pdf_crop",
                "pdf_stamp",          // watermark and page numbers
                "pdf_privacy_redact", // redact sensitive text/areas
                "pdf_nup",            // 2/4 pages per sheet
                "pdf_01"              // view
            )
        ),
        WorkspaceEntry(
            ToolWorkspace.SCANNER,
            primary = listOf("scan_doc", "scan_id_card"),
            merged = emptyList()
        ),
        WorkspaceEntry(
            ToolWorkspace.IMAGES,
            primary = listOf(
                "img_01",        // convert images
                "img_02",        // resize
                "img_04",        // edit, crop, rotate & compress
                "img_bg_remove", // remove background
                "img_join",
                "img_watermark"
            ),
            merged = emptyList()
        ),
        WorkspaceEntry(
            ToolWorkspace.FILES,
            primary = listOf(
                "file_07",           // create an archive (standard or encrypted)
                "file_08",           // extract an archive
                "file_batch_rename"
            ),
            merged = emptyList()
        ),
        WorkspaceEntry(
            ToolWorkspace.DOWNLOADS,
            primary = listOf("dl_media"),
            merged = emptyList()
        )
    )

    private val byWorkspace = entries.associateBy { it.workspace }
    private val primaryIds = entries.flatMap { it.primary }.toSet()
    private val mergedIds = entries.flatMap { it.merged }.toSet()

    /** Maps consolidated legacy tool IDs to their unified user-facing target ID. */
    private val legacyAliases: Map<String, String> = mapOf(
        "img_03" to "img_04",
        "file_encrypted_zip" to "file_07",
        "dl_02" to "dl_media",
        "dl_03" to "dl_media"
    )

    private val hidden: Set<String> = legacyAliases.keys

    fun all(): List<WorkspaceEntry> = entries

    fun entry(workspace: ToolWorkspace): WorkspaceEntry? = byWorkspace[workspace]

    fun primaryTools(): List<String> = primaryIds.toList()

    fun mergedTools(): List<String> = mergedIds.toList()

    fun isPrimary(toolId: String): Boolean = toolId in primaryIds

    fun workspaceOf(toolId: String): ToolWorkspace? =
        entries.firstOrNull { toolId in it.primary || toolId in it.merged }?.workspace

    /** Resolves consolidated legacy IDs to their unified user-facing tool ID. */
    fun canonicalId(toolId: String): String = legacyAliases[toolId] ?: toolId

    /** Extra EN/AR search terms so merged capabilities and dual-mode tools remain discoverable. */
    fun searchAliases(toolId: String): String = when (toolId) {
        "scan_id_card" -> "id card passport front back بطاقة هوية جواز"
        "img_04" -> "convert crop rotate flip optimize compress تحويل قص تدوير قلب تعديل ضغط"
        "img_bg_remove" -> "remove background cutout transparent eraser png خلفية إزالة تفريغ شفافية"
        "file_07" -> "encrypt encrypted password aes archive zip مشفر كلمة مرور أرشيف ضغط"
        "dl_media" -> "direct link batch url links video audio download رابط مباشر دفعة روابط فيديو صوت تنزيل"
        "pdf_protect" -> "unlock decrypt remove password lock protect فك قفل إزالة كلمة المرور حماية"
        "pdf_05" -> "extract pages split استخراج صفحات تقسيم"
        "pdf_long_image" -> "pdf to images png jpg convert long image تحويل صور صورة طويلة"
        else -> ""
    }

    fun isListed(toolId: String): Boolean =
        toolId !in hidden && (toolId in primaryIds || toolId in mergedIds)

    fun hiddenTools(): Set<String> = hidden

    fun isSearchable(toolId: String): Boolean = isListed(toolId)
}
