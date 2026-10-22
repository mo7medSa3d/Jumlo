package com.jumlo.app.ui.components

import com.jumlo.app.ui.theme.pressFeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.jumlo.app.ui.icons.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.model.PillarCategory
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

@Composable
fun QuietRootTopBar(
    title: String,
    badgeText: String? = null,
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onAccountClick: (() -> Unit)? = null,
    onVaultClick: (() -> Unit)? = null
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val colors = MaterialTheme.colorScheme
    val settingsCd = stringResource(R.string.settings_title)
    val accountAction = onAccountClick ?: onVaultClick
    val haptics = rememberAppHaptics()
    Surface(color = colors.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 8.dp, bottom = 10.dp)
                .padding(horizontal = QuietTheme.spacing.canvasGutterCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painterResource(R.drawable.ic_prism_brand_mark),
                        contentDescription = null, modifier = Modifier.size(37.dp))
                }
                Text(
                    text = title,
                    style = QuietTheme.typography.screenTitle.copy(letterSpacing = (-0.8).sp),
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

            // Search icon removed from top bars per 2026 navigation guidelines; search lives inside pages.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if(accountAction == null) QuietTonalIconButton(Icons.Rounded.Settings, settingsCd, onSettingsClick)
                Surface(
                    onClick = {
                        haptics.actionClick()
                        accountAction?.invoke()
                    },
                    enabled = accountAction != null,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.pressFeedback().size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = stringResource(R.string.ui_account_data),
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
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
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
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
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTop + 8.dp, bottom = 8.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                QuietTonalIconButton(Icons.AutoMirrored.Rounded.ArrowBack, backCd, onBack)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    QuietTonalIconButton(Icons.Rounded.MoreVert, moreCd, onMoreClick)
                }
            }
        }
    }
}
