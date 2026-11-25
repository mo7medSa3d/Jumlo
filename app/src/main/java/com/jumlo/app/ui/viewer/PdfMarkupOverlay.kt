package com.jumlo.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.core.NormPoint
import com.jumlo.app.core.PdfMarkupItem
import com.jumlo.app.core.PdfMarkupPolicy
import com.jumlo.app.core.PdfMarkupTool
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.Redo
import com.jumlo.app.ui.icons.automirrored.rounded.Undo
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun PdfMarkupPageLayer(
    pageIndex: Int,
    items: List<PdfMarkupItem>,
    activeTool: PdfMarkupTool,
    activeColor: Color,
    penWidthNorm: Float,
    highlighterWidthNorm: Float,
    eraserRadiusNorm: Float,
    textSizePt: Float,
    checkSizeNorm: Float,
    signatureScaleNorm: Float,
    pendingSignaturePath: String?,
    selectedItemId: String?,
    onSelectItem: (String?) -> Unit,
    onAddItem: (PdfMarkupItem) -> Unit,
    onRequestTextAt: (Int, Float, Float) -> Unit,
    onMoveItem: (String, Float, Float) -> Unit,
    onResizeSignature: (String, Float, Float) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var livePoints by remember(pageIndex) { mutableStateOf<List<NormPoint>>(emptyList()) }
    var eraserCursor by remember(pageIndex) { mutableStateOf<NormPoint?>(null) }
    // Local GPU-only drag/resize state tracked in stationary page coordinates: zero layout oscillation, 120fps iOS glide.
    var draggingItemId by remember(pageIndex) { mutableStateOf<String?>(null) }
    var dragDeltaPx by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var resizingItemId by remember(pageIndex) { mutableStateOf<String?>(null) }
    var resizeDeltaPx by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val isSelectMode = activeTool == PdfMarkupTool.SELECT

    LaunchedEffect(activeTool) {
        livePoints = emptyList()
        eraserCursor = null
        if (activeTool != PdfMarkupTool.SELECT) {
            draggingItemId = null
            dragDeltaPx = Offset.Zero
            resizingItemId = null
            resizeDeltaPx = Offset.Zero
        }
    }

    val latestItems by rememberUpdatedState(items)
    val latestSelectedId by rememberUpdatedState(selectedItemId)
    val latestColor by rememberUpdatedState(activeColor)
    val latestPenWidth by rememberUpdatedState(penWidthNorm)
    val latestHlWidth by rememberUpdatedState(highlighterWidthNorm)
    val latestEraserRadius by rememberUpdatedState(eraserRadiusNorm)
    val latestTextSize by rememberUpdatedState(textSizePt)
    val latestCheckSize by rememberUpdatedState(checkSizeNorm)
    val latestSigScale by rememberUpdatedState(signatureScaleNorm)
    val latestSigPath by rememberUpdatedState(pendingSignaturePath)
    val latestOnSelect by rememberUpdatedState(onSelectItem)
    val latestOnAdd by rememberUpdatedState(onAddItem)
    val latestOnRequestText by rememberUpdatedState(onRequestTextAt)
    val latestOnMove by rememberUpdatedState(onMoveItem)
    val latestOnResize by rememberUpdatedState(onResizeSignature)
    val latestOnDelete by rememberUpdatedState(onDeleteItem)

    Box(
        contentAlignment = AbsoluteAlignment.TopLeft,
        modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }
            .pointerInput(pageIndex, activeTool) {
                if (activeTool == PdfMarkupTool.NONE) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Yield immediately if 2+ fingers are down so pinch-zoom/pan wins cleanly.
                    if (currentEvent.changes.count { it.pressed } >= 2) return@awaitEachGesture
                    val w = boxSize.width.toFloat()
                    val h = boxSize.height.toFloat()
                    if (w <= 0f || h <= 0f) return@awaitEachGesture

                    when (activeTool) {
                        PdfMarkupTool.SELECT -> {
                            // If a child control (delete button or resize handle) already consumed down, yield cleanly.
                            if (down.isConsumed) return@awaitEachGesture
                            val startNx = (down.position.x / w).coerceIn(0f, 1f)
                            val startNy = (down.position.y / h).coerceIn(0f, 1f)
                            val currentSel = latestSelectedId
                            val hit = latestItems.firstOrNull { it.id == currentSel && PdfMarkupPolicy.hitTestItem(it, startNx, startNy) }
                                ?: latestItems.asReversed().firstOrNull { PdfMarkupPolicy.hitTestItem(it, startNx, startNy) }

                            if (hit == null) {
                                // Empty space: never consume drag so 1-finger PDF scrolling stays completely smooth; tap clears selection.
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) return@awaitEachGesture
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                    if (!change.pressed) break
                                }
                                if (!moved && latestSelectedId != null) latestOnSelect(null)
                                return@awaitEachGesture
                            }

                            // Hit an item: select immediately and track butter-smooth GPU drag in stationary page coordinates.
                            down.consume()
                            if (latestSelectedId != hit.id) {
                                latestOnSelect(hit.id)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            draggingItemId = hit.id
                            dragDeltaPx = Offset.Zero
                            var totalDelta = Offset.Zero
                            var movedBeyondSlop = false
                            var cancelledByPinch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    cancelledByPinch = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                val delta = change.position - down.position
                                if (!movedBeyondSlop && delta.getDistance() > viewConfiguration.touchSlop) {
                                    movedBeyondSlop = true
                                }
                                if (movedBeyondSlop) {
                                    totalDelta = delta
                                    dragDeltaPx = delta
                                }
                            }
                            if (!cancelledByPinch && movedBeyondSlop && totalDelta != Offset.Zero) {
                                latestOnMove(hit.id, totalDelta.x / w, totalDelta.y / h)
                            }
                            draggingItemId = null
                            dragDeltaPx = Offset.Zero
                        }

                        PdfMarkupTool.PEN, PdfMarkupTool.HIGHLIGHTER -> {
                            down.consume()
                            val pts = mutableListOf(NormPoint((down.position.x / w).coerceIn(0f, 1f), (down.position.y / h).coerceIn(0f, 1f)))
                            livePoints = pts.toList()
                            var cancelledByPinch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    cancelledByPinch = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                val nx = (change.position.x / w).coerceIn(0f, 1f)
                                val ny = (change.position.y / h).coerceIn(0f, 1f)
                                val last = pts.last()
                                if (hypot(nx - last.x, ny - last.y) >= 0.0015f) {
                                    pts.add(NormPoint(nx, ny))
                                    livePoints = pts.toList()
                                }
                            }
                            if (!cancelledByPinch && pts.isNotEmpty()) {
                                val id = UUID.randomUUID().toString()
                                val item = if (activeTool == PdfMarkupTool.HIGHLIGHTER) {
                                    PdfMarkupItem.HighlightStroke(id, pageIndex, pts.toList(), latestColor.toArgb(), latestHlWidth)
                                } else {
                                    PdfMarkupItem.InkStroke(id, pageIndex, pts.toList(), latestColor.toArgb(), latestPenWidth)
                                }
                                latestOnAdd(item)
                            }
                            livePoints = emptyList()
                        }

                        PdfMarkupTool.ERASER -> {
                            down.consume()
                            val erasedIds = mutableSetOf<String>()
                            fun eraseAt(px: Float, py: Float) {
                                val nx = (px / w).coerceIn(0f, 1f)
                                val ny = (py / h).coerceIn(0f, 1f)
                                eraserCursor = NormPoint(nx, ny)
                                latestItems.forEach { item ->
                                    if (item.id !in erasedIds && PdfMarkupPolicy.hitTestEraser(item, nx, ny, latestEraserRadius)) {
                                        erasedIds.add(item.id)
                                        latestOnDelete(item.id)
                                    }
                                }
                            }
                            eraseAt(down.position.x, down.position.y)
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) break
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                eraseAt(change.position.x, change.position.y)
                            }
                            eraserCursor = null
                        }

                        PdfMarkupTool.TEXT, PdfMarkupTool.DATE, PdfMarkupTool.CHECK, PdfMarkupTool.SIGNATURE -> {
                            var movedTooFar = false
                            var cancelledByPinch = false
                            var lastPos = down.position
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    cancelledByPinch = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                lastPos = change.position
                                if ((lastPos - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    movedTooFar = true
                                }
                                if (!change.pressed) break
                            }
                            if (!cancelledByPinch && !movedTooFar) {
                                latestOnSelect(null)
                                val nx = (lastPos.x / w).coerceIn(0f, 1f)
                                val ny = (lastPos.y / h).coerceIn(0f, 1f)
                                when (activeTool) {
                                    PdfMarkupTool.TEXT -> latestOnRequestText(pageIndex, nx, ny)
                                    PdfMarkupTool.DATE -> {
                                        val today = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT).format(Date())
                                        val draft = PdfMarkupItem.TextBox(
                                            UUID.randomUUID().toString(),
                                            pageIndex,
                                            nx,
                                            ny,
                                            today,
                                            latestColor.toArgb(),
                                            latestTextSize
                                        )
                                        val b = PdfMarkupPolicy.boundingBox(draft)
                                        latestOnAdd(
                                            draft.copy(
                                                normX = (nx - b.width * 0.5f).coerceIn(0.02f, (1f - b.width).coerceAtLeast(0.02f)),
                                                normY = (ny - b.height * 0.5f).coerceIn(0.02f, (1f - b.height).coerceAtLeast(0.02f))
                                            )
                                        )
                                    }
                                    PdfMarkupTool.CHECK -> {
                                        val draft = PdfMarkupItem.CheckMark(
                                            UUID.randomUUID().toString(),
                                            pageIndex,
                                            nx,
                                            ny,
                                            false,
                                            latestColor.toArgb(),
                                            latestCheckSize
                                        )
                                        val b = PdfMarkupPolicy.boundingBox(draft)
                                        latestOnAdd(
                                            draft.copy(
                                                normX = (nx - b.width * 0.5f).coerceIn(0.02f, (1f - b.width).coerceAtLeast(0.02f)),
                                                normY = (ny - b.height * 0.5f).coerceIn(0.02f, (1f - b.height).coerceAtLeast(0.02f))
                                            )
                                        )
                                    }
                                    PdfMarkupTool.SIGNATURE -> {
                                        val sigPath = latestSigPath
                                        if (sigPath != null) {
                                            val sigW = latestSigScale.coerceIn(0.10f, 0.70f)
                                            val sigH = (sigW * 0.36f).coerceIn(0.05f, 0.35f)
                                            val newId = UUID.randomUUID().toString()
                                            latestOnAdd(
                                                PdfMarkupItem.SavedSignatureStamp(
                                                    newId,
                                                    pageIndex,
                                                    sigPath,
                                                    (nx - sigW * 0.5f).coerceIn(0.02f, (1f - sigW).coerceAtLeast(0.02f)),
                                                    (ny - sigH * 0.5f).coerceIn(0.02f, (1f - sigH).coerceAtLeast(0.02f)),
                                                    sigW,
                                                    sigH
                                                )
                                            )
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }

                        PdfMarkupTool.NONE -> Unit
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun drawNormStroke(pts: List<NormPoint>, argb: Int, widthNorm: Float, alpha: Float) {
                if (pts.isEmpty()) return
                val strokePx = (widthNorm * minOf(size.width, size.height)).coerceAtLeast(2f)
                val color = Color(argb).copy(alpha = alpha)
                if (pts.size == 1) {
                    drawCircle(color = color, radius = strokePx * 0.5f, center = Offset(pts[0].x * size.width, pts[0].y * size.height))
                    return
                }
                val path = Path().apply {
                    moveTo(pts.first().x * size.width, pts.first().y * size.height)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        val px = prev.x * size.width
                        val py = prev.y * size.height
                        val cx = cur.x * size.width
                        val cy = cur.y * size.height
                        quadraticBezierTo(px, py, (px + cx) * 0.5f, (py + cy) * 0.5f)
                    }
                    lineTo(pts.last().x * size.width, pts.last().y * size.height)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            items.forEach { item ->
                val isDragging = draggingItemId == item.id && dragDeltaPx != Offset.Zero
                when (item) {
                    is PdfMarkupItem.InkStroke -> {
                        if (isDragging) translate(dragDeltaPx.x, dragDeltaPx.y) {
                            drawNormStroke(item.points, item.colorArgb, item.widthNorm, 1f)
                        } else {
                            drawNormStroke(item.points, item.colorArgb, item.widthNorm, 1f)
                        }
                    }
                    is PdfMarkupItem.HighlightStroke -> {
                        if (isDragging) translate(dragDeltaPx.x, dragDeltaPx.y) {
                            drawNormStroke(item.points, item.colorArgb, item.widthNorm, item.alpha)
                        } else {
                            drawNormStroke(item.points, item.colorArgb, item.widthNorm, item.alpha)
                        }
                    }
                    else -> Unit
                }
            }
            if (livePoints.isNotEmpty()) {
                val isHl = activeTool == PdfMarkupTool.HIGHLIGHTER
                drawNormStroke(
                    livePoints,
                    activeColor.toArgb(),
                    if (isHl) highlighterWidthNorm else penWidthNorm,
                    if (isHl) 0.35f else 1f
                )
            }
            eraserCursor?.let { pt ->
                val radiusPx = (eraserRadiusNorm * minOf(size.width, size.height)).coerceAtLeast(10f)
                val center = Offset(pt.x * size.width, pt.y * size.height)
                drawCircle(color = Color(0x33EF5350), radius = radiusPx, center = center)
                drawCircle(color = Color(0xCCEF5350), radius = radiusPx, center = center, style = Stroke(width = 2f))
            }
        }
        if (boxSize.width > 0 && boxSize.height > 0) {
            items.forEach { item ->
                key(item.id) {
                val isDragging = draggingItemId == item.id
                val isSelected = isSelectMode && selectedItemId == item.id
                val gpuTransform = Modifier.graphicsLayer {
                    if (isDragging) {
                        translationX = dragDeltaPx.x
                        translationY = dragDeltaPx.y
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                }
                when (item) {
                    is PdfMarkupItem.TextBox -> {
                        Box(
                            Modifier
                                .absoluteOffset { IntOffset((item.normX * boxSize.width).roundToInt(), (item.normY * boxSize.height).roundToInt()) }
                                .then(gpuTransform)
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier.background(Color.White.copy(alpha = 0.72f), QuietTheme.shapes.chip)
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                item.text,
                                color = Color(item.colorArgb),
                                fontSize = item.fontSizePt.sp,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content)
                            )
                        }
                    }

                    is PdfMarkupItem.CheckMark -> {
                        val sizeSp = (item.sizeNorm * 480f).coerceIn(14f, 44f).sp
                        Box(
                            Modifier
                                .absoluteOffset { IntOffset((item.normX * boxSize.width).roundToInt(), (item.normY * boxSize.height).roundToInt()) }
                                .then(gpuTransform)
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 4.dp)
                                    } else Modifier
                                )
                        ) {
                            Text(
                                if (item.isCross) "✗" else "✓",
                                color = Color(item.colorArgb),
                                fontSize = sizeSp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    is PdfMarkupItem.SavedSignatureStamp -> {
                        val bmp by produceState<Bitmap?>(null, item.signaturePath) {
                            value = withContext(Dispatchers.IO) {
                                try { BitmapFactory.decodeFile(item.signaturePath) }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { null }
                                catch (_: OutOfMemoryError) { null }
                            }
                        }
                        DisposeOwnedPreviewBitmap(bmp)
                        val isResizing = resizingItemId == item.id
                        val liveW = (item.normW * boxSize.width + if (isResizing) resizeDeltaPx.x else 0f).coerceIn(boxSize.width * 0.08f, boxSize.width * 0.85f)
                        val liveH = (item.normH * boxSize.height + if (isResizing) resizeDeltaPx.y else 0f).coerceIn(boxSize.height * 0.04f, boxSize.height * 0.55f)
                        val wDp = with(density) { liveW.toDp() }
                        val hDp = with(density) { liveH.toDp() }
                        val readyBmp = bmp
                        if (readyBmp != null && !readyBmp.isRecycled) {
                            val imageBitmap = remember(readyBmp) { readyBmp.asImageBitmap() }
                            Box(
                                Modifier
                                    .absoluteOffset { IntOffset((item.normX * boxSize.width).roundToInt(), (item.normY * boxSize.height).roundToInt()) }
                                    .then(gpuTransform)
                                    .size(wDp, hDp)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                        } else Modifier
                                    )
                            ) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(3.dp)
                                )
                            }
                        }
                    }

                    else -> Unit
                }
                }
            }

            // Selection frame and handles are visible only in Select mode (all item types).
            if (isSelectMode && selectedItemId != null) {
                val selected = items.firstOrNull { it.id == selectedItemId }
                if (selected != null) {
                    val b = PdfMarkupPolicy.boundingBox(selected)
                    val isDraggingSel = draggingItemId == selected.id
                    val isResizingSel = resizingItemId == selected.id
                    val frameW = (b.width * boxSize.width + if (isResizingSel) resizeDeltaPx.x else 0f).coerceAtLeast(with(density) { 36.dp.toPx() })
                    val frameH = (b.height * boxSize.height + if (isResizingSel) resizeDeltaPx.y else 0f).coerceAtLeast(with(density) { 28.dp.toPx() })
                    val frameWDp = with(density) { frameW.toDp() }
                    val frameHDp = with(density) { frameH.toDp() }
                    val isStroke = selected is PdfMarkupItem.InkStroke || selected is PdfMarkupItem.HighlightStroke

                    Box(
                        Modifier
                            .absoluteOffset { IntOffset((b.x * boxSize.width).roundToInt(), (b.y * boxSize.height).roundToInt()) }
                            .graphicsLayer {
                                if (isDraggingSel) {
                                    translationX = dragDeltaPx.x
                                    translationY = dragDeltaPx.y
                                }
                            }
                            .size(frameWDp, frameHDp)
                            .then(
                                if (isStroke) {
                                    Modifier
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                                } else Modifier
                            )
                    ) {
                        Surface(
                            onClick = { latestOnDelete(selected.id) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            shadowElevation = 4.dp,
                            modifier = Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = 10.dp, y = (-10).dp).size(24.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.s8_markup_delete_item),
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Bottom-end smooth resize handle for signatures (accumulates delta cleanly without oscillation)
                        if (selected is PdfMarkupItem.SavedSignatureStamp) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(AbsoluteAlignment.BottomRight)
                                    .absoluteOffset(x = 10.dp, y = 10.dp)
                                    .size(26.dp)
                                    .pointerInput("resize-${selected.id}", boxSize) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (currentEvent.changes.count { it.pressed } >= 2) return@awaitEachGesture
                                            down.consume()
                                            resizingItemId = selected.id
                                            resizeDeltaPx = Offset.Zero
                                            var accum = Offset.Zero
                                            var cancelled = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.count { it.pressed } >= 2) {
                                                    cancelled = true
                                                    break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) {
                                                    change.consume()
                                                    break
                                                }
                                                val step = change.position - change.previousPosition
                                                change.consume()
                                                accum += step
                                                resizeDeltaPx = accum
                                            }
                                            if (!cancelled && accum != Offset.Zero && boxSize.width > 0 && boxSize.height > 0) {
                                                latestOnResize(selected.id, accum.x / boxSize.width, accum.y / boxSize.height)
                                            }
                                            resizingItemId = null
                                            resizeDeltaPx = Offset.Zero
                                        }
                                    }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.ZoomOutMap,
                                        contentDescription = stringResource(R.string.s8_markup_resize_signature),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PdfMarkupFloatingBar(
    activeTool: PdfMarkupTool,
    activeColor: Color,
    penWidthNorm: Float,
    highlighterWidthNorm: Float,
    eraserRadiusNorm: Float,
    textSizePt: Float,
    checkSizeNorm: Float,
    signatureScaleNorm: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    hasEdits: Boolean,
    saving: Boolean,
    onSelectTool: (PdfMarkupTool) -> Unit,
    onSelectColor: (Color) -> Unit,
    onPenWidthChange: (Float) -> Unit,
    onHighlighterWidthChange: (Float) -> Unit,
    onEraserRadiusChange: (Float) -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onCheckSizeChange: (Float) -> Unit,
    onSignatureScaleChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveInPlace: () -> Unit,
    onSaveAsCopy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val quickPalette = remember {
        listOf(Color(0xFF2E67FF), Color(0xFFFFD60A), Color(0xFF0E142B), Color(0xFFE53935), Color(0xFF2E7D32))
    }
    var colorWheelOpen by remember { mutableStateOf(false) }
    var saveMenuOpen by remember { mutableStateOf(false) }

    val supportsColor = activeTool == PdfMarkupTool.PEN ||
        activeTool == PdfMarkupTool.HIGHLIGHTER ||
        activeTool == PdfMarkupTool.TEXT ||
        activeTool == PdfMarkupTool.DATE ||
        activeTool == PdfMarkupTool.CHECK

    LaunchedEffect(supportsColor) {
        if (!supportsColor) colorWheelOpen = false
    }

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(visible = colorWheelOpen && supportsColor) {
            HsvColorWheelCard(
                color = activeColor,
                onColorChange = onSelectColor,
                onDismiss = { colorWheelOpen = false }
            )
        }

        if (activeTool != PdfMarkupTool.NONE && activeTool != PdfMarkupTool.SELECT) {
            Surface(
                shape = QuietTheme.shapes.pill,
                color = colors.surfaceContainerHigh.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, pillars.cardBorder),
                shadowElevation = 6.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (supportsColor) {
                        quickPalette.forEach { swatch ->
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(swatch)
                                    .border(
                                        if (activeColor == swatch) 2.5.dp else 1.dp,
                                        if (activeColor == swatch) colors.primary else pillars.cardBorder,
                                        CircleShape
                                    )
                                    .clickable { onSelectColor(swatch) }
                            )
                        }
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color.Red, Color.Yellow, Color.Green,
                                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                        )
                                    )
                                )
                                .border(
                                    if (colorWheelOpen || activeColor !in quickPalette) 2.5.dp else 1.dp,
                                    if (colorWheelOpen) colors.primary else Color.White,
                                    CircleShape
                                )
                                .clickable { colorWheelOpen = !colorWheelOpen }
                                .testTag("pdf.markup.colorwheel.toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(activeColor).border(1.dp, Color.White, CircleShape))
                        }
                    }

                    val sliderConfig = when (activeTool) {
                        PdfMarkupTool.PEN -> Triple(penWidthNorm, 0.0015f..0.020f, onPenWidthChange)
                        PdfMarkupTool.HIGHLIGHTER -> Triple(highlighterWidthNorm, 0.010f..0.055f, onHighlighterWidthChange)
                        PdfMarkupTool.ERASER -> Triple(eraserRadiusNorm, 0.015f..0.080f, onEraserRadiusChange)
                        PdfMarkupTool.TEXT, PdfMarkupTool.DATE -> Triple(textSizePt, 8f..36f, onTextSizeChange)
                        PdfMarkupTool.CHECK -> Triple(checkSizeNorm, 0.022f..0.085f, onCheckSizeChange)
                        PdfMarkupTool.SIGNATURE -> Triple(signatureScaleNorm, 0.12f..0.60f, onSignatureScaleChange)
                        PdfMarkupTool.NONE, PdfMarkupTool.SELECT -> null
                    }
                    if (sliderConfig != null) {
                        val (value, range, onChange) = sliderConfig
                        val label = when (activeTool) {
                            PdfMarkupTool.TEXT, PdfMarkupTool.DATE -> "${value.roundToInt()}pt"
                            PdfMarkupTool.SIGNATURE -> "${(value * 100f).roundToInt()}%"
                            else -> stringResource(R.string.s8_markup_size)
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Slider(
                            value = value.coerceIn(range.start, range.endInclusive),
                            onValueChange = onChange,
                            valueRange = range,
                            modifier = Modifier.width(if (supportsColor) 96.dp else 160.dp).height(28.dp)
                        )
                    }
                }
            }
        }

        Surface(
            shape = QuietTheme.shapes.pill,
            color = colors.surfaceContainerHigh.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, pillars.cardBorder),
            shadowElevation = 10.dp,
            modifier = Modifier.testTag("pdf.markup.bar")
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                MarkupToolChip(Icons.Rounded.NearMe, stringResource(R.string.markup_tool_select), activeTool == PdfMarkupTool.SELECT) { onSelectTool(PdfMarkupTool.SELECT) }
                MarkupToolChip(Icons.Rounded.Edit, stringResource(R.string.markup_tool_pen), activeTool == PdfMarkupTool.PEN) { onSelectTool(PdfMarkupTool.PEN) }
                MarkupToolChip(Icons.Rounded.AutoAwesome, stringResource(R.string.markup_tool_highlight), activeTool == PdfMarkupTool.HIGHLIGHTER) { onSelectTool(PdfMarkupTool.HIGHLIGHTER) }
                MarkupToolChip(Icons.Rounded.CleaningServices, stringResource(R.string.s8_markup_tool_eraser), activeTool == PdfMarkupTool.ERASER) { onSelectTool(PdfMarkupTool.ERASER) }
                MarkupToolChip(Icons.Rounded.FormatSize, stringResource(R.string.markup_tool_text), activeTool == PdfMarkupTool.TEXT) { onSelectTool(PdfMarkupTool.TEXT) }
                MarkupToolChip(Icons.Rounded.Schedule, stringResource(R.string.markup_tool_date), activeTool == PdfMarkupTool.DATE) { onSelectTool(PdfMarkupTool.DATE) }
                MarkupToolChip(Icons.Rounded.Check, stringResource(R.string.markup_tool_check), activeTool == PdfMarkupTool.CHECK) { onSelectTool(PdfMarkupTool.CHECK) }
                MarkupToolChip(Icons.Rounded.Palette, stringResource(R.string.markup_tool_sign), activeTool == PdfMarkupTool.SIGNATURE) { onSelectTool(PdfMarkupTool.SIGNATURE) }
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.markup_undo), modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.markup_redo), modifier = Modifier.size(19.dp))
                }
                Box {
                    FilledTonalButton(
                        onClick = { if (hasEdits && !saving) onSaveInPlace() },
                        enabled = hasEdits && !saving,
                        shape = QuietTheme.shapes.pill,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp).testTag("pdf.markup.save")
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.s8_markup_saving), style = QuietTheme.typography.metadataStrong)
                        } else {
                            Text(stringResource(R.string.action_save), style = QuietTheme.typography.metadataStrong)
                        }
                    }
                    DropdownMenu(expanded = saveMenuOpen, onDismissRequest = { saveMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.s8_markup_save_in_place)) },
                            onClick = { saveMenuOpen = false; onSaveInPlace() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.s8_markup_save_copy)) },
                            onClick = { saveMenuOpen = false; onSaveAsCopy() }
                        )
                    }
                }
                if (hasEdits && !saving) {
                    IconButton(onClick = { saveMenuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.action_save_as), modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_close), modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun HsvColorWheelCard(
    color: Color,
    onColorChange: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val initialHsv = remember(color) {
        FloatArray(3).also { AndroidColor.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.05f)) }
    val latestOnColorChange by rememberUpdatedState(onColorChange)
    LaunchedEffect(color) {
        val currentArgb = Color.hsv(hue, saturation, value).toArgb()
        if (currentArgb != color.toArgb()) {
            hue = initialHsv[0]
            saturation = initialHsv[1]
            value = initialHsv[2].coerceAtLeast(0.05f)
        }
    }

    val selectedColor = remember(hue, saturation, value) { Color.hsv(hue, saturation, value) }
    val hexText = remember(selectedColor) {
        val argb = selectedColor.toArgb()
        String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    }

    Surface(
        shape = QuietTheme.shapes.card,
        color = colors.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, pillars.cardBorder),
        shadowElevation = 12.dp,
        modifier = Modifier.width(248.dp).testTag("pdf.markup.colorwheel")
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(selectedColor).border(1.dp, pillars.cardBorder, CircleShape))
                    Text(hexText, style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_close), modifier = Modifier.size(16.dp))
                }
            }

            Canvas(
                Modifier
                    .size(156.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun pick(pos: Offset) {
                                val cx = size.width * 0.5f
                                val cy = size.height * 0.5f
                                val radius = minOf(cx, cy).coerceAtLeast(1f)
                                val dx = pos.x - cx
                                val dy = pos.y - cy
                                val angle = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
                                hue = ((angle % 360f) + 360f) % 360f
                                saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                                latestOnColorChange(Color.hsv(hue, saturation, value))
                            }
                            down.consume()
                            pick(down.position)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                pick(change.position)
                            }
                        }
                    }
            ) {
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                val radius = minOf(size.width, size.height) * 0.5f
                val hueColors = listOf(
                    Color.hsv(0f, 1f, value),
                    Color.hsv(60f, 1f, value),
                    Color.hsv(120f, 1f, value),
                    Color.hsv(180f, 1f, value),
                    Color.hsv(240f, 1f, value),
                    Color.hsv(300f, 1f, value),
                    Color.hsv(360f, 1f, value)
                )
                drawCircle(brush = Brush.sweepGradient(hueColors, center = center), radius = radius, center = center)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.hsv(0f, 0f, value), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                val rad = Math.toRadians(hue.toDouble())
                val thumbX = center.x + (cos(rad) * saturation * radius).toFloat()
                val thumbY = center.y + (sin(rad) * saturation * radius).toFloat()
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(thumbX, thumbY), style = Stroke(width = 2.5.dp.toPx()))
                drawCircle(color = selectedColor, radius = 6.5.dp.toPx(), center = Offset(thumbX, thumbY))
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.s8_markup_brightness), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Slider(
                    value = value,
                    onValueChange = { next ->
                        value = next
                        onColorChange(Color.hsv(hue, saturation, value))
                    },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f).height(28.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkupToolChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else Color.Transparent,
        modifier = Modifier.size(38.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
    }
}
