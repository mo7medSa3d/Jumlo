package com.jumlo.app.ui.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Folder
import com.jumlo.app.ui.icons.rounded.InsertDriveFile
import com.jumlo.app.ui.theme.QuietTheme

const val ROOT_DROP_TARGET_ID = "__root__"

@Stable
class FileDragDropState {
    var draggingIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var draggingLabel by mutableStateOf("")
        private set
    var pointerInRoot by mutableStateOf(Offset.Zero)
        private set
    var containerOriginInRoot by mutableStateOf(Offset.Zero)
    var hoveredTargetId by mutableStateOf<String?>(null)
        private set

    private val dropTargets = HashMap<String, Rect>()

    val isDragging: Boolean get() = draggingIds.isNotEmpty()

    fun registerDropTarget(id: String, bounds: Rect) {
        dropTargets[id] = bounds
    }

    fun unregisterDropTarget(id: String) {
        dropTargets.remove(id)
        if (hoveredTargetId == id) hoveredTargetId = null
    }

    fun startDrag(ids: Set<String>, label: String, startInRoot: Offset) {
        if (ids.isEmpty()) return
        draggingIds = ids
        draggingLabel = label
        pointerInRoot = startInRoot
        hoveredTargetId = hitTest(startInRoot)
    }

    fun updateDrag(delta: Offset, onHoverChanged: () -> Unit) {
        if (!isDragging) return
        pointerInRoot += delta
        val next = hitTest(pointerInRoot)
        if (next != hoveredTargetId) {
            hoveredTargetId = next
            if (next != null) onHoverChanged()
        }
    }

    fun finishDrag(): Pair<Set<String>, String?> {
        val ids = draggingIds
        val target = hoveredTargetId
        draggingIds = emptySet()
        draggingLabel = ""
        hoveredTargetId = null
        return ids to target
    }

    fun cancelDrag() {
        draggingIds = emptySet()
        draggingLabel = ""
        hoveredTargetId = null
    }

    private fun hitTest(point: Offset): String? =
        dropTargets.entries.firstOrNull { (_, rect) -> rect.inflate(48f).contains(point) }?.key
}

@Composable
fun rememberFileDragDropState(): FileDragDropState = remember { FileDragDropState() }

fun Modifier.fileDropTarget(
    targetId: String,
    dragState: FileDragDropState
): Modifier = composed {
    DisposableEffect(targetId, dragState) {
        onDispose { dragState.unregisterDropTarget(targetId) }
    }
    this.onGloballyPositioned { coords ->
        dragState.registerDropTarget(targetId, coords.boundsInRoot())
    }
}

fun Modifier.fileDragSource(
    itemId: String,
    itemLabel: String,
    dragState: FileDragDropState,
    resolveDragIds: () -> Set<String>,
    onDragStartHaptic: () -> Unit,
    onHoverHaptic: () -> Unit,
    onDrop: (Set<String>, String) -> Unit
): Modifier = composed {
    val itemOrigin = remember { floatArrayOf(0f, 0f) }
    val latestResolve by rememberUpdatedState(resolveDragIds)
    val latestStartHaptic by rememberUpdatedState(onDragStartHaptic)
    val latestHoverHaptic by rememberUpdatedState(onHoverHaptic)
    val latestOnDrop by rememberUpdatedState(onDrop)
    val latestLabel by rememberUpdatedState(itemLabel)

    this
        .onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            itemOrigin[0] = pos.x
            itemOrigin[1] = pos.y
        }
        .pointerInput(itemId, dragState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { localOffset ->
                    val ids = latestResolve()
                    if (ids.isNotEmpty()) {
                        latestStartHaptic()
                        dragState.startDrag(ids, latestLabel, Offset(itemOrigin[0], itemOrigin[1]) + localOffset)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragState.updateDrag(dragAmount, latestHoverHaptic)
                },
                onDragEnd = {
                    val (ids, target) = dragState.finishDrag()
                    if (ids.isNotEmpty() && target != null) {
                        latestOnDrop(ids, target)
                    }
                },
                onDragCancel = {
                    dragState.cancelDrag()
                }
            )
        }
}

@Composable
fun FloatingDragGhost(
    dragState: FileDragDropState,
    modifier: Modifier = Modifier
) {
    val isOverTarget = dragState.hoveredTargetId != null
    val count = dragState.draggingIds.size

    AnimatedVisibility(
        visible = dragState.isDragging,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val localOffset = dragState.pointerInRoot - dragState.containerOriginInRoot
                translationX = (localOffset.x - 110f).coerceAtLeast(12f)
                translationY = (localOffset.y - 110f).coerceAtLeast(12f)
            }
        ) {
            Surface(
                shape = QuietTheme.shapes.dockPill,
                color = if (isOverTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (isOverTarget) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    1.5.dp,
                    if (isOverTarget) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                shadowElevation = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(min = 140.dp, max = 260.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isOverTarget) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (count > 1) stringResource(R.string.files_folder_drag_count, count) else dragState.draggingLabel,
                        style = QuietTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDirection = TextDirection.Content
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (count > 1) {
                        Surface(
                            shape = QuietTheme.shapes.pill,
                            color = if (isOverTarget) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.primary,
                            contentColor = if (isOverTarget) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = count.toString(),
                                style = QuietTheme.typography.badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
