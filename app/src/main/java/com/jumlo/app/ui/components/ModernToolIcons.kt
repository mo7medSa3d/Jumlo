package com.jumlo.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.jumlo.app.R

/** Original 2026 rounded outline set, consistent stroke and optically centered at 24dp. */
@DrawableRes
internal fun modernToolIconRes(name: String): Int = when (name) {
    "picture_as_pdf", "pdf", "description", "assignment" -> R.drawable.ic_prism_document
    "photo_library", "image", "collections", "photo", "add_photo_alternate" -> R.drawable.ic_prism_image
    "download", "file_download", "cloud_download" -> R.drawable.ic_prism_download
    "add_link", "link", "url" -> R.drawable.ic_prism_link
    "folder", "folder_open", "folder_shared" -> R.drawable.ic_prism_folder
    "folder_zip", "archive", "zip" -> R.drawable.ic_prism_archive
    "unarchive", "extract" -> R.drawable.ic_prism_unarchive
    "merge", "call_merge", "layers_merge", "file_copy" -> R.drawable.ic_prism_merge
    "layers", "view_agenda" -> R.drawable.ic_prism_layers
    "copy_all" -> R.drawable.ic_prism_copy
    "crop", "crop_rotate", "aspect_ratio" -> R.drawable.ic_prism_crop
    "compress", "press", "speed" -> R.drawable.ic_prism_compress
    "visibility", "file_open", "preview", "visibility_off" -> R.drawable.ic_prism_eye
    "lock", "enhanced_encryption", "password" -> R.drawable.ic_prism_lock
    "security", "shield_lock", "verified_user", "shield", "verified", "approval" -> R.drawable.ic_prism_shield
    "settings", "tune", "tune_rounded" -> R.drawable.ic_prism_settings
    "search" -> R.drawable.ic_prism_search
    "home" -> R.drawable.ic_prism_home
    "grid_view", "apps", "dashboard" -> R.drawable.ic_prism_grid
    "document_scanner" -> R.drawable.ic_prism_scan
    "content_cut", "cut", "scissors" -> R.drawable.ic_prism_split
    "bolt", "auto_fix", "flash_on", "magic", "build_circle" -> R.drawable.ic_prism_sparkles
    "language", "translate", "webpage" -> R.drawable.ic_prism_globe
    "info" -> R.drawable.ic_prism_info
    "edit", "rename", "draw", "drive_file_rename_outline", "text_fields" -> R.drawable.ic_prism_edit
    "checklist" -> R.drawable.ic_prism_checklist
    "ocr", "text_scan" -> R.drawable.ic_prism_ocr
    "watermark" -> R.drawable.ic_prism_watermark
    "delete", "trash" -> R.drawable.ic_prism_trash
    "cloud", "cloud_sync", "sync" -> R.drawable.ic_prism_cloud
    "print" -> R.drawable.ic_prism_printer
    "person", "account" -> R.drawable.ic_prism_user
    "star", "favorite" -> R.drawable.ic_prism_star
    "rotate", "rotate_right", "history", "schedule_history" -> R.drawable.ic_prism_rotate
    "swap", "compare" -> R.drawable.ic_prism_compare
    "file_open2", "storage", "hard_drive" -> R.drawable.ic_prism_files
    "audio", "audio_file" -> R.drawable.ic_prism_audio
    "video", "movie" -> R.drawable.ic_prism_video
    else -> R.drawable.ic_prism_sparkles
}

@Composable
fun ModernToolIcon(iconName: String, tint: Color, modifier: Modifier = Modifier) {
    Icon(painter = painterResource(modernToolIconRes(iconName)),
        contentDescription = null, modifier = modifier, tint = tint)
}
