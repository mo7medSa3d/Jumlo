package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.PreviewFrame
import kotlin.math.*

private data class WatermarkRaster(val image: Bitmap, val textSize: Float)

/** Exact text rasterization and PDF-point sizing, with selection, handles and independent page zoom. */
@Composable
internal fun PdfWatermarkPreview(
    frame: PreviewFrame, draft: WorkspaceDraft, page: Int, applies: Boolean,
    centerX: Float, centerY: Float, onPosition: (Float, Float) -> Unit, onCommit: () -> Unit,
    onScaleRotation: (Int, Int) -> Unit = { _, _ -> }
) {
    val text = draft.text1.trim().replace(Regex("\\s+"), " ").take(120)
    val color = draft.text3.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) } ?: "#64748B"
    val opacity = draftNumber(draft.num4, 28).coerceIn(5, 100)
    val fontSize = draftNumber(draft.num1, 42).coerceIn(12, 90)
    val rotation = draftNumber(draft.text4, -35).coerceIn(-180, 180)
    val tiled = draft.text2 == "TILE"
    var selected by rememberSaveable(page) { mutableStateOf(true) }
    var pageZoom by rememberSaveable(page) { mutableFloatStateOf(1f) }
    var pagePan by remember(page) { mutableStateOf(Offset.Zero) }
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val raster = remember(text, color, opacity, draft.watermarkFont) { if (text.isBlank()) null else {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true; textSize = 84f; typeface = Typeface.create(when(draft.watermarkFont) {"serif"->"serif";"mono"->"monospace";else->"sans-serif"}, Typeface.BOLD)
            this.color = android.graphics.Color.parseColor(color); alpha = opacity * 255 / 100
        }
        val rawWidth = paint.measureText(text)
        if (rawWidth > 1650f) paint.textSize *= 1650f / rawWidth
        val metrics = paint.fontMetrics
        val image = Bitmap.createBitmap((paint.measureText(text) + 48f).toInt().coerceIn(64, 1710),
            (metrics.descent - metrics.ascent + 36f).toInt().coerceIn(80, 220), Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(image).drawText(text, 24f, 18f - metrics.ascent, paint)
        WatermarkRaster(image, paint.textSize)
    } }
    DisposeOwnedPreviewBitmap(raster?.image)
    val latestX by rememberUpdatedState(centerX)
    val latestY by rememberUpdatedState(centerY)
    val latestFont by rememberUpdatedState(fontSize)
    val latestRotation by rememberUpdatedState(rotation)
    val latestRaster by rememberUpdatedState(raster)
    val latestPosition by rememberUpdatedState(onPosition)
    val latestScaleRotation by rememberUpdatedState(onScaleRotation)
    val latestCommit by rememberUpdatedState(onCommit)
    val objectDescription = stringResource(R.string.studio_watermark_object)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("preview.bitmap").clipToBounds(), contentAlignment = Alignment.Center) {
            val aspect = frame.width.toFloat() / frame.height
            val width = minOf(maxWidth, 560.dp, 560.dp * aspect)
            val height = width / aspect
            Canvas(Modifier.size(width, height).testTag("watermark.canvas")
                .semantics { contentDescription = objectDescription }
                .graphicsLayer { scaleX = pageZoom; scaleY = pageZoom; translationX = pagePan.x; translationY = pagePan.y }
                .pointerInput(frame, page, tiled, applies) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var x = latestX; var y = latestY
                        var pointSize = latestFont.toFloat(); var angle = latestRotation.toFloat()
                        val overlay = latestRaster
                        fun dimensions(): Pair<Float, Float> {
                            if (overlay == null) return 0f to 0f
                            val rawW = overlay.image.width * (pointSize / overlay.textSize)
                            val rawH = overlay.image.height * (pointSize / overlay.textSize)
                            val fit = minOf(1f, frame.width * (if (tiled) .36f else .8f) / rawW)
                            return rawW * fit / frame.width * size.width to rawH * fit / frame.height * size.height
                        }
                        fun local(point: Offset): Offset {
                            val delta = point - Offset(x * size.width, y * size.height)
                            val radians = Math.toRadians(-angle.toDouble())
                            return Offset((delta.x * cos(radians) - delta.y * sin(radians)).toFloat(),
                                (delta.x * sin(radians) + delta.y * cos(radians)).toFloat())
                        }
                        val (w, h) = dimensions()
                        val start = local(down.position)
                        val handleRadius = 24.dp.toPx()
                        val rotateHandle = Offset(0f, -h / 2f - 24.dp.toPx())
                        val rotateHit = selected && (start - rotateHandle).getDistance() <= handleRadius
                        val cornerHit = selected && listOf(Offset(-w/2,-h/2), Offset(w/2,-h/2), Offset(-w/2,h/2), Offset(w/2,h/2))
                            .any { (start - it).getDistance() <= handleRadius }
                        val hit = applies && !tiled && overlay != null &&
                            (abs(start.x) <= w/2 + 12.dp.toPx() && abs(start.y) <= h/2 + 12.dp.toPx() || rotateHit || cornerHit)
                        if (hit) selected = true else if (selected) selected = false
                        val editing = selected && hit && !tiled && applies
                        var changed = false
                        var previous = down.position
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 0) break
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val turn = event.calculateRotation()
                            if (editing) {
                                if (pressed > 1) {
                                    x = (x + pan.x / size.width).coerceIn(0f, 1f)
                                    y = (y + pan.y / size.height).coerceIn(0f, 1f)
                                    pointSize = (pointSize * zoom).coerceIn(12f, 90f)
                                    angle = normalizeWatermarkRotation(angle + turn)
                                } else {
                                    val current = event.changes.first().position
                                    val center = Offset(x * size.width, y * size.height)
                                    when {
                                        rotateHit -> {
                                            val a = previous - center; val b = current - center
                                            angle = normalizeWatermarkRotation(angle + Math.toDegrees((atan2(b.y, b.x) - atan2(a.y, a.x)).toDouble()).toFloat())
                                        }
                                        cornerHit -> {
                                            val before = (previous - center).getDistance().coerceAtLeast(1f)
                                            pointSize = (pointSize * ((current - center).getDistance() / before)).coerceIn(12f, 90f)
                                        }
                                        else -> {
                                            x = (x + pan.x / size.width).coerceIn(0f, 1f)
                                            y = (y + pan.y / size.height).coerceIn(0f, 1f)
                                        }
                                    }
                                    previous = current
                                }
                                latestPosition(x, y)
                                latestScaleRotation(pointSize.roundToInt(), angle.roundToInt())
                                event.changes.forEach { it.consume() }
                                changed = true
                            } else if (pressed > 1 || pageZoom > 1.01f) {
                                pageZoom = (pageZoom * zoom).coerceIn(1f, 4f)
                                val maxX = size.width * (pageZoom - 1f) / 2f
                                val maxY = size.height * (pageZoom - 1f) / 2f
                                pagePan = Offset((pagePan.x + pan.x).coerceIn(-maxX, maxX), (pagePan.y + pan.y).coerceIn(-maxY, maxY))
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (changed) latestCommit()
                    }
                }) {
                drawIntoCanvas { compose ->
                    val canvas = compose.nativeCanvas
                    canvas.save()
                    try {
                        canvas.scale(size.width / frame.width, size.height / frame.height)
                        canvas.drawBitmap(frame.bitmap, null, RectF(0f, 0f, frame.width.toFloat(), frame.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                        if (applies) {
                            raster?.let { overlay ->
                                val rawW = overlay.image.width * (fontSize / overlay.textSize)
                                val rawH = overlay.image.height * (fontSize / overlay.textSize)
                                val fit = minOf(1f, frame.width * (if (tiled) .36f else .8f) / rawW)
                                val w = rawW * fit; val h = rawH * fit
                                val spots = if (tiled) listOf(.18f, .5f, .82f).flatMap { y -> listOf(.18f, .5f, .82f).map { x -> x to y } }
                                    else listOf(centerX to centerY)
                                spots.forEach { (x, y) ->
                                    canvas.save(); canvas.translate(x * frame.width, y * frame.height); canvas.rotate(rotation.toFloat())
                                    canvas.drawBitmap(overlay.image, null, RectF(-w/2, -h/2, w/2, h/2), Paint(Paint.FILTER_BITMAP_FLAG))
                                    if (selected && !tiled) {
                                        val unit = frame.width / size.width
                                        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = accent; style = Paint.Style.STROKE; strokeWidth = 2.dp.toPx() * unit }
                                        canvas.drawRect(-w/2, -h/2, w/2, h/2, outline)
                                        val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = accent }
                                        listOf(-w/2 to -h/2, w/2 to -h/2, -w/2 to h/2, w/2 to h/2).forEach { (hx, hy) ->
                                            canvas.drawCircle(hx, hy, 6.dp.toPx() * unit, handle)
                                        }
                                        val top = -h/2 - 24.dp.toPx() * unit
                                        canvas.drawLine(0f, -h/2, 0f, top, outline)
                                        canvas.drawCircle(0f, top, 7.dp.toPx() * unit, handle)
                                    }
                                    canvas.restore()
                                }
                            }
                            if (draft.flag1) canvas.drawText(page.toString(), frame.width * .47f, frame.height * .96f,
                                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; this.color = android.graphics.Color.rgb(102,102,102); typeface = Typeface.create("sans-serif", Typeface.NORMAL) })
                        }
                    } finally { canvas.restore() }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!tiled && applies) TextButton(onClick = { selected = !selected }, modifier = Modifier.testTag("watermark.selection")) {
                Text(stringResource(if (selected) R.string.studio_watermark_deselect else R.string.studio_watermark_select))
            }
            if (pageZoom > 1.01f) TextButton(onClick = { pageZoom = 1f; pagePan = Offset.Zero }) {
                Text(stringResource(R.string.ui_preview_reset_zoom))
            }
        }
    }
}

internal fun normalizeWatermarkRotation(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f
