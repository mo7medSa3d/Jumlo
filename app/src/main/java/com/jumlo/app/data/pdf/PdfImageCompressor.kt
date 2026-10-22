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
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

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
        codec: PdfImageCodec,
        maxWorkingBytes: Long = minOf(Runtime.getRuntime().maxMemory() / 2, 192L * 1024 * 1024),
        checkCancel: () -> Unit = {}
    ): Report {
        require(maxDpi in 36..600) { "INVALID_COMPRESS_SETTINGS" }
        require(jpegQuality in 0..100) { "INVALID_COMPRESS_SETTINGS" }
        require(maxWorkingBytes > 0) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        val ladder = PdfCompressionPolicy.profiles(maxDpi, jpegQuality)
        var replaced = 0
        val skipped = mutableListOf<String>()
        val pageCount = doc.numberOfPages
        // A resource dictionary or image may be shared by pages of different
        // sizes. Collect the largest required resolution before changing it;
        // otherwise a thumbnail page encountered first degrades every use.
        val targets = java.util.IdentityHashMap<COSDictionary, Pair<Int, Int>>()
        fun pageTarget(page: com.tom_roush.pdfbox.pdmodel.PDPage): Pair<Int, Int> {
            val media = page.mediaBox
            val crop = page.cropBox
            // Cropping clips an image; it does not make its uncropped pixels
            // occupy the smaller page. Keep the full page's resolution.
            val unit = page.userUnit.toDouble().takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            val width = maxOf(media.width.toDouble(), crop.width.toDouble()) * unit
            val height = maxOf(media.height.toDouble(), crop.height.toDouble()) * unit
            return (width * maxDpi / 72.0).toInt().coerceAtLeast(16) to
                (height * maxDpi / 72.0).toInt().coerceAtLeast(16)
        }
        fun collect(resources: com.tom_roush.pdfbox.pdmodel.PDResources?, width: Int, height: Int,
                    path: java.util.IdentityHashMap<COSDictionary, Boolean>, depth: Int) {
            if (resources == null || depth > 8 || path.put(resources.cosObject, true) != null) return
            try {
                for (name in resources.xObjectNames) {
                    checkCancel()
                    when (val image = runCatching { resources.getXObject(name) }.getOrNull()) {
                        is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject ->
                            collect(image.resources, width, height, path, depth + 1)
                        is PDImageXObject -> {
                            val previous = targets[image.cosObject]
                            targets[image.cosObject] = maxOf(width, previous?.first ?: 0) to
                                maxOf(height, previous?.second ?: 0)
                        }
                    }
                }
            } finally { path.remove(resources.cosObject) }
        }
        doc.pages.forEach { page ->
            checkCancel()
            val (width, height) = pageTarget(page)
            collect(page.resources, width, height, java.util.IdentityHashMap(), 0)
        }
        // Scanner apps often put the photo inside Form XObjects. The loop walks
        // into them, because a scanner PDF keeps the page photo there.
        val visited = java.util.IdentityHashMap<COSDictionary, Boolean>()
        val outcomes = java.util.IdentityHashMap<COSDictionary, Pair<PDImageXObject?, String?>>()
        fun process(resources: com.tom_roush.pdfbox.pdmodel.PDResources?, maxWidth: Int, maxHeight: Int, depth: Int) {
            if (resources == null || depth > 8) return
            if (visited.put(resources.cosObject, true) != null) return
            for (name in resources.xObjectNames.toList()) {
                checkCancel()
                val objectRef = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
                when (objectRef) {
                    is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject ->
                        process(objectRef.resources, maxWidth, maxHeight, depth + 1)
                    is PDImageXObject -> {
                        val firstUse = !outcomes.containsKey(objectRef.cosObject)
                        val outcome = outcomes[objectRef.cosObject] ?: run {
                            val target = targets[objectRef.cosObject] ?: (maxWidth to maxHeight)
                            compressImage(doc, objectRef, target.first, target.second, ladder, codec, maxWorkingBytes, checkCancel)
                                .also { outcomes[objectRef.cosObject] = it }
                        }
                        val container = resources.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
                        if (outcome.first != null) {
                            if (container != null) {
                                container.setItem(name, outcome.first)
                                if (firstUse) replaced++
                            } else {
                                skipped += "${name.name}: XOBJECT dictionary missing"
                            }
                        } else if (firstUse && outcome.second != null) {
                            skipped += "${name.name}: ${outcome.second}"
                        }
                    }
                }
            }
        }
        doc.pages.forEachIndexed { pageIndex, page ->
            val (maxWidth, maxHeight) = pageTarget(page)
            process(page.resources, maxWidth, maxHeight, 0)
            onPage?.invoke(pageIndex + 1, pageCount)
        }
        return Report(replaced, skipped, pageCount)
    }

    /**
     * An 8-bit Flate image without a mask is box-downscaled in pure Kotlin and
     * written back as a Flate stream. Downsampling and low-quality quantization
     * are lossy even though the resulting stream uses a lossless Flate codec.
     *
     * The codec seam handles the JPEG case, which is what a phone photo is. A
     * Flate image has no JPEG bytes to hand to the codec, so it is scaled here.
     */
    private fun downscaleFlateImage(doc: PDDocument, image: PDImageXObject, maxWidth: Int, maxHeight: Int,
                                   jpegQuality: Int, maxWorkingBytes: Long, checkCancel: () -> Unit): PDImageXObject? {
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
        if (image.width <= 0 || image.height <= 0) return null
        val pixels = image.width.toLong() * image.height
        require(pixels <= minOf(Int.MAX_VALUE.toLong(), maxWorkingBytes) / bytesPerPixel) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        val decodedBytes = pixels * bytesPerPixel
        val scale = minOf(1.0, minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height))
        val targetW = maxOf(1, (image.width * scale).toInt())
        val targetH = maxOf(1, (image.height * scale).toInt())
        val outputBytes = targetW.toLong() * targetH * bytesPerPixel
        require(outputBytes <= maxWorkingBytes - decodedBytes) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        val raw = ByteArray(decodedBytes.toInt())
        image.createInputStream().use { input ->
            var read = 0
            while (read < raw.size) {
                checkCancel()
                val n = input.read(raw, read, raw.size - read)
                if (n < 0) return null
                read += n
            }
        }
        val data = boxDownscale(raw, image.width, image.height, targetW, targetH, bytesPerPixel, jpegQuality, checkCancel)
        // Use the document's scratch-file policy rather than an independent
        // memory-only COSStream for every resampled page.
        val stream = PDStream(doc).cosObject
        stream.setItem(COSName.SUBTYPE, COSName.IMAGE)
        stream.setInt(COSName.WIDTH, targetW)
        stream.setInt(COSName.HEIGHT, targetH)
        stream.setItem(COSName.COLORSPACE, colorSpace)
        stream.setInt(COSName.BITS_PER_COMPONENT, 8)
        stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
        // /Decode maps stored samples to colors; losing it can invert a scan.
        for (key in listOf(COSName.DECODE, COSName.INTERPOLATE, COSName.INTENT, COSName.METADATA,
            COSName.getPDFName("OC"), COSName.getPDFName("StructParent"))) {
            dict.getItem(key)?.let { stream.setItem(key, it) }
        }
        stream.createOutputStream().use { it.write(data) }
        return PDImageXObject(PDStream(stream), null)
    }

    private fun boxDownscale(src: ByteArray, sw: Int, sh: Int, tw: Int, th: Int, cpp: Int, quality: Int,
                             checkCancel: () -> Unit): ByteArray {
        val out = ByteArray(tw * th * cpp)
        val step = if (quality in 1..79) ((100 - quality) / 12).coerceAtLeast(1) else 1
        for (y in 0 until th) {
            checkCancel()
            val y0 = (y.toLong() * sh / th).toInt()
            val y1 = ((y + 1L) * sh / th).toInt().coerceAtLeast(y0 + 1).coerceAtMost(sh)
            for (x in 0 until tw) {
                val x0 = (x.toLong() * sw / tw).toInt()
                val x1 = ((x + 1L) * sw / tw).toInt().coerceAtLeast(x0 + 1).coerceAtMost(sw)
                val sums = LongArray(cpp)
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
        maxWorkingBytes: Long,
        checkCancel: () -> Unit
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
            // Not a JPEG: the pure raster path handles the Flate case.
            val flate = try {
                downscaleFlateImage(doc, image, maxWidth, maxHeight, ladder.first().second, maxWorkingBytes, checkCancel)
            } catch (cancel: CancellationException) { throw cancel }
            catch (oom: OutOfMemoryError) { return null to "OUT_OF_MEMORY" }
            catch (failure: Exception) { return null to (failure.message ?: "DECODE_FAILED") }
            return when {
                flate == null -> null to "UNSUPPORTED_CODEC"
                flate.cosObject.length in 1 until originalLength -> flate to null
                else -> { flate.cosObject.close(); null to "NOT_SMALLER" }
            }
        }
        // JPEG bytes do not include the PDF's color-space or sample mapping.
        // The bitmap codec cannot reproduce ICC/CMYK/Decode transformations.
        val colorSpace = dict.getDictionaryObject(COSName.COLORSPACE)
        if (colorSpace != COSName.DEVICERGB && colorSpace != COSName.DEVICEGRAY) return null to "COLORSPACE"
        if (dict.getDictionaryObject(COSName.DECODE) != null) return null to "DECODE_MAPPING"
        if (dict.getDictionaryObject(COSName.DECODE_PARMS) != null) return null to "DECODE_PARAMETERS"
        // Encoded images are not allowed to allocate arbitrary byte arrays, even
        // when their declared dimensions are small or outer filters expand them.
        val encodedLimit = minOf(maxWorkingBytes / 4, 64L * 1024 * 1024).coerceAtLeast(1)
        if (originalLength > encodedLimit) return null to "IMAGE_MEMORY_BUDGET_EXCEEDED"
        val bytes = try {
            image.createInputStream().use { input ->
                val out = ByteArrayOutputStream(minOf(encodedLimit, 64L * 1024).toInt())
                val buffer = ByteArray(minOf(encodedLimit, 64L * 1024).toInt())
                while (true) {
                    checkCancel()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count.toLong() <= encodedLimit - out.size()) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
                    if (count > 0) out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (oom: OutOfMemoryError) { return null to "OUT_OF_MEMORY" }
        catch (failure: Exception) { return null to (failure.message ?: "UNREADABLE") }
        for ((dpi, quality) in ladder) {
            checkCancel()
            val scale = dpi.toDouble() / baseDpi
            val targetWidth = (maxWidth * scale).toInt().coerceAtLeast(1)
            val targetHeight = (maxHeight * scale).toInt().coerceAtLeast(1)
            val recoded = try {
                codec.recode(bytes, targetWidth, targetHeight, quality)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (oom: OutOfMemoryError) {
                // Deliberately wider than Exception: a decode that runs out of
                // memory raises an Error, and one oversized page must not take
                // the whole document down.
                return null to "OUT_OF_MEMORY"
            } catch (failure: Exception) {
                return null to (failure.message ?: "DECODE_FAILED")
            } ?: return null to "DECODE_FAILED"
            checkCancel()
            if (recoded.bytes.isEmpty() || recoded.bytes.size >= originalLength) continue
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
            if (newLength > 0L && newLength < originalLength) {
                for (key in listOf(COSName.INTERPOLATE, COSName.INTENT, COSName.METADATA,
                    COSName.getPDFName("OC"), COSName.getPDFName("StructParent"))) {
                    dict.getItem(key)?.let { candidate.cosObject.setItem(key, it) }
                }
                return candidate to null
            }
        }
        return null to "NOT_SMALLER"
    }
}
