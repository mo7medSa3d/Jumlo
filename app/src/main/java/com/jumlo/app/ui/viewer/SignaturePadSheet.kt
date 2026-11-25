package com.jumlo.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.pdf.SavedSignature
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignaturePadSheet(
    saved: List<SavedSignature>,
    onPickSaved: (SavedSignature) -> Unit,
    onSaveNew: (ByteArray) -> Unit,
    onDeleteSaved: (SavedSignature) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val inkColors = remember {
        listOf(
            Color(0xFF0E142B) to R.string.studio_ink_black,
            Color(0xFF2E67FF) to R.string.studio_ink_blue,
            Color(0xFFD32F2F) to R.string.studio_ink_red
        )
    }
    var activeColor by remember { mutableStateOf(inkColors.first().first) }
    val strokes = remember { mutableStateListOf<Pair<Int, List<Offset>>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val latestColor by rememberUpdatedState(activeColor)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = pillars.cardSurface,
        shape = QuietTheme.shapes.sheet
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.markup_signature_title), style = QuietTheme.typography.sectionTitle, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    inkColors.forEach { (ink, labelRes) ->
                        val inkLabel = stringResource(labelRes)
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(ink)
                                .border(if (activeColor == ink) 2.5.dp else 1.dp, if (activeColor == ink) colors.primary else pillars.cardBorder, CircleShape)
                                .semantics { contentDescription = inkLabel; selected = activeColor == ink }
                                .clickable { activeColor = ink }
                        )
                    }
                    TextButton(onClick = { strokes.clear(); currentStroke = emptyList() }) {
                        Text(stringResource(R.string.markup_clear))
                    }
                }
            }
            if (saved.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(saved, key = { it.id }, contentType = { "saved-signature" }) { sig ->
                        val preview by produceState<Bitmap?>(null, sig.file.absolutePath) {
                            value = withContext(Dispatchers.IO) {
                                try { BitmapFactory.decodeFile(sig.file.absolutePath) }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { null }
                                catch (_: OutOfMemoryError) { null }
                            }
                        }
                        DisposeOwnedPreviewBitmap(preview)
                        Surface(
                            onClick = { onPickSaved(sig) },
                            shape = QuietTheme.shapes.card,
                            color = Color.White,
                            border = BorderStroke(1.dp, pillars.cardBorder),
                            modifier = Modifier.width(124.dp).height(58.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                val readyPreview = preview
                                if (readyPreview != null && !readyPreview.isRecycled) {
                                    val imageBitmap = remember(readyPreview) { readyPreview.asImageBitmap() }
                                    Image(imageBitmap, stringResource(R.string.studio_use_signature), Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                                }
                                IconButton(onClick = { onDeleteSaved(sig) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.action_delete), Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                shape = QuietTheme.shapes.card,
                color = Color.White,
                border = BorderStroke(1.dp, pillars.cardBorder),
                modifier = Modifier.fillMaxWidth().height(190.dp)
            ) {
                Canvas(
                    Modifier.fillMaxSize().onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val pts = mutableListOf(down.position)
                                currentStroke = pts.toList()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    change.consume()
                                    val pos = change.position
                                    if ((pos - pts.last()).getDistanceSquared() >= 1.5f) {
                                        pts.add(pos)
                                        currentStroke = pts.toList()
                                    }
                                }
                                if (pts.isNotEmpty()) {
                                    strokes += latestColor.toArgb() to pts.toList()
                                }
                                currentStroke = emptyList()
                            }
                        }
                ) {
                    fun drawPts(pts: List<Offset>, argb: Int) {
                        if (pts.isEmpty()) return
                        val strokeColor = Color(argb)
                        if (pts.size == 1) {
                            drawCircle(strokeColor, radius = 3.2f, center = pts.first())
                            return
                        }
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) {
                                val prev = pts[i - 1]
                                val cur = pts[i]
                                quadraticBezierTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                            }
                            lineTo(pts.last().x, pts.last().y)
                        }
                        drawPath(path, strokeColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    strokes.forEach { (c, pts) -> drawPts(pts, c) }
                    drawPts(currentStroke, activeColor.toArgb())
                }
            }
            QuietPrimaryButton(
                text = stringResource(R.string.markup_use_signature),
                enabled = strokes.isNotEmpty() && padSize.width > 0 && padSize.height > 0,
                onClick = {
                    val bytes = renderTrimmedHighResSignature(strokes)
                    if (bytes.isNotEmpty()) onSaveNew(bytes)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Renders trimmed, 3x supersampled smooth quadratic Bezier signature PNG bytes with transparent background. */
private fun renderTrimmedHighResSignature(strokes: List<Pair<Int, List<Offset>>>): ByteArray {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for ((_, pts) in strokes) {
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
    }
    if (!minX.isFinite() || !minY.isFinite()) return ByteArray(0)

    val margin = 12f
    val left = floor(minX - margin)
    val top = floor(minY - margin)
    val srcW = (ceil(maxX + margin) - left).coerceAtLeast(32f)
    val srcH = (ceil(maxY + margin) - top).coerceAtLeast(24f)

    // Supersample 3x for crisp 300 DPI PDF flattening, capped to keep memory bounded.
    val scale = minOf(3f, 1600f / srcW, 800f / srcH).coerceAtLeast(1f)
    val outW = ceil(srcW * scale).toInt().coerceIn(64, 1600)
    val outH = ceil(srcH * scale).toInt().coerceIn(32, 800)

    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    return try {
        val canvas = AndroidCanvas(bmp)
        canvas.scale(scale, scale)
        canvas.translate(-left, -top)
        val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.DITHER_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
        val dotPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.DITHER_FLAG).apply {
            style = AndroidPaint.Style.FILL
        }
        for ((argb, pts) in strokes) {
            if (pts.isEmpty()) continue
            if (pts.size == 1) {
                dotPaint.color = argb
                canvas.drawCircle(pts[0].x, pts[0].y, strokePaint.strokeWidth * 0.5f, dotPaint)
                continue
            }
            strokePaint.color = argb
            val path = AndroidPath().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val cur = pts[i]
                    quadTo(prev.x, prev.y, (prev.x + cur.x) * 0.5f, (prev.y + cur.y) * 0.5f)
                }
                lineTo(pts.last().x, pts.last().y)
            }
            canvas.drawPath(path, strokePaint)
        }
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    } finally {
        bmp.recycle()
    }
}
