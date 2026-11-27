package com.jumlo.app.ui.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jumlo.app.R
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.RotateRight
import com.jumlo.app.ui.icons.rounded.AutoAwesome
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.icons.rounded.ZoomOutMap
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class QuadPoint(val x: Float, val y: Float)

internal data class NormalizedQuad(
    val tl: QuadPoint,
    val tr: QuadPoint,
    val br: QuadPoint,
    val bl: QuadPoint
) {
    fun corner(index: Int): QuadPoint = when (index) {
        0 -> tl
        1 -> tr
        2 -> br
        else -> bl
    }

    fun withCorner(index: Int, pt: QuadPoint): NormalizedQuad = when (index) {
        0 -> copy(tl = pt)
        1 -> copy(tr = pt)
        2 -> copy(br = pt)
        else -> copy(bl = pt)
    }

    fun isFullFrame(): Boolean =
        tl.x <= 0.002f && tl.y <= 0.002f &&
            tr.x >= 0.998f && tr.y <= 0.002f &&
            br.x >= 0.998f && br.y >= 0.998f &&
            bl.x <= 0.002f && bl.y >= 0.998f

    companion object {
        val FULL = NormalizedQuad(
            tl = QuadPoint(0f, 0f),
            tr = QuadPoint(1f, 0f),
            br = QuadPoint(1f, 1f),
            bl = QuadPoint(0f, 1f)
        )
    }
}

internal object DocumentQuadGeometry {
    fun area(q: NormalizedQuad): Float {
        val pts = arrayOf(q.tl, q.tr, q.br, q.bl)
        var sum = 0f
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) * 0.5f
    }

    fun isValid(q: NormalizedQuad): Boolean {
        val pts = arrayOf(q.tl, q.tr, q.br, q.bl)
        if (pts.any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
        if (area(q) < 0.04f) return false
        var sign = 0f
        for (i in 0 until 4) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % 4]
            val p2 = pts[(i + 2) % 4]
            val dx1 = p1.x - p0.x
            val dy1 = p1.y - p0.y
            val dx2 = p2.x - p1.x
            val dy2 = p2.y - p1.y
            if (hypot(dx1, dy1) < 0.08f) return false
            val cross = dx1 * dy2 - dy1 * dx2
            if (abs(cross) < 1e-4f) return false
            if (sign == 0f) sign = cross
            else if (cross * sign <= 0f) return false
        }
        return sign > 0f // Clockwise in screen Y-down coords.
    }

    fun targetDimensions(q: NormalizedQuad, srcW: Int, srcH: Int, maxEdge: Int = 3200): Pair<Int, Int> {
        val wTop = hypot((q.tr.x - q.tl.x) * srcW, (q.tr.y - q.tl.y) * srcH)
        val wBot = hypot((q.br.x - q.bl.x) * srcW, (q.br.y - q.bl.y) * srcH)
        val hLeft = hypot((q.bl.x - q.tl.x) * srcW, (q.bl.y - q.tl.y) * srcH)
        val hRight = hypot((q.br.x - q.tr.x) * srcW, (q.br.y - q.tr.y) * srcH)
        var outW = max(wTop, wBot).roundToInt().coerceAtLeast(64)
        var outH = max(hLeft, hRight).roundToInt().coerceAtLeast(64)
        val longest = max(outW, outH)
        if (longest > maxEdge) {
            val s = maxEdge.toFloat() / longest.toFloat()
            outW = (outW * s).roundToInt().coerceAtLeast(1)
            outH = (outH * s).roundToInt().coerceAtLeast(1)
        }
        return outW to outH
    }

    /**
     * Conservative outer-boundary document detector on a luminance grid.
     * Combines 2D Sobel gradient scoring across 6 perimeter bands per side with outlier-pruned
     * linear least-squares fitting and 4-line corner intersections so angled pages snap accurately
     * without ever shrinking onto interior tables/text. Falls back to FULL on weak edges (< 42% area).
     */
    fun detectQuadFromLuma(lum: IntArray, w: Int, h: Int): NormalizedQuad {
        if (w < 40 || h < 40 || lum.size < w * h) return NormalizedQuad.FULL

        fun rowGradient(y: Int, x0: Int, x1: Int): Float {
            if (y <= 1 || y >= h - 2 || x1 <= x0) return 0f
            var sum = 0L
            for (x in x0 until x1) {
                val dy = abs(lum[(y + 1) * w + x] - lum[(y - 1) * w + x])
                val dx = abs(lum[y * w + (x + 1)] - lum[y * w + (x - 1)]) / 3
                sum += dy + dx
            }
            return sum.toFloat() / (x1 - x0)
        }

        fun colGradient(x: Int, y0: Int, y1: Int): Float {
            if (x <= 1 || x >= w - 2 || y1 <= y0) return 0f
            var sum = 0L
            for (y in y0 until y1) {
                val dx = abs(lum[y * w + (x + 1)] - lum[y * w + (x - 1)])
                val dy = abs(lum[(y + 1) * w + x] - lum[(y - 1) * w + x]) / 3
                sum += dx + dy
            }
            return sum.toFloat() / (y1 - y0)
        }

        fun outerEdgePeak(range: IntProgression, scorer: (Int) -> Float): Int? {
            var sum = 0f
            var count = 0
            val scores = ArrayList<Pair<Int, Float>>()
            for (pos in range) {
                val s = scorer(pos)
                scores += pos to s
                sum += s
                count++
            }
            val avg = if (count > 0) sum / count else 0f
            val maxScore = scores.maxOfOrNull { it.second } ?: 0f
            val threshold = max(14f, avg * 2.0f)
            if (maxScore < threshold) return null
            val outerThreshold = max(threshold, maxScore * 0.72f)
            return scores.firstOrNull { it.second >= outerThreshold }?.first
        }

        // Fit dependent = slope * independent + intercept with median outlier rejection.
        fun fitLine(samples: List<Pair<Float, Float>>, maxResidual: Float, fallbackConst: Float): Pair<Float, Float> {
            if (samples.size < 3) return 0f to fallbackConst
            val sortedDep = samples.map { it.second }.sorted()
            val medianDep = sortedDep[sortedDep.size / 2]
            fun ols(pts: List<Pair<Float, Float>>): Pair<Float, Float> {
                val n = pts.size.toDouble()
                var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
                for ((x, y) in pts) {
                    sx += x; sy += y; sxx += x * x; sxy += x * y
                }
                val denom = n * sxx - sx * sx
                if (abs(denom) < 1e-4) return 0f to (sy / n).toFloat()
                val slope = ((n * sxy - sx * sy) / denom).toFloat().coerceIn(-0.45f, 0.45f)
                val intercept = ((sy - slope * sx) / n).toFloat()
                return slope to intercept
            }
            val initial = ols(samples)
            val inliers = samples.filter { (x, y) ->
                abs(y - (initial.first * x + initial.second)) <= maxResidual
            }
            return when {
                inliers.size >= 3 -> ols(inliers)
                else -> 0f to medianDep
            }
        }

        val maxInY = (h * 0.28f).toInt().coerceAtLeast(4)
        val maxInX = (w * 0.28f).toInt().coerceAtLeast(4)
        val sliceCount = 6

        val topSamples = mutableListOf<Pair<Float, Float>>()
        val botSamples = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until sliceCount) {
            val f0 = 0.16f + (0.68f * i / sliceCount)
            val f1 = 0.16f + (0.68f * (i + 1) / sliceCount)
            val x0 = (w * f0).toInt().coerceIn(2, w - 3)
            val x1 = (w * f1).toInt().coerceIn(x0 + 1, w - 2)
            val midX = (x0 + x1) * 0.5f
            outerEdgePeak(2..maxInY) { rowGradient(it, x0, x1) }?.let { topSamples += midX to it.toFloat() }
            outerEdgePeak((h - 3) downTo (h - 1 - maxInY)) { rowGradient(it, x0, x1) }?.let { botSamples += midX to it.toFloat() }
        }

        val leftSamples = mutableListOf<Pair<Float, Float>>()
        val rightSamples = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until sliceCount) {
            val f0 = 0.16f + (0.68f * i / sliceCount)
            val f1 = 0.16f + (0.68f * (i + 1) / sliceCount)
            val y0 = (h * f0).toInt().coerceIn(2, h - 3)
            val y1 = (h * f1).toInt().coerceIn(y0 + 1, h - 2)
            val midY = (y0 + y1) * 0.5f
            outerEdgePeak(2..maxInX) { colGradient(it, y0, y1) }?.let { leftSamples += midY to it.toFloat() }
            outerEdgePeak((w - 3) downTo (w - 1 - maxInX)) { colGradient(it, y0, y1) }?.let { rightSamples += midY to it.toFloat() }
        }

        if (topSamples.size < 3 && botSamples.size < 3 && leftSamples.size < 3 && rightSamples.size < 3) {
            return NormalizedQuad.FULL
        }

        val topLine = fitLine(topSamples, max(3f, h * 0.035f), 0f)
        val botLine = fitLine(botSamples, max(3f, h * 0.035f), (h - 1).toFloat())
        val leftLine = fitLine(leftSamples, max(3f, w * 0.035f), 0f)
        val rightLine = fitLine(rightSamples, max(3f, w * 0.035f), (w - 1).toFloat())

        fun intersect(horiz: Pair<Float, Float>, vert: Pair<Float, Float>): QuadPoint {
            val a = horiz.first; val b = horiz.second
            val c = vert.first; val d = vert.second
            val denom = 1f - a * c
            val x = if (abs(denom) > 1e-4f) (c * b + d) / denom else d
            val y = a * x + b
            return QuadPoint((x / (w - 1)).coerceIn(0f, 1f), (y / (h - 1)).coerceIn(0f, 1f))
        }

        val tl = intersect(topLine, leftLine)
        val tr = intersect(topLine, rightLine)
        val br = intersect(botLine, rightLine)
        val bl = intersect(botLine, leftLine)
        val quad = NormalizedQuad(
            tl = QuadPoint(tl.x.coerceIn(0f, 0.35f), tl.y.coerceIn(0f, 0.35f)),
            tr = QuadPoint(tr.x.coerceIn(0.65f, 1f), tr.y.coerceIn(0f, 0.35f)),
            br = QuadPoint(br.x.coerceIn(0.65f, 1f), br.y.coerceIn(0.65f, 1f)),
            bl = QuadPoint(bl.x.coerceIn(0f, 0.35f), bl.y.coerceIn(0.65f, 1f))
        )
        return if (isValid(quad) && area(quad) >= 0.42f) quad else NormalizedQuad.FULL
    }
}

internal object DocumentQuadProcessor {
    fun detectConservativeQuad(src: Bitmap): NormalizedQuad {
        if (src.isRecycled || src.width < 40 || src.height < 40) return NormalizedQuad.FULL
        val maxSide = max(src.width, src.height)
        val scale = if (maxSide > 320) 320f / maxSide else 1f
        val work = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
        } else src
        return try {
            val w = work.width
            val h = work.height
            val pixels = IntArray(w * h)
            work.getPixels(pixels, 0, w, 0, 0, w, h)
            val lum = IntArray(w * h) { i ->
                val c = pixels[i]
                (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            }
            DocumentQuadGeometry.detectQuadFromLuma(lum, w, h)
        } finally {
            if (work !== src && !work.isRecycled) work.recycle()
        }
    }

    private fun cropBoundingRect(src: Bitmap, quad: NormalizedQuad, maxEdge: Int): Bitmap {
        val left = (min(quad.tl.x, quad.bl.x) * src.width).roundToInt().coerceIn(0, src.width - 2)
        val top = (min(quad.tl.y, quad.tr.y) * src.height).roundToInt().coerceIn(0, src.height - 2)
        val right = (max(quad.tr.x, quad.br.x) * src.width).roundToInt().coerceIn(left + 1, src.width)
        val bottom = (max(quad.bl.y, quad.br.y) * src.height).roundToInt().coerceIn(top + 1, src.height)
        val cropped = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        val longest = max(cropped.width, cropped.height)
        if (longest <= maxEdge) return cropped
        val scale = maxEdge.toFloat() / longest.toFloat()
        val sw = (cropped.width * scale).roundToInt().coerceAtLeast(1)
        val sh = (cropped.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, sw, sh, true)
        if (scaled !== cropped && !cropped.isRecycled) cropped.recycle()
        return scaled
    }

    fun warp(src: Bitmap, quad: NormalizedQuad, maxEdge: Int = 3200): Bitmap {
        require(!src.isRecycled) { "BITMAP_RECYCLED" }
        if (quad.isFullFrame() || !DocumentQuadGeometry.isValid(quad)) {
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        val isAxisAligned = abs(quad.tl.x - quad.bl.x) < 0.004f &&
            abs(quad.tr.x - quad.br.x) < 0.004f &&
            abs(quad.tl.y - quad.tr.y) < 0.004f &&
            abs(quad.bl.y - quad.br.y) < 0.004f
        if (isAxisAligned) {
            return cropBoundingRect(src, quad, maxEdge)
        }
        val (outW, outH) = DocumentQuadGeometry.targetDimensions(quad, src.width, src.height, maxEdge)
        val srcPts = floatArrayOf(
            quad.tl.x * src.width, quad.tl.y * src.height,
            quad.tr.x * src.width, quad.tr.y * src.height,
            quad.br.x * src.width, quad.br.y * src.height,
            quad.bl.x * src.width, quad.bl.y * src.height
        )
        val dstPts = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix()
        if (m.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(out)
                canvas.drawBitmap(src, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                return out
            } catch (t: Throwable) {
                out.recycle()
                throw t
            }
        }
        // Fallback when Matrix.setPolyToPoly is unsupported on host shadows.
        return cropBoundingRect(src, quad, maxEdge)
    }
}

@Composable
internal fun InteractiveDocumentCropDialog(
    initialBitmap: Bitmap,
    autoDetectInitial: Boolean = true,
    onApply: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var workingBitmap by remember(initialBitmap) { mutableStateOf(initialBitmap) }
    var quad by remember(initialBitmap) {
        mutableStateOf(
            if (autoDetectInitial) DocumentQuadProcessor.detectConservativeQuad(initialBitmap)
            else NormalizedQuad.FULL
        )
    }
    var activeCorner by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }

    DisposeOwnedPreviewBitmap(workingBitmap.takeUnless { it === initialBitmap })

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0D12)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(enabled = !busy, onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = Color.White)
                    }
                    Text(
                        text = stringResource(R.string.scan_crop_title),
                        style = QuietTheme.typography.sectionTitle,
                        color = Color.White,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            val src = workingBitmap
                            if (!src.isRecycled) {
                                val m = Matrix().apply { postRotate(90f) }
                                workingBitmap = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                                quad = NormalizedQuad.FULL
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.visual_rotate), tint = Color.White)
                    }
                    IconButton(
                        enabled = !busy,
                        onClick = { quad = DocumentQuadProcessor.detectConservativeQuad(workingBitmap) }
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.scan_crop_auto), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        enabled = !busy,
                        onClick = { quad = NormalizedQuad.FULL }
                    ) {
                        Icon(Icons.Rounded.ZoomOutMap, stringResource(R.string.scan_crop_full), tint = Color.White)
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = workingBitmap
                    val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                    val areaW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val areaH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val bmpRatio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                    val areaRatio = areaW / areaH
                    val imgW = if (bmpRatio > areaRatio) areaW else areaH * bmpRatio
                    val imgH = if (bmpRatio > areaRatio) areaW / bmpRatio else areaH
                    val imgLeft = (areaW - imgW) * 0.5f
                    val imgTop = (areaH - imgH) * 0.5f
                    val primaryColor = MaterialTheme.colorScheme.primary

                    Image(
                        bitmap = imageBitmap,
                        contentDescription = stringResource(R.string.scan_crop_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    ComposeCanvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bmp, busy) {
                                if (busy) return@pointerInput
                                val touchSlopPx = 56.dp.toPx()
                                detectDragGestures(
                                    onDragStart = { start ->
                                        var bestIdx = -1
                                        var bestDist = touchSlopPx
                                        for (i in 0 until 4) {
                                            val c = quad.corner(i)
                                            val cx = imgLeft + c.x * imgW
                                            val cy = imgTop + c.y * imgH
                                            val d = hypot(start.x - cx, start.y - cy)
                                            if (d < bestDist) {
                                                bestDist = d
                                                bestIdx = i
                                            }
                                        }
                                        activeCorner = bestIdx
                                    },
                                    onDragEnd = { activeCorner = -1 },
                                    onDragCancel = { activeCorner = -1 },
                                    onDrag = { change, _ ->
                                        val idx = activeCorner
                                        if (idx in 0..3) {
                                            change.consume()
                                            val nx = ((change.position.x - imgLeft) / imgW).coerceIn(0f, 1f)
                                            val ny = ((change.position.y - imgTop) / imgH).coerceIn(0f, 1f)
                                            val candidate = quad.withCorner(idx, QuadPoint(nx, ny))
                                            if (DocumentQuadGeometry.isValid(candidate)) {
                                                quad = candidate
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        val pts = List(4) { i ->
                            val c = quad.corner(i)
                            Offset(imgLeft + c.x * imgW, imgTop + c.y * imgH)
                        }
                        val maskPath = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            moveTo(pts[0].x, pts[0].y)
                            lineTo(pts[1].x, pts[1].y)
                            lineTo(pts[2].x, pts[2].y)
                            lineTo(pts[3].x, pts[3].y)
                            close()
                        }
                        drawPath(maskPath, Color.Black.copy(alpha = 0.56f))

                        val quadPath = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            lineTo(pts[1].x, pts[1].y)
                            lineTo(pts[2].x, pts[2].y)
                            lineTo(pts[3].x, pts[3].y)
                            close()
                        }
                        drawPath(
                            quadPath,
                            color = primaryColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        pts.forEachIndexed { i, p ->
                            val active = i == activeCorner
                            val outerR = if (active) 16.dp.toPx() else 12.dp.toPx()
                            drawCircle(Color.White, radius = outerR, center = p)
                            drawCircle(primaryColor, radius = outerR - 3.dp.toPx(), center = p)
                            if (active) {
                                drawCircle(
                                    primaryColor.copy(alpha = 0.25f),
                                    radius = 28.dp.toPx(),
                                    center = p
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12161E))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.scan_crop_hint),
                        style = QuietTheme.typography.metadata,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    QuietPrimaryButton(
                        text = stringResource(R.string.scan_crop_apply),
                        enabled = !busy,
                        onClick = {
                            busy = true
                            val targetBmp = workingBitmap
                            val targetQuad = quad
                            scope.launch {
                                val cropped = withContext(Dispatchers.IO) {
                                    runCatching { DocumentQuadProcessor.warp(targetBmp, targetQuad) }.getOrNull()
                                }
                                busy = false
                                if (cropped != null) onApply(cropped) else onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
