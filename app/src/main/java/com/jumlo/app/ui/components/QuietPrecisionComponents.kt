package com.jumlo.app.ui.components

import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Add
import com.jumlo.app.ui.icons.rounded.Bolt
import com.jumlo.app.ui.icons.rounded.Build
import com.jumlo.app.ui.icons.rounded.Cloud
import com.jumlo.app.ui.icons.rounded.Compress
import com.jumlo.app.ui.icons.rounded.ContentCut
import com.jumlo.app.ui.icons.rounded.Crop
import com.jumlo.app.ui.icons.rounded.Dashboard
import com.jumlo.app.ui.icons.rounded.DarkMode
import com.jumlo.app.ui.icons.rounded.Delete
import com.jumlo.app.ui.icons.rounded.Description
import com.jumlo.app.ui.icons.rounded.Download
import com.jumlo.app.ui.icons.rounded.Edit
import com.jumlo.app.ui.icons.rounded.Fingerprint
import com.jumlo.app.ui.icons.rounded.Folder
import com.jumlo.app.ui.icons.rounded.FolderZip
import com.jumlo.app.ui.icons.rounded.GridView
import com.jumlo.app.ui.icons.rounded.History
import com.jumlo.app.ui.icons.rounded.Home
import com.jumlo.app.ui.icons.rounded.Image
import com.jumlo.app.ui.icons.rounded.InsertDriveFile
import com.jumlo.app.ui.icons.rounded.Language
import com.jumlo.app.ui.icons.rounded.Link
import com.jumlo.app.ui.icons.rounded.Lock
import com.jumlo.app.ui.icons.rounded.Merge
import com.jumlo.app.ui.icons.rounded.Notifications
import com.jumlo.app.ui.icons.rounded.Person
import com.jumlo.app.ui.icons.rounded.PhotoLibrary
import com.jumlo.app.ui.icons.rounded.PictureAsPdf
import com.jumlo.app.ui.icons.rounded.Schedule
import com.jumlo.app.ui.icons.rounded.Search
import com.jumlo.app.ui.icons.rounded.Security
import com.jumlo.app.ui.icons.rounded.Share
import com.jumlo.app.ui.icons.rounded.Star
import com.jumlo.app.ui.icons.rounded.Storage
import com.jumlo.app.ui.icons.rounded.SwapHoriz
import com.jumlo.app.ui.icons.rounded.Tune
import com.jumlo.app.ui.icons.rounded.Unarchive
import com.jumlo.app.ui.icons.rounded.VerifiedUser
import com.jumlo.app.ui.icons.rounded.Visibility
import com.jumlo.app.ui.icons.rounded.Warning
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
