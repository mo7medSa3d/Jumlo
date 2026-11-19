package com.jumlo.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

/** Four primary tabs. Downloads remains a dedicated detail route opened from Home. */
val MainDockTabs = listOf(DockTab.HOME, DockTab.TOOLS, DockTab.FILES, DockTab.SETTINGS)

/**
 * Standardized Material 3 bottom [NavigationBar] following modern thumb-zone
 * ergonomics, system navigation-bar insets, 48dp+ touch targets, and Jumlo's
 * primary brand icon on the Home destination.
 */
@Composable
fun JumloNavigationBar(
    selectedTab: DockTab,
    language: AppLanguage,
    onSelectTab: (DockTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val haptics = rememberAppHaptics()
    val reduced = LocalReducedMotion.current
    val showLabels = LocalDensity.current.fontScale < 1.6f

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 1.dp,
            color = pillars.dockBorder
        )
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("navigation.bar"),
            containerColor = pillars.dockSurface,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            MainDockTabs.forEach { destination ->
                val active = destination == selectedTab
                val label = if (language.isRtl) destination.labelAr else destination.labelEn
                val iconScale by animateFloatAsState(
                    targetValue = if (active) 1f else 0.92f,
                    animationSpec = if (reduced) snap() else spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "nav-icon-scale-${destination.name}"
                )
                val brandAlpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0.78f,
                    animationSpec = if (reduced) snap() else spring(stiffness = Spring.StiffnessMedium),
                    label = "nav-brand-alpha-${destination.name}"
                )

                NavigationBarItem(
                    selected = active,
                    onClick = {
                        haptics.navigationTab()
                        if (!active) onSelectTab(destination)
                    },
                    alwaysShowLabel = showLabels,
                    modifier = Modifier
                        .testTag("nav.${destination.name}")
                        .semantics { contentDescription = label },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onPrimaryContainer,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant
                    ),
                    icon = {
                        Box(
                            modifier = Modifier.testTag("nav.indicator.${destination.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (destination == DockTab.HOME) {
                                JumloAppIconBadge(
                                    size = 24.dp,
                                    cornerRadius = 7.dp,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                        alpha = brandAlpha
                                    }
                                )
                            } else {
                                Icon(
                                    painter = painterResource(icon(destination)),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                            }
                        }
                    },
                    label = if (showLabels) {
                        {
                            Text(
                                text = label,
                                style = QuietTheme.typography.navigation.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER") // Retained for callers and tests compiled against the previous dock API.
fun QuietFloatingDock(
    selectedTab: DockTab,
    language: AppLanguage,
    onSelectTab: (DockTab) -> Unit,
    modifier: Modifier = Modifier,
    onScan: () -> Unit = {},
    onPrint: (() -> Unit)? = null
) {
    JumloNavigationBar(
        selectedTab = selectedTab,
        language = language,
        onSelectTab = onSelectTab,
        modifier = modifier
    )
}

private fun icon(tab: DockTab): Int = when (tab) {
    DockTab.HOME -> R.drawable.ic_prism_home
    DockTab.FILES -> R.drawable.ic_prism_folder
    DockTab.TOOLS -> R.drawable.ic_prism_grid
    DockTab.SETTINGS -> R.drawable.ic_prism_settings
    DockTab.DOWNLOADS -> R.drawable.ic_prism_download
    DockTab.PRINT -> R.drawable.ic_prism_printer
}
