package com.jumlo.app.ui.files

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jumlo.app.R
import androidx.compose.foundation.shape.CircleShape
import com.jumlo.app.ui.icons.rounded.Search
import com.jumlo.app.model.FileFolder
import com.jumlo.app.model.OutputRecord
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.automirrored.rounded.Sort
import com.jumlo.app.ui.icons.rounded.Add
import com.jumlo.app.ui.icons.rounded.CheckCircle
import com.jumlo.app.ui.icons.rounded.Compress
import com.jumlo.app.ui.icons.rounded.DeleteOutline
import com.jumlo.app.ui.icons.rounded.Edit
import com.jumlo.app.ui.icons.rounded.Folder
import com.jumlo.app.ui.icons.rounded.FolderOpen
import com.jumlo.app.ui.icons.rounded.FolderZip
import com.jumlo.app.ui.icons.rounded.MergeType
import com.jumlo.app.ui.icons.rounded.MoreVert
import com.jumlo.app.ui.icons.rounded.PictureAsPdf
import com.jumlo.app.ui.icons.rounded.Share
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderShelf(
    folders: List<FileFolder>,
    folderCounts: Map<String, Int>,
    dragState: FileDragDropState,
    onCreateFolder: () -> Unit,
    onOpenFolder: (FileFolder) -> Unit,
    onFolderMenu: (FileFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.files_folders_heading),
                style = QuietTheme.typography.metadataStrong,
                color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            TextButton(
                onClick = onCreateFolder,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.files_folder_create_title), style = QuietTheme.typography.metadataStrong)
            }
        }
        if (folders.isNotEmpty()) {
            val reduced = LocalReducedMotion.current
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(folders, key = { it.id }, contentType = { "folder-card" }) { folder ->
                    val count = folderCounts[folder.id] ?: 0
                    val isHovered = dragState.isDragging && dragState.hoveredTargetId == folder.id
                    val scale by animateFloatAsState(
                        targetValue = if (isHovered) 1.05f else 1f,
                        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.72f, stiffness = 420f),
                        label = "folderDropScale"
                    )
                    Surface(
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(QuietTheme.shapes.card)
                            .fileDropTarget(folder.id, dragState)
                            .pressFeedback()
                            .combinedClickable(
                                onClick = { onOpenFolder(folder) },
                                onLongClick = { onFolderMenu(folder) }
                            ),
                        shape = QuietTheme.shapes.card,
                        color = if (isHovered) colors.primaryContainer else pillars.cardSurface,
                        border = BorderStroke(
                            width = if (isHovered) 2.dp else 1.dp,
                            color = if (isHovered) colors.primary else pillars.cardBorder
                        ),
                        shadowElevation = if (isHovered) 4.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 68.dp)
                                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            JumloFolderIcon(
                                hasItems = count > 0,
                                isHovered = isHovered,
                                accent = if (isHovered) colors.primary else pillars.filesPrimary,
                                modifier = Modifier.size(46.dp)
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(end = 2.dp)
                            ) {
                                Text(
                                    text = folder.name,
                                    style = QuietTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        textDirection = TextDirection.Content
                                    ),
                                    color = if (isHovered) colors.onPrimaryContainer else colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isHovered) stringResource(R.string.files_folder_drop_here)
                                    else if (count == 0) stringResource(R.string.files_folder_empty_short)
                                    else stringResource(R.string.files_folder_count_items, count),
                                    style = QuietTheme.typography.metadata,
                                    color = if (isHovered) colors.primary else colors.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { onFolderMenu(folder) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.files_folder_actions_title),
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.files_folder_drag_hint),
                style = QuietTheme.typography.metadata,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FolderBreadcrumbBar(
    folder: FileFolder,
    itemCount: Int,
    dragState: FileDragDropState,
    onBackToRoot: () -> Unit,
    onOpenFolderActions: (() -> Unit)? = null,
    onCreateSubfolder: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onToggleSearch: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null,
    selecting: Boolean = false,
    onSelectAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isRootDropHovered = dragState.isDragging && dragState.hoveredTargetId == ROOT_DROP_TARGET_ID

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fileDropTarget(ROOT_DROP_TARGET_ID, dragState)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = onBackToRoot,
            shape = CircleShape,
            color = if (isRootDropHovered) colors.primaryContainer else colors.surfaceContainer,
            border = if (isRootDropHovered) BorderStroke(1.5.dp, colors.primary) else null,
            modifier = Modifier.size(40.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.files_folder_all_files),
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = if (isRootDropHovered) stringResource(R.string.files_folder_drop_to_root) else folder.name,
            style = QuietTheme.typography.sectionTitle.copy(textDirection = TextDirection.Content),
            color = if (isRootDropHovered) colors.primary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (onCreateSubfolder != null) {
            Surface(
                onClick = onCreateSubfolder,
                shape = CircleShape,
                color = colors.surfaceContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = stringResource(R.string.files_folder_create_title),
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (onImport != null) {
            Surface(
                onClick = onImport,
                shape = CircleShape,
                color = colors.primary,
                modifier = Modifier.size(38.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.import_device_files),
                        tint = colors.onPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
        if (onToggleSearch != null) {
            Surface(
                onClick = onToggleSearch,
                shape = CircleShape,
                color = colors.surfaceContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.ui_search_files),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (selecting && onSelectAll != null) {
            TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.redesign_select_all), style = QuietTheme.typography.metadataStrong)
            }
        }
        if (onToggleSelect != null) {
            TextButton(onClick = onToggleSelect, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(if (selecting) R.string.action_cancel else R.string.ui_select_files), style = QuietTheme.typography.metadataStrong)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionsSheet(
    folder: FileFolder,
    folderOutputs: List<OutputRecord>,
    onDismiss: () -> Unit,
    onZipFolder: () -> Unit,
    onMergePdfs: () -> Unit,
    onImagesToPdf: () -> Unit,
    onCompressImages: () -> Unit,
    onShareAll: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    isLocked: Boolean = false,
    onToggleLock: (() -> Unit)? = null
) {
    val pdfs = remember(folderOutputs) { folderOutputs.filter { it.mimeType == "application/pdf" } }
    val images = remember(folderOutputs) { folderOutputs.filter { it.mimeType.startsWith("image/") } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = folder.name,
                style = QuietTheme.typography.sectionTitle.copy(textDirection = TextDirection.Content),
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (folderOutputs.isEmpty()) stringResource(R.string.files_folder_empty_short)
                else stringResource(R.string.files_folder_count_items, folderOutputs.size),
                style = QuietTheme.typography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(6.dp))

            if (folderOutputs.isNotEmpty()) {
                SheetActionRow(
                    icon = Icons.Rounded.FolderZip,
                    title = stringResource(R.string.files_folder_action_zip),
                    subtitle = stringResource(R.string.files_folder_action_zip_sub, folderOutputs.size),
                    onClick = { onDismiss(); onZipFolder() }
                )
            }
            if (pdfs.size >= 2) {
                SheetActionRow(
                    icon = Icons.Rounded.MergeType,
                    title = stringResource(R.string.files_folder_action_merge_pdf),
                    subtitle = stringResource(R.string.files_folder_action_merge_pdf_sub, pdfs.size),
                    onClick = { onDismiss(); onMergePdfs() }
                )
            }
            if (images.isNotEmpty()) {
                SheetActionRow(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = stringResource(R.string.files_folder_action_images_pdf),
                    subtitle = stringResource(R.string.files_folder_action_images_pdf_sub, images.size),
                    onClick = { onDismiss(); onImagesToPdf() }
                )
                SheetActionRow(
                    icon = Icons.Rounded.Compress,
                    title = stringResource(R.string.files_folder_action_compress_images),
                    subtitle = stringResource(R.string.files_folder_action_compress_images_sub, images.size),
                    onClick = { onDismiss(); onCompressImages() }
                )
            }
            if (folderOutputs.isNotEmpty()) {
                SheetActionRow(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.files_folder_action_share_all),
                    onClick = { onDismiss(); onShareAll() }
                )
            }
            SheetActionRow(
                icon = Icons.Rounded.Edit,
                title = stringResource(R.string.files_folder_action_rename),
                onClick = { onDismiss(); onRename() }
            )
            SheetActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.files_folder_action_delete),
                destructive = true,
                onClick = { onDismiss(); onDelete() }
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageQuickActionsSheet(
    canSelect: Boolean,
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onImport: () -> Unit,
    onStartSelect: () -> Unit,
    onOpenSort: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.files_page_actions_title),
                style = QuietTheme.typography.sectionTitle,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.size(4.dp))
            SheetActionRow(
                icon = Icons.Rounded.Folder,
                title = stringResource(R.string.files_page_action_new_folder),
                subtitle = stringResource(R.string.files_page_action_new_folder_sub),
                onClick = { onDismiss(); onCreateFolder() }
            )
            SheetActionRow(
                icon = Icons.Rounded.Add,
                title = stringResource(R.string.files_page_action_import),
                onClick = { onDismiss(); onImport() }
            )
            if (canSelect) {
                SheetActionRow(
                    icon = Icons.Rounded.CheckCircle,
                    title = stringResource(R.string.files_page_action_select),
                    onClick = { onDismiss(); onStartSelect() }
                )
            }
            SheetActionRow(
                icon = Icons.AutoMirrored.Rounded.Sort,
                title = stringResource(R.string.files_page_action_sort),
                onClick = { onDismiss(); onOpenSort() }
            )
        }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (destructive) colors.error else colors.primary
    Surface(
        onClick = onClick,
        shape = QuietTheme.shapes.inputField,
        color = colors.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (destructive) colors.error else colors.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FolderNameDialog(
    title: String,
    confirmLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val valid = value.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuietTheme.shapes.heroCard,
        title = {
            Text(text = title, modifier = Modifier.semantics { heading() })
        },
        text = {
            TextField(
                value = value,
                onValueChange = { if (it.length <= 60) onValueChange(it) },
                singleLine = true,
                label = { Text(stringResource(R.string.files_folder_create_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = valid) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<FileFolder>,
    currentFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onCreateNewFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuietTheme.shapes.heroCard,
        title = {
            Text(
                text = stringResource(R.string.files_move_to_folder),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (currentFolderId != null) {
                    SheetActionRow(
                        icon = Icons.Rounded.FolderOpen,
                        title = stringResource(R.string.files_move_out_of_folder),
                        onClick = { onSelectFolder(null) }
                    )
                }
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.files_move_no_folders),
                        style = QuietTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    folders.filter { it.id != currentFolderId }.forEach { folder ->
                        SheetActionRow(
                            icon = Icons.Rounded.Folder,
                            title = folder.name,
                            onClick = { onSelectFolder(folder.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNewFolder) {
                Text(stringResource(R.string.files_folder_create_title))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Layered iOS-style tactile folder icon drawn natively in Compose Canvas:
 * back plate with curved tab, subtle white paper peek when non-empty, and a
 * specular-lit front cover that lifts smoothly on drag-hover.
 */
@Composable
fun JumloFolderIcon(
    hasItems: Boolean,
    isHovered: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val reduced = LocalReducedMotion.current
    val coverLift by animateFloatAsState(
        targetValue = if (isHovered && !reduced) 0.12f else 0f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "folder-cover-lift"
    )
    val backDark = Color(
        red = (accent.red * 0.78f).coerceIn(0f, 1f),
        green = (accent.green * 0.78f).coerceIn(0f, 1f),
        blue = (accent.blue * 0.78f).coerceIn(0f, 1f),
        alpha = 1f
    )

    val backPath = remember { Path() }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = w * 0.06f
        val bodyW = w - padX * 2f
        val r = w * 0.14f

        backPath.reset()
        backPath.apply {
            moveTo(padX + r, h * 0.14f)
            lineTo(padX + bodyW * 0.42f, h * 0.14f)
            lineTo(padX + bodyW * 0.52f, h * 0.25f)
            lineTo(padX + bodyW - r, h * 0.25f)
            quadraticTo(padX + bodyW, h * 0.25f, padX + bodyW, h * 0.25f + r)
            lineTo(padX + bodyW, h * 0.84f - r)
            quadraticTo(padX + bodyW, h * 0.84f, padX + bodyW - r, h * 0.84f)
            lineTo(padX + r, h * 0.84f)
            quadraticTo(padX, h * 0.84f, padX, h * 0.84f - r)
            lineTo(padX, h * 0.14f + r)
            quadraticTo(padX, h * 0.14f, padX + r, h * 0.14f)
            close()
        }
        drawPath(backPath, color = backDark)

        if (hasItems) {
            val paperTop = h * (0.27f - coverLift * 0.45f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.94f),
                topLeft = Offset(padX + bodyW * 0.10f, paperTop),
                size = Size(bodyW * 0.80f, h * 0.26f),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
            )
        }

        val frontTop = h * (0.34f + coverLift)
        val frontHeight = (h * 0.54f - h * coverLift).coerceAtLeast(h * 0.34f)
        drawRoundRect(
            color = accent,
            topLeft = Offset(padX, frontTop),
            size = Size(bodyW, frontHeight),
            cornerRadius = CornerRadius(r, r)
        )
    }
}
