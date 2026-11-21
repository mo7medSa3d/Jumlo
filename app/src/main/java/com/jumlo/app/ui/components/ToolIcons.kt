package com.jumlo.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.jumlo.app.R

/** Unique official Material Symbols Rounded, filled, 24dp; see ICON_MAP.md. */
internal val toolIconResources: Map<String, Int> = linkedMapOf(
    "pdf_to_docx" to R.drawable.ic_tool_pdf_to_docx,
    "pdf_to_pptx" to R.drawable.ic_tool_pdf_to_pptx,
    "office_to_pdf" to R.drawable.ic_tool_pdf_to_docx,
    "pdf_02" to R.drawable.ic_tool_pdf_02,
    "pdf_03" to R.drawable.ic_tool_pdf_03,
    "pdf_05" to R.drawable.ic_tool_pdf_05,
    "pdf_04" to R.drawable.ic_tool_pdf_04,
    "pdf_06" to R.drawable.ic_tool_pdf_06,
    "pdf_07" to R.drawable.ic_tool_pdf_07,
    "pdf_01" to R.drawable.ic_tool_pdf_01,
    "pdf_compress" to R.drawable.ic_tool_pdf_compress,
    "pdf_crop" to R.drawable.ic_tool_pdf_crop,
    "pdf_stamp" to R.drawable.ic_tool_pdf_stamp,
    "pdf_signature" to R.drawable.ic_tool_pdf_signature,
    "pdf_protect" to R.drawable.ic_tool_pdf_protect,
    "pdf_nup" to R.drawable.ic_tool_pdf_nup,
    "pdf_privacy_redact" to R.drawable.ic_tool_pdf_privacy_redact,
    "pdf_long_image" to R.drawable.ic_tool_pdf_long_image,
    "pdf_ocr" to R.drawable.ic_tool_pdf_ocr,
    "scan_doc" to R.drawable.ic_tool_scan_doc,
    "img_01" to R.drawable.ic_tool_img_01,
    "img_02" to R.drawable.ic_tool_img_02,
    "img_03" to R.drawable.ic_tool_img_03,
    "img_04" to R.drawable.ic_tool_img_04,
    "img_watermark" to R.drawable.ic_tool_img_watermark,
    "img_join" to R.drawable.ic_tool_img_join,
    "dl_media" to R.drawable.ic_tool_dl_media,
    "dl_02" to R.drawable.ic_tool_dl_02,
    "dl_03" to R.drawable.ic_tool_dl_03,
    "dl_settings" to R.drawable.ic_tool_dl_settings,
    "file_02" to R.drawable.ic_tool_file_02,
    "file_07" to R.drawable.ic_tool_file_07,
    "file_08" to R.drawable.ic_tool_file_08,
    "file_encrypted_zip" to R.drawable.ic_tool_file_encrypted_zip,
    "file_batch_rename" to R.drawable.ic_tool_file_batch_rename,
)

@DrawableRes
internal fun toolIconRes(toolId: String): Int =
    toolIconResources[toolId] ?: R.drawable.ic_prism_document

@Composable
fun ToolIcon(toolId: String, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(painterResource(toolIconRes(toolId)), contentDescription, modifier, tint)
}
