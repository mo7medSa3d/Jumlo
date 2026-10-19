package com.jumlo.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.LocalReducedMotion

/** Brief SVG-derived mark reveal while data initializes; has no blocking timers for navigation. */
@Composable fun JumloSplash(modifier: Modifier=Modifier) {
    val reduced=LocalReducedMotion.current
    var visible by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {visible=true}
    val scale by animateFloatAsState(if(visible) 1f else .83f,
        animationSpec=if(reduced) snap() else spring(dampingRatio=.68f,stiffness=Spring.StiffnessMediumLow),label="splash-logo")
    val alpha by animateFloatAsState(if(visible) 1f else 0f,
        animationSpec=if(reduced) snap() else spring(dampingRatio=Spring.DampingRatioNoBouncy,stiffness=Spring.StiffnessMedium),label="splash-opacity")
    Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF081317),Color(0xFF0C4743)))),
        contentAlignment=Alignment.Center) {
        Column(Modifier.graphicsLayer {scaleX=scale;scaleY=scale;this.alpha=alpha},horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(132.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0A5148),Color(0xFF178C7E)))),
                contentAlignment=Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground),contentDescription=null,
                    modifier=Modifier.fillMaxSize())
            }
            Text(stringResource(R.string.app_name),fontSize=34.sp,fontWeight=FontWeight.Bold,color=Color.White)
            Text(stringResource(R.string.brand_splash_tagline),fontSize=14.sp,color=Color(0xFFD7FFF6))
        }
    }
}
