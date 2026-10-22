package com.jumlo.app.data.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jumlo.app.core.ImageWorkPolicy
import java.io.ByteArrayOutputStream

/**
 * The Android side of the image seam: `BitmapFactory` in, a scaled JPEG out.
 *
 * The decode is budgeted. A 2550x3300 scan is 33.6 MB as ARGB_8888, which does
 * not fit a small heap, so the image is decoded with a sample size that brings it
 * inside [maxBitmapBytes] before it is scaled and re-encoded. The sample size is
 * taken from the image header, so the full-resolution bitmap is never the thing
 * that gets allocated.
 */
class AndroidImageCodec(private val maxBitmapBytes: Long) : PdfImageCodec {

    override fun recode(source: ByteArray, maxWidth: Int, maxHeight: Int, quality: Int): RecodedImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(sourceWidth, sourceHeight) }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options) ?: return null
        try {
            val scale = minOf(
                1.0,
                minOf(maxWidth.toDouble() / decoded.width.coerceAtLeast(1), maxHeight.toDouble() / decoded.height.coerceAtLeast(1))
            )
            val targetWidth = maxOf(1, (decoded.width * scale).toInt())
            val targetHeight = maxOf(1, (decoded.height * scale).toInt())
            ImageWorkPolicy.requireFits(targetWidth, targetHeight, 0L, maxBitmapBytes)
            val work = if (targetWidth < decoded.width || targetHeight < decoded.height) {
                Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
            } else {
                decoded
            }
            try {
                val out = ByteArrayOutputStream()
                if (!work.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 95), out)) return null
                if (out.size() <= 0) return null
                return RecodedImage(targetWidth, targetHeight, out.toByteArray())
            } finally {
                if (work !== decoded && !work.isRecycled) work.recycle()
            }
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    /** The smallest power-of-two sample size that fits the decoded page in the budget. */
    private fun sampleSize(width: Int, height: Int): Int {
        var step = 1
        while (width.toLong() * height * 4L / (step.toLong() * step) > maxBitmapBytes && step < 64) step *= 2
        return step
    }
}
