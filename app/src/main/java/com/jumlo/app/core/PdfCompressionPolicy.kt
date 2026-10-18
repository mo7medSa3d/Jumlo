package com.jumlo.app.core

/** Bounds optional PDF image bitmap decoding so compression cannot exhaust the Java heap.
 * A source image can coexist with a scaled bitmap and JPEG encoder buffers. */
object PdfCompressionPolicy {
    private const val MAX_DECODE_BYTES = 32L * 1024 * 1024

    fun canDecodeImage(width: Int, height: Int, maxHeapBytes: Long): Boolean {
        if (width <= 0 || height <= 0 || maxHeapBytes <= 0L) return false
        // Compare pixel counts before multiplying by ARGB_8888's four bytes per pixel.
        // Even Int.MAX_VALUE x Int.MAX_VALUE fits in Long, preventing overflow.
        val maxPixels = minOf(MAX_DECODE_BYTES, maxHeapBytes / 8) / 4
        return width.toLong() * height.toLong() <= maxPixels
    }
}
