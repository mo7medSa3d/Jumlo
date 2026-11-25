package com.jumlo.app.data.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class ScanFilter { ORIGINAL, CLEAN_DOCUMENT, CRISP_BW, MAGIC_COLOR }

/** On-device document enhancement filters (shadow lift, crisp B&W print, magic color). */
object DocumentFilterEngine {
    fun apply(source: Bitmap, filter: ScanFilter): Bitmap {
        require(!source.isRecycled) { "BITMAP_RECYCLED" }
        if (filter == ScanFilter.ORIGINAL) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
        val canvas = Canvas(out)
        val matrix = when (filter) {
            ScanFilter.ORIGINAL -> ColorMatrix()
            ScanFilter.CLEAN_DOCUMENT -> ColorMatrix().apply {
                val contrast = 1.38f
                val bright = 24f
                val t = (-0.5f * contrast + 0.5f) * 255f + bright
                set(floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            ScanFilter.CRISP_BW -> ColorMatrix().apply {
                setSaturation(0f)
                val contrast = 2.05f
                val t = (-0.5f * contrast + 0.5f) * 255f + 18f
                postConcat(ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            ScanFilter.MAGIC_COLOR -> ColorMatrix().apply {
                setSaturation(1.22f)
                val contrast = 1.25f
                val t = (-0.5f * contrast + 0.5f) * 255f + 14f
                postConcat(ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
        } catch (t: Throwable) {
            out.recycle()
            throw t
        }
    }
}
