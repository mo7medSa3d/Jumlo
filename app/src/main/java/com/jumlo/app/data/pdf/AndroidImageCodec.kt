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
        if (maxWidth <= 0 || maxHeight <= 0 || maxBitmapBytes < 4) return null
        val sample = sampleSize(sourceWidth, sourceHeight, maxWidth, maxHeight)
        if (sample == 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
        }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options) ?: return null
        try {
            val scale = minOf(
                1.0,
                minOf(maxWidth.toDouble() / decoded.width.coerceAtLeast(1), maxHeight.toDouble() / decoded.height.coerceAtLeast(1))
            )
            val targetWidth = maxOf(1, (decoded.width * scale).toInt())
            val targetHeight = maxOf(1, (decoded.height * scale).toInt())
            val scaling = targetWidth < decoded.width || targetHeight < decoded.height
            require(decoded.allocationByteCount.toLong() <= maxBitmapBytes) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
            ImageWorkPolicy.requireFits(targetWidth, targetHeight,
                if (scaling) decoded.allocationByteCount.toLong() else 0L, maxBitmapBytes)
            val work = if (scaling) {
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

    /** Decode only the pixels needed by the target, accounting for both resident
     * source and resized output rather than checking each allocation in isolation. */
    private fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        val scale = minOf(1.0, maxWidth.toDouble() / width, maxHeight.toDouble() / height)
        val requestedW = maxOf(1, (width * scale).toInt())
        val requestedH = maxOf(1, (height * scale).toInt())
        fun sampled(value: Int, sample: Int) = ((value.toLong() + sample - 1) / sample).toInt()
        var step = 1
        while (step < (1 shl 30) && step * 2 <= maxOf(width, height) &&
            sampled(width, step * 2) >= requestedW && sampled(height, step * 2) >= requestedH) step *= 2
        while (true) {
            val w = sampled(width, step); val h = sampled(height, step)
            val factor = minOf(1.0, maxWidth.toDouble() / w, maxHeight.toDouble() / h)
            val tw = maxOf(1, (w * factor).toInt()); val th = maxOf(1, (h * factor).toInt())
            val sourcePixels = w.toLong() * h
            val extraPixels = if (tw == w && th == h) 0L else tw.toLong() * th
            if (sourcePixels <= maxBitmapBytes / 4 && extraPixels <= maxBitmapBytes / 4 - sourcePixels) return step
            if (step >= (1 shl 30)) return 0
            step *= 2
        }
    }
}
