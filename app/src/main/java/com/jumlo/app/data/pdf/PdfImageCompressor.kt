package com.jumlo.app.data.pdf

import com.jumlo.app.core.PdfCompressionPolicy
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject as ImageXObject
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import java.io.ByteArrayInputStream

/**
 * The image compressor, with no Android types in it.
 *
 * The work is done through [PdfImageCodec], a byte-level seam, so the same code
 * runs on the host as on the device. That matters because the JPEG path is the
 * one that used to have no coverage at all: the Android unit-test source set
 * replaces the JDK with `android.jar`, so `BitmapFactory` returns null there and
 * no test could reach the scanner case.
 *
 * The ladder is walked **per image**, not per document: an image that resists
 * the chosen resolution is pushed down the ladder until it is smaller than it
 * was, and an image that is already smaller at the chosen resolution is left
 * alone. That keeps the cost proportional to the pages that need it.
 */
object PdfImageCompressor {

    /** What the compressor did, and what it could not do. */
    data class Report(
        val replaced: Int,
        val skipped: List<String>,
        val pages: Int
    )

    /**
     * Compress the images of [doc].
     *
     * @param maxDpi the resolution the pages are scaled to.
     * @param jpegQuality the quality of the re-encoded images, 1..100.
     * @param codec the byte-level image codec.
     */
    fun compress(
        doc: PDDocument,
        maxDpi: Int,
        jpegQuality: Int,
        onPage: ((Int, Int) -> Unit)? = null,
        codec: PdfImageCodec
    ): Report {
        require(maxDpi in 36..600) { "INVALID_COMPRESS_SETTINGS" }
        require(jpegQuality in 0..100) { "INVALID_COMPRESS_SETTINGS" }
        val ladder = PdfCompressionPolicy.profiles(maxDpi, jpegQuality)
        var replaced = 0
        val skipped = mutableListOf<String>()
        val pageCount = doc.numberOfPages
        // Scanner apps often put the photo inside Form XObjects. The loop walks
        // into them, because a scanner PDF keeps the page photo there.
        val visited = java.util.IdentityHashMap<COSDictionary, Boolean>()
        fun process(resources: com.tom_roush.pdfbox.pdmodel.PDResources?, maxWidth: Int, maxHeight: Int, depth: Int) {
            if (resources == null || depth > 8) return
            if (visited.put(resources.cosObject, true) != null) return
            for (name in resources.xObjectNames.toList()) {
                val objectRef = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
                when (objectRef) {
                    is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject ->
                        process(objectRef.resources, maxWidth, maxHeight, depth + 1)
                    is PDImageXObject -> {
                        val outcome = compressImage(doc, objectRef, maxWidth, maxHeight, ladder, codec, name.name)
                        val container = resources.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
                        if (outcome.first != null) {
                            if (container != null) {
                                container.setItem(name, outcome.first)
                                replaced++
                            } else {
                                skipped += "${name.name}: XOBJECT dictionary missing"
                            }
                        } else if (outcome.second != null) {
                            skipped += "${name.name}: ${outcome.second}"
                        }
                    }
                }
            }
        }
        doc.pages.forEachIndexed { pageIndex, page ->
            // The page box in pixels at the chosen resolution. The CropBox is used
            // when the page has one, because that is the part the reader sees.
            val box = page.cropBox.let { if (it.width > 0 && it.height > 0) it else page.mediaBox }
            val maxWidth = (box.width * maxDpi / 72.0).toInt().coerceAtLeast(16)
            val maxHeight = (box.height * maxDpi / 72.0).toInt().coerceAtLeast(16)
            process(page.resources, maxWidth, maxHeight, 0)
            onPage?.invoke(pageIndex + 1, pageCount)
        }
        return Report(replaced, skipped, pageCount)
    }

    /**
     * The lossless path: an 8-bit Flate image that has no mask is box-downscaled
     * in pure Kotlin and written back as a Flate stream.
     *
     * The codec seam handles the JPEG case, which is what a phone photo is. A
     * Flate image has no JPEG bytes to hand to the codec, so it is scaled here.
     */
    private fun downscaleFlateImage(image: PDImageXObject, maxWidth: Int, maxHeight: Int, jpegQuality: Int = 85): PDImageXObject? {
        if (image.width <= maxWidth && image.height <= maxHeight && jpegQuality >= 95) return null
        val cosStream = image.cosObject as? COSStream ?: return null
        val dict = cosStream as COSDictionary
        val filter = dict.getDictionaryObject(COSName.FILTER)
        val isFlate = when (filter) {
            is COSName -> filter == COSName.FLATE_DECODE
            is COSArray -> filter.size() == 1 && (filter.get(0) as? COSName) == COSName.FLATE_DECODE
            else -> false
        }
        if (!isFlate) return null
        if (dict.getDictionaryObject(COSName.DECODE_PARMS) != null) return null
        val bits = (dict.getDictionaryObject(COSName.BITS_PER_COMPONENT) as? COSNumber)?.intValue() ?: return null
        if (bits != 8) return null
        val colorSpace = (dict.getDictionaryObject(COSName.COLORSPACE) as? COSName) ?: return null
        if (colorSpace != COSName.DEVICERGB && colorSpace != COSName.DEVICEGRAY) return null
        if (dict.getDictionaryObject(COSName.SMASK) != null) return null
        val bytesPerPixel = if (colorSpace == COSName.DEVICERGB) 3 else 1
        val decodedBytes = image.width.toLong() * image.height * bytesPerPixel
        if (image.width <= 0 || image.height <= 0 || decodedBytes > minOf(Runtime.getRuntime().maxMemory() / 4, 96L * 1024 * 1024)) return null
        val raw = ByteArray(decodedBytes.toInt())
        image.createInputStream().use { input ->
            var read = 0
            while (read < raw.size) {
                val n = input.read(raw, read, raw.size - read)
                if (n < 0) return null
                read += n
            }
        }
        val scale = minOf(1.0, minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height))
        val targetW = maxOf(1, (image.width * scale).toInt())
        val targetH = maxOf(1, (image.height * scale).toInt())
        val data = boxDownscale(raw, image.width, image.height, targetW, targetH, bytesPerPixel, jpegQuality)
        val stream = COSStream()
        stream.setItem(COSName.SUBTYPE, COSName.IMAGE)
        stream.setInt(COSName.WIDTH, targetW)
        stream.setInt(COSName.HEIGHT, targetH)
        stream.setItem(COSName.COLORSPACE, colorSpace)
        stream.setInt(COSName.BITS_PER_COMPONENT, 8)
        stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
        stream.createOutputStream().use { it.write(data) }
        return PDImageXObject(PDStream(stream), null)
    }

    private fun boxDownscale(src: ByteArray, sw: Int, sh: Int, tw: Int, th: Int, cpp: Int, quality: Int = 85): ByteArray {
        val out = ByteArray(tw * th * cpp)
        val step = if (quality in 1..79) ((100 - quality) / 12).coerceAtLeast(1) else 1
        for (y in 0 until th) {
            val y0 = y * sh / th
            val y1 = ((y + 1) * sh / th).coerceAtLeast(y0 + 1).coerceAtMost(sh)
            for (x in 0 until tw) {
                val x0 = x * sw / tw
                val x1 = ((x + 1) * sw / tw).coerceAtLeast(x0 + 1).coerceAtMost(sw)
                val sums = IntArray(cpp)
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val base = (yy * sw + xx) * cpp
                    for (c in 0 until cpp) sums[c] += src[base + c].toInt() and 255
                }
                val count = (y1 - y0) * (x1 - x0)
                val base = (y * tw + x) * cpp
                for (c in 0 until cpp) {
                    val avg = sums[c] / count
                    val quantized = if (step > 1) (avg / step) * step else avg
                    out[base + c] = quantized.toByte()
                }
            }
        }
        return out
    }

    /** Shared image placement, separate from Android text rasterization for real-PDF JVM fixtures. */

    /**
     * Re-encode one image, walking the ladder until the result is smaller.
     *
     * The first element of the result is the new image, the second the reason
     * the image was left alone.
     */
    private fun compressImage(
        doc: PDDocument,
        image: PDImageXObject,
        maxWidth: Int,
        maxHeight: Int,
        ladder: List<Pair<Int, Int>>,
        codec: PdfImageCodec,
        name: String
    ): Pair<PDImageXObject?, String?> {
        val originalLength = (image.cosObject as? COSStream)?.length ?: Long.MAX_VALUE
        val dict = image.cosObject as? COSDictionary ?: return null to "UNREADABLE"
        // A mask or a one-bit raster is left alone: turning it into a lossy
        // JPEG would lose the transparency or the crispness it is there for.
        if (dict.getDictionaryObject(COSName.SMASK) != null) return null to "SMASK"
        if (dict.getDictionaryObject(COSName.MASK) != null) return null to "MASK"
        if (dict.getInt(COSName.BITS_PER_COMPONENT) == 1) return null to "BITMAP_1BIT"
        val baseDpi = ladder.first().first
        if (image.suffix != "jpg") {
            // Not a JPEG: the pure lossless path handles the Flate case.
            val flate = downscaleFlateImage(image, maxWidth, maxHeight, ladder.first().second)
            return if (flate != null) flate to null else null to "UNSUPPORTED_CODEC"
        }
        val bytes = runCatching { image.createInputStream().use { it.readBytes() } }.getOrNull()
            ?: return null to "UNREADABLE"
        for ((dpi, quality) in ladder) {
            val scale = dpi.toDouble() / baseDpi
            val targetWidth = (maxWidth * scale).toInt().coerceAtLeast(1)
            val targetHeight = (maxHeight * scale).toInt().coerceAtLeast(1)
            val recoded = try {
                codec.recode(bytes, targetWidth, targetHeight, quality)
            } catch (oom: OutOfMemoryError) {
                // Deliberately wider than Exception: a decode that runs out of
                // memory raises an Error, and one oversized page must not take
                // the whole document down.
                return null to "OUT_OF_MEMORY"
            } catch (failure: Exception) {
                return null to (failure.message ?: "DECODE_FAILED")
            } ?: return null to "DECODE_FAILED"
            val candidate = ImageXObject(
                doc,
                ByteArrayInputStream(recoded.bytes),
                COSName.DCT_DECODE,
                recoded.width,
                recoded.height,
                8,
                PDDeviceRGB.INSTANCE
            )
            val newLength = (candidate.cosObject as? COSStream)?.length ?: Long.MAX_VALUE
            if (newLength > 0L && newLength < originalLength) return candidate to null
        }
        return null to "NOT_SMALLER"
    }
}
