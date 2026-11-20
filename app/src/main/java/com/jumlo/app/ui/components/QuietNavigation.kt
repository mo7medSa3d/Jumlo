package com.jumlo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.jumlo.app.ui.util.rememberAppHaptics

/** Four primary tabs. Downloads remains a dedicated detail route opened from Home. */
val MainDockTabs = listOf(DockTab.HOME, DockTab.TOOLS, DockTab.FILES, DockTab.SETTINGS)

/**
 * Floating iOS-style translucent oval navigation capsule.
 * Sized strictly to the capsule so all surrounding space remains transparent
 * and pass-through clickable to the scrolling content underneath.
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
    val isDark = colors.background.luminance() < 0.5f
    val haptics = rememberAppHaptics()
    val reduced = LocalReducedMotion.current
    val showLabels = LocalDensity.current.fontScale < 1.6f

    // Translucent frosted capsule without any full-width backdrop so touches outside pass straight through.
    val capsuleColor = pillars.dockSurface.copy(alpha = if (isDark) 0.80f else 0.84f)
    val capsuleBorder = if (isDark) {
        colors.onSurface.copy(alpha = 0.12f)
    } else {
        pillars.dockBorder.copy(alpha = 0.85f)
    }
    val activePillBg = if (isDark) {
        colors.primary.copy(alpha = 0.22f)
    } else {
        colors.primary.copy(alpha = 0.12f)
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .widthIn(min = 280.dp, max = 380.dp)
            .testTag("navigation.bar"),
        shape = CircleShape,
        color = capsuleColor,
        border = BorderStroke(1.dp, capsuleBorder),
        shadowElevation = if (isDark) 12.dp else 10.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MainDockTabs.forEach { destination ->
                val active = destination == selectedTab
                val label = if (language.isRtl) destination.labelAr else destination.labelEn
                val iconScaleState = animateFloatAsState(
                    targetValue = if (active) 1.08f else 0.94f,
                    animationSpec = if (reduced) snap() else spring(
                        dampingRatio = JumloMotion.bouncyDamping,
                        stiffness = JumloMotion.bouncyStiffness
                    ),
                    label = "nav-icon-scale-${destination.name}"
                )
                val iconLiftState = animateFloatAsState(
                    targetValue = if (active && !reduced) -1.5f else 0f,
                    animationSpec = if (reduced) snap() else spring(
                        dampingRatio = JumloMotion.fluidDamping,
                        stiffness = JumloMotion.fluidStiffness
                    ),
                    label = "nav-icon-lift-${destination.name}"
                )
                val interactionSource = remember { MutableInteractionSource() }
                val tint = if (active) colors.primary else colors.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(if (active) activePillBg else Color.Transparent)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                haptics.navigationTab()
                                if (!active) onSelectTab(destination)
                            }
                        )
                        .testTag("nav.${destination.name}")
                        .semantics {
                            contentDescription = label
                            selected = active
                            role = Role.Tab
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier.testTag("nav.indicator.${destination.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(icon(destination)),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer {
                                        val s = iconScaleState.value
                                        scaleX = s
                                        scaleY = s
                                        translationY = iconLiftState.value
                                    }
                            )
                        }
                        if (showLabels) {
                            Text(
                                text = label,
                                color = tint,
                                style = QuietTheme.typography.navigation.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
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
