package com.jumlo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.PillarCategory
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.MoreVert
import com.jumlo.app.ui.icons.rounded.Settings
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolIconColors

@Composable
@Suppress("UNUSED_PARAMETER")
fun QuietRootTopBar(
    title: String,
    titleContent: (@Composable () -> Unit)? = null,
    badgeText: String? = null,
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onAccountClick: (() -> Unit)? = null,
    onVaultClick: (() -> Unit)? = null,
    showSettings: Boolean = true
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = statusTop + 10.dp, bottom = 10.dp)
                    .padding(horizontal = QuietTheme.spacing.canvasGutterCompact),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (titleContent != null) {
                    Box(Modifier.weight(1f)) { titleContent() }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        JumloAppIconBadge(size = 30.dp, cornerRadius = 9.dp)
                        Text(
                            title,
                            style = QuietTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                badgeText?.let { QuietBadge(it, colors.primaryContainer, colors.onPrimaryContainer) }
                if (showSettings) QuietTonalIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    onClick = onSettingsClick,
                    containerColor = colors.surfaceContainer
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outlineVariant.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
fun QuietWorkspaceTopBar(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    pillar: PillarCategory = PillarCategory.PDF,
    onBack: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    toolId: String? = null
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val backCd = stringResource(R.string.scan_back)
    val moreCd = stringResource(R.string.action_customize)
    val (badgeBg, badgeFg) = when (pillar) {
        PillarCategory.PDF -> pillars.pdfContainer to pillars.pdfPrimary
        PillarCategory.IMAGES -> pillars.imagesContainer to pillars.imagesPrimary
        PillarCategory.DOWNLOADS -> pillars.downloadsContainer to pillars.downloadsPrimary
        PillarCategory.FILES -> pillars.filesContainer to pillars.filesPrimary
        PillarCategory.VAULT -> pillars.vaultContainer to pillars.vaultPrimary
    }

    Surface(
        color = colors.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusTop + 8.dp, bottom = 8.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    QuietTonalIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = backCd,
                        onClick = onBack,
                        containerColor = colors.surfaceContainer
                    )
                    toolId?.let { id ->
                        val iconBg = ToolIconColors.iconColorFor(id)
                        val iconTint = ToolIconColors.glyphOn(iconBg)
                        Box(
                            Modifier
                                .padding(start = 10.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            ToolIcon(id, iconTint, Modifier.size(22.dp).toolSharedElement(id))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = QuietTheme.typography.sectionTitle,
                                color = colors.onSurface,
                                maxLines = 2,
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
                                color = colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trailingContent?.invoke(this)
                    if (onMoreClick != null) {
                        QuietTonalIconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = moreCd,
                            onClick = onMoreClick,
                            containerColor = colors.surfaceContainer
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outlineVariant.copy(alpha = 0.55f))
            )
        }
    }
}
