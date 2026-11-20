package com.jumlo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme

/**
 * Exact replica of the Jumlo Android adaptive launcher icon:
 * 45° Cobalt-to-Violet gradient (`ic_launcher_background.xml`) beneath the white
 * folded-page glyph with luminous mint corner fold and royal-indigo J
 * (`ic_launcher_foreground.xml`).
 */
private val LauncherIconGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF2E67FF), Color(0xFF0E142B)),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

@Composable
fun JumloAppIconBadge(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    shape: Shape = QuietTheme.shapes.badge
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .clip(shape)
            .background(LauncherIconGradient)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center
    ) {
        // Adaptive icon foregrounds include an 18dp safe-zone margin on a 108dp canvas;
        // scaling by 1.44f crops cleanly to the exact home-screen launcher silhouette.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.44f
                    scaleY = 1.44f
                }
        )
    }
}

/**
 * The Jumlo top-bar wordmark: pairs the exact Jumlo app launcher icon with
 * crisp architectural typography and a luminous mint signature dot.
 * Always laid out left-to-right, even in Arabic.
 */
@Composable
fun JumloWordmark(modifier: Modifier = Modifier) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Jumlo",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = if (isRtl) 0.sp else (-0.6).sp,
                maxLines = 1
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
