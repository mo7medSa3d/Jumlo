package com.jumlo.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.DragAndDropPermissions
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme

/** A grant remains alive until SourceStore has copied every dropped URI, including when tabs change. */
@Composable
fun DropFilesTarget(
    enabled: Boolean,
    onDropFiles: (List<String>, () -> Unit) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.activity() }
    val grants = remember(activity) { DropGrants(activity) }
    DisposableEffect(grants) { onDispose { grants.dispose() } }
    val currentDrop by rememberUpdatedState(onDropFiles)
    val currentFailure by rememberUpdatedState(onFailure)
    var entered by remember { mutableStateOf(false) }
    val target = remember(activity, grants) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { entered = true }
            override fun onExited(event: DragAndDropEvent) { entered = false }
            override fun onEnded(event: DragAndDropEvent) { entered = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                entered = false
                val androidEvent = event.toAndroidDragEvent()
                val clip = androidEvent.clipData ?: return false
                val uris = (0 until clip.itemCount.coerceAtMost(1000))
                    .mapNotNull { clip.getItemAt(it).uri?.takeIf { uri -> uri.scheme == "content" }?.toString() }
                    .distinct()
                if (uris.isEmpty()) return false
                var release: () -> Unit = {}
                return try {
                    release = grants.retain(activity?.requestDragAndDropPermissions(androidEvent))
                    currentDrop(uris, release)
                    true
                } catch (_: Exception) {
                    release()
                    currentFailure()
                    false
                }
            }
        }
    }
    Box(modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { enabled && it.mimeTypes().isNotEmpty() }, target = target
    )) {
        content()
        if (entered && enabled) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .96f)) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.modern_drop_files), style = QuietTheme.typography.screenTitle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.activity()
    else -> null
}

private class DropGrants(private val activity: Activity?) {
    private val pending = mutableSetOf<DragAndDropPermissions>()
    private val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    private var disposed = false
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) {
            pending.toList().forEach { runCatching { it.release() } }
            pending.clear()
        }
    }
    init { lifecycle?.addObserver(observer) }
    fun retain(permission: DragAndDropPermissions?): () -> Unit {
        permission ?: return {}
        pending += permission
        return {
            if (pending.remove(permission)) runCatching { permission.release() }
            if (disposed && pending.isEmpty()) lifecycle?.removeObserver(observer)
            Unit
        }
    }
    fun dispose() {
        disposed = true
        if (activity?.isDestroyed == true) {
            pending.toList().forEach { runCatching { it.release() } }
            pending.clear()
        }
        if (pending.isEmpty()) lifecycle?.removeObserver(observer)
    }
}
