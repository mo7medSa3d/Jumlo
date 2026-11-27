package com.jumlo.app.ui.workspace

import com.jumlo.app.model.WorkspaceDraft

enum class ToolInputKind {
    PDF_SINGLE,
    PDF_MULTI,
    IMAGES,
    PDF_OR_IMAGE,
    OFFICE,
    ZIP_SINGLE,
    ANY_FILES
}

data class ToolInputSpec(
    val kind: ToolInputKind,
    val mimeTypes: List<String>,
    val allowMultiple: Boolean,
    val showPhotoGallery: Boolean
)

/** Single source of truth mapping every tool to its exact input MIME types, picker multiplicity, and gallery visibility. */
object ToolInputPolicy {
    private val OFFICE_MIMES = listOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.ms-powerpoint"
    )
    private val ZIP_MIMES = listOf("application/zip", "application/x-zip-compressed")
    private val PDF_AND_IMAGE_MIMES = listOf("application/pdf", "image/*")

    fun resolve(toolId: String, form: WorkspaceDraft): ToolInputSpec = when (toolId) {
        "pdf_03" -> ToolInputSpec(
            kind = ToolInputKind.PDF_MULTI,
            mimeTypes = listOf("application/pdf"),
            allowMultiple = true,
            showPhotoGallery = false
        )
        "pdf_01", "pdf_04", "pdf_05", "pdf_06", "pdf_07", "pdf_long_image",
        "pdf_crop", "pdf_compress", "pdf_protect", "pdf_nup",
        "pdf_privacy_redact", "pdf_to_docx", "pdf_to_pptx" -> ToolInputSpec(
            kind = ToolInputKind.PDF_SINGLE,
            mimeTypes = listOf("application/pdf"),
            allowMultiple = false,
            showPhotoGallery = false
        )
        "pdf_stamp" -> if (form.watermarkUseImage) {
            ToolInputSpec(
                kind = ToolInputKind.PDF_OR_IMAGE,
                mimeTypes = PDF_AND_IMAGE_MIMES,
                allowMultiple = true,
                showPhotoGallery = true
            )
        } else {
            ToolInputSpec(
                kind = ToolInputKind.PDF_SINGLE,
                mimeTypes = listOf("application/pdf"),
                allowMultiple = false,
                showPhotoGallery = false
            )
        }
        "pdf_signature" -> ToolInputSpec(
            kind = ToolInputKind.PDF_SINGLE,
            mimeTypes = listOf("application/pdf"),
            allowMultiple = false,
            showPhotoGallery = false
        )
        "pdf_ocr" -> ToolInputSpec(
            kind = ToolInputKind.PDF_OR_IMAGE,
            mimeTypes = PDF_AND_IMAGE_MIMES,
            allowMultiple = false,
            showPhotoGallery = true
        )
        "pdf_02", "img_01", "img_02", "img_03", "img_04", "img_bg_remove", "img_watermark", "img_join" -> ToolInputSpec(
            kind = ToolInputKind.IMAGES,
            mimeTypes = listOf("image/*"),
            allowMultiple = true,
            showPhotoGallery = true
        )
        "office_to_pdf" -> ToolInputSpec(
            kind = ToolInputKind.OFFICE,
            mimeTypes = OFFICE_MIMES,
            allowMultiple = false,
            showPhotoGallery = false
        )
        "file_07" -> ToolInputSpec(
            kind = ToolInputKind.ANY_FILES,
            mimeTypes = listOf("*/*"),
            allowMultiple = true,
            showPhotoGallery = false
        )
        "file_08" -> ToolInputSpec(
            kind = ToolInputKind.ZIP_SINGLE,
            mimeTypes = ZIP_MIMES,
            allowMultiple = false,
            showPhotoGallery = false
        )
        "file_encrypted_zip" -> if (form.flag1) {
            ToolInputSpec(
                kind = ToolInputKind.ZIP_SINGLE,
                mimeTypes = ZIP_MIMES,
                allowMultiple = false,
                showPhotoGallery = false
            )
        } else {
            ToolInputSpec(
                kind = ToolInputKind.ANY_FILES,
                mimeTypes = listOf("*/*"),
                allowMultiple = true,
                showPhotoGallery = false
            )
        }
        else -> ToolInputSpec(
            kind = ToolInputKind.ANY_FILES,
            mimeTypes = listOf("*/*"),
            allowMultiple = true,
            showPhotoGallery = false
        )
    }
}
