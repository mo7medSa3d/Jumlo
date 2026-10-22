package com.jumlo.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector

fun resolveStitchIcon(iconName: String): ImageVector = when (iconName) {
    "picture_as_pdf", "pdf", "description" -> Icons.Rounded.PictureAsPdf
    "photo_library", "image", "collections", "photo", "view_agenda" -> Icons.Rounded.PhotoLibrary
    "download", "file_download", "add_link", "cloud_download" -> Icons.Rounded.Download
    "folder", "folder_open", "folder_shared" -> Icons.Rounded.Folder
    "folder_zip", "archive", "zip" -> Icons.Rounded.FolderZip
    "unarchive", "extract" -> Icons.Rounded.Unarchive
    "merge", "call_merge", "file_copy", "layers", "copy_all" -> Icons.Rounded.Merge
    "crop", "crop_rotate", "aspect_ratio" -> Icons.Rounded.Crop
    "compress", "press", "speed" -> Icons.Rounded.Compress
    "visibility", "file_open", "preview", "visibility_off" -> Icons.Rounded.Visibility
    "lock", "enhanced_encryption", "password" -> Icons.Rounded.Lock
    "security", "shield_lock" -> Icons.Rounded.Security
    "verified_user", "shield", "verified", "approval" -> Icons.Rounded.VerifiedUser
    "settings", "tune", "tune_rounded" -> Icons.Rounded.Tune
    "search" -> Icons.Rounded.Search
    "share", "ios_share" -> Icons.Rounded.Share
    "home" -> Icons.Rounded.Home
    "grid_view", "apps", "build", "tools", "document_scanner" -> Icons.Rounded.GridView
    "content_cut", "cut", "scissors" -> Icons.Rounded.ContentCut
    "bolt", "auto_fix", "flash_on" -> Icons.Rounded.Bolt
    "dashboard" -> Icons.Rounded.Dashboard
    "history", "schedule_history" -> Icons.Rounded.History
    "schedule", "timer" -> Icons.Rounded.Schedule
    "storage", "hard_drive" -> Icons.Rounded.Storage
    "cloud", "cloud_sync", "sync" -> Icons.Rounded.Cloud
    "link", "url" -> Icons.Rounded.Link
    "language", "translate" -> Icons.Rounded.Language
    "dark_mode" -> Icons.Rounded.DarkMode
    "fingerprint", "biometric" -> Icons.Rounded.Fingerprint
    "notifications", "notifications_active" -> Icons.Rounded.Notifications
    "star", "favorite" -> Icons.Rounded.Star
    "add", "new" -> Icons.Rounded.Add
    "edit", "rename", "draw", "drive_file_rename_outline", "text_fields", "assignment" -> Icons.Rounded.Edit
    "delete", "trash" -> Icons.Rounded.Delete
    "warning", "error" -> Icons.Rounded.Warning
    "info" -> Icons.Rounded.Description
    "person", "account" -> Icons.Rounded.Person
    "swap", "compare" -> Icons.Rounded.SwapHoriz
    "build_circle", "magic" -> Icons.Rounded.Build
    else -> Icons.Rounded.Bolt
}

fun fileIconFor(extension: String): ImageVector = when (extension.lowercase()) {
    "pdf" -> Icons.Rounded.PictureAsPdf
    "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif" -> Icons.Rounded.Image
    "zip", "tar", "gz", "tgz", "7z", "rar" -> Icons.Rounded.FolderZip
    "txt", "md", "doc", "docx", "rtf" -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}
