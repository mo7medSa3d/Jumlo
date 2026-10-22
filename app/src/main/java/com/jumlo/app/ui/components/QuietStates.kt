package com.jumlo.app.ui.components

import com.jumlo.app.ui.theme.pressFeedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.jumlo.app.ui.icons.*
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
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
            modifier = Modifier.weight(1f)
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
    icon: ImageVector = Icons.Rounded.Bolt,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val brush = Brush.linearGradient(listOf(colors.primary, colors.tertiary))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(QuietTheme.shapes.card)
            .background(brush)
            .then(if (onClick != null) Modifier.pressFeedback().clickable(onClick = onClick) else Modifier)
            .padding(QuietTheme.spacing.cardInteriorPadding + 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = QuietTheme.typography.screenTitle,
                    color = colors.onPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = QuietTheme.typography.metadata,
                    color = colors.onPrimary.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(32.dp))
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
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(com.jumlo.app.ui.theme.JumloMotion.loadingPulseMs, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(QuietTheme.shapes.card)
            .background(colors.surfaceContainerHigh.copy(alpha = alpha))
    )
}

@Composable
fun QuietStateCard(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    QuietCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (isError) colors.errorContainer else QuietTheme.pillars.cardSurface,
        borderColor = if (isError) colors.error.copy(alpha = 0.65f) else QuietTheme.pillars.cardBorder
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 310.dp || LocalDensity.current.fontScale >= 1.75f
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietIconTile(
                        icon = if (isError) Icons.Rounded.Warning else Icons.Rounded.Info,
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
                        icon = if (isError) Icons.Rounded.Warning else Icons.Rounded.Info,
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
    val clickModifier = if (onClick != null) Modifier.clip(shape).clickable(onClick = {
        haptics.toolSelect()
        onClick()
    }) else Modifier
    Surface(
        modifier = modifier.then(clickModifier),
        shape = shape,
        color = containerColor,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
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
            .clip(QuietTheme.shapes.activeIndicatorPill)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 4.dp),
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
