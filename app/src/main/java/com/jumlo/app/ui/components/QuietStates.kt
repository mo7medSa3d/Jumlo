package com.jumlo.app.ui.components

import com.jumlo.app.ui.theme.pressFeedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Bolt
import com.jumlo.app.ui.icons.rounded.Info
import com.jumlo.app.ui.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics

@Composable
fun QuietSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = QuietTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (trailingText != null && onTrailingClick != null) {
            TextButton(onClick = onTrailingClick, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text(trailingText, style = QuietTheme.typography.metadataStrong, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun QuietHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Bolt,
    onClick: (() -> Unit)? = null
) {
    val system = com.jumlo.app.ui.theme.LocalColorSystem.current
    val brush = remember(system) { Brush.linearGradient(listOf(system.splashStart, system.splashMid, system.splashEnd)) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(QuietTheme.shapes.card)
            .background(brush)
            .then(if (onClick != null) Modifier.pressFeedback().clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(QuietTheme.spacing.cardInteriorPadding + 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = QuietTheme.typography.screenTitle,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = subtitle,
                    style = QuietTheme.typography.metadata,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(QuietTheme.shapes.inputField)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun QuietShimmerSkeleton(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    if (reduceMotion || com.jumlo.app.ui.theme.LocalReducedMotion.current) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(QuietTheme.shapes.card)
                .background(colors.surfaceContainerHigh)
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alphaState = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(com.jumlo.app.ui.theme.JumloMotion.loadingPulseMs, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val baseColor = colors.surfaceContainerHigh
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(QuietTheme.shapes.card)
            .drawBehind { drawRect(baseColor.copy(alpha = alphaState.value)) }
    )
}

@Composable
fun QuietStateCard(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info
) {
    val colors = MaterialTheme.colorScheme
    QuietCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        containerColor = if (isError) colors.errorContainer else QuietTheme.pillars.cardSurface,
        borderColor = if (isError) colors.error.copy(alpha = 0.65f) else QuietTheme.pillars.cardBorder
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 310.dp || LocalDensity.current.fontScale >= 1.75f
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietIconTile(
                        icon = if (isError) Icons.Rounded.Warning else icon,
                        tint = if (isError) colors.onErrorContainer else colors.primary,
                        container = if (isError) colors.errorContainer else colors.primaryContainer,
                        size = 44.dp
                    )
                    Text(text = title, modifier = Modifier.semantics { heading() },
                        style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isError) colors.onErrorContainer else colors.onSurface)
                    Text(text = subtitle, style = QuietTheme.typography.metadata,
                        color = if (isError) colors.onErrorContainer else colors.onSurfaceVariant)
                }
            } else {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuietIconTile(
                        icon = if (isError) Icons.Rounded.Warning else icon,
                        tint = if (isError) colors.onErrorContainer else colors.primary,
                        container = if (isError) colors.errorContainer else colors.primaryContainer,
                        size = 44.dp
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = title, modifier = Modifier.semantics { heading() },
                            style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isError) colors.onErrorContainer else colors.onSurface)
                        Text(text = subtitle, style = QuietTheme.typography.metadata,
                            color = if (isError) colors.onErrorContainer else colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            QuietTonalButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    containerColor: Color = QuietTheme.pillars.cardSurface,
    borderColor: Color = QuietTheme.pillars.cardBorder,
    contentPadding: PaddingValues = PaddingValues(QuietTheme.spacing.cardInteriorPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = QuietTheme.shapes.card
    val haptics = rememberAppHaptics()
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
    if (onClick != null) {
        Surface(
            onClick = {
                haptics.toolSelect()
                onClick()
            },
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 1.dp,
            content = body
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 1.dp,
            content = body
        )
    }
}

@Composable
fun QuietBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(QuietTheme.shapes.badge)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        }
        Text(
            text = text,
            style = QuietTheme.typography.badge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
