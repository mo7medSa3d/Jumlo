package com.jumlo.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.LocalReducedMotion

/**
 * Branded start screen drawn entirely by Compose using a vector mark.
 * No blocking timer lives here: MainActivity owns readiness and cold-start duration.
 * Repeat motion is composed only when reduced motion is disabled.
 */
@Composable
fun JumloSplash(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val logoScale by animateFloatAsState(
        targetValue = if (revealed) 1f else .72f,
        animationSpec = if (reduced) snap() else spring(
            dampingRatio = .73f, stiffness = Spring.StiffnessMediumLow),
        label = "brand-entrance-scale"
    )
    val logoOpacity by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(440),
        label = "brand-entrance-fade"
    )
    val captionTranslate by animateFloatAsState(
        targetValue = if (revealed) 0f else 28f,
        animationSpec = if (reduced) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "brand-wordmark-rise"
    )
    // Avoid running an infinite transition on devices configured for reduced motion.
    val orbitRotation = if (reduced) 0f else {
        val atmosphere = rememberInfiniteTransition(label = "brand-orbit")
        val rotation by atmosphere.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "brand-orbit-rotation"
        )
        rotation
    }
    val breath = if (reduced) .28f else {
        val ambient = rememberInfiniteTransition(label = "brand-ambient")
        val value by ambient.animateFloat(
            initialValue = .16f, targetValue = .37f,
            animationSpec = infiniteRepeatable(tween(1350), RepeatMode.Reverse),
            label = "brand-ambient-opacity"
        )
        value
    }
    Box(
        modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(
                Color(0xFF0C1231), Color(0xFF231D66), Color(0xFF4434A9)
            ))
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 158.dp, y = (-205).dp)
                .size(400.dp).background(
                    Brush.radialGradient(listOf(Color(0xFF6B61EB).copy(alpha = .37f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-180).dp, y = 180.dp)
                .size(410.dp).background(
                    Brush.radialGradient(listOf(Color(0xFF3EABD0).copy(alpha = .16f), Color.Transparent)),
                    CircleShape
                )
        )
        // A low-contrast orbit signals motion without competing with the brand.
        Box(
            Modifier.size(282.dp).graphicsLayer { rotationZ = orbitRotation },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.align(Alignment.TopCenter).size(8.dp)
                    .background(Color(0xFF88F5D1).copy(alpha = breath + .37f), CircleShape)
            )
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = (-25).dp, y = (-25).dp)
                    .size(5.dp).background(Color.White.copy(alpha = .25f), CircleShape)
            )
        }
        Column(
            Modifier.padding(horizontal = 24.dp).graphicsLayer {
                alpha = logoOpacity
                translationY = captionTranslate
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.size(128.dp).graphicsLayer { scaleX = logoScale; scaleY = logoScale }
                    .clip(RoundedCornerShape(38.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6452E8), Color(0xFF8F68F4)))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_prism_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name),
                    fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.7).sp,
                    color = Color.White
                )
                Box(
                    Modifier.padding(start = 3.dp, top = 23.dp).size(8.dp)
                        .background(Color(0xFF81F2CE), CircleShape)
                )
            }
            Text(
                stringResource(R.string.brand_splash_tagline),
                fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
                color = Color(0xFFE0E5FF), textAlign = TextAlign.Center
            )
        }
    }
}
