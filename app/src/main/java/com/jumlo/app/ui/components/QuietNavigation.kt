package com.jumlo.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/** Floating four-destination dock plus the real camera-scanning action.
 * Downloads remain reachable from Home and Tools; the route and history are preserved.
 * Row start follows Compose RTL automatically, including the animated selection shape.
 */
@Composable
fun QuietFloatingDock(
    selectedTab: DockTab,
    language: AppLanguage,
    onSelectTab: (DockTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val reduced = LocalReducedMotion.current
    val haptics = rememberAppHaptics()
    val showLabels = LocalDensity.current.fontScale < 1.5f
    val scanLabel = stringResource(R.string.neon_scan_action)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val slot = when (selectedTab) {
        DockTab.HOME -> 0
        DockTab.FILES -> 1
        DockTab.TOOLS -> 3
        DockTab.SETTINGS -> 4
        DockTab.DOWNLOADS -> -1
    }
    Box(
        modifier.fillMaxWidth().padding(bottom = bottom + 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(.96f).height(78.dp)
                .shadow(14.dp, QuietTheme.shapes.dockPill,
                    ambientColor = Color.Black.copy(alpha = .22f)),
            shape = QuietTheme.shapes.dockPill,
            color = pillars.dockSurface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, pillars.dockBorder)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val segment = (maxWidth - 12.dp) / 5
                val position by animateDpAsState(
                    targetValue = 6.dp + segment * slot.coerceAtLeast(0),
                    animationSpec = if (reduced) snap()
                        else spring(dampingRatio = JumloMotion.indicatorDamping,
                            stiffness = Spring.StiffnessMedium),
                    label = "neon-dock-position"
                )
                if (slot >= 0) {
                    Box(Modifier.align(Alignment.CenterStart)
                        .offset(x = position).width(segment).height(62.dp)
                        .padding(horizontal = 2.dp)
                        .background(pillars.activePillContainer.copy(alpha = .67f),
                            QuietTheme.shapes.activeIndicatorPill))
                }
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockDestination(DockTab.HOME, if (language.isRtl) DockTab.HOME.labelAr
                        else DockTab.HOME.labelEn, slot == 0, showLabels,
                        Modifier.weight(1f)) { onSelectTab(DockTab.HOME) }
                    DockDestination(DockTab.FILES, stringResource(R.string.neon_dock_documents),
                        slot == 1, showLabels, Modifier.weight(1f)) {
                        onSelectTab(DockTab.FILES)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Surface(
                            onClick = { haptics.actionClick(); onScan() },
                            shape = CircleShape,
                            color = palette.primary,
                            contentColor = palette.onPrimary,
                            shadowElevation = 8.dp,
                            modifier = Modifier.pressFeedback().offset(y = (-9).dp)
                                .size(66.dp).semantics {
                                    contentDescription = scanLabel
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.DocumentScanner, null, Modifier.size(28.dp))
                            }
                        }
                    }
                    DockDestination(DockTab.TOOLS, if (language.isRtl) DockTab.TOOLS.labelAr
                        else DockTab.TOOLS.labelEn, slot == 3, showLabels,
                        Modifier.weight(1f)) { onSelectTab(DockTab.TOOLS) }
                    DockDestination(DockTab.SETTINGS, stringResource(R.string.neon_dock_me),
                        slot == 4, showLabels, Modifier.weight(1f)) {
                        onSelectTab(DockTab.SETTINGS)
                    }
                }
            }
        }
    }
}

private fun icon(tab: DockTab): ImageVector = when (tab) {
    DockTab.HOME -> Icons.Rounded.Home
    DockTab.FILES -> Icons.Rounded.Folder
    DockTab.TOOLS -> Icons.Rounded.GridView
    DockTab.SETTINGS -> Icons.Rounded.Person
    DockTab.DOWNLOADS -> Icons.Rounded.Folder
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
    val haptics = rememberAppHaptics()
    val reduced = LocalReducedMotion.current
    val colors = MaterialTheme.colorScheme
    val bounce by animateFloatAsState(
        if (active && !reduced) 1.08f else 1f,
        animationSpec = if (reduced) snap()
            else spring(dampingRatio = .69f, stiffness = Spring.StiffnessMedium),
        label = "dock-icon-spring"
    )
    Column(
        modifier.pressFeedback().heightIn(min = 52.dp).clickable {
            haptics.navigationTab()
            if (!active) onClick()
        }.semantics {
            contentDescription = label
            role = Role.Tab
            selected = active
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon(tab), null, Modifier.size(23.dp).graphicsLayer {
            scaleX = bounce; scaleY = bounce
        }, tint = if (active) colors.primary else colors.onSurfaceVariant)
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = QuietTheme.typography.navigation.copy(
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                color = if (active) colors.primary else colors.onSurfaceVariant)
        }
    }
}
