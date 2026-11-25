package com.jumlo.app.ui.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
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
import com.jumlo.app.ui.icons.rounded.FolderOpen
import com.jumlo.app.ui.icons.rounded.InsertDriveFile
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme

const val ROOT_DROP_TARGET_ID = "__root__"

@Stable
class FileDragDropState {
    var activePointerId by mutableStateOf<PointerId?>(null)
        private set
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

    fun startDrag(pointerId: PointerId, ids: Set<String>, label: String, startInRoot: Offset) {
        if (ids.isEmpty()) return
        activePointerId = pointerId
        draggingIds = ids
        draggingLabel = label
        pointerInRoot = startInRoot
        hoveredTargetId = hitTest(startInRoot)
    }

    fun updateDragPosition(pointInRoot: Offset, onHoverChanged: () -> Unit) {
        if (!isDragging) return
        pointerInRoot = pointInRoot
        val next = hitTest(pointerInRoot)
        if (next != hoveredTargetId) {
            hoveredTargetId = next
            if (next != null) onHoverChanged()
        }
    }

    fun finishDrag(): Pair<Set<String>, String?> {
        val ids = draggingIds
        val target = hoveredTargetId
        activePointerId = null
        draggingIds = emptySet()
        draggingLabel = ""
        hoveredTargetId = null
        return ids to target
    }

    fun cancelDrag() {
        activePointerId = null
        draggingIds = emptySet()
        draggingLabel = ""
        hoveredTargetId = null
    }

    private fun hitTest(point: Offset): String? {
        dropTargets.entries.firstOrNull { (_, rect) -> rect.contains(point) }?.let { return it.key }
        return dropTargets.entries.firstOrNull { (_, rect) -> rect.inflate(32f).contains(point) }?.key
    }
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

/**
 * Root-level pointer tracker attached above LazyColumn.
 * Tracks and consumes ONLY dragState.activePointerId in PointerEventPass.Initial so other pointers
 * (e.g. a second finger) remain unconsumed to tap folders, tap breadcrumbs, or scroll while dragging.
 */
fun Modifier.fileDragDropContainer(
    dragState: FileDragDropState,
    onHoverHaptic: () -> Unit,
    onDrop: (Set<String>, String) -> Unit
): Modifier = composed {
    val latestHoverHaptic by rememberUpdatedState(onHoverHaptic)
    val latestOnDrop by rememberUpdatedState(onDrop)

    this.pointerInput(dragState) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val trackedId = dragState.activePointerId ?: continue
                val trackedChange = event.changes.firstOrNull { it.id == trackedId }
                if (trackedChange == null) {
                    if (event.changes.none { it.pressed }) dragState.cancelDrag()
                    continue
                }
                trackedChange.consume()
                when {
                    event.type == PointerEventType.Cancel -> dragState.cancelDrag()
                    trackedChange.changedToUpIgnoreConsumed() || !trackedChange.pressed -> {
                        val (ids, target) = dragState.finishDrag()
                        if (ids.isNotEmpty() && target != null) {
                            latestOnDrop(ids, target)
                        }
                    }
                    else -> {
                        val posInRoot = dragState.containerOriginInRoot + trackedChange.position
                        dragState.updateDragPosition(posInRoot, latestHoverHaptic)
                    }
                }
            }
        }
    }
}

fun Modifier.fileDragSource(
    itemId: String,
    itemLabel: String,
    dragState: FileDragDropState,
    resolveDragIds: () -> Set<String>,
    onDragStartHaptic: () -> Unit,
    onHoverHaptic: () -> Unit = {},
    onDrop: (Set<String>, String) -> Unit = { _, _ -> }
): Modifier = composed {
    val itemOrigin = remember { floatArrayOf(0f, 0f) }
    val latestResolve by rememberUpdatedState(resolveDragIds)
    val latestStartHaptic by rememberUpdatedState(onDragStartHaptic)
    val latestLabel by rememberUpdatedState(itemLabel)

    this
        .onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            itemOrigin[0] = pos.x
            itemOrigin[1] = pos.y
        }
        .pointerInput(itemId, dragState) {
            // Replaces per-item detectDragGesturesAfterLongPress so opening folders never cancels an active drag.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (dragState.isDragging) return@awaitEachGesture
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress != null && !dragState.isDragging) {
                    val ids = latestResolve()
                    if (ids.isNotEmpty()) {
                        longPress.consume()
                        latestStartHaptic()
                        val startInRoot = Offset(itemOrigin[0], itemOrigin[1]) + longPress.position
                        dragState.startDrag(longPress.id, ids, latestLabel, startInRoot)
                    }
                }
            }
        }
}

@Composable
fun ActiveDragDropBanner(
    dragState: FileDragDropState,
    activeFolderId: String?,
    activeFolderName: String?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val targetId = activeFolderId ?: ROOT_DROP_TARGET_ID
    val isHovered = dragState.isDragging && dragState.hoveredTargetId == targetId
    val count = dragState.draggingIds.size
    val scale by animateFloatAsState(
        targetValue = if (isHovered && !reduced) 1.02f else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "dropBannerScale"
    )

    AnimatedVisibility(
        visible = dragState.isDragging,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .fileDropTarget(targetId, dragState),
            shape = QuietTheme.shapes.card,
            color = if (isHovered) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0.55f),
            contentColor = colors.onPrimaryContainer,
            border = BorderStroke(
                width = if (isHovered) 2.5.dp else 1.5.dp,
                color = if (isHovered) colors.primary else colors.primary.copy(alpha = 0.65f)
            ),
            shadowElevation = if (isHovered) 10.dp else 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isHovered) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (activeFolderName != null) stringResource(R.string.files_drop_here, activeFolderName)
                        else stringResource(R.string.files_drop_root),
                        style = QuietTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDirection = TextDirection.Content
                        ),
                        color = if (isHovered) colors.primary else colors.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.files_dragging_hint),
                        style = QuietTheme.typography.metadata,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (count > 0) {
                    Surface(
                        shape = QuietTheme.shapes.pill,
                        color = colors.primary,
                        contentColor = colors.onPrimary
                    ) {
                        Text(
                            text = count.toString(),
                            style = QuietTheme.typography.badge,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
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
                            contentColor = MaterialTheme.colorScheme.onPrimary
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
