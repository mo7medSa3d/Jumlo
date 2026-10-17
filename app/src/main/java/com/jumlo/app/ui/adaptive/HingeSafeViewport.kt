package com.jumlo.app.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private tailrec fun Context.hostActivity():Activity?=when(this) {
    is Activity -> this
    is ContextWrapper -> if(baseContext!==this) baseContext.hostActivity() else null
    else -> null
}

/** Content lives in one contiguous physical pane. Insets are still handled by its native shell. */
@Composable fun HingeSafeViewport(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val activity=remember(context) {context.hostActivity()}
    val updates=remember(activity,context) {
        if(activity==null) flowOf(emptyList<HingeBounds>())
        else WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).map {info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>()
                .filter {it.isSeparating || it.occlusionType==FoldingFeature.OcclusionType.FULL}
                .map {HingeBounds(PixelBounds(it.bounds.left,it.bounds.top,it.bounds.right,it.bounds.bottom),it.orientation==FoldingFeature.Orientation.VERTICAL)}
        }
    }
    val hinges by updates.collectAsStateWithLifecycle(initialValue=emptyList())
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    var rootPosition by remember {mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)}
    Layout(content=content,modifier=Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned {
        rootPosition=it.positionInWindow()
    }) {children,constraints->
        val root=PixelBounds(rootPosition.x.roundToInt(),rootPosition.y.roundToInt(),rootPosition.x.roundToInt()+constraints.maxWidth,rootPosition.y.roundToInt()+constraints.maxHeight)
        val viewport=safeViewport(root,hinges,rtl)
        val measured=children.map {it.measure(Constraints.fixed(viewport.width,viewport.height))}
        layout(constraints.maxWidth,constraints.maxHeight) {
            // place(), not placeRelative(): the geometry is already in physical window coordinates.
            measured.forEach {it.place(viewport.left-root.left,viewport.top-root.top)}
        }
    }
}
