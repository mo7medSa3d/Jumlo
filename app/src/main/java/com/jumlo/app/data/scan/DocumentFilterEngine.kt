package com.jumlo.app.data.scan

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScanFilter { ORIGINAL, CLEAN_DOCUMENT, CRISP_BW, MAGIC_COLOR }

/**
 * On-device CamScanner-grade document enhancement filters using fast local illumination
 * normalization (shadow removal + white-paper recovery) followed by mode-specific tone curves.
 */
object DocumentFilterEngine {
    fun apply(source: Bitmap, filter: ScanFilter): Bitmap {
        require(!source.isRecycled) { "BITMAP_RECYCLED" }
        if (filter == ScanFilter.ORIGINAL) return source
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, 0, 0, w, h)

            // Estimate local paper illumination on a tiny downscaled grid (~96px max side) via 5x5 local-max dilation.
            val bgScale = max(1, max(w, h) / 96)
            val bw = max(1, w / bgScale)
            val bh = max(1, h / bgScale)
            val smallLum = IntArray(bw * bh)
            for (by in 0 until bh) {
                val sy = min(h - 1, by * bgScale)
                for (bx in 0 until bw) {
                    val sx = min(w - 1, bx * bgScale)
                    val c = pixels[sy * w + sx]
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    smallLum[by * bw + bx] = max(r, max(g, b))
                }
            }
            val radius = max(8, min(bw, bh) / 6)
            val horizMax = IntArray(bw * bh)
            for (by in 0 until bh) {
                val row = by * bw
                for (bx in 0 until bw) {
                    var peak = 64
                    val x0 = max(0, bx - radius)
                    val x1 = min(bw - 1, bx + radius)
                    for (xx in x0..x1) {
                        val v = smallLum[row + xx]
                        if (v > peak) peak = v
                    }
                    horizMax[row + bx] = peak
                }
            }
            val bgLum = IntArray(bw * bh)
            for (by in 0 until bh) {
                val y0 = max(0, by - radius)
                val y1 = min(bh - 1, by + radius)
                for (bx in 0 until bw) {
                    var peak = 64
                    for (yy in y0..y1) {
                        val v = horizMax[yy * bw + bx]
                        if (v > peak) peak = v
                    }
                    bgLum[by * bw + bx] = peak
                }
            }

            for (y in 0 until h) {
                val by = min(bh - 1, y / bgScale)
                val bgRow = by * bw
                val rowOff = y * w
                for (x in 0 until w) {
                    val bx = min(bw - 1, x / bgScale)
                    val localPaper = bgLum[bgRow + bx].coerceAtLeast(80)
                    val c = pixels[rowOff + x]
                    val r0 = (c ushr 16) and 0xFF
                    val g0 = (c ushr 8) and 0xFF
                    val b0 = c and 0xFF

                    // Normalize against local paper brightness to strip uneven shadows.
                    val nr = (r0 * 255 / localPaper).coerceIn(0, 255)
                    val ng = (g0 * 255 / localPaper).coerceIn(0, 255)
                    val nb = (b0 * 255 / localPaper).coerceIn(0, 255)

                    val (fr, fg, fb) = when (filter) {
                        ScanFilter.ORIGINAL -> Triple(r0, g0, b0)
                        ScanFilter.CLEAN_DOCUMENT -> {
                            val cr = (((nr - 128) * 1.28f) + 136f).roundToInt().coerceIn(0, 255)
                            val cg = (((ng - 128) * 1.28f) + 136f).roundToInt().coerceIn(0, 255)
                            val cb = (((nb - 128) * 1.28f) + 136f).roundToInt().coerceIn(0, 255)
                            Triple(cr, cg, cb)
                        }
                        ScanFilter.CRISP_BW -> {
                            val lum = (nr * 77 + ng * 150 + nb * 29) shr 8
                            val crisp = when {
                                lum >= 205 -> 255
                                lum <= 95 -> 0
                                else -> (((lum - 95) * 255) / 110).coerceIn(0, 255)
                            }
                            Triple(crisp, crisp, crisp)
                        }
                        ScanFilter.MAGIC_COLOR -> {
                            val lum = (nr * 77 + ng * 150 + nb * 29) shr 8
                            val sat = 1.32f
                            val sr = (lum + (nr - lum) * sat).roundToInt().coerceIn(0, 255)
                            val sg = (lum + (ng - lum) * sat).roundToInt().coerceIn(0, 255)
                            val sb = (lum + (nb - lum) * sat).roundToInt().coerceIn(0, 255)
                            val cr = (((sr - 128) * 1.22f) + 134f).roundToInt().coerceIn(0, 255)
                            val cg = (((sg - 128) * 1.22f) + 134f).roundToInt().coerceIn(0, 255)
                            val cb = (((sb - 128) * 1.22f) + 134f).roundToInt().coerceIn(0, 255)
                            Triple(cr, cg, cb)
                        }
                    }
                    pixels[rowOff + x] = (0xFF shl 24) or (fr shl 16) or (fg shl 8) or fb
                }
            }

            out.setPixels(pixels, 0, w, 0, 0, w, h)
            return out
        } catch (t: Throwable) {
            out.recycle()
            throw t
        }
    }
}
