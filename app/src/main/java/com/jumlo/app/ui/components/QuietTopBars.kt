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
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.MoreVert
import com.jumlo.app.ui.icons.rounded.Person
import com.jumlo.app.ui.icons.rounded.Search
import com.jumlo.app.ui.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.model.PillarCategory
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

@Composable
@Suppress("UNUSED_PARAMETER")
fun QuietRootTopBar(
    title: String,
    badgeText: String? = null,
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onAccountClick: (() -> Unit)? = null,
    onVaultClick: (() -> Unit)? = null,
    showSettings: Boolean = true
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = statusTop + 8.dp, bottom = 8.dp)
            .padding(horizontal = QuietTheme.spacing.canvasGutterCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = QuietTheme.typography.screenTitle,
                color = colors.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            badgeText?.let { QuietBadge(it, colors.primary, colors.onPrimary) }
            if (showSettings) QuietTonalIconButton(Icons.Rounded.Settings,
                stringResource(R.string.settings_title), onSettingsClick)
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
                toolId?.let { id ->
                    Box(Modifier.padding(start = 10.dp).size(44.dp)
                        .clip(RoundedCornerShape(14.dp)).background(badgeBg), contentAlignment = Alignment.Center) {
                        ToolIcon(id, badgeFg, Modifier.size(24.dp).toolSharedElement(id))
                    }
                }
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
