package com.jumlo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

/** Four primary tabs. Downloads remains a dedicated detail route opened from Home. */
val MainDockTabs = listOf(DockTab.HOME, DockTab.TOOLS, DockTab.FILES, DockTab.SETTINGS)

/**
 * Floating Liquid Glass bottom [NavigationBar] inspired by modern iOS tab bars:
 * sits in a translucent floating capsule with a subtle specular rim, spring-lifted
 * active icons, full system navigation-bar insets, and 48dp+ touch targets.
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
    // Reference NavigationBarDefaults.windowInsets for system inset parity inside the floating host.
    val navInsets = NavigationBarDefaults.windowInsets

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = QuietTheme.shapes.dockPill,
            color = pillars.dockSurface.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, pillars.dockBorder),
            shadowElevation = 0.dp
        ) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("navigation.bar"),
                containerColor = Color.Transparent,
                contentColor = colors.onSurface,
                tonalElevation = 0.dp,
                windowInsets = if (navInsets == WindowInsets(0, 0, 0, 0)) navInsets else WindowInsets(0, 0, 0, 0)
            ) {
                MainDockTabs.forEach { destination ->
                    val active = destination == selectedTab
                    val label = if (language.isRtl) destination.labelAr else destination.labelEn
                    val iconScaleState = animateFloatAsState(
                        targetValue = if (active) 1.09f else 0.93f,
                        animationSpec = if (reduced) snap() else spring(
                            dampingRatio = JumloMotion.bouncyDamping,
                            stiffness = JumloMotion.bouncyStiffness
                        ),
                        label = "nav-icon-scale-${destination.name}"
                    )
                    val iconLiftState = animateFloatAsState(
                        targetValue = if (active && !reduced) -2.5f else 0f,
                        animationSpec = if (reduced) snap() else spring(
                            dampingRatio = JumloMotion.fluidDamping,
                            stiffness = JumloMotion.fluidStiffness
                        ),
                        label = "nav-icon-lift-${destination.name}"
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
                            indicatorColor = colors.primaryContainer.copy(alpha = 0.92f),
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant
                        ),
                        icon = {
                            Box(
                                modifier = Modifier.testTag("nav.indicator.${destination.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(icon(destination)),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            val s = iconScaleState.value
                                            scaleX = s
                                            scaleY = s
                                            translationY = iconLiftState.value
                                        }
                                )
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
