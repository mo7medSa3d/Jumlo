package com.jumlo.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.DataLoadStatus
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.QuietTheme

/** Loading and errors have distinct accessible states; an empty repository is not a loader. */
@Composable
fun DocumentLoadState(status:DataLoadStatus,onRetry:()->Unit) {
    if(status==DataLoadStatus.ERROR) {
        QuietStateCard(stringResource(R.string.upgrade_loading_failed),"",
            stringResource(R.string.action_retry_failed),onRetry,isError=true)
    } else if(status==DataLoadStatus.LOADING) {
        val label=stringResource(R.string.upgrade_loading_documents)
        val reduced=LocalReducedMotion.current
        val opacity=if(reduced) .65f else {
            val transition=rememberInfiniteTransition(label="document-loading")
            val animated by transition.animateFloat(.42f,.75f,
                infiniteRepeatable(tween(JumloMotion.loadingPulseMs),RepeatMode.Reverse),label="placeholder-opacity")
            animated
        }
        Column(Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription=label; liveRegion=LiveRegionMode.Polite
        },verticalArrangement=Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(Modifier.fillMaxWidth().height(72.dp).clip(QuietTheme.shapes.card)
                    .graphicsLayer { alpha=opacity }
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
    }
}
