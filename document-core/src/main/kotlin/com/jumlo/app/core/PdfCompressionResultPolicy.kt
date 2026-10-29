package com.jumlo.app.core

/**
 * Interpret measured PDF output bytes, not the number of images the codec attempted.
 *
 * Skipped images are a useful warning only when the result actually became smaller.
 * An unchanged export must not announce even a partial size reduction.
 * This stays Android-free and accepts only validated nonempty, non-growing output.
 */
data class PdfCompressionVerdict(
    val smaller: Boolean,
    val savedBytes: Long,
    val notice: String?
)

object PdfCompressionResultPolicy {
    fun classify(originalBytes: Long, outputBytes: Long, skippedImages: Int): PdfCompressionVerdict {
        require(originalBytes > 0L && outputBytes in 1..originalBytes) { "PDF_VERIFY_FAILED" }
        require(skippedImages >= 0) { "INVALID_COMPRESS_SETTINGS" }
        val saved = originalBytes - outputBytes
        val note = when {
            saved == 0L -> "PDF_NOT_COMPRESSIBLE"
            skippedImages > 0 -> "PDF_IMAGE_COMPRESSION_PARTIAL"
            else -> null
        }
        return PdfCompressionVerdict(smaller = saved > 0L, savedBytes = saved, notice = note)
    }
}
