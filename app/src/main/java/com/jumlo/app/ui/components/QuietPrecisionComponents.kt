package com.jumlo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.model.ManagedFileItem
import com.jumlo.app.model.PillarCategory
import com.jumlo.app.ui.theme.QuietTheme

/**
 * Maps icon names (Material Symbols style names used across the catalog)
 * to Google Material Icons (Rounded set, from material-icons-extended).
 */
fun resolveStitchIcon(iconName: String): ImageVector = when (iconName) {
    "picture_as_pdf", "pdf", "description" -> Icons.Rounded.PictureAsPdf
    "photo_library", "image", "collections", "photo" -> Icons.Rounded.PhotoLibrary
    "download", "file_download", "add_link", "cloud_download" -> Icons.Rounded.Download
    "folder", "folder_open", "folder_shared" -> Icons.Rounded.Folder
    "folder_zip", "archive", "zip" -> Icons.Rounded.FolderZip
    "unarchive", "extract" -> Icons.Rounded.Unarchive
    "merge", "call_merge", "file_copy", "layers" -> Icons.Rounded.Merge
    "crop", "crop_rotate", "aspect_ratio" -> Icons.Rounded.Crop
    "compress", "press", "speed" -> Icons.Rounded.Compress
    "visibility", "file_open", "preview" -> Icons.Rounded.Visibility
    "lock", "enhanced_encryption", "password" -> Icons.Rounded.Lock
    "security", "shield_lock" -> Icons.Rounded.Security
    "verified_user", "shield", "verified" -> Icons.Rounded.VerifiedUser
    "settings", "tune", "tune_rounded" -> Icons.Rounded.Tune
    "search" -> Icons.Rounded.Search
    "share", "ios_share" -> Icons.Rounded.Share
    "home" -> Icons.Rounded.Home
    "grid_view", "apps", "build", "tools" -> Icons.Rounded.GridView
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
    "edit", "rename" -> Icons.Rounded.Edit
    "delete", "trash" -> Icons.Rounded.Delete
    "warning", "error" -> Icons.Rounded.Warning
    "info" -> Icons.Rounded.Description
    "person", "account" -> Icons.Rounded.Person
    "swap", "compare" -> Icons.Rounded.SwapHoriz
    "build_circle", "magic" -> Icons.Rounded.Build
    else -> Icons.Rounded.Bolt
}

/** Icon for a file by its extension. */
fun fileIconFor(extension: String): ImageVector = when (extension.lowercase()) {
    "pdf" -> Icons.Rounded.PictureAsPdf
    "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif" -> Icons.Rounded.Image
    "zip", "tar", "gz", "tgz", "7z", "rar" -> Icons.Rounded.FolderZip
    "txt", "md", "doc", "docx", "rtf" -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

/** Round tonal icon button used in app bars and toolbars. */
@Composable
fun QuietTonalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/** Square-ish colored icon tile (the signature element of every list item and tool card). */
@Composable
fun QuietIconTile(
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

/** Section heading with optional trailing text, e.g. "Recent results   See all". */
@Composable
fun QuietSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = QuietTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingText != null && onTrailingClick != null) {
            TextButton(onClick = onTrailingClick) {
                Text(trailingText, style = QuietTheme.typography.metadataStrong, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Hero banner: gradient "aurora" card used at the top of the home screen.
 */
@Composable
fun QuietHero(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Rounded.Bolt,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val brush = Brush.linearGradient(listOf(colors.primary, colors.tertiary))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(QuietTheme.shapes.card)
            .background(brush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(QuietTheme.spacing.cardInteriorPadding + 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = QuietTheme.typography.screenTitle,
                    color = colors.onPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = QuietTheme.typography.metadata,
                    color = colors.onPrimary.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(32.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

private fun dockIcon(tab: DockTab, selected: Boolean): ImageVector = when (tab) {
    DockTab.HOME -> Icons.Rounded.Home
    DockTab.TOOLS -> Icons.Rounded.GridView
    DockTab.DOWNLOADS -> Icons.Rounded.Download
    DockTab.FILES -> Icons.Rounded.Folder
}

/**
 * Floating pill navigation for compact widths. The active tab expands into a
 * labelled capsule; the others collapse to icons.
 */
@Composable
fun QuietFloatingDock(
    selectedTab: DockTab,
    language: AppLanguage,
    onSelectTab: (DockTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = navBottomPadding + QuietTheme.spacing.dockBottomOffset),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = QuietTheme.spacing.dockWidth)
                .fillMaxWidth(0.94f)
                .height(QuietTheme.spacing.dockHeight)
                .shadow(
                    elevation = 24.dp,
                    shape = QuietTheme.shapes.dockPill,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                ),
            shape = QuietTheme.shapes.dockPill,
            color = pillars.dockSurface,
            border = BorderStroke(1.dp, pillars.dockBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val label = if (language == AppLanguage.ARABIC) tab.labelAr else tab.labelEn
                    NavItem(
                        icon = dockIcon(tab, isSelected),
                        label = label,
                        selected = isSelected,
                        vertical = false,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.weight(if (isSelected) 1.7f else 1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * Permanent vertical navigation rail for expanded widths (>= 840dp).
 */
@Composable
fun QuietExpandedNavigationRail(
    selectedTab: DockTab,
    language: AppLanguage,
    onSelectTab: (DockTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    Surface(
        modifier = modifier.width(104.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, pillars.cardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            DockTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val label = if (language == AppLanguage.ARABIC) tab.labelAr else tab.labelEn
                NavItem(
                    icon = dockIcon(tab, isSelected),
                    label = label,
                    selected = isSelected,
                    vertical = true,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.width(88.dp).height(68.dp)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    vertical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    val pillBg by animateColorAsState(
        targetValue = if (selected) pillars.activePillContainer else Color.Transparent,
        animationSpec = tween(260),
        label = "navPillBg"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) pillars.activePillContent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(260),
        label = "navTint"
    )
    Box(
        modifier = modifier
            .clip(QuietTheme.shapes.activeIndicatorPill)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Tab
            },
        contentAlignment = Alignment.Center
    ) {
        if (vertical) {
            Column(
                modifier = Modifier
                    .clip(QuietTheme.shapes.activeIndicatorPill)
                    .background(pillBg)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = QuietTheme.typography.navigation,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(QuietTheme.shapes.activeIndicatorPill)
                    .background(pillBg)
                    .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 10.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                if (selected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = QuietTheme.typography.navigation.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Root top bar: large title, badge, and tonal action buttons with a brand avatar.
 */
@Composable
fun QuietRootTopBar(
    title: String,
    badgeText: String? = null,
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onVaultClick: (() -> Unit)? = null
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 8.dp, bottom = 6.dp)
                .padding(horizontal = QuietTheme.spacing.canvasGutterCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = QuietTheme.typography.screenTitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeText != null) {
                    QuietBadge(
                        text = badgeText,
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSearchClick != null) {
                    QuietTonalIconButton(Icons.Rounded.Search, "Search", onSearchClick)
                }
                QuietTonalIconButton(Icons.Rounded.Settings, "Settings", onSettingsClick)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
                        .clickable { onVaultClick?.invoke() }
                        .semantics { contentDescription = "Profile & Vault" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Workspace top bar: back button, title with optional subtitle and pillar badge, trailing actions.
 */
@Composable
fun QuietWorkspaceTopBar(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    pillar: PillarCategory = PillarCategory.PDF,
    onBack: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pillars = QuietTheme.pillars
    val (badgeBg, badgeFg) = when (pillar) {
        PillarCategory.PDF -> pillars.pdfContainer to pillars.pdfPrimary
        PillarCategory.IMAGES -> pillars.imagesContainer to pillars.imagesPrimary
        PillarCategory.DOWNLOADS -> pillars.downloadsContainer to pillars.downloadsPrimary
        PillarCategory.FILES -> pillars.filesContainer to pillars.filesPrimary
        PillarCategory.VAULT -> pillars.vaultContainer to pillars.vaultPrimary
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 6.dp, bottom = 8.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                QuietTonalIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = QuietTheme.typography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (badgeText != null) {
                            QuietBadge(text = badgeText, containerColor = badgeBg, contentColor = badgeFg)
                        }
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = QuietTheme.typography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                trailingContent?.invoke(this)
                if (onMoreClick != null) {
                    QuietTonalIconButton(Icons.Rounded.MoreVert, "More options", onMoreClick)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Surfaces, badges, buttons
// ---------------------------------------------------------------------------

/** Soft, layered card (28dp radius, hairline border, tonal fill). */
@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    containerColor: Color = QuietTheme.pillars.cardSurface,
    borderColor: Color = QuietTheme.pillars.cardBorder,
    contentPadding: PaddingValues = PaddingValues(QuietTheme.spacing.cardInteriorPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = QuietTheme.shapes.card
    val clickModifier = if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(clickModifier),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Pill-shaped status or category badge. */
@Composable
fun QuietBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(QuietTheme.shapes.activeIndicatorPill)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        }
        Text(
            text = text,
            style = QuietTheme.typography.badge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun QuietPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = QuietTheme.pillars.primaryActionBackground,
    contentColor: Color = QuietTheme.pillars.primaryActionText
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(QuietTheme.spacing.primaryButtonHeight),
        enabled = enabled,
        shape = QuietTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = QuietTheme.typography.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun QuietTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(QuietTheme.spacing.tonalButtonHeight),
        shape = QuietTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, style = QuietTheme.typography.metadataStrong.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pill search field. With [onClickBar] it behaves as a tappable launcher. */
@Composable
fun QuietSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClickBar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (onClickBar != null) Modifier.clickable(onClick = onClickBar) else Modifier),
        shape = QuietTheme.shapes.button,
        color = colors.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onClickBar != null) {
                    Text(
                        text = placeholder,
                        style = QuietTheme.typography.body,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    if (query.isEmpty()) {
                        Text(text = placeholder, style = QuietTheme.typography.body, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = QuietTheme.typography.body.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (query.isNotEmpty() && onClickBar == null) {
                QuietTonalIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Clear",
                    onClick = onClear,
                    size = 40.dp,
                    containerColor = colors.surfaceContainerHighest
                )
            }
        }
    }
}

/** Color family used for a file's icon tile, derived from its type. */
@Composable
private fun fileTint(extension: String): Pair<Color, Color> {
    val p = QuietTheme.pillars
    return when (extension.lowercase()) {
        "pdf" -> p.pdfPrimary to p.pdfContainer
        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif" -> p.imagesPrimary to p.imagesContainer
        "zip", "tar", "gz", "tgz", "7z", "rar" -> p.filesPrimary to p.filesContainer
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    }
}

/** File list row with a colored type tile, metadata line and optional selection. */
@Composable
fun QuietFileRow(
    file: ManagedFileItem,
    language: AppLanguage,
    showCheckbox: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: () -> Unit,
    trailingActionText: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (tint, container) = fileTint(file.extension)
    val isRtl = language.isRtl
    val modified = if (isRtl) file.modifiedLabelAr else file.modifiedLabelEn
    val details = buildList {
        add(file.formattedSize)
        file.pageCount?.let { add(if (isRtl) "$it صفحة" else "$it pages") }
        file.itemCount?.let { add(if (isRtl) "$it عنصر" else "$it items") }
        file.dimensions?.let { add(it) }
        add(modified)
    }.joinToString("  ·  ")
    val selected = file.isSelected

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) QuietTheme.pillars.activePillContainer else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(QuietTheme.spacing.fileRowStandardHeight)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                QuietCheckbox(checked = selected, onCheckedChange = { onToggleSelect?.invoke() })
                Spacer(modifier = Modifier.width(10.dp))
            }
            QuietIconTile(
                icon = fileIconFor(file.extension),
                tint = tint,
                container = container,
                size = 48.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = details,
                    style = QuietTheme.typography.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingActionText != null && onTrailingAction != null) {
                TextButton(onClick = onTrailingAction) {
                    Text(trailingActionText, style = QuietTheme.typography.metadataStrong, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Animated rounded checkbox. */
@Composable
fun QuietCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (checked) colors.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "checkFill"
    )
    val stroke by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.outline,
        animationSpec = tween(200),
        label = "checkStroke"
    )
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(fill)
            .border(2.dp, stroke, RoundedCornerShape(9.dp))
            .clickable { onCheckedChange(!checked) }
            .semantics { role = Role.Checkbox },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Side-by-side before/after comparison tile. */
@Composable
fun QuietBeforeAfterPreview(
    fileName: String,
    resolution: String,
    originalLabel: String,
    compressedLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(QuietTheme.shapes.card)
            .background(QuietTheme.pillars.cardSurface)
            .border(1.dp, QuietTheme.pillars.cardBorder, QuietTheme.shapes.card)
            .padding(QuietTheme.spacing.cardInteriorPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = fileName,
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            QuietBadge(text = resolution, containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PreviewPane(label = originalLabel, highlighted = false, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            PreviewPane(label = compressedLabel, highlighted = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewPane(label: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (highlighted) Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))
                    else SolidColor(colors.surfaceContainerHighest)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                tint = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = label,
            style = QuietTheme.typography.metadataStrong,
            color = if (highlighted) colors.primary else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Privacy reassurance strip shown on tools that process files locally. */
@Composable
fun QuietIsolationFooter(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(QuietTheme.shapes.activeIndicatorPill)
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = QuietTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
