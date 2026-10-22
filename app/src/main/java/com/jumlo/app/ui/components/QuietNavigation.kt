package com.jumlo.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/**
 * 2026 5-slot bottom navigation bar:
 * Slot 0: Tools | Slot 1: Files | Slot 2 (Center): Home | Slot 3: Downloads | Slot 4: Settings
 * Contained, centered, spring-animated active indicator pill that never overflows the icon/label slot.
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
    val showLabels = LocalDensity.current.fontScale < 1.5f
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val slot = when (selectedTab) {
        DockTab.TOOLS -> 0
        DockTab.FILES -> 1
        DockTab.HOME -> 2
        DockTab.DOWNLOADS -> 3
        DockTab.SETTINGS -> 4
        DockTab.PRINT -> 2
    }
    Box(
        modifier.fillMaxWidth().padding(bottom = bottom + 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(.96f),
            shape = RoundedCornerShape(30.dp),
            color = palette.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp)) {
                val horizontalInset = 8.dp
                val innerWidth = (maxWidth - horizontalInset * 2).coerceAtLeast(0.dp)
                val segment = innerWidth / 5
                val indicatorX by animateDpAsState(
                    targetValue = horizontalInset + segment * slot,
                    animationSpec = if (reduced) snap()
                        else spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                    label = "dock-active-position"
                )
                // Contained, centered animated active indicator pill inside the exact slot bounds.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = indicatorX)
                        .width(segment)
                        .height(56.dp)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (slot == 2) palette.primaryContainer else pillars.activePillContainer,
                            RoundedCornerShape(20.dp)
                        )
                )
                Row(
                    Modifier.fillMaxSize().padding(horizontal = horizontalInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockDestination(DockTab.TOOLS,
                        if (language.isRtl) DockTab.TOOLS.labelAr else DockTab.TOOLS.labelEn,
                        slot == 0,
                        showLabels,
                        Modifier.weight(1f)
                    ) { onSelectTab(DockTab.TOOLS) }

                    DockDestination(DockTab.FILES,
                        stringResource(R.string.neon_dock_documents),
                        slot == 1,
                        showLabels,
                        Modifier.weight(1f)
                    ) { onSelectTab(DockTab.FILES) }

                    // Center slot (Slot 2) is Home
                    DockDestination(DockTab.HOME,
                        if (language.isRtl) DockTab.HOME.labelAr else DockTab.HOME.labelEn,
                        slot == 2,
                        showLabels,
                        Modifier.weight(1f)
                    ) { onSelectTab(DockTab.HOME) }

                    DockDestination(DockTab.DOWNLOADS,
                        if (language.isRtl) DockTab.DOWNLOADS.labelAr else DockTab.DOWNLOADS.labelEn,
                        slot == 3,
                        showLabels,
                        Modifier.weight(1f)
                    ) { onSelectTab(DockTab.DOWNLOADS) }

                    DockDestination(DockTab.SETTINGS,
                        if (language.isRtl) DockTab.SETTINGS.labelAr else DockTab.SETTINGS.labelEn,
                        slot == 4,
                        showLabels,
                        Modifier.weight(1f)
                    ) { onSelectTab(DockTab.SETTINGS) }
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
        modifier.pressFeedback().heightIn(min = 52.dp).padding(horizontal = 4.dp).clickable {
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
        Icon(painterResource(icon(tab)), null, Modifier.size(22.dp).graphicsLayer {
            scaleX = bounce; scaleY = bounce
        }, tint = if (active) colors.primary else colors.onSurfaceVariant)
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = QuietTheme.typography.navigation.copy(
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                color = if (active) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
