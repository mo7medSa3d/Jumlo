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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.DockTab
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

private fun dockIcon(tab: DockTab): ImageVector = when (tab) {
    DockTab.HOME -> Icons.Rounded.Home
    DockTab.TOOLS -> Icons.Rounded.GridView
    DockTab.DOWNLOADS -> Icons.Rounded.Download
    DockTab.FILES -> Icons.Rounded.Folder
    DockTab.SETTINGS -> Icons.Rounded.Settings
}

/** One moving, spring-driven selection shape; automatically reverses in RTL via CenterStart + offset(x). */
@Composable
fun QuietFloatingDock(selectedTab: DockTab, language: AppLanguage,
    onSelectTab: (DockTab) -> Unit, modifier: Modifier = Modifier) {
    val pillars = QuietTheme.pillars
    val reduced = LocalReducedMotion.current
    val direction = LocalLayoutDirection.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabs = DockTab.entries
    Box(modifier.fillMaxWidth().padding(bottom=bottom + 12.dp),contentAlignment=Alignment.BottomCenter) {
        Surface(
            modifier=Modifier.widthIn(max=400.dp).fillMaxWidth(.94f).height(74.dp)
                .shadow(20.dp,QuietTheme.shapes.dockPill,ambientColor=Color.Black.copy(alpha=.16f)),
            shape=QuietTheme.shapes.dockPill, color=pillars.dockSurface,
            tonalElevation=6.dp,
            border=BorderStroke(1.dp,pillars.dockBorder)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val segment=(maxWidth-16.dp)/tabs.size
                val index=tabs.indexOf(selectedTab).coerceAtLeast(0)
                val visualIndex=if(direction==LayoutDirection.Rtl) tabs.lastIndex-index else index
                // In Compose RTL, Alignment.CenterStart anchors to the right edge and Modifier.offset(x)
                // shifts toward the end (left), so 8.dp + segment * index moves from Right to Left in Arabic.
                val startOffset=if(direction==LayoutDirection.Rtl) (tabs.lastIndex - visualIndex) else visualIndex
                val x by animateDpAsState(
                    targetValue=8.dp + segment * startOffset,
                    animationSpec=if(reduced) snap() else spring(
                        dampingRatio=JumloMotion.indicatorDamping,stiffness=Spring.StiffnessMedium),
                    label="dock-morph-offset"
                )
                Box(Modifier.offset(x=x).align(Alignment.CenterStart)
                    .width(segment).height(57.dp).padding(horizontal=2.dp)
                    .background(pillars.activePillContainer, RoundedCornerShape(27.dp)))
                Row(Modifier.fillMaxSize().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    tabs.forEach { tab ->
                        val label=if(language.isRtl) tab.labelAr else tab.labelEn
                        DockItem(tab,label,tab==selectedTab,
                            modifier=Modifier.weight(1f),onClick={onSelectTab(tab)})
                    }
                }
            }
        }
    }
}

@Composable
private fun DockItem(tab:DockTab,label:String,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    val haptics=rememberAppHaptics()
    val colors=QuietTheme.pillars
    val reduced=LocalReducedMotion.current
    val bounce by animateFloatAsState(if(selected && !reduced) 1.13f else 1f,
        animationSpec=if(reduced) snap() else spring(dampingRatio=.68f,stiffness=Spring.StiffnessMedium),label="dock-icon")
    Column(modifier.pressFeedback().heightIn(min=52.dp).clickable {
        haptics.navigationTab()
        if(!selected) onClick()
    }.semantics {contentDescription=label;role=Role.Tab;this.selected=selected},
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Icon(dockIcon(tab),contentDescription=null,modifier=Modifier.size(23.dp).graphicsLayer {
            scaleX=bounce;scaleY=bounce
        },tint=if(selected) colors.activePillContent else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,
            style=QuietTheme.typography.navigation.copy(fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium),
            color=if(selected) colors.activePillContent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
