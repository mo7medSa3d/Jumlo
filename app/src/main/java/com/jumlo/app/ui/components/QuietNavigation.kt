package com.jumlo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/** Four primary tabs. Downloads remains a dedicated detail route opened from Home. */
val MainDockTabs = listOf(DockTab.HOME, DockTab.TOOLS, DockTab.FILES, DockTab.SETTINGS)

@Composable
@Suppress("UNUSED_PARAMETER") // Retained for callers compiled against the previous dock API.
fun QuietFloatingDock(
    selectedTab: DockTab,
    language: AppLanguage,
    onSelectTab: (DockTab) -> Unit,
    onScan: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPrint: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val showLabels = LocalDensity.current.fontScale < 1.6f
    Surface(
        modifier = modifier.fillMaxWidth().testTag("navigation.bar"),
        color = colors.surfaceContainerLow,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 720.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainDockTabs.forEach { destination ->
                DockDestination(destination,
                    if (language.isRtl) destination.labelAr else destination.labelEn,
                    destination == selectedTab, showLabels, Modifier.weight(1f)) {
                    onSelectTab(destination)
                }
            }
        }
    }
}

private fun icon(tab: DockTab): Int = when (tab) {
    DockTab.HOME -> R.drawable.ic_prism_home
    DockTab.FILES -> R.drawable.ic_prism_folder
    DockTab.TOOLS -> R.drawable.ic_prism_grid
    DockTab.SETTINGS -> R.drawable.ic_prism_settings
    DockTab.DOWNLOADS -> R.drawable.ic_prism_download
    DockTab.PRINT -> R.drawable.ic_prism_printer
}

@Composable
private fun DockDestination(
    tab: DockTab,
    label: String,
    active: Boolean,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val reduced = LocalReducedMotion.current
    val indicatorColor by animateColorAsState(
        if (active) colors.primary else Color.Transparent,
        animationSpec = if (reduced) snap() else spring(stiffness = Spring.StiffnessMedium), label = "tab-container"
    )
    val scale by animateFloatAsState(
        if (active) 1f else .92f,
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab-icon-scale"
    )
    Column(
        modifier.heightIn(min = 64.dp).clip(QuietTheme.shapes.inputField)
            .selectable(selected = active, role = Role.Tab, onClick = {
                haptics.navigationTab()
                if (!active) onClick()
            }).testTag("nav.${tab.name}").semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Box(
            Modifier.fillMaxWidth(.88f).widthIn(max = 64.dp).height(34.dp)
                .clip(RoundedCornerShape(14.dp)).background(indicatorColor)
                .testTag("nav.indicator.${tab.name}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon(tab)), null,
                Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                tint = if (active) colors.onPrimary else colors.onSurfaceVariant)
        }
        if (showLabel) Text(label,
            style = QuietTheme.typography.navigation.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
            color = if (active) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {})
    }
}
