package com.universalutilities.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalutilities.app.model.AppLanguage
import com.universalutilities.app.model.DockTab
import com.universalutilities.app.model.ManagedFileItem
import com.universalutilities.app.model.PillarCategory
import com.universalutilities.app.ui.theme.QuietTheme
import kotlin.math.roundToInt

/**
 * Maps Stitch icon strings to Material Rounded Icons.
 */
fun resolveStitchIcon(iconName: String): ImageVector = when (iconName) {
    "picture_as_pdf" -> Icons.Rounded.PictureAsPdf
    "photo_library", "image", "collections" -> Icons.Rounded.PhotoLibrary
    "download", "file_download", "add_link" -> Icons.Rounded.Download
    "folder", "folder_open", "folder_shared" -> Icons.Rounded.Folder
    "folder_zip", "archive" -> Icons.Rounded.FolderZip
    "unarchive" -> Icons.Rounded.Unarchive
    "merge", "call_merge", "file_copy" -> Icons.Rounded.Merge
    "crop", "crop_rotate", "aspect_ratio" -> Icons.Rounded.Crop
    "compress" -> Icons.Rounded.Compress
    "visibility", "file_open" -> Icons.Rounded.Visibility
    "lock", "enhanced_encryption", "security" -> Icons.Rounded.Lock
    "verified_user", "shield" -> Icons.Rounded.VerifiedUser
    "settings", "tune" -> Icons.Rounded.Tune
    "search" -> Icons.Rounded.Search
    "share", "ios_share" -> Icons.Rounded.Share
    else -> Icons.Rounded.AutoAwesome
}

/**
 * Quiet Precision Floating Dock (380dp wide, 80dp tall, 32dp radius, 12dp above nav bar).
 * Features 4 equal slots: Home, Tools, Downloads, Files with 64x32dp active pill indicator.
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
                .fillMaxWidth(0.92f)
                .height(QuietTheme.spacing.dockHeight)
                .shadow(
                    elevation = 16.dp,
                    shape = QuietTheme.shapes.dockPill,
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                ),
            shape = QuietTheme.shapes.dockPill,
            color = pillars.dockSurface,
            border = BorderStroke(1.dp, pillars.dockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val label = if (language == AppLanguage.ARABIC) tab.labelAr else tab.labelEn
                    val icon = when (tab) {
                        DockTab.HOME -> Icons.Rounded.Home
                        DockTab.TOOLS -> Icons.Rounded.Build
                        DockTab.DOWNLOADS -> Icons.Rounded.Download
                        DockTab.FILES -> Icons.Rounded.Folder
                    }

                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) pillars.activePillContainer else Color.Transparent,
                        animationSpec = tween(220),
                        label = "dockPillBg"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) pillars.activePillContent else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(220),
                        label = "dockIconTint"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onSelectTab(tab) }
                            .semantics { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(32.dp)
                                .clip(QuietTheme.shapes.activeIndicatorPill)
                                .background(pillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = QuietTheme.typography.navigation.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Permanent 96dp Left-Hand Vertical Navigation Rail for Expanded Breakpoints (>= 840dp).
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
        modifier = modifier
            .width(96.dp)
            .fillMaxHeight(),
        color = pillars.cardSurface,
        border = BorderStroke(1.dp, pillars.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            DockTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val label = if (language == AppLanguage.ARABIC) tab.labelAr else tab.labelEn
                val icon = when (tab) {
                    DockTab.HOME -> Icons.Rounded.Home
                    DockTab.TOOLS -> Icons.Rounded.Build
                    DockTab.DOWNLOADS -> Icons.Rounded.Download
                    DockTab.FILES -> Icons.Rounded.Folder
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(QuietTheme.shapes.activeIndicatorPill)
                            .background(if (isSelected) pillars.activePillContainer else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) pillars.activePillContent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = QuietTheme.typography.navigation,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Standard Quiet Precision Top App Bar for Root Pillar Screens.
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
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(64.dp)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeText != null) {
                    QuietBadge(
                        text = badgeText,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onSearchClick != null) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onVaultClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "Profile & Vault",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Standard Workspace / Subscreen Top App Bar with Back Navigation, Subtitle, and Action Slot.
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
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, pillars.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop)
                .height(68.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
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
                            overflow = TextOverflow.Ellipsis
                        )
                        if (badgeText != null) {
                            QuietBadge(
                                text = badgeText,
                                containerColor = badgeBg,
                                contentColor = badgeFg
                            )
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                trailingContent?.invoke(this)
                if (onMoreClick != null) {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tactile Card Surface (20dp radius, 16dp internal padding, 1px hairline border).
 */
@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    containerColor: Color = QuietTheme.pillars.cardSurface,
    borderColor: Color = QuietTheme.pillars.cardBorder,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(QuietTheme.shapes.card)
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = cardModifier,
        shape = QuietTheme.shapes.card,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Semantic Pillar Badge / Tag.
 */
@Composable
fun QuietBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = QuietTheme.shapes.thumbnail,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = QuietTheme.typography.badge,
                color = contentColor
            )
        }
    }
}

/**
 * Primary Action Button (52dp height, 16dp radius, #4F3DFF in light / #8F86FF in dark).
 */
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
        enabled = enabled,
        shape = QuietTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(QuietTheme.spacing.primaryButtonHeight)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = QuietTheme.typography.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Tonal Pillar Button (44dp height, 16dp radius).
 */
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
        shape = QuietTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(QuietTheme.spacing.tonalButtonHeight)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = QuietTheme.typography.metadataStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Standard 52dp Search Bar with Voice & Filter triggers.
 */
@Composable
fun QuietSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClickBar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .let { if (onClickBar != null) it.clickable { onClickBar() } else it },
        shape = QuietTheme.shapes.inputField,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (query.isNotEmpty()) 2.dp else 1.dp,
            color = if (query.isNotEmpty()) pillars.primaryActionBackground else pillars.cardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (onClickBar != null && query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = QuietTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = QuietTheme.typography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    textStyle = QuietTheme.typography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = "Voice search",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Standard 68dp File Representation Row with 48dp leading pillar icon/thumbnail,
 * title (16/24 weight 500), tabular metadata subtitle (14/20), and trailing action/checkbox.
 */
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
    val pillars = QuietTheme.pillars
    val (iconBg, iconTint, iconVec) = when (file.extension.lowercase()) {
        "pdf" -> Triple(pillars.pdfContainer, pillars.pdfPrimary, Icons.Rounded.PictureAsPdf)
        "webp", "jpg", "jpeg", "png", "heic" -> Triple(pillars.imagesContainer, pillars.imagesPrimary, Icons.Rounded.PhotoLibrary)
        "zip", "tar.gz" -> Triple(pillars.filesContainer, pillars.filesPrimary, Icons.Rounded.FolderZip)
        else -> Triple(pillars.downloadsContainer, pillars.downloadsPrimary, Icons.Rounded.Description)
    }

    val subtitle = buildString {
        if (file.pageCount != null) append("${file.pageCount} pages · ")
        if (file.itemCount != null) append("${file.itemCount} items · ")
        if (file.dimensions != null) append("${file.dimensions} · ")
        append(file.formattedSize)
        append(" · ")
        append(if (language == AppLanguage.ARABIC) file.modifiedLabelAr else file.modifiedLabelEn)
    }

    QuietCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        borderColor = if (file.isSelected) pillars.primaryActionBackground else pillars.cardBorder,
        onClick = {
            if (showCheckbox && onToggleSelect != null) {
                onToggleSelect()
            } else {
                onClick()
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showCheckbox) {
                QuietCheckbox(
                    checked = file.isSelected,
                    onCheckedChange = { onToggleSelect?.invoke() }
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(QuietTheme.shapes.thumbnail)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVec,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuietBadge(
                        text = file.extension.uppercase(),
                        containerColor = iconBg,
                        contentColor = iconTint
                    )
                    Text(
                        text = file.name,
                        style = QuietTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subtitle,
                    style = QuietTheme.typography.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingActionText != null && onTrailingAction != null) {
                QuietTonalButton(
                    text = trailingActionText,
                    onClick = onTrailingAction
                )
            } else {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "File actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Custom 20dp Quiet Precision Checkbox with 6dp radius.
 */
@Composable
fun QuietCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillars = QuietTheme.pillars
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) pillars.primaryActionBackground else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (checked) pillars.primaryActionBackground else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = pillars.primaryActionText,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Interactive Split-Screen Before/After Image Texture Inspector (used in Compress Images img_04).
 */
@Composable
fun QuietBeforeAfterPreview(
    fileName: String,
    resolution: String,
    originalLabel: String,
    compressedLabel: String,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.52f) }
    val pillars = QuietTheme.pillars

    QuietCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount / size.width.toFloat().coerceAtLeast(1f)
                        splitFraction = (splitFraction + delta).coerceIn(0.15f, 0.85f)
                    }
                }
        ) {
            // Simulated high-contrast visual texture canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val splitX = size.width * splitFraction
                // Left side: Original crisp gradient
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF233347), Color(0xFF3A5B7C), Color(0xFF1A2530))
                    ),
                    size = size.copy(width = splitX)
                )
                // Right side: Compressed WebP/JPEG preview
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1F2D3D), Color(0xFF33516E), Color(0xFF162029))
                    ),
                    topLeft = Offset(splitX, 0f),
                    size = size.copy(width = size.width - splitX)
                )
                // Fine grid lines representing architectural facade texture
                val step = 28.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += step
                }
                // Divider line
                drawLine(
                    color = Color.White,
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, size.height),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color(0xFF4F3DFF),
                    radius = 16.dp.toPx(),
                    center = Offset(splitX, size.height / 2f)
                )
            }

            // Top badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuietBadge(
                    text = originalLabel,
                    containerColor = Color.Black.copy(alpha = 0.68f),
                    contentColor = Color.White
                )
                QuietBadge(
                    text = compressedLabel,
                    containerColor = pillars.primaryActionBackground,
                    contentColor = pillars.primaryActionText
                )
            }

            // Bottom caption
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$fileName · $resolution",
                    style = QuietTheme.typography.metadata,
                    color = Color.White
                )
                Text(
                    text = "Slide divider to inspect fine textures · Zoom 1:1",
                    style = QuietTheme.typography.badge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Privacy & On-Device Isolation Footer Banner present across Stitch screens.
 */
@Composable
fun QuietIsolationFooter(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = QuietTheme.shapes.button,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = QuietTheme.typography.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
