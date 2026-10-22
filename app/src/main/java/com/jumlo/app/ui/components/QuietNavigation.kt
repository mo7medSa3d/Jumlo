package com.jumlo.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    modifier: Modifier = Modifier,
    onPrint: (() -> Unit)? = null
) {
    val palette = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val reduced = LocalReducedMotion.current
    val haptics = rememberAppHaptics()
    val showLabels = LocalDensity.current.fontScale < 1.5f
    val scanLabel = if(onPrint!=null) stringResource(R.string.upgrade_print) else stringResource(R.string.neon_scan_action)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Four destinations and one primary action. Home and Tools were the same screen
    // to the user, so only one of them is in the bar.
    val slot = when (selectedTab) {
        DockTab.HOME -> 0
        DockTab.TOOLS -> 1
        DockTab.PRINT -> 2
        DockTab.FILES -> 3
        DockTab.DOWNLOADS -> 4
        DockTab.SETTINGS -> 0
    }
    Box(
        modifier.fillMaxWidth().padding(bottom = bottom + 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(.96f),
            shape = RoundedCornerShape(30.dp),
            color = palette.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
        ) {
            Column {
                BoxWithConstraints(Modifier.fillMaxWidth().height(74.dp)) {
                val segment = maxWidth / 5
                val indicatorX by animateDpAsState(
                    targetValue = 6.dp + segment * slot,
                    animationSpec = if (reduced) snap()
                        else spring(dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium),
                    label = "dock-active-position"
                )
                if (slot >= 0 && slot != 2) {
                    Box(Modifier.align(Alignment.CenterStart)
                        .offset(x = indicatorX)
                        .width(segment).height(52.dp)
                        .padding(horizontal = 6.dp)
                        .background(pillars.activePillContainer, RoundedCornerShape(18.dp)))
                }
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockDestination(DockTab.HOME, if (language.isRtl) DockTab.HOME.labelAr
                        else DockTab.HOME.labelEn, slot == 0, showLabels,
                        Modifier.weight(1f)) { onSelectTab(DockTab.HOME) }
                    DockDestination(DockTab.TOOLS, if (language.isRtl) DockTab.TOOLS.labelAr
                        else DockTab.TOOLS.labelEn, slot == 1, showLabels,
                        Modifier.weight(1f)) { onSelectTab(DockTab.TOOLS) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Surface(
                            onClick = { haptics.actionClick(); (onPrint ?: onScan)() },
                            shape = RoundedCornerShape(23.dp),
                            color = palette.primary,
                            contentColor = palette.onPrimary,
                            shadowElevation = QuietTheme.elevation.floating,
                            modifier = Modifier.pressFeedback().offset(y = (-9).dp)
                                .size(66.dp).semantics {
                                    contentDescription = scanLabel
                                    selected = selectedTab == DockTab.PRINT
                                    role = Role.Tab
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(if(onPrint!=null) R.drawable.ic_prism_printer else R.drawable.ic_prism_scan), null, Modifier.size(29.dp))
                            }
                        }
                    }
                    DockDestination(DockTab.FILES, stringResource(R.string.neon_dock_documents),
                        slot == 3, showLabels, Modifier.weight(1f)) {
                        onSelectTab(DockTab.FILES)
                    }
                    DockDestination(DockTab.DOWNLOADS, if (language.isRtl) DockTab.DOWNLOADS.labelAr
                        else DockTab.DOWNLOADS.labelEn, slot == 4, showLabels,
                        Modifier.weight(1f)) { onSelectTab(DockTab.DOWNLOADS) }
                }
                }
            }
        }
    }
}

private fun icon(tab: DockTab): Int = when (tab) {
    DockTab.HOME -> R.drawable.ic_prism_home
    DockTab.FILES -> R.drawable.ic_prism_folder
    DockTab.TOOLS -> R.drawable.ic_prism_grid
    DockTab.SETTINGS -> R.drawable.ic_prism_user
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
        Icon(painterResource(icon(tab)), null, Modifier.size(24.dp).graphicsLayer {
            scaleX = bounce; scaleY = bounce
        }, tint = if (active) colors.primary else colors.onSurfaceVariant)
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = QuietTheme.typography.navigation.copy(
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                color = if (active) colors.primary else colors.onSurfaceVariant,
                // The column already carries the label; announcing the Text too reads it twice.
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
