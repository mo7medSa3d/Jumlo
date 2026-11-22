package com.jumlo.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.jumlo.app.R

/**
 * Resolves either a tool ID or a legacy Material icon name to the unified
 * filled rounded vector style used across Tools and Home.
 */
@DrawableRes
internal fun modernToolIconRes(name: String): Int =
    toolIconResources[name] ?: when (name) {
        "picture_as_pdf", "pdf" -> R.drawable.ic_tool_pdf_02
        "description", "assignment", "article" -> R.drawable.ic_tool_pdf_to_docx
        "slideshow" -> R.drawable.ic_tool_pdf_to_pptx
        "photo_library", "collections", "add_photo_alternate" -> R.drawable.ic_tool_img_01
        "image", "photo" -> R.drawable.ic_tool_pdf_07
        "download", "file_download", "cloud_download" -> R.drawable.ic_tool_dl_media
        "add_link", "link", "url" -> R.drawable.ic_tool_dl_02
        "folder", "folder_open", "folder_shared" -> R.drawable.ic_tool_file_02
        "folder_zip", "archive", "zip" -> R.drawable.ic_tool_file_07
        "unarchive", "extract" -> R.drawable.ic_tool_file_08
        "merge", "call_merge", "layers_merge", "file_copy" -> R.drawable.ic_tool_pdf_03
        "layers", "view_agenda", "view_module" -> R.drawable.ic_tool_pdf_04
        "copy_all" -> R.drawable.ic_prism_copy
        "crop" -> R.drawable.ic_tool_pdf_crop
        "crop_rotate" -> R.drawable.ic_tool_img_03
        "aspect_ratio" -> R.drawable.ic_tool_img_02
        "compress", "press", "speed" -> R.drawable.ic_tool_pdf_compress
        "visibility", "preview", "visibility_off" -> R.drawable.ic_tool_pdf_01
        "file_open" -> R.drawable.ic_tool_pdf_06
        "lock", "password" -> R.drawable.ic_tool_pdf_protect
        "enhanced_encryption" -> R.drawable.ic_tool_file_encrypted_zip
        "approval", "watermark" -> R.drawable.ic_tool_pdf_stamp
        "security", "shield_lock", "verified_user", "shield", "verified" -> R.drawable.ic_prism_shield
        "settings", "tune", "tune_rounded" -> R.drawable.ic_tool_dl_settings
        "search" -> R.drawable.ic_prism_search
        "home" -> R.drawable.ic_prism_home
        "grid_view", "apps" -> R.drawable.ic_prism_all_tools
        "dashboard" -> R.drawable.ic_tool_img_join
        "document_scanner" -> R.drawable.ic_tool_scan_doc
        "badge", "id_card" -> R.drawable.ic_prism_id_card
        "content_cut", "cut", "scissors" -> R.drawable.ic_tool_pdf_05
        "bolt", "auto_fix", "flash_on", "magic", "build_circle" -> R.drawable.ic_prism_sparkles
        "language", "translate", "webpage" -> R.drawable.ic_prism_globe
        "info" -> R.drawable.ic_prism_info
        "draw" -> R.drawable.ic_tool_pdf_signature
        "edit", "rename", "drive_file_rename_outline" -> R.drawable.ic_tool_file_batch_rename
        "text_fields", "ocr", "text_scan" -> R.drawable.ic_tool_pdf_ocr
        "checklist" -> R.drawable.ic_prism_checklist
        "delete", "trash" -> R.drawable.ic_prism_trash
        "cloud", "cloud_sync", "sync" -> R.drawable.ic_prism_cloud
        "print" -> R.drawable.ic_prism_printer
        "person", "account" -> R.drawable.ic_prism_user
        "star", "favorite" -> R.drawable.ic_prism_star
        "rotate", "rotate_right", "history", "schedule_history" -> R.drawable.ic_prism_rotate
        "swap", "compare", "convert" -> R.drawable.ic_prism_convert
        "file_open2", "storage", "hard_drive" -> R.drawable.ic_prism_files
        "audio", "audio_file" -> R.drawable.ic_prism_audio
        "video", "movie" -> R.drawable.ic_prism_video
        else -> R.drawable.ic_prism_all_tools
    }

@Composable
fun ModernToolIcon(iconName: String, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(modernToolIconRes(iconName)),
        contentDescription = null,
        modifier = modifier,
        tint = tint
    )
}
