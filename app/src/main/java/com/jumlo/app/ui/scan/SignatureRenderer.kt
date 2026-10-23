package com.jumlo.app.ui.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset

/** Normalized pad coordinates keep saved ink stable through size changes. */
internal data class SignatureStroke(
    val points: List<Offset>,
    val colorArgb: Int,
    val widthFraction: Float
)

/** Production alpha renderer, also exercised by exact-output Android pixel tests. */
internal fun renderSignature(strokes: List<SignatureStroke>, canvasWidth: Int, canvasHeight: Int): Bitmap {
    require(canvasWidth > 0 && canvasHeight > 0 && strokes.any { it.points.size >= 2 })
    require(strokes.size <= 300 && strokes.sumOf { it.points.size } <= 60_000)
    val geometry = signatureExportGeometry(canvasWidth, canvasHeight)
    val bitmap = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
    try {
        // A fresh ARGB bitmap is transparent; do not paint a page-colored background.
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            require(stroke.widthFraction.isFinite() && stroke.widthFraction in .0001f..1f)
            val path = Path()
            stroke.points.forEachIndexed { index, point ->
                require(point.x.isFinite() && point.y.isFinite())
                val x = point.x.coerceIn(0f, 1f) * geometry.width
                val y = point.y.coerceIn(0f, 1f) * geometry.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.colorArgb
                style = Paint.Style.STROKE
                strokeWidth = stroke.widthFraction * geometry.width
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
        return bitmap
    } catch (error: Throwable) {
        bitmap.recycle()
        throw error
    }
}
