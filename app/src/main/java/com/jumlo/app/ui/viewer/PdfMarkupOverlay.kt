package com.jumlo.app.ui.viewer

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.jumlo.app.core.PdfMarkupTool
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun PdfMarkupPageLayer(
    pageIndex: Int,
    items: List<PdfMarkupItem>,
    activeTool: PdfMarkupTool,
    activeColor: Color,
    pendingSignaturePath: String?,
    onAddItem: (PdfMarkupItem) -> Unit,
    onRequestTextAt: (Int, Float, Float) -> Unit,
    onMoveItem: (String, Float, Float) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var livePoints by remember { mutableStateOf<List<NormPoint>>(emptyList()) }
    val density = LocalDensity.current

    Box(
        Modifier.fillMaxSize().onSizeChanged { boxSize = it }
            .pointerInput(activeTool, activeColor, pendingSignaturePath, boxSize) {
                if (activeTool == PdfMarkupTool.PEN || activeTool == PdfMarkupTool.HIGHLIGHTER) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (boxSize.width > 0 && boxSize.height > 0) {
                                livePoints = listOf(NormPoint((pos.x / boxSize.width).coerceIn(0f, 1f), (pos.y / boxSize.height).coerceIn(0f, 1f)))
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (boxSize.width > 0 && boxSize.height > 0) {
                                livePoints = livePoints + NormPoint((change.position.x / boxSize.width).coerceIn(0f, 1f), (change.position.y / boxSize.height).coerceIn(0f, 1f))
                            }
                        },
                        onDragEnd = {
                            if (livePoints.size >= 2) {
                                val id = UUID.randomUUID().toString()
                                val item = if (activeTool == PdfMarkupTool.HIGHLIGHTER) {
                                    PdfMarkupItem.HighlightStroke(id, pageIndex, livePoints, activeColor.toArgb())
                                } else {
                                    PdfMarkupItem.InkStroke(id, pageIndex, livePoints, activeColor.toArgb())
                                }
                                onAddItem(item)
                            }
                            livePoints = emptyList()
                        }
                    )
                } else if (activeTool != PdfMarkupTool.NONE) {
                    detectTapGestures { pos ->
                        if (boxSize.width <= 0 || boxSize.height <= 0) return@detectTapGestures
                        val nx = (pos.x / boxSize.width).coerceIn(0.02f, 0.88f)
                        val ny = (pos.y / boxSize.height).coerceIn(0.02f, 0.90f)
                        when (activeTool) {
                            PdfMarkupTool.TEXT -> onRequestTextAt(pageIndex, nx, ny)
                            PdfMarkupTool.DATE -> {
                                val today = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT).format(Date())
                                onAddItem(PdfMarkupItem.TextBox(UUID.randomUUID().toString(), pageIndex, nx, ny, today, activeColor.toArgb(), 13f))
                            }
                            PdfMarkupTool.CHECK -> onAddItem(PdfMarkupItem.CheckMark(UUID.randomUUID().toString(), pageIndex, nx, ny, false, activeColor.toArgb()))
                            PdfMarkupTool.SIGNATURE -> if (pendingSignaturePath != null) {
                                onAddItem(PdfMarkupItem.SavedSignatureStamp(UUID.randomUUID().toString(), pageIndex, pendingSignaturePath, (nx - 0.14f).coerceIn(0.02f, 0.70f), (ny - 0.05f).coerceIn(0.02f, 0.88f)))
                            }
                            else -> Unit
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun drawNormStroke(pts: List<NormPoint>, argb: Int, widthNorm: Float, alpha: Float) {
                if (pts.size < 2) return
                val path = Path().apply {
                    moveTo(pts.first().x * size.width, pts.first().y * size.height)
                    for (i in 1 until pts.size) {
                        lineTo(pts[i].x * size.width, pts[i].y * size.height)
                    }
                }
                drawPath(
                    path = path,
                    color = Color(argb).copy(alpha = alpha),
                    style = Stroke(width = (widthNorm * minOf(size.width, size.height)).coerceAtLeast(2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            items.forEach { item ->
                when (item) {
                    is PdfMarkupItem.InkStroke -> drawNormStroke(item.points, item.colorArgb, item.widthNorm, 1f)
                    is PdfMarkupItem.HighlightStroke -> drawNormStroke(item.points, item.colorArgb, item.widthNorm, item.alpha)
                    else -> Unit
                }
            }
            if (livePoints.size >= 2) {
                val isHl = activeTool == PdfMarkupTool.HIGHLIGHTER
                drawNormStroke(livePoints, activeColor.toArgb(), if (isHl) 0.024f else 0.0045f, if (isHl) 0.35f else 1f)
            }
        }
        if (boxSize.width > 0 && boxSize.height > 0) {
            items.forEach { item ->
                when (item) {
                    is PdfMarkupItem.TextBox -> {
                        Box(
                            Modifier.offset { IntOffset((item.normX * boxSize.width).roundToInt(), (item.normY * boxSize.height).roundToInt()) }
                                .pointerInput(item.id) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        onMoveItem(item.id, drag.x / boxSize.width, drag.y / boxSize.height)
                                    }
                                }
                                .pointerInput(item.id) { detectTapGestures(onLongPress = { onDeleteItem(item.id) }) }
                                .background(Color.White.copy(alpha = 0.72f), QuietTheme.shapes.chip)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(item.text, color = Color(item.colorArgb), fontSize = item.fontSizePt.sp, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
                        }
                    }
                    is PdfMarkupItem.CheckMark -> {
                        Box(
                            Modifier.offset { IntOffset((item.normX * boxSize.width).roundToInt(), (item.normY * boxSize.height).roundToInt()) }
                                .pointerInput(item.id) { detectDragGestures { change, drag -> change.consume(); onMoveItem(item.id, drag.x / boxSize.width, drag.y / boxSize.height) } }
                                .pointerInput(item.id) { detectTapGestures(onLongPress = { onDeleteItem(item.id) }) }
                        ) {
                            Text(if (item.isCross) "✗" else "✓", color = Color(item.colorArgb), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is PdfMarkupItem.SavedSignatureStamp -> {
                        val bmp = remember(item.signaturePath) { runCatching { BitmapFactory.decodeFile(item.signaturePath) }.getOrNull() }
                        val wDp = with(density) { (item.normW * boxSize.width).toDp() }
                        val hDp = with(density) { (item.normH * boxSize.height).toDp() }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .offset { IntOffset((item.normX * boxSize.width).roundToInt(), (item.normY * boxSize.height).roundToInt()) }
                                    .size(wDp, hDp)
                                    .pointerInput(item.id) { detectDragGestures { change, drag -> change.consume(); onMoveItem(item.id, drag.x / boxSize.width, drag.y / boxSize.height) } }
                                    .pointerInput(item.id) { detectTapGestures(onLongPress = { onDeleteItem(item.id) }) }
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** iOS 27 Liquid Glass floating markup toolbar. */
@Composable
internal fun PdfMarkupFloatingBar(
    activeTool: PdfMarkupTool,
    activeColor: Color,
    canUndo: Boolean,
    canRedo: Boolean,
    hasEdits: Boolean,
    saving: Boolean,
    onSelectTool: (PdfMarkupTool) -> Unit,
    onSelectColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val palette = remember { listOf(Color(0xFF2E67FF), Color(0xFFFFD60A), Color(0xFF0E142B), Color(0xFFE53935), Color(0xFF2E7D32)) }

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (activeTool != PdfMarkupTool.NONE && activeTool != PdfMarkupTool.SIGNATURE) {
            Surface(
                shape = QuietTheme.shapes.pill,
                color = colors.surfaceContainerHigh.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, pillars.cardBorder),
                shadowElevation = 6.dp
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    palette.forEach { swatch ->
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(swatch)
                                .border(if (activeColor == swatch) 2.5.dp else 1.dp, if (activeColor == swatch) colors.primary else pillars.cardBorder, CircleShape)
                                .clickable { onSelectColor(swatch) }
                        )
                    }
                }
            }
        }
        Surface(
            shape = QuietTheme.shapes.pill,
            color = colors.surfaceContainerHigh.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, pillars.cardBorder),
            shadowElevation = 10.dp,
            modifier = Modifier.testTag("pdf.markup.bar")
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MarkupToolChip(Icons.Rounded.Edit, stringResource(R.string.markup_tool_pen), activeTool == PdfMarkupTool.PEN) { onSelectTool(PdfMarkupTool.PEN) }
                MarkupToolChip(Icons.Rounded.AutoAwesome, stringResource(R.string.markup_tool_highlight), activeTool == PdfMarkupTool.HIGHLIGHTER) { onSelectTool(PdfMarkupTool.HIGHLIGHTER) }
                MarkupToolChip(Icons.Rounded.FormatSize, stringResource(R.string.markup_tool_text), activeTool == PdfMarkupTool.TEXT) { onSelectTool(PdfMarkupTool.TEXT) }
                MarkupToolChip(Icons.Rounded.Schedule, stringResource(R.string.markup_tool_date), activeTool == PdfMarkupTool.DATE) { onSelectTool(PdfMarkupTool.DATE) }
                MarkupToolChip(Icons.Rounded.Check, stringResource(R.string.markup_tool_check), activeTool == PdfMarkupTool.CHECK) { onSelectTool(PdfMarkupTool.CHECK) }
                MarkupToolChip(Icons.Rounded.Palette, stringResource(R.string.markup_tool_sign), activeTool == PdfMarkupTool.SIGNATURE) { onSelectTool(PdfMarkupTool.SIGNATURE) }
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Undo, stringResource(R.string.markup_undo), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Redo, stringResource(R.string.markup_redo), modifier = Modifier.size(20.dp)) }
                FilledTonalButton(
                    onClick = onSave,
                    enabled = hasEdits && !saving,
                    shape = QuietTheme.shapes.pill,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(stringResource(R.string.action_save), style = QuietTheme.typography.metadataStrong)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close), modifier = Modifier.size(19.dp)) }
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
        modifier = Modifier.size(40.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
