package com.jumlo.app.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-JVM + Android image enhancement pipeline prior to OCR recognition:
 * estimates skew angle via horizontal ink projection variance, straightens tilted scans,
 * normalizes paper illumination, and computes 0/90/180/270 orientation corrections from text vectors.
 */
object OcrImageEnhancer {
    /** Returns the rotation needed (0, 90, 180, or 270 clockwise degrees) from an average baseline vector. */
    fun quarterTurnFromBaselineVector(dx: Float, dy: Float): Int {
        if (abs(dx) < 1e-3f && abs(dy) < 1e-3f) return 0
        return if (abs(dx) >= abs(dy)) {
            if (dx >= 0f) 0 else 180
        } else {
            if (dy > 0f) 270 else 90
        }
    }

    /**
     * Estimates page tilt in degrees within `[-8..+8]` by maximizing horizontal projection row
     * variance on foreground ink pixels. Returns `0f` if the page is already level or flat.
     */
    fun estimateSkewDegrees(luma: IntArray, w: Int, h: Int): Float {
        if (w < 40 || h < 40 || luma.size < w * h) return 0f
        var lumaSum = 0L
        for (i in 0 until w * h) lumaSum += luma[i]
        val meanLuma = (lumaSum / (w * h)).toInt()
        val inkThreshold = (meanLuma - 18).coerceIn(40, 210)

        var totalInk = 0
        val rowCounts = DoubleArray(h)
        fun scoreAngle(deg: Float): Double {
            val rad = Math.toRadians(deg.toDouble())
            val s = sin(rad)
            val c = cos(rad)
            val cx = w * 0.5
            val cy = h * 0.5
            rowCounts.fill(0.0)
            var count = 0
            // Subsample stride 2 in interior for sub-millisecond evaluation.
            val yMin = (h * 0.06).toInt()
            val yMax = (h * 0.94).toInt()
            val xMin = (w * 0.06).toInt()
            val xMax = (w * 0.94).toInt()
            for (y in yMin until yMax step 2) {
                val dy = y - cy
                val rowOff = y * w
                for (x in xMin until xMax step 2) {
                    if (luma[rowOff + x] < inkThreshold) {
                        val dx = x - cx
                        val ry = (dy * c - dx * s + cy).roundToInt()
                        if (ry in 0 until h) {
                            rowCounts[ry] += 1.0
                            count++
                        }
                    }
                }
            }
            if (deg == 0f) totalInk = count
            if (count < 24) return 0.0
            var mean = 0.0
            for (v in rowCounts) mean += v
            mean /= h
            var variance = 0.0
            for (v in rowCounts) {
                val d = v - mean
                variance += d * d
            }
            return variance / h
        }

        val zeroScore = scoreAngle(0f)
        if (totalInk < 24 || zeroScore <= 0.0) return 0f

        var bestAngle = 0f
        var bestScore = zeroScore
        var a = -8f
        while (a <= 8f) {
            if (abs(a) >= 0.5f) {
                val sc = scoreAngle(a)
                if (sc > bestScore) {
                    bestScore = sc
                    bestAngle = a
                }
            }
            a += 1f
        }
        // Only apply deskew when tilt improves line separation meaningfully (> 6% projection variance gain).
        return if (abs(bestAngle) >= 1.5f && bestScore > zeroScore * 1.06) -bestAngle else 0f
    }

    /** Upscales/bounds [source], applies projection deskew when tilted, and normalizes contrast onto ARGB_8888. */
    fun prepareDocumentForOcr(
        source: Bitmap,
        targetWidthPx: Int = 1800,
        maxUpscale: Float = 2.5f,
        maxPixels: Long = 6_000_000L,
        contrastGain: Float = 1.38f
    ): Bitmap {
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        var targetW = srcW
        var targetH = srcH
        if (srcW < targetWidthPx) {
            val scale = (targetWidthPx.toFloat() / srcW).coerceAtMost(maxUpscale)
            targetW = (srcW * scale).toInt().coerceAtLeast(1)
            targetH = (srcH * scale).toInt().coerceAtLeast(1)
        }
        val pixels = targetW.toLong() * targetH.toLong()
        if (pixels > maxPixels) {
            val shrink = sqrt(maxPixels.toDouble() / pixels.toDouble())
            targetW = (targetW * shrink).toInt().coerceAtLeast(1)
            targetH = (targetH * shrink).toInt().coerceAtLeast(1)
        }

        val deskewAngle = runCatching {
            val probeSide = 160
            val pScale = min(1f, probeSide.toFloat() / max(srcW, srcH))
            val pw = (srcW * pScale).roundToInt().coerceAtLeast(1)
            val ph = (srcH * pScale).roundToInt().coerceAtLeast(1)
            if (pw < 40 || ph < 40) 0f
            else {
                val tiny = Bitmap.createScaledBitmap(source, pw, ph, true)
                try {
                    val buf = IntArray(pw * ph)
                    tiny.getPixels(buf, 0, pw, 0, 0, pw, ph)
                    val luma = IntArray(pw * ph) { i ->
                        val c = buf[i]
                        (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
                    }
                    estimateSkewDegrees(luma, pw, ph)
                } finally {
                    if (tiny !== source && !tiny.isRecycled) tiny.recycle()
                }
            }
        }.getOrDefault(0f)

        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            val translate = (-0.5f * contrastGain + 0.5f) * 255f
            val gray = ColorMatrix().apply { setSaturation(0f) }
            val boost = ColorMatrix(floatArrayOf(
                contrastGain, 0f, 0f, 0f, translate,
                0f, contrastGain, 0f, 0f, translate,
                0f, 0f, contrastGain, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            gray.postConcat(boost)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(gray)
            }
            val matrix = Matrix().apply {
                postScale(targetW.toFloat() / srcW, targetH.toFloat() / srcH)
                if (abs(deskewAngle) >= 1f) {
                    postRotate(deskewAngle, targetW * 0.5f, targetH * 0.5f)
                }
            }
            canvas.drawBitmap(source, matrix, paint)
            return out
        } catch (t: Throwable) {
            out.recycle()
            throw t
        }
    }
}
