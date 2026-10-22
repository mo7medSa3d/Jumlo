package com.jumlo.app.data.pdf

/**
 * The image part of PDF compression, expressed in bytes.
 *
 * The seam is byte-oriented on purpose. An interface that mentioned
 * `android.graphics.Bitmap` could not be implemented off the device, and the
 * JPEG path is the one that has to be provable without one. The input is the
 * raw bytes of the image stream taken out of the PDF; the output is a
 * re-encoded JPEG.
 */
data class RecodedImage(val width: Int, val height: Int, val bytes: ByteArray)

fun interface PdfImageCodec {
    /**
     * Decode [source], scale it to fit [maxWidth] x [maxHeight] without enlarging
     * it, and re-encode it as JPEG at [quality].
     *
     * Null when the image cannot be read. The codec is the part that allocates,
     * so the codec is also where the memory budget is enforced.
     */
    fun recode(source: ByteArray, maxWidth: Int, maxHeight: Int, quality: Int): RecodedImage?
}
