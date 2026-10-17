package com.jumlo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

private fun dockIcon(tab: DockTab): ImageVector = when (tab) {
    DockTab.HOME -> Icons.Rounded.Home
    DockTab.TOOLS -> Icons.Rounded.GridView
    DockTab.DOWNLOADS -> Icons.Rounded.Download
    DockTab.FILES -> Icons.Rounded.Folder
}

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
                .heightIn(min = QuietTheme.spacing.dockHeight)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val label = if (language == AppLanguage.ARABIC) tab.labelAr else tab.labelEn
                    NavItem(
                        icon = dockIcon(tab),
                        label = label,
                        selected = isSelected,
                        vertical = false,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.weight(if (isSelected) 1.7f else 1f).sizeIn(minHeight = 48.dp)
                    )
                }
            }
        }
    }
}

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
            .widthIn(min = 88.dp, max = 104.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, pillars.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 18.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            DockTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val label = if (language == AppLanguage.ARABIC) tab.labelAr else tab.labelEn
                NavItem(
                    icon = dockIcon(tab),
                    label = label,
                    selected = isSelected,
                    vertical = true,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.width(84.dp).heightIn(min = 60.dp)
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
    val pillBg = if(selected) pillars.activePillContainer else Color.Transparent
    val tint = if(selected) pillars.activePillContent else MaterialTheme.colorScheme.onSurfaceVariant
    val haptics = rememberAppHaptics()
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(QuietTheme.shapes.activeIndicatorPill)
            .clickable(onClick = {
                haptics.navigationTab()
                onClick()
            })
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
                    ,
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
                    ,
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
