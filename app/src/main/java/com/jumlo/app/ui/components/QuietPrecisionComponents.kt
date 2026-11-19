package com.jumlo.app.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Description
import com.jumlo.app.ui.icons.rounded.FolderZip
import com.jumlo.app.ui.icons.rounded.Image
import com.jumlo.app.ui.icons.rounded.InsertDriveFile
import com.jumlo.app.ui.icons.rounded.PictureAsPdf

fun fileIconFor(extension: String): ImageVector = when (extension.lowercase()) {
    "pdf" -> Icons.Rounded.PictureAsPdf
    "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif" -> Icons.Rounded.Image
    "zip", "tar", "gz", "tgz", "7z", "rar" -> Icons.Rounded.FolderZip
    "txt", "md", "doc", "docx", "rtf" -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}
